// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.api.ParameterSlot;
import de.mossgrabers.pull.core.api.SessionTrackSnapshot;
import de.mossgrabers.pull.core.api.output.ControllerDisplayScene;
import de.mossgrabers.pull.core.api.output.DisplayCommand;
import de.mossgrabers.pull.core.api.output.RgbColor;

import java.util.ArrayList;
import java.util.List;


/** Core-owned footer region for the bounded VS Live track-selection strip. */
final class TrackSelectionDisplayScene
{
    static final int WIDTH = 960;
    static final int HEIGHT = 17;

    private static final RgbColor BLACK = new RgbColor (0, 0, 0);


    private TrackSelectionDisplayScene ()
    {
        // Utility class.
    }


    /** Render up to eight authoritative visible tracks. */
    static ControllerDisplayScene render (final List<SessionTrackSnapshot> tracks)
    {
        final ArrayList<DisplayCommand> commands = new ArrayList<> (24);
        commands.add (new DisplayCommand.Rectangle (0, 0, WIDTH, HEIGHT, BLACK));
        for (int index = 0; index < Math.min (ParameterSlot.BANK_SIZE, tracks.size ()); index++)
        {
            final SessionTrackSnapshot track = tracks.get (index);
            if (!track.exists ())
                continue;
            TrackFooterDisplayScene.append (commands, index, 0, track);
        }
        return new ControllerDisplayScene (WIDTH, HEIGHT, commands);
    }
}
