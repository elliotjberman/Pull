// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.controller.ableton.push.controller;

import org.openjdk.jmh.annotations.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageOutputStream;

/** Combined CPU cost of a sampled debug snapshot and PNG production; excludes file/HTTP I/O. */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 4, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(3)
@State(Scope.Thread)
public class DebugFrameBenchmark
{
    @Param({"capture", "unchanged", "macro", "random"}) public String content;
    private ByteBuffer[] frames;
    private int cursor;
    private PushDebugFrame previous;

    @Setup
    public void setup() throws Exception
    {
        FrameBenchmark fixtures = new FrameBenchmark();
        fixtures.content = content;
        fixtures.setup();
        frames = fixtures.frames;
        for (ByteBuffer frame : frames)
        {
            BufferedImage expected = ImageIO.read(new ByteArrayInputStream(legacyPng(frame)));
            previous = PushDebugFrame.capture(frame, 960, 160, previous);
            BufferedImage actual = ImageIO.read(new ByteArrayInputStream(previous.png()));
            if (!Arrays.equals(expected.getRGB(0, 0, 960, 160, null, 0, 960), actual.getRGB(0, 0, 960, 160, null, 0, 960)))
                throw new AssertionError("Debug PNG pixel mismatch");
        }
    }

    @Benchmark
    public byte[] baseline() throws Exception { return legacyPng(frames[cursor++ & 7]); }

    @Benchmark
    public byte[] optimized() throws Exception
    {
        previous = PushDebugFrame.capture(frames[cursor++ & 7], 960, 160, previous);
        return previous.png();
    }

    /** Frozen snapshot/conversion operations from PushDebugCaptureHost at 28b7059c. */
    private static byte[] legacyPng(ByteBuffer source) throws Exception
    {
        byte[] pixels = new byte[960 * 160 * 4];
        source.duplicate().get(pixels);
        ByteBuffer buffer = ByteBuffer.wrap(pixels);
        BufferedImage image = new BufferedImage(960, 160, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 160; y++) for (int x = 0; x < 960; x++)
        {
            int blue = buffer.get() & 255, green = buffer.get() & 255, red = buffer.get() & 255, alpha = buffer.get() & 255;
            image.setRGB(x, y, alpha << 24 | red << 16 | green << 8 | blue);
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        // Both versions use the same in-memory PNG sink, keeping filesystem latency outside
        // this CPU comparison while retaining the original per-pixel conversion work.
        try (var stream = new MemoryCacheImageOutputStream(output))
        {
            if (!ImageIO.write(image, "png", stream)) throw new AssertionError("No PNG writer");
        }
        return output.toByteArray();
    }
}
