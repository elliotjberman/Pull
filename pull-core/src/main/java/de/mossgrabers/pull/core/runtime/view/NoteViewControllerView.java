// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.api.BridgeSubscription;
import de.mossgrabers.pull.core.api.ControllerNoteView;
import de.mossgrabers.pull.core.api.ControllerSnapshot;
import de.mossgrabers.pull.core.api.DesiredControllerLayout;
import de.mossgrabers.pull.core.api.DesiredNoteRepeat;
import de.mossgrabers.pull.core.api.NoteViewSnapshot;
import de.mossgrabers.pull.core.api.SelectedTrackSnapshot;
import de.mossgrabers.pull.core.api.output.ControllerDisplayOverlay;
import de.mossgrabers.pull.core.api.output.ControllerDisplayScene;
import de.mossgrabers.pull.core.api.output.ControllerPadGridOverlay;
import de.mossgrabers.pull.core.view.ControllerView;
import de.mossgrabers.pull.core.view.ViewOutput;
import de.mossgrabers.pull.core.view.ViewProfile;

import java.util.Map;
import java.util.Objects;
import java.util.Set;


/** Core-owned per-track selection of the installed stable note-controller view. */
public final class NoteViewControllerView implements ControllerView
{
    private static final Set<BridgeSubscription> SUBSCRIPTIONS = Set.of (
        BridgeSubscription.SELECTED_TRACK,
        BridgeSubscription.CONTROLLER_LAYOUT,
        BridgeSubscription.NOTE_VIEW);
    private static final ViewProfile PROFILE = ViewProfile.fixed ("policy", Set.of (), Set.of ());

    private final WorkspaceSelection selection;


    /** Construct the policy around shared Note-destination state. */
    public NoteViewControllerView (final WorkspaceSelection selection)
    {
        this.selection = Objects.requireNonNull (selection, "selection");
    }


    @Override
    public String id ()
    {
        return "note-view-controller";
    }


    @Override
    public ViewProfile profile ()
    {
        return PROFILE;
    }


    @Override
    public Set<BridgeSubscription> bridgeSubscriptions ()
    {
        return SUBSCRIPTIONS;
    }


    @Override
    public ViewOutput render (final ControllerSnapshot snapshot)
    {
        final ControllerNoteView desired = desiredView (snapshot, this.selection.pendingDestination () == WorkspaceSelection.Destination.NOTE);
        if (!desired.isPresent ())
            return ViewOutput.empty ();
        return new ViewOutput (
            Map.of (),
            Map.of (),
            ControllerDisplayScene.empty (),
            ControllerPadGridOverlay.inactive (),
            ControllerDisplayOverlay.inactive (),
            DesiredControllerLayout.note (desired),
            DesiredNoteRepeat.unowned ());
    }


    static ControllerNoteView desiredView (final ControllerSnapshot snapshot, final boolean noteDestinationPending)
    {
        final ControllerNoteView active = ControllerNoteView.fromStableId (snapshot.bridge ().layout ().viewId ());
        if (!noteDestinationPending && (!active.isPresent () || !"TRACK".equals (snapshot.bridge ().layout ().modeId ())))
            return ControllerNoteView.NONE;

        final SelectedTrackSnapshot selected = snapshot.bridge ().selectedTrack ();
        final NoteViewSnapshot preference = snapshot.bridge ().noteView ();
        if (!selected.exists () || !sameTarget (selected, preference))
            return ControllerNoteView.NONE;

        final boolean drumReady = preference.drumControllerApplicable ();
        final ControllerNoteView preferred = preference.preferredView ();
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
}
