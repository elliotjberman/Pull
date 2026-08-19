// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api;

import de.mossgrabers.pull.core.api.output.RgbColor;

import java.util.Objects;
import java.util.Optional;


/** Authoritative host read-back for one reusable mixer control. */
public record MixerControlSnapshot (int column, MixerControlKind kind, String label, double value, double modulatedValue, String displayedValue, MixerControlRole role, boolean enabled, boolean touched, Optional<RgbColor> hostAccentColor, double vuLeft, double vuRight)
{
    /** Validate one bounded control snapshot. */
    public MixerControlSnapshot
    {
        if (column < 0 || column >= ParameterSlot.BANK_SIZE)
            throw new IllegalArgumentException ("column must be between 0 and 7");
        kind = Objects.requireNonNull (kind, "kind");
        label = boundedText (label, "label", 128);
        if (kind == MixerControlKind.KNOB && label.isBlank ())
            throw new IllegalArgumentException ("knob label must not be blank");
        displayedValue = boundedText (displayedValue, "displayedValue", 128);
        requireNormalized (value, "value");
        if (modulatedValue != -1)
            requireNormalized (modulatedValue, "modulatedValue");
        role = Objects.requireNonNull (role, "role");
        hostAccentColor = Objects.requireNonNull (hostAccentColor, "hostAccentColor");
        if (role == MixerControlRole.HOST_COLORED && hostAccentColor.isEmpty ())
            throw new IllegalArgumentException ("host-colored controls require an accent color");
        if (role == MixerControlRole.PROJECT_MACRO && hostAccentColor.isPresent ())
            throw new IllegalArgumentException ("project macros must leave visual color policy to the core");
        requireNormalized (vuLeft, "vuLeft");
        requireNormalized (vuRight, "vuRight");
    }


    private static String boundedText (final String text, final String name, final int maximumLength)
    {
        final String value = Objects.requireNonNullElse (text, "");
        if (value.length () > maximumLength)
            throw new IllegalArgumentException (name + " is too long");
        return value;
    }


    private static void requireNormalized (final double value, final String name)
    {
        if (!Double.isFinite (value) || value < 0 || value > 1)
            throw new IllegalArgumentException (name + " must be finite and between 0 and 1");
    }
}
