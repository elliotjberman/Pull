// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.api.BridgeSubscription;
import de.mossgrabers.pull.core.api.ControlId;
import de.mossgrabers.pull.core.api.ControllerActionBinding;
import de.mossgrabers.pull.core.api.ControllerActionId;
import de.mossgrabers.pull.core.api.ControllerNoteView;
import de.mossgrabers.pull.core.api.ControllerSnapshot;
import de.mossgrabers.pull.core.api.ControllerStateScope;
import de.mossgrabers.pull.core.api.DesiredControllerLayout;
import de.mossgrabers.pull.core.api.DesiredNotePerformance;
import de.mossgrabers.pull.core.api.DesiredNoteRepeat;
import de.mossgrabers.pull.core.api.NoteViewSnapshot;
import de.mossgrabers.pull.core.api.PushControlIds;
import de.mossgrabers.pull.core.api.SelectedTrackSnapshot;
import de.mossgrabers.pull.core.api.effect.CoreEffect;
import de.mossgrabers.pull.core.api.effect.SetNoteViewPreferenceEffect;
import de.mossgrabers.pull.core.api.event.ControllerInputEvent;
import de.mossgrabers.pull.core.api.event.InputKind;
import de.mossgrabers.pull.core.api.output.ControllerDisplayOverlay;
import de.mossgrabers.pull.core.api.output.ControllerDisplayScene;
import de.mossgrabers.pull.core.api.output.ControllerPadGridOverlay;
import de.mossgrabers.pull.core.view.ControllerView;
import de.mossgrabers.pull.core.view.ResolvedControllerAction;
import de.mossgrabers.pull.core.view.SurfaceArea;
import de.mossgrabers.pull.core.view.SurfaceClaim;
import de.mossgrabers.pull.core.view.ViewOutput;
import de.mossgrabers.pull.core.view.ViewProfile;

import java.util.List;
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
    private static final ControlId LAYOUT_BUTTON = PushControlIds.button ("LAYOUT");
    private static final ControlId SHIFT_BUTTON = PushControlIds.button ("SHIFT");
    private static final Set<ControllerActionBinding> ACTION_BINDINGS = Set.of (
        new ControllerActionBinding (LAYOUT_BUTTON, InputKind.BUTTON, ControllerActionId.SELECT_NOTE_LAYOUT, Set.of (ControllerStateScope.ACTIVE_PARAMETERS)));
    private static final ViewProfile PROFILE = ViewProfile.fixed (
        "policy",
        Set.of (
            new SurfaceClaim (SurfaceArea.LAYOUT_BUTTON, SurfaceClaim.Kind.EXCLUSIVE_INPUT),
            new SurfaceClaim (SurfaceArea.SHIFT_MODIFIER, SurfaceClaim.Kind.OBSERVE_INPUT)),
        Set.of ());

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
    public Set<ControllerActionBinding> actionBindings ()
    {
        return ACTION_BINDINGS;
    }


    @Override
    public ResolvedControllerAction resolveAction (final ControllerActionBinding binding, final ControllerInputEvent input, final ControllerSnapshot snapshot)
    {
        if (!LAYOUT_BUTTON.equals (input.controlId ()))
            throw new IllegalArgumentException ("Unsupported Note-layout action input " + input.controlId ());
        final ControllerNoteView active = ControllerNoteView.fromStableId (snapshot.bridge ().layout ().viewId ());
        final ControllerNoteView requested = snapshot.pressedControls ().contains (SHIFT_BUTTON) ? shiftedLayout (active) : nextLayout (active);
        final NoteViewSnapshot target = snapshot.bridge ().noteView ();
        final SelectedTrackSnapshot selected = snapshot.bridge ().selectedTrack ();
        if (!requested.isPresent () || !sameTarget (selected, target))
            return ResolvedControllerAction.of (binding.intent (), List::of);
        final CoreEffect effect = new SetNoteViewPreferenceEffect (target.targetGeneration (), target.targetChannelId (), target.trackPosition (), requested);
        return ResolvedControllerAction.of (binding.intent (), () -> {
            this.selection.selectPreferredNoteView (target, requested, snapshot.bridge ().layout ());
            return List.of (effect);
        });
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


    private static ControllerNoteView nextLayout (final ControllerNoteView active)
    {
        return switch (active)
        {
            case PLAY -> ControllerNoteView.CHORDS;
            case CHORDS -> ControllerNoteView.PIANO;
            case PIANO -> ControllerNoteView.DRUM64;
            case DRUM64 -> ControllerNoteView.PLAY;
            case SEQUENCER -> ControllerNoteView.RAINDROPS;
            case RAINDROPS -> ControllerNoteView.DRUM;
            case DRUM -> ControllerNoteView.DRUM4;
            case DRUM4 -> ControllerNoteView.DRUM8;
            case DRUM8 -> ControllerNoteView.SEQUENCER;
            default -> ControllerNoteView.NONE;
        };
    }


    private static ControllerNoteView shiftedLayout (final ControllerNoteView active)
    {
        return switch (active)
        {
            case DRUM, DRUM4, DRUM8, SEQUENCER, RAINDROPS, POLY_SEQUENCER -> ControllerNoteView.PLAY;
            default -> ControllerNoteView.SEQUENCER;
        };
    }


    private static boolean sameTarget (final SelectedTrackSnapshot selected, final NoteViewSnapshot noteView)
    {
        return selected.exists () && selected.canHoldNotes () && selected.generation () == noteView.targetGeneration () && selected.channelId ().equals (noteView.targetChannelId ()) && selected.position () == noteView.trackPosition ();
    }


}
