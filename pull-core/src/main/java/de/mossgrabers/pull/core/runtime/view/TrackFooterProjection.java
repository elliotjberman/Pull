// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.api.CurrentTrackBankSnapshot;
import de.mossgrabers.pull.core.api.CurrentTrackSnapshot;
import de.mossgrabers.pull.core.api.SessionTrackSnapshot;
import de.mossgrabers.pull.core.api.SessionTrackType;
import de.mossgrabers.pull.core.api.output.DisplayIcon;
import de.mossgrabers.pull.core.ui.page.TrackFooterPresentation;
import java.util.ArrayList;
import java.util.List;

/** Projects the chosen authoritative bank into footer values without retaining track identities. */
final class TrackFooterProjection
{
    private TrackFooterProjection () { }

    static TrackFooterPresentation currentBank (final CurrentTrackBankSnapshot bank)
    {
        final List<TrackFooterPresentation.Cell> cells = new ArrayList<> (8);
        for (int column = 0; column < bank.tracks ().size (); column++)
        {
            final CurrentTrackSnapshot current = bank.tracks ().get (column);
            final SessionTrackSnapshot track = current.track ();
            if (!track.exists ()) continue;
            final SessionTrackType type = track.type () == SessionTrackType.GROUP && current.groupExpanded () ? SessionTrackType.GROUP_OPEN : track.type ();
            final DisplayIcon icon = track.selected () && bank.cursorPinned () ? DisplayIcon.PIN : icon (type);
            cells.add (cell (column, track, track.name ().substring (0, Math.min (12, track.name ().length ())), icon));
        }
        return new TrackFooterPresentation (cells);
    }

    static TrackFooterPresentation sessionBank (final List<SessionTrackSnapshot> tracks)
    {
        final List<TrackFooterPresentation.Cell> cells = new ArrayList<> (8);
        for (int column = 0; column < Math.min (8, tracks.size ()); column++)
        {
            final SessionTrackSnapshot track = tracks.get (column);
            if (track.exists ()) cells.add (cell (column, track, track.name (), icon (track.type ())));
        }
        return new TrackFooterPresentation (cells);
    }

    private static TrackFooterPresentation.Cell cell (final int column, final SessionTrackSnapshot track, final String name, final DisplayIcon icon)
    {
        return new TrackFooterPresentation.Cell (column, name, icon, track.color (), track.selected (), track.activated (), track.recordArmed ());
    }

    private static DisplayIcon icon (final SessionTrackType type)
    {
        return switch (type)
        {
            case AUDIO -> DisplayIcon.AUDIO_TRACK;
            case INSTRUMENT -> DisplayIcon.INSTRUMENT_TRACK;
            case HYBRID -> DisplayIcon.HYBRID_TRACK;
            case GROUP -> DisplayIcon.GROUP_TRACK;
            case GROUP_OPEN -> DisplayIcon.GROUP_TRACK_OPEN;
            case EFFECT -> DisplayIcon.RETURN_TRACK;
            case MASTER -> DisplayIcon.MASTER;
            case LAYER -> DisplayIcon.MULTI_LAYER;
            case UNKNOWN, CUE -> null;
        };
    }
}
