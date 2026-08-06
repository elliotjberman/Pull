// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api;

import java.util.Objects;
import java.util.Set;


/** Complete replayable selection of installed parameter banks available when parameters are subscribed. */
public record DesiredParameterBanks (Set<ParameterBankId> banks)
{
    private static final DesiredParameterBanks EMPTY = new DesiredParameterBanks (Set.of ());


    /** Validate and copy the bounded bank set. */
    public DesiredParameterBanks
    {
        banks = Set.copyOf (Objects.requireNonNull (banks, "banks"));
    }


    /** Test whether one installed bank is requested. */
    public boolean includes (final ParameterBankId bank)
    {
        return this.banks.contains (Objects.requireNonNull (bank, "bank"));
    }


    /** Get the empty selection. */
    public static DesiredParameterBanks empty ()
    {
        return EMPTY;
    }
}
