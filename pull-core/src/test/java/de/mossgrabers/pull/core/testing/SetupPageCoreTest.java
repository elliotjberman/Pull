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
import org.junit.jupiter.params.provider.ValueSource;
import java.util.*;
import java.util.stream.IntStream;
import static de.mossgrabers.pull.core.api.LegacyControllerPageRequest.Operation.*;
import static org.junit.jupiter.api.Assertions.*;

/** Routed Setup requests remain separate from later observed hardware preferences. */
class SetupPageCoreTest
{
    private static final EncoderConfigurationSnapshot ENCODERS = new EncoderConfigurationSnapshot (true, 128, 10, 0, 0);
    private static final ControllerHardwareSettingsSnapshot HARDWARE = hardware (40, 60, 2, 3, 7);

    @Test
    void physicalSetupButtonOpensOnPressAndRestoresTheUnderlyingPageAfterInfo ()
    {
        final var host = setup (HARDWARE, ENCODERS);
        host.requestPage (SELECT, "PAN");
        assertEquals (new RgbColor (60, 60, 60), light (host, "SETUP"));
        edge (host, "SETUP", true);
        assertEquals ("SETUP", page (host));
        assertEquals (new RgbColor (255, 255, 255), light (host, "SETUP"));
        host.controllerButtonLong (PushControlIds.button ("SETUP"));
        edge (host, "SETUP", false);
        assertEquals ("SETUP", page (host), "release and long press do not toggle the page again");
        edge (host, "ROW2_1", true);
        edge (host, "ROW2_1", false);
        assertEquals ("INFO", page (host));
        assertEquals (new RgbColor (255, 255, 255), light (host, "SETUP"));
        edge (host, "SETUP", true);
        edge (host, "SETUP", false);
        assertEquals ("SETUP", page (host));
        edge (host, "SETUP", true);
        assertEquals ("PAN", page (host));
        edge (host, "SETUP", false);
        assertEquals (new RgbColor (60, 60, 60), light (host, "SETUP"));
    }

    @ParameterizedTest
    @CsvSource ({ "2, DISPLAY_BRIGHTNESS, 40, 100", "3, LED_BRIGHTNESS, 60, 100", "5, PAD_SENSITIVITY, 2, 10", "6, PAD_GAIN, 3, 10", "7, PAD_DYNAMICS, 7, 10" })
    void assignedKnobsUseTheirObservedValueAndClampAtBothEnds (final int knob, final SetControllerIntegerSettingEffect.Setting setting, final int initial, final int maximum)
    {
        final var host = setup (HARDWARE, ENCODERS);
        final var initialDisplay = host.effects ().desiredOutput ().display ();
        host.controllerTouch (knob (knob), true);
        assertNotEquals (initialDisplay, host.effects ().desiredOutput ().display (), "touch feedback belongs to the core page");
        assertTrue (columnText (host, knob).contains (Integer.toString (initial)));
        host.controllerTouch (knob (knob), false);
        assertEquals (initialDisplay, host.effects ().desiredOutput ().display ());
        assertTrue (writes (host).isEmpty (), "an ordinary touch changes feedback without writing a preference");
        turn (host, knob, 1);
        assertEquals (List.of (new SetControllerIntegerSettingEffect (setting, initial + 1)), writes (host));
        assertEquals (initialDisplay, host.effects ().desiredOutput ().display (), "submitting a write cannot change the displayed hardware value");
        observe (host, withValue (knob, initial + 1));
        assertTrue (columnText (host, knob).contains (Integer.toString (initial + 1)));

        turn (host, knob, 1000);
        assertEquals (new SetControllerIntegerSettingEffect (setting, maximum), lastWrite (host));
        observe (host, withValue (knob, maximum));
        final int atMaximum = writes (host).size ();
        turn (host, knob, 1);
        assertEquals (atMaximum, writes (host).size (), "turning beyond the upper bound does not submit another write");
        assertTrue (columnText (host, knob).contains (Integer.toString (maximum)));

        turn (host, knob, -1000);
        assertEquals (new SetControllerIntegerSettingEffect (setting, 0), lastWrite (host));
        observe (host, withValue (knob, 0));
        final int atMinimum = writes (host).size ();
        turn (host, knob, -1);
        assertEquals (atMinimum, writes (host).size ());
        assertTrue (columnText (host, knob).contains ("0"));
    }

    @ParameterizedTest
    @CsvSource ({ "1, '', 0", "2, DISPLAY_BRIGHTNESS, 100", "3, LED_BRIGHTNESS, 100", "4, '', 0", "5, PAD_SENSITIVITY, 5", "6, PAD_GAIN, 5", "7, PAD_DYNAMICS, 5", "8, '', 0" })
    void deleteTouchConsumesDeleteAndResetsOnlyAssignedKnobs (final int knob, final String setting, final int reset)
    {
        final var host = setup (HARDWARE, ENCODERS);
        edge (host, "DELETE", true);
        host.controllerTouch (knob (knob), true);
        assertEquals (List.of (new ConsumeControllerButtonEffect (PushControlIds.button ("DELETE"))), effects (host, ConsumeControllerButtonEffect.class));
        assertEquals (setting.isEmpty () ? List.of () : List.of (new SetControllerIntegerSettingEffect (SetControllerIntegerSettingEffect.Setting.valueOf (setting), reset)), writes (host));
        final int beforeRelease = host.effects ().executionOrder ().size ();
        host.controllerTouch (knob (knob), false);
        assertEquals (beforeRelease, host.effects ().executionOrder ().size (), "touch release must not submit another reset");
        assertEquals (List.of (), effects (host, AcquireParameterTouchEffect.class), "hardware preferences do not acquire Bitwig parameter touches");
    }

    @ParameterizedTest
    @ValueSource (ints = { 1, 4, 8 })
    void unusedKnobsRemainInertWithoutDelete (final int knob)
    {
        final var host = setup (HARDWARE, ENCODERS);
        final var display = host.effects ().desiredOutput ().display ();
        final int before = host.effects ().executionOrder ().size ();
        host.controllerTouch (knob (knob), true);
        turn (host, knob, 100);
        host.controllerTouch (knob (knob), false);
        assertEquals (before, host.effects ().executionOrder ().size ());
        assertEquals (display, host.effects ().desiredOutput ().display ());
    }

    @Test
    void laterReadBackReleasesOnlyTheLatestQueuedWriteForThatSetting ()
    {
        final var host = setup (HARDWARE, ENCODERS);
        turn (host, 2, 5);
        turn (host, 2, 7);
        turn (host, 2, -2);
        host.controllerTick ();
        assertEquals (List.of (new SetControllerIntegerSettingEffect (SetControllerIntegerSettingEffect.Setting.DISPLAY_BRIGHTNESS, 45)), writes (host));
        assertTrue (columnText (host, 2).contains ("40"));
        assertTrue (host.effects ().executionRequirements ().ticksRequested ());

        observe (host, withValue (2, 45));
        assertEquals (new SetControllerIntegerSettingEffect (SetControllerIntegerSettingEffect.Setting.DISPLAY_BRIGHTNESS, 50), lastWrite (host));
        assertEquals (2, writes (host).size ());
        assertTrue (columnText (host, 2).contains ("45"), "the queued request is not read-back");
        host.controllerTick ();
        assertEquals (2, writes (host).size ());
        observe (host, withValue (2, 50));
        assertTrue (columnText (host, 2).contains ("50"));
        assertFalse (host.effects ().executionRequirements ().ticksRequested ());
    }

    @Test
    void differentHardwareSettingsCanAwaitReadBackIndependently ()
    {
        final var host = setup (HARDWARE, ENCODERS);
        turn (host, 2, 1);
        turn (host, 3, 2);
        assertEquals (List.of (
            new SetControllerIntegerSettingEffect (SetControllerIntegerSettingEffect.Setting.DISPLAY_BRIGHTNESS, 41),
            new SetControllerIntegerSettingEffect (SetControllerIntegerSettingEffect.Setting.LED_BRIGHTNESS, 62)), writes (host));
        assertTrue (columnText (host, 2).contains ("40"));
        assertTrue (columnText (host, 3).contains ("60"));
        observe (host, hardware (41, 60, 2, 3, 7));
        assertTrue (host.effects ().executionRequirements ().ticksRequested ());
        observe (host, hardware (41, 62, 2, 3, 7));
        assertFalse (host.effects ().executionRequirements ().ticksRequested ());
    }

    @Test
    void pageExitCancelsPendingIntentAndReentryStartsFromNewReadBack ()
    {
        final var host = setup (HARDWARE, ENCODERS);
        turn (host, 2, 5);
        turn (host, 2, 7);
        edge (host, "ROW2_1", true);
        host.requestPage (SELECT, "INFO");
        assertEquals ("INFO", page (host));
        observe (host, withValue (2, 45));
        edge (host, "ROW2_1", false);
        host.controllerTick ();
        assertEquals (1, writes (host).size (), "a departed page must not submit its old queued intent");
        host.requestPage (TEMPORARY, "SETUP");
        turn (host, 2, 1);
        assertEquals (new SetControllerIntegerSettingEffect (SetControllerIntegerSettingEffect.Setting.DISPLAY_BRIGHTNESS, 46), lastWrite (host));
    }

    @Test
    void unavailableReadBackPreventsWritesButKeepsTabsAndDeleteConsumptionAvailable ()
    {
        final var host = setup (ControllerHardwareSettingsSnapshot.empty (), ENCODERS);
        assertTrue (text (host).contains ("Waiting for Push settings"));
        edge (host, "DELETE", true);
        for (int index = 1; index <= 8; index++)
        {
            turn (host, index, 1);
            host.controllerTouch (knob (index), true);
            host.controllerTouch (knob (index), false);
        }
        assertTrue (writes (host).isEmpty ());
        assertEquals (8, effects (host, ConsumeControllerButtonEffect.class).size ());
        edge (host, "ROW2_1", true);
        edge (host, "ROW2_1", false);
        assertEquals ("INFO", page (host));
    }

    @Test
    void missingEncoderCalibrationBlocksTurnsButNotAbsoluteReset ()
    {
        final var host = setup (HARDWARE, EncoderConfigurationSnapshot.empty ());
        turn (host, 2, 100);
        assertTrue (writes (host).isEmpty ());
        edge (host, "DELETE", true);
        host.controllerTouch (knob (2), true);
        assertEquals (List.of (new SetControllerIntegerSettingEffect (SetControllerIntegerSettingEffect.Setting.DISPLAY_BRIGHTNESS, 100)), writes (host));
    }

    @Test
    void infoKnobsStayInertEvenWhenHardwareSettingsAreAvailable ()
    {
        final var host = setup (HARDWARE, ENCODERS);
        host.requestPage (TEMPORARY, "INFO");
        edge (host, "DELETE", true);
        final int before = host.effects ().executionOrder ().size ();
        for (int index = 1; index <= 8; index++)
        {
            host.controllerTouch (knob (index), true);
            turn (host, index, 100);
            host.controllerTouch (knob (index), false);
        }
        assertEquals (before, host.effects ().executionOrder ().size ());
    }

    @Test
    void setupRendersObservedCurveAndTabsAndChangesItsSubscriptionsWithInfo ()
    {
        final var host = setup (HARDWARE, ENCODERS);
        assertTrue (host.effects ().desiredBridgeSubscriptions ().domains ().containsAll (Set.of (BridgeSubscription.CONTROLLER_SETTINGS, BridgeSubscription.ENCODER_CONFIGURATION)));
        assertFalse (host.effects ().desiredBridgeSubscriptions ().domains ().contains (BridgeSubscription.CONTROLLER_HARDWARE));
        assertEquals (new RgbColor (30, 30, 30), light (host, "ROW2_1"));
        assertEquals (new RgbColor (255, 255, 255), light (host, "ROW2_2"));
        for (int index = 1; index <= 8; index++) assertEquals (new RgbColor (0, 0, 0), light (host, "ROW1_" + index));
        for (int index = 3; index <= 8; index++) assertEquals (new RgbColor (0, 0, 0), light (host, "ROW2_" + index));
        final var display = host.effects ().desiredOutput ().display ();
        observe (host, new ControllerHardwareSettingsSnapshot (true, 40, 60, 2, 3, 7, Collections.nCopies (128, 64)));
        assertNotEquals (display, host.effects ().desiredOutput ().display (), "the velocity curve is host read-back too");
        edge (host, "ROW2_1", true);
        assertEquals ("SETUP", page (host));
        edge (host, "ROW2_1", false);
        assertEquals ("INFO", page (host));
        assertTrue (host.effects ().desiredBridgeSubscriptions ().domains ().contains (BridgeSubscription.CONTROLLER_HARDWARE));
        assertEquals (new RgbColor (255, 255, 255), light (host, "ROW2_1"));
        edge (host, "ROW2_2", true);
        edge (host, "ROW2_2", false);
        assertEquals ("SETUP", page (host));
    }

    private static FakeCoreHost setup (final ControllerHardwareSettingsSnapshot hardware, final EncoderConfigurationSnapshot encoders)
    {
        final var provider = new PullCoreProvider ();
        final var host = new FakeCoreHost (provider.create (), provider.descriptor ().requiredCapabilities ());
        host.initialBridge (bridge (hardware, encoders));
        host.start (Optional.empty ());
        host.requestPage (TEMPORARY, "SETUP");
        return host;
    }

    private static ControllerBridgeSnapshot bridge (final ControllerHardwareSettingsSnapshot hardware, final EncoderConfigurationSnapshot encoders)
    {
        final var e = ControllerBridgeSnapshot.empty ();
        final var layout = new ControllerLayoutSnapshot (1, "PLAY", "TRACK", false, false, 0, GridPressureConfiguration.OFF);
        final var settings = new ControllerSettingsSnapshot (true, false, "VOLUME", 0, CursorSendBankSnapshot.empty (), false, 127, hardware, RibbonSettingsSnapshot.empty ());
        return new ControllerBridgeSnapshot (e.transport (), e.selectedTrack (), e.sessionBank (), layout, e.noteView (), e.noteRepeat (), e.drum (), e.parameters (), e.controllerMappingFeedback (), e.master (), e.project (), e.automation (), encoders, e.currentTrackBank (), e.transportSettings (), settings, e.applicationUi (), e.controllerPages (), e.browser (), e.controllerHardware ());
    }

    private static ControllerHardwareSettingsSnapshot hardware (final int display, final int leds, final int sensitivity, final int gain, final int dynamics)
    {
        return new ControllerHardwareSettingsSnapshot (true, display, leds, sensitivity, gain, dynamics, IntStream.rangeClosed (1, 128).map (value -> Math.min (127, value)).boxed ().toList ());
    }

    private static ControllerHardwareSettingsSnapshot withValue (final int knob, final int value)
    {
        return hardware (knob == 2 ? value : 40, knob == 3 ? value : 60, knob == 5 ? value : 2, knob == 6 ? value : 3, knob == 7 ? value : 7);
    }

    private static void observe (final FakeCoreHost host, final ControllerHardwareSettingsSnapshot hardware) { host.bridge (bridge (hardware, ENCODERS)); }
    private static ControlId knob (final int index) { return PushControlIds.continuous ("KNOB" + index); }
    private static void turn (final FakeCoreHost host, final int knob, final long delta) { host.controllerMotion (knob (knob), InputKind.RELATIVE, delta); }
    private static void edge (final FakeCoreHost host, final String name, final boolean pressed) { host.controllerButton (PushControlIds.button (name), pressed); }
    private static String page (final FakeCoreHost host) { return host.effects ().desiredControllerPage ().effectivePage ().legacyAlias (); }
    private static RgbColor light (final FakeCoreHost host, final String name) { return host.effects ().desiredOutput ().lights ().get (PushControlIds.button (name)); }
    private static List<String> text (final FakeCoreHost host) { return host.effects ().desiredOutput ().display ().commands ().stream ().filter (DisplayCommand.TextBox.class::isInstance).map (DisplayCommand.TextBox.class::cast).map (DisplayCommand.TextBox::text).toList (); }
    private static List<String> columnText (final FakeCoreHost host, final int knob) { return host.effects ().desiredOutput ().display ().commands ().stream ().filter (DisplayCommand.TextBox.class::isInstance).map (DisplayCommand.TextBox.class::cast).filter (text -> text.x () >= (knob - 1) * 120 && text.x () < knob * 120).map (DisplayCommand.TextBox::text).toList (); }
    private static <T> List<T> effects (final FakeCoreHost host, final Class<T> type) { return host.effects ().executionOrder ().stream ().filter (type::isInstance).map (type::cast).toList (); }
    private static List<SetControllerIntegerSettingEffect> writes (final FakeCoreHost host) { return effects (host, SetControllerIntegerSettingEffect.class); }
    private static SetControllerIntegerSettingEffect lastWrite (final FakeCoreHost host) { final var writes = writes (host); return writes.get (writes.size () - 1); }
}
