// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api;

/** Observed native panel options, independent of the controller page. */
public record MixerUiSnapshot (boolean clipLauncherVisible, boolean ioSectionVisible, boolean crossFadeVisible, boolean deviceSectionVisible, boolean meterSectionVisible, boolean sendSectionVisible)
{
    public static MixerUiSnapshot empty ()
    {
        return new MixerUiSnapshot (false, false, false, false, false, false);
    }
}
