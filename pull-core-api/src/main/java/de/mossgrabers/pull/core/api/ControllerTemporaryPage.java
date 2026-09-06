// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.api;

import java.util.Objects;

/** One core-owned temporary-page transaction; the token is never a Bitwig identity. */
public record ControllerTemporaryPage (long token, ControllerPageRef page)
{
    public ControllerTemporaryPage
    {
        page = Objects.requireNonNull (page, "page");
        if (token <= 0 || !page.isPresent ()) throw new IllegalArgumentException ("Temporary page requires a positive token and page");
    }
}
