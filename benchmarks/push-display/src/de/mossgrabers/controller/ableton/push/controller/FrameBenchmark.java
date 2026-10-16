// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.controller.ableton.push.controller;

import org.openjdk.jmh.annotations.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** One operation is a complete frame plus USB-memory copy, excluding USB I/O and rendering. */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 4, time = 1)
@Measurement(iterations = 6, time = 1)
@Fork(3)
@State(Scope.Thread)
public class FrameBenchmark
{
    @Param({"ui", "random", "gray"}) public String content;
    private ByteBuffer[] frames;
    private final byte[] wire = new byte[327680];
    private final ByteBuffer destination = ByteBuffer.allocateDirect(327680);
    private int cursor;
    private final PushFrameEncoder encoder = new PushFrameEncoder();

    @Setup
    public void setup() throws Exception
    {
        frames = new ByteBuffer[8];
        List<Path> captures = List.of();
        if (content.equals("capture"))
        {
            try (var paths = Files.list(Path.of(System.getProperty("pull.benchmark.frames"))))
            {
                captures = paths.filter(p -> p.toString().endsWith(".png")).sorted().limit(8).toList();
            }
            if (captures.size() != 8) throw new IllegalArgumentException("Supply eight 960x160 PNG fixtures");
        }
        Random random = new Random(0x50555348);
        int[] palette = {0, 0x202020, 0x606060, 0xFFFFFF, 0x2878DF, 0xE84545, 0x45BD6C, 0xC39032};
        for (int f = 0; f < frames.length; f++)
        {
            ByteBuffer frame = frames[f] = ByteBuffer.allocateDirect(960 * 160 * 4);
            BufferedImage capture = captures.isEmpty() ? null : ImageIO.read(captures.get(f).toFile());
            if (capture != null && (capture.getWidth() != 960 || capture.getHeight() != 160))
                throw new IllegalArgumentException("Wrong capture dimensions");
            for (int y = 0; y < 160; y++) for (int x = 0; x < 960; x++)
            {
                int rgb;
                if (capture != null) rgb = capture.getRGB(x, y);
                else if (content.equals("random")) rgb = random.nextInt();
                else if (content.equals("gray")) rgb = ((x + y + f) & 255) * 0x010101;
                else rgb = palette[((x / 120 + (content.equals("unchanged") ? 0 : f)) % 6 + 2) * ((y > 32 && y < 120 && x % 120 < 80) ? 1 : 0)];
                frame.put((byte) rgb).put((byte) (rgb >> 8)).put((byte) (rgb >> 16)).put((byte) 255);
            }
            frame.flip();
        }
        // Validate complete outputs outside the measured region, including padding and row reuse.
        byte[] actual = new byte[wire.length];
        for (ByteBuffer frame : frames)
        {
            LegacyFrameEncoder.encode(frame, wire);
            encoder.encode(frame);
            encoder.copyTo(destination);
            destination.flip();
            destination.get(actual);
            if (!Arrays.equals(wire, actual))
                throw new AssertionError("Wire mismatch at " + Arrays.mismatch(wire, actual));
        }
    }

    @Benchmark
    public int optimized()
    {
        encoder.encode(frames[cursor++ & 7]);
        encoder.copyTo(destination);
        return destination.get((cursor * 31) & 0x3FFFF);
    }

    @Benchmark
    public int baselineBulkCopy()
    {
        LegacyFrameEncoder.encode(frames[cursor++ & 7], wire);
        destination.clear();
        destination.put(wire);
        return destination.get((cursor * 31) & 0x3FFFF);
    }

    @Benchmark
    public int baseline()
    {
        LegacyFrameEncoder.encode(frames[cursor++ & 7], wire);
        LegacyFrameEncoder.copy(wire, destination);
        return destination.get((cursor * 31) & 0x3FFFF);
    }
}
