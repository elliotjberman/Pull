// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.runtime;

import de.mossgrabers.pull.core.api.ControllerCore;
import de.mossgrabers.pull.core.api.ControllerActionId;
import de.mossgrabers.pull.core.api.ControllerSnapshot;
import de.mossgrabers.pull.core.api.CoreResult;
import de.mossgrabers.pull.core.api.ParameterSlot;
import de.mossgrabers.pull.core.api.MixerControlsSnapshot;
import de.mossgrabers.pull.core.api.StateEnvelope;
import de.mossgrabers.pull.core.api.effect.CoreEffect;
import de.mossgrabers.pull.core.api.effect.NavigateProjectEffect;
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
import de.mossgrabers.pull.core.runtime.view.SessionView;
import de.mossgrabers.pull.core.runtime.view.StableDestinationWorkspace;
import de.mossgrabers.pull.core.runtime.view.TrackSelectionStripView;
import de.mossgrabers.pull.core.runtime.view.WorkspaceSelection;
import de.mossgrabers.pull.core.view.CompiledWorkspace;
import de.mossgrabers.pull.core.view.ControllerView;
import de.mossgrabers.pull.core.view.RetainedControllerView;
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
    private CompiledWorkspace                              defaultDrumWorkspace;
    private CompiledWorkspace                              defaultSessionWorkspace;
    private CompiledWorkspace                              vsLiveStablePageWorkspace;
    private CompiledWorkspace                              vsLiveTrackMixerWorkspace;
    private WorkspaceSelection                             selection;
    private CompiledWorkspace                              workspace;
    private Map<WorkspaceSelection.Id, CompiledWorkspace> masterWorkspaces = Map.of ();
    private Map<WorkspaceSelection.Destination, CompiledWorkspace> destinationWorkspaces = Map.of ();
    private ProjectPlaybackCoordinator                     playbackCoordinator;
    private boolean                                        masterLayoutObserved;
    private long                                           masterEntryWorkspaceRequest;
    private MasterNavigationLease                          masterNavigationLease;
    private VsLivePage                                     vsLivePage = VsLivePage.DEFAULT;
    private long                                           vsLiveWorkspaceRequest = -1;
    private long                                           vsLivePendingPageAfterGeneration = -1;
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
        this.selection = new WorkspaceSelection (restoredState.workspace (), restoredState.selectedDestination (), restoredState.pendingDestination ());
        this.playbackCoordinator = new ProjectPlaybackCoordinator ();
        this.playbackCoordinator.restoreEngineOwner (restoredState.engineOwnerIdentity (), restoredState.engineOwnerPlaying ());
        final ControllerView retainedSessionView = new RetainedControllerView (SessionView.full ());
        final List<ControllerView> retainedVsLiveGridViews = VsLiveWorkspace.retainedGridViews ();
        final ControllerView retainedVsLiveTrackSelection = new RetainedControllerView (new TrackSelectionStripView ());
        final Map<WorkspaceSelection.Id, CompiledWorkspace> compiled = new EnumMap<> (WorkspaceSelection.Id.class);
        compiled.put (WorkspaceSelection.Id.DEFAULT, DefaultWorkspace.create (this.selection, this.playbackCoordinator));
        compiled.put (WorkspaceSelection.Id.VS_LIVE, VsLiveWorkspace.create (this.selection, this.playbackCoordinator, retainedVsLiveTrackSelection, retainedVsLiveGridViews));
        this.workspaces = Map.copyOf (compiled);
        this.defaultDrumWorkspace = DefaultWorkspace.createDrum (this.selection, this.playbackCoordinator);
        this.defaultSessionWorkspace = StableDestinationWorkspace.selectedSession (this.selection, this.playbackCoordinator, retainedSessionView);
        this.vsLiveStablePageWorkspace = VsLiveWorkspace.createWithStablePage (this.selection, this.playbackCoordinator, retainedVsLiveGridViews);
        this.vsLiveTrackMixerWorkspace = VsLiveWorkspace.createWithTrackMixerPage (this.selection, this.playbackCoordinator, retainedVsLiveTrackSelection, retainedVsLiveGridViews);
        final Map<WorkspaceSelection.Id, CompiledWorkspace> compiledMaster = new EnumMap<> (WorkspaceSelection.Id.class);
        for (final WorkspaceSelection.Id background: WorkspaceSelection.Id.values ())
            compiledMaster.put (background, MasterWorkspace.create (this.selection, this.playbackCoordinator, background, retainedVsLiveGridViews));
        this.masterWorkspaces = Map.copyOf (compiledMaster);
        this.destinationWorkspaces = Map.of (
            WorkspaceSelection.Destination.SESSION, StableDestinationWorkspace.session (this.selection, this.playbackCoordinator, retainedSessionView),
            WorkspaceSelection.Destination.NOTE, StableDestinationWorkspace.note (this.selection, this.playbackCoordinator));
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
            currentResult = actionUpdate.intercepted () ? this.workspace.activate (snapshot) : this.dispatchActionToWorkspace (action, snapshot, false);
        }
        else
            currentResult = update.intercepted () ? this.workspace.activate (snapshot) : this.workspace.handle (event, snapshot);

        currentResult = this.transitionToSelectedWorkspace (currentResult, snapshot);
        final List<CoreEffect> effects = new ArrayList<> (currentResult.effects ());
        for (final ResolvedControllerAction released: update.releasedActions ())
        {
            currentResult = this.dispatchActionToWorkspace (released, snapshot, true);
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
        final ByteBuffer payload = ByteBuffer.allocate (Integer.BYTES + 4 + owner.length);
        payload.put ((byte) (this.selection.active () == WorkspaceSelection.Id.VS_LIVE ? 1 : 0));
        payload.put ((byte) (this.playbackCoordinator.engineOwnerPlaying () ? 1 : 0));
        payload.put ((byte) this.selection.selectedDestination ().ordinal ());
        payload.put ((byte) this.selection.pendingDestination ().ordinal ());
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
        if (payload.length < Integer.BYTES + 4)
            return RestoredState.empty ();
        final ByteBuffer buffer = ByteBuffer.wrap (payload);
        final WorkspaceSelection.Id workspace = buffer.get () == 1 ? WorkspaceSelection.Id.VS_LIVE : WorkspaceSelection.Id.DEFAULT;
        final boolean playing = buffer.get () == 1;
        final int selectedDestinationOrdinal = Byte.toUnsignedInt (buffer.get ());
        final int pendingDestinationOrdinal = Byte.toUnsignedInt (buffer.get ());
        if (selectedDestinationOrdinal >= WorkspaceSelection.Destination.values ().length || pendingDestinationOrdinal >= WorkspaceSelection.Destination.values ().length)
            return RestoredState.empty ();
        final WorkspaceSelection.Destination selectedDestination = WorkspaceSelection.Destination.values ()[selectedDestinationOrdinal];
        final WorkspaceSelection.Destination pendingDestination = WorkspaceSelection.Destination.values ()[pendingDestinationOrdinal];
        if (pendingDestination != WorkspaceSelection.Destination.NONE && pendingDestination != selectedDestination)
            return RestoredState.empty ();
        final int ownerLength = buffer.getInt ();
        if (ownerLength < 0 || ownerLength > 1024 || ownerLength != buffer.remaining ())
            return new RestoredState (workspace, selectedDestination, pendingDestination, "", false);
        final byte [] owner = new byte [ownerLength];
        buffer.get (owner);
        return new RestoredState (workspace, selectedDestination, pendingDestination, new String (owner, StandardCharsets.UTF_8), playing);
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
            activeResult.desiredControllerState (),
            activeResult.desiredNoteRepeat (),
            activeResult.desiredControllerActions (),
            activeResult.desiredParameterBanks (),
            activeResult.desiredParameterInteraction (),
            effects);
    }


    private CoreResult dispatchActionToWorkspace (final ResolvedControllerAction action, final ControllerSnapshot snapshot, final boolean awaitStableReadback)
    {
        final List<CoreEffect> effects = this.workspace.dispatchAction (action, snapshot);
        this.observeMasterNavigationAction (effects);
        this.observeMasterPageExitAction (action);
        this.observeVsLivePageAction (action, snapshot, awaitStableReadback);
        final CompiledWorkspace selectedWorkspace = this.desiredWorkspace (snapshot);
        if (selectedWorkspace != this.workspace)
            this.workspace = selectedWorkspace;
        return transitionTo (effects, this.workspace.activate (snapshot));
    }


    private CoreResult transitionToSelectedWorkspace (final CoreResult currentResult, final ControllerSnapshot snapshot)
    {
        final CompiledWorkspace selectedWorkspace = this.desiredWorkspace (snapshot);
        if (selectedWorkspace == this.workspace)
            return currentResult;

        this.workspace = selectedWorkspace;
        return transitionTo (currentResult.effects (), this.workspace.activate (snapshot));
    }


    private CompiledWorkspace desiredWorkspace (final ControllerSnapshot snapshot)
    {
        this.selection.observe (snapshot.bridge ().layout ());
        this.selection.observe (snapshot.bridge ().noteView ());
        this.observeVsLivePageReadback (snapshot.bridge ().layout ());
        final CompiledWorkspace selectedWorkspace = this.selectedWorkspace (snapshot);
        final String mode = snapshot.bridge ().layout ().modeId ();
        final boolean masterLayout = "MASTER".equals (mode) || "MASTER_TEMP".equals (mode);
        if (this.masterNavigationLease != null)
        {
            if (this.selection.requestSequence () != this.masterNavigationLease.workspaceRequest ())
                this.masterNavigationLease = null;
            else
                return this.masterWorkspaces.get (this.selection.active ());
        }
        if (!masterLayout)
        {
            this.masterLayoutObserved = false;
            return selectedWorkspace;
        }
        if (!this.masterLayoutObserved)
        {
            this.masterLayoutObserved = true;
            this.masterEntryWorkspaceRequest = this.selection.requestSequence ();
        }
        return this.selection.requestSequence () == this.masterEntryWorkspaceRequest ? this.masterWorkspaces.get (this.selection.active ()) : selectedWorkspace;
    }


    private void observeMasterNavigationAction (final List<CoreEffect> effects)
    {
        for (final CoreEffect effect: effects)
        {
            if (effect instanceof NavigateProjectEffect)
            {
                this.masterNavigationLease = new MasterNavigationLease (this.selection.requestSequence ());
                return;
            }
        }
    }


    private void observeMasterPageExitAction (final ResolvedControllerAction action)
    {
        if (this.masterNavigationLease == null)
            return;
        switch (action.intent ().action ())
        {
            case SELECT_PARAMETER_CONTEXT, SELECT_PARAMETER_PAGE, SWITCH_PARAMETER_CONTEXT, SWITCH_WORKSPACE, SELECT_NOTE_LAYOUT -> this.masterNavigationLease = null;
            default -> {
                // Target navigation and Master-owned actions retain the explicitly selected page.
            }
        }
    }


    private CompiledWorkspace selectedWorkspace (final ControllerSnapshot snapshot)
    {
        if (this.selection.active () != WorkspaceSelection.Id.VS_LIVE)
        {
            this.vsLivePage = VsLivePage.DEFAULT;
            this.vsLivePendingPageAfterGeneration = -1;
            this.vsLiveWorkspaceRequest = this.selection.requestSequence ();
        }
        else if (this.vsLiveWorkspaceRequest != this.selection.requestSequence ())
        {
            // Shift+Session selects the declared composite, including its default Project Macro
            // page. It is an idempotent workspace selection, not a request to retain a stale page.
            this.vsLivePage = VsLivePage.DEFAULT;
            this.vsLivePendingPageAfterGeneration = -1;
            this.vsLiveWorkspaceRequest = this.selection.requestSequence ();
        }
        final WorkspaceSelection.Destination destination = this.selection.pendingDestination ();
        if (destination != WorkspaceSelection.Destination.NONE)
            return this.destinationWorkspaces.get (destination);
        if (this.selection.active () == WorkspaceSelection.Id.DEFAULT && this.selection.selectedDestination () == WorkspaceSelection.Destination.SESSION)
            return this.defaultSessionWorkspace;
        if (this.selection.active () == WorkspaceSelection.Id.DEFAULT && snapshot.bridge ().layout ().drumLayoutActive ())
            return this.defaultDrumWorkspace;
        if (this.selection.active () == WorkspaceSelection.Id.VS_LIVE && this.vsLivePage == VsLivePage.TRACK_MIXER)
            return this.vsLiveTrackMixerWorkspace;
        if (this.selection.active () == WorkspaceSelection.Id.VS_LIVE && this.vsLivePage == VsLivePage.STABLE)
            return this.vsLiveStablePageWorkspace;
        return this.workspaces.get (this.selection.active ());
    }


    private void observeVsLivePageAction (final ResolvedControllerAction action, final ControllerSnapshot snapshot, final boolean awaitStableReadback)
    {
        if (this.selection.active () != WorkspaceSelection.Id.VS_LIVE || action.intent ().action () != ControllerActionId.SWITCH_PARAMETER_CONTEXT)
            return;

        final de.mossgrabers.pull.core.api.ControllerLayoutSnapshot layout = snapshot.bridge ().layout ();
        if (awaitStableReadback)
        {
            // Snapback released the semantic action before the corresponding stable command. The
            // shell runs that deferred command only after this result retires the action barrier.
            this.vsLivePendingPageAfterGeneration = layout.generation ();
            return;
        }

        // An ordinary stable command runs before its semantic observation is delivered, so this
        // snapshot already contains the page it selected.
        this.selectVsLivePage (layout.modeId ());
    }


    private void observeVsLivePageReadback (final de.mossgrabers.pull.core.api.ControllerLayoutSnapshot layout)
    {
        if (this.selection.active () != WorkspaceSelection.Id.VS_LIVE || this.vsLivePendingPageAfterGeneration < 0 || layout.generation () <= this.vsLivePendingPageAfterGeneration)
            return;
        this.vsLivePendingPageAfterGeneration = -1;
        this.selectVsLivePage (layout.modeId ());
    }


    private void selectVsLivePage (final String mode)
    {
        // Incidental mode changes used to neutralize a selected-track Note route never call this
        // method and therefore cannot be mistaken for page input.
        this.vsLivePendingPageAfterGeneration = -1;
        if ("TRACK".equals (mode))
            this.vsLivePage = VsLivePage.TRACK_MIXER;
        else if ("WORKSPACE".equals (mode))
            this.vsLivePage = VsLivePage.DEFAULT;
        else if (!"MASTER".equals (mode) && !"MASTER_TEMP".equals (mode))
            this.vsLivePage = VsLivePage.STABLE;
    }


    private static CoreResult withEffects (final CoreResult result, final List<CoreEffect> effects)
    {
        return new CoreResult (
            result.desiredOutput (),
            result.desiredInputRoutes (),
            result.desiredBridgeSubscriptions (),
            result.desiredClipBindings (),
            result.desiredControllerState (),
            result.desiredNoteRepeat (),
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
            result.desiredControllerState (),
            result.desiredNoteRepeat (),
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


    private record RestoredState (WorkspaceSelection.Id workspace, WorkspaceSelection.Destination selectedDestination, WorkspaceSelection.Destination pendingDestination, String engineOwnerIdentity, boolean engineOwnerPlaying)
    {
        private static RestoredState empty ()
        {
            return new RestoredState (WorkspaceSelection.Id.DEFAULT, WorkspaceSelection.Destination.NONE, WorkspaceSelection.Destination.NONE, "", false);
        }
    }


    private enum VsLivePage
    {
        DEFAULT,
        TRACK_MIXER,
        STABLE
    }


    /** Keeps Master selected after its own project navigation until an explicit page request. */
    private record MasterNavigationLease (long workspaceRequest)
    {
    }
}
