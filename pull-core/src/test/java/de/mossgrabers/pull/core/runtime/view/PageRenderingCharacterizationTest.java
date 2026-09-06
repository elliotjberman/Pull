// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.api.*;
import de.mossgrabers.pull.core.api.event.*;
import de.mossgrabers.pull.core.runtime.PullCoreProvider;
import de.mossgrabers.pull.core.view.ViewOutput;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Exact command/row-light fingerprints captured from the pre-extraction 9f0d575 build. */
class PageRenderingCharacterizationTest
{
    @Test
    void framePreservesEveryObservedOptionCombinationAndUnavailableLayout () throws Exception
    {
        assertEquals ("7228d8cb5390eacbcae456ebae6d39747c1ae25512ecca5eeb8e97b4ed3a0573", fingerprint (frame ()));
    }

    @Test
    void settingsPreserveModesPreRollVolumeEndpointsAndUnavailableValues () throws Exception
    {
        assertEquals ("09b8c33cdff33ef921854e49f8bfe3a409de2ab705ddf7e908c7459f03ae230c", fingerprint (settings ()));
    }

    @Test
    void accentPreservesEveryVelocityAcrossEncoderRangesAndTouchBrightness () throws Exception
    {
        assertEquals ("15f6d2f54ee14a61829fbb025895ddbadf011c266e1a8758167eb9ce2532bb43", fingerprint (accent ()));
    }

    private static List<ViewOutput> frame ()
    {
        final List<ViewOutput> output = new ArrayList<> ();
        final FramePageView view = new FramePageView ();
        output.add (view.render (snapshot (ApplicationUiSnapshot.empty (), AutomationSnapshot.empty (), TransportSettingsSnapshot.empty (), ControllerSettingsSnapshot.empty (), EncoderConfigurationSnapshot.empty (), ParameterBridgeSnapshot.empty ())));
        for (final String layout: List.of ("ARRANGE", "MIX", "EDIT", "UNKNOWN"))
            for (int mask = 0; mask < 128; mask++)
            {
                final ApplicationUiSnapshot ui = new ApplicationUiSnapshot (1, "project", layout,
                    new ArrangerUiSnapshot (bit (mask, 0), bit (mask, 1), bit (mask, 2), bit (mask, 3), bit (mask, 4), bit (mask, 5), bit (mask, 6)),
                    new MixerUiSnapshot (bit (mask, 0), bit (mask, 1), bit (mask, 2), bit (mask, 3), bit (mask, 4), bit (mask, 5)));
                output.add (view.render (snapshot (ui, AutomationSnapshot.empty (), TransportSettingsSnapshot.empty (), ControllerSettingsSnapshot.empty (), EncoderConfigurationSnapshot.empty (), ParameterBridgeSnapshot.empty ())));
            }
        return output;
    }

    private static List<ViewOutput> settings ()
    {
        final List<ViewOutput> output = new ArrayList<> ();
        final TransportSettingsPageView automation = new TransportSettingsPageView (true, new AutomationControlState ());
        final TransportSettingsPageView metronome = new TransportSettingsPageView (false, new AutomationControlState ());
        final ControllerSnapshot empty = snapshot (ApplicationUiSnapshot.empty (), AutomationSnapshot.empty (), TransportSettingsSnapshot.empty (), ControllerSettingsSnapshot.empty (), EncoderConfigurationSnapshot.empty (), ParameterBridgeSnapshot.empty ());
        output.add (automation.render (empty));
        output.add (metronome.render (empty));
        for (final AutomationWriteMode mode: AutomationWriteMode.values ())
            for (final boolean writing: List.of (false, true))
                output.add (automation.render (snapshot (ApplicationUiSnapshot.empty (), new AutomationSnapshot ("project", writing, false, mode), TransportSettingsSnapshot.empty (), ControllerSettingsSnapshot.empty (), EncoderConfigurationSnapshot.empty (), ParameterBridgeSnapshot.empty ())));
        for (final PreRoll preRoll: PreRoll.values ())
            for (final boolean during: List.of (false, true))
                for (final int value: List.of (0, 64, 127, 1023))
                    for (final boolean modulated: List.of (false, true))
                    {
                        final ParameterTargetSnapshot volume = new ParameterTargetSnapshot (new ParameterTargetRef (ParameterTargetKind.FIXED, "metronome", 0), "Volume", value, modulated ? 512 : -1, value + " dB", -1, 0.5);
                        final ParameterBridgeSnapshot parameters = new ParameterBridgeSnapshot (Map.of (ParameterSlot.METRONOME_VOLUME, volume), Map.of ());
                        output.add (metronome.render (snapshot (ApplicationUiSnapshot.empty (), AutomationSnapshot.empty (), new TransportSettingsSnapshot ("project", false, preRoll, during), ControllerSettingsSnapshot.empty (), new EncoderConfigurationSnapshot (true, 1024, 1, 1, 1), parameters)));
                    }
        return output;
    }

    private static List<ViewOutput> accent ()
    {
        final List<ViewOutput> output = new ArrayList<> ();
        for (final boolean available: List.of (false, true))
            for (final int upperBound: List.of (128, 1024))
                for (int velocity = 1; velocity <= 127; velocity++)
                    for (final boolean touched: List.of (false, true))
                    {
                        final AccentPageView view = new AccentPageView (new SessionStopGesture ());
                        final ControllerSettingsSnapshot settings = available ? new ControllerSettingsSnapshot (true, false, "VOLUME", 0, CursorSendBankSnapshot.empty (), false, velocity) : ControllerSettingsSnapshot.empty ();
                        final ControllerSnapshot snapshot = snapshot (ApplicationUiSnapshot.empty (), AutomationSnapshot.empty (), TransportSettingsSnapshot.empty (), settings, new EncoderConfigurationSnapshot (true, upperBound, 1, 1, 1), ParameterBridgeSnapshot.empty ());
                        if (touched) view.handle (new ControllerInputEvent (1, 1, PushControlIds.continuous ("KNOB8"), InputKind.TOUCH, InputPhase.BEGIN, 127), snapshot);
                        output.add (view.render (snapshot));
                    }
        return output;
    }

    private static boolean bit (final int mask, final int index) { return (mask & 1 << index) != 0; }

    private static ControllerSnapshot snapshot (final ApplicationUiSnapshot ui, final AutomationSnapshot automation, final TransportSettingsSnapshot transport, final ControllerSettingsSnapshot settings, final EncoderConfigurationSnapshot encoder, final ParameterBridgeSnapshot parameters)
    {
        final var empty = ControllerBridgeSnapshot.empty ();
        final var bridge = new ControllerBridgeSnapshot (empty.transport (), empty.selectedTrack (), empty.sessionBank (), empty.layout (), empty.noteView (), empty.noteRepeat (), empty.drum (), parameters, empty.controllerMappingFeedback (), empty.master (), empty.project (), automation, encoder, empty.currentTrackBank (), transport, settings, ui);
        return new ControllerSnapshot (1, 1, new PullCoreProvider ().descriptor ().requiredCapabilities (), bridge, ClipCatalogSnapshot.empty (), Map.of (), Map.of (), Optional.empty (), Set.of (), Set.of ());
    }

    private static String fingerprint (final List<ViewOutput> output) throws Exception
    {
        final MessageDigest digest = MessageDigest.getInstance ("SHA-256");
        for (final ViewOutput frame: output)
        {
            digest.update (frame.display ().toString ().getBytes (StandardCharsets.UTF_8));
            frame.lights ().entrySet ().stream ().sorted (Map.Entry.comparingByKey (java.util.Comparator.comparing (ControlId::value))).forEach (entry -> digest.update (entry.toString ().getBytes (StandardCharsets.UTF_8)));
        }
        return HexFormat.of ().formatHex (digest.digest ());
    }
}
