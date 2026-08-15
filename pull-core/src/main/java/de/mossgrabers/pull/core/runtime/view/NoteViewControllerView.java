// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.api.BridgeSubscription;
import de.mossgrabers.pull.core.api.ControllerNoteView;
import de.mossgrabers.pull.core.api.ControllerSnapshot;
import de.mossgrabers.pull.core.api.DesiredControllerLayout;
import de.mossgrabers.pull.core.api.DesiredNotePerformance;
import de.mossgrabers.pull.core.api.DesiredNoteRepeat;
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
        final ResolvedNoteViewer resolved = ResolvedNoteViewer.resolve (
            snapshot,
            this.selection.selectedDestination () == WorkspaceSelection.Destination.NOTE,
            this.selection.preferredNoteView (snapshot.bridge ().noteView ()));
        if (!resolved.layout ().isPresent ())
            return ViewOutput.empty ();
        return new ViewOutput (
            Map.of (),
            Map.of (),
            ControllerDisplayScene.empty (),
            ControllerPadGridOverlay.inactive (),
            ControllerDisplayOverlay.inactive (),
            new DesiredNotePerformance (DesiredControllerLayout.note (resolved.layout ()), resolved.noteInputRoute ()),
            DesiredNoteRepeat.unowned ());
    }


}
