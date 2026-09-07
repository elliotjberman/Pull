// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.controller.ableton.push.view;

import de.mossgrabers.controller.ableton.push.controller.PushControlSurface;
import de.mossgrabers.controller.ableton.push.workspace.WorkspaceFacetAdapter;
import de.mossgrabers.framework.daw.IModel;
import de.mossgrabers.framework.daw.data.bank.ITrackBank;
import de.mossgrabers.pull.core.api.ControllerViewFacet;
import de.mossgrabers.pull.core.api.SessionBankShape;


/**
 * Structural bank and Drum indication adapter for fixed core-owned workspace facets.
 */
public final class WorkspaceView extends SessionView implements WorkspaceFacetAdapter
{
    /** Session bank shape supported by the current upper-grid adapter. */
    public static final SessionBankShape SESSION_BANK_SHAPE = new SessionBankShape (8, 4);

    private final DrumPadControls controls;


    /**
     * Constructor.
     *
     * @param surface The surface
     * @param model The model
     * @param controls The reusable lower Drum controller
     */
    public WorkspaceView (final PushControlSurface surface, final IModel model, final DrumPadControls controls)
    {
        super ("Workspace", surface, model);
        this.controls = controls;

        final ITrackBank trackBank = model.getTrackBank ();
        trackBank.addNoteObserver (this.keyManager);
        trackBank.addSelectionObserver ( (index, isSelected) -> this.keyManager.clearPressedKeys ());
    }


    /** {@inheritDoc} */
    @Override
    public void onActivate ()
    {
        this.reconcileWorkspaceFacets ();
        super.onActivate ();
    }


    /** {@inheritDoc} */
    @Override
    protected SessionBankShape getSessionBankShape ()
    {
        return this.surface.getControllerWorkspaceHost ().getSessionBankShape ();
    }


    /** {@inheritDoc} */
    @Override
    public void reconcileWorkspaceFacets ()
    {
        if (this.hasFacet (ControllerViewFacet.DRUM_CONTROLLER_LOWER))
            this.controls.activate ();
        else
            this.controls.deactivate ();
        this.updateNoteMapping ();
    }


    /** {@inheritDoc} */
    @Override
    public void onDeactivate ()
    {
        this.controls.deactivate ();
        super.onDeactivate ();
    }


    private boolean hasFacet (final ControllerViewFacet facet)
    {
        return this.surface.getControllerWorkspaceHost ().hasFacet (facet);
    }
}
