// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api;

import de.mossgrabers.pull.core.api.output.RgbColor;

import java.util.Objects;


/** One observed scene location in the active bounded Session bank. */
public record SessionSceneSnapshot (boolean exists, int position, String name, boolean selected, RgbColor color)
{
    public SessionSceneSnapshot
    {
        name = Objects.requireNonNullElse (name, "");
        color = Objects.requireNonNull (color, "color");
        if (name.length () > 128 || position < -1 || exists && position < 0)
            throw new IllegalArgumentException ("Invalid Session scene metadata");
        if (!exists && (position != -1 || !name.isEmpty () || selected))
            throw new IllegalArgumentException ("An unavailable Session scene cannot contain state");
    }


    public static SessionSceneSnapshot empty ()
    {
        return new SessionSceneSnapshot (false, -1, "", false, new RgbColor (0, 0, 0));
    }
}
