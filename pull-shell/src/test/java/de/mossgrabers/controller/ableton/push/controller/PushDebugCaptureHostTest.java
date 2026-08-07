// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.controller.ableton.push.controller;

import de.mossgrabers.framework.graphics.IBitmap;
import de.mossgrabers.framework.graphics.IEncoder;
import de.mossgrabers.framework.graphics.IRenderer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


/** Request identity and exact-frame contracts for the Push debugger capture transport. */
class PushDebugCaptureHostTest
{
    @TempDir
    Path debugDirectory;


    @Test
    void publishesOnlyTheRequestedFramebufferUnderItsRequestID () throws IOException
    {
        final PushDebugCaptureHost host = new PushDebugCaptureHost (this.debugDirectory);
        Files.writeString (this.debugDirectory.resolve (PushDebugCaptureHost.STATUS_FILE), "stale\tREADY\tdisplay-stale.png\t\n");
        Files.writeString (this.debugDirectory.resolve (PushDebugCaptureHost.REQUEST_FILE), "frame-1\n");

        host.capturePending (new FakeBitmap (new byte []
        {
            3, 2, 1, (byte) 255
        }, 1, 1));

        final List<String> status = this.status ();
        assertEquals (List.of ("frame-1", "READY", "display-frame-1.png", ""), status);
        final Path output = this.debugDirectory.resolve (status.get (2));
        assertTrue (Files.isRegularFile (output));
        assertEquals (0xFF010203, ImageIO.read (output.toFile ()).getRGB (0, 0));
    }


    @Test
    void invalidRequestCannotProduceAnUncorrelatedImage () throws IOException
    {
        final PushDebugCaptureHost host = new PushDebugCaptureHost (this.debugDirectory);
        Files.writeString (this.debugDirectory.resolve (PushDebugCaptureHost.REQUEST_FILE), "../escape\n");

        host.capturePending (new FakeBitmap (new byte [4], 1, 1));

        final List<String> status = this.status ();
        assertEquals ("invalid", status.get (0));
        assertEquals ("FAILED", status.get (1));
        assertTrue (status.get (3).contains ("invalid capture request"));
        try (Stream<Path> files = Files.list (this.debugDirectory))
        {
            assertEquals (0L, files.filter (path -> path.getFileName ().toString ().endsWith (".png")).count ());
        }
    }


    @Test
    void shutdownFailsAClaimedRequestWithoutCapturing () throws IOException
    {
        final PushDebugCaptureHost host = new PushDebugCaptureHost (this.debugDirectory);
        Files.writeString (this.debugDirectory.resolve (PushDebugCaptureHost.REQUEST_FILE), "closing-frame\n");
        host.pollForTest ();

        host.close ();

        final List<String> status = this.status ();
        assertEquals ("closing-frame", status.get (0));
        assertEquals ("FAILED", status.get (1));
        assertTrue (status.get (3).contains ("closing"));
    }


    private List<String> status () throws IOException
    {
        final String content = Files.readString (this.debugDirectory.resolve (PushDebugCaptureHost.STATUS_FILE));
        return List.of (content.substring (0, content.length () - 1).split ("\t", -1));
    }


    private record FakeBitmap (byte [] pixels, int width, int height) implements IBitmap
    {
        @Override
        public void setDisplayWindowTitle (final String title)
        {
            // Not used.
        }


        @Override
        public void showDisplayWindow ()
        {
            // Not used.
        }


        @Override
        public void render (final boolean enableAntialias, final IRenderer renderer)
        {
            // Not used.
        }


        @Override
        public void encode (final IEncoder encoder)
        {
            encoder.encode (ByteBuffer.wrap (this.pixels), this.width, this.height);
        }
    }
}
