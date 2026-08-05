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
        interceptor.mutate (mutation (target, 20));
        target.advanceHost ();
        interceptor.mutate (mutation (target, 30));
        target.advanceHost ();
        interceptor.triggerReleased ();

        assertEquals (10, target.requested);
        assertEquals (1, target.restoreRequests);
        assertTrue (interceptor.isRestoring ());

        target.advanceHost ();
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
            interceptor.mutate (mutation (target, 100 + index));
            target.advanceHost ();
        }
        assertEquals (targets.size (), interceptor.captureCount ());

        interceptor.triggerReleased ();
        for (final FakeTarget target: targets)
        {
            assertEquals (target.baseline, target.requested);
            target.advanceHost ();
        }
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
        interceptor.mutate (mutation (target, 90));
        target.advanceHost ();
        interceptor.beforePotentialTargetRebind ( () -> order.add ("navigate"));

        assertEquals (32, target.requested);
        assertTrue (order.isEmpty ());
        interceptor.tick ();
        assertTrue (order.isEmpty ());

        target.advanceHost ();
        interceptor.tick ();

        assertEquals (List.of ("navigate"), order);
    }


    @Test
    void failedReadbackCannotLeaveControlsAndNavigationCapturedForever ()
    {
        final FakeTarget target = new FakeTarget ("device-remote", 32);
        final List<String> order = new ArrayList<> ();
        final List<String> warnings = new ArrayList<> ();
        final SnapbackInterceptor interceptor = new SnapbackInterceptor (request -> request.mutation ().run (), warnings::add);

        interceptor.triggerPressed ();
        interceptor.mutate (mutation (target, 90));
        target.advanceHost ();
        interceptor.triggerReleased ();
        interceptor.beforePotentialTargetRebind ( () -> order.add ("navigate"));

        for (int tick = 0; tick < 16; tick++)
            interceptor.tick ();

        assertFalse (interceptor.isRestoring ());
        assertEquals (List.of ("navigate"), order);
        assertEquals (1, warnings.size ());

        interceptor.mutate (mutation (target, 45));
        assertEquals (45, target.requested);
    }


    @Test
    void staleGenerationNeverRestoresThroughTheReplacementTarget ()
    {
        final FakeTarget original = new FakeTarget ("remote-slot", 15);
        final AtomicBoolean navigationRan = new AtomicBoolean ();
        final List<String> warnings = new ArrayList<> ();
        final SnapbackInterceptor interceptor = new SnapbackInterceptor (request -> request.mutation ().run (), warnings::add);

        interceptor.triggerPressed ();
        interceptor.mutate (mutation (original, 70));
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
        interceptor.mutate (mutation (target, 40));
        target.advanceHost ();
        interceptor.triggerReleased ();
        interceptor.triggerPressed ();
        interceptor.mutate (mutation (target, 90));

        assertEquals (10, target.requested);
        target.advanceHost ();
        interceptor.tick ();

        interceptor.mutate (mutation (target, 25));
        target.advanceHost ();
        interceptor.triggerReleased ();

        assertEquals (10, target.requested);
    }


    @Test
    void shutdownRequestsBestEffortRestoration ()
    {
        final FakeTarget target = new FakeTarget ("tempo", 128);
        final SnapbackInterceptor interceptor = interceptor ();

        interceptor.triggerPressed ();
        interceptor.mutate (mutation (target, 140));
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
        final SnapbackInterceptor interceptor = new SnapbackInterceptor (request -> request.mutation ().run (), warnings::add);

        interceptor.triggerPressed ();
        for (int index = 0; index < 16; index++)
            interceptor.mutate (mutation (new FakeTarget ("target-" + index, index), index + 100));

        final FakeTarget overflow = new FakeTarget ("overflow", 99);
        interceptor.mutate (mutation (overflow, 127));

        assertEquals (99, overflow.requested);
        assertEquals (16, interceptor.captureCount ());
        assertFalse (warnings.isEmpty ());
    }


    private static SnapbackInterceptor interceptor ()
    {
        return new SnapbackInterceptor (request -> request.mutation ().run (), ignored -> {
            // No-op warning sink.
        });
    }


    private static ParameterMutationRequest mutation (final FakeTarget target, final double value)
    {
        return ParameterMutationRequest.snapback (target, () -> target.request (value));
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
