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
import org.junit.jupiter.params.provider.ValueSource;
import java.util.*;
import java.util.stream.IntStream;
import static de.mossgrabers.pull.core.api.LegacyControllerPageRequest.Operation.*;
import static org.junit.jupiter.api.Assertions.*;

/** Exercises the configuration pages through the production core router and later read-back. */
class InfoPageCoreTest
{
    @Test
    void displayAndLightsUseObservedHardwareAndReleaseTheSubscriptionOnExit ()
    {
        final var host = info (false);
        assertTrue (text (host).contains ("Waiting for Push hardware information"));
        assertTrue (host.effects ().desiredBridgeSubscriptions ().domains ().contains (BridgeSubscription.CONTROLLER_HARDWARE));
        assertEquals (new RgbColor (255, 255, 255), light (host, "ROW2_1"));
        assertEquals (new RgbColor (30, 30, 30), light (host, "ROW2_2"));
        for (int i = 1; i <= 8; i++) assertEquals (new RgbColor (0, 0, 0), light (host, "ROW1_" + i));
        for (int i = 3; i <= 8; i++) assertEquals (new RgbColor (0, 0, 0), light (host, "ROW2_" + i));
        host.bridge (bridge (false, 1, new ControllerHardwareSnapshot (1, 1, 2, 53, 7, -1)));
        assertTrue (text (host).containsAll (List.of ("1.2 Build 53", "7", "-1")), "signed serial is observed data, not the availability sentinel");
        host.bridge (bridge (false, 1, new ControllerHardwareSnapshot (2, 1, 3, 54, 8, 12345)));
        assertTrue (text (host).containsAll (List.of ("1.3 Build 54", "8", "12345")));
        assertFalse (text (host).contains ("1.2 Build 53"));
        host.requestPage (SELECT, "TRACK");
        host.controllerTick ();
        assertFalse (host.effects ().desiredBridgeSubscriptions ().domains ().contains (BridgeSubscription.CONTROLLER_HARDWARE));
    }

    @ParameterizedTest
    @ValueSource (strings = { "INFO", "SETUP" })
    void everyLowerRowSelectsOnlyItsCapturedTrackOnReleaseDespiteModifiers (final String page)
    {
        for (int index = 0; index < 8; index++)
        {
            final var host = configuration (false, page);
            for (final String modifier: List.of ("SHIFT", "DELETE", "SELECT", "RECORD")) edge (host, modifier, true);
            edge (host, "ROW1_" + (index + 1), true);
            assertTrue (effects (host, CurrentTrackActionEffect.class).isEmpty ());
            edge (host, "ROW1_" + (index + 1), false);
            assertEquals (List.of (new CurrentTrackActionEffect (new CurrentTrackTarget (1, "main", index, "track-" + index), CurrentTrackActionEffect.Action.SELECT)), effects (host, CurrentTrackActionEffect.class));
            edge (host, "ROW1_" + (index + 1), false);
            assertEquals (1, effects (host, CurrentTrackActionEffect.class).size ());
        }
    }

    @ParameterizedTest
    @ValueSource (strings = { "INFO", "SETUP" })
    void leavingAndReturningToThePageOrBankCannotReviveAHeldRowOrTab (final String page)
    {
        final var host = configuration (false, page);
        edge (host, "ROW1_3", true);
        host.bridge (bridge (false, 2, ControllerHardwareSnapshot.empty ()));
        host.bridge (bridge (false, 1, ControllerHardwareSnapshot.empty ()));
        edge (host, "ROW1_3", false);
        assertTrue (effects (host, CurrentTrackActionEffect.class).isEmpty ());
        edge (host, "ROW1_3", true);
        final String otherTab = page.equals ("INFO") ? "ROW2_2" : "ROW2_1";
        edge (host, otherTab, true);
        host.requestPage (SELECT, "PAN");
        host.requestPage (TEMPORARY, page);
        edge (host, "ROW1_3", false);
        edge (host, otherTab, false);
        assertEquals (page, page (host));
        assertTrue (effects (host, CurrentTrackActionEffect.class).isEmpty ());
        edge (host, "ROW1_3", true);
        edge (host, "ROW1_3", false);
        assertEquals (1, effects (host, CurrentTrackActionEffect.class).size (), "a fresh gesture can acquire the current target");
        edge (host, otherTab, true);
        assertEquals (page, page (host));
        edge (host, otherTab, false);
        assertEquals (page.equals ("INFO") ? "SETUP" : "INFO", page (host), "a fresh tab gesture can navigate after cancellation");
    }

    @ParameterizedTest
    @ValueSource (strings = { "INFO", "SETUP" })
    void stopChordStopsVisibleSessionTrackOnBeginAndConsumesPlainStop (final String page)
    {
        final var host = configuration (true, page);
        edge (host, "STOP_CLIP", true);
        edge (host, "ROW1_3", true);
        assertEquals (List.of (new StopSessionTrackEffect (3, new SessionBankShape (8, 8), 2, "track-2", true)), effects (host, StopSessionTrackEffect.class));
        assertTrue (effects (host, ConsumeControllerButtonEffect.class).contains (new ConsumeControllerButtonEffect (PushControlIds.button ("ROW1_3"))));
        edge (host, "ROW1_3", false);
        edge (host, "STOP_CLIP", false);
        assertTrue (effects (host, CurrentTrackActionEffect.class).isEmpty ());
        assertTrue (effects (host, SelectedTrackActionEffect.class).isEmpty ());
        assertTrue (effects (host, StopSessionBankEffect.class).isEmpty ());
    }

    @ParameterizedTest
    @ValueSource (strings = { "INFO", "SETUP" })
    void bothTabAndUnusedUpperRowConsumeSessionStopWhileTabsStillNavigate (final String page)
    {
        for (final String button: List.of ("ROW2_1", "ROW2_2", "ROW2_8"))
        {
            final var host = configuration (true, page);
            edge (host, "STOP_CLIP", true);
            edge (host, button, true);
            edge (host, button, false);
            edge (host, "STOP_CLIP", false);
            assertEquals (button.equals ("ROW2_1") ? "INFO" : button.equals ("ROW2_2") ? "SETUP" : page, page (host));
            assertTrue (effects (host, SelectedTrackActionEffect.class).isEmpty ());
            assertTrue (effects (host, StopSessionTrackEffect.class).isEmpty ());
        }
    }

    @Test
    void knobsAreInertAndNoteStopDoesNotReplacePlainRowSelection ()
    {
        final var host = info (false);
        edge (host, "DELETE", true);
        final int before = host.effects ().executionOrder ().size ();
        for (int index = 1; index <= 8; index++)
        {
            final ControlId knob = PushControlIds.continuous ("KNOB" + index);
            host.controllerTouch (knob, true);
            host.controllerMotion (knob, InputKind.RELATIVE, 10);
            host.controllerTouch (knob, false);
        }
        assertEquals (before, host.effects ().executionOrder ().size ());
        edge (host, "STOP_CLIP", true);
        edge (host, "ROW1_1", true);
        edge (host, "ROW1_1", false);
        assertEquals (1, effects (host, CurrentTrackActionEffect.class).size ());
        assertTrue (effects (host, StopSessionTrackEffect.class).isEmpty ());
    }

    private static FakeCoreHost info (final boolean session) { return configuration (session, "INFO"); }
    private static FakeCoreHost configuration (final boolean session, final String page) { final var host = host (session); host.requestPage (TEMPORARY, page); return host; }
    private static FakeCoreHost host (final boolean session)
    {
        final var provider = new PullCoreProvider ();
        final var host = new FakeCoreHost (provider.create (), provider.descriptor ().requiredCapabilities ());
        host.initialBridge (bridge (session, 1, ControllerHardwareSnapshot.empty ()));
        host.start (Optional.empty ());
        return host;
    }
    private static void edge (final FakeCoreHost host, final String name, final boolean pressed) { host.controllerButton (PushControlIds.button (name), pressed); }
    private static String page (final FakeCoreHost host) { return host.effects ().desiredControllerPage ().effectivePage ().legacyAlias (); }
    private static RgbColor light (final FakeCoreHost host, final String name) { return host.effects ().desiredOutput ().lights ().get (PushControlIds.button (name)); }
    private static List<String> text (final FakeCoreHost host) { return host.effects ().desiredOutput ().display ().commands ().stream ().filter (DisplayCommand.TextBox.class::isInstance).map (DisplayCommand.TextBox.class::cast).map (DisplayCommand.TextBox::text).toList (); }
    private static <T> List<T> effects (final FakeCoreHost host, final Class<T> type) { return host.effects ().executionOrder ().stream ().filter (type::isInstance).map (type::cast).toList (); }
    private static ControllerBridgeSnapshot bridge (final boolean session, final long generation, final ControllerHardwareSnapshot hardware)
    {
        final var e = ControllerBridgeSnapshot.empty ();
        final var tracks = IntStream.range (0, 8).mapToObj (i -> new SessionTrackSnapshot ("track-" + i, i, "Track " + i, true, i == 0, true, false, false, false, false, SessionTrackType.AUDIO, new RgbColor (100, 150, 200))).toList ();
        final var current = new CurrentTrackBankSnapshot (generation, "main", 0, tracks.stream ().map (track -> new CurrentTrackSnapshot (track, false, 0, 0)).toList (), "track-0", false, 1, false);
        final var bank = session ? new SessionBankSnapshot (3, new SessionBankShape (8, 8), 0, 0, tracks) : SessionBankSnapshot.empty ();
        final var layout = new ControllerLayoutSnapshot (1, session ? "SESSION" : "PLAY", "TRACK", false, false, 0, GridPressureConfiguration.OFF);
        final var selected = new SelectedTrackSnapshot (1, "track-0", "Track 0", 0, "Audio", true, false, false, false, true, true, false, TrackMonitorMode.OFF, false, false, false, true, 0.5, 0.5, new RgbColor (100, 150, 200));
        return new ControllerBridgeSnapshot (e.transport (), selected, bank, layout, e.noteView (), e.noteRepeat (), e.drum (), e.parameters (), e.controllerMappingFeedback (), e.master (), e.project (), e.automation (), e.encoderConfiguration (), current, e.transportSettings (), e.controllerSettings (), e.applicationUi (), e.controllerPages (), e.browser (), hardware);
    }
}
