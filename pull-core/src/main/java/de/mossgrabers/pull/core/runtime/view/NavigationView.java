// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.api.BankNavigationSnapshot;
import de.mossgrabers.pull.core.api.BridgeSubscription;
import de.mossgrabers.pull.core.api.ControlId;
import de.mossgrabers.pull.core.api.ControllerActionBinding;
import de.mossgrabers.pull.core.api.ControllerActionId;
import de.mossgrabers.pull.core.api.ControllerSnapshot;
import de.mossgrabers.pull.core.api.ControllerStateScope;
import de.mossgrabers.pull.core.api.CurrentTrackBankSnapshot;
import de.mossgrabers.pull.core.api.PushControlIds;
import de.mossgrabers.pull.core.api.effect.CoreEffect;
import de.mossgrabers.pull.core.api.effect.CurrentTrackNavigationEffect;
import de.mossgrabers.pull.core.api.effect.CurrentTrackNavigationEffect.Operation;
import de.mossgrabers.pull.core.api.event.ControllerInputEvent;
import de.mossgrabers.pull.core.api.event.CoreEvent;
import de.mossgrabers.pull.core.api.event.InputKind;
import de.mossgrabers.pull.core.api.event.InputPhase;
import de.mossgrabers.pull.core.api.output.RgbColor;
import de.mossgrabers.pull.core.view.ControllerView;
import de.mossgrabers.pull.core.view.ResolvedControllerAction;
import de.mossgrabers.pull.core.view.SurfaceArea;
import de.mossgrabers.pull.core.view.SurfaceClaim;
import de.mossgrabers.pull.core.view.ViewOutput;
import de.mossgrabers.pull.core.view.ViewProfile;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Fixed arrow policy; the Session grid's page buttons are a separate surface. */
public class NavigationView implements ControllerView
{
    public enum Horizontal { MIXER, SESSION, INERT }

    private static final ControlId LEFT = PushControlIds.button ("ARROW_LEFT");
    private static final ControlId RIGHT = PushControlIds.button ("ARROW_RIGHT");
    private static final ControlId UP = PushControlIds.button ("ARROW_UP");
    private static final ControlId DOWN = PushControlIds.button ("ARROW_DOWN");
    private static final ControlId SHIFT = PushControlIds.button ("SHIFT");
    private static final RgbColor ON = new RgbColor (60, 60, 60);
    private static final RgbColor OFF = new RgbColor (0, 0, 0);
    private final Horizontal horizontal;
    private final ViewProfile profile;
    private final Set<ControllerActionBinding> actions;

    public NavigationView (final Horizontal horizontal)
    {
        this.horizontal = java.util.Objects.requireNonNull (horizontal, "horizontal");
        this.profile = ViewProfile.fixed (horizontal.name (), Set.of (
            new SurfaceClaim (SurfaceArea.NAVIGATION_ARROWS, SurfaceClaim.Kind.EXCLUSIVE_INPUT),
            new SurfaceClaim (SurfaceArea.NAVIGATION_ARROWS, SurfaceClaim.Kind.OUTPUT),
            new SurfaceClaim (SurfaceArea.SHIFT_MODIFIER, SurfaceClaim.Kind.OBSERVE_INPUT)), Set.of ());
        final ControllerActionId action = horizontal == Horizontal.SESSION ? ControllerActionId.NAVIGATE_SELECTED_TARGET : ControllerActionId.SELECT_PARAMETER_PAGE;
        this.actions = Set.of (
            new ControllerActionBinding (LEFT, InputKind.BUTTON, action, Set.of (ControllerStateScope.ACTIVE_PARAMETERS)),
            new ControllerActionBinding (RIGHT, InputKind.BUTTON, action, Set.of (ControllerStateScope.ACTIVE_PARAMETERS)));
    }

    @Override public String id () { return "navigation-arrows"; }
    @Override public ViewProfile profile () { return this.profile; }
    @Override public Set<BridgeSubscription> bridgeSubscriptions () { return Set.of (BridgeSubscription.CURRENT_TRACK_BANK); }
    @Override public Set<ControllerActionBinding> actionBindings () { return this.actions; }

    @Override
    public ResolvedControllerAction resolveAction (final ControllerActionBinding binding, final ControllerInputEvent input, final ControllerSnapshot snapshot)
    {
        final List<CoreEffect> effects = this.request (input.controlId (), snapshot);
        return ResolvedControllerAction.of (binding.intent (), () -> effects);
    }

    @Override
    public List<CoreEffect> handle (final CoreEvent event, final ControllerSnapshot snapshot)
    {
        if (event instanceof final ControllerInputEvent input && input.kind () == InputKind.BUTTON && input.phase () == InputPhase.BEGIN && (UP.equals (input.controlId ()) || DOWN.equals (input.controlId ())))
            return this.request (input.controlId (), snapshot);
        return List.of ();
    }

    private List<CoreEffect> request (final ControlId control, final ControllerSnapshot snapshot)
    {
        final CurrentTrackBankSnapshot bank = snapshot.bridge ().currentTrackBank ();
        if (bank.navigationGeneration () == 0)
            return List.of ();
        final boolean shift = snapshot.pressedControls ().contains (SHIFT);
        final Operation operation;
        if (UP.equals (control))
            operation = shift ? Operation.SCENE_PAGE_PREVIOUS : Operation.SCENE_SCROLL_PREVIOUS;
        else if (DOWN.equals (control))
            operation = shift ? Operation.SCENE_PAGE_NEXT : Operation.SCENE_SCROLL_NEXT;
        else if (this.horizontal == Horizontal.INERT)
            return List.of ();
        else if (shift)
            operation = LEFT.equals (control) ? Operation.TRACK_PAGE_PREVIOUS : Operation.TRACK_PAGE_NEXT;
        else
            operation = LEFT.equals (control) ? Operation.TRACK_SCROLL_PREVIOUS : Operation.TRACK_SCROLL_NEXT;
        return List.of (new CurrentTrackNavigationEffect (bank.navigationGeneration (), bank.bankId (), operation));
    }

    @Override
    public ViewOutput render (final ControllerSnapshot snapshot)
    {
        final CurrentTrackBankSnapshot bank = snapshot.bridge ().currentTrackBank ();
        final boolean shift = snapshot.pressedControls ().contains (SHIFT);
        final BankNavigationSnapshot tracks = bank.trackNavigation ();
        final BankNavigationSnapshot scenes = bank.sceneNavigation ();
        return new ViewOutput (Map.of (
            LEFT, color (this.horizontal != Horizontal.INERT && (shift ? tracks.previousPage () : tracks.previousItem ())),
            RIGHT, color (this.horizontal != Horizontal.INERT && (shift ? tracks.nextPage () : tracks.nextItem ())),
            UP, color (shift ? scenes.previousPage () : scenes.previousItem ()),
            DOWN, color (shift ? scenes.nextPage () : scenes.nextItem ())), Map.of ());
    }

    private static RgbColor color (final boolean enabled) { return enabled ? ON : OFF; }
}
