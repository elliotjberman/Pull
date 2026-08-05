// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.controller.ableton.push.workspace;

import de.mossgrabers.controller.ableton.push.controller.PushControlSurface;
import de.mossgrabers.controller.ableton.push.view.WorkspaceView;
import de.mossgrabers.framework.featuregroup.ModeManager;
import de.mossgrabers.framework.featuregroup.ViewManager;
import de.mossgrabers.framework.mode.Modes;
import de.mossgrabers.framework.view.Views;
import de.mossgrabers.pull.core.api.ControllerViewFacet;
import de.mossgrabers.pull.core.api.DesiredControllerWorkspace;
import de.mossgrabers.pull.core.api.SessionBankShape;

import java.util.Objects;


/**
 * Realizes core-selected fixed facets through the inherited Push mode/view machinery.
 */
public final class ControllerWorkspaceHost
{
    private final PushControlSurface surface;

    private DesiredControllerWorkspace desiredWorkspace = DesiredControllerWorkspace.empty ();
    private Views previousView;
    private Modes previousMode;


    /**
     * Constructor.
     *
     * @param surface The stable Push surface
     */
    public ControllerWorkspaceHost (final PushControlSurface surface)
    {
        this.surface = Objects.requireNonNull (surface, "surface");
    }


    /**
     * Validate a complete desired workspace without changing controller state.
     *
     * @param workspace The requested workspace
     * @return The validated immutable value
     */
    public DesiredControllerWorkspace prepare (final DesiredControllerWorkspace workspace)
    {
        final DesiredControllerWorkspace candidate = validate (workspace);
        if (candidate.sessionBankShape ().isPresent ())
        {
            this.surface.getSessionBankRegistry ().requireDeclared (candidate.sessionBankShape ());
            if (!WorkspaceView.SESSION_BANK_SHAPE.equals (candidate.sessionBankShape ()))
                throw new IllegalArgumentException ("Workspace grid adapter requires Session bank " + WorkspaceView.SESSION_BANK_SHAPE.tracks () + "x" + WorkspaceView.SESSION_BANK_SHAPE.scenes ());
        }
        return candidate;
    }


    static DesiredControllerWorkspace validate (final DesiredControllerWorkspace workspace)
    {
        final DesiredControllerWorkspace candidate = Objects.requireNonNull (workspace, "workspace");
        if (candidate.facets ().contains (ControllerViewFacet.SESSION_SCENE_KEYS_UPPER) && !candidate.facets ().contains (ControllerViewFacet.SESSION_CLIP_GRID_UPPER))
            throw new IllegalArgumentException ("Upper Session scene keys require the upper Session clip grid");
        if (candidate.facets ().contains (ControllerViewFacet.DRUM_PITCH_BEND) && !candidate.facets ().contains (ControllerViewFacet.DRUM_CONTROLLER_LOWER))
            throw new IllegalArgumentException ("Drum pitch bend requires the lower Drum controller");
        return candidate;
    }


    /**
     * Apply a validated complete desired workspace.
     *
     * @param workspace The workspace
     */
    public void apply (final DesiredControllerWorkspace workspace)
    {
        final DesiredControllerWorkspace next = this.prepare (workspace);
        final boolean hadGrid = usesGridAdapter (this.desiredWorkspace);
        final boolean hadMode = usesModeAdapter (this.desiredWorkspace);
        final boolean wantsGrid = usesGridAdapter (next);
        final boolean wantsMode = usesModeAdapter (next);

        this.desiredWorkspace = next;
        if (next.sessionBankShape ().isPresent ())
            this.surface.getSessionBankRegistry ().activate (next.sessionBankShape ());
        else
            this.surface.getSessionBankRegistry ().restoreDefault ();
        final ViewManager viewManager = this.surface.getViewManager ();
        final ModeManager modeManager = this.surface.getModeManager ();

        if (!hadGrid && wantsGrid)
            this.previousView = viewManager.getActiveID ();
        if (!hadMode && wantsMode)
            this.previousMode = modeManager.getActiveID ();

        if (wantsGrid)
        {
            viewManager.setActive (Views.WORKSPACE);
            if (!(viewManager.getActive () instanceof final WorkspaceFacetAdapter adapter))
                throw new IllegalStateException ("Workspace grid adapter is not registered");
            adapter.reconcileWorkspaceFacets ();
        }
        else if (hadGrid)
        {
            if (viewManager.isActive (Views.WORKSPACE) && this.previousView != null)
                viewManager.setActive (this.previousView);
            this.previousView = null;
        }

        if (wantsMode)
        {
            modeManager.setActive (Modes.WORKSPACE);
            if (!(modeManager.getActive () instanceof final WorkspaceFacetAdapter adapter))
                throw new IllegalStateException ("Workspace mode adapter is not registered");
            adapter.reconcileWorkspaceFacets ();
        }
        else if (hadMode)
        {
            if (modeManager.isActive (Modes.WORKSPACE) && this.previousMode != null)
                modeManager.setActive (this.previousMode);
            this.previousMode = null;
        }
    }


    /**
     * Restore all stable-owned areas during core invalidation.
     */
    public void invalidate ()
    {
        this.apply (DesiredControllerWorkspace.empty ());
    }


    /**
     * Test whether a fixed facet is active.
     *
     * @param facet The facet
     * @return True when selected by the core
     */
    public boolean hasFacet (final ControllerViewFacet facet)
    {
        return this.desiredWorkspace.facets ().contains (Objects.requireNonNull (facet, "facet"));
    }


    /**
     * Test whether any core-owned workspace is active.
     *
     * @return True when active
     */
    public boolean isActive ()
    {
        return this.desiredWorkspace.isActive ();
    }


    /**
     * Get the Session bank requested by the active core workspace.
     *
     * @return Declared Session bank shape
     */
    public SessionBankShape getSessionBankShape ()
    {
        return this.desiredWorkspace.sessionBankShape ();
    }


    private static boolean usesGridAdapter (final DesiredControllerWorkspace workspace)
    {
        return workspace.facets ().contains (ControllerViewFacet.SESSION_CLIP_GRID_UPPER) || workspace.facets ().contains (ControllerViewFacet.SESSION_SCENE_KEYS_UPPER) || workspace.facets ().contains (ControllerViewFacet.DRUM_CONTROLLER_LOWER);
    }


    private static boolean usesModeAdapter (final DesiredControllerWorkspace workspace)
    {
        return workspace.facets ().contains (ControllerViewFacet.PROJECT_MACRO_CONTROLS) || workspace.facets ().contains (ControllerViewFacet.TRACK_SELECTION_STRIP);
    }
}
