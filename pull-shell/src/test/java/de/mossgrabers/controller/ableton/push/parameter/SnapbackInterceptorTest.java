// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.controller.ableton.push.parameter;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * Deterministic snapback lifecycle and authoritative read-back tests.
 */
class SnapbackInterceptorTest
{
    @Test
    void capturesTheFirstAuthoritativeBaselineOnlyOnce ()
    {
        final FakeTarget target = new FakeTarget ("macro-1", 10);
        final SnapbackInterceptor interceptor = interceptor ();

        interceptor.triggerPressed ();
        mutate (interceptor, target, 20);
        target.advanceHost ();
        mutate (interceptor, target, 30);
        target.advanceHost ();
        interceptor.triggerReleased ();

        assertEquals (30, target.requested);
        assertEquals (0, target.restoreRequests);
        settleAndRequestRestore (interceptor);
        assertEquals (10, target.requested);
        assertEquals (1, target.restoreRequests);
        assertTrue (interceptor.isRestoring ());

        target.advanceHost ();
        interceptor.tick ();
        assertTrue (interceptor.isRestoring ());
        interceptor.tick ();

        assertFalse (interceptor.isRestoring ());
    }


    @Test
    void restoresMacrosTempoMasterAndDeviceParametersTogether ()
    {
        final List<FakeTarget> targets = List.of (
            new FakeTarget ("project-macro-1", 11),
            new FakeTarget ("project-macro-2", 22),
            new FakeTarget ("tempo", 120),
            new FakeTarget ("master-volume", 72),
            new FakeTarget ("selected-device-remote-1", 44));
        final SnapbackInterceptor interceptor = interceptor ();

        interceptor.triggerPressed ();
        for (int index = 0; index < targets.size (); index++)
        {
            final FakeTarget target = targets.get (index);
            mutate (interceptor, target, 100 + index);
            target.advanceHost ();
        }
        assertEquals (targets.size (), interceptor.captureCount ());

        interceptor.triggerReleased ();
        settleAndRequestRestore (interceptor);
        for (final FakeTarget target: targets)
        {
            assertEquals (target.baseline, target.requested);
            target.advanceHost ();
        }
        interceptor.tick ();
        assertTrue (interceptor.isRestoring ());
        interceptor.tick ();

        assertEquals (0, interceptor.captureCount ());
        assertFalse (interceptor.isRestoring ());
    }


    @Test
    void navigationWaitsForAuthoritativeRestoration ()
    {
        final FakeTarget target = new FakeTarget ("device-remote", 32);
        final List<String> order = new ArrayList<> ();
        final SnapbackInterceptor interceptor = interceptor ();

        interceptor.triggerPressed ();
        mutate (interceptor, target, 90);
        target.advanceHost ();
        interceptor.beforePotentialTargetRebind ( () -> order.add ("navigate"));

        assertEquals (90, target.requested);
        assertTrue (order.isEmpty ());
        settleAndRequestRestore (interceptor);
        assertEquals (32, target.requested);
        assertTrue (order.isEmpty ());

        target.advanceHost ();
        interceptor.tick ();
        assertTrue (order.isEmpty ());
        interceptor.tick ();

        assertEquals (List.of ("navigate"), order);
    }


    @Test
    void waitsForRevisitedRelativeTargetMotionToSettleBeforeRestoringItsFirstBaseline ()
    {
        final FakeTarget target = new FakeTarget ("project-macro", 20);
        final SnapbackInterceptor interceptor = interceptor ();

        interceptor.triggerPressed ();
        mutate (interceptor, target, 100);
        target.advanceHost ();
        interceptor.triggerReleased ();

        interceptor.tick ();
        target.request (94);
        target.advanceHost ();
        interceptor.tick ();
        assertTrue (interceptor.isRestoring ());
        assertEquals (0, target.restoreRequests);

        interceptor.tick ();
        interceptor.tick ();
        assertEquals (20, target.requested);
        assertEquals (1, target.restoreRequests);

        target.advanceHost ();
        interceptor.tick ();
        interceptor.tick ();
        assertFalse (interceptor.isRestoring ());
    }


    @Test
    void failedReadbackCannotLeaveControlsAndNavigationCapturedForever ()
    {
        final FakeTarget target = new FakeTarget ("device-remote", 32);
        final List<String> order = new ArrayList<> ();
        final List<String> warnings = new ArrayList<> ();
        final SnapbackInterceptor interceptor = new SnapbackInterceptor (warnings::add);

        interceptor.triggerPressed ();
        mutate (interceptor, target, 90);
        target.advanceHost ();
        interceptor.triggerReleased ();
        interceptor.beforePotentialTargetRebind ( () -> order.add ("navigate"));

        for (int tick = 0; tick < 32; tick++)
            interceptor.tick ();

        assertFalse (interceptor.isRestoring ());
        assertEquals (List.of ("navigate"), order);
        assertEquals (1, warnings.size ());

        mutate (interceptor, target, 45);
        assertEquals (45, target.requested);
    }


    @Test
    void staleGenerationNeverRestoresThroughTheReplacementTarget ()
    {
        final FakeTarget original = new FakeTarget ("remote-slot", 15);
        final AtomicBoolean navigationRan = new AtomicBoolean ();
        final List<String> warnings = new ArrayList<> ();
        final SnapbackInterceptor interceptor = new SnapbackInterceptor (warnings::add);

        interceptor.triggerPressed ();
        mutate (interceptor, original, 70);
        original.advanceHost ();
        original.current = false;
        interceptor.beforePotentialTargetRebind ( () -> navigationRan.set (true));

        assertEquals (0, original.restoreRequests);
        assertTrue (navigationRan.get ());
        assertFalse (warnings.isEmpty ());
    }


    @Test
    void rapidRepressCannotCaptureTheTemporaryValueWhileRestoring ()
    {
        final FakeTarget target = new FakeTarget ("macro", 10);
        final SnapbackInterceptor interceptor = interceptor ();

        interceptor.triggerPressed ();
        mutate (interceptor, target, 40);
        target.advanceHost ();
        interceptor.triggerReleased ();
        interceptor.triggerPressed ();
        mutate (interceptor, target, 90);

        assertEquals (40, target.requested);
        settleAndRequestRestore (interceptor);
        assertEquals (10, target.requested);
        target.advanceHost ();
        interceptor.tick ();
        interceptor.tick ();

        mutate (interceptor, target, 25);
        target.advanceHost ();
        interceptor.triggerReleased ();

        settleAndRequestRestore (interceptor);
        assertEquals (10, target.requested);
    }


    @Test
    void shutdownRequestsBestEffortRestoration ()
    {
        final FakeTarget target = new FakeTarget ("tempo", 128);
        final SnapbackInterceptor interceptor = interceptor ();

        interceptor.triggerPressed ();
        mutate (interceptor, target, 140);
        target.advanceHost ();
        interceptor.shutdown ();

        assertEquals (128, target.requested);
        assertEquals (1, target.restoreRequests);
        assertEquals (0, interceptor.captureCount ());
    }


    @Test
    void rejectsTemporaryMutationWhenTheBoundedCaptureSetIsFull ()
    {
        final List<String> warnings = new ArrayList<> ();
        final SnapbackInterceptor interceptor = new SnapbackInterceptor (warnings::add);

        interceptor.triggerPressed ();
        for (int index = 0; index < 16; index++)
            mutate (interceptor, new FakeTarget ("target-" + index, index), index + 100);

        final FakeTarget overflow = new FakeTarget ("overflow", 99);
        mutate (interceptor, overflow, 127);

        assertEquals (99, overflow.requested);
        assertEquals (16, interceptor.captureCount ());
        assertFalse (warnings.isEmpty ());
    }


    private static SnapbackInterceptor interceptor ()
    {
        return new SnapbackInterceptor (ignored -> {
            // No-op warning sink.
        });
    }


    private static void settleAndRequestRestore (final SnapbackInterceptor interceptor)
    {
        interceptor.tick ();
        interceptor.tick ();
    }


    private static void mutate (final SnapbackInterceptor interceptor, final FakeTarget target, final double value)
    {
        interceptor.mutate (target, () -> target.request (value));
    }


    private static final class FakeTarget implements ParameterMutationTarget
    {
        private final ParameterTargetRef reference;
        private final double baseline;
        private double authoritative;
        private double requested;
        private boolean current = true;
        private int restoreRequests;


        private FakeTarget (final String identity, final double baseline)
        {
            this.reference = new ParameterTargetRef ("test", identity, 1);
            this.baseline = baseline;
            this.authoritative = baseline;
            this.requested = baseline;
        }


        @Override
        public ParameterTargetRef reference ()
        {
            return this.reference;
        }


        @Override
        public double readAuthoritativeValue ()
        {
            return this.authoritative;
        }


        @Override
        public void restore (final double value)
        {
            this.restoreRequests++;
            this.request (value);
        }


        @Override
        public boolean isCurrent ()
        {
            return this.current;
        }


        @Override
        public boolean isAt (final double expected)
        {
            return this.authoritative == expected;
        }


        private void request (final double value)
        {
            this.requested = value;
        }


        private void advanceHost ()
        {
            this.authoritative = this.requested;
        }
    }
}
