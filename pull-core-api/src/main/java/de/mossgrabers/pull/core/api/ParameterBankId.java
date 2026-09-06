// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api;


/** Fixed parameter banks installed by the stable shell. */
public enum ParameterBankId
{
    /** Compatibility window currently bound by an inherited stable mode. */
    ACTIVE,
    /** Selected-track volume and pan, independent of controller bindings. */
    SELECTED_TRACK,
    /** Eight sends of the private-selection-aligned track. */
    SELECTED_TRACK_SENDS,
    /** Eight project remote controls. */
    PROJECT_REMOTE,
    /** Current page of eight selected-device remote controls. */
    SELECTED_DEVICE_REMOTE,
    /** Eight visible-track volume parameters. */
    TRACK_VOLUME,
    /** Eight visible-track pan parameters. */
    TRACK_PAN,
    /** Send column one through eight across the eight current-bank tracks. */
    TRACK_SEND1, TRACK_SEND2, TRACK_SEND3, TRACK_SEND4,
    TRACK_SEND5, TRACK_SEND6, TRACK_SEND7, TRACK_SEND8,
    /** Tempo, master-volume, and metronome-volume parameters. */
    GLOBAL,
    /** Master volume, master pan, cue volume, and cue mix. */
    MASTER;

    /** Number of installed bank identities. */
    public static final int BANK_CAPACITY = values ().length;

    /** Resolve one zero-based send column in the installed eight-by-eight current-track matrix. */
    public static ParameterBankId trackSend (final int sendIndex)
    {
        if (sendIndex < 0 || sendIndex >= 8)
            throw new IllegalArgumentException ("send column must be in 0..7");
        return values ()[TRACK_SEND1.ordinal () + sendIndex];
    }
}
