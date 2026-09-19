// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api;

import java.util.Objects;


/**
 * One bounded parameter position exposed by the stable shell, independent of physical controls.
 *
 * @param bank Installed bank
 * @param index Zero-based index within the bank
 */
public record ParameterSlot (ParameterBankId bank, int index)
{
    /** Number of parameter slots in each installed bank. */
    public static final int BANK_SIZE = 8;
    /** Number of fixed global parameter slots. */
    public static final int GLOBAL_BANK_SIZE = 3;
    /** Maximum parameter targets exposed in one snapshot. */
    public static final int INSTALLED_TARGET_CAPACITY = java.util.Arrays.stream (ParameterBankId.values ()).mapToInt (ParameterSlot::capacity).sum ();

    /** Maximum selected note cells in an installed edit window. */
    public static final int NOTE_CAPACITY = 128;

    public static int capacity (final ParameterBankId bank)
    {
        if (bank == ParameterBankId.NOTE) return NOTE_CAPACITY * NoteParameterRole.values ().length;
        if (bank == ParameterBankId.GLOBAL) return GLOBAL_BANK_SIZE;
        return bank.isLayer () && bank != ParameterBankId.SELECTED_LAYER && bank != ParameterBankId.SELECTED_LAYER_SENDS ? 16 : BANK_SIZE;
    }
    /** Maximum exact targets one physical eight-knob interaction can retain, including globals. */
    public static final int INTERACTION_TARGET_CAPACITY = NOTE_CAPACITY * BANK_SIZE + 2;

    /** Selected-track volume. */
    public static final ParameterSlot SELECTED_TRACK_VOLUME = new ParameterSlot (ParameterBankId.SELECTED_TRACK, 0);
    /** Selected-track pan. */
    public static final ParameterSlot SELECTED_TRACK_PAN = new ParameterSlot (ParameterBankId.SELECTED_TRACK, 1);

    /** Get one of the eight selected-track sends. */
    public static ParameterSlot selectedTrackSend (final int index)
    {
        return new ParameterSlot (ParameterBankId.SELECTED_TRACK_SENDS, index);
    }


    /** Fixed tempo target. */
    public static final ParameterSlot TEMPO = new ParameterSlot (ParameterBankId.GLOBAL, 0);

    /** Fixed master-volume target. */
    public static final ParameterSlot MASTER_VOLUME = new ParameterSlot (ParameterBankId.GLOBAL, 1);

    /** Fixed current-project metronome volume. */
    public static final ParameterSlot METRONOME_VOLUME = new ParameterSlot (ParameterBankId.GLOBAL, 2);

    /** Master-mode master-volume target. */
    public static final ParameterSlot MASTER_MIX_VOLUME = new ParameterSlot (ParameterBankId.MASTER, 0);
    /** Master-mode master-pan target. */
    public static final ParameterSlot MASTER_MIX_PAN = new ParameterSlot (ParameterBankId.MASTER, 1);
    /** Master-mode cue-volume target. */
    public static final ParameterSlot CUE_VOLUME = new ParameterSlot (ParameterBankId.MASTER, 2);
    /** Master-mode cue-mix target. */
    public static final ParameterSlot CUE_MIX = new ParameterSlot (ParameterBankId.MASTER, 3);


    /**
     * Validate the slot.
     */
    public ParameterSlot
    {
        bank = Objects.requireNonNull (bank, "bank");
        final int capacity = capacity (bank);
        if (index < 0 || index >= capacity)
            throw new IllegalArgumentException ("parameter slot index is outside the installed bank capacity");
    }


    /** Get one project remote-control slot. */
    public static ParameterSlot projectRemote (final int index)
    {
        return new ParameterSlot (ParameterBankId.PROJECT_REMOTE, index);
    }


    /** Get one selected-device remote-control slot. */
    public static ParameterSlot selectedDeviceRemote (final int index)
    {
        return new ParameterSlot (ParameterBankId.SELECTED_DEVICE_REMOTE, index);
    }


    /** Get one visible-track volume slot. */
    public static ParameterSlot trackVolume (final int index)
    {
        return new ParameterSlot (ParameterBankId.TRACK_VOLUME, index);
    }


    /** Get one visible-track pan slot. */
    public static ParameterSlot trackPan (final int index)
    {
        return new ParameterSlot (ParameterBankId.TRACK_PAN, index);
    }


    /** Get one send column's slot for a current-bank track, with both indices zero-based. */
    public static ParameterSlot trackSend (final int sendIndex, final int trackIndex)
    {
        return new ParameterSlot (ParameterBankId.trackSend (sendIndex), trackIndex);
    }
}
