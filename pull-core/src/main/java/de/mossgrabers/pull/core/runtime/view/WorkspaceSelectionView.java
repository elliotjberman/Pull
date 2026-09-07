// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.api.BridgeSubscription;
import de.mossgrabers.pull.core.api.ControlId;
import de.mossgrabers.pull.core.api.ControllerActionBinding;
import de.mossgrabers.pull.core.api.ControllerActionId;
import de.mossgrabers.pull.core.api.ControllerSnapshot;
import de.mossgrabers.pull.core.api.ControllerStateScope;
import de.mossgrabers.pull.core.api.ControllerNoteView;
import de.mossgrabers.pull.core.api.NoteViewSnapshot;
import de.mossgrabers.pull.core.api.PushControlIds;
import de.mossgrabers.pull.core.api.effect.CoreEffect;
import de.mossgrabers.pull.core.api.effect.SetNoteViewPreferenceEffect;
import de.mossgrabers.pull.core.api.event.ControllerInputEvent;
import de.mossgrabers.pull.core.api.event.CoreEvent;
import de.mossgrabers.pull.core.api.event.InputKind;
import de.mossgrabers.pull.core.api.event.InputPhase;
import de.mossgrabers.pull.core.view.ControllerView;
import de.mossgrabers.pull.core.view.InputTarget;
import de.mossgrabers.pull.core.view.ResolvedControllerAction;
import de.mossgrabers.pull.core.view.SurfaceArea;
import de.mossgrabers.pull.core.view.SurfaceClaim;
import de.mossgrabers.pull.core.view.ViewProfile;

import java.util.List;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;


/**
 * Core-owned entry and exit gestures for compiled workspaces.
 */
public final class WorkspaceSelectionView implements ControllerView
{
    private static final Set<BridgeSubscription> SUBSCRIPTIONS = Set.of (BridgeSubscription.CONTROLLER_LAYOUT, BridgeSubscription.SELECTED_TRACK, BridgeSubscription.NOTE_VIEW);
    private static final ControlId SESSION_BUTTON = PushControlIds.button ("SESSION");
    private static final ControlId NOTE_BUTTON = PushControlIds.button ("NOTE");
    private static final ControlId SHIFT_BUTTON = PushControlIds.button ("SHIFT");
    private static final Set<ControllerActionBinding> ACTION_BINDINGS = Set.of (
        new ControllerActionBinding (SESSION_BUTTON, InputKind.BUTTON, ControllerActionId.SWITCH_WORKSPACE, Set.of (ControllerStateScope.ACTIVE_PARAMETERS)),
        new ControllerActionBinding (NOTE_BUTTON, InputKind.BUTTON, ControllerActionId.SWITCH_WORKSPACE, Set.of (ControllerStateScope.ACTIVE_PARAMETERS)));
    private static final ViewProfile PROFILE = ViewProfile.fixed (
        "session-note",
        Set.of (
            new SurfaceClaim (SurfaceArea.SESSION_BUTTON, SurfaceClaim.Kind.EXCLUSIVE_INPUT),
            new SurfaceClaim (SurfaceArea.NOTE_BUTTON, SurfaceClaim.Kind.EXCLUSIVE_INPUT),
            new SurfaceClaim (SurfaceArea.SHIFT_MODIFIER, SurfaceClaim.Kind.OBSERVE_INPUT)),
        Set.of ());

    private final WorkspaceSelection selection;
    private final Map<WorkspaceSelection.Gesture, SelectionRequest> held = new EnumMap<> (WorkspaceSelection.Gesture.class);


    /**
     * Constructor.
     *
     * @param selection Shared workspace selection
     */
    public WorkspaceSelectionView (final WorkspaceSelection selection)
    {
        this.selection = Objects.requireNonNull (selection, "selection");
    }


    /** {@inheritDoc} */
    @Override
    public String id ()
    {
        return "workspace-selection";
    }


    /** {@inheritDoc} */
    @Override
    public ViewProfile profile ()
    {
        return PROFILE;
    }


    /** {@inheritDoc} */
    @Override
    public Set<ControllerActionBinding> actionBindings ()
    {
        return ACTION_BINDINGS;
    }


    /** {@inheritDoc} */
    @Override
    public Set<BridgeSubscription> bridgeSubscriptions ()
    {
        return SUBSCRIPTIONS;
    }


    @Override
    public InputTarget inputTarget (final ControlId control, final InputKind kind, final ControllerSnapshot snapshot)
    {
        if (!NOTE_BUTTON.equals (control)) return ControllerView.super.inputTarget (control, kind, snapshot);
        final var selected = snapshot.bridge ().selectedTrack ();
        final var note = snapshot.bridge ().noteView ();
        return new InputTarget.Note (control, selected.generation (), selected.channelId (), note.targetGeneration (),
            note.targetChannelId (), note.trackPosition (), note.drumControllerApplicable ());
    }

    @Override
    public List<CoreEffect> cancel (final ControlId control, final InputKind kind, final InputTarget target, final ControllerSnapshot snapshot)
    {
        if (!NOTE_BUTTON.equals (control) && !SESSION_BUTTON.equals (control)) return List.of ();
        final SelectionRequest request = this.held.get (NOTE_BUTTON.equals (control) ? WorkspaceSelection.Gesture.NOTE : WorkspaceSelection.Gesture.SESSION);
        return request == null ? List.of () : this.cancelRequest (request);
    }

    /** {@inheritDoc} */
    @Override
    public ResolvedControllerAction resolveAction (final ControllerActionBinding binding, final ControllerInputEvent input, final ControllerSnapshot snapshot)
    {
        final WorkspaceSelection.Id target;
        final WorkspaceSelection.Destination destination;
        final WorkspaceSelection.Gesture gesture;
        final boolean switched;
        final List<CoreEffect> effects;
        if (SESSION_BUTTON.equals (input.controlId ()))
        {
            final boolean shifted = snapshot.pressedControls ().contains (SHIFT_BUTTON);
            target = shifted ? WorkspaceSelection.Id.VS_LIVE : WorkspaceSelection.Id.DEFAULT;
            destination = shifted ? WorkspaceSelection.Destination.NONE : WorkspaceSelection.Destination.SESSION;
            gesture = WorkspaceSelection.Gesture.SESSION;
            switched = this.selection.active () != target || !shifted && !"SESSION".equals (snapshot.bridge ().layout ().viewId ());
            effects = List.of ();
        }
        else if (NOTE_BUTTON.equals (input.controlId ()))
        {
            target = WorkspaceSelection.Id.DEFAULT;
            destination = WorkspaceSelection.Destination.NOTE;
            gesture = WorkspaceSelection.Gesture.NOTE;
            final boolean shifted = snapshot.pressedControls ().contains (SHIFT_BUTTON);
            final NoteViewSnapshot noteView = snapshot.bridge ().noteView ();
            final boolean requestDrum = shifted && noteView.drumControllerApplicable () && !noteView.targetChannelId ().isBlank () && noteView.trackPosition () >= 0;
            final ControllerNoteView requestedView = requestDrum ? ControllerNoteView.DRUM_PAD : ControllerNoteView.NONE;
            switched = this.selection.active () != target || !ControllerNoteView.fromStableId (snapshot.bridge ().layout ().viewId ()).isPresent () || requestedView.isPresent () && !requestedView.name ().equals (snapshot.bridge ().layout ().viewId ());
            if (requestDrum)
                effects = List.of (new SetNoteViewPreferenceEffect (noteView.targetGeneration (), noteView.targetChannelId (), noteView.trackPosition (), requestedView));
            else
                effects = List.of ();
        }
        else
            throw new IllegalArgumentException ("Unsupported workspace action input " + input.controlId ());
        final SelectionRequest request = new SelectionRequest (gesture);
        this.held.put (gesture, request);
        return ResolvedControllerAction.of (binding.intent (), () -> {
            if (!this.selection.beginGesture (gesture, target, destination, snapshot.bridge ().layout (), switched))
                return List.of ();
            request.admitted = true;
            if (!effects.isEmpty ())
                this.selection.requestPreferredNoteView (snapshot.bridge ().noteView (), ControllerNoteView.DRUM_PAD);
            if (request.temporary) this.selection.makeTemporary (gesture);
            if (request.ended) this.selection.endGesture (gesture, snapshot.bridge ().layout ());
            return effects;
        }).onCancellation (() -> this.cancelRequest (request));
    }


    @Override
    public List<CoreEffect> handle (final CoreEvent event, final ControllerSnapshot snapshot)
    {
        if (!(event instanceof final ControllerInputEvent input) || input.kind () != InputKind.BUTTON)
            return List.of ();
        final WorkspaceSelection.Gesture gesture;
        if (NOTE_BUTTON.equals (input.controlId ()))
            gesture = WorkspaceSelection.Gesture.NOTE;
        else if (SESSION_BUTTON.equals (input.controlId ()))
            gesture = WorkspaceSelection.Gesture.SESSION;
        else
            return List.of ();
        final SelectionRequest request = this.held.get (gesture);
        if (request == null) return List.of ();
        if (input.phase () == InputPhase.LONG)
        {
            request.temporary = true;
            if (request.admitted) this.selection.makeTemporary (gesture);
        }
        else if (input.phase () == InputPhase.END)
        {
            request.ended = true;
            this.held.remove (gesture);
            if (request.admitted) this.selection.endGesture (gesture, snapshot.bridge ().layout ());
        }
        return List.of ();
    }

    private List<CoreEffect> cancelRequest (final SelectionRequest request)
    {
        this.held.remove (request.gesture, request);
        if (request.admitted && !request.ended) this.selection.cancelGesture (request.gesture);
        return List.of ();
    }

    /** The temporary/latched meaning of a resolved selection survives delayed action admission. */
    private static final class SelectionRequest
    {
        private final WorkspaceSelection.Gesture gesture;
        private boolean admitted;
        private boolean temporary;
        private boolean ended;
        private SelectionRequest (final WorkspaceSelection.Gesture gesture) { this.gesture = gesture; }
    }
}
