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
        host.observePadTransmission (29, 43, ColorEx.fromRGB (12, 34, 56), 5, ColorEx.fromRGB (255, 0, 0), true);
        host.observePressedControls (List.of ("push.button.play", "push.pad.29"));
        host.observeDebugInput (PushControlIds.button ("PLAY"), InputKind.BUTTON, InputPhase.BEGIN, 127);
        host.observeDebugInput (PushControlIds.button ("PLAY"), InputKind.BUTTON, InputPhase.END, 0);
        host.pollForTest ();

        final String live = Files.readString (statePath);
        assertTrue (live.contains ("\"push.button.play\":{\"rgb\":\"00FF60\",\"palette\":21"));
        assertTrue (live.contains ("\"push.pad.29\":{\"rgb\":\"0C2238\",\"palette\":43,\"blinkRgb\":\"FF0000\",\"blinkPalette\":5,\"fast\":true,\"pulse\":null}"));
        assertTrue (live.contains ("\"pressed\":[\"push.button.play\",\"push.pad.29\"]"));
        assertTrue (live.contains ("\"events\":[{\"sequence\":1,\"control\":\"push.button.play\",\"kind\":\"BUTTON\",\"phase\":\"BEGIN\",\"value\":127},{\"sequence\":2,\"control\":\"push.button.play\",\"kind\":\"BUTTON\",\"phase\":\"END\",\"value\":0}]"));

        host.close ();
        assertTrue (Files.readString (statePath).contains ("\"connected\":false"));
    }


    @Test
    void publishesExactMusicalPulseCadenceForTheBrowser (@TempDir final Path directory) throws IOException
    {
        final Path statePath = directory.resolve (PushDebugSurfaceHost.STATE_FILE);
        final PushDebugSurfaceHost host = new PushDebugSurfaceHost (directory, null);
        host.observePadTransmission (57, 43, ColorEx.fromRGB (67, 210, 185), 0, ColorEx.BLACK, false);
        host.observePadSemantic (
            57,
            new PushDebugSurfaceHost.DebugMusicalPulse (43, ColorEx.fromRGB (67, 210, 185), 44, ColorEx.fromRGB (0, 89, 0), 0.25, 0.125, 12.5));
        host.observePadTransmission (57, 43, ColorEx.fromRGB (67, 210, 185), 0, ColorEx.BLACK, false);
        host.pollForTest ();

        final String live = Files.readString (statePath);
        assertTrue (live.contains ("\"push.pad.57\":{\"rgb\":\"43D2B9\",\"palette\":43,\"blinkRgb\":null,\"blinkPalette\":0,\"fast\":false,\"pulse\":{\"baseRgb\":\"43D2B9\",\"basePalette\":43,\"alternateRgb\":\"005900\",\"alternatePalette\":44,\"cycleBeats\":0.25,\"alternatePhaseStartBeats\":0.125,\"transportOffsetBeats\":12.5}}"));
        host.close ();
    }


    @Test
    void publishesCurrentPadMovementAsOneCompleteSemanticFrame (@TempDir final Path directory) throws IOException
    {
        final Path statePath = directory.resolve (PushDebugSurfaceHost.STATE_FILE);
        final PushDebugSurfaceHost host = new PushDebugSurfaceHost (directory, null);
        final PushDebugSurfaceHost.DebugMusicalPulse pulse = new PushDebugSurfaceHost.DebugMusicalPulse (
            43, ColorEx.fromRGB (67, 210, 185), 44, ColorEx.fromRGB (0, 89, 0), 0.25, 0.125, 12.5);
        host.observePadSemantic (57, pulse);
        host.pollForTest ();

        host.observePadSemanticFrame ( () -> {
            host.observePadSemantic (57, null);
            host.observePadSemantic (58, pulse);
        });
        host.pollForTest ();

        final String live = Files.readString (statePath);
        assertTrue (live.contains ("\"revision\":3"), "both pad changes publish one revision");
        assertTrue (live.contains ("\"push.pad.57\":{\"rgb\":\"000000\",\"palette\":0,\"blinkRgb\":null,\"blinkPalette\":0,\"fast\":false,\"pulse\":null}"));
        assertTrue (live.contains ("\"push.pad.58\":{\"rgb\":\"000000\",\"palette\":0,\"blinkRgb\":null,\"blinkPalette\":0,\"fast\":false,\"pulse\":{"));
        host.close ();
    }


    @Test
    void publishesTheTransportClockAtBeatBoundariesAndCoalescesItsLatestSample (@TempDir final Path directory) throws IOException
    {
        final Path statePath = directory.resolve (PushDebugSurfaceHost.STATE_FILE);
        final PushDebugSurfaceHost host = new PushDebugSurfaceHost (directory, null);
        host.pollForTest ();

        host.observeClock (true, 140, 12.125, 1_000);
        host.pollForTest ();
        assertTrue (Files.readString (statePath).contains ("\"clock\":{\"available\":true,\"playing\":true,\"tempo\":140.0,\"positionBeats\":12.125,\"sampledAtMillis\":1000}"));

        host.observeClock (true, 140, 12.75, 1_250);
        host.pollForTest ();
        assertTrue (Files.readString (statePath).contains ("\"positionBeats\":12.125"), "sub-beat samples do not create standalone filesystem writes");

        host.observeButton (ButtonID.PLAY, 21, ColorEx.fromRGB (0, 255, 96));
        host.pollForTest ();
        assertTrue (Files.readString (statePath).contains ("\"positionBeats\":12.75"), "another output publication carries the freshest clock sample");

        host.observeClock (true, 140, 13, 1_500);
        host.pollForTest ();
        assertTrue (Files.readString (statePath).contains ("\"positionBeats\":13.0"));
        host.close ();
    }
}
