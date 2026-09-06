// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.view;

import de.mossgrabers.pull.core.api.*;
import de.mossgrabers.pull.core.api.effect.CoreEffect;
import de.mossgrabers.pull.core.api.event.*;
import de.mossgrabers.pull.core.api.output.RgbColor;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class InputGestureRouterTest
{
    private static final ControlId BUTTON = PushControlIds.button ("ROW1_1");
    private static final ControlId SECOND = PushControlIds.button ("ROW1_2");
    private static final ControlId KNOB = PushControlIds.continuous ("KNOB1");
    private static final ParameterTargetRef TARGET = new ParameterTargetRef (ParameterTargetKind.LIVE, "original", 1);
    private static final ControllerSnapshot SNAPSHOT = new ControllerSnapshot (0, 0, ShellCapabilities.empty (), ClipCatalogSnapshot.empty (), Map.of (), Set.of (), Set.of ());

    @Test
    void freezesBothReceiversAndDropsDuplicateOrOrphanEdges ()
    {
        final var router = new InputGestureRouter ();
        final var original = new Probe ("original");
        final var observer = new Probe ("observer", SurfaceClaim.Kind.OBSERVE_INPUT);
        final var replacement = new Probe ("replacement");
        final var oldPage = page (original, observer);
        final var newPage = page (replacement);
        deliver (router, oldPage, button (BUTTON, InputPhase.BEGIN));
        router.transition (oldPage, newPage);
        assertEquals (0, original.deactivated);
        deliver (router, newPage, button (BUTTON, InputPhase.BEGIN));
        deliver (router, newPage, button (BUTTON, InputPhase.LONG));
        deliver (router, newPage, button (BUTTON, InputPhase.LONG));
        deliver (router, newPage, button (BUTTON, InputPhase.END));
        deliver (router, newPage, button (BUTTON, InputPhase.END));
        assertEquals (List.of ("BEGIN", "LONG", "END"), original.events);
        assertEquals (original.events, observer.events);
        assertTrue (replacement.events.isEmpty ());
        assertEquals (1, original.deactivated);
        deliver (router, newPage, button (BUTTON, InputPhase.BEGIN));
        deliver (router, newPage, button (BUTTON, InputPhase.END));
        assertEquals (List.of ("BEGIN", "END"), replacement.events);
    }

    @Test
    void deferredActionKeepsOriginalOwnerAliveAfterEndWithoutRepeatingObserverBegin ()
    {
        final var router = new InputGestureRouter ();
        final var owner = new Probe ("owner"); owner.action = true;
        final var observer = new Probe ("observer", SurfaceClaim.Kind.OBSERVE_INPUT);
        final var oldPage = page (owner, observer);
        final var newPage = page (new Probe ("replacement"));
        final var begin = router.capture (button (BUTTON, InputPhase.BEGIN), oldPage);
        final var action = router.resolveAction (begin, SNAPSHOT);
        assertNotNull (action);
        assertNull (router.resolveAction (begin, SNAPSHOT));
        router.transition (oldPage, newPage);
        deliver (router, newPage, button (BUTTON, InputPhase.LONG));
        deliver (router, newPage, button (BUTTON, InputPhase.END));
        assertEquals (0, owner.deactivated);
        assertEquals (List.of ("LONG", "END"), observer.events);
        assertTrue (router.decorate (newPage, newPage.activate (SNAPSHOT), SNAPSHOT).desiredBridgeSubscriptions ().includes (BridgeSubscription.APPLICATION_UI));
        router.dispatchAction (action, SNAPSHOT);
        router.finish (null, newPage);
        assertEquals (List.of ("LONG", "END", "ACTION"), owner.events);
        assertEquals (1, owner.deactivated);
        assertEquals (1, observer.deactivated);
    }

    @Test
    void retainsOnlyNonvisualTouchStateAndLeavesContinuousInputWithTheCurrentPage ()
    {
        final var router = new InputGestureRouter ();
        final var oldView = new Probe ("old"); oldView.parameters = true;
        final var newView = new Probe ("new"); newView.parameters = true;
        final var oldPage = page (oldView);
        final var newPage = page (newView);
        deliver (router, oldPage, touch (InputPhase.BEGIN));
        router.transition (oldPage, newPage);
        final int oldRenders = oldView.renders;
        final var result = router.decorate (newPage, newPage.activate (SNAPSHOT), SNAPSHOT);
        assertEquals (Map.of (KNOB, TARGET), result.desiredParameterTouches ().targets ());
        assertTrue (result.desiredParameterBanks ().includes (ParameterBankId.PROJECT_REMOTE));
        assertEquals (oldRenders, oldView.renders, "retaining a gesture must not render its hidden scene");
        deliver (router, newPage, new ControllerInputEvent (1, 1, KNOB, InputKind.RELATIVE, InputPhase.UPDATE, 3));
        assertEquals (List.of ("BEGIN"), oldView.events);
        assertEquals (List.of ("UPDATE"), newView.events);
        deliver (router, newPage, touch (InputPhase.END));
        assertEquals (List.of ("BEGIN", "END"), oldView.events);
        assertTrue (router.decorate (newPage, newPage.activate (SNAPSHOT), SNAPSHOT).desiredParameterTouches ().targets ().isEmpty ());
    }

    @Test
    void twoHeldControlsAndReturningToTheSameInstanceDoNotDuplicateLifecycle ()
    {
        final var router = new InputGestureRouter ();
        final var view = new Probe ("owner");
        final var oldPage = page (view);
        final var other = page ();
        deliver (router, oldPage, button (BUTTON, InputPhase.BEGIN));
        deliver (router, oldPage, button (SECOND, InputPhase.BEGIN));
        router.transition (oldPage, other);
        deliver (router, other, button (BUTTON, InputPhase.END));
        assertEquals (0, view.deactivated);
        router.transition (other, oldPage);
        deliver (router, oldPage, button (SECOND, InputPhase.END));
        assertEquals (0, view.deactivated, "a returned active view must not be deactivated by its old debt");
        router.transition (oldPage, other);
        assertEquals (1, view.deactivated);
    }

    @Test
    void ticksAndReconciliationDeduplicateSharedInstancesInDeterministicOrder ()
    {
        final List<String> order = new ArrayList<> ();
        final var router = new InputGestureRouter ();
        final var z = new Probe ("z", SurfaceClaim.Kind.OBSERVE_INPUT); z.order = order;
        final var a = new Probe ("a", SurfaceClaim.Kind.OBSERVE_INPUT); a.order = order;
        final var oldPage = page (z, a);
        final var current = page (a);
        deliver (router, oldPage, button (BUTTON, InputPhase.BEGIN));
        router.transition (oldPage, current);
        order.clear ();
        router.reconcile (current, SNAPSHOT);
        assertEquals (List.of ("a:reconcile", "z:reconcile"), order);
        order.clear ();
        deliver (router, current, new ControllerTickEvent (1, 1));
        assertEquals (List.of ("a:tick", "z:tick"), order);
    }

    @Test
    void repeatedActivationAndDeferredDispatchReconcileEachIdentityOncePerEvent ()
    {
        final var router = new InputGestureRouter ();
        final var view = new Probe ("owner"); view.action = true;
        final var current = page (view);
        view.order.clear ();
        router.beginEvent ();
        router.reconcile (current, SNAPSHOT);
        final var action = router.resolveAction (router.capture (button (BUTTON, InputPhase.BEGIN), current), SNAPSHOT);
        router.activate (current, SNAPSHOT);
        router.dispatchAction (action, SNAPSHOT);
        router.activate (current, SNAPSHOT);
        assertEquals (List.of ("owner:reconcile"), view.order);
        router.beginEvent ();
        router.activate (current, SNAPSHOT);
        assertEquals (List.of ("owner:reconcile", "owner:reconcile"), view.order);
    }

    @Test
    void legacyNormalizedEdgesAlsoReturnToTheirOriginalReceiver ()
    {
        final var router = new InputGestureRouter ();
        final var view = new Probe ("owner"); view.parameters = true;
        final var oldPage = page (view);
        final var other = page ();
        deliver (router, oldPage, new ButtonInputEvent (1, 1, BUTTON, true));
        deliver (router, oldPage, new TouchInputEvent (2, 2, KNOB, true));
        router.transition (oldPage, other);
        deliver (router, other, new ButtonInputEvent (3, 3, BUTTON, false));
        deliver (router, other, new TouchInputEvent (4, 4, KNOB, false));
        assertEquals (List.of ("button:true", "touch:true", "button:false", "touch:false"), view.events);
        assertEquals (1, view.deactivated);
    }

    @Test
    void capacityIsExplicitAndReleasedSlotsCanBeReused ()
    {
        final var router = new InputGestureRouter ();
        final var current = page ();
        for (int index = 0; index < InputGestureRouter.CAPACITY; index++)
            deliver (router, current, button (new ControlId ("test-" + index), InputPhase.BEGIN));
        assertThrows (IllegalStateException.class, () -> router.capture (button (new ControlId ("overflow"), InputPhase.BEGIN), current));
        deliver (router, current, button (new ControlId ("test-0"), InputPhase.END));
        assertDoesNotThrow (() -> router.capture (button (new ControlId ("overflow"), InputPhase.BEGIN), current));
    }

    private static void deliver (final InputGestureRouter router, final CompiledWorkspace current, final CoreEvent event)
    {
        final var dispatch = router.capture (event, current);
        router.dispatch (dispatch, SNAPSHOT);
        assertTrue (router.dispatch (dispatch, SNAPSHOT).isEmpty (), "one captured event dispatches only once");
        router.finish (dispatch, current);
    }
    private static CompiledWorkspace page (final Probe... views)
    {
        final var page = CompiledWorkspace.compile ("test", List.of (views));
        page.start (SNAPSHOT);
        return page;
    }
    private static ControllerInputEvent button (final ControlId control, final InputPhase phase) { return new ControllerInputEvent (1, 1, control, InputKind.BUTTON, phase, phase == InputPhase.END ? 0 : 127); }
    private static ControllerInputEvent touch (final InputPhase phase) { return new ControllerInputEvent (1, 1, KNOB, InputKind.TOUCH, phase, phase == InputPhase.END ? 0 : 127); }

    private static final class Probe implements ControllerView
    {
        private final String name;
        private final SurfaceClaim.Kind kind;
        private final List<String> events = new ArrayList<> ();
        private List<String> order = new ArrayList<> ();
        private boolean parameters;
        private boolean action;
        private boolean touched;
        private int deactivated;
        private int renders;
        Probe (final String name) { this (name, SurfaceClaim.Kind.EXCLUSIVE_INPUT); }
        Probe (final String name, final SurfaceClaim.Kind kind) { this.name = name; this.kind = kind; }
        @Override public String id () { return this.name; }
        @Override public ViewProfile profile ()
        {
            final Set<SurfaceClaim> claims = new LinkedHashSet<> (Set.of (new SurfaceClaim (SurfaceArea.SOFT_KEYS_LOWER, this.kind)));
            if (this.parameters) claims.add (new SurfaceClaim (SurfaceArea.ENCODERS, SurfaceClaim.Kind.EXCLUSIVE_INPUT));
            return ViewProfile.fixed ("probe", claims, Set.of ());
        }
        @Override public Set<BridgeSubscription> bridgeSubscriptions () { return this.name.equals ("replacement") ? Set.of () : Set.of (BridgeSubscription.APPLICATION_UI); }
        @Override public Set<ParameterBankId> parameterBanks () { return this.parameters ? Set.of (ParameterBankId.PROJECT_REMOTE) : Set.of (); }
        @Override public Set<ControllerActionBinding> actionBindings () { return this.action ? Set.of (new ControllerActionBinding (BUTTON, InputKind.BUTTON, ControllerActionId.SWITCH_PARAMETER_CONTEXT, Set.of (ControllerStateScope.ACTIVE_PARAMETERS))) : Set.of (); }
        @Override public ResolvedControllerAction resolveAction (final ControllerActionBinding binding, final ControllerInputEvent input, final ControllerSnapshot snapshot)
        { return ResolvedControllerAction.of (binding.intent (), () -> { assertEquals (0, this.deactivated); this.events.add ("ACTION"); return List.of (); }); }
        @Override public void reconcile (final ControllerSnapshot snapshot) { this.order.add (this.name + ":reconcile"); }
        @Override public void deactivate () { this.deactivated++; this.touched = false; }
        @Override public List<CoreEffect> handle (final CoreEvent event, final ControllerSnapshot snapshot)
        {
            if (event instanceof final ControllerInputEvent input)
            {
                this.events.add (input.phase ().name ());
                if (input.kind () == InputKind.TOUCH) this.touched = input.phase () != InputPhase.END;
            }
            else if (event instanceof final ButtonInputEvent input) this.events.add ("button:" + input.pressed ());
            else if (event instanceof final TouchInputEvent input) this.events.add ("touch:" + input.touched ());
            else if (event instanceof ControllerTickEvent) this.order.add (this.name + ":tick");
            return List.of ();
        }
        @Override public DesiredParameterTouches parameterTouches (final ControllerSnapshot snapshot) { return new DesiredParameterTouches (this.touched ? Map.of (KNOB, TARGET) : Map.of ()); }
        @Override public ViewOutput render (final ControllerSnapshot snapshot) { this.renders++; return ViewOutput.empty (); }
    }
}
