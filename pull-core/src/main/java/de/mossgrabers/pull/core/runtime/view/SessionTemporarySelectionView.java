// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.api.ControllerSnapshot;
import de.mossgrabers.pull.core.api.effect.CoreEffect;
import de.mossgrabers.pull.core.api.event.ControllerInputEvent;
import de.mossgrabers.pull.core.api.event.CoreEvent;
import de.mossgrabers.pull.core.api.event.InputPhase;
import de.mossgrabers.pull.core.view.ControllerView;
import de.mossgrabers.pull.core.view.SurfaceArea;
import de.mossgrabers.pull.core.view.SurfaceClaim;
import de.mossgrabers.pull.core.view.ViewProfile;

import java.util.List;
import java.util.Objects;
import java.util.Set;


/** Preserves Push's temporary Session gesture after the stable Session command is made inert. */
final class SessionTemporarySelectionView implements ControllerView
{
    private static final ViewProfile PROFILE = ViewProfile.fixed (
        "temporary-session",
        Set.of (
            new SurfaceClaim (SurfaceArea.GRID_UPPER_PAD_EDGES, SurfaceClaim.Kind.OBSERVE_INPUT),
            new SurfaceClaim (SurfaceArea.GRID_LOWER_PAD_EDGES, SurfaceClaim.Kind.OBSERVE_INPUT),
            new SurfaceClaim (SurfaceArea.SCENE_KEYS_UPPER, SurfaceClaim.Kind.OBSERVE_INPUT),
            new SurfaceClaim (SurfaceArea.SCENE_KEYS_LOWER, SurfaceClaim.Kind.OBSERVE_INPUT)),
        Set.of ());

    private final WorkspaceSelection selection;


    SessionTemporarySelectionView (final WorkspaceSelection selection)
    {
        this.selection = Objects.requireNonNull (selection, "selection");
    }


    @Override
    public String id ()
    {
        return "temporary-session-selection";
    }


    @Override
    public ViewProfile profile ()
    {
        return PROFILE;
    }


    @Override
    public List<CoreEffect> handle (final CoreEvent event, final ControllerSnapshot snapshot)
    {
        if (event instanceof final ControllerInputEvent input && input.phase () == InputPhase.END)
            this.selection.makeTemporary (WorkspaceSelection.Gesture.SESSION);
        return List.of ();
    }
}
