// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.runtime;

import de.mossgrabers.pull.core.api.ControllerCore;
import de.mossgrabers.pull.core.api.ControllerSnapshot;
import de.mossgrabers.pull.core.api.CoreResult;
import de.mossgrabers.pull.core.api.ParameterSlot;
import de.mossgrabers.pull.core.api.MixerControlsSnapshot;
import de.mossgrabers.pull.core.api.StateEnvelope;
import de.mossgrabers.pull.core.api.effect.CoreEffect;
import de.mossgrabers.pull.core.api.event.ControllerInputEvent;
import de.mossgrabers.pull.core.api.event.ControllerActionEvent;
import de.mossgrabers.pull.core.api.event.CoreEvent;
import de.mossgrabers.pull.core.api.event.InputKind;
import de.mossgrabers.pull.core.api.event.ParameterMutationEvent;
import de.mossgrabers.pull.core.api.output.MixerControlsDisplay;
import de.mossgrabers.pull.core.runtime.view.DefaultWorkspace;
import de.mossgrabers.pull.core.runtime.view.VsLiveWorkspace;
import de.mossgrabers.pull.core.runtime.view.MasterWorkspace;
import de.mossgrabers.pull.core.runtime.view.MixerDisplayScene;
import de.mossgrabers.pull.core.runtime.view.ProjectPlaybackCoordinator;
import de.mossgrabers.pull.core.runtime.view.WorkspaceSelection;
import de.mossgrabers.pull.core.view.CompiledWorkspace;
import de.mossgrabers.pull.core.view.ResolvedControllerAction;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;


/**
 * Reloadable Pull behavior. The stable shell owns physical mappings and all effect execution.
 */
final class PullControllerCore implements ControllerCore
{
    private Map<WorkspaceSelection.Id, CompiledWorkspace> workspaces = Map.of ();
    private WorkspaceSelection                             selection;
    private CompiledWorkspace                              workspace;
    private CompiledWorkspace                              masterWorkspace;
    private ProjectPlaybackCoordinator                     playbackCoordinator;
    private boolean                                        masterLayoutObserved;
    private long                                           masterEntryWorkspaceRequest;
    private final SnapbackSession                          snapback = new SnapbackSession ();
    private Lifecycle                                      lifecycle = Lifecycle.NEW;


    /** {@inheritDoc} */
    @Override
    public CoreResult start (final ControllerSnapshot snapshot, final Optional<StateEnvelope> previousState)
    {
        Objects.requireNonNull (snapshot, "snapshot");
        Objects.requireNonNull (previousState, "previousState");
        if (this.lifecycle != Lifecycle.NEW)
            throw new IllegalStateException ("Core can only be started once");

        final RestoredState restoredState = restoreState (previousState);
        this.selection = new WorkspaceSelection (restoredState.workspace ());
        this.playbackCoordinator = new ProjectPlaybackCoordinator ();
        this.playbackCoordinator.restoreEngineOwner (restoredState.engineOwnerIdentity (), restoredState.engineOwnerPlaying ());
        final Map<WorkspaceSelection.Id, CompiledWorkspace> compiled = new EnumMap<> (WorkspaceSelection.Id.class);
        compiled.put (WorkspaceSelection.Id.DEFAULT, DefaultWorkspace.create (this.selection, this.playbackCoordinator));
        compiled.put (WorkspaceSelection.Id.VS_LIVE, VsLiveWorkspace.create (this.selection, this.playbackCoordinator));
        this.workspaces = Map.copyOf (compiled);
        this.masterWorkspace = MasterWorkspace.create (this.selection, this.playbackCoordinator);
        this.workspace = this.desiredWorkspace (snapshot);
        this.lifecycle = Lifecycle.RUNNING;
        this.snapback.start (snapshot);
        return this.withExecutionRequirements (this.snapback.decorate (this.workspace.activate (snapshot), List.of ()));
    }


    /** {@inheritDoc} */
    @Override
    public CoreResult handle (final CoreEvent event, final ControllerSnapshot snapshot)
    {
        this.requireRunning ();
        Objects.requireNonNull (event, "event");
        Objects.requireNonNull (snapshot, "snapshot");
        final CompiledWorkspace desiredWorkspace = this.desiredWorkspace (snapshot);
        if (desiredWorkspace != this.workspace)
        {
            this.workspace = desiredWorkspace;
            this.workspace.activate (snapshot);
        }
        final ParameterSlot mutationSlot;
        if (event instanceof final ParameterMutationEvent mutation)
            mutationSlot = this.workspace.parameterSlotOrNull (mutation.controlId ());
        else if (event instanceof final ControllerInputEvent input && input.kind () == InputKind.RELATIVE)
            mutationSlot = this.workspace.parameterSlotOrNull (input.controlId ());
        else
            mutationSlot = null;
        SnapbackSession.Update update = this.snapback.handle (event, snapshot, mutationSlot);
        CoreResult currentResult;
        final ResolvedControllerAction action;
        if (event instanceof final ControllerInputEvent input)
            action = this.workspace.resolveAction (input, snapshot);
        else if (event instanceof final ControllerActionEvent semanticAction)
            action = ResolvedControllerAction.stable (semanticAction.intent ());
        else
            action = null;

        if (action != null)
        {
            final SnapbackSession.Update actionUpdate = this.snapback.handleAction (action, snapshot);
            update = mergeUpdates (update, actionUpdate);
            currentResult = actionUpdate.intercepted () ? this.workspace.activate (snapshot) : this.dispatchActionToWorkspace (action, snapshot);
        }
        else
            currentResult = update.intercepted () ? this.workspace.activate (snapshot) : this.workspace.handle (event, snapshot);

        final List<CoreEffect> effects = new ArrayList<> (currentResult.effects ());
        for (final ResolvedControllerAction released: update.releasedActions ())
        {
            currentResult = this.dispatchActionToWorkspace (released, snapshot);
            effects.addAll (currentResult.effects ());
        }
        return this.withExecutionRequirements (this.snapback.decorate (withEffects (currentResult, effects), update.effects ()));
    }


    /** {@inheritDoc} */
    @Override
    public StateEnvelope checkpoint ()
    {
        this.requireRunning ();
        final byte [] owner = this.playbackCoordinator.engineOwnerIdentity ().getBytes (StandardCharsets.UTF_8);
        final ByteBuffer payload = ByteBuffer.allocate (Integer.BYTES + 2 + owner.length);
        payload.put ((byte) (this.selection.active () == WorkspaceSelection.Id.VS_LIVE ? 1 : 0));
        payload.put ((byte) (this.playbackCoordinator.engineOwnerPlaying () ? 1 : 0));
        payload.putInt (owner.length);
        payload.put (owner);
        return new StateEnvelope (PullCoreProvider.STATE_SCHEMA, PullCoreProvider.STATE_SCHEMA_VERSION, payload.array ());
    }


    /** {@inheritDoc} */
    @Override
    public MixerControlsDisplay renderMixerControls (final MixerControlsSnapshot snapshot)
    {
        return MixerDisplayScene.render (Objects.requireNonNull (snapshot, "snapshot"));
    }


    private static RestoredState restoreState (final Optional<StateEnvelope> previousState)
    {
        if (previousState.isEmpty ())
            return RestoredState.empty ();
        final byte [] payload = previousState.get ().payload ();
        if (payload.length < Integer.BYTES + 2)
            return RestoredState.empty ();
        final ByteBuffer buffer = ByteBuffer.wrap (payload);
        final WorkspaceSelection.Id workspace = buffer.get () == 1 ? WorkspaceSelection.Id.VS_LIVE : WorkspaceSelection.Id.DEFAULT;
        final boolean playing = buffer.get () == 1;
        final int ownerLength = buffer.getInt ();
        if (ownerLength < 0 || ownerLength > 1024 || ownerLength != buffer.remaining ())
            return new RestoredState (workspace, "", false);
        final byte [] owner = new byte [ownerLength];
        buffer.get (owner);
        return new RestoredState (workspace, new String (owner, StandardCharsets.UTF_8), playing);
    }


    private static CoreResult transitionTo (final List<CoreEffect> departingEffects, final CoreResult activeResult)
    {
        if (departingEffects.isEmpty ())
            return activeResult;

        final List<CoreEffect> effects = new ArrayList<> (departingEffects);
        effects.addAll (activeResult.effects ());
        return new CoreResult (
            activeResult.desiredOutput (),
            activeResult.desiredInputRoutes (),
            activeResult.desiredBridgeSubscriptions (),
            activeResult.desiredClipBindings (),
            activeResult.desiredControllerWorkspace (),
            activeResult.desiredControllerActions (),
            activeResult.desiredParameterBanks (),
            activeResult.desiredParameterInteraction (),
            effects);
    }


    private CoreResult dispatchActionToWorkspace (final ResolvedControllerAction action, final ControllerSnapshot snapshot)
    {
        final CoreResult currentResult = this.workspace.handleAction (action, snapshot);
        final CompiledWorkspace selectedWorkspace = this.desiredWorkspace (snapshot);
        if (selectedWorkspace == this.workspace)
            return currentResult;

        this.workspace = selectedWorkspace;
        return transitionTo (currentResult.effects (), this.workspace.activate (snapshot));
    }


    private CompiledWorkspace desiredWorkspace (final ControllerSnapshot snapshot)
    {
        final String mode = snapshot.bridge ().layout ().modeId ();
        final boolean masterLayout = "MASTER".equals (mode) || "MASTER_TEMP".equals (mode);
        if (!masterLayout)
        {
            this.masterLayoutObserved = false;
            return this.workspaces.get (this.selection.active ());
        }

        if (!this.masterLayoutObserved)
        {
            this.masterLayoutObserved = true;
            this.masterEntryWorkspaceRequest = this.selection.requestSequence ();
        }
        return this.selection.requestSequence () == this.masterEntryWorkspaceRequest ? this.masterWorkspace : this.workspaces.get (this.selection.active ());
    }


    private static CoreResult withEffects (final CoreResult result, final List<CoreEffect> effects)
    {
        return new CoreResult (
            result.desiredOutput (),
            result.desiredInputRoutes (),
            result.desiredBridgeSubscriptions (),
            result.desiredClipBindings (),
            result.desiredControllerWorkspace (),
            result.desiredControllerActions (),
            result.desiredParameterBanks (),
            result.desiredParameterInteraction (),
            result.executionRequirements (),
            effects);
    }


    private CoreResult withExecutionRequirements (final CoreResult result)
    {
        return new CoreResult (
            result.desiredOutput (),
            result.desiredInputRoutes (),
            result.desiredBridgeSubscriptions (),
            result.desiredClipBindings (),
            result.desiredControllerWorkspace (),
            result.desiredControllerActions (),
            result.desiredParameterBanks (),
            result.desiredParameterInteraction (),
            this.playbackCoordinator.executionRequirements (),
            result.effects ());
    }


    private static SnapbackSession.Update mergeUpdates (final SnapbackSession.Update left, final SnapbackSession.Update right)
    {
        final List<ResolvedControllerAction> released = new ArrayList<> (left.releasedActions ());
        released.addAll (right.releasedActions ());
        final List<CoreEffect> effects = new ArrayList<> (left.effects ());
        effects.addAll (right.effects ());
        return new SnapbackSession.Update (left.intercepted () || right.intercepted (), released, effects);
    }


    private void requireRunning ()
    {
        if (this.lifecycle != Lifecycle.RUNNING)
            throw new IllegalStateException ("Core is not running");
    }


    private enum Lifecycle
    {
        NEW,
        RUNNING
    }


    private record RestoredState (WorkspaceSelection.Id workspace, String engineOwnerIdentity, boolean engineOwnerPlaying)
    {
        private static RestoredState empty ()
        {
            return new RestoredState (WorkspaceSelection.Id.DEFAULT, "", false);
        }
    }
}
