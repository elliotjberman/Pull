// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.testing;

import de.mossgrabers.pull.core.api.ControlId;
import de.mossgrabers.pull.core.api.ControllerCore;
import de.mossgrabers.pull.core.api.ControllerSnapshot;
import de.mossgrabers.pull.core.api.CoreResult;
import de.mossgrabers.pull.core.api.StateEnvelope;
import de.mossgrabers.pull.core.api.TimerId;
import de.mossgrabers.pull.core.api.effect.ScheduleTimerEffect;
import de.mossgrabers.pull.core.api.event.ButtonInputEvent;
import de.mossgrabers.pull.core.api.event.CoreEvent;
import de.mossgrabers.pull.core.api.event.TimerElapsedEvent;
import de.mossgrabers.pull.core.api.event.TouchInputEvent;
import de.mossgrabers.pull.core.api.output.DesiredHardwareOutput;
import de.mossgrabers.pull.core.api.output.RgbColor;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Stateful test core used to exercise the fake shell.
 */
final class DeterministicTimerCore implements ControllerCore
{
    static final TimerId TIMER_ID = new TimerId ("test-pulse");
    static final ControlId LIGHT_ID = new ControlId ("test-light");
    private static final String STATE_SCHEMA = "test.timer";
    private static final int STATE_VERSION = 1;
    private static final long TIMER_INTERVAL_NANOS = 100;

    private int pulses;
    private boolean running;
    private boolean stopped;
    private ControllerSnapshot lastSnapshot;


    /** {@inheritDoc} */
    @Override
    public CoreResult start (final ControllerSnapshot snapshot, final Optional<StateEnvelope> previousState)
    {
        Objects.requireNonNull (snapshot, "snapshot");
        Objects.requireNonNull (previousState, "previousState");
        if (this.running || this.stopped)
            throw new IllegalStateException ("Core can only be started once");

        previousState.ifPresent (this::restore);
        this.running = true;
        this.lastSnapshot = snapshot;
        return new CoreResult (this.output (), List.of (new ScheduleTimerEffect (TIMER_ID, Math.addExact (snapshot.monotonicTimeNanos (), TIMER_INTERVAL_NANOS))));
    }


    /** {@inheritDoc} */
    @Override
    public CoreResult handle (final CoreEvent event, final ControllerSnapshot snapshot)
    {
        this.requireRunning ();
        Objects.requireNonNull (event, "event");
        this.lastSnapshot = Objects.requireNonNull (snapshot, "snapshot");

        if (event instanceof final ButtonInputEvent button)
        {
            if (snapshot.pressedControls ().contains (button.controlId ()) != button.pressed ())
                throw new IllegalStateException ("Button state was not applied before event delivery");
            return new CoreResult (this.output (), List.of ());
        }

        if (event instanceof final TouchInputEvent touch)
        {
            if (snapshot.touchedControls ().contains (touch.controlId ()) != touch.touched ())
                throw new IllegalStateException ("Touch state was not applied before event delivery");
            return new CoreResult (this.output (), List.of ());
        }

        if (event instanceof final TimerElapsedEvent timer && TIMER_ID.equals (timer.timerId ()))
        {
            this.pulses = Math.incrementExact (this.pulses);
            return new CoreResult (this.output (), List.of (new ScheduleTimerEffect (TIMER_ID, Math.addExact (timer.monotonicTimeNanos (), TIMER_INTERVAL_NANOS))));
        }

        return new CoreResult (this.output (), List.of ());
    }


    /** {@inheritDoc} */
    @Override
    public StateEnvelope checkpoint ()
    {
        this.requireRunning ();
        return new StateEnvelope (STATE_SCHEMA, STATE_VERSION, ByteBuffer.allocate (Integer.BYTES).putInt (this.pulses).array ());
    }


    /** {@inheritDoc} */
    @Override
    public void stop ()
    {
        this.running = false;
        this.stopped = true;
    }


    /**
     * Get the pulse count.
     *
     * @return The count
     */
    int pulses ()
    {
        return this.pulses;
    }


    /**
     * Get the last authoritative snapshot.
     *
     * @return The snapshot
     */
    ControllerSnapshot lastSnapshot ()
    {
        return this.lastSnapshot;
    }


    private void restore (final StateEnvelope state)
    {
        if (!STATE_SCHEMA.equals (state.schema ()) || STATE_VERSION != state.version ())
            return;

        final byte [] payload = state.payload ();
        if (payload.length != Integer.BYTES)
            throw new IllegalArgumentException ("Test checkpoint payload must contain one integer");
        this.pulses = ByteBuffer.wrap (payload).getInt ();
    }


    private DesiredHardwareOutput output ()
    {
        return new DesiredHardwareOutput (Map.of (LIGHT_ID, new RgbColor (0, Math.min (this.pulses, 255), 0)));
    }


    private void requireRunning ()
    {
        if (!this.running)
            throw new IllegalStateException ("Core is not running");
    }
}
