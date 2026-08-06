// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.controller.ableton.push.controller;

import de.mossgrabers.framework.graphics.IBitmap;

import javax.imageio.ImageIO;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.stream.Stream;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;


/** Request-correlated Push framebuffer capture with filesystem work isolated to one owned worker. */
final class PushDebugCaptureHost implements AutoCloseable
{
    static final String REQUEST_FILE = "capture-request.txt";
    static final String STATUS_FILE  = "capture-status.txt";

    private static final int  MAX_REQUEST_BYTES    = 96;
    private static final int  MAX_CAPTURE_FILES    = 16;
    private static final long POLL_INTERVAL_MILLIS = 100;
    private static final long SHUTDOWN_WAIT_MILLIS = 250;

    private final Path                      directory;
    private final Path                      requestPath;
    private final Path                      statusPath;
    private final ScheduledExecutorService  worker;
    private final AtomicReference<String>   pendingRequest = new AtomicReference<> ();
    private final AtomicReference<Capture>  completedCapture = new AtomicReference<> ();
    private final AtomicBoolean             closed = new AtomicBoolean ();


    PushDebugCaptureHost ()
    {
        this (
            Path.of (System.getProperty ("user.home"), ".drivenbymoss", "pull", "debug"),
            Executors.newSingleThreadScheduledExecutor (task -> {
                final Thread thread = new Thread (task, "Pull debug capture transport");
                thread.setDaemon (true);
                return thread;
            }));
        this.worker.scheduleWithFixedDelay (this::pollFilesSafely, 0, POLL_INTERVAL_MILLIS, TimeUnit.MILLISECONDS);
    }


    PushDebugCaptureHost (final Path directory)
    {
        this (directory, null);
    }


    private PushDebugCaptureHost (final Path directory, final ScheduledExecutorService worker)
    {
        this.directory = Objects.requireNonNull (directory, "directory");
        this.requestPath = directory.resolve (REQUEST_FILE);
        this.statusPath = directory.resolve (STATUS_FILE);
        this.worker = worker;
    }


    /** Claim at most one request and copy that exact rendered frame before returning. */
    void capturePending (final IBitmap image)
    {
        if (this.closed.get ())
            return;
        if (this.worker == null)
            this.pollFilesSafely ();

        final String requestID = this.pendingRequest.getAndSet (null);
        if (requestID != null)
        {
            try
            {
                Objects.requireNonNull (image, "image").encode ( (buffer, width, height) ->
                    this.completedCapture.set (Capture.ready (requestID, copy (buffer, width, height), width, height)));
            }
            catch (final RuntimeException ex)
            {
                this.completedCapture.set (Capture.failed (requestID, ex.getMessage ()));
            }
        }

        if (this.worker == null)
            this.pollFilesSafely ();
    }


    /** Deterministic transport seam for tests. */
    void pollForTest ()
    {
        if (this.worker != null)
            throw new IllegalStateException ("Production capture transport polls itself");
        this.pollFilesSafely ();
    }


    private void pollFilesSafely ()
    {
        if (this.closed.get () && this.completedCapture.get () == null)
            return;
        try
        {
            final Capture capture = this.completedCapture.getAndSet (null);
            if (capture != null)
                this.writeCapture (capture);
            if (!this.closed.get () && this.pendingRequest.get () == null && this.completedCapture.get () == null)
                this.readRequest ();
        }
        catch (final IOException | RuntimeException ignored)
        {
            // The protocol client times out; transport failure cannot affect display delivery.
        }
    }


    private void readRequest () throws IOException
    {
        if (!Files.isRegularFile (this.requestPath, LinkOption.NOFOLLOW_LINKS))
            return;

        final String requestID;
        if (Files.size (this.requestPath) > MAX_REQUEST_BYTES)
            requestID = "";
        else
            requestID = Files.readString (this.requestPath).strip ();
        Files.deleteIfExists (this.requestPath);

        if (isRequestID (requestID) && !this.closed.get ())
            this.pendingRequest.compareAndSet (null, requestID);
        else if (isRequestID (requestID))
            this.completedCapture.compareAndSet (null, Capture.failed (requestID, "extension is closing"));
        else
            this.completedCapture.compareAndSet (null, Capture.failed ("invalid", "invalid capture request ID"));
    }


    private void writeCapture (final Capture capture) throws IOException
    {
        Files.createDirectories (this.directory);
        if (capture.pixels () == null)
        {
            this.writeStatus (capture.requestID (), "FAILED", "", capture.message ());
            return;
        }

        final String filename = "display-" + capture.requestID () + ".png";
        final Path output = this.directory.resolve (filename);
        final Path temporary = this.directory.resolve (filename + ".tmp");
        writePng (capture.pixels (), capture.width (), capture.height (), temporary);
        try
        {
            Files.move (temporary, output, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        }
        catch (final IOException ex)
        {
            Files.move (temporary, output, StandardCopyOption.REPLACE_EXISTING);
        }
        this.writeStatus (capture.requestID (), "READY", filename, "");
        this.pruneCaptures ();
    }


    private void pruneCaptures () throws IOException
    {
        final java.util.List<Path> captures;
        try (Stream<Path> files = Files.list (this.directory))
        {
            captures = files
                .filter (path -> path.getFileName ().toString ().startsWith ("display-") && path.getFileName ().toString ().endsWith (".png"))
                .sorted ()
                .toList ();
        }
        for (int index = 0; index < captures.size () - MAX_CAPTURE_FILES; index++)
            Files.deleteIfExists (captures.get (index));
    }


    private void writeStatus (final String requestID, final String state, final String filename, final String message) throws IOException
    {
        final String content = String.join ("\t", requestID, state, filename, sanitize (message)) + "\n";
        final Path temporary = this.statusPath.resolveSibling (this.statusPath.getFileName () + ".tmp");
        Files.writeString (temporary, content);
        try
        {
            Files.move (temporary, this.statusPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        }
        catch (final IOException ex)
        {
            Files.move (temporary, this.statusPath, StandardCopyOption.REPLACE_EXISTING);
        }
    }


    @Override
    public void close ()
    {
        if (!this.closed.compareAndSet (false, true))
            return;

        final String pending = this.pendingRequest.getAndSet (null);
        if (pending != null)
            this.completedCapture.set (Capture.failed (pending, "extension is closing"));
        if (this.worker == null)
        {
            this.pollFilesSafely ();
            return;
        }

        this.worker.execute (this::pollFilesSafely);
        this.worker.shutdown ();
        try
        {
            if (!this.worker.awaitTermination (SHUTDOWN_WAIT_MILLIS, TimeUnit.MILLISECONDS))
                this.worker.shutdownNow ();
        }
        catch (final InterruptedException ex)
        {
            this.worker.shutdownNow ();
            Thread.currentThread ().interrupt ();
        }
    }


    private static byte [] copy (final ByteBuffer source, final int width, final int height)
    {
        final int length = Math.multiplyExact (Math.multiplyExact (width, height), 4);
        final ByteBuffer buffer = source.duplicate ();
        buffer.rewind ();
        if (buffer.remaining () < length)
            throw new IllegalArgumentException ("framebuffer is smaller than its dimensions");
        final byte [] pixels = new byte [length];
        buffer.get (pixels);
        return pixels;
    }


    private static void writePng (final byte [] pixels, final int width, final int height, final Path output) throws IOException
    {
        final ByteBuffer buffer = ByteBuffer.wrap (pixels);
        final BufferedImage image = new BufferedImage (width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++)
        {
            for (int x = 0; x < width; x++)
            {
                final int blue = buffer.get () & 0xFF;
                final int green = buffer.get () & 0xFF;
                final int red = buffer.get () & 0xFF;
                final int alpha = buffer.get () & 0xFF;
                image.setRGB (x, y, alpha << 24 | red << 16 | green << 8 | blue);
            }
        }
        if (!ImageIO.write (image, "png", output.toFile ()))
            throw new IOException ("PNG encoder is unavailable");
    }


    private static boolean isRequestID (final String value)
    {
        if (value == null || value.isEmpty () || value.length () > 80)
            return false;
        for (int index = 0; index < value.length (); index++)
        {
            final char character = value.charAt (index);
            if (!Character.isLetterOrDigit (character) && character != '.' && character != '_' && character != '-')
                return false;
        }
        return true;
    }


    private static String sanitize (final String value)
    {
        return value == null ? "" : value.replace ('\t', ' ').replace ('\r', ' ').replace ('\n', ' ');
    }


    private record Capture (String requestID, byte [] pixels, int width, int height, String message)
    {
        private static Capture ready (final String requestID, final byte [] pixels, final int width, final int height)
        {
            return new Capture (requestID, pixels, width, height, "");
        }


        private static Capture failed (final String requestID, final String message)
        {
            return new Capture (requestID, null, 0, 0, sanitize (message));
        }
    }
}
