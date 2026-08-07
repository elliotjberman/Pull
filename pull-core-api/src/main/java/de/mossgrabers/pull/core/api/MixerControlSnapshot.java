// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api;

import de.mossgrabers.pull.core.api.output.RgbColor;

import java.util.Objects;


/** Authoritative host read-back for one reusable mixer control. */
public record MixerControlSnapshot (int column, MixerControlKind kind, String label, double value, double modulatedValue, String displayedValue, boolean active, RgbColor accentColor, double vuLeft, double vuRight)
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
        accentColor = Objects.requireNonNull (accentColor, "accentColor");
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
