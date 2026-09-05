// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api;


/** Authoritative target presence and normalized value from one semantic hardware control. */
public record ControllerMappingTarget (boolean hasTarget, double value)
{
    /** Preserve the host value independently of target presence, without interpreting its meaning. */
    public ControllerMappingTarget
    {
        if (!Double.isFinite (value) || value < 0 || value > 1)
            throw new IllegalArgumentException ("controller mapping target value must be between 0 and 1");
    }
}
