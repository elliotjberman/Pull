// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.controller.ableton.push.controller;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.Arrays;

/**
 * Pixel-only 960x160 BGRA-to-BGR565 transport encoder, including row padding and signal shaping.
 * It consumes any finished bitmap: components, layouts, views and animation stay in the renderer.
 * All invalidation comes from exact pixel comparisons, so adding a component needs no cache hooks.
 * Caller serializes encoding and copying; bounded primitive storage stays below 1 MiB.
 *
 * The seemingly repetitive loops are intentional. Row-local int arrays with matching indices let
 * HotSpot generate four-lane NEON arithmetic on Bitwig's bundled AArch64 JVM. Fusing calibration,
 * packing and short stores inhibited vectorization in measured prototypes. Keep the cheap scalar
 * narrowing pass separate and benchmark generated code before simplifying these loops.
 * See benchmarks/push-display for exhaustive compatibility tests, rejected variants and evidence.
 */
final class PushFrameEncoder
{
    static final int WIDTH = 960;
    static final int HEIGHT = 160;
    static final int STRIDE = 1024;
    static final int DATA_SIZE = STRIDE * HEIGHT * 2;

    // Two cells per common 120-pixel column, but this is only a cache geometry, not UI ownership.
    // One-row cells avoid invalidating a whole column for a small label or meter change. Width 60
    // preserves the two-pixel XOR phase and four-int SIMD lanes; larger 2D cells benchmarked worse.
    private static final int CHUNK = 60;
    private final int [] previousFrame = new int [HEIGHT * WIDTH];
    private final IntBuffer previousInput = IntBuffer.wrap (this.previousFrame);
    private boolean valid;
    private final int [] rowPixels = new int [WIDTH];
    private final int [] pixels = new int [CHUNK];
    private final int [] allGray = new int [CHUNK];
    private final int [] grayMasks = new int [CHUNK];
    private final int [] luminance = new int [CHUNK];
    private final int [] red = new int [CHUNK];
    private final int [] green = new int [CHUNK];
    private final int [] blue = new int [CHUNK];
    private final int [] shaping = new int [CHUNK];
    private final short [] wire = new short [STRIDE * HEIGHT];

    PushFrameEncoder ()
    {
        // Padding stays zero before shaping. Visible pixels replace their already-shaped words.
        Arrays.fill (this.allGray, -1);
        for (int i = 0; i < CHUNK; i++) this.shaping[i] = 0xF3E7 ^ (i & 1) * 0x0C00;
        for (int i = 0; i < this.wire.length; i++)
            this.wire[i] = (short) (0xF3E7 ^ (i & 1) * 0x0C00);
    }

    void encode (final ByteBuffer source)
    {
        if (source.remaining () < WIDTH * HEIGHT * Integer.BYTES)
            throw new IllegalArgumentException ("Incomplete Push bitmap");
        final IntBuffer input = source.duplicate ().order (ByteOrder.LITTLE_ENDIAN).asIntBuffer ();
        input.limit (WIDTH * HEIGHT);
        // NIO mismatch compares the host buffer directly with the cached raw frame. It can stop
        // at the first change, and avoids copying unchanged input merely to compare it. A hash
        // would still need an exact comparison on hits to preserve the wire contract.
        if (this.valid && input.mismatch (this.previousInput) < 0)
        {
            source.rewind ();
            return;
        }
        for (int row = 0; row < HEIGHT; row++)
        {
            input.get (this.rowPixels);
            // Unchanged rows bypass per-cell bookkeeping, even inside a changing frame.
            final int base = row * WIDTH;
            if (this.valid && Arrays.equals (this.rowPixels, 0, WIDTH, this.previousFrame, base, base + WIDTH))
                continue;
            for (int column = 0; column < WIDTH; column += CHUNK)
            {
                final int previous = base + column;
                if (this.valid && Arrays.equals (this.rowPixels, column, column + CHUNK, this.previousFrame, previous, previous + CHUNK))
                    continue;
                // Retain raw BGRA before the scratch pixels are overwritten by calibrated words.
                System.arraycopy (this.rowPixels, column, this.previousFrame, previous, CHUNK);
                System.arraycopy (this.rowPixels, column, this.pixels, 0, CHUNK);
                luminance (this.pixels, this.luminance, this.grayMasks);
                if (Arrays.equals (this.grayMasks, this.allGray))
                    packUncalibrated (this.pixels, this.shaping);
                else
                {
                    channel (this.pixels, this.luminance, this.grayMasks, this.red, 16);
                    channel (this.pixels, this.luminance, this.grayMasks, this.green, 8);
                    channel (this.pixels, this.luminance, this.grayMasks, this.blue, 0);
                    pack (this.red, this.green, this.blue, this.shaping, this.pixels);
                }
                narrow (this.pixels, this.wire, row * STRIDE + column);
            }
        }
        this.valid = true;
        source.rewind ();
    }

    void copyTo (final ByteBuffer destination)
    {
        destination.clear ();
        destination.duplicate ().order (ByteOrder.LITTLE_ENDIAN).asShortBuffer ().put (this.wire);
        destination.position (DATA_SIZE);
    }

    private static void luminance (final int [] source, final int [] target, final int [] masks)
    {
        for (int x = 0; x < CHUNK; x++)
        {
            final int pixel = source[x];
            final int red = pixel >>> 16 & 255;
            final int green = pixel >>> 8 & 255;
            final int blue = pixel & 255;
            target[x] = (54 * red + 183 * green + 19 * blue) >> 8;
            masks[x] = (Math.max (red, Math.max (green, blue)) - Math.min (red, Math.min (green, blue)) - PushColorCalibration.CHROMA_THRESHOLD) >> 31;
        }
    }

    private static void channel (final int [] source, final int [] luminance, final int [] masks, final int [] target, final int shift)
    {
        for (int x = 0; x < CHUNK; x++)
        {
            final int value = source[x] >>> shift & 255;
            final int gray = luminance[x];
            final int chroma = (value - gray) * PushColorCalibration.DISPLAY_SATURATION;
            // Preserve Java's truncation toward zero for negative chroma; an arithmetic shift
            // alone would round down and change colors. Negative final channels clamp to zero,
            // so the brightness division can use a shift without the same correction.
            final int saturated = gray + ((chroma + ((chroma >> 31) & 255)) >> 8);
            final int calibrated = Math.max (0, Math.min (255, saturated * PushColorCalibration.DISPLAY_BRIGHTNESS >> 8));
            target[x] = (value & masks[x]) | (calibrated & ~masks[x]);
        }
    }

    private static void pack (final int [] reds, final int [] greens, final int [] blues, final int [] shaping, final int [] target)
    {
        for (int x = 0; x < CHUNK; x++)
        {
            final int packed = (reds[x] >> 3) | (greens[x] & 0xFC) << 3 | (blues[x] & 0xF8) << 8;
            target[x] = packed ^ shaping[x];
        }
    }

    private static void packUncalibrated (final int [] pixels, final int [] shaping)
    {
        for (int x = 0; x < CHUNK; x++)
        {
            final int pixel = pixels[x];
            pixels[x] = ((pixel >>> 19 & 31) | (pixel >>> 5 & 0x7E0) | (pixel << 8 & 0xF800)) ^ shaping[x];
        }
    }

    // A separate, cheap scalar narrowing pass leaves the heavier int arithmetic vectorizable.
    private static void narrow (final int [] source, final short [] target, final int offset)
    {
        for (int x = 0; x < CHUNK; x++)
            target[offset + x] = (short) source[x];
    }
}
