// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.api.EncoderConfigurationSnapshot;
import de.mossgrabers.pull.core.api.ParameterSlot;
import de.mossgrabers.pull.core.api.ParameterTargetSnapshot;
import de.mossgrabers.pull.core.api.effect.AdjustParameterValueEffect;
import de.mossgrabers.pull.core.api.effect.CoreEffect;
import de.mossgrabers.pull.core.api.effect.SetParameterNormalizedValueEffect;

import java.util.List;

/** Preserved normal Track encoder response; input decoding and effect execution remain generic. */
final class TrackEncoderResponse
{
    private TrackEncoderResponse () { }

    static List<CoreEffect> adjust (final ParameterSlot slot, final ParameterTargetSnapshot target, final double ticks, final boolean fine, final EncoderConfigurationSnapshot configuration)
    {
        if (!configuration.available ())
            return List.of ();
        final int sensitivity = fine ? configuration.fineSensitivity () : configuration.normalSensitivity ();
        final double scale = sensitivity < 0 ? Math.max (0.1, (100.0 + sensitivity) / 100.0) : 1 + sensitivity / 100.0 * 9;
        final double delta = ticks * configuration.baseStep () * scale;
        final double range = configuration.valueUpperBound () - 1.0;
        final double normalized = Math.min (target.value () / range, 1);
        if (slot.equals (ParameterSlot.SELECTED_TRACK_VOLUME))
            return List.of (new AdjustParameterValueEffect (target.target (), delta * (1 + 0.2 * normalized)));
        if (!slot.equals (ParameterSlot.SELECTED_TRACK_PAN))
            return List.of (new AdjustParameterValueEffect (target.target (), delta));
        final double increment = delta * 0.5;
        if (increment == 0)
            return List.of ();
        if (Math.abs (normalized - 0.5) > 1 / range)
        {
            final double next = Math.max (0, Math.min (1, normalized + increment / range));
            final boolean towards = increment > 0 && normalized < 0.5 || increment < 0 && normalized > 0.5;
            final boolean crosses = normalized < 0.5 && next >= 0.5 || normalized > 0.5 && next <= 0.5;
            if (towards && (crosses || Math.abs (next - 0.5) <= 0.015))
                return List.of (new SetParameterNormalizedValueEffect (target.target (), 0.5));
        }
        return List.of (new AdjustParameterValueEffect (target.target (), increment));
    }
}
