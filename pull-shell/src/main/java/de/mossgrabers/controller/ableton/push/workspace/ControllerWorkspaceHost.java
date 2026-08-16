// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.controller.ableton.push.workspace;

import de.mossgrabers.controller.ableton.push.controller.PushControlSurface;
import de.mossgrabers.controller.ableton.push.view.SessionView;
import de.mossgrabers.controller.ableton.push.view.WorkspaceView;
import de.mossgrabers.framework.featuregroup.ModeManager;
import de.mossgrabers.framework.featuregroup.ViewManager;
import de.mossgrabers.framework.mode.Modes;
import de.mossgrabers.framework.view.Views;
import de.mossgrabers.pull.core.api.ControllerViewFacet;
import de.mossgrabers.pull.core.api.DesiredControllerWorkspace;
import de.mossgrabers.pull.core.api.DesiredControllerLayout;
import de.mossgrabers.pull.core.api.SessionBankShape;

import java.util.Objects;


/**
 * Realizes core-selected fixed facets through the inherited Push mode/view machinery.
 */
public final class ControllerWorkspaceHost
{
    private final PushControlSurface surface;
    private final ControllerPageLease pageLease = new ControllerPageLease ();

    private DesiredControllerWorkspace desiredWorkspace = DesiredControllerWorkspace.empty ();
    private Views previousView;


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
            final SessionBankShape expectedShape = candidate.facets ().contains (ControllerViewFacet.SESSION_GRID_FULL) ? SessionView.SESSION_BANK_SHAPE : WorkspaceView.SESSION_BANK_SHAPE;
            if (!expectedShape.equals (candidate.sessionBankShape ()))
                throw new IllegalArgumentException ("Selected Session view requires bank " + expectedShape.tracks () + "x" + expectedShape.scenes ());
        }
        return candidate;
    }


    /** Validate a bounded note-controller layout without changing controller state. */
    public DesiredControllerLayout prepareLayout (final DesiredControllerLayout layout)
    {
        final DesiredControllerLayout requested = Objects.requireNonNull (layout, "layout");
        if (!requested.isPresent ())
            return requested;
        final Views view = Views.valueOf (requested.noteView ().name ());
        if (this.surface.getViewManager ().get (view) == null)
            throw new IllegalArgumentException ("Requested note view is not installed: " + requested.noteView ());
        return requested;
    }


    /** Reassert a prepared note-controller layout until layout read-back acknowledges it. */
    public void applyLayout (final DesiredControllerLayout layout)
    {
        final DesiredControllerLayout requested = this.prepareLayout (layout);
        applyPreparedLayout (requested, this.surface.getModeManager (), this.surface.getViewManager ());
    }


    static void applyPreparedLayout (final DesiredControllerLayout requested, final ModeManager modeManager, final ViewManager viewManager)
    {
        if (requested.neutralizing ())
        {
            modeManager.setActive (Modes.TRACK);
            viewManager.setActive (Views.SESSION);
            return;
        }
        if (!requested.isPresent ())
            return;
        viewManager.setActive (Views.valueOf (requested.noteView ().name ()));
    }


    static DesiredControllerWorkspace validate (final DesiredControllerWorkspace workspace)
    {
        final DesiredControllerWorkspace candidate = Objects.requireNonNull (workspace, "workspace");
        if (candidate.facets ().contains (ControllerViewFacet.SESSION_SCENE_KEYS_UPPER) && !candidate.facets ().contains (ControllerViewFacet.SESSION_CLIP_GRID_UPPER))
            throw new IllegalArgumentException ("Upper Session scene keys require the upper Session clip grid");
        if (candidate.facets ().contains (ControllerViewFacet.DRUM_PITCH_BEND) && !candidate.facets ().contains (ControllerViewFacet.DRUM_CONTROLLER_LOWER))
            throw new IllegalArgumentException ("Drum pitch bend requires the lower Drum controller");
        if (candidate.facets ().contains (ControllerViewFacet.SESSION_CLIP_GRID_UPPER) && candidate.facets ().contains (ControllerViewFacet.SESSION_GRID_FULL))
            throw new IllegalArgumentException ("Upper and full Session views cannot be active together");
        if (candidate.facets ().contains (ControllerViewFacet.SESSION_GRID_FULL) && (candidate.facets ().contains (ControllerViewFacet.DRUM_CONTROLLER_LOWER) || candidate.facets ().contains (ControllerViewFacet.SESSION_NAVIGATION) || candidate.facets ().contains (ControllerViewFacet.SESSION_SCENE_KEYS_UPPER)))
            throw new IllegalArgumentException ("Full Session cannot overlap a separately composed grid or navigation facet");
        ControllerPageLease.validate (candidate);
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
        if (next.equals (this.desiredWorkspace))
        {
            this.reconcileDesiredAdapters (next);
            return;
        }

        final DesiredControllerWorkspace previous = this.desiredWorkspace;
        final boolean hadGrid = usesGridAdapter (previous);
        final boolean wantsGrid = usesGridAdapter (next);

        this.desiredWorkspace = next;
        if (next.sessionBankShape ().isPresent ())
            this.surface.getSessionBankRegistry ().activate (next.sessionBankShape ());
        else
            this.surface.getSessionBankRegistry ().restoreDefault ();
        final ViewManager viewManager = this.surface.getViewManager ();
        final ModeManager modeManager = this.surface.getModeManager ();
        this.pageLease.apply (previous, next, modeManager);

        if (!hadGrid && wantsGrid)
            this.previousView = viewManager.getActiveID ();

        if (!wantsGrid && hadGrid)
        {
            if (viewManager.isActive (Views.WORKSPACE) && this.previousView != null)
                viewManager.setActive (this.previousView);
            this.previousView = null;
        }

        this.reconcileDesiredAdapters (next);
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
        return desiredGridView (workspace) != null;
    }


    static Views desiredGridView (final DesiredControllerWorkspace workspace)
    {
        final DesiredControllerWorkspace checked = Objects.requireNonNull (workspace, "workspace");
        if (checked.facets ().contains (ControllerViewFacet.SESSION_GRID_FULL))
            return Views.SESSION;
        if (checked.facets ().contains (ControllerViewFacet.SESSION_CLIP_GRID_UPPER) || checked.facets ().contains (ControllerViewFacet.SESSION_SCENE_KEYS_UPPER) || checked.facets ().contains (ControllerViewFacet.DRUM_CONTROLLER_LOWER))
            return Views.WORKSPACE;
        return null;
    }


    private static boolean usesWorkspaceModeAdapter (final DesiredControllerWorkspace workspace)
    {
        return workspace.facets ().contains (ControllerViewFacet.PROJECT_MACRO_CONTROLS) || workspace.facets ().contains (ControllerViewFacet.TRACK_SELECTION_STRIP);
    }


    private void reconcileDesiredAdapters (final DesiredControllerWorkspace workspace)
    {
        final ViewManager viewManager = this.surface.getViewManager ();
        final ModeManager modeManager = this.surface.getModeManager ();
        this.pageLease.reconcile (workspace, modeManager);

        final Views gridView = desiredGridView (workspace);
        if (gridView != null)
        {
            viewManager.setActive (gridView);
            if (gridView == Views.WORKSPACE)
            {
                if (!(viewManager.getActive () instanceof final WorkspaceFacetAdapter adapter))
                    throw new IllegalStateException ("Workspace grid adapter is not registered");
                adapter.reconcileWorkspaceFacets ();
            }
        }

        if (usesWorkspaceModeAdapter (workspace))
        {
            if (!(modeManager.getActive () instanceof final WorkspaceFacetAdapter adapter))
                throw new IllegalStateException ("Workspace mode adapter is not registered");
            adapter.reconcileWorkspaceFacets ();
        }
    }
}
