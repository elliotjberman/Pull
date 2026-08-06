// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.shell.input;

import de.mossgrabers.pull.core.api.ControllerActionId;
import de.mossgrabers.pull.core.api.ControllerActionIntent;
import de.mossgrabers.pull.core.api.ControllerStateScope;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the stable physical-input routing seam independently of Bitwig hardware bindings.
 */
class PhysicalInputRouterTest
{
    private static final String BUTTON = "push.button.play";
    private static final String PAD = "push.pad.1";
    private static final String ENCODER = "push.encoder.1";
    private static final String RIBBON = "push.ribbon";


    @Test
    void unclaimedInputRunsStableCommandExactlyOncePerEdge ()
    {
        final AtomicInteger stableCalls = new AtomicInteger ();
        final List<PhysicalInputEvent<String>> events = new ArrayList<> ();
        final PhysicalInputRouter<String> router = router (InputRoute.NONE, events);

        assertEquals (InputRoute.NONE, router.route (BUTTON, InputKind.BUTTON, InputPhase.BEGIN, 127, stableCalls::incrementAndGet));
        assertEquals (1, router.activeGestureCount ());
        assertEquals (InputRoute.NONE, router.route (BUTTON, InputKind.BUTTON, InputPhase.END, 0, stableCalls::incrementAndGet));

        assertEquals (2, stableCalls.get ());
        assertTrue (events.isEmpty ());
        assertEquals (0, router.activeGestureCount ());
    }


    @Test
    void observeRunsStableCommandBeforePublishingEachEdge ()
    {
        final List<String> order = new ArrayList<> ();
        final PhysicalControlRegistry<String> registry = registry ();
        final PhysicalInputRouter<String> router = new PhysicalInputRouter<> (
            registry,
            (ignoredControl, ignoredKind) -> InputRoute.OBSERVE,
            event -> order.add ("core " + event.phase ()),
            new IncrementingClock ());

        router.route (BUTTON, InputKind.BUTTON, InputPhase.BEGIN, 127, () -> order.add ("stable begin"));
        router.route (BUTTON, InputKind.BUTTON, InputPhase.END, 0, () -> order.add ("stable end"));

        assertEquals (List.of ("stable begin", "core BEGIN", "stable end", "core END"), order);
    }


    @Test
    void exclusiveSuppressesStableCommandAndPublishesEachEdge ()
    {
        final AtomicInteger legacyCalls = new AtomicInteger ();
        final List<PhysicalInputEvent<String>> events = new ArrayList<> ();
        final PhysicalInputRouter<String> router = router (InputRoute.EXCLUSIVE, events);

        router.route (BUTTON, InputKind.BUTTON, InputPhase.BEGIN, 127, legacyCalls::incrementAndGet);
        router.route (BUTTON, InputKind.BUTTON, InputPhase.LONG, 127, legacyCalls::incrementAndGet);
        router.route (BUTTON, InputKind.BUTTON, InputPhase.END, 0, legacyCalls::incrementAndGet);

        assertEquals (0, legacyCalls.get ());
        assertEquals (List.of (InputPhase.BEGIN, InputPhase.LONG, InputPhase.END), events.stream ().map (PhysicalInputEvent::phase).toList ());
    }


    @Test
    void semanticActionBarrierDefersTheWholeStableGestureUntilItReleases ()
    {
        final AtomicBoolean blocked = new AtomicBoolean (true);
        final List<String> order = new ArrayList<> ();
        final PhysicalInputRouter<String> router = new PhysicalInputRouter<> (
            registry (),
            (ignoredControl, ignoredKind) -> InputRoute.OBSERVE,
            event -> order.add ("core " + event.phase ()),
            (ignoredControl, ignoredKind, ignoredAction) -> blocked.get (),
            new IncrementingClock (),
            () -> 7);

        router.route (BUTTON, InputKind.BUTTON, InputPhase.BEGIN, 127, () -> order.add ("stable begin"));
        blocked.set (false);
        router.route (BUTTON, InputKind.BUTTON, InputPhase.END, 0, () -> order.add ("stable end"));
        assertEquals (List.of ("core BEGIN", "core END"), order);
        assertEquals (2, router.deferredStableDispatchCount ());

        // The BEGIN-time decision is frozen through END even though the live barrier cleared.
        router.releaseDeferredStableDispatches ();
        assertEquals (List.of ("core BEGIN", "core END", "stable begin", "stable end"), order);
        assertEquals (0, router.deferredStableDispatchCount ());
    }


    @Test
    void stableOwnedInputPublishesOnlyItsResolvedSemanticAction ()
    {
        final AtomicBoolean blocked = new AtomicBoolean (true);
        final AtomicInteger stableCalls = new AtomicInteger ();
        final List<PhysicalInputEvent<String>> events = new ArrayList<> ();
        final ControllerActionIntent action = new ControllerActionIntent (ControllerActionId.SELECT_PARAMETER_PAGE, Set.of (ControllerStateScope.ACTIVE_PARAMETERS));
        final PhysicalInputRouter<String> router = new PhysicalInputRouter<> (
            registry (),
            (ignoredControl, ignoredKind) -> InputRoute.NONE,
            events::add,
            (ignoredControl, ignoredKind, resolved) -> resolved != null && blocked.get (),
            new IncrementingClock (),
            () -> 7);

        assertEquals (InputRoute.NONE, router.route (BUTTON, InputKind.BUTTON, InputPhase.BEGIN, 127, action, stableCalls::incrementAndGet));
        assertEquals (1, events.size ());
        assertEquals (action, events.getFirst ().stableAction ().orElseThrow ());
        assertEquals (0, stableCalls.get ());

        blocked.set (false);
        router.releaseDeferredStableDispatches ();
        assertEquals (1, stableCalls.get ());
    }


    @Test
    void semanticActionBarrierDoesNotSuppressUnrelatedMotion ()
    {
        final AtomicInteger stableCalls = new AtomicInteger ();
        final List<PhysicalInputEvent<String>> events = new ArrayList<> ();
        final PhysicalInputRouter<String> router = new PhysicalInputRouter<> (
            registry (),
            (ignoredControl, ignoredKind) -> InputRoute.OBSERVE,
            events::add,
            (ignoredControl, ignoredKind, ignoredAction) -> true,
            new IncrementingClock (),
            () -> 7);

        router.route (ENCODER, InputKind.RELATIVE, InputPhase.CHANGE, 5, stableCalls::incrementAndGet);
        router.flush ();

        assertEquals (1, stableCalls.get ());
        assertEquals (5, events.getFirst ().value ());
    }


    @Test
    void edgeLeaseCannotChangeOwnerMidGesture ()
    {
        final AtomicReference<InputRoute> desiredRoute = new AtomicReference<> (InputRoute.EXCLUSIVE);
        final AtomicLong activeGeneration = new AtomicLong (11);
        final AtomicInteger legacyCalls = new AtomicInteger ();
        final List<PhysicalInputEvent<String>> events = new ArrayList<> ();
        final PhysicalInputRouter<String> router = new PhysicalInputRouter<> (
            registry (),
            (ignoredControl, ignoredKind) -> desiredRoute.get (),
            events::add,
            new IncrementingClock (),
            activeGeneration::get);

        router.route (BUTTON, InputKind.BUTTON, InputPhase.BEGIN, 127, legacyCalls::incrementAndGet);
        desiredRoute.set (InputRoute.NONE);
        activeGeneration.set (12);
        assertEquals (InputRoute.EXCLUSIVE, router.route (BUTTON, InputKind.BUTTON, InputPhase.LONG, 127, legacyCalls::incrementAndGet));
        assertEquals (InputRoute.EXCLUSIVE, router.route (BUTTON, InputKind.BUTTON, InputPhase.END, 0, legacyCalls::incrementAndGet));

        assertEquals (0, legacyCalls.get ());
        assertEquals (3, events.size ());
        assertEquals (List.of (11L, 11L, 11L), events.stream ().map (PhysicalInputEvent::ownerGeneration).toList ());
        assertEquals (0, router.activeGestureCount ());

        // An END without a leased BEGIN remains stable-owned even if a newly loaded core now
        // requests the control. This preserves the owner which could have received the old press.
        desiredRoute.set (InputRoute.EXCLUSIVE);
        assertEquals (InputRoute.NONE, router.route (BUTTON, InputKind.BUTTON, InputPhase.END, 0, legacyCalls::incrementAndGet));
        assertEquals (1, legacyCalls.get ());
        assertEquals (3, events.size ());

        router.route (BUTTON, InputKind.BUTTON, InputPhase.BEGIN, 127, legacyCalls::incrementAndGet);
        assertEquals (12, events.getLast ().ownerGeneration ());
    }


    @Test
    void coreReplacementWaitsForThePhysicalGestureAndItsMotionQueue ()
    {
        final PhysicalInputRouter<String> router = router (InputRoute.OBSERVE, new ArrayList<> ());

        assertTrue (router.isIdle ());
        router.route (BUTTON, InputKind.BUTTON, InputPhase.BEGIN, 127, () -> {
            // Established stable behavior is irrelevant to the lifecycle fence.
        });
        assertFalse (router.isIdle ());

        router.route (ENCODER, InputKind.RELATIVE, InputPhase.CHANGE, 1, () -> {
            // Established stable behavior is irrelevant to the lifecycle fence.
        });
        router.route (BUTTON, InputKind.BUTTON, InputPhase.END, 0, () -> {
            // Established stable behavior is irrelevant to the lifecycle fence.
        });
        assertFalse (router.isIdle ());

        router.flush ();
        assertTrue (router.isIdle ());
    }


    @Test
    void stableGestureCannotBeStolenByAChangedExclusiveRoute ()
    {
        final AtomicReference<InputRoute> desiredRoute = new AtomicReference<> (InputRoute.NONE);
        final AtomicInteger legacyCalls = new AtomicInteger ();
        final List<PhysicalInputEvent<String>> events = new ArrayList<> ();
        final PhysicalInputRouter<String> router = new PhysicalInputRouter<> (
            registry (),
            (ignoredControl, ignoredKind) -> desiredRoute.get (),
            events::add,
            new IncrementingClock ());

        router.route (BUTTON, InputKind.BUTTON, InputPhase.BEGIN, 127, legacyCalls::incrementAndGet);
        desiredRoute.set (InputRoute.EXCLUSIVE);
        assertEquals (InputRoute.NONE, router.route (BUTTON, InputKind.BUTTON, InputPhase.END, 0, legacyCalls::incrementAndGet));

        assertEquals (2, legacyCalls.get ());
        assertTrue (events.isEmpty ());
    }


    @Test
    void relativeMotionSumsIntoOneBoundedDelivery ()
    {
        final AtomicInteger legacyCalls = new AtomicInteger ();
        final List<PhysicalInputEvent<String>> events = new ArrayList<> ();
        final PhysicalInputRouter<String> router = router (InputRoute.OBSERVE, events);

        router.route (ENCODER, InputKind.RELATIVE, InputPhase.CHANGE, 2, legacyCalls::incrementAndGet);
        router.route (ENCODER, InputKind.RELATIVE, InputPhase.CHANGE, -5, legacyCalls::incrementAndGet);
        router.route (ENCODER, InputKind.RELATIVE, InputPhase.CHANGE, 4, legacyCalls::incrementAndGet);

        assertEquals (3, legacyCalls.get ());
        assertTrue (events.isEmpty ());
        assertEquals (1, router.pendingMotionCount ());

        router.flush ();

        assertEquals (1, events.size ());
        assertEquals (1, events.getFirst ().value ());
        assertEquals (3, events.getFirst ().sequence ());
        assertEquals (0, router.pendingMotionCount ());
    }


    @Test
    void relativeMotionNeverCoalescesAcrossCoreGenerations ()
    {
        final AtomicLong generation = new AtomicLong (4);
        final List<PhysicalInputEvent<String>> events = new ArrayList<> ();
        final PhysicalInputRouter<String> router = new PhysicalInputRouter<> (
            registry (),
            (ignoredControl, ignoredKind) -> InputRoute.EXCLUSIVE,
            events::add,
            new IncrementingClock (),
            generation::get);

        router.route (ENCODER, InputKind.RELATIVE, InputPhase.CHANGE, 7, () -> {
            // Exclusive routing suppresses this command.
        });
        generation.set (5);
        router.route (ENCODER, InputKind.RELATIVE, InputPhase.CHANGE, 2, () -> {
            // Exclusive routing suppresses this command.
        });
        router.flush ();

        assertEquals (1, events.size ());
        assertEquals (2, events.getFirst ().value ());
        assertEquals (5, events.getFirst ().ownerGeneration ());
    }


    @Test
    void absoluteMotionKeepsOnlyLatestValue ()
    {
        final AtomicInteger legacyCalls = new AtomicInteger ();
        final List<PhysicalInputEvent<String>> events = new ArrayList<> ();
        final PhysicalInputRouter<String> router = router (InputRoute.EXCLUSIVE, events);

        router.route (RIBBON, InputKind.ABSOLUTE, InputPhase.CHANGE, 1, legacyCalls::incrementAndGet);
        router.route (RIBBON, InputKind.ABSOLUTE, InputPhase.CHANGE, 63, legacyCalls::incrementAndGet);
        router.route (RIBBON, InputKind.ABSOLUTE, InputPhase.CHANGE, 127, legacyCalls::incrementAndGet);
        router.flush (RIBBON, InputKind.ABSOLUTE);

        assertEquals (0, legacyCalls.get ());
        assertEquals (1, events.size ());
        assertEquals (127, events.getFirst ().value ());
        assertEquals (3, events.getFirst ().sequence ());
    }


    @Test
    void cancellingRelativeMotionDoesNotPublishAZeroDelta ()
    {
        final List<PhysicalInputEvent<String>> events = new ArrayList<> ();
        final PhysicalInputRouter<String> router = router (InputRoute.EXCLUSIVE, events);

        router.route (ENCODER, InputKind.RELATIVE, InputPhase.CHANGE, 7, () -> {
            // Exclusive routing suppresses this command.
        });
        router.route (ENCODER, InputKind.RELATIVE, InputPhase.CHANGE, -7, () -> {
            // Exclusive routing suppresses this command.
        });

        assertEquals (0, router.pendingMotionCount ());
        router.flush ();
        assertTrue (events.isEmpty ());
    }


    @Test
    void registryRejectsDuplicateOverflowUnknownControlsAndWrongPhases ()
    {
        final PhysicalControlRegistry<String> multiKind = PhysicalControlRegistry.<String>builder (2)
            .register (BUTTON, InputKind.BUTTON)
            .register (BUTTON, InputKind.TOUCH)
            .build ();
        assertEquals (2, multiKind.size ());
        assertTrue (multiKind.contains (BUTTON, InputKind.BUTTON));
        assertTrue (multiKind.contains (BUTTON, InputKind.TOUCH));

        final PhysicalControlRegistry.Builder<String> builder = PhysicalControlRegistry.builder (1);
        builder.register (BUTTON, InputKind.BUTTON);
        assertThrows (IllegalArgumentException.class, () -> builder.register (BUTTON, InputKind.BUTTON));
        assertThrows (IllegalStateException.class, () -> builder.register (ENCODER, InputKind.RELATIVE));

        final PhysicalControlRegistry<String> registry = builder.build ();
        final PhysicalInputRouter<String> router = new PhysicalInputRouter<> (registry, (ignoredControl, ignoredKind) -> InputRoute.NONE, ignored -> {
            // No event is expected for the stable-only route.
        });
        assertThrows (IllegalArgumentException.class, () -> router.route ("unknown", InputKind.BUTTON, InputPhase.BEGIN, 1, () -> {
            // Not reached.
        }));
        assertThrows (IllegalArgumentException.class, () -> router.route (BUTTON, InputKind.BUTTON, InputPhase.CHANGE, 1, () -> {
            // Not reached.
        }));
    }


    private static PhysicalInputRouter<String> router (final InputRoute route, final List<PhysicalInputEvent<String>> events)
    {
        return new PhysicalInputRouter<> (registry (), (ignoredControl, ignoredKind) -> route, events::add, new IncrementingClock ());
    }


    private static PhysicalControlRegistry<String> registry ()
    {
        return PhysicalControlRegistry.<String>builder (4)
            .register (BUTTON, InputKind.BUTTON)
            .register (PAD, InputKind.PAD)
            .register (ENCODER, InputKind.RELATIVE)
            .register (RIBBON, InputKind.ABSOLUTE)
            .build ();
    }


    private static final class IncrementingClock implements java.util.function.LongSupplier
    {
        private final AtomicLong value = new AtomicLong (100);


        @Override
        public long getAsLong ()
        {
            return this.value.getAndIncrement ();
        }
    }
}
