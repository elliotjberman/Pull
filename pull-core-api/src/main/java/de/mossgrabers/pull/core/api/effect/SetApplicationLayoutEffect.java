// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api.effect;

import de.mossgrabers.pull.core.api.ApplicationUiContext;
import java.util.Objects;

/** One native application UI request with an exact observed origin. */
public record SetApplicationLayoutEffect (ApplicationUiContext context, Layout layout) implements CoreEffect
{
    public enum Layout { ARRANGE, MIX, EDIT, PLAY }

    public SetApplicationLayoutEffect
    {
        context = Objects.requireNonNull (context, "context");
        layout = Objects.requireNonNull (layout, "layout");
    }
}
