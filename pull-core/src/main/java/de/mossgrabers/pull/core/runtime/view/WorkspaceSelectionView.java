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
import de.mossgrabers.pull.core.view.ResolvedControllerAction;
import de.mossgrabers.pull.core.view.SurfaceArea;
import de.mossgrabers.pull.core.view.SurfaceClaim;
import de.mossgrabers.pull.core.view.ViewProfile;

import java.util.List;
import java.util.Objects;
import java.util.Set;


/**
 * Core-owned entry and exit gestures for compiled workspaces.
 */
public final class WorkspaceSelectionView implements ControllerView
{
    private static final Set<BridgeSubscription> SUBSCRIPTIONS = Set.of (BridgeSubscription.CONTROLLER_LAYOUT);
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
            switched = this.selection.active () != target || !shifted && !("TRACK".equals (snapshot.bridge ().layout ().modeId ()) && "SESSION".equals (snapshot.bridge ().layout ().viewId ()));
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
            switched = this.selection.active () != target || !"TRACK".equals (snapshot.bridge ().layout ().modeId ()) || !ControllerNoteView.fromStableId (snapshot.bridge ().layout ().viewId ()).isPresent () || requestedView.isPresent () && !requestedView.name ().equals (snapshot.bridge ().layout ().viewId ());
            if (requestDrum)
                effects = List.of (new SetNoteViewPreferenceEffect (noteView.targetGeneration (), noteView.targetChannelId (), noteView.trackPosition (), requestedView));
            else
                effects = List.of ();
        }
        else
            throw new IllegalArgumentException ("Unsupported workspace action input " + input.controlId ());
        return ResolvedControllerAction.of (binding.intent (), () -> {
            if (!this.selection.beginGesture (gesture, target, destination, snapshot.bridge ().layout (), switched))
                return List.of ();
            if (!effects.isEmpty ())
                this.selection.requestPreferredNoteView (snapshot.bridge ().noteView (), ControllerNoteView.DRUM_PAD);
            return effects;
        });
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
        if (input.phase () == InputPhase.LONG)
            this.selection.makeTemporary (gesture);
        else if (input.phase () == InputPhase.END)
            this.selection.endGesture (gesture, snapshot.bridge ().layout ());
        return List.of ();
    }
}
