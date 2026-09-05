// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api;

import java.util.Map;
import java.util.Objects;


/** Complete replayable exact-parameter touch ownership, bounded to eight physical controls. */
public record DesiredParameterTouches (Map<ControlId, ParameterTargetRef> targets)
{
    public static final int CAPACITY = 8;

    private static final DesiredParameterTouches EMPTY = new DesiredParameterTouches (Map.of ());


    public DesiredParameterTouches
    {
        targets = Map.copyOf (Objects.requireNonNull (targets, "targets"));
        if (targets.size () > CAPACITY)
            throw new IllegalArgumentException ("parameter touches exceed the installed capacity");
    }


    public static DesiredParameterTouches empty ()
    {
        return EMPTY;
    }
}
