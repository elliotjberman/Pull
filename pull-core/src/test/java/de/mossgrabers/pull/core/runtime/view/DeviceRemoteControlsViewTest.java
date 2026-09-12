// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.api.*;
import de.mossgrabers.pull.core.api.effect.*;
import de.mossgrabers.pull.core.api.event.*;
import de.mossgrabers.pull.core.api.output.DisplayCommand;
import de.mossgrabers.pull.core.runtime.PullCoreProvider;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Production page composition and interaction lifecycle over independently advanced host samples. */
class DeviceRemoteControlsViewTest
{
    private static final ControlId DELETE = PushControlIds.button ("DELETE");
    private static final ControlId SHIFT = PushControlIds.button ("SHIFT");
    private static final ControlId SELECT = PushControlIds.button ("SELECT");
    private static final EncoderConfigurationSnapshot CONFIG = new EncoderConfigurationSnapshot (true, 1024, 10, 0, -90);

    @Test
    void allEightEncodersUseRetainedTargetsAndPreserveNormalShiftAndSelectVariants ()
    {
        for (final Set<ControlId> modifiers: List.of (Set.<ControlId>of (), Set.of (SELECT), Set.of (SHIFT), Set.of (SHIFT, SELECT)))
        {
            final Fixture f = new Fixture ();
            f.pressed = modifiers;
            for (int index = 0; index < 8; index++)
            {
                f.turn (index, 2);
                assertEquals (List.of (new AdjustParameterValueEffect (f.target (index), modifiers.contains (SHIFT) ? 2 : 20)), f.result.effects ());
                assertEquals (InputRouteMode.EXCLUSIVE, f.result.desiredInputRoutes ().modeOrNull (knob (index), InputKind.RELATIVE));
                assertEquals (InputRouteMode.EXCLUSIVE, f.result.desiredInputRoutes ().modeOrNull (knob (index), InputKind.TOUCH));
            }
            assertTrue (f.result.desiredParameterBanks ().banks ().contains (ParameterBankId.SELECTED_DEVICE_REMOTE));
            assertFalse (f.result.desiredParameterBanks ().banks ().contains (ParameterBankId.ACTIVE));
            assertTrue (hasText (f.result, "Remote 0"));
            assertFalse (hasText (f.result, "Stale raw parameter"));
            assertTrue (hasText (f.result, "Device menu"), "The frozen navigation menu remains visible");
            assertFalse (f.result.desiredControllerActions ().bindings ().stream ().anyMatch (binding ->
                binding.controlId ().equals (PushControlIds.button ("ROW1_1")) || binding.controlId ().equals (PushControlIds.button ("ROW2_1"))));
        }
    }

    @Test
    void submittedValueAndTouchRequestsDoNotOptimisticallyChangeTheDisplay ()
    {
        final Fixture f = new Fixture ();
        final var before = f.result.desiredOutput ().display ();
        f.touch (0, InputPhase.BEGIN);
        assertEquals (Map.of (knob (0), f.target (0)), f.result.desiredParameterTouches ().targets ());
        assertEquals (before, f.result.desiredOutput ().display ());
        f.turn (0, 1);
        assertEquals (List.of (new AdjustParameterValueEffect (f.target (0), 10)), f.result.effects ());
        assertEquals (before, f.result.desiredOutput ().display ());
        f.leases = Set.of (f.target (0));
        f.value = 700;
        f.displayedValue = "Host acknowledged";
        f.tick ();
        assertTrue (hasText (f.result, "Host acknowledged"));
        assertNotEquals (before, f.result.desiredOutput ().display ());
        f.touch (0, InputPhase.END);
        assertTrue (f.result.desiredParameterTouches ().targets ().isEmpty ());
        assertTrue (f.leases.contains (f.target (0)), "Desired cleanup is not host retirement");
    }

    @Test
    void shiftCapturesAllEightNamedBaselinesAndRestoresOnlyThroughLaterHostReadback ()
    {
        final Fixture f = new Fixture ();
        f.shift (InputPhase.BEGIN);
        final Map<ParameterTargetRef, Double> baselines = new LinkedHashMap<> ();
        for (int index = 0; index < 8; index++)
        {
            f.turn (index, 2);
            assertEquals (List.of (new AdjustParameterValueEffect (f.target (index), 2)), f.result.effects ());
            baselines.put (f.target (index), Double.valueOf (512));
        }
        assertEquals (baselines, f.result.desiredParameterInteraction ().baselines ());
        f.value = 700; // Bitwig applies the requested changes independently.
        f.tick ();
        f.shift (InputPhase.END);
        assertTrue (f.result.effects ().isEmpty ());
        f.hostTick ();
        assertTrue (f.result.effects ().isEmpty ());
        f.hostTick ();
        assertEquals (baselines.entrySet ().stream ().map (entry -> new SetParameterValueEffect (entry.getKey (), entry.getValue ().doubleValue ())).toList (), f.result.effects ());
        assertEquals (700, f.value, "Restoration submission does not acknowledge host state");
        assertEquals (baselines, f.result.desiredParameterInteraction ().baselines ());
        f.value = 512;
        f.hostTick ();
        assertEquals (baselines, f.result.desiredParameterInteraction ().baselines ());
        f.hostTick ();
        assertTrue (f.result.desiredParameterInteraction ().baselines ().isEmpty ());
    }

    @Test
    void deleteTouchResetsOnceAndReleaseStopsAutomationThroughTheSharedLifecycle ()
    {
        final Fixture f = new Fixture ();
        f.pressed = Set.of (DELETE, SHIFT, SELECT);
        f.writingAutomation = true;
        f.touch (7, InputPhase.BEGIN);
        assertEquals (List.of (new ConsumeControllerButtonEffect (DELETE), new ResetParameterEffect (f.target (7))), f.result.effects ());
        assertEquals (Map.of (knob (7), f.target (7)), f.result.desiredParameterTouches ().targets ());
        f.touch (7, InputPhase.LONG);
        assertTrue (f.result.effects ().isEmpty ());
        f.touch (7, InputPhase.BEGIN);
        assertTrue (f.result.effects ().isEmpty (), "Duplicate BEGIN must not reset again");
        f.touch (7, InputPhase.END);
        assertEquals (List.of (new SetAutomationWriteEffect ("project", false)), f.result.effects ());
        assertTrue (f.result.desiredParameterTouches ().targets ().isEmpty ());
        assertTrue (f.writingAutomation, "Automation effect waits for later host state");
        f.touch (7, InputPhase.END);
        assertTrue (f.result.effects ().isEmpty ());
    }

    @Test
    void sourceChangeCancelsCapturedTouchAndItsPhysicalTailCannotControlTheNewDevice ()
    {
        final Fixture f = new Fixture ();
        f.writingAutomation = true;
        f.touch (0, InputPhase.BEGIN);
        final ParameterTargetRef old = f.target (0);
        f.leases = Set.of (old);
        f.tick ();
        f.observedOwner = "new-device-page";
        f.tick (); // menu source advanced while retained parameter data still names its predecessor
        assertTrue (f.result.desiredParameterTouches ().targets ().isEmpty ());
        assertEquals (List.of (new SetAutomationWriteEffect ("project", false)), f.result.effects ());
        assertFalse (hasText (f.result, "Remote 0"));
        f.parameterOwner = f.observedOwner;
        f.assignment++;
        f.tick ();
        f.turn (0, 1);
        assertTrue (f.result.effects ().isEmpty (), "A cancelled held gesture never adopts a new source");
        f.touch (0, InputPhase.END);
        assertTrue (f.result.effects ().isEmpty (), "The cancelled END must not run release policy again");
        f.leases = Set.of (); // independent host retirement acknowledgement
        f.tick ();
        f.touch (0, InputPhase.BEGIN);
        assertEquals (Map.of (knob (0), f.target (0)), f.result.desiredParameterTouches ().targets ());
        assertNotEquals (old, f.target (0));
        f.turn (0, 1);
        assertEquals (List.of (new AdjustParameterValueEffect (f.target (0), 10)), f.result.effects ());
    }

    @Test
    void mismatchedOwnerPageSlotOrModeSuppressesResetTouchTurnAndParameterFeedback ()
    {
        for (int mismatch = 0; mismatch < 5; mismatch++)
        {
            final Fixture f = new Fixture ();
            switch (mismatch)
            {
                case 0 -> f.observedOwner = "other-source";
                case 1 -> f.observedPage = 1;
                case 2 -> f.slotOffset = 1;
                case 3 -> f.observedOwner = "";
                default -> f.observedMode = "DEVICE_CHAINS";
            }
            f.pressed = Set.of (DELETE, SHIFT, SELECT);
            f.tick ();
            f.touch (0, InputPhase.BEGIN);
            assertEquals (mismatch == 4 ? List.of () : List.of (new ConsumeControllerButtonEffect (DELETE)), f.result.effects ());
            assertTrue (f.result.desiredParameterTouches ().targets ().isEmpty ());
            f.turn (0, 1);
            assertTrue (f.result.effects ().isEmpty ());
            assertFalse (hasText (f.result, "Remote 0"));
            f.touch (0, InputPhase.END);
            assertTrue (f.result.effects ().isEmpty ());
        }
    }

    @Test
    void unavailableSlotConsumesDeleteWithoutAParameterAndStillStopsAutomationOnRelease ()
    {
        for (final boolean pending: List.of (false, true))
        {
            final Fixture f = new Fixture ();
            f.missingSlots = Set.of (7);
            if (pending) f.observedOwner = "";
            f.pressed = Set.of (DELETE);
            f.writingAutomation = true;
            f.tick ();
            f.touch (7, InputPhase.BEGIN);
            assertEquals (List.of (new ConsumeControllerButtonEffect (DELETE)), f.result.effects ());
            assertTrue (f.result.desiredParameterTouches ().targets ().isEmpty ());
            f.turn (7, 1);
            assertTrue (f.result.effects ().isEmpty ());
            f.touch (7, InputPhase.END);
            assertEquals (List.of (new SetAutomationWriteEffect ("project", false)), f.result.effects ());
            assertTrue (f.writingAutomation, "Cleanup submission is not host read-back");
        }
    }

    @Test
    void anEmptyTouchCannotAdoptAParameterThatBecomesReadyWhileHeld ()
    {
        final Fixture f = new Fixture ();
        f.missingSlots = Set.of (0);
        f.tick ();
        f.touch (0, InputPhase.BEGIN);
        f.missingSlots = Set.of ();
        f.tick ();
        f.turn (0, 1);
        assertTrue (f.result.effects ().isEmpty ());
        assertTrue (f.result.desiredParameterTouches ().targets ().isEmpty ());
        f.touch (0, InputPhase.END);
        f.touch (0, InputPhase.BEGIN);
        assertEquals (Map.of (knob (0), f.target (0)), f.result.desiredParameterTouches ().targets ());
    }

    @Test
    void absoluteRemotePageBeyondFirstWindowJoinsTheVisibleMenuAndLeavingDeviceRetiresTouch ()
    {
        final Fixture f = new Fixture ();
        f.observedPage = 1;
        f.parameterPage = 9;
        f.tick ();
        f.touch (0, InputPhase.BEGIN);
        assertEquals (Map.of (knob (0), f.target (0)), f.result.desiredParameterTouches ().targets ());
        f.leases = Set.of (f.target (0));
        f.page ("TRACK");
        assertTrue (f.result.desiredParameterTouches ().targets ().isEmpty ());
        f.page ("DEVICE_PARAMS");
        f.turn (0, 1);
        assertTrue (f.result.effects ().isEmpty ());
        f.touch (0, InputPhase.END);
        f.leases = Set.of ();
        f.tick ();
        f.touch (0, InputPhase.BEGIN);
        assertEquals (Map.of (knob (0), f.target (0)), f.result.desiredParameterTouches ().targets ());
    }

    private static boolean hasText (final CoreResult result, final String text)
    {
        return result.desiredOutput ().display ().commands ().stream ().anyMatch (command ->
            command instanceof DisplayCommand.TextBox box && text.equals (box.text ()) || command instanceof DisplayCommand.TextAt label && text.equals (label.text ()));
    }

    private static ControlId knob (final int index) { return PushControlIds.continuous ("KNOB" + (index + 1)); }

    private static final class Fixture
    {
        private final ControllerCore core = new PullCoreProvider ().create ();
        private CoreResult result;
        private String observedOwner = "retained-device-page";
        private String parameterOwner = this.observedOwner;
        private String observedMode = "DEVICE_PARAMS";
        private int observedPage;
        private int parameterPage;
        private int slotOffset;
        private long assignment = 1;
        private double value = 512;
        private String displayedValue = "Observed value";
        private boolean writingAutomation;
        private Set<ControlId> touched = Set.of ();
        private Set<ControlId> pressed = Set.of ();
        private Set<ParameterTargetRef> leases = Set.of ();
        private Set<Integer> missingSlots = Set.of ();
        private LegacyControllerPageRequests requests = LegacyControllerPageRequests.empty ();
        private long revision;

        private Fixture ()
        {
            this.result = this.core.start (this.snapshot (), Optional.empty ());
            this.page ("DEVICE_PARAMS");
        }

        private ParameterTargetRef target (final int index) { return new ParameterTargetRef (ParameterTargetKind.LIVE, this.parameterOwner + ":" + index, this.assignment); }
        private void tick () { this.revision++; this.result = this.core.handle (new SnapshotChangedEvent (this.revision, this.revision), this.snapshot ()); }
        private void hostTick () { this.revision++; this.result = this.core.handle (new ControllerTickEvent (this.revision, this.revision), this.snapshot ()); }
        private void shift (final InputPhase phase)
        {
            this.pressed = phase == InputPhase.END ? Set.of () : Set.of (SHIFT);
            this.revision++;
            this.result = this.core.handle (new ControllerInputEvent (this.revision, this.revision, SHIFT, InputKind.BUTTON, phase, phase == InputPhase.END ? 0 : 127), this.snapshot ());
        }
        private void touch (final int index, final InputPhase phase)
        {
            this.touched = phase == InputPhase.END ? Set.of () : Set.of (knob (index));
            this.revision++;
            this.result = this.core.handle (new ControllerInputEvent (this.revision, this.revision, knob (index), InputKind.TOUCH, phase, phase == InputPhase.END ? 0 : 127), this.snapshot ());
        }
        private void turn (final int index, final int amount)
        {
            this.revision++;
            this.result = this.core.handle (new ControllerInputEvent (this.revision, this.revision, knob (index), InputKind.RELATIVE, InputPhase.UPDATE, amount), this.snapshot ());
        }
        private void page (final String alias)
        {
            final var state = this.result.desiredControllerState ().page ();
            this.requests = new LegacyControllerPageRequests (state.acknowledgedRequestSequence (), List.of (new LegacyControllerPageRequest (
                state.acknowledgedRequestSequence () + 1, state.revision (), state.temporaryToken (), LegacyControllerPageRequest.Operation.SELECT, alias)));
            this.tick ();
            this.requests = new LegacyControllerPageRequests (this.result.desiredControllerState ().page ().acknowledgedRequestSequence (), List.of ());
        }
        private ControllerSnapshot snapshot ()
        {
            final Map<ParameterSlot, ParameterTargetSnapshot> parameters = new LinkedHashMap<> ();
            for (int index = 0; index < 8; index++)
                if (!this.missingSlots.contains (Integer.valueOf (index)))
                    parameters.put (ParameterSlot.selectedDeviceRemote (index), new ParameterTargetSnapshot (this.target (index), "Remote " + index,
                        this.value, this.value, this.displayedValue, 128, 0, Optional.empty (), new ParameterTargetIdentitySnapshot (
                            "retained-device-remote", this.parameterOwner, this.parameterPage, index + this.slotOffset)));
            final var device = new DevicePageState.Device (true, "Device", true, true, true, false, false, false, false, 0,
                List.of ("Device menu"), List.of ("Page A", "Page B"), this.observedPage, List.of ());
            final var selection = new DevicePageState.Selection (true, false, true, true, 0, 0, 0, false, false, false, 0, "track");
            final var page = new DevicePageState (DevicePageState.Kind.PARAMETERS, device, List.of (), DevicePageState.Channel.empty (),
                List.of (new DevicePageState.Parameter (true, "Stale raw parameter", 0, 0, "Stale raw value", true, false)), List.of (), selection, this.observedOwner);
            final var e = ControllerBridgeSnapshot.empty ();
            final var bridge = new ControllerBridgeSnapshot (e.transport (), e.selectedTrack (), e.sessionBank (), e.layout (), e.noteView (), e.noteRepeat (), e.drum (),
                new ParameterBridgeSnapshot (parameters, Map.of (), this.leases), e.controllerMappingFeedback (), e.master (), e.project (),
                new AutomationSnapshot ("project", this.writingAutomation, true), CONFIG, e.currentTrackBank (), e.transportSettings (), e.controllerSettings (), e.applicationUi (),
                this.requests, e.browser (), e.controllerHardware (), new ControllerPageDisplaySnapshot (this.observedMode, page));
            return new ControllerSnapshot (this.revision, this.revision, new PullCoreProvider ().descriptor ().requiredCapabilities (), bridge,
                ClipCatalogSnapshot.empty (), Map.of (), Map.of (), Optional.empty (), this.pressed, this.touched);
        }
    }
}
