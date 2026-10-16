// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.controller.ableton.push.controller;

import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Immutable, pre-calibration pixel snapshot for the optional debugger. Knows nothing about display
 * components or the hardware encoder. Exact bulk comparison reuses unchanged snapshots; changed
 * input is copied because the host bitmap can be overwritten while the debug worker reads it.
 * PNG construction is lazy and confined to the single capture worker, which also reuses it for
 * request-correlated captures. Never mutate the returned PNG bytes.
 */
final class PushDebugFrame
{
    private final byte [] pixels;
    private final ByteBuffer comparison;
    private final int width;
    private final int height;
    private byte [] png;

    private PushDebugFrame (final byte [] pixels, final int width, final int height)
    {
        this.pixels = pixels;
        this.comparison = ByteBuffer.wrap (pixels).asReadOnlyBuffer ();
        this.width = width;
        this.height = height;
    }

    static PushDebugFrame capture (final ByteBuffer source, final int width, final int height, final PushDebugFrame previous)
    {
        if (width <= 0 || height <= 0)
            throw new IllegalArgumentException ("Invalid framebuffer dimensions");
        final int length = Math.multiplyExact (Math.multiplyExact (width, height), Integer.BYTES);
        final ByteBuffer input = source.duplicate ();
        input.rewind ();
        if (input.remaining () < length)
            throw new IllegalArgumentException ("framebuffer is smaller than its dimensions");
        input.limit (length);
        if (previous != null && previous.width == width && previous.height == height && input.mismatch (previous.comparison) < 0)
            return previous;
        final byte [] pixels = new byte [length];
        input.get (pixels);
        return new PushDebugFrame (pixels, width, height);
    }

    int width () { return this.width; }
    int height () { return this.height; }

    byte [] png () throws IOException
    {
        if (this.png == null)
        {
            final BufferedImage image = new BufferedImage (this.width, this.height, BufferedImage.TYPE_INT_ARGB);
            // A little-endian BGRA word is exactly an ARGB int. Bulk transfer avoids setRGB's
            // per-pixel bounds checks and color-model conversion; alpha remains unmodified.
            final int [] argb = ((DataBufferInt) image.getRaster ().getDataBuffer ()).getData ();
            ByteBuffer.wrap (this.pixels).order (ByteOrder.LITTLE_ENDIAN).asIntBuffer ().get (argb);
            final ByteArrayOutputStream output = new ByteArrayOutputStream ();
            // Explicit memory caching avoids ImageIO's process-global default, which may use
            // temporary files even for a ByteArrayOutputStream. Only the transport writes files.
            try (var stream = new MemoryCacheImageOutputStream (output))
            {
                if (!ImageIO.write (image, "png", stream))
                    throw new IOException ("PNG encoder is unavailable");
            }
            this.png = output.toByteArray ();
        }
        return this.png;
    }
}
