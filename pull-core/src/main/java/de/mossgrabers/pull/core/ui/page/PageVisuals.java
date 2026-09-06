// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.ui.page;

import de.mossgrabers.pull.core.api.ControlId;
import de.mossgrabers.pull.core.api.output.ControllerDisplayScene;
import de.mossgrabers.pull.core.api.output.RgbColor;
import java.util.Map;
import java.util.Objects;

/** Pure page output; renderers cannot contribute effects, touches, or musical routing. */
public record PageVisuals (Map<ControlId, RgbColor> lights, ControllerDisplayScene display)
{
    public PageVisuals
    {
        lights = Map.copyOf (Objects.requireNonNull (lights, "lights"));
        display = Objects.requireNonNull (display, "display");
    }
}
