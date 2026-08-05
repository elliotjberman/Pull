// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.api.ControlId;
import de.mossgrabers.pull.core.api.ControllerSnapshot;
import de.mossgrabers.pull.core.api.ControllerViewFacet;
import de.mossgrabers.pull.core.api.DesiredControllerWorkspace;
import de.mossgrabers.pull.core.api.PushControlIds;
import de.mossgrabers.pull.core.api.SessionBankShape;
import de.mossgrabers.pull.core.api.effect.CoreEffect;
import de.mossgrabers.pull.core.api.event.ControllerInputEvent;
import de.mossgrabers.pull.core.api.event.CoreEvent;
import de.mossgrabers.pull.core.api.event.InputKind;
import de.mossgrabers.pull.core.api.event.InputPhase;
import de.mossgrabers.pull.core.view.ControllerView;
import de.mossgrabers.pull.core.view.SurfaceArea;
import de.mossgrabers.pull.core.view.SurfaceClaim;
import de.mossgrabers.pull.core.view.ViewOutput;

import java.util.List;
import java.util.Map;
import java.util.Set;


/**
 * Reloadable selection policy for the first composed Push 2 workspace.
 */
public final class ControllerWorkspaceView implements ControllerView
{
    /** Core-owned first composite workspace. */
    public static final DesiredControllerWorkspace VS_LIVE = new DesiredControllerWorkspace (
        "VS Live",
        Set.of (
            ControllerViewFacet.PROJECT_MACRO_CONTROLS,
            ControllerViewFacet.TRACK_SELECTION_STRIP,
            ControllerViewFacet.SESSION_NAVIGATION,
            ControllerViewFacet.SESSION_CLIP_GRID_UPPER,
            ControllerViewFacet.SESSION_SCENE_KEYS_UPPER,
            ControllerViewFacet.DRUM_CONTROLLER_LOWER,
            ControllerViewFacet.DRUM_PITCH_BEND),
        new SessionBankShape (8, 4));

    private static final ControlId SESSION_BUTTON = PushControlIds.button ("SESSION");
    private static final ControlId NOTE_BUTTON = PushControlIds.button ("NOTE");
    private static final ControlId SHIFT_BUTTON = PushControlIds.button ("SHIFT");
    private static final Set<SurfaceClaim> CLAIMS = Set.of (
        new SurfaceClaim (SurfaceArea.SESSION_BUTTON, SurfaceClaim.Kind.OBSERVE_INPUT),
        new SurfaceClaim (SurfaceArea.NOTE_BUTTON, SurfaceClaim.Kind.OBSERVE_INPUT),
        new SurfaceClaim (SurfaceArea.SHIFT_MODIFIER, SurfaceClaim.Kind.OBSERVE_INPUT));

    private boolean active;


    /**
     * Constructor.
     *
     * @param active True when restored into VS Live
     */
    public ControllerWorkspaceView (final boolean active)
    {
        this.active = active;
    }


    /** {@inheritDoc} */
    @Override
    public String id ()
    {
        return "controller-workspace";
    }


    /** {@inheritDoc} */
    @Override
    public Set<SurfaceClaim> claims ()
    {
        return CLAIMS;
    }


    /** {@inheritDoc} */
    @Override
    public List<CoreEffect> handle (final CoreEvent event, final ControllerSnapshot snapshot)
    {
        if (event instanceof final ControllerInputEvent input && input.kind () == InputKind.BUTTON && input.phase () == InputPhase.BEGIN)
        {
            if (SESSION_BUTTON.equals (input.controlId ()))
                this.active = snapshot.pressedControls ().contains (SHIFT_BUTTON);
            else if (NOTE_BUTTON.equals (input.controlId ()))
                this.active = false;
        }
        return List.of ();
    }


    /** {@inheritDoc} */
    @Override
    public ViewOutput render (final ControllerSnapshot snapshot)
    {
        return new ViewOutput (Map.of (), Map.of (), this.active ? VS_LIVE : DesiredControllerWorkspace.empty ());
    }


    /**
     * Test whether VS Live is selected for checkpointing.
     *
     * @return True when active
     */
    public boolean isActive ()
    {
        return this.active;
    }
}
