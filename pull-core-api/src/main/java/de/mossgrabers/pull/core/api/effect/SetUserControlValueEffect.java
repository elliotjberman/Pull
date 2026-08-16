// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api.effect;

import de.mossgrabers.pull.core.api.UserControlBankSnapshot;


/** Request an absolute value for one fixed Bitwig user-control slot. */
public record SetUserControlValueEffect (int slot, double normalizedValue) implements CoreEffect
{
    /** Validate the bounded request. */
    public SetUserControlValueEffect
    {
        if (slot < 0 || slot >= UserControlBankSnapshot.CAPACITY)
            throw new IllegalArgumentException ("user-control slot is outside the installed bank");
        if (!Double.isFinite (normalizedValue) || normalizedValue < 0 || normalizedValue > 1)
            throw new IllegalArgumentException ("normalizedValue must be finite and between 0 and 1");
    }
}
