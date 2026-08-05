// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api;

import java.util.Objects;


/**
 * One bounded parameter position exposed by the stable shell, independent of physical controls.
 *
 * @param domain Slot domain
 * @param index Zero-based index within the domain
 */
public record ParameterSlot (Domain domain, int index)
{
    /** Number of active parameter slots aligned with the Push top encoders. */
    public static final int ACTIVE_SLOT_COUNT = 8;

    /** Fixed tempo target. */
    public static final ParameterSlot TEMPO = new ParameterSlot (Domain.TEMPO, 0);

    /** Fixed master-volume target. */
    public static final ParameterSlot MASTER_VOLUME = new ParameterSlot (Domain.MASTER_VOLUME, 0);


    /**
     * Validate the slot.
     */
    public ParameterSlot
    {
        domain = Objects.requireNonNull (domain, "domain");
        final int upperBound = domain == Domain.ACTIVE ? ACTIVE_SLOT_COUNT : 1;
        if (index < 0 || index >= upperBound)
            throw new IllegalArgumentException ("parameter slot index is outside the installed domain capacity");
    }


    /**
     * Get one active parameter-bank slot.
     *
     * @param index Zero-based slot index
     * @return Slot
     */
    public static ParameterSlot active (final int index)
    {
        return new ParameterSlot (Domain.ACTIVE, index);
    }


    /** Stable parameter domains installed at extension startup. */
    public enum Domain
    {
        /** Current eight-slot parameter bank selected by the active controller view. */
        ACTIVE,
        /** Global transport tempo. */
        TEMPO,
        /** Master-track volume while the master encoder is in volume mode. */
        MASTER_VOLUME
    }
}
