// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api;

/** Observed native panel options, independent of the controller page. */
public record ArrangerUiSnapshot (boolean clipLauncherVisible, boolean ioSectionVisible, boolean cueMarkersVisible, boolean timelineVisible, boolean effectTracksVisible, boolean playbackFollowEnabled, boolean doubleRowTrackHeight)
{
    public static ArrangerUiSnapshot empty ()
    {
        return new ArrangerUiSnapshot (false, false, false, false, false, false, false);
    }
}
