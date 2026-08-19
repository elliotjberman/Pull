// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.controller.ableton.push.controller;

import de.mossgrabers.framework.controller.ButtonID;
import de.mossgrabers.framework.controller.color.ColorEx;
import de.mossgrabers.pull.core.api.PushControlIds;
import de.mossgrabers.pull.core.api.event.InputKind;
import de.mossgrabers.pull.core.api.event.InputPhase;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;


/** Tests the bounded JSON mirror consumed by the local Push debugger surface. */
class PushDebugSurfaceHostTest
{
    @Test
    void publishesCompleteTransmittedLightAndPressedState (@TempDir final Path directory) throws IOException
    {
        final Path statePath = directory.resolve (PushDebugSurfaceHost.STATE_FILE);
        final PushDebugSurfaceHost host = new PushDebugSurfaceHost (directory, null);
        host.pollForTest ();
        assertTrue (Files.readString (statePath).contains ("\"connected\":true"));

        host.observeButton (ButtonID.PLAY, 21, ColorEx.fromRGB (0, 255, 96));
        host.observePad (29, 43, ColorEx.fromRGB (12, 34, 56), 5, ColorEx.fromRGB (255, 0, 0), true);
        host.observePressedControls (List.of ("push.button.play", "push.pad.29"));
        host.observeDebugInput (PushControlIds.button ("PLAY"), InputKind.BUTTON, InputPhase.BEGIN, 127);
        host.observeDebugInput (PushControlIds.button ("PLAY"), InputKind.BUTTON, InputPhase.END, 0);
        host.pollForTest ();

        final String live = Files.readString (statePath);
        assertTrue (live.contains ("\"push.button.play\":{\"rgb\":\"00FF60\",\"palette\":21"));
        assertTrue (live.contains ("\"push.pad.29\":{\"rgb\":\"0C2238\",\"palette\":43,\"blinkRgb\":\"FF0000\",\"blinkPalette\":5,\"fast\":true}"));
        assertTrue (live.contains ("\"pressed\":[\"push.button.play\",\"push.pad.29\"]"));
        assertTrue (live.contains ("\"events\":[{\"sequence\":1,\"control\":\"push.button.play\",\"kind\":\"BUTTON\",\"phase\":\"BEGIN\",\"value\":127},{\"sequence\":2,\"control\":\"push.button.play\",\"kind\":\"BUTTON\",\"phase\":\"END\",\"value\":0}]"));

        host.close ();
        assertTrue (Files.readString (statePath).contains ("\"connected\":false"));
    }
}
