// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.api;

/** Observed Session preferences. Clip length is resolved against the current time signature. */
public record SessionSettingsSnapshot (boolean available, boolean selectOnLaunch, int actionForArmedPad, int newClipLengthBeats, boolean drawRecordStripe)
{
    public SessionSettingsSnapshot
    {
        if (actionForArmedPad < 0 || actionForArmedPad > 2 || newClipLengthBeats < 0 || available && newClipLengthBeats == 0)
            throw new IllegalArgumentException ("Invalid Session settings");
    }
    public static SessionSettingsSnapshot empty () { return new SessionSettingsSnapshot (false, false, 2, 0, false); }
}
