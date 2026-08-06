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
    public static final int GLOBAL_BANK_SIZE = 2;
    /** Maximum parameter targets exposed in one snapshot. */
    public static final int INSTALLED_TARGET_CAPACITY = (ParameterBankId.BANK_CAPACITY - 1) * BANK_SIZE + GLOBAL_BANK_SIZE;
    /** Maximum exact targets one physical eight-knob interaction can retain, including globals. */
    public static final int INTERACTION_TARGET_CAPACITY = 10;

    /** Fixed tempo target. */
    public static final ParameterSlot TEMPO = new ParameterSlot (ParameterBankId.GLOBAL, 0);

    /** Fixed master-volume target. */
    public static final ParameterSlot MASTER_VOLUME = new ParameterSlot (ParameterBankId.GLOBAL, 1);


    /**
     * Validate the slot.
     */
    public ParameterSlot
    {
        bank = Objects.requireNonNull (bank, "bank");
        final int capacity = bank == ParameterBankId.GLOBAL ? GLOBAL_BANK_SIZE : BANK_SIZE;
        if (index < 0 || index >= capacity)
            throw new IllegalArgumentException ("parameter slot index is outside the installed bank capacity");
    }


    /**
     * Get one active parameter-bank slot.
     *
     * @param index Zero-based slot index
     * @return Slot
     */
    public static ParameterSlot active (final int index)
    {
        return new ParameterSlot (ParameterBankId.ACTIVE, index);
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
}
