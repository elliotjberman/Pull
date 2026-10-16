// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.controller.ableton.push.controller;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.Arrays;


/**
 * Converts a finished BGRA bitmap to the Push 2 wire format. Pixel comparisons invalidate the
 * cache independently of display components. The caller serializes encode/copy access.
 *
 * Separate int-array passes with matching indices let HotSpot generate four-lane NEON arithmetic.
 * Fusing calibration, packing and short stores prevented vectorization in measured prototypes.
 * Keep narrowing separate; see benchmarks/push-display for compatibility and performance evidence.
 */
final class PushFrameEncoder
{
    static final int WIDTH = 960;
    static final int HEIGHT = 160;
    static final int STRIDE = 1024;
    static final int DATA_SIZE = STRIDE * HEIGHT * 2;

    // Two cells fit a 120-pixel column without coupling the cache to layout. One-row cells avoid
    // recalibrating the column for a small edit. Width 60 preserves XOR phase and four-int lanes;
    // larger 2D cells benchmarked worse.
    private static final int CELL_WIDTH = 60;
    private final int [] previousFrame = new int [HEIGHT * WIDTH];
    private final IntBuffer previousFrameView = IntBuffer.wrap (this.previousFrame);
    private boolean hasPreviousFrame;
    private final int [] rowPixels = new int [WIDTH];
    private final int [] cellPixels = new int [CELL_WIDTH];
    private final int [] allGrayMasks = new int [CELL_WIDTH];
    private final int [] grayMasks = new int [CELL_WIDTH];
    private final int [] luminance = new int [CELL_WIDTH];
    private final int [] red = new int [CELL_WIDTH];
    private final int [] green = new int [CELL_WIDTH];
    private final int [] blue = new int [CELL_WIDTH];
    private final int [] shaping = new int [CELL_WIDTH];
    private final short [] wire = new short [STRIDE * HEIGHT];

    PushFrameEncoder ()
    {
        // Push requires alternating XOR words 0xF3E7/0xFFE7. Initialize padding in its final
        // form; encoding replaces only visible pixels.
        Arrays.fill (this.allGrayMasks, -1);
        for (int i = 0; i < CELL_WIDTH; i++)
            this.shaping[i] = 0xF3E7 ^ (i & 1) * 0x0C00;
        for (int i = 0; i < this.wire.length; i++)
            this.wire[i] = (short) (0xF3E7 ^ (i & 1) * 0x0C00);
    }


    void encode (final ByteBuffer source)
    {
        if (source.remaining () < WIDTH * HEIGHT * Integer.BYTES)
            throw new IllegalArgumentException ("Incomplete Push bitmap");
        final IntBuffer input = source.duplicate ().order (ByteOrder.LITTLE_ENDIAN).asIntBuffer ();
        input.limit (WIDTH * HEIGHT);
        // Compare host memory directly to avoid copying unchanged input. Exact equality keeps
        // cache hits collision-free; mismatch stops at the first changed pixel.
        if (this.hasPreviousFrame && input.mismatch (this.previousFrameView) < 0)
        {
            source.rewind ();
            return;
        }
        for (int row = 0; row < HEIGHT; row++)
        {
            input.get (this.rowPixels);
            // Unchanged rows bypass per-cell bookkeeping, even inside a changing frame.
            final int base = row * WIDTH;
            if (this.hasPreviousFrame && Arrays.equals (this.rowPixels, 0, WIDTH, this.previousFrame, base, base + WIDTH))
                continue;
            for (int column = 0; column < WIDTH; column += CELL_WIDTH)
            {
                final int previous = base + column;
                if (this.hasPreviousFrame && Arrays.equals (this.rowPixels, column, column + CELL_WIDTH, this.previousFrame, previous, previous + CELL_WIDTH))
                    continue;
                // Retain raw BGRA before the scratch pixels are overwritten by calibrated words.
                System.arraycopy (this.rowPixels, column, this.previousFrame, previous, CELL_WIDTH);
                System.arraycopy (this.rowPixels, column, this.cellPixels, 0, CELL_WIDTH);
                luminance (this.cellPixels, this.luminance, this.grayMasks);
                if (Arrays.equals (this.grayMasks, this.allGrayMasks))
                    packUncalibrated (this.cellPixels, this.shaping);
                else
                {
                    channel (this.cellPixels, this.luminance, this.grayMasks, this.red, 16);
                    channel (this.cellPixels, this.luminance, this.grayMasks, this.green, 8);
                    channel (this.cellPixels, this.luminance, this.grayMasks, this.blue, 0);
                    pack (this.red, this.green, this.blue, this.shaping, this.cellPixels);
                }
                narrow (this.cellPixels, this.wire, row * STRIDE + column);
            }
        }
        this.hasPreviousFrame = true;
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
        for (int x = 0; x < CELL_WIDTH; x++)
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
        for (int x = 0; x < CELL_WIDTH; x++)
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
        for (int x = 0; x < CELL_WIDTH; x++)
        {
            final int packed = (reds[x] >> 3) | (greens[x] & 0xFC) << 3 | (blues[x] & 0xF8) << 8;
            target[x] = packed ^ shaping[x];
        }
    }


    private static void packUncalibrated (final int [] pixels, final int [] shaping)
    {
        for (int x = 0; x < CELL_WIDTH; x++)
        {
            final int pixel = pixels[x];
            pixels[x] = ((pixel >>> 19 & 31) | (pixel >>> 5 & 0x7E0) | (pixel << 8 & 0xF800)) ^ shaping[x];
        }
    }


    // A separate, cheap scalar narrowing pass leaves the heavier int arithmetic vectorizable.
    private static void narrow (final int [] source, final short [] target, final int offset)
    {
        for (int x = 0; x < CELL_WIDTH; x++)
            target[offset + x] = (short) source[x];
    }
}
