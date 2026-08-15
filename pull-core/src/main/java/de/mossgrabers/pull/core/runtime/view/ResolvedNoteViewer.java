// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.api.ControllerNoteView;
import de.mossgrabers.pull.core.api.ControllerSnapshot;
import de.mossgrabers.pull.core.api.DesiredNoteInputRoute;
import de.mossgrabers.pull.core.api.NoteViewSnapshot;
import de.mossgrabers.pull.core.api.SelectedTrackSnapshot;

import java.util.Objects;


/** One target-fenced note viewer and the controller mechanisms attached to it. */
record ResolvedNoteViewer (ControllerNoteView layout, DesiredNoteInputRoute noteInputRoute, boolean automaticRollAttached)
{
    /** Resolve the musical lease contributed by a composite lower-half Drum controller. */
    static ResolvedNoteViewer resolveCompositeDrum (final ControllerSnapshot snapshot)
    {
        final SelectedTrackSnapshot selected = snapshot.bridge ().selectedTrack ();
        final NoteViewSnapshot preference = snapshot.bridge ().noteView ();
        if (!selected.exists () || !sameTarget (selected, preference) || !selected.canHoldNotes () || !preference.drumControllerApplicable ())
            return new ResolvedNoteViewer (ControllerNoteView.NONE, DesiredNoteInputRoute.disabled (), false);

        final DesiredNoteInputRoute route = DesiredNoteInputRoute.selectedTrack (selected.generation (), selected.channelId ());
        return new ResolvedNoteViewer (ControllerNoteView.NONE, route, automaticRollAttached (snapshot, preference, ControllerNoteView.NONE));
    }


    static ResolvedNoteViewer resolve (final ControllerSnapshot snapshot, final boolean noteDestinationPending)
    {
        return resolve (snapshot, noteDestinationPending, snapshot.bridge ().noteView ().preferredView ());
    }


    static ResolvedNoteViewer resolve (final ControllerSnapshot snapshot, final boolean noteDestinationPending, final ControllerNoteView preferredView)
    {
        final SelectedTrackSnapshot selected = snapshot.bridge ().selectedTrack ();
        final NoteViewSnapshot preference = snapshot.bridge ().noteView ();
        if (!selected.exists () || !sameTarget (selected, preference))
            return new ResolvedNoteViewer (ControllerNoteView.NONE, DesiredNoteInputRoute.disabled (), false);

        final boolean alignedDrumTarget = preference.drumControllerApplicable ();
        final ControllerNoteView active = ControllerNoteView.fromStableId (snapshot.bridge ().layout ().viewId ());
        final boolean noteViewerVisible = noteDestinationPending || active.isPresent ();
        final ControllerNoteView layout = noteViewerVisible ? resolveLayout (selected, Objects.requireNonNull (preferredView, "preferredView"), alignedDrumTarget) : ControllerNoteView.NONE;
        final DesiredNoteInputRoute noteInputRoute = layout.isPresent () && selected.canHoldNotes () ? DesiredNoteInputRoute.selectedTrack (selected.generation (), selected.channelId ()) : DesiredNoteInputRoute.disabled ();
        return new ResolvedNoteViewer (layout, noteInputRoute, automaticRollAttached (snapshot, preference, layout));
    }


    private static ControllerNoteView resolveLayout (final SelectedTrackSnapshot selected, final ControllerNoteView preferred, final boolean drumReady)
    {
        if (preferred == ControllerNoteView.DRUM_PAD && selected.canHoldNotes () && drumReady)
            return preferred;
        if (preferred == ControllerNoteView.CLIP_LENGTH && selected.canHoldAudio ())
            return preferred;
        if (preferred.isPresent () && preferred != ControllerNoteView.DRUM_PAD && preferred != ControllerNoteView.CLIP_LENGTH && selected.canHoldNotes ())
            return preferred;
        if (selected.canHoldNotes ())
            return drumReady ? ControllerNoteView.DRUM_PAD : ControllerNoteView.PLAY;
        return selected.canHoldAudio () ? ControllerNoteView.CLIP_LENGTH : ControllerNoteView.NONE;
    }


    private static boolean sameTarget (final SelectedTrackSnapshot selected, final NoteViewSnapshot preference)
    {
        return selected.generation () == preference.targetGeneration () && selected.channelId ().equals (preference.targetChannelId ()) && selected.position () == preference.trackPosition ();
    }


    private static boolean automaticRollAttached (final ControllerSnapshot snapshot, final NoteViewSnapshot preference, final ControllerNoteView layout)
    {
        return preference.drumControllerApplicable () && snapshot.bridge ().layout ().drumLayoutActive () && snapshot.bridge ().layout ().drumControllerEngaged () && (!layout.isPresent () || layout == ControllerNoteView.DRUM_PAD);
    }
}
