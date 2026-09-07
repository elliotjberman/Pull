// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.api.*;
import de.mossgrabers.pull.core.view.InputTarget;
import java.util.List;

/** Shared exact contexts for rows whose release modifiers select current-bank or Session actions. */
final class TrackInputTargets
{
    private TrackInputTargets () { }

    static InputTarget row (final ControlId control, final int index, final ControllerSnapshot snapshot)
    {
        final CurrentTrackBankSnapshot current = snapshot.bridge ().currentTrackBank ();
        final String channel = index < current.tracks ().size () ? current.tracks ().get (index).track ().channelId () : "";
        final SessionBankSnapshot session = snapshot.bridge ().sessionBank ();
        final String sessionChannel = index < session.tracks ().size () ? session.tracks ().get (index).channelId () : "";
        return new InputTarget.Composite (List.of (
            new InputTarget.Context (control, "current-track-bank", current.bankId (), current.generation ()),
            new InputTarget.Context (control, "current-track-slot", channel, current.generation ()),
            new InputTarget.Context (control, "current-track-parent", current.cursorChannelId (), current.parentGeneration ()),
            new InputTarget.SessionBank (control, session.generation (), session.shape ()),
            new InputTarget.Context (control, "session-track-slot", sessionChannel, session.generation ())));
    }

    static InputTarget sessionRow (final ControlId control, final int index, final ControllerSnapshot snapshot)
    {
        final SessionBankSnapshot bank = snapshot.bridge ().sessionBank ();
        final String channel = index < bank.tracks ().size () ? bank.tracks ().get (index).channelId () : "";
        return new InputTarget.Composite (List.of (
            new InputTarget.SessionBank (control, bank.generation (), bank.shape ()),
            new InputTarget.Context (control, "session-track-slot", channel, bank.generation ())));
    }

    static InputTarget stop (final ControlId control, final ControllerSnapshot snapshot)
    {
        final SelectedTrackSnapshot selected = snapshot.bridge ().selectedTrack ();
        final SessionBankSnapshot session = snapshot.bridge ().sessionBank ();
        return new InputTarget.Composite (List.of (
            new InputTarget.Context (control, "selected-track", selected.channelId (), selected.generation ()),
            new InputTarget.SessionBank (control, session.generation (), session.shape ())));
    }
}
