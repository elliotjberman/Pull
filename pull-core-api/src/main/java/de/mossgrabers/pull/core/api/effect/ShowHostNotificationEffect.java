// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api.effect;

import java.util.Objects;


/** Ask the DAW to present core-authored text using its native notification UI. */
public record ShowHostNotificationEffect (String text) implements CoreEffect
{
    public ShowHostNotificationEffect
    {
        text = Objects.requireNonNull (text, "text");
        if (text.isBlank () || text.length () > 256)
            throw new IllegalArgumentException ("Host notifications require 1..256 characters");
    }
}
