// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.shell.runtime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


/** Tests for stable managed-preset publication. */
class BundledDrumPitchPresetTest
{
    @TempDir
    Path temporaryDirectory;


    @Test
    void publishesTheBrandedPresetIdempotently () throws IOException
    {
        final RuntimePaths paths = new RuntimePaths (this.temporaryDirectory);
        final Path first = BundledDrumPitchPreset.materialize (paths);
        final byte [] firstContents = Files.readAllBytes (first);
        final Path second = BundledDrumPitchPreset.materialize (paths);

        assertEquals (first, second);
        assertEquals (BundledDrumPitchPreset.PRESET_NAME + ".bwpreset", first.getFileName ().toString ());
        assertEquals (6684, firstContents.length);
        assertArrayEquals (firstContents, Files.readAllBytes (second));
        final String binaryText = new String (firstContents, StandardCharsets.ISO_8859_1);
        assertTrue (binaryText.contains (BundledDrumPitchPreset.PRESET_NAME));
        assertTrue (binaryText.contains (BundledDrumPitchPreset.PRESET_CREATOR));
        assertTrue (binaryText.contains ("Managed Pull drum-pitch helper. Do not edit manually."));
        assertEquals (2, occurrences (firstContents, "6aec6e78-9c1e-4c0b-8a88-0c2c37890a1d".getBytes (StandardCharsets.US_ASCII)));
        assertEquals (1, occurrences (firstContents, new byte []
        {
            0x6a,
            (byte) 0xec,
            0x6e,
            0x78,
            (byte) 0x9c,
            0x1e,
            0x4c,
            0x0b,
            (byte) 0x8a,
            (byte) 0x88,
            0x0c,
            0x2c,
            0x37,
            (byte) 0x89,
            0x0a,
            0x1d
        }));

        assertDoubleSetting (firstContents, "SEMITONES", 0);
        assertBooleanSetting (firstContents, "DELAY_ON", true);
        assertBooleanSetting (firstContents, "ENV_DELAY_SYNC", false);
        assertDoubleSetting (firstContents, "ENV_DELAY_SECONDS", 2);
        assertBooleanSetting (firstContents, "SYNC", false);
        assertDoubleSetting (firstContents, "DURATION_IN_SECONDS", 2);
        assertDoubleSetting (firstContents, "BEND", 0);
        assertDoubleSetting (firstContents, "OFFSET", 0);
        assertIntegerSetting (firstContents, "DURATION_IN_BEATS", 16);
        assertIntegerSetting (firstContents, "ENV_DELAY_BEATS", 16);
        assertDoubleSetting (firstContents, "ENV_DELAY_BEATS_OFFSET", 0);

        Files.writeString (first, "corrupt");
        final Path repaired = BundledDrumPitchPreset.materialize (paths);
        assertEquals (first, repaired);
        assertArrayEquals (firstContents, Files.readAllBytes (repaired));
    }


    private static void assertDoubleSetting (final byte [] preset, final String name, final double value)
    {
        assertEquals (1, occurrences (preset, settingSentinel (name, new byte []
        {
            0,
            0,
            1,
            0x36,
            7
        }, ByteBuffer.allocate (Double.BYTES).putDouble (value).array ())), name);
    }


    private static void assertIntegerSetting (final byte [] preset, final String name, final int value)
    {
        assertEquals (1, occurrences (preset, settingSentinel (name, new byte []
        {
            0,
            0,
            3,
            0x30,
            1
        }, new byte []
        {
            (byte) value
        })), name);
    }


    private static void assertBooleanSetting (final byte [] preset, final String name, final boolean value)
    {
        assertEquals (1, occurrences (preset, settingSentinel (name, new byte []
        {
            0,
            0,
            1,
            0x2f,
            5
        }, new byte []
        {
            value ? (byte) 1 : (byte) 0
        })), name);
    }


    private static byte [] settingSentinel (final String name, final byte [] valueHeader, final byte [] value)
    {
        final byte [] nameBytes = name.getBytes (StandardCharsets.US_ASCII);
        return ByteBuffer.allocate (1 + Integer.BYTES + nameBytes.length + valueHeader.length + value.length)
            .put ((byte) 8)
            .putInt (nameBytes.length)
            .put (nameBytes)
            .put (valueHeader)
            .put (value)
            .array ();
    }


    private static int occurrences (final byte [] bytes, final byte [] sought)
    {
        int count = 0;
        int start = 0;
        while (start <= bytes.length - sought.length)
        {
            final int found = indexOf (bytes, sought, start);
            if (found < 0)
                break;
            count++;
            start = found + 1;
        }
        return count;
    }


    private static int indexOf (final byte [] bytes, final byte [] sought, final int start)
    {
        for (int index = Math.max (0, start); index <= bytes.length - sought.length; index++)
        {
            boolean match = true;
            for (int offset = 0; offset < sought.length; offset++)
                match &= bytes[index + offset] == sought[offset];
            if (match)
                return index;
        }
        return -1;
    }
}
