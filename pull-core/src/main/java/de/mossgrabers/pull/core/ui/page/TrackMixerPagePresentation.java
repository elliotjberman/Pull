// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.ui.page;

import de.mossgrabers.pull.core.api.MixerControlSnapshot;
import de.mossgrabers.pull.core.api.output.RgbColor;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Selected-track display data after owner alignment and subpage selection. */
public record TrackMixerPagePresentation (boolean inputOutput, boolean sendsLeft, boolean sendsRight,
    Optional<RgbColor> menuAccent, List<Metadata> metadata, List<MixerControlSnapshot> controls)
{
    public TrackMixerPagePresentation
    {
        Objects.requireNonNull (menuAccent, "menuAccent");
        metadata = List.copyOf (metadata);
        controls = List.copyOf (controls);
        if (controls.size () > PageStyle.COLUMNS || controls.stream ().map (MixerControlSnapshot::column).distinct ().count () != controls.size ())
            throw new IllegalArgumentException ("Track controls must have unique bounded columns");
        if (metadata.size () > 2 || metadata.stream ().map (Metadata::column).distinct ().count () != metadata.size ())
            throw new IllegalArgumentException ("Track metadata must have unique bounded columns");
    }

    public record Metadata (int column, String label, String value, boolean active)
    {
        public Metadata
        {
            if (column < 0 || column > 1) throw new IllegalArgumentException ("Track metadata column outside I/O region");
            Objects.requireNonNull (label, "label");
            Objects.requireNonNull (value, "value");
        }
    }
}
