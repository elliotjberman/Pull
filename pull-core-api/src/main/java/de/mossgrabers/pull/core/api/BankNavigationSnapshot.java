// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api;


/** Observed extent and navigation availability of a bounded bank. */
public record BankNavigationSnapshot (int itemCount, boolean previousItem, boolean nextItem, boolean previousPage, boolean nextPage)
{
    public BankNavigationSnapshot
    {
        if (itemCount < 0)
            throw new IllegalArgumentException ("itemCount must not be negative");
    }


    public static BankNavigationSnapshot empty ()
    {
        return new BankNavigationSnapshot (0, false, false, false, false);
    }
}
