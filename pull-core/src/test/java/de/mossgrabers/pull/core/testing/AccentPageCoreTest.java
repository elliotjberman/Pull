// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.testing;

import de.mossgrabers.pull.core.api.*;
import de.mossgrabers.pull.core.api.effect.SetControllerIntegerSettingEffect;
import de.mossgrabers.pull.core.api.event.InputKind;
import de.mossgrabers.pull.core.api.output.DisplayCommand;
import de.mossgrabers.pull.core.api.output.RgbColor;
import de.mossgrabers.pull.core.runtime.PullCoreProvider;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import static de.mossgrabers.pull.core.api.LegacyControllerPageRequest.Operation.SELECT;
import static de.mossgrabers.pull.core.api.LegacyControllerPageRequest.Operation.TEMPORARY;
import static org.junit.jupiter.api.Assertions.*;

/** Accent feedback uses the production normalized touch route. */
class AccentPageCoreTest
{
    @Test
    void eighthKnobTouchHighlightsTheObservedAccentValueUntilRelease ()
    {
        final var host = host ();
        final var restingDisplay = host.effects ().desiredOutput ().display ();
        final int effectsBefore = host.effects ().executionOrder ().size ();
        for (int index = 1; index < 8; index++)
        {
            host.controllerTouch (PushControlIds.continuous ("KNOB" + index), true);
            assertEquals (restingDisplay, host.effects ().desiredOutput ().display ());
        }
        host.controllerTouch (PushControlIds.continuous ("KNOB8"), true);
        assertNotEquals (restingDisplay, host.effects ().desiredOutput ().display ());
        assertTrue (host.effects ().desiredOutput ().display ().commands ().stream ().anyMatch (command -> command instanceof final DisplayCommand.TextBox text && "Accent".equals (text.text ()) && new RgbColor (190, 235, 247).equals (text.color ())));
        for (int index = 1; index <= 8; index++)
            for (int row = 1; row <= 2; row++) assertEquals (new RgbColor (0, 0, 0), host.effects ().desiredOutput ().lights ().get (PushControlIds.button ("ROW" + row + "_" + index)));
        assertTrue (host.effects ().desiredOutput ().display ().commands ().stream ().filter (DisplayCommand.TextBox.class::isInstance).map (DisplayCommand.TextBox.class::cast).anyMatch (text -> text.text ().equals ("93")));
        host.controllerTouch (PushControlIds.continuous ("KNOB8"), false);
        assertEquals (restingDisplay, host.effects ().desiredOutput ().display ());
        for (int index = 1; index < 8; index++) host.controllerTouch (PushControlIds.continuous ("KNOB" + index), false);
        assertEquals (effectsBefore, host.effects ().executionOrder ().size (), "touch feedback must not submit a hardware or parameter mutation");
    }

    @Test
    void heldTouchCannotKeepQueuedVelocityWritesAliveAfterThePageCloses ()
    {
        final var host = host ();
        final var knob = PushControlIds.continuous ("KNOB8");
        host.controllerTouch (knob, true);
        host.controllerMotion (knob, InputKind.RELATIVE, 1);
        host.controllerMotion (knob, InputKind.RELATIVE, 2);
        host.requestPage (SELECT, "INFO");
        assertEquals ("INFO", host.effects ().desiredControllerPage ().effectivePage ().legacyAlias ());
        host.bridge (bridge (94));
        host.controllerTouch (knob, false);
        assertEquals (List.of (new SetControllerIntegerSettingEffect (SetControllerIntegerSettingEffect.Setting.ACCENT_VELOCITY, 94)),
            host.effects ().executionOrder ().stream ().filter (SetControllerIntegerSettingEffect.class::isInstance).toList ());
    }

    private static FakeCoreHost host ()
    {
        final var provider = new PullCoreProvider ();
        final var host = new FakeCoreHost (provider.create (), provider.descriptor ().requiredCapabilities ());
        host.initialBridge (bridge (93));
        host.start (Optional.empty ());
        host.requestPage (TEMPORARY, "ACCENT");
        return host;
    }

    private static ControllerBridgeSnapshot bridge (final int velocity)
    {
        final var e = ControllerBridgeSnapshot.empty ();
        final var layout = new ControllerLayoutSnapshot (1, "PLAY", "TRACK", false, false, 0, GridPressureConfiguration.OFF);
        final var settings = new ControllerSettingsSnapshot (true, false, "VOLUME", 0, CursorSendBankSnapshot.empty (), true, velocity);
        final var encoders = new EncoderConfigurationSnapshot (true, 128, 10, 0, 0);
        return new ControllerBridgeSnapshot (e.transport (), e.selectedTrack (), e.sessionBank (), layout, e.noteView (), e.noteRepeat (), e.drum (), e.parameters (), e.controllerMappingFeedback (), e.master (), e.project (), e.automation (), encoders, e.currentTrackBank (), e.transportSettings (), settings, e.applicationUi (), e.controllerPages (), e.browser (), e.controllerHardware ());
    }
}
