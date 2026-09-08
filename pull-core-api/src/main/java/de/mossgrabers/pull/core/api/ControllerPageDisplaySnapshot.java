// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.api;

import java.util.Objects;

/** One observed mode and its bounded domain data. No input authority or drawing policy. */
public record ControllerPageDisplaySnapshot (String modeId, ControllerPageDisplayState state)
{
    private static final ControllerPageDisplaySnapshot EMPTY = new ControllerPageDisplaySnapshot ("", new ControllerPageDisplayState.Empty ());
    public ControllerPageDisplaySnapshot
    {
        modeId = Objects.requireNonNull (modeId, "modeId");
        if (modeId.length () > 64) throw new IllegalArgumentException ("modeId too long");
        state = Objects.requireNonNull (state, "state");
    }
    public static ControllerPageDisplaySnapshot empty () { return EMPTY; }
}
