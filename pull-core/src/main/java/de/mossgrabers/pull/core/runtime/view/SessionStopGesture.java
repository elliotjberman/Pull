// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.runtime.view;


/** Shared Stop-Clip gesture state for separately composed Session controls. */
public final class SessionStopGesture
{
    private boolean consumed;


    /** Begin one new physical Stop gesture. */
    void begin ()
    {
        this.consumed = false;
    }


    /** Consume the trailing plain Stop action. */
    void consume ()
    {
        this.consumed = true;
    }


    /** Take and clear the consumption decision at physical release. */
    boolean takeConsumed ()
    {
        final boolean result = this.consumed;
        this.consumed = false;
        return result;
    }
}
