// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.testing;

import de.mossgrabers.pull.core.api.ControlId;
import de.mossgrabers.pull.core.api.ControllerCore;
import de.mossgrabers.pull.core.api.ControllerSnapshot;
import de.mossgrabers.pull.core.api.CoreResult;
import de.mossgrabers.pull.core.api.DesiredBridgeSubscriptions;
import de.mossgrabers.pull.core.api.DesiredControllerWorkspace;
import de.mossgrabers.pull.core.api.DesiredInputRoutes;
import de.mossgrabers.pull.core.api.ShellCapabilities;
import de.mossgrabers.pull.core.api.StateEnvelope;
import de.mossgrabers.pull.core.api.TimerId;
import de.mossgrabers.pull.core.api.effect.CancelTimerEffect;
import de.mossgrabers.pull.core.api.effect.CoreEffect;
import de.mossgrabers.pull.core.api.effect.ScheduleTimerEffect;
import de.mossgrabers.pull.core.api.event.CoreEvent;
import de.mossgrabers.pull.core.api.event.TimerElapsedEvent;
import de.mossgrabers.pull.core.api.output.DesiredHardwareOutput;
import de.mossgrabers.pull.core.api.output.RgbColor;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the deterministic offline shell harness.
 */
class FakeCoreHostTest
{
    @Test
    void advancesLogicalTimersWithoutSleeping ()
    {
        final DeterministicTimerCore core = new DeterministicTimerCore ();
        final FakeCoreHost host = new FakeCoreHost (core, ShellCapabilities.empty ());

        host.start (Optional.empty ());
        assertEquals (100, host.effects ().deadline (DeterministicTimerCore.TIMER_ID).orElseThrow ());

        host.advance (Duration.ofNanos (99));
        assertEquals (0, core.pulses ());
        assertEquals (99, host.nowNanos ());

        host.advance (Duration.ofNanos (1));
        assertEquals (1, core.pulses ());
        assertEquals (new RgbColor (0, 1, 0), host.effects ().desiredOutput ().lights ().get (DeterministicTimerCore.LIGHT_ID));
        assertEquals (200, host.effects ().deadline (DeterministicTimerCore.TIMER_ID).orElseThrow ());
    }


    @Test
    void restoresCheckpointIntoAFreshCore ()
    {
        final DeterministicTimerCore firstCore = new DeterministicTimerCore ();
        final FakeCoreHost firstHost = new FakeCoreHost (firstCore, ShellCapabilities.empty ());
        firstHost.start (Optional.empty ());
        firstHost.advance (Duration.ofNanos (100));
        final StateEnvelope checkpoint = firstHost.checkpoint ();

        final DeterministicTimerCore secondCore = new DeterministicTimerCore ();
        final FakeCoreHost secondHost = new FakeCoreHost (secondCore, ShellCapabilities.empty ());
        secondHost.start (Optional.of (checkpoint));
        secondHost.advance (Duration.ofNanos (100));

        assertEquals (2, secondCore.pulses ());
    }


    @Test
    void updatesHeldStateBeforeDeliveringInputs ()
    {
        final ControlId button = new ControlId ("button");
        final ControlId knob = new ControlId ("knob");
        final DeterministicTimerCore core = new DeterministicTimerCore ();
        final FakeCoreHost host = new FakeCoreHost (core, ShellCapabilities.empty ());
        host.start (Optional.empty ());

        host.button (button, true);
        assertTrue (core.lastSnapshot ().pressedControls ().contains (button));
        host.button (button, false);
        assertFalse (core.lastSnapshot ().pressedControls ().contains (button));

        host.touch (knob, true);
        assertTrue (core.lastSnapshot ().touchedControls ().contains (knob));
        host.touch (knob, false);
        assertFalse (core.lastSnapshot ().touchedControls ().contains (knob));
    }


    @Test
    void appliesEffectsInOrderAndReplacesTimersByIdentifier ()
    {
        final RecordingEffectExecutor executor = new RecordingEffectExecutor ();
        final TimerId timerId = new TimerId ("replace-me");
        final List<CoreEffect> effects = List.of (new ScheduleTimerEffect (timerId, 10), new ScheduleTimerEffect (timerId, 20), new CancelTimerEffect (timerId));

        executor.apply (result (new DesiredHardwareOutput (Map.of (new ControlId ("light"), new RgbColor (1, 2, 3))), effects.subList (0, 2)));
        assertEquals (20, executor.deadline (timerId).orElseThrow ());
        executor.apply (result (DesiredHardwareOutput.empty (), effects.subList (2, 3)));

        assertEquals (effects, executor.executionOrder ());
        assertTrue (executor.deadline (timerId).isEmpty ());
        assertEquals (DesiredHardwareOutput.empty (), executor.desiredOutput ());
    }


    @Test
    void timerCallbackCanCancelAnotherTimerDueInTheSameAdvance ()
    {
        final CancelingTimerCore core = new CancelingTimerCore ();
        final FakeCoreHost host = new FakeCoreHost (core, ShellCapabilities.empty ());
        host.start (Optional.empty ());

        host.advance (Duration.ofNanos (20));

        assertEquals (List.of (CancelingTimerCore.FIRST_TIMER), core.firedTimers);
    }


    @Test
    void identicalInputsProduceIdenticalResults ()
    {
        final DeterministicTimerCore firstCore = new DeterministicTimerCore ();
        final DeterministicTimerCore secondCore = new DeterministicTimerCore ();
        final FakeCoreHost firstHost = new FakeCoreHost (firstCore, ShellCapabilities.empty ());
        final FakeCoreHost secondHost = new FakeCoreHost (secondCore, ShellCapabilities.empty ());
        final ControlId button = new ControlId ("button");

        firstHost.start (Optional.empty ());
        secondHost.start (Optional.empty ());
        firstHost.button (button, true);
        secondHost.button (button, true);
        firstHost.advance (Duration.ofNanos (100));
        secondHost.advance (Duration.ofNanos (100));

        assertEquals (firstHost.effects ().executionOrder (), secondHost.effects ().executionOrder ());
        assertEquals (firstHost.effects ().desiredOutput (), secondHost.effects ().desiredOutput ());
        assertEquals (firstHost.checkpoint (), secondHost.checkpoint ());
    }


    private static final class CancelingTimerCore implements ControllerCore
    {
        private static final TimerId FIRST_TIMER = new TimerId ("first");
        private static final TimerId SECOND_TIMER = new TimerId ("second");

        private final List<TimerId> firedTimers = new ArrayList<> ();


        @Override
        public CoreResult start (final ControllerSnapshot snapshot, final Optional<StateEnvelope> previousState)
        {
            Objects.requireNonNull (snapshot, "snapshot");
            Objects.requireNonNull (previousState, "previousState");
            return result (DesiredHardwareOutput.empty (), List.of (new ScheduleTimerEffect (FIRST_TIMER, 10), new ScheduleTimerEffect (SECOND_TIMER, 20)));
        }


        @Override
        public CoreResult handle (final CoreEvent event, final ControllerSnapshot snapshot)
        {
            Objects.requireNonNull (snapshot, "snapshot");
            if (event instanceof final TimerElapsedEvent timer)
            {
                this.firedTimers.add (timer.timerId ());
                if (FIRST_TIMER.equals (timer.timerId ()))
                    return result (DesiredHardwareOutput.empty (), List.of (new CancelTimerEffect (SECOND_TIMER)));
            }
            return CoreResult.empty ();
        }


        @Override
        public StateEnvelope checkpoint ()
        {
            return new StateEnvelope ("test.cancel", 1, new byte [0]);
        }

    }


    private static CoreResult result (final DesiredHardwareOutput output, final List<CoreEffect> effects)
    {
        return new CoreResult (
            output,
            DesiredInputRoutes.empty (),
            DesiredBridgeSubscriptions.empty (),
            Map.of (),
            DesiredControllerWorkspace.empty (),
            de.mossgrabers.pull.core.api.DesiredControllerActions.empty (),
            de.mossgrabers.pull.core.api.DesiredParameterBanks.empty (),
            de.mossgrabers.pull.core.api.DesiredParameterInteraction.empty (),
            effects);
    }
}
