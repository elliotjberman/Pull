// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.runtime;

import de.mossgrabers.pull.core.runtime.curve.ReturnCurve;

import de.mossgrabers.pull.core.api.*;
import de.mossgrabers.pull.core.api.effect.CoreEffect;
import de.mossgrabers.pull.core.api.event.*;
import de.mossgrabers.pull.core.api.output.MixerControlsDisplay;
import de.mossgrabers.pull.core.runtime.view.*;
import de.mossgrabers.pull.core.ui.page.MixerDisplayScene;
import de.mossgrabers.pull.core.view.CompiledWorkspace;
import de.mossgrabers.pull.core.view.InputGestureRouter;
import de.mossgrabers.pull.core.view.PageId;
import de.mossgrabers.pull.core.view.ResolvedControllerAction;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Reloadable controller behavior, including the authoritative page/navigation state. */
final class PullControllerCore implements ControllerCore
{
    private WorkspaceSelection selection;
    private TrackMixerPageState trackMixerPage;
    private PageNavigation pages;
    private ControllerPages catalog;
    private MasterTrackPageNavigation masterSelection;
    private BrowserPageNavigation browserPage;
    private CompiledWorkspace workspace;
    private ProjectPlaybackCoordinator playbackCoordinator;
    private final SnapbackSession snapback;
    private final String configurationWarning;

    public PullControllerCore () { this (InterpolationCurve.LINEAR); }

    PullControllerCore (final ReturnCurve curve) { this (curve, null); }

    PullControllerCore (final ReturnCurve curve, final String configurationWarning)
    {
        this.snapback = new SnapbackSession (curve);
        this.configurationWarning = configurationWarning;
    }
    private final InputGestureRouter gestures = new InputGestureRouter ();
    private Lifecycle lifecycle = Lifecycle.NEW;

    @Override
    public CoreResult start (final ControllerSnapshot snapshot, final Optional<StateEnvelope> previousState)
    {
        Objects.requireNonNull (snapshot, "snapshot");
        Objects.requireNonNull (previousState, "previousState");
        if (this.lifecycle != Lifecycle.NEW) throw new IllegalStateException ("Core can only be started once");
        final ControllerCheckpoint restored = ControllerCheckpoint.decode (previousState);
        this.selection = new WorkspaceSelection (restored.workspace (), restored.selectedDestination (), restored.pendingDestination ());
        this.trackMixerPage = new TrackMixerPageState (restored.inputOutputSelected (), restored.sendOffset ());
        final ControllerPageRef initial = restored.workspace () == WorkspaceSelection.Id.VS_LIVE ? LegacyPageAliases.reference (PageId.PROJECT_MACROS) : LegacyPageAliases.resolve (snapshot.bridge ().layout ().modeId ());
        this.pages = new PageNavigation (initial, LegacyPageAliases::resolve);
        restored.page ().ifPresent (this.pages::restoreState);
        this.pages.startRequestStream (snapshot.bridge ().controllerPages ());
        this.masterSelection = new MasterTrackPageNavigation (this.pages);
        this.masterSelection.start (snapshot);
        this.browserPage = new BrowserPageNavigation (this.pages);
        this.browserPage.reconcile (snapshot.bridge ().browser ());
        this.playbackCoordinator = new ProjectPlaybackCoordinator ();
        this.playbackCoordinator.restoreEngineOwner (restored.engineOwnerIdentity (), restored.engineOwnerPlaying ());
        final ControllerLevelViews controls = new ControllerLevelViews (this.selection, this.playbackCoordinator, this.pages);
        this.catalog = new ControllerPages (this.selection, controls, this.pages, this.trackMixerPage);
        this.workspace = this.desiredWorkspace (snapshot);
        this.lifecycle = Lifecycle.RUNNING;
        this.snapback.start (snapshot);
        final List<CoreEffect> configurationEffects = this.configurationWarning == null ? List.of () : List.of (
            new de.mossgrabers.pull.core.api.effect.ShowHostNotificationEffect (this.configurationWarning));
        return this.completeResult (this.snapback.decorate (this.gestures.activate (this.workspace, snapshot), configurationEffects), snapshot);
    }

    @Override
    public CoreResult handle (final CoreEvent event, final ControllerSnapshot snapshot)
    {
        this.requireRunning ();
        Objects.requireNonNull (event, "event");
        Objects.requireNonNull (snapshot, "snapshot");
        this.gestures.beginEvent ();
        this.browserPage.reconcile (snapshot.bridge ().browser ());
        this.activateSelectedWorkspace (snapshot);
        this.gestures.reconcile (this.workspace, snapshot);
        // Capture edges and their companion motion before deferred page actions can change owners.
        final boolean edge = event instanceof final ControllerInputEvent input && input.kind ().isEdge () || event instanceof ButtonInputEvent || event instanceof TouchInputEvent;
        final InputGestureRouter.Dispatch captured = this.gestures.capture (event, this.workspace);
        final ResolvedControllerAction capturedAction = this.gestures.resolveAction (captured, snapshot);
        final ParameterSlot mutationSlot;
        if (captured.suppressed ()) mutationSlot = null;
        else if (event instanceof final ParameterMutationEvent mutation) mutationSlot = this.workspace.parameterSlotOrNull (mutation.controlId (), snapshot);
        else if (event instanceof final ControllerInputEvent input && input.kind () == InputKind.RELATIVE) mutationSlot = this.workspace.parameterSlotOrNull (input.controlId (), snapshot);
        else mutationSlot = null;
        final List<CoreEffect> effects = new ArrayList<> ();
        SnapbackSession.Update update = this.drainReleased (captured.suppressed () ? new SnapbackSession.Update (true, List.of (), List.of ()) : this.snapback.handle (event, snapshot, mutationSlot), snapshot, effects);
        final boolean eventIntercepted = update.intercepted ();

        // Legacy callbacks and host-driven page selection use the same parameter-restoration
        // admission as physical page buttons. Resolving a request does not acknowledge it.
        final List<ResolvedControllerAction> pageActions = new ArrayList<> (this.pages.resolveLegacyActions (snapshot.bridge ().controllerPages ()));
        final ResolvedControllerAction masterAction = this.masterSelection.observe (snapshot);
        if (masterAction != null) pageActions.add (masterAction);
        for (final ResolvedControllerAction request: pageActions)
        {
            final SnapbackSession.Update admitted = this.drainReleased (this.snapback.handleAction (request, snapshot), snapshot, effects);
            update = mergeUpdates (update, admitted);
            if (!admitted.intercepted ()) effects.addAll (this.dispatchActionToWorkspace (request, snapshot).effects ());
        }

        final ResolvedControllerAction browserAction = this.browserPage.resolveAction ();
        if (browserAction != null)
        {
            final SnapbackSession.Update admitted = this.drainReleased (this.snapback.handleAction (browserAction, snapshot), snapshot, effects);
            update = mergeUpdates (update, admitted);
            if (!admitted.intercepted ()) effects.addAll (this.dispatchActionToWorkspace (browserAction, snapshot).effects ());
        }

        final ResolvedControllerAction action;
        if (edge) action = capturedAction;
        else if (event instanceof final ControllerActionEvent semantic) action = ResolvedControllerAction.stable (semantic.intent ());
        else action = null;
        CoreResult currentResult;
        if (action != null)
        {
            final SnapbackSession.Update admitted = this.drainReleased (this.snapback.handleAction (action, snapshot), snapshot, effects);
            update = mergeUpdates (update, admitted);
            currentResult = admitted.intercepted () ? this.gestures.activate (this.workspace, snapshot) : this.dispatchActionToWorkspace (action, snapshot);
            effects.addAll (action.immediateEffects ());
        }
        else
        {
            final List<CoreEffect> routed = eventIntercepted ? List.of () : this.gestures.dispatch (captured, snapshot);
            currentResult = withEffects (this.gestures.activate (this.workspace, snapshot), routed);
        }
        this.gestures.finish (captured, snapshot);
        currentResult = this.transitionToSelectedWorkspace (currentResult, snapshot);
        effects.addAll (currentResult.effects ());
        return this.completeResult (this.snapback.decorate (withEffects (currentResult, effects), update.effects ()), snapshot);
    }

    private SnapbackSession.Update drainReleased (final SnapbackSession.Update update, final ControllerSnapshot snapshot, final List<CoreEffect> effects)
    {
        for (final ResolvedControllerAction released: update.releasedActions ())
            effects.addAll (this.dispatchActionToWorkspace (released, snapshot).effects ());
        return new SnapbackSession.Update (update.intercepted (), List.of (), update.effects ());
    }

    @Override
    public StateEnvelope checkpoint ()
    {
        this.requireRunning ();
        return new ControllerCheckpoint (this.selection.active (), this.selection.selectedDestination (), this.selection.pendingDestination (), this.playbackCoordinator.engineOwnerIdentity (), this.playbackCoordinator.engineOwnerPlaying (), this.trackMixerPage.inputOutputSelected (), this.trackMixerPage.sendOffset (), Optional.of (this.pages.state ())).encode ();
    }

    @Override
    public MixerControlsDisplay renderMixerControls (final MixerControlsSnapshot snapshot)
    {
        return MixerDisplayScene.render (Objects.requireNonNull (snapshot, "snapshot"));
    }

    private CompiledWorkspace desiredWorkspace (final ControllerSnapshot snapshot)
    {
        this.selection.observe (snapshot.bridge ().layout ());
        this.selection.observe (snapshot.bridge ().noteView ());
        final PageId defaultPage = this.selection.active () == WorkspaceSelection.Id.VS_LIVE ? PageId.PROJECT_MACROS : PageId.TRACK;
        this.pages.workspaceChanged (this.selection.requestSequence (), LegacyPageAliases.reference (defaultPage));
        return this.catalog.select (this.pages.visible (), this.selection, snapshot);
    }

    private void activateSelectedWorkspace (final ControllerSnapshot snapshot)
    {
        final CompiledWorkspace selected = this.desiredWorkspace (snapshot);
        if (selected == this.workspace) return;
        this.gestures.transition (this.workspace, selected, snapshot);
        this.workspace = selected;
        this.gestures.activate (this.workspace, snapshot);
    }

    private CoreResult dispatchActionToWorkspace (final ResolvedControllerAction action, final ControllerSnapshot snapshot)
    {
        final List<CoreEffect> effects = this.gestures.dispatchAction (action, snapshot);
        this.masterSelection.observeEffects (effects);
        this.activateSelectedWorkspace (snapshot);
        return transitionTo (effects, this.gestures.activate (this.workspace, snapshot));
    }

    private CoreResult transitionToSelectedWorkspace (final CoreResult result, final ControllerSnapshot snapshot)
    {
        final CompiledWorkspace selected = this.desiredWorkspace (snapshot);
        if (selected == this.workspace) return result;
        this.gestures.transition (this.workspace, selected, snapshot);
        this.workspace = selected;
        return transitionTo (result.effects (), this.gestures.activate (this.workspace, snapshot));
    }

    private CoreResult completeResult (final CoreResult activeResult, final ControllerSnapshot snapshot)
    {
        final CoreResult result = this.gestures.decorate (this.workspace, activeResult, snapshot);
        final EnumSet<BridgeSubscription> subscriptions = EnumSet.noneOf (BridgeSubscription.class);
        subscriptions.addAll (result.desiredBridgeSubscriptions ().domains ());
        subscriptions.add (BridgeSubscription.CONTROLLER_PAGES);
        subscriptions.add (BridgeSubscription.BROWSER);
        subscriptions.add (BridgeSubscription.MASTER);
        final DesiredControllerPageState state = this.pages.state ();
        final DesiredControllerPageState page = new DesiredControllerPageState (state.revision (), state.selected (), state.previous (), state.temporary (), state.acknowledgedRequestSequence (), this.catalog.indications (this.pages.visible (), this.selection, snapshot));
        final DesiredControllerState controller = new DesiredControllerState (result.desiredControllerState ().workspace (), result.desiredControllerState ().notePerformance (), page);
        return new CoreResult (result.desiredOutput (), result.desiredInputRoutes (), new DesiredBridgeSubscriptions (subscriptions, result.desiredBridgeSubscriptions ().clipScan ()), result.desiredClipBindings (), controller, result.desiredNoteRepeat (), result.desiredControllerActions (), result.desiredParameterBanks (), result.desiredParameterInteraction (), result.desiredParameterTouches (), result.executionRequirements ().merge (this.playbackCoordinator.executionRequirements ()), result.effects ());
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


    private enum Lifecycle { NEW, RUNNING }
}
