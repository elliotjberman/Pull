// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.shell.runtime;

import de.mossgrabers.framework.controller.color.ColorEx;
import de.mossgrabers.framework.controller.valuechanger.IValueChanger;
import de.mossgrabers.framework.daw.IApplication;
import de.mossgrabers.framework.daw.IModel;
import de.mossgrabers.framework.daw.IProject;
import de.mossgrabers.framework.daw.data.ICursorTrack;
import de.mossgrabers.framework.daw.data.IMasterTrack;
import de.mossgrabers.pull.core.api.MasterSnapshot;
import de.mossgrabers.pull.core.api.ProjectSnapshot;
import de.mossgrabers.pull.core.api.CoreExecutionRequirements;
import de.mossgrabers.pull.core.api.effect.CoreEffect;
import de.mossgrabers.pull.core.api.effect.NavigateProjectEffect;
import de.mossgrabers.pull.core.api.effect.ProjectFileAction;
import de.mossgrabers.pull.core.api.effect.ProjectFileActionEffect;
import de.mossgrabers.pull.core.api.effect.ProjectNavigationDirection;
import de.mossgrabers.pull.core.api.effect.SetProjectEngineEffect;
import de.mossgrabers.pull.core.api.output.RgbColor;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;


/** Serialized current-project command lane and authoritative lightweight/Master snapshots. */
final class MasterCommandHost
{
    private static final int ENGINE_ACKNOWLEDGEMENT_TIMEOUT_TICKS = 8;
    private static final int NAVIGATION_ACKNOWLEDGEMENT_TIMEOUT_TICKS = 100;
    private static final int NAVIGATION_LEASE_PATH_CAPACITY = 32;

    private final IProject project;
    private final IApplication application;
    private final IMasterTrack masterTrack;
    private final ICursorTrack cursorTrack;
    private final IValueChanger valueChanger;

    private boolean previousUnavailable;
    private boolean nextUnavailable;
    private String observedProjectIdentity;
    private PendingCommand pending;
    private NavigationLease navigationLease;
    private boolean navigationLeaseAbandoned;
    private MasterSnapshot snapshot = MasterSnapshot.empty ();
    private ProjectSnapshot projectSnapshot = ProjectSnapshot.empty ();


    MasterCommandHost (final IModel model)
    {
        final IModel checkedModel = Objects.requireNonNull (model, "model");
        this.project = checkedModel.getProject ();
        this.application = checkedModel.getApplication ();
        this.masterTrack = checkedModel.getMasterTrack ();
        this.cursorTrack = checkedModel.getCursorTrack ();
        this.valueChanger = checkedModel.getValueChanger ();
    }


    boolean refresh (final boolean publishMaster, final boolean publishProject)
    {
        if (publishMaster || publishProject || this.pending != null || this.navigationLease != null)
        {
            this.advancePending ();
            this.advanceNavigationLeaseCleanup ();
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
        return this.pending == null && this.identityMatches (expectedIdentity);
    }


    CoreExecutionRequirements prepareExecutionRequirements (final CoreExecutionRequirements requirements)
    {
        final CoreExecutionRequirements requested = Objects.requireNonNull (requirements, "requirements");
        if (requested.hasProjectNavigationLease ())
        {
            if (this.navigationLease == null)
            {
                if (this.pending != null || !this.identityMatches (requested.projectNavigationOrigin ()))
                    throw new IllegalArgumentException ("Project-navigation lease does not match the idle visible project");
            }
            else if (this.navigationLease.id != requested.projectNavigationLeaseId () || !this.navigationLease.originIdentity.equals (requested.projectNavigationOrigin ()))
                throw new IllegalArgumentException ("Project-navigation lease identity changed before completion");
        }
        else if (this.navigationLease != null && !this.navigationLeaseAbandoned && (this.pending != null || !this.navigationLease.path.isEmpty () || !this.identityMatches (this.navigationLease.originIdentity)))
            throw new IllegalArgumentException ("Project-navigation lease was released before its exact return completed");
        return requested;
    }


    void applyExecutionRequirements (final CoreExecutionRequirements requirements)
    {
        final CoreExecutionRequirements requested = Objects.requireNonNull (requirements, "requirements");
        if (requested.hasProjectNavigationLease ())
        {
            if (this.navigationLease == null)
                this.navigationLease = new NavigationLease (requested.projectNavigationLeaseId (), requested.projectNavigationOrigin ());
            return;
        }
        if (this.navigationLease != null && !this.navigationLeaseAbandoned)
            this.navigationLease = null;
    }


    boolean canReplaceActiveCore ()
    {
        return this.navigationLease == null;
    }


    void abandonActiveCore ()
    {
        if (this.navigationLease != null)
            this.navigationLeaseAbandoned = true;
    }


    ControllerBridge.PreparedAction prepare (final CoreEffect effect)
    {
        if (effect instanceof final NavigateProjectEffect navigation)
        {
            final boolean unavailable = navigation.direction () == ProjectNavigationDirection.PREVIOUS ? this.previousUnavailable : this.nextUnavailable;
            return this.canTargetProject (navigation.expectedProjectIdentity ()) && !unavailable && this.canRecordNavigation (navigation.direction ()) ? new PreparedNavigation (navigation.expectedProjectIdentity (), navigation.direction ()) : IgnoredAction.INSTANCE;
        }
        if (effect instanceof final SetProjectEngineEffect engine)
            return this.canTargetProject (engine.expectedProjectIdentity ()) ? new PreparedEngine (engine.expectedProjectIdentity (), engine.active ()) : IgnoredAction.INSTANCE;
        if (effect instanceof final ProjectFileActionEffect file)
            return this.canTargetProject (file.expectedProjectIdentity ()) ? new PreparedFile (file.expectedProjectIdentity (), file.action ()) : IgnoredAction.INSTANCE;
        return null;
    }


    boolean applyIfOwned (final ControllerBridge.PreparedAction action)
    {
        if (action == IgnoredAction.INSTANCE)
            return true;
        if (action instanceof final PreparedNavigation navigation)
        {
            if (!this.canTargetProject (navigation.expectedProjectIdentity ()))
                return true;
            final long leaseId = this.navigationLease == null ? 0 : this.navigationLease.id;
            this.pending = PendingCommand.navigation (navigation.expectedProjectIdentity (), navigation.direction (), leaseId);
            if (navigation.direction () == ProjectNavigationDirection.PREVIOUS)
                this.project.previous ();
            else
                this.project.next ();
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
        if (action instanceof final PreparedFile file && this.identityMatches (file.expectedProjectIdentity ()))
        {
            if (file.action () == ProjectFileAction.OPEN)
                this.project.load ();
            else
                this.project.save ();
            return true;
        }
        return action instanceof PreparedFile;
    }


    private boolean identityMatches (final String expectedIdentity)
    {
        return !expectedIdentity.isBlank () && expectedIdentity.equals (this.project.getIdentity ());
    }


    private void advancePending ()
    {
        final String currentIdentity = Objects.requireNonNullElse (this.project.getIdentity (), "");
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

        if (this.pending.engineCommand)
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
            {
                if (this.pending.direction == ProjectNavigationDirection.PREVIOUS)
                    this.previousUnavailable = true;
                else
                    this.nextUnavailable = true;
                this.pending = null;
            }
            return;
        }

        if (!currentIdentity.equals (this.pending.targetIdentity))
        {
            this.pending.targetIdentity = currentIdentity;
            return;
        }

        final PendingCommand completed = this.pending;
        this.observedProjectIdentity = currentIdentity;
        this.previousUnavailable = false;
        this.nextUnavailable = false;
        this.pending = null;
        this.recordNavigationStep (completed, currentIdentity);
    }


    private void recordNavigationStep (final PendingCommand command, final String targetIdentity)
    {
        if (this.navigationLease == null || command.engineCommand || command.navigationLeaseId != this.navigationLease.id)
            return;
        final PathStep step = new PathStep (command.originIdentity, targetIdentity, command.direction);
        if (!this.navigationLease.path.isEmpty ())
        {
            final PathStep previous = this.navigationLease.path.getLast ();
            if (previous.fromIdentity.equals (step.toIdentity) && previous.toIdentity.equals (step.fromIdentity) && opposite (previous.direction) == step.direction)
            {
                this.navigationLease.path.removeLast ();
                return;
            }
        }
        this.navigationLease.path.add (step);
    }


    private boolean canRecordNavigation (final ProjectNavigationDirection direction)
    {
        if (this.navigationLease == null || this.navigationLease.path.size () < NAVIGATION_LEASE_PATH_CAPACITY)
            return true;
        final PathStep previous = this.navigationLease.path.getLast ();
        return opposite (previous.direction) == direction;
    }


    private void advanceNavigationLeaseCleanup ()
    {
        if (this.navigationLease == null || !this.navigationLeaseAbandoned || this.pending != null)
            return;
        if (this.navigationLease.path.isEmpty ())
        {
            if (this.identityMatches (this.navigationLease.originIdentity))
            {
                this.navigationLease = null;
                this.navigationLeaseAbandoned = false;
            }
            return;
        }

        final PathStep step = this.navigationLease.path.getLast ();
        if (!this.identityMatches (step.toIdentity))
            return;
        final ProjectNavigationDirection direction = opposite (step.direction);
        this.pending = PendingCommand.navigation (step.toIdentity, direction, this.navigationLease.id);
        if (direction == ProjectNavigationDirection.PREVIOUS)
            this.project.previous ();
        else
            this.project.next ();
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


    private MasterSnapshot captureMasterSnapshot ()
    {
        final String identity = Objects.requireNonNullElse (this.project.getIdentity (), "");
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
            this.pending != null,
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
        final String identity = Objects.requireNonNullElse (this.project.getIdentity (), "");
        if (identity.isBlank ())
            return ProjectSnapshot.empty ();
        return new ProjectSnapshot (
            true,
            identity,
            this.project.getName (),
            this.application.isEngineActive (),
            !this.previousUnavailable,
            !this.nextUnavailable,
            this.pending != null);
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


    private static final class PendingCommand
    {
        private final String originIdentity;
        private final ProjectNavigationDirection direction;
        private final boolean engineCommand;
        private final boolean desiredEngineActive;
        private final long navigationLeaseId;
        private int age;
        private String targetIdentity;


        private PendingCommand (final String originIdentity, final ProjectNavigationDirection direction, final boolean engineCommand, final boolean desiredEngineActive, final long navigationLeaseId)
        {
            this.originIdentity = originIdentity;
            this.direction = direction;
            this.engineCommand = engineCommand;
            this.desiredEngineActive = desiredEngineActive;
            this.navigationLeaseId = navigationLeaseId;
        }


        private static PendingCommand navigation (final String originIdentity, final ProjectNavigationDirection direction, final long navigationLeaseId)
        {
            return new PendingCommand (originIdentity, direction, false, false, navigationLeaseId);
        }


        private static PendingCommand engine (final String originIdentity, final boolean desiredEngineActive)
        {
            return new PendingCommand (originIdentity, null, true, desiredEngineActive, 0);
        }
    }


    private static final class NavigationLease
    {
        private final long id;
        private final String originIdentity;
        private final List<PathStep> path = new ArrayList<> ();


        private NavigationLease (final long id, final String originIdentity)
        {
            this.id = id;
            this.originIdentity = originIdentity;
        }
    }


    private record PathStep (String fromIdentity, String toIdentity, ProjectNavigationDirection direction)
    {
    }
}
