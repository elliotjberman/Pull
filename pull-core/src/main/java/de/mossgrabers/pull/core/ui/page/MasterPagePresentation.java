// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.ui.page;

import de.mossgrabers.pull.core.api.MixerControlSnapshot;
import de.mossgrabers.pull.core.api.output.DisplayIcon;
import de.mossgrabers.pull.core.api.output.RgbColor;
import java.util.List;
import java.util.Objects;

/** Master and project display values; project identity and parameter actuators remain in the view. */
public record MasterPagePresentation (List<MixerControlSnapshot> controls, TrackFooter track,
    boolean engineActive, String projectName, boolean canPrevious, boolean canNext, boolean projectDirty)
{
    public MasterPagePresentation
    {
        controls = List.copyOf (controls);
        if (controls.size () > 4 || controls.stream ().anyMatch (control -> control.column () > 3) || controls.stream ().map (MixerControlSnapshot::column).distinct ().count () != controls.size ())
            throw new IllegalArgumentException ("Master controls must have unique columns between zero and three");
        Objects.requireNonNull (track, "track");
        Objects.requireNonNull (projectName, "projectName");
    }

    public record TrackFooter (String name, DisplayIcon icon, RgbColor color, boolean selected, boolean active)
    {
        public TrackFooter
        {
            Objects.requireNonNull (name, "name");
            Objects.requireNonNull (color, "color");
        }
    }
}
