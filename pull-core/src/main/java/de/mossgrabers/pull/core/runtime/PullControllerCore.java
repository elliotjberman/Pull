// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.runtime;

import de.mossgrabers.pull.core.api.ControllerCore;
import de.mossgrabers.pull.core.api.ControllerSnapshot;
import de.mossgrabers.pull.core.api.CoreResult;
import de.mossgrabers.pull.core.api.ParameterSlot;
import de.mossgrabers.pull.core.api.StateEnvelope;
import de.mossgrabers.pull.core.api.effect.CoreEffect;
import de.mossgrabers.pull.core.api.event.ControllerInputEvent;
import de.mossgrabers.pull.core.api.event.ControllerActionEvent;
import de.mossgrabers.pull.core.api.event.CoreEvent;
import de.mossgrabers.pull.core.api.event.InputKind;
import de.mossgrabers.pull.core.api.event.ParameterMutationEvent;
import de.mossgrabers.pull.core.runtime.view.DefaultWorkspace;
import de.mossgrabers.pull.core.runtime.view.VsLiveWorkspace;
import de.mossgrabers.pull.core.runtime.view.WorkspaceSelection;
import de.mossgrabers.pull.core.view.CompiledWorkspace;
import de.mossgrabers.pull.core.view.ResolvedControllerAction;

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

        this.selection = new WorkspaceSelection (restoreWorkspace (previousState));
        final Map<WorkspaceSelection.Id, CompiledWorkspace> compiled = new EnumMap<> (WorkspaceSelection.Id.class);
        compiled.put (WorkspaceSelection.Id.DEFAULT, DefaultWorkspace.create (this.selection));
        compiled.put (WorkspaceSelection.Id.VS_LIVE, VsLiveWorkspace.create (this.selection));
        this.workspaces = Map.copyOf (compiled);
        this.workspace = this.workspaces.get (this.selection.active ());
        this.lifecycle = Lifecycle.RUNNING;
        this.snapback.start (snapshot);
        return this.snapback.decorate (this.workspace.activate (snapshot), List.of ());
    }


    /** {@inheritDoc} */
    @Override
    public CoreResult handle (final CoreEvent event, final ControllerSnapshot snapshot)
    {
        this.requireRunning ();
        Objects.requireNonNull (event, "event");
        Objects.requireNonNull (snapshot, "snapshot");
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
            currentResult = update.intercepted () ? this.workspace.activate (snapshot) : this.dispatchToWorkspace (event, snapshot);

        final List<CoreEffect> effects = new ArrayList<> (currentResult.effects ());
        for (final ResolvedControllerAction released: update.releasedActions ())
        {
            currentResult = this.dispatchActionToWorkspace (released, snapshot);
            effects.addAll (currentResult.effects ());
        }
        return this.snapback.decorate (withEffects (currentResult, effects), update.effects ());
    }


    /** {@inheritDoc} */
    @Override
    public StateEnvelope checkpoint ()
    {
        this.requireRunning ();
        return new StateEnvelope (
            PullCoreProvider.STATE_SCHEMA,
            PullCoreProvider.STATE_SCHEMA_VERSION,
            new byte []
            {
                (byte) (this.selection.active () == WorkspaceSelection.Id.VS_LIVE ? 1 : 0)
            });
    }


    private static WorkspaceSelection.Id restoreWorkspace (final Optional<StateEnvelope> previousState)
    {
        if (previousState.isEmpty ())
            return WorkspaceSelection.Id.DEFAULT;
        final byte [] payload = previousState.get ().payload ();
        return payload.length == 1 && payload[0] == 1 ? WorkspaceSelection.Id.VS_LIVE : WorkspaceSelection.Id.DEFAULT;
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


    private CoreResult dispatchToWorkspace (final CoreEvent event, final ControllerSnapshot snapshot)
    {
        final CoreResult currentResult = this.workspace.handle (event, snapshot);
        final CompiledWorkspace selectedWorkspace = this.workspaces.get (this.selection.active ());
        if (selectedWorkspace == this.workspace)
            return currentResult;

        this.workspace = selectedWorkspace;
        return transitionTo (currentResult.effects (), this.workspace.activate (snapshot));
    }


    private CoreResult dispatchActionToWorkspace (final ResolvedControllerAction action, final ControllerSnapshot snapshot)
    {
        final CoreResult currentResult = this.workspace.handleAction (action, snapshot);
        final CompiledWorkspace selectedWorkspace = this.workspaces.get (this.selection.active ());
        if (selectedWorkspace == this.workspace)
            return currentResult;

        this.workspace = selectedWorkspace;
        return transitionTo (currentResult.effects (), this.workspace.activate (snapshot));
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


    private enum Lifecycle
    {
        NEW,
        RUNNING
    }
}
