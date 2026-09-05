// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api.effect;

import de.mossgrabers.pull.core.api.ApplicationUiContext;
import java.util.Objects;

/** One native application UI request with an exact observed origin. */
public record ToggleApplicationPanelEffect (ApplicationUiContext context, Panel panel) implements CoreEffect
{
    public enum Panel { NOTE_EDITOR, AUTOMATION_EDITOR, DEVICES, MIXER, INSPECTOR, FULLSCREEN }

    public ToggleApplicationPanelEffect
    {
        context = Objects.requireNonNull (context, "context");
        panel = Objects.requireNonNull (panel, "panel");
    }
}
