// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.shell.runtime;

import de.mossgrabers.framework.controller.valuechanger.IValueChanger;
import de.mossgrabers.framework.daw.data.IChannel;

import java.util.Objects;


/** One authoritative stereo meter read-back shared by Master and Mix surfaces. */
public record MixerMeterLevels (int left, int right)
{
    /** Validate captured values. */
    public MixerMeterLevels
    {
        if (left < 0 || right < 0)
            throw new IllegalArgumentException ("Meter values must not be negative");
    }


    /** Capture both channels from the same host-backed channel sample. */
    public static MixerMeterLevels capture (final IChannel channel)
    {
        final IChannel checkedChannel = Objects.requireNonNull (channel, "channel");
        return new MixerMeterLevels (checkedChannel.getVuLeft (), checkedChannel.getVuRight ());
    }


    /** Normalize the left channel for core-owned mixer rendering. */
    public double normalizedLeft (final IValueChanger valueChanger)
    {
        return Objects.requireNonNull (valueChanger, "valueChanger").toNormalizedValue (this.left);
    }


    /** Normalize the right channel for core-owned mixer rendering. */
    public double normalizedRight (final IValueChanger valueChanger)
    {
        return Objects.requireNonNull (valueChanger, "valueChanger").toNormalizedValue (this.right);
    }
}
