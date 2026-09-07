// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.testing;

import de.mossgrabers.pull.core.api.*;
import de.mossgrabers.pull.core.api.effect.*;
import de.mossgrabers.pull.core.api.event.InputKind;
import de.mossgrabers.pull.core.api.output.*;
import de.mossgrabers.pull.core.runtime.PullCoreProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.util.*;
import static de.mossgrabers.pull.core.api.LegacyControllerPageRequest.Operation.*;
import static de.mossgrabers.pull.core.api.effect.SetControllerIntegerSettingEffect.Setting.*;
import static org.junit.jupiter.api.Assertions.*;

/** Ribbon preferences through routed input, separately advanced read-back and complete output. */
class RibbonPageCoreTest
{
    private static final EncoderConfigurationSnapshot ENCODERS = new EncoderConfigurationSnapshot (true, 128, 10, 0, 0);
    private static final RgbColor OFF = new RgbColor (0, 0, 0);
    private static final RgbColor ON = new RgbColor (30, 30, 30);
    private static final RgbColor SELECTED = new RgbColor (255, 255, 255);
    private static final ParameterTargetRef TEMPO = new ParameterTargetRef (ParameterTargetKind.LIVE, "ribbon-barrier-tempo", 1);

    @ParameterizedTest
    @CsvSource ({ "ROW1_1, RIBBON_FUNCTION, 0", "ROW1_2, RIBBON_FUNCTION, 1", "ROW1_3, RIBBON_FUNCTION, 2", "ROW1_4, RIBBON_FUNCTION, 3", "ROW1_5, RIBBON_FUNCTION, 4", "ROW1_6, RIBBON_FUNCTION, 5",
        "ROW2_2, RIBBON_CC, 1", "ROW2_3, RIBBON_CC, 11", "ROW2_4, RIBBON_CC, 7", "ROW2_5, RIBBON_CC, 64", "ROW2_6, RIBBON_NOTE_REPEAT, 0", "ROW2_7, RIBBON_NOTE_REPEAT, 1", "ROW2_8, RIBBON_NOTE_REPEAT, 2" })
    void rowChoicesSubmitOnReleaseAndRenderOnlyLaterObservedSelection (final String button, final SetControllerIntegerSettingEffect.Setting setting, final int value)
    {
        final var initial = state (setting == RIBBON_FUNCTION ? (value + 1) % 6 : 0, 74, setting == RIBBON_NOTE_REPEAT ? (value + 1) % 3 : 0);
        final var host = ribbon (initial, ENCODERS);
        final var display = host.effects ().desiredOutput ().display ();
        for (final String modifier: List.of ("SHIFT", "DELETE", "SELECT")) edge (host, modifier, true);
        edge (host, button, true);
        host.controllerButtonLong (PushControlIds.button (button));
        assertTrue (writes (host).isEmpty ());
        edge (host, button, false);
        assertEquals (List.of (new SetControllerIntegerSettingEffect (setting, value)), writes (host));
        assertEquals (display, host.effects ().desiredOutput ().display (), "the requested setting is not observed state");
        assertEquals (ON, light (host, button));
        edge (host, button, false);
        assertEquals (1, writes (host).size (), "an unmatched release must not submit the action twice");
        observe (host, state (setting == RIBBON_FUNCTION ? value : initial.function (), setting == RIBBON_CC ? value : initial.cc (), setting == RIBBON_NOTE_REPEAT ? value : initial.noteRepeat ()));
        assertEquals (setting == RIBBON_CC ? ON : SELECTED, light (host, button), "CC presets remain action lights even when the numeric CC equals that preset");
        if (setting == RIBBON_CC) assertTrue (text (host).contains (Integer.toString (value)));
        else
        {
            assertNotEquals (display, host.effects ().desiredOutput ().display ());
            assertEquals (ON, light (host, setting == RIBBON_FUNCTION ? "ROW1_" + (initial.function () + 1) : "ROW2_" + (initial.noteRepeat () + 6)));
        }
    }

    @ParameterizedTest
    @CsvSource ({ "ROW1_7, true", "ROW1_8, true", "ROW1_7, false", "ROW1_8, false" })
    void bothUnlitReturnButtonsRestoreTheExactUnderlyingPageOnRelease (final String button, final boolean available)
    {
        final var host = ribbon (available ? state (0, 74, 0) : RibbonSettingsSnapshot.empty (), ENCODERS);
        assertEquals (OFF, light (host, button));
        edge (host, button, true);
        host.controllerButtonLong (PushControlIds.button (button));
        assertEquals ("RIBBON", page (host));
        edge (host, button, false);
        assertEquals ("PAN", page (host));
        assertTrue (writes (host).isEmpty ());
    }

    @Test
    void firstKnobUsesTheCcRangeWhileOtherKnobsTouchesAndDeleteAreInert ()
    {
        final var host = ribbon (state (0, 74, 0), ENCODERS);
        final var display = host.effects ().desiredOutput ().display ();
        edge (host, "DELETE", true);
        final int before = host.effects ().executionOrder ().size ();
        for (int index = 1; index <= 8; index++)
        {
            host.controllerTouch (knob (index), true);
            host.controllerTouch (knob (index), false);
            if (index > 1) host.controllerMotion (knob (index), InputKind.RELATIVE, 20);
        }
        assertTrue (host.effects ().desiredBridgeSubscriptions ().domains ().containsAll (Set.of (BridgeSubscription.CONTROLLER_SETTINGS, BridgeSubscription.ENCODER_CONFIGURATION)));
        assertEquals (OFF, light (host, "ROW2_1"));
        click (host, "ROW2_1");
        assertEquals (before, host.effects ().executionOrder ().size ());
        assertEquals (display, host.effects ().desiredOutput ().display ());
        host.controllerMotion (knob (1), InputKind.RELATIVE, 5);
        assertEquals (List.of (new SetControllerIntegerSettingEffect (RIBBON_CC, 79)), writes (host));
        assertTrue (text (host).contains ("74"));
        assertTrue (effects (host, ConsumeControllerButtonEffect.class).isEmpty (), "Delete does not add a Ribbon reset gesture");
        observe (host, state (0, 79, 0));
        host.controllerMotion (knob (1), InputKind.RELATIVE, 1000);
        assertEquals (new SetControllerIntegerSettingEffect (RIBBON_CC, 127), lastWrite (host));
        observe (host, state (0, 127, 0));
        final int maximum = writes (host).size ();
        host.controllerMotion (knob (1), InputKind.RELATIVE, 1);
        assertEquals (maximum, writes (host).size ());
        host.controllerMotion (knob (1), InputKind.RELATIVE, -1000);
        assertEquals (new SetControllerIntegerSettingEffect (RIBBON_CC, 0), lastWrite (host));
        observe (host, state (0, 0, 0));
        final int minimum = writes (host).size ();
        host.controllerMotion (knob (1), InputKind.RELATIVE, -1);
        assertEquals (minimum, writes (host).size ());
    }

    @Test
    void allThreePreferenceLanesWaitIndependentlyForReadBackBeforeFollowingTheLatestIntent ()
    {
        final var host = ribbon (state (0, 74, 0), ENCODERS);
        click (host, "ROW1_2");
        click (host, "ROW1_3");
        click (host, "ROW2_3");
        click (host, "ROW2_5");
        click (host, "ROW2_7");
        click (host, "ROW2_8");
        host.controllerTick ();
        assertEquals (Set.of (new SetControllerIntegerSettingEffect (RIBBON_FUNCTION, 1), new SetControllerIntegerSettingEffect (RIBBON_CC, 11), new SetControllerIntegerSettingEffect (RIBBON_NOTE_REPEAT, 1)), Set.copyOf (writes (host)));
        assertEquals (3, writes (host).size ());
        assertEquals (SELECTED, light (host, "ROW1_1"));
        assertEquals (SELECTED, light (host, "ROW2_6"));
        assertTrue (text (host).contains ("74"));
        observe (host, state (1, 11, 1));
        assertEquals (Set.of (new SetControllerIntegerSettingEffect (RIBBON_FUNCTION, 2), new SetControllerIntegerSettingEffect (RIBBON_CC, 64), new SetControllerIntegerSettingEffect (RIBBON_NOTE_REPEAT, 2)), Set.copyOf (writes (host).subList (3, 6)));
        assertEquals (SELECTED, light (host, "ROW1_2"));
        assertEquals (SELECTED, light (host, "ROW2_7"));
        assertTrue (text (host).contains ("11"));
        observe (host, state (2, 64, 2));
        assertEquals (6, writes (host).size ());
        assertEquals (SELECTED, light (host, "ROW1_3"));
        assertEquals (SELECTED, light (host, "ROW2_8"));
        assertTrue (text (host).contains ("64"));
        assertFalse (host.effects ().executionRequirements ().ticksRequested ());
    }

    @Test
    void pageExitInvalidatesPendingWritesAndHeldRowsEvenAfterReturning ()
    {
        final var host = ribbon (state (0, 74, 0), ENCODERS);
        host.controllerMotion (knob (1), InputKind.RELATIVE, 2);
        host.controllerMotion (knob (1), InputKind.RELATIVE, 3);
        edge (host, "ROW1_2", true);
        host.requestPage (SELECT, "PAN");
        assertEquals ("PAN", page (host));
        observe (host, state (0, 76, 0));
        host.requestPage (TEMPORARY, "RIBBON");
        edge (host, "ROW1_2", false);
        assertEquals (List.of (new SetControllerIntegerSettingEffect (RIBBON_CC, 76)), writes (host));
        host.controllerMotion (knob (1), InputKind.RELATIVE, 1);
        assertEquals (new SetControllerIntegerSettingEffect (RIBBON_CC, 77), lastWrite (host));
    }

    @Test
    void deferredReturnBeforeAnAlreadyReleasedPresetCancelsThePresetInTheSameAdmissionBatch ()
    {
        final var host = ribbon (state (0, 74, 0), ENCODERS);
        host.bridge (bridgeWithTempo (100));
        edge (host, "SHIFT", true);
        host.parameterMutation (PushControlIds.continuous ("TEMPO"), new ParameterTargetSnapshot (TEMPO, 100, 0));
        host.bridge (bridgeWithTempo (40));
        click (host, "ROW1_7");
        assertEquals (1, host.effects ().desiredParameterInteraction ().pendingActionCount ());
        click (host, "ROW2_2");
        assertEquals (2, host.effects ().desiredParameterInteraction ().pendingActionCount ());
        assertTrue (writes (host).isEmpty ());
        host.controllerTick ();
        host.controllerTick ();
        assertEquals ("RIBBON", page (host), "submitted restoration cannot admit either action");

        host.bridge (bridgeWithTempo (100));
        host.controllerTick ();
        host.controllerTick ();
        assertEquals ("PAN", page (host));
        assertEquals (0, host.effects ().desiredParameterInteraction ().pendingActionCount ());
        assertTrue (writes (host).isEmpty (), "the preceding return invalidates the preset before it can first-submit");
        host.requestPage (TEMPORARY, "RIBBON");
        click (host, "ROW2_2");
        assertEquals (List.of (new SetControllerIntegerSettingEffect (RIBBON_CC, 1)), writes (host), "a new row gesture remains available after cancellation");
    }

    @Test
    void unavailableSettingsCannotAcquireAChoiceAndMissingCalibrationBlocksOnlyTurns ()
    {
        final var host = ribbon (RibbonSettingsSnapshot.empty (), ENCODERS);
        for (int index = 1; index <= 8; index++)
        {
            assertEquals (OFF, light (host, "ROW1_" + index));
            assertEquals (OFF, light (host, "ROW2_" + index));
        }
        host.controllerMotion (knob (1), InputKind.RELATIVE, 1);
        edge (host, "ROW1_2", true);
        observe (host, state (0, 74, 0));
        edge (host, "ROW1_2", false);
        assertTrue (writes (host).isEmpty (), "later availability cannot revive a choice pressed without state");
        host.bridge (bridge (state (0, 74, 0), EncoderConfigurationSnapshot.empty ()));
        host.controllerMotion (knob (1), InputKind.RELATIVE, 1);
        assertTrue (writes (host).isEmpty ());
        click (host, "ROW2_2");
        assertEquals (List.of (new SetControllerIntegerSettingEffect (RIBBON_CC, 1)), writes (host));
    }

    private static FakeCoreHost ribbon (final RibbonSettingsSnapshot ribbon, final EncoderConfigurationSnapshot encoders)
    {
        final var provider = new PullCoreProvider ();
        final var host = new FakeCoreHost (provider.create (), provider.descriptor ().requiredCapabilities ());
        host.initialBridge (bridge (ribbon, encoders));
        host.start (Optional.empty ());
        host.requestPage (SELECT, "PAN");
        host.requestPage (TEMPORARY, "RIBBON");
        return host;
    }

    private static ControllerBridgeSnapshot bridge (final RibbonSettingsSnapshot ribbon, final EncoderConfigurationSnapshot encoders)
    {
        return bridge (ribbon, encoders, ParameterBridgeSnapshot.empty ());
    }

    private static ControllerBridgeSnapshot bridgeWithTempo (final double value)
    {
        return bridge (state (0, 74, 0), ENCODERS, new ParameterBridgeSnapshot (Map.of (ParameterSlot.TEMPO, new ParameterTargetSnapshot (TEMPO, value, 0)), Map.of ()));
    }

    private static ControllerBridgeSnapshot bridge (final RibbonSettingsSnapshot ribbon, final EncoderConfigurationSnapshot encoders, final ParameterBridgeSnapshot parameters)
    {
        final var e = ControllerBridgeSnapshot.empty ();
        final var layout = new ControllerLayoutSnapshot (1, "PLAY", "TRACK", false, false, 0, GridPressureConfiguration.OFF);
        final var settings = new ControllerSettingsSnapshot (true, false, "VOLUME", 0, CursorSendBankSnapshot.empty (), false, 127, ControllerHardwareSettingsSnapshot.empty (), ribbon);
        return new ControllerBridgeSnapshot (e.transport (), e.selectedTrack (), e.sessionBank (), layout, e.noteView (), e.noteRepeat (), e.drum (), parameters, e.controllerMappingFeedback (), e.master (), e.project (), e.automation (), encoders, e.currentTrackBank (), e.transportSettings (), settings, e.applicationUi (), e.controllerPages (), e.browser (), e.controllerHardware ());
    }

    private static RibbonSettingsSnapshot state (final int function, final int cc, final int repeat) { return new RibbonSettingsSnapshot (true, function, cc, repeat); }
    private static void observe (final FakeCoreHost host, final RibbonSettingsSnapshot ribbon) { host.bridge (bridge (ribbon, ENCODERS)); }
    private static ControlId knob (final int index) { return PushControlIds.continuous ("KNOB" + index); }
    private static void edge (final FakeCoreHost host, final String name, final boolean pressed) { host.controllerButton (PushControlIds.button (name), pressed); }
    private static void click (final FakeCoreHost host, final String name) { edge (host, name, true); edge (host, name, false); }
    private static String page (final FakeCoreHost host) { return host.effects ().desiredControllerPage ().effectivePage ().legacyAlias (); }
    private static RgbColor light (final FakeCoreHost host, final String name) { return host.effects ().desiredOutput ().lights ().get (PushControlIds.button (name)); }
    private static List<String> text (final FakeCoreHost host) { return host.effects ().desiredOutput ().display ().commands ().stream ().filter (DisplayCommand.TextBox.class::isInstance).map (DisplayCommand.TextBox.class::cast).map (DisplayCommand.TextBox::text).toList (); }
    private static <T> List<T> effects (final FakeCoreHost host, final Class<T> type) { return host.effects ().executionOrder ().stream ().filter (type::isInstance).map (type::cast).toList (); }
    private static List<SetControllerIntegerSettingEffect> writes (final FakeCoreHost host) { return effects (host, SetControllerIntegerSettingEffect.class); }
    private static SetControllerIntegerSettingEffect lastWrite (final FakeCoreHost host) { final var writes = writes (host); return writes.get (writes.size () - 1); }
}
