// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.api.ControlId;
import de.mossgrabers.pull.core.api.ControllerSnapshot;
import de.mossgrabers.pull.core.api.PushControlIds;
import de.mossgrabers.pull.core.api.effect.CoreEffect;
import de.mossgrabers.pull.core.api.event.ControllerInputEvent;
import de.mossgrabers.pull.core.api.event.CoreEvent;
import de.mossgrabers.pull.core.api.event.InputKind;
import de.mossgrabers.pull.core.api.event.InputPhase;
import de.mossgrabers.pull.core.view.ControllerView;
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
    private static final ControlId SESSION_BUTTON = PushControlIds.button ("SESSION");
    private static final ControlId NOTE_BUTTON = PushControlIds.button ("NOTE");
    private static final ControlId SHIFT_BUTTON = PushControlIds.button ("SHIFT");
    private static final ViewProfile PROFILE = ViewProfile.fixed (
        "session-note",
        Set.of (
            new SurfaceClaim (SurfaceArea.SESSION_BUTTON, SurfaceClaim.Kind.OBSERVE_INPUT),
            new SurfaceClaim (SurfaceArea.NOTE_BUTTON, SurfaceClaim.Kind.OBSERVE_INPUT),
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
    public List<CoreEffect> handle (final CoreEvent event, final ControllerSnapshot snapshot)
    {
        if (!(event instanceof final ControllerInputEvent input) || input.kind () != InputKind.BUTTON || input.phase () != InputPhase.BEGIN)
            return List.of ();

        if (SESSION_BUTTON.equals (input.controlId ()))
        {
            this.selection.select (snapshot.pressedControls ().contains (SHIFT_BUTTON) ? WorkspaceSelection.Id.VS_LIVE : WorkspaceSelection.Id.DEFAULT);
        }
        else if (NOTE_BUTTON.equals (input.controlId ()))
            this.selection.select (WorkspaceSelection.Id.DEFAULT);
        return List.of ();
    }
}
