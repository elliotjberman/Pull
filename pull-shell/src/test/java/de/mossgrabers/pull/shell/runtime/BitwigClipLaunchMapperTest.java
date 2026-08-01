// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.shell.runtime;

import de.mossgrabers.pull.core.api.effect.ClipLaunchMode;
import de.mossgrabers.pull.core.api.effect.ClipLaunchQuantization;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Exact Bitwig API 21 string-mapping tests for stable launch policies.
 */
class BitwigClipLaunchMapperTest
{
    @Test
    void mapsEveryQuantizationToAnApi21Value ()
    {
        assertEquals (
            List.of ("default", "none", "8", "4", "2", "1", "1/2", "1/4", "1/8", "1/16"),
            List.of (ClipLaunchQuantization.values ()).stream ().map (BitwigClipLaunchMapper::quantization).toList ());
    }


    @Test
    void mapsEveryLaunchModeToAnApi21Value ()
    {
        assertEquals (
            List.of ("default", "from_start", "continue_or_from_start", "continue_or_synced", "synced"),
            List.of (ClipLaunchMode.values ()).stream ().map (BitwigClipLaunchMapper::mode).toList ());
    }
}
