// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.ui.page;

import de.mossgrabers.pull.core.api.MixerControlSnapshot;
import de.mossgrabers.pull.core.ui.component.ChoiceCell;
import java.util.List;

/** Fully resolved display policy. It deliberately carries no input routes or light output. */
public record DevicePagePresentation (List<ChoiceCell> upper, List<ChoiceCell> lower,
                                      TrackFooterPresentation footer, List<MixerControlSnapshot> controls,
                                      String heading, String message, List<ToggleCell> toggles)
{
    public DevicePagePresentation
    {
        upper = List.copyOf (upper); lower = List.copyOf (lower);
        controls = List.copyOf (controls); toggles = List.copyOf (toggles);
    }
    public record ToggleCell (int column, boolean on) { }
}
