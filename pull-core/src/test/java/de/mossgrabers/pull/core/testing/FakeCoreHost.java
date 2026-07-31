// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.testing;

import de.mossgrabers.pull.core.api.ControlId;
import de.mossgrabers.pull.core.api.ControllerCore;
import de.mossgrabers.pull.core.api.ControllerSnapshot;
import de.mossgrabers.pull.core.api.ShellCapabilities;
import de.mossgrabers.pull.core.api.StateEnvelope;
import de.mossgrabers.pull.core.api.TimerId;
import de.mossgrabers.pull.core.api.event.ButtonInputEvent;
import de.mossgrabers.pull.core.api.event.TimerElapsedEvent;
import de.mossgrabers.pull.core.api.event.TouchInputEvent;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Small deterministic stand-in for the stable shell.
 */
final class FakeCoreHost
{
    private static final int MAX_TIMER_DISPATCHES_PER_ADVANCE = 10_000;

    private final ControllerCore core;
    private final FakeMonotonicTime time = new FakeMonotonicTime ();
    private final RecordingEffectExecutor effectExecutor = new RecordingEffectExecutor ();
    private final Set<ControlId> pressedControls = new LinkedHashSet<> ();
    private final Set<ControlId> touchedControls = new LinkedHashSet<> ();
    private final ShellCapabilities capabilities;
    private long revision;
    private long eventSequence;


    /**
     * Constructor.
     *
     * @param core The core under test
     * @param capabilities Fake shell capabilities
     */
    FakeCoreHost (final ControllerCore core, final ShellCapabilities capabilities)
    {
        this.core = Objects.requireNonNull (core, "core");
        this.capabilities = Objects.requireNonNull (capabilities, "capabilities");
    }


    /**
     * Start the core.
     *
     * @param previousState Optional checkpoint
     */
    void start (final Optional<StateEnvelope> previousState)
    {
        this.effectExecutor.apply (this.core.start (this.snapshot (), previousState));
    }


    /**
     * Deliver a press or release after updating authoritative held state.
     *
     * @param controlId The control
     * @param pressed True for pressed
     */
    void button (final ControlId controlId, final boolean pressed)
    {
        if (pressed)
            this.pressedControls.add (controlId);
        else
            this.pressedControls.remove (controlId);

        this.revision++;
        this.eventSequence++;
        this.effectExecutor.apply (this.core.handle (new ButtonInputEvent (this.eventSequence, this.time.nowNanos (), controlId, pressed), this.snapshot ()));
    }


    /**
     * Deliver a touch transition after updating authoritative held state.
     *
     * @param controlId The control
     * @param touched True for touched
     */
    void touch (final ControlId controlId, final boolean touched)
    {
        if (touched)
            this.touchedControls.add (controlId);
        else
            this.touchedControls.remove (controlId);

        this.revision++;
        this.eventSequence++;
        this.effectExecutor.apply (this.core.handle (new TouchInputEvent (this.eventSequence, this.time.nowNanos (), controlId, touched), this.snapshot ()));
    }


    /**
     * Advance fake time and serially deliver timers due at the resulting time. Each event is
     * stamped with the final advanced time, and effects are applied before selecting the next
     * timer so callbacks may cancel or replace one another.
     *
     * @param duration The duration
     */
    void advance (final Duration duration)
    {
        this.time.advance (duration);
        int dispatchCount = 0;
        while (true)
        {
            final Optional<TimerId> timerId = this.effectExecutor.takeNextDueTimer (this.time.nowNanos ());
            if (timerId.isEmpty ())
                return;
            if (dispatchCount >= MAX_TIMER_DISPATCHES_PER_ADVANCE)
                throw new IllegalStateException ("Timer dispatch limit exceeded");

            dispatchCount++;
            this.revision++;
            this.eventSequence++;
            this.effectExecutor.apply (this.core.handle (new TimerElapsedEvent (this.eventSequence, this.time.nowNanos (), timerId.get ()), this.snapshot ()));
        }
    }


    /**
     * Get the current checkpoint.
     *
     * @return The checkpoint
     */
    StateEnvelope checkpoint ()
    {
        return this.core.checkpoint ();
    }


    /**
     * Stop the core.
     */
    void stop ()
    {
        this.core.stop ();
    }


    /**
     * Get the fake effect executor for assertions.
     *
     * @return The executor
     */
    RecordingEffectExecutor effects ()
    {
        return this.effectExecutor;
    }


    /**
     * Get current fake time.
     *
     * @return Monotonic nanoseconds
     */
    long nowNanos ()
    {
        return this.time.nowNanos ();
    }


    private ControllerSnapshot snapshot ()
    {
        return new ControllerSnapshot (this.revision, this.time.nowNanos (), this.capabilities, this.pressedControls, this.touchedControls);
    }
}
