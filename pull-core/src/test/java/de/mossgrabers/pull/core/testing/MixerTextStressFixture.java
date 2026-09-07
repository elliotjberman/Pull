// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.testing;

import de.mossgrabers.pull.core.api.MixerControlKind;
import de.mossgrabers.pull.core.api.MixerControlRole;
import de.mossgrabers.pull.core.api.MixerControlSnapshot;
import de.mossgrabers.pull.core.api.MixerControlsSnapshot;
import de.mossgrabers.pull.core.api.output.RgbColor;
import java.util.List;
import java.util.Optional;

/** The same observed values drive the provider regression and the shared visual catalog. */
final class MixerTextStressFixture
{
    static final RgbColor ACCENT = new RgbColor (10, 80, 140);

    private MixerTextStressFixture () { }

    static MixerControlsSnapshot snapshot ()
    {
        return new MixerControlsSnapshot (List.of (
            new MixerControlSnapshot (0, MixerControlKind.VOLUME, "", 0.5, -1, "+3.0 dB", MixerControlRole.HOST_COLORED, true, false, Optional.of (ACCENT), 0.25, 0.5),
            new MixerControlSnapshot (1, MixerControlKind.PAN, "", 0.75, -1, "23 R", MixerControlRole.HOST_COLORED, true, false, Optional.of (ACCENT), 0, 0),
            macro (2, "Very Long Project Macro Name", 0.6, "-123.456 dB"),
            macro (3, "Positive Decimal Decibels", 0.4, "+123.456 dB"),
            macro (4, "Very Long Frequency Parameter", 0.7, "+12345.678 kHz"),
            macro (5, "Very Long Millisecond Value", 0.3, "-9876.543 ms"),
            macro (6, "Fine Tune Hundredths", 0.8, "+100.000 ct"),
            macro (7, "Boolean Macro With Long Name", 1, "On")));
    }

    private static MixerControlSnapshot macro (final int column, final String label, final double value, final String displayedValue)
    {
        return new MixerControlSnapshot (column, MixerControlKind.KNOB, label, value, -1, displayedValue, MixerControlRole.PROJECT_MACRO, true, true, Optional.empty (), 0, 0);
    }
}
