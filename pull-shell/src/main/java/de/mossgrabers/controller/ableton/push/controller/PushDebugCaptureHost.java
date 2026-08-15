// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.controller.ableton.push.controller;

import de.mossgrabers.framework.graphics.IBitmap;
import de.mossgrabers.pull.shell.PushDebugging;

import javax.imageio.ImageIO;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Objects;
import java.util.stream.Stream;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;


/** Sampled latest-frame and request-correlated Push capture with filesystem work on one worker. */
final class PushDebugCaptureHost implements AutoCloseable
{
    static final String REQUEST_FILE       = "capture-request.txt";
    static final String STATUS_FILE        = "capture-status.txt";
    static final String LATEST_IMAGE_FILE  = "latest.png";
    static final String LATEST_STATUS_FILE = "latest-frame.txt";
    static final String SAMPLE_RATE_FILE   = "frame-sample-rate.txt";

    private static final int  DEFAULT_FRAME_SAMPLE_INTERVAL = 1;
    private static final int  MAX_FRAME_SAMPLE_INTERVAL     = 600;
    private static final int  MAX_REQUEST_BYTES             = 96;
    private static final int  MAX_CAPTURE_FILES             = 16;
    private static final long POLL_INTERVAL_MILLIS          = 100;

    private final Path                      directory;
    private final Path                      requestPath;
    private final Path                      statusPath;
    private final Path                      latestImagePath;
    private final Path                      latestStatusPath;
    private final Path                      sampleRatePath;
    private final ScheduledExecutorService  worker;
    private final AtomicReference<CaptureRequest> pendingRequest = new AtomicReference<> ();
    private final AtomicReference<Capture>  completedCapture = new AtomicReference<> ();
    private final AtomicReference<Frame>    latestFrame = new AtomicReference<> ();
    private final AtomicReference<Frame>    pendingLatestWrite = new AtomicReference<> ();
    private final AtomicInteger             frameSampleInterval = new AtomicInteger (DEFAULT_FRAME_SAMPLE_INTERVAL);
    private final AtomicLong                frameRevision = new AtomicLong ();
    private final AtomicLong                sampledFrames = new AtomicLong ();
    private final AtomicLong                coalescedFrames = new AtomicLong ();
    private final AtomicBoolean             drainScheduled = new AtomicBoolean ();
    private final AtomicBoolean             closed = new AtomicBoolean ();


    static PushDebugCaptureHost createIfEnabled ()
    {
        if (!PushDebugging.isEnabled ())
            return null;

        final ScheduledExecutorService worker = PushDebugging.createWorker ("Pull debug capture transport");
        final PushDebugCaptureHost host = new PushDebugCaptureHost (PushDebugging.directory (), worker);
        worker.scheduleWithFixedDelay (host::pollFilesSafely, 0, POLL_INTERVAL_MILLIS, TimeUnit.MILLISECONDS);
        return host;
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
        this.latestImagePath = directory.resolve (LATEST_IMAGE_FILE);
        this.latestStatusPath = directory.resolve (LATEST_STATUS_FILE);
        this.sampleRatePath = directory.resolve (SAMPLE_RATE_FILE);
        this.worker = worker;
    }


    /** Sample outbound frames at the live debug rate; a pending next-frame request forces a sample. */
    void observeFrame (final IBitmap image)
    {
        if (this.closed.get ())
            return;
        if (this.worker == null)
            this.pollFilesSafely ();

        final long revision = this.frameRevision.incrementAndGet ();
        final int sampleInterval = this.frameSampleInterval.get ();
        if (revision == 1 || revision % sampleInterval == 0 || this.pendingRequest.get () != null)
        {
            try
            {
                final long captureStarted = System.nanoTime ();
                Objects.requireNonNull (image, "image").encode ( (buffer, width, height) ->
                {
                    final byte [] pixels = copy (buffer, width, height);
                    final long captureMicros = TimeUnit.NANOSECONDS.toMicros (System.nanoTime () - captureStarted);
                    final Frame frame = new Frame (revision, pixels, width, height, sampleInterval, this.sampledFrames.incrementAndGet (), captureMicros);
                    this.latestFrame.set (frame);
                    if (this.pendingLatestWrite.getAndSet (frame) != null)
                        this.coalescedFrames.incrementAndGet ();
                    final CaptureRequest request = this.pendingRequest.getAndSet (null);
                    if (request != null)
                        this.completedCapture.set (Capture.ready (request.requestID (), frame));
                });
            }
            catch (final RuntimeException ex)
            {
                final CaptureRequest request = this.pendingRequest.getAndSet (null);
                if (request != null)
                    this.completedCapture.set (Capture.failed (request.requestID (), ex.getMessage ()));
            }
        }

        if (this.worker == null)
            this.pollFilesSafely ();
        else
            this.requestDrain ();
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
        if (this.closed.get () && this.completedCapture.get () == null && this.pendingLatestWrite.get () == null)
            return;
        try
        {
            this.readSampleInterval ();
            final Frame latest = this.pendingLatestWrite.getAndSet (null);
            if (latest != null)
                this.writeLatestFrame (latest);
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

        final String requestText;
        if (Files.size (this.requestPath) > MAX_REQUEST_BYTES)
            requestText = "";
        else
            requestText = Files.readString (this.requestPath).strip ();
        Files.deleteIfExists (this.requestPath);

        final String [] fields = requestText.split ("\\t", -1);
        final String requestID = fields.length == 0 ? "" : fields[0];
        final boolean nextFrame = fields.length == 2 && "NEXT_FRAME".equals (fields[1]);
        final boolean valid = PushDebugging.isIdentifier (requestID) && (fields.length == 1 || nextFrame);
        if (valid && !this.closed.get ())
        {
            final Frame frame = nextFrame ? null : this.latestFrame.get ();
            if (frame == null)
                this.pendingRequest.compareAndSet (null, new CaptureRequest (requestID));
            else
                this.completedCapture.compareAndSet (null, Capture.ready (requestID, frame));
        }
        else if (valid)
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
        PushDebugging.replaceAtomically (temporary, output);
        this.writeStatus (capture.requestID (), "READY", filename, "");
        this.pruneCaptures ();
    }


    private void writeLatestFrame (final Frame frame) throws IOException
    {
        Files.createDirectories (this.directory);
        final Path imageTemporary = this.latestImagePath.resolveSibling (LATEST_IMAGE_FILE + ".tmp");
        final long started = System.nanoTime ();
        writePng (frame.pixels (), frame.width (), frame.height (), imageTemporary);
        PushDebugging.replaceAtomically (imageTemporary, this.latestImagePath);
        final long writeMicros = TimeUnit.NANOSECONDS.toMicros (System.nanoTime () - started);

        final String status = String.join ("\t",
            Long.toString (frame.revision ()),
            Integer.toString (frame.width ()),
            Integer.toString (frame.height ()),
            Integer.toString (frame.sampleInterval ()),
            Long.toString (frame.sampledFrames ()),
            Long.toString (this.coalescedFrames.get ()),
            Long.toString (frame.captureMicros ()),
            Long.toString (writeMicros)) + "\n";
        final Path statusTemporary = this.latestStatusPath.resolveSibling (LATEST_STATUS_FILE + ".tmp");
        Files.writeString (statusTemporary, status);
        PushDebugging.replaceAtomically (statusTemporary, this.latestStatusPath);
    }


    private void readSampleInterval () throws IOException
    {
        if (!Files.isRegularFile (this.sampleRatePath, LinkOption.NOFOLLOW_LINKS))
        {
            this.frameSampleInterval.set (DEFAULT_FRAME_SAMPLE_INTERVAL);
            return;
        }

        final int requested;
        try
        {
            requested = Integer.parseInt (Files.readString (this.sampleRatePath).strip ());
        }
        catch (final NumberFormatException ex)
        {
            return;
        }
        if (requested >= 1 && requested <= MAX_FRAME_SAMPLE_INTERVAL)
            this.frameSampleInterval.set (requested);
    }


    private void requestDrain ()
    {
        if (this.worker == null || this.closed.get () || !this.drainScheduled.compareAndSet (false, true))
            return;
        this.worker.execute ( () -> {
            try
            {
                this.pollFilesSafely ();
            }
            finally
            {
                this.drainScheduled.set (false);
                if (this.pendingLatestWrite.get () != null || this.completedCapture.get () != null)
                    this.requestDrain ();
            }
        });
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
        final String content = String.join ("\t", requestID, state, filename, PushDebugging.sanitize (message)) + "\n";
        final Path temporary = this.statusPath.resolveSibling (this.statusPath.getFileName () + ".tmp");
        Files.writeString (temporary, content);
        PushDebugging.replaceAtomically (temporary, this.statusPath);
    }


    @Override
    public void close ()
    {
        if (!this.closed.compareAndSet (false, true))
            return;

        final CaptureRequest pending = this.pendingRequest.getAndSet (null);
        if (pending != null)
            this.completedCapture.set (Capture.failed (pending.requestID (), "extension is closing"));
        if (this.worker == null)
        {
            this.pollFilesSafely ();
            return;
        }

        PushDebugging.shutdownWorker (this.worker, this::pollFilesSafely);
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


    private record Capture (String requestID, byte [] pixels, int width, int height, String message)
    {
        private static Capture ready (final String requestID, final Frame frame)
        {
            return new Capture (requestID, frame.pixels (), frame.width (), frame.height (), "");
        }


        private static Capture failed (final String requestID, final String message)
        {
            return new Capture (requestID, null, 0, 0, PushDebugging.sanitize (message));
        }
    }


    private record CaptureRequest (String requestID)
    {
    }


    private record Frame (long revision, byte [] pixels, int width, int height, int sampleInterval, long sampledFrames, long captureMicros)
    {
    }
}
