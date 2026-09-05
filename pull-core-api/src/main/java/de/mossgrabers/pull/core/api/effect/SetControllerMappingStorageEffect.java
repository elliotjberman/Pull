// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api.effect;

import de.mossgrabers.pull.core.api.ControllerMappingContext;
import de.mossgrabers.pull.core.api.ControllerMappingStorageSnapshot;

import java.util.Objects;


/** Compare observed document storage before submitting one replacement; later readback acknowledges it. */
public record SetControllerMappingStorageEffect (ControllerMappingContext context, String expectedValue, String value) implements CoreEffect
{
    /** Validate the bounded request without interpreting either payload. */
    public SetControllerMappingStorageEffect
    {
        context = Objects.requireNonNull (context, "context");
        expectedValue = Objects.requireNonNull (expectedValue, "expectedValue");
        value = Objects.requireNonNull (value, "value");
        if (!context.active ())
            throw new IllegalArgumentException ("controller mapping storage requires an owner");
        if (expectedValue.length () > ControllerMappingStorageSnapshot.MAX_LENGTH || value.length () > ControllerMappingStorageSnapshot.MAX_LENGTH)
            throw new IllegalArgumentException ("controller mapping storage exceeds its capacity");
    }
}
