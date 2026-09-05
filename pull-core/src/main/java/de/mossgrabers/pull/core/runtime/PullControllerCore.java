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
import de.mossgrabers.pull.core.runtime.view.DrumControlPadView;
import de.mossgrabers.pull.core.runtime.view.ControllerLevelViews;
import de.mossgrabers.pull.core.runtime.view.VsLiveWorkspace;
import de.mossgrabers.pull.core.runtime.view.MasterWorkspace;
import de.mossgrabers.pull.core.runtime.view.MixerDisplayScene;
import de.mossgrabers.pull.core.runtime.view.ProjectPlaybackCoordinator;
import de.mossgrabers.pull.core.runtime.view.SessionView;
import de.mossgrabers.pull.core.runtime.view.SessionStopGesture;
import de.mossgrabers.pull.core.runtime.view.StableDestinationWorkspace;
import de.mossgrabers.pull.core.runtime.view.TrackMixerPageState;
import de.mossgrabers.pull.core.runtime.view.TrackMixerControlsView;
import de.mossgrabers.pull.core.runtime.view.CurrentTrackFooterView;
import de.mossgrabers.pull.core.api.effect.SelectControllerModeEffect;
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
import java.util.IdentityHashMap;
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
    private CompiledWorkspace                              defaultDrumLegacyWorkspace;
    private CompiledWorkspace                              masterDrumRawWorkspace;
    private CompiledWorkspace                              masterDrumLegacyWorkspace;
    private CompiledWorkspace                              vsLiveStablePageWorkspace;
    private CompiledWorkspace                              vsLiveTrackMixerWorkspace;
    private Map<CompiledWorkspace, CompiledWorkspace>      trackPageWorkspaces = Map.of ();
    private TrackMixerPageState                            trackMixerPage;
    private boolean                                        normalTrackPageSelected;
    private long                                           normalPageWorkspaceRequest = -1;
    private PendingPageSelection                           pendingPageSelection;
    private WorkspaceSelection                             selection;
    private CompiledWorkspace                              workspace;
    private Map<CompiledWorkspace, CompiledWorkspace>      masterWorkspaces = Map.of ();
    private Map<WorkspaceSelection.Destination, CompiledWorkspace> destinationWorkspaces = Map.of ();
    private ProjectPlaybackCoordinator                     playbackCoordinator;
    private boolean                                        masterLayoutObserved;
    private long                                           masterEntryWorkspaceRequest;
    private MasterNavigationLease                          masterNavigationLease;
    private CompiledWorkspace                              activeMasterWorkspace;
    private VsLivePage                                     vsLivePage = VsLivePage.DEFAULT;
    private long                                           vsLiveWorkspaceRequest = -1;
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
        this.trackMixerPage = new TrackMixerPageState (restoredState.inputOutputSelected (), restoredState.sendOffset ());
        this.normalTrackPageSelected = restoredState.pageStatePresent () ? restoredState.normalTrackPageSelected () : "TRACK".equals (snapshot.bridge ().layout ().modeId ());
        this.normalPageWorkspaceRequest = this.selection.requestSequence ();
        this.vsLivePage = restoredState.vsLivePage ();
        this.vsLiveWorkspaceRequest = this.selection.requestSequence ();
        this.playbackCoordinator = new ProjectPlaybackCoordinator ();
        this.playbackCoordinator.restoreEngineOwner (restoredState.engineOwnerIdentity (), restoredState.engineOwnerPlaying ());
        final ControllerView drumControlPadView = new DrumControlPadView ();
        final ControllerLevelViews controllerViews = new ControllerLevelViews (this.selection, this.playbackCoordinator);
        final SessionStopGesture fullSessionStopGesture = new SessionStopGesture ();
        final ControllerView retainedSessionView = new RetainedControllerView (SessionView.full (fullSessionStopGesture));
        final SessionStopGesture vsLiveStopGesture = new SessionStopGesture ();
        final List<ControllerView> retainedVsLiveGridViews = VsLiveWorkspace.retainedGridViews (vsLiveStopGesture, drumControlPadView);
        final ControllerView retainedVsLiveTrackSelection = new RetainedControllerView (new TrackSelectionStripView (vsLiveStopGesture));
        final List<ControllerView> retainedDefaultDrumViews = DefaultWorkspace.retainedDrumViews (drumControlPadView);
        final List<ControllerView> normalTrackPage = List.of (
            new RetainedControllerView (new TrackMixerControlsView (this.trackMixerPage, controllerViews.parameterTouches (), true)),
            new RetainedControllerView (new CurrentTrackFooterView (controllerViews.buttonGestures (), fullSessionStopGesture)));
        final ControllerView vsLiveTrackPage = new RetainedControllerView (new TrackMixerControlsView (this.trackMixerPage, controllerViews.parameterTouches (), false));
        final Map<WorkspaceSelection.Id, CompiledWorkspace> compiled = new EnumMap<> (WorkspaceSelection.Id.class);
        compiled.put (WorkspaceSelection.Id.DEFAULT, DefaultWorkspace.create (controllerViews));
        compiled.put (WorkspaceSelection.Id.VS_LIVE, VsLiveWorkspace.create (controllerViews, retainedVsLiveTrackSelection, retainedVsLiveGridViews));
        this.workspaces = Map.copyOf (compiled);
        this.defaultDrumWorkspace = DefaultWorkspace.createDrum (controllerViews, retainedDefaultDrumViews, true);
        this.defaultDrumLegacyWorkspace = DefaultWorkspace.createDrum (controllerViews, retainedDefaultDrumViews, false);
        this.defaultSessionWorkspace = StableDestinationWorkspace.selectedSession (controllerViews, retainedSessionView);
        this.vsLiveStablePageWorkspace = VsLiveWorkspace.createWithStablePage (controllerViews, retainedVsLiveGridViews);
        this.vsLiveTrackMixerWorkspace = VsLiveWorkspace.createWithTrackMixerPage (controllerViews, retainedVsLiveTrackSelection, retainedVsLiveGridViews, vsLiveTrackPage);
        this.trackPageWorkspaces = Map.of (
            compiled.get (WorkspaceSelection.Id.DEFAULT), DefaultWorkspace.create (controllerViews, normalTrackPage),
            this.defaultDrumWorkspace, DefaultWorkspace.createDrum (controllerViews, retainedDefaultDrumViews, true, normalTrackPage),
            this.defaultDrumLegacyWorkspace, DefaultWorkspace.createDrum (controllerViews, retainedDefaultDrumViews, false, normalTrackPage),
            this.defaultSessionWorkspace, StableDestinationWorkspace.selectedSession (controllerViews, retainedSessionView, normalTrackPage));
        this.destinationWorkspaces = Map.of (
            WorkspaceSelection.Destination.SESSION, StableDestinationWorkspace.session (this.selection, controllerViews, retainedSessionView, normalTrackPage),
            WorkspaceSelection.Destination.NOTE, StableDestinationWorkspace.note (controllerViews, normalTrackPage));
        final Map<CompiledWorkspace, CompiledWorkspace> compiledMaster = new IdentityHashMap<> ();
        compiledMaster.put (compiled.get (WorkspaceSelection.Id.DEFAULT), MasterWorkspace.create (controllerViews, de.mossgrabers.pull.core.api.SessionBankShape.empty (), List.of (), true, false));
        this.masterDrumRawWorkspace = MasterWorkspace.create (controllerViews, de.mossgrabers.pull.core.api.SessionBankShape.empty (), retainedDefaultDrumViews, true, true);
        this.masterDrumLegacyWorkspace = MasterWorkspace.create (controllerViews, de.mossgrabers.pull.core.api.SessionBankShape.empty (), retainedDefaultDrumViews, true, false);
        compiledMaster.put (this.defaultDrumWorkspace, this.masterDrumRawWorkspace);
        compiledMaster.put (this.defaultDrumLegacyWorkspace, this.masterDrumLegacyWorkspace);
        final CompiledWorkspace masterSession = MasterWorkspace.create (controllerViews, StableDestinationWorkspace.SESSION_BANK, List.of (retainedSessionView), true, true);
        compiledMaster.put (this.defaultSessionWorkspace, masterSession);
        compiledMaster.put (this.destinationWorkspaces.get (WorkspaceSelection.Destination.SESSION), masterSession);
        final CompiledWorkspace masterVsLive = MasterWorkspace.create (controllerViews, VsLiveWorkspace.SESSION_BANK, retainedVsLiveGridViews, false, true);
        compiledMaster.put (compiled.get (WorkspaceSelection.Id.VS_LIVE), masterVsLive);
        compiledMaster.put (this.vsLiveStablePageWorkspace, masterVsLive);
        compiledMaster.put (this.vsLiveTrackMixerWorkspace, masterVsLive);
        compiledMaster.put (this.destinationWorkspaces.get (WorkspaceSelection.Destination.NOTE), compiledMaster.get (compiled.get (WorkspaceSelection.Id.DEFAULT)));
        for (final var entry: this.trackPageWorkspaces.entrySet ())
            compiledMaster.put (entry.getValue (), compiledMaster.get (entry.getKey ()));
        this.masterWorkspaces = Map.copyOf (compiledMaster);
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
            this.workspace.deactivateExcept (desiredWorkspace);
            this.workspace = desiredWorkspace;
            this.workspace.activate (snapshot);
        }
        final ParameterSlot mutationSlot;
        if (event instanceof final ParameterMutationEvent mutation)
            mutationSlot = this.workspace.parameterSlotOrNull (mutation.controlId (), snapshot);
        else if (event instanceof final ControllerInputEvent input && input.kind () == InputKind.RELATIVE)
            mutationSlot = this.workspace.parameterSlotOrNull (input.controlId (), snapshot);
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

        this.observePageEffects (currentResult.effects (), snapshot);
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
        final ByteBuffer payload = ByteBuffer.allocate (Integer.BYTES + 8 + owner.length);
        payload.put ((byte) (this.selection.active () == WorkspaceSelection.Id.VS_LIVE ? 1 : 0));
        payload.put ((byte) (this.playbackCoordinator.engineOwnerPlaying () ? 1 : 0));
        payload.put ((byte) this.selection.selectedDestination ().ordinal ());
        payload.put ((byte) this.selection.pendingDestination ().ordinal ());
        payload.put ((byte) (this.normalTrackPageSelected ? 1 : 0));
        payload.put ((byte) this.vsLivePage.ordinal ());
        payload.put ((byte) (this.trackMixerPage.inputOutputSelected () ? 1 : 0));
        payload.put ((byte) this.trackMixerPage.sendOffset ());
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
        final StateEnvelope state = previousState.get ();
        if (!PullCoreProvider.STATE_SCHEMA.equals (state.schema ()) || state.version () != PullCoreProvider.STATE_SCHEMA_VERSION)
            return RestoredState.empty ();
        final byte [] payload = state.payload ();
        if (payload.length < Integer.BYTES + 8)
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
        final int normalTrackPage = Byte.toUnsignedInt (buffer.get ());
        final int vsPageOrdinal = Byte.toUnsignedInt (buffer.get ());
        final int inputOutputPage = Byte.toUnsignedInt (buffer.get ());
        final int sendOffset = Byte.toUnsignedInt (buffer.get ());
        if (normalTrackPage > 1 || vsPageOrdinal >= VsLivePage.values ().length || inputOutputPage > 1 || sendOffset != 0 && sendOffset != 4)
            return RestoredState.empty ();
        final int ownerLength = buffer.getInt ();
        if (ownerLength < 0 || ownerLength > 1024 || ownerLength != buffer.remaining ())
            return RestoredState.empty ();
        final byte [] owner = new byte [ownerLength];
        buffer.get (owner);
        return new RestoredState (workspace, selectedDestination, pendingDestination, new String (owner, StandardCharsets.UTF_8), playing, true, normalTrackPage == 1, VsLivePage.values ()[vsPageOrdinal], inputOutputPage == 1, sendOffset);
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
            activeResult.desiredParameterTouches (),
            activeResult.executionRequirements (),
            effects);
    }


    private CoreResult dispatchActionToWorkspace (final ResolvedControllerAction action, final ControllerSnapshot snapshot, final boolean awaitStableReadback)
    {
        final List<CoreEffect> effects = this.workspace.dispatchAction (action, snapshot);
        this.observeMasterNavigationAction (effects);
        this.observeMasterPageExitAction (action);
        this.observeParameterPageAction (action, snapshot, awaitStableReadback);
        this.observePageEffects (effects, snapshot);
        final CompiledWorkspace selectedWorkspace = this.desiredWorkspace (snapshot);
        if (selectedWorkspace != this.workspace)
            this.workspace.deactivateExcept (selectedWorkspace);
        this.workspace = selectedWorkspace;
        return transitionTo (effects, this.workspace.activate (snapshot));
    }


    private CoreResult transitionToSelectedWorkspace (final CoreResult currentResult, final ControllerSnapshot snapshot)
    {
        final CompiledWorkspace selectedWorkspace = this.desiredWorkspace (snapshot);
        if (selectedWorkspace == this.workspace)
            return currentResult;

        this.workspace.deactivateExcept (selectedWorkspace);
        this.workspace = selectedWorkspace;
        return transitionTo (currentResult.effects (), this.workspace.activate (snapshot));
    }


    private CompiledWorkspace desiredWorkspace (final ControllerSnapshot snapshot)
    {
        this.selection.observe (snapshot.bridge ().layout ());
        this.selection.observe (snapshot.bridge ().noteView ());
        this.observeParameterPageReadback (snapshot.bridge ().layout ());
        final CompiledWorkspace selectedWorkspace = this.selectedWorkspace (snapshot);
        final String mode = snapshot.bridge ().layout ().modeId ();
        final boolean masterLayout = "MASTER".equals (mode) || "MASTER_TEMP".equals (mode);
        if (this.masterNavigationLease != null)
        {
            if (this.selection.requestSequence () != this.masterNavigationLease.workspaceRequest ())
                this.masterNavigationLease = null;
            else
                return this.activeMasterWorkspace;
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
            this.activeMasterWorkspace = Objects.requireNonNull (this.masterWorkspaces.get (selectedWorkspace), "Master composition for " + selectedWorkspace.name ());
        }
        if (this.activeMasterWorkspace == this.masterDrumRawWorkspace || this.activeMasterWorkspace == this.masterDrumLegacyWorkspace)
            this.activeMasterWorkspace = snapshot.bridge ().layout ().drumLayoutActive () && snapshot.bridge ().layout ().drumControllerEngaged () ? this.masterDrumRawWorkspace : this.masterDrumLegacyWorkspace;
        return this.selection.requestSequence () == this.masterEntryWorkspaceRequest ? this.activeMasterWorkspace : selectedWorkspace;
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
            this.vsLiveWorkspaceRequest = this.selection.requestSequence ();
        }
        else if (this.vsLiveWorkspaceRequest != this.selection.requestSequence ())
        {
            // Shift+Session selects the declared composite, including its default Project Macro
            // page. It is an idempotent workspace selection, not a request to retain a stale page.
            this.vsLivePage = VsLivePage.DEFAULT;
            this.vsLiveWorkspaceRequest = this.selection.requestSequence ();
        }
        if (this.normalPageWorkspaceRequest != this.selection.requestSequence ())
        {
            this.normalPageWorkspaceRequest = this.selection.requestSequence ();
            if (this.selection.active () == WorkspaceSelection.Id.DEFAULT && this.selection.pendingDestination () != WorkspaceSelection.Destination.NONE)
                this.normalTrackPageSelected = true;
        }
        final WorkspaceSelection.Destination destination = this.selection.pendingDestination ();
        if (destination != WorkspaceSelection.Destination.NONE)
            return this.destinationWorkspaces.get (destination);
        if (this.selection.active () == WorkspaceSelection.Id.DEFAULT && (this.selection.selectedDestination () == WorkspaceSelection.Destination.SESSION || this.selection.selectedDestination () != WorkspaceSelection.Destination.NOTE && "SESSION".equals (snapshot.bridge ().layout ().viewId ())))
            return this.selectedNormalPage (this.defaultSessionWorkspace);
        if (this.selection.active () == WorkspaceSelection.Id.DEFAULT && snapshot.bridge ().layout ().drumLayoutActive ())
            return this.selectedNormalPage (snapshot.bridge ().layout ().drumControllerEngaged () ? this.defaultDrumWorkspace : this.defaultDrumLegacyWorkspace);
        if (this.selection.active () == WorkspaceSelection.Id.VS_LIVE && this.vsLivePage == VsLivePage.TRACK_MIXER)
            return this.vsLiveTrackMixerWorkspace;
        if (this.selection.active () == WorkspaceSelection.Id.VS_LIVE && this.vsLivePage == VsLivePage.STABLE)
            return this.vsLiveStablePageWorkspace;
        final CompiledWorkspace selected = this.workspaces.get (this.selection.active ());
        return this.selection.active () == WorkspaceSelection.Id.DEFAULT ? this.selectedNormalPage (selected) : selected;
    }


    private CompiledWorkspace selectedNormalPage (final CompiledWorkspace base)
    {
        return this.normalTrackPageSelected ? this.trackPageWorkspaces.get (base) : base;
    }


    private void observeParameterPageAction (final ResolvedControllerAction action, final ControllerSnapshot snapshot, final boolean awaitStableReadback)
    {
        if (action.intent ().action () != ControllerActionId.SWITCH_PARAMETER_CONTEXT)
            return;
        if (awaitStableReadback)
            this.pendingPageSelection = new PendingPageSelection (snapshot.bridge ().layout ().generation (), this.selection.requestSequence (), "");
        else
            this.selectParameterPage (snapshot.bridge ().layout ().modeId ());
    }


    private void observePageEffects (final List<CoreEffect> effects, final ControllerSnapshot snapshot)
    {
        for (final CoreEffect effect: effects)
        {
            if (effect instanceof final SelectControllerModeEffect mode)
                this.pendingPageSelection = new PendingPageSelection (mode.layoutGeneration (), this.selection.requestSequence (), mode.modeId ());
        }
    }


    private void observeParameterPageReadback (final de.mossgrabers.pull.core.api.ControllerLayoutSnapshot layout)
    {
        final PendingPageSelection pending = this.pendingPageSelection;
        if (pending == null)
            return;
        if (pending.workspaceRequest () != this.selection.requestSequence ())
        {
            this.pendingPageSelection = null;
            return;
        }
        if (layout.generation () <= pending.afterGeneration ())
            return;
        this.pendingPageSelection = null;
        if (pending.modeId ().isEmpty () || pending.modeId ().equals (layout.modeId ()))
            this.selectParameterPage (layout.modeId ());
    }


    private void selectParameterPage (final String mode)
    {
        // Only an explicit page action or its later read-back selects this state. Temporary TRACK
        // read-back used to neutralize a Note route carries no parameter-page selection intent.
        this.pendingPageSelection = null;
        if ("MASTER".equals (mode) || "MASTER_TEMP".equals (mode))
            return;
        if (this.selection.active () == WorkspaceSelection.Id.DEFAULT)
            this.normalTrackPageSelected = "TRACK".equals (mode);
        else if ("TRACK".equals (mode))
            this.vsLivePage = VsLivePage.TRACK_MIXER;
        else if ("WORKSPACE".equals (mode))
            this.vsLivePage = VsLivePage.DEFAULT;
        else
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
            result.desiredParameterTouches (),
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
            result.desiredParameterTouches (),
            new de.mossgrabers.pull.core.api.CoreExecutionRequirements (result.executionRequirements ().ticksRequested () || this.playbackCoordinator.executionRequirements ().ticksRequested ()),
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


    private record PendingPageSelection (long afterGeneration, long workspaceRequest, String modeId)
    {
    }


    private record RestoredState (WorkspaceSelection.Id workspace, WorkspaceSelection.Destination selectedDestination, WorkspaceSelection.Destination pendingDestination, String engineOwnerIdentity, boolean engineOwnerPlaying, boolean pageStatePresent, boolean normalTrackPageSelected, VsLivePage vsLivePage, boolean inputOutputSelected, int sendOffset)
    {
        private static RestoredState empty ()
        {
            return new RestoredState (WorkspaceSelection.Id.DEFAULT, WorkspaceSelection.Destination.NONE, WorkspaceSelection.Destination.NONE, "", false, false, false, VsLivePage.DEFAULT, false, 0);
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
