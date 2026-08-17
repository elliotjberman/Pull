// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api.effect;

import de.mossgrabers.pull.core.api.ControlId;

import java.util.Objects;


/** Request mechanical consumption of a held stable button's later release callback. */
public record ConsumeControllerButtonEffect (ControlId controlId) implements CoreEffect
{
    /** Validate the physical button identity. */
    public ConsumeControllerButtonEffect
    {
        controlId = Objects.requireNonNull (controlId, "controlId");
    }
}
