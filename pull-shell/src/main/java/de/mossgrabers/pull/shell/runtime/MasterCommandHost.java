// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.shell.runtime;

import de.mossgrabers.framework.controller.color.ColorEx;
import de.mossgrabers.framework.controller.valuechanger.IValueChanger;
import de.mossgrabers.framework.daw.IApplication;
import de.mossgrabers.framework.daw.IModel;
import de.mossgrabers.framework.daw.IProject;
import de.mossgrabers.framework.daw.ITransport;
import de.mossgrabers.framework.daw.data.ICursorTrack;
import de.mossgrabers.framework.daw.data.IMasterTrack;
import de.mossgrabers.pull.core.api.MasterSnapshot;
import de.mossgrabers.pull.core.api.ProjectSnapshot;
import de.mossgrabers.pull.core.api.effect.CoreEffect;
import de.mossgrabers.pull.core.api.effect.NavigateProjectEffect;
import de.mossgrabers.pull.core.api.effect.ProjectFileAction;
import de.mossgrabers.pull.core.api.effect.ProjectFileActionEffect;
import de.mossgrabers.pull.core.api.effect.ProjectNavigationDirection;
import de.mossgrabers.pull.core.api.effect.SetProjectEngineEffect;
import de.mossgrabers.pull.core.api.effect.SetProjectTransportStateEffect;
import de.mossgrabers.pull.core.api.effect.TransportState;
import de.mossgrabers.pull.core.api.output.RgbColor;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;


/**
 * Serialized project command lane and authoritative lightweight/Master snapshots.
 *
 * <p>A remote transport command is one stable-owned operation. It searches the bounded open-tab
 * window, applies the absolute state only after observing the exact target, waits for authoritative
 * transport readback, and retraces its acknowledged path. The operation survives core reload or
 * quarantine and never owns core lifecycle.</p>
 *
 * <p>This cross-project transaction belongs in the stable canopy because Bitwig exposes project
 * operations through a mutable active-project context. Navigating that context also changes the
 * project state observed by the reloadable core. The core can choose the semantic target, but the
 * stable owner must keep the navigate, verify, apply, acknowledge, and return sequence together
 * while that shared context moves underneath it.</p>
 */
final class MasterCommandHost
{
    private static final int ENGINE_ACKNOWLEDGEMENT_TIMEOUT_TICKS = 8;
    private static final int TRANSPORT_ACKNOWLEDGEMENT_TIMEOUT_TICKS = 16;
    private static final int NAVIGATION_ACKNOWLEDGEMENT_TIMEOUT_TICKS = 100;
    private static final int REMOTE_NAVIGATION_CAPACITY = 32;

    private final IProject project;
    private final IApplication application;
    private final ITransport transport;
    private final IMasterTrack masterTrack;
    private final ICursorTrack cursorTrack;
    private final IValueChanger valueChanger;
    private final RuntimeLog log;

    private boolean previousUnavailable;
    private boolean nextUnavailable;
    private String observedProjectIdentity;
    private PendingCommand pending;
    private RemoteTransportCommand remoteTransport;
    private MasterSnapshot snapshot = MasterSnapshot.empty ();
    private ProjectSnapshot projectSnapshot = ProjectSnapshot.empty ();


    MasterCommandHost (final IModel model, final RuntimeLog log)
    {
        final IModel checkedModel = Objects.requireNonNull (model, "model");
        this.project = checkedModel.getProject ();
        this.application = checkedModel.getApplication ();
        this.transport = checkedModel.getTransport ();
        this.masterTrack = checkedModel.getMasterTrack ();
        this.cursorTrack = checkedModel.getCursorTrack ();
        this.valueChanger = checkedModel.getValueChanger ();
        this.log = Objects.requireNonNull (log, "log");
    }


    boolean refresh (final boolean publishMaster, final boolean publishProject)
    {
        if (publishMaster || publishProject || this.pending != null || this.remoteTransport != null)
        {
            this.advancePending ();
            this.advanceRemoteTransport ();
        }
        final MasterSnapshot refreshedMaster = publishMaster ? this.captureMasterSnapshot () : MasterSnapshot.empty ();
        final ProjectSnapshot refreshedProject = publishProject ? this.captureProjectSnapshot () : ProjectSnapshot.empty ();
        final boolean changed = !refreshedMaster.equals (this.snapshot) || !refreshedProject.equals (this.projectSnapshot);
        this.snapshot = refreshedMaster;
        this.projectSnapshot = refreshedProject;
        return changed;
    }


    MasterSnapshot snapshot ()
    {
        return this.snapshot;
    }


    ProjectSnapshot projectSnapshot ()
    {
        return this.projectSnapshot;
    }


    boolean canTargetProject (final String expectedIdentity)
    {
        return this.pending == null && this.remoteTransport == null && this.identityMatches (expectedIdentity);
    }


    ControllerBridge.PreparedAction prepare (final CoreEffect effect)
    {
        if (effect instanceof final NavigateProjectEffect navigation)
        {
            final boolean unavailable = navigation.direction () == ProjectNavigationDirection.PREVIOUS ? this.previousUnavailable : this.nextUnavailable;
            return this.canTargetProject (navigation.expectedProjectIdentity ()) && !unavailable ? new PreparedNavigation (navigation.expectedProjectIdentity (), navigation.direction ()) : IgnoredAction.INSTANCE;
        }
        if (effect instanceof final SetProjectEngineEffect engine)
            return this.canTargetProject (engine.expectedProjectIdentity ()) ? new PreparedEngine (engine.expectedProjectIdentity (), engine.active ()) : IgnoredAction.INSTANCE;
        if (effect instanceof final ProjectFileActionEffect file)
            return this.canTargetProject (file.expectedProjectIdentity ()) ? new PreparedFile (file.expectedProjectIdentity (), file.action ()) : IgnoredAction.INSTANCE;
        if (effect instanceof final SetProjectTransportStateEffect transportState)
        {
            final boolean valid = this.canTargetProject (transportState.originProjectIdentity ());
            return valid ? new PreparedProjectTransport (
                transportState.originProjectIdentity (),
                transportState.targetProjectIdentity (),
                transportState.state (),
                transportState.enabled ()) : IgnoredAction.INSTANCE;
        }
        return null;
    }


    boolean applyIfOwned (final ControllerBridge.PreparedAction action)
    {
        if (action == IgnoredAction.INSTANCE)
            return true;
        if (action instanceof final PreparedNavigation navigation)
        {
            if (this.canTargetProject (navigation.expectedProjectIdentity ()))
                this.beginNavigation (navigation.direction ());
            return true;
        }
        if (action instanceof final PreparedEngine engine)
        {
            if (!this.canTargetProject (engine.expectedProjectIdentity ()) || this.application.isEngineActive () == engine.active ())
                return true;
            this.pending = PendingCommand.engine (engine.expectedProjectIdentity (), engine.active ());
            this.application.setEngineActive (engine.active ());
            return true;
        }
        if (action instanceof final PreparedFile file)
        {
            if (!this.canTargetProject (file.expectedProjectIdentity ()))
                return true;
            if (file.action () == ProjectFileAction.OPEN)
                this.project.load ();
            else
                this.project.save ();
            return true;
        }
        if (action instanceof final PreparedProjectTransport request)
        {
            this.applyProjectTransport (request);
            return true;
        }
        return false;
    }


    private void applyProjectTransport (final PreparedProjectTransport request)
    {
        if (!this.canTargetProject (request.originIdentity ()))
            return;
        if (request.originIdentity ().equals (request.targetIdentity ()))
        {
            this.applyTransportState (request.state (), request.enabled ());
            return;
        }

        this.remoteTransport = new RemoteTransportCommand (
            request.originIdentity (),
            request.targetIdentity (),
            request.state (),
            request.enabled ());
        this.advanceRemoteTransport ();
    }


    private void advancePending ()
    {
        final String currentIdentity = this.currentIdentity ();
        if (currentIdentity.isBlank ())
            return;
        if (this.observedProjectIdentity == null)
            this.observedProjectIdentity = currentIdentity;
        if (this.pending == null)
        {
            if (!this.observedProjectIdentity.equals (currentIdentity))
                this.observeExternalProjectChange (currentIdentity);
            return;
        }

        if (this.pending.kind == PendingKind.ENGINE)
        {
            if (!this.pending.originIdentity.equals (currentIdentity))
            {
                this.observeExternalProjectChange (currentIdentity);
                this.pending = null;
            }
            else if (this.application.isEngineActive () == this.pending.desiredEngineActive || ++this.pending.age >= ENGINE_ACKNOWLEDGEMENT_TIMEOUT_TICKS)
                this.pending = null;
            return;
        }

        if (this.pending.originIdentity.equals (currentIdentity))
        {
            if (++this.pending.age >= NAVIGATION_ACKNOWLEDGEMENT_TIMEOUT_TICKS)
                this.completeFailedNavigation ();
            return;
        }

        if (!currentIdentity.equals (this.pending.targetIdentity))
        {
            this.pending.targetIdentity = currentIdentity;
            return;
        }

        this.completeSuccessfulNavigation (currentIdentity);
    }


    private void completeFailedNavigation ()
    {
        final PendingCommand failed = this.pending;
        this.pending = null;
        if (failed.direction == ProjectNavigationDirection.PREVIOUS)
            this.previousUnavailable = true;
        else
            this.nextUnavailable = true;
        if (this.remoteTransport == null)
            return;

        if (this.remoteTransport.stage == RemoteStage.RETURNING)
        {
            this.log.warn ("Project return was not observed; retaining the command lane and retrying from " + this.currentIdentity ());
            return;
        }
        if (failed.direction == ProjectNavigationDirection.PREVIOUS)
            this.remoteTransport.searchDirection = ProjectNavigationDirection.NEXT;
        else
            this.remoteTransport.stage = RemoteStage.RETURNING;
    }


    private void completeSuccessfulNavigation (final String targetIdentity)
    {
        final PendingCommand completed = this.pending;
        this.pending = null;
        this.observedProjectIdentity = targetIdentity;
        this.previousUnavailable = false;
        this.nextUnavailable = false;
        if (this.remoteTransport == null)
            return;

        if (this.remoteTransport.stage == RemoteStage.RETURNING)
        {
            if (this.remoteTransport.path.isEmpty ())
            {
                this.retainFailedRemoteTransport ("Project return completed without an acknowledged path");
                return;
            }
            final PathStep step = this.remoteTransport.path.getLast ();
            if (!step.toIdentity.equals (completed.originIdentity) || !step.fromIdentity.equals (targetIdentity) || opposite (step.direction) != completed.direction)
            {
                this.retainFailedRemoteTransport ("Project return diverged from its acknowledged path");
                return;
            }
            this.remoteTransport.path.removeLast ();
        }
        else
        {
            this.recordRemoteNavigationStep (new PathStep (completed.originIdentity, targetIdentity, completed.direction));
            this.remoteTransport.navigationSteps++;
        }
        this.remoteTransport.expectedCurrentIdentity = targetIdentity;
    }


    private void advanceRemoteTransport ()
    {
        if (this.remoteTransport == null || this.pending != null)
            return;
        final String currentIdentity = this.currentIdentity ();
        if (currentIdentity.isBlank ())
            return;
        if (this.remoteTransport.stage == RemoteStage.FAILED_RETURN)
        {
            if (currentIdentity.equals (this.remoteTransport.originIdentity))
                this.remoteTransport = null;
            return;
        }

        if (!currentIdentity.equals (this.remoteTransport.expectedCurrentIdentity))
        {
            if (currentIdentity.equals (this.remoteTransport.originIdentity))
            {
                this.remoteTransport = null;
                return;
            }
            if (this.remoteTransport.path.isEmpty () || !currentIdentity.equals (this.remoteTransport.path.getLast ().toIdentity))
            {
                this.retainFailedRemoteTransport ("Visible project changed outside the remote transport transaction");
                return;
            }
            this.remoteTransport.expectedCurrentIdentity = currentIdentity;
            this.remoteTransport.stage = RemoteStage.RETURNING;
        }

        switch (this.remoteTransport.stage)
        {
            case SEARCHING -> this.advanceRemoteSearch (currentIdentity);
            case WAITING_FOR_TRANSPORT -> this.advanceRemoteTransportAcknowledgement (currentIdentity);
            case RETURNING -> this.advanceRemoteReturn (currentIdentity);
            case FAILED_RETURN -> {
                // Handled before mutable project-context validation above.
            }
        }
    }


    private void advanceRemoteSearch (final String currentIdentity)
    {
        if (currentIdentity.equals (this.remoteTransport.targetIdentity))
        {
            if (!this.application.isEngineActive ())
            {
                this.remoteTransport.stage = RemoteStage.RETURNING;
                this.advanceRemoteReturn (currentIdentity);
                return;
            }
            if (this.transportState (this.remoteTransport.state) == this.remoteTransport.enabled)
            {
                this.remoteTransport.stage = RemoteStage.RETURNING;
                this.advanceRemoteReturn (currentIdentity);
                return;
            }
            this.applyTransportState (this.remoteTransport.state, this.remoteTransport.enabled);
            this.remoteTransport.stage = RemoteStage.WAITING_FOR_TRANSPORT;
            this.remoteTransport.transportAcknowledgementAge = 0;
            return;
        }

        if (this.remoteTransport.navigationSteps >= REMOTE_NAVIGATION_CAPACITY)
        {
            this.remoteTransport.stage = RemoteStage.RETURNING;
            this.advanceRemoteReturn (currentIdentity);
            return;
        }

        if (this.remoteTransport.searchDirection == ProjectNavigationDirection.PREVIOUS && this.previousUnavailable)
            this.remoteTransport.searchDirection = ProjectNavigationDirection.NEXT;
        final boolean unavailable = this.remoteTransport.searchDirection == ProjectNavigationDirection.PREVIOUS ? this.previousUnavailable : this.nextUnavailable;
        if (unavailable)
        {
            this.remoteTransport.stage = RemoteStage.RETURNING;
            this.advanceRemoteReturn (currentIdentity);
            return;
        }
        this.beginNavigation (this.remoteTransport.searchDirection);
    }


    private void advanceRemoteTransportAcknowledgement (final String currentIdentity)
    {
        if (!currentIdentity.equals (this.remoteTransport.targetIdentity) || !this.application.isEngineActive ())
        {
            this.remoteTransport.stage = RemoteStage.RETURNING;
            this.advanceRemoteReturn (currentIdentity);
            return;
        }
        if (this.transportState (this.remoteTransport.state) == this.remoteTransport.enabled || ++this.remoteTransport.transportAcknowledgementAge >= TRANSPORT_ACKNOWLEDGEMENT_TIMEOUT_TICKS)
        {
            this.remoteTransport.stage = RemoteStage.RETURNING;
            this.advanceRemoteReturn (currentIdentity);
        }
    }


    private void advanceRemoteReturn (final String currentIdentity)
    {
        if (this.remoteTransport.path.isEmpty ())
        {
            if (currentIdentity.equals (this.remoteTransport.originIdentity))
                this.remoteTransport = null;
            else
                this.retainFailedRemoteTransport ("Remote transport lost its return path");
            return;
        }

        final PathStep step = this.remoteTransport.path.getLast ();
        if (!currentIdentity.equals (step.toIdentity))
        {
            this.retainFailedRemoteTransport ("Remote transport no longer matches its return path");
            return;
        }
        this.beginNavigation (opposite (step.direction));
    }


    private void beginNavigation (final ProjectNavigationDirection direction)
    {
        final String origin = this.currentIdentity ();
        if (origin.isBlank () || this.pending != null)
            return;
        this.pending = PendingCommand.navigation (origin, direction);
        if (direction == ProjectNavigationDirection.PREVIOUS)
            this.project.previous ();
        else
            this.project.next ();
    }


    private void recordRemoteNavigationStep (final PathStep step)
    {
        if (!this.remoteTransport.path.isEmpty ())
        {
            final PathStep previous = this.remoteTransport.path.getLast ();
            if (previous.fromIdentity.equals (step.toIdentity) && previous.toIdentity.equals (step.fromIdentity) && opposite (previous.direction) == step.direction)
            {
                this.remoteTransport.path.removeLast ();
                return;
            }
        }
        if (this.remoteTransport.path.size () < REMOTE_NAVIGATION_CAPACITY)
            this.remoteTransport.path.add (step);
        else
            this.remoteTransport.stage = RemoteStage.RETURNING;
    }


    private void retainFailedRemoteTransport (final String message)
    {
        this.log.warn (message + "; retaining the command lane until origin " + this.remoteTransport.originIdentity + " is observed");
        this.remoteTransport.stage = RemoteStage.FAILED_RETURN;
    }


    private boolean transportState (final TransportState state)
    {
        return switch (state)
        {
            case PLAYING -> this.transport.isPlaying ();
            case RECORDING -> this.transport.isRecording ();
            case ARRANGER_OVERDUB -> this.transport.isArrangerOverdub ();
            case LAUNCHER_OVERDUB -> this.transport.isLauncherOverdub ();
            case LOOP -> this.transport.isLoop ();
            case METRONOME -> this.transport.isMetronomeOn ();
            case FILL_MODE -> this.transport.isFillModeActive ();
        };
    }


    /** Apply one absolute transport state without entering the cross-project command lane. */
    void applyTransportState (final TransportState state, final boolean enabled)
    {
        if (this.transportState (state) == enabled)
            return;
        switch (state)
        {
            case PLAYING ->
            {
                if (enabled)
                    this.transport.play ();
                else
                    this.transport.stop ();
            }
            case RECORDING -> this.transport.setRecording (enabled);
            case ARRANGER_OVERDUB -> this.transport.setArrangerOverdub (enabled);
            case LAUNCHER_OVERDUB -> this.transport.setLauncherOverdub (enabled);
            case LOOP -> this.transport.setLoop (enabled);
            case METRONOME -> this.transport.setMetronome (enabled);
            case FILL_MODE -> this.transport.setFillModeActive (enabled);
        }
    }


    private boolean identityMatches (final String expectedIdentity)
    {
        return !expectedIdentity.isBlank () && expectedIdentity.equals (this.project.getIdentity ());
    }


    private String currentIdentity ()
    {
        return Objects.requireNonNullElse (this.project.getIdentity (), "");
    }


    private static ProjectNavigationDirection opposite (final ProjectNavigationDirection direction)
    {
        return direction == ProjectNavigationDirection.PREVIOUS ? ProjectNavigationDirection.NEXT : ProjectNavigationDirection.PREVIOUS;
    }


    private void observeExternalProjectChange (final String identity)
    {
        this.observedProjectIdentity = identity;
        this.previousUnavailable = false;
        this.nextUnavailable = false;
    }


    private boolean commandPending ()
    {
        return this.pending != null || this.remoteTransport != null;
    }


    private MasterSnapshot captureMasterSnapshot ()
    {
        final String identity = this.currentIdentity ();
        if (identity.isBlank ())
            return MasterSnapshot.empty ();
        final MixerMeterLevels meterLevels = MixerMeterLevels.capture (this.masterTrack);
        return new MasterSnapshot (
            true,
            identity,
            this.project.getName (),
            this.application.isEngineActive (),
            !this.previousUnavailable,
            !this.nextUnavailable,
            this.commandPending (),
            this.project.isDirty (),
            this.masterTrack.getName (),
            toRgb (this.masterTrack.getColor ()),
            this.masterTrack.isActivated (),
            this.masterTrack.isSelected (),
            this.cursorTrack.isPinned (),
            this.valueChanger.toDisplayValue (meterLevels.left ()),
            this.valueChanger.toDisplayValue (meterLevels.right ()));
    }


    private ProjectSnapshot captureProjectSnapshot ()
    {
        final String identity = this.currentIdentity ();
        if (identity.isBlank ())
            return ProjectSnapshot.empty ();
        return new ProjectSnapshot (
            true,
            identity,
            this.project.getName (),
            this.application.isEngineActive (),
            !this.previousUnavailable,
            !this.nextUnavailable,
            this.commandPending ());
    }


    private static RgbColor toRgb (final ColorEx color)
    {
        return new RgbColor ((int) Math.round (255 * color.getRed ()), (int) Math.round (255 * color.getGreen ()), (int) Math.round (255 * color.getBlue ()));
    }


    private enum IgnoredAction implements ControllerBridge.PreparedAction
    {
        INSTANCE
    }


    private record PreparedNavigation (String expectedProjectIdentity, ProjectNavigationDirection direction) implements ControllerBridge.PreparedAction
    {
    }


    private record PreparedEngine (String expectedProjectIdentity, boolean active) implements ControllerBridge.PreparedAction
    {
    }


    private record PreparedFile (String expectedProjectIdentity, ProjectFileAction action) implements ControllerBridge.PreparedAction
    {
    }


    private record PreparedProjectTransport (String originIdentity, String targetIdentity, TransportState state, boolean enabled) implements ControllerBridge.PreparedAction
    {
    }


    private enum RemoteStage
    {
        SEARCHING,
        WAITING_FOR_TRANSPORT,
        RETURNING,
        FAILED_RETURN
    }


    private enum PendingKind
    {
        ENGINE,
        NAVIGATION
    }


    private static final class RemoteTransportCommand
    {
        private final String originIdentity;
        private final String targetIdentity;
        private final TransportState state;
        private final boolean enabled;
        private final List<PathStep> path = new ArrayList<> ();
        private String expectedCurrentIdentity;
        private ProjectNavigationDirection searchDirection = ProjectNavigationDirection.PREVIOUS;
        private RemoteStage stage = RemoteStage.SEARCHING;
        private int navigationSteps;
        private int transportAcknowledgementAge;


        private RemoteTransportCommand (final String originIdentity, final String targetIdentity, final TransportState state, final boolean enabled)
        {
            this.originIdentity = originIdentity;
            this.targetIdentity = targetIdentity;
            this.state = state;
            this.enabled = enabled;
            this.expectedCurrentIdentity = originIdentity;
        }
    }


    private static final class PendingCommand
    {
        private final String originIdentity;
        private final ProjectNavigationDirection direction;
        private final PendingKind kind;
        private final boolean desiredEngineActive;
        private int age;
        private String targetIdentity;


        private PendingCommand (final String originIdentity, final ProjectNavigationDirection direction, final PendingKind kind, final boolean desiredEngineActive)
        {
            this.originIdentity = originIdentity;
            this.direction = direction;
            this.kind = kind;
            this.desiredEngineActive = desiredEngineActive;
        }


        private static PendingCommand navigation (final String originIdentity, final ProjectNavigationDirection direction)
        {
            return new PendingCommand (originIdentity, direction, PendingKind.NAVIGATION, false);
        }


        private static PendingCommand engine (final String originIdentity, final boolean desiredEngineActive)
        {
            return new PendingCommand (originIdentity, null, PendingKind.ENGINE, desiredEngineActive);
        }
    }


    private record PathStep (String fromIdentity, String toIdentity, ProjectNavigationDirection direction)
    {
    }
}
