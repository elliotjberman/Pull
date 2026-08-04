// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.shell.runtime;

import de.mossgrabers.pull.core.api.effect.ClipLaunchMode;
import de.mossgrabers.pull.core.api.effect.ClipLaunchQuantization;

import java.util.Objects;

/**
 * Maps the stable launch-policy vocabulary to Bitwig controller API 21 option strings.
 */
final class BitwigClipLaunchMapper
{
    private BitwigClipLaunchMapper ()
    {
        // Utility class
    }


    static String quantization (final ClipLaunchQuantization quantization)
    {
        return switch (Objects.requireNonNull (quantization, "quantization"))
        {
            case DEFAULT -> "default";
            case IMMEDIATE -> "none";
            case EIGHT_BARS -> "8";
            case FOUR_BARS -> "4";
            case TWO_BARS -> "2";
            case ONE_BAR -> "1";
            case HALF_NOTE -> "1/2";
            case QUARTER_NOTE -> "1/4";
            case EIGHTH_NOTE -> "1/8";
            case SIXTEENTH_NOTE -> "1/16";
        };
    }


    static String mode (final ClipLaunchMode mode)
    {
        return switch (Objects.requireNonNull (mode, "mode"))
        {
            case DEFAULT -> "default";
            case TRIGGER_FROM_START -> "from_start";
            case LEGATO_FROM_CLIP_OR_START -> "continue_or_from_start";
            case LEGATO_FROM_CLIP_OR_PROJECT -> "continue_or_synced";
            case LEGATO_FROM_PROJECT -> "synced";
        };
    }
}
