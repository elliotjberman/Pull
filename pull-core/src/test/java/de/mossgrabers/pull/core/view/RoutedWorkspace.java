// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.view;

import de.mossgrabers.pull.core.api.ControlId;
import de.mossgrabers.pull.core.api.ControllerSnapshot;
import de.mossgrabers.pull.core.api.CoreResult;
import de.mossgrabers.pull.core.api.ParameterSlot;
import de.mossgrabers.pull.core.api.effect.CoreEffect;
import de.mossgrabers.pull.core.api.event.ControllerInputEvent;
import de.mossgrabers.pull.core.api.event.CoreEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Drives production lifecycle routing without advancing or acknowledging any host state. */
public final class RoutedWorkspace
{
    private final InputGestureRouter router = new InputGestureRouter ();
    private CompiledWorkspace workspace;

    public RoutedWorkspace (final CompiledWorkspace workspace)
    {
        this.workspace = Objects.requireNonNull (workspace, "workspace");
    }

    public CoreResult start (final ControllerSnapshot snapshot) { return this.activate (snapshot); }

    public CoreResult activate (final ControllerSnapshot snapshot)
    {
        this.router.beginEvent ();
        return this.router.decorate (this.workspace, this.router.activate (this.workspace, snapshot), snapshot);
    }

    public CoreResult activate (final CompiledWorkspace next, final ControllerSnapshot snapshot)
    {
        this.router.beginEvent ();
        this.router.transition (this.workspace, next, snapshot);
        this.workspace = next;
        return this.router.decorate (next, this.router.activate (next, snapshot), snapshot);
    }

    public CoreResult handle (final CoreEvent event, final ControllerSnapshot snapshot)
    {
        this.router.beginEvent ();
        this.router.reconcile (this.workspace, snapshot);
        final var dispatch = this.router.capture (event, this.workspace);
        final var action = this.router.resolveAction (dispatch, snapshot);
        final List<CoreEffect> effects = new ArrayList<> ();
        if (action == null) effects.addAll (this.router.dispatch (dispatch, snapshot));
        else
        {
            effects.addAll (action.immediateEffects ());
            effects.addAll (this.router.dispatchAction (action, snapshot));
        }
        this.router.finish (dispatch, snapshot);
        return this.render (snapshot, effects);
    }

    /** Capture now; tests can advance host state or release input before dispatching this action. */
    public ResolvedControllerAction resolveAction (final ControllerInputEvent input, final ControllerSnapshot snapshot)
    {
        this.router.beginEvent ();
        this.router.reconcile (this.workspace, snapshot);
        return this.router.resolveAction (this.router.capture (input, this.workspace), snapshot);
    }

    public List<CoreEffect> dispatchAction (final ResolvedControllerAction action, final ControllerSnapshot snapshot)
    {
        this.router.beginEvent ();
        this.router.reconcile (this.workspace, snapshot);
        return this.router.dispatchAction (action, snapshot);
    }

    public CoreResult handleAction (final ResolvedControllerAction action, final ControllerSnapshot snapshot)
    {
        this.router.beginEvent ();
        this.router.reconcile (this.workspace, snapshot);
        final List<CoreEffect> effects = new ArrayList<> (action.immediateEffects ());
        effects.addAll (this.router.dispatchAction (action, snapshot));
        return this.render (snapshot, effects);
    }

    public ParameterSlot parameterSlotOrNull (final ControlId control) { return this.workspace.parameterSlotOrNull (control); }
    public ParameterSlot parameterSlotOrNull (final ControlId control, final ControllerSnapshot snapshot) { return this.workspace.parameterSlotOrNull (control, snapshot); }

    private CoreResult render (final ControllerSnapshot snapshot, final List<CoreEffect> effects)
    {
        this.router.reconcile (this.workspace, snapshot);
        return this.router.decorate (this.workspace, this.workspace.render (this.router.viewSnapshot (snapshot), effects), snapshot);
    }
}
