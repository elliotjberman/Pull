// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.controller.ableton.push.view;

import de.mossgrabers.controller.ableton.push.controller.PushControlSurface;
import de.mossgrabers.controller.ableton.push.workspace.WorkspaceFacetAdapter;
import de.mossgrabers.framework.controller.ButtonID;
import de.mossgrabers.framework.controller.grid.IPadGrid;
import de.mossgrabers.framework.daw.IModel;
import de.mossgrabers.framework.daw.data.bank.ITrackBank;
import de.mossgrabers.framework.featuregroup.AbstractFeatureGroup;
import de.mossgrabers.framework.utils.ButtonEvent;
import de.mossgrabers.pull.core.api.ControllerViewFacet;
import de.mossgrabers.pull.core.api.SessionBankShape;


/**
 * Stable grid adapter which realizes fixed Session and Drum workspace facets.
 */
public final class WorkspaceView extends SessionView implements WorkspaceFacetAdapter
{
    /** Session bank shape supported by the current upper-grid adapter. */
    public static final SessionBankShape SESSION_BANK_SHAPE = new SessionBankShape (8, 4);

    private static final int SESSION_ROWS = SESSION_BANK_SHAPE.scenes ();

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
        super ("Workspace", surface, model, SESSION_ROWS, 0);
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


    /** {@inheritDoc} */
    @Override
    public void onGridNote (final int note, final int velocity)
    {
        if (this.hasFacet (ControllerViewFacet.DRUM_CONTROLLER_LOWER) && this.controls.ownsGridNote (note))
        {
            this.controls.onGridNote (note, velocity);
            return;
        }
        if (this.hasFacet (ControllerViewFacet.SESSION_CLIP_GRID_UPPER))
            super.onGridNote (note, velocity);
    }


    /** {@inheritDoc} */
    @Override
    public void onGridPressure (final int note, final int value)
    {
        // The reloadable DrumPressureView owns pressure policy for this composite adapter.
    }


    /** {@inheritDoc} */
    @Override
    public void drawGrid ()
    {
        final IPadGrid padGrid = this.surface.getPadGrid ();
        for (int y = 0; y < padGrid.getRows (); y++)
        {
            for (int x = 0; x < padGrid.getCols (); x++)
                padGrid.lightEx (x, y, IPadGrid.GRID_OFF);
        }

        if (this.hasFacet (ControllerViewFacet.SESSION_CLIP_GRID_UPPER))
            super.drawGrid ();
        if (this.hasFacet (ControllerViewFacet.DRUM_CONTROLLER_LOWER))
            this.controls.drawOwnedPads (padGrid);
    }


    /** {@inheritDoc} */
    @Override
    public void updateNoteMapping ()
    {
        final boolean drumActive = this.hasFacet (ControllerViewFacet.DRUM_CONTROLLER_LOWER) && this.surface.isDrumControllerActive ();
        this.delayedUpdateNoteMapping (drumActive ? this.scales.getDrumMatrix () : EMPTY_TABLE);
    }


    /** {@inheritDoc} */
    @Override
    public void onButton (final ButtonID buttonID, final ButtonEvent event, final int velocity)
    {
        final int sceneIndex = buttonID.ordinal () - ButtonID.SCENE1.ordinal ();
        if (this.hasFacet (ControllerViewFacet.SESSION_SCENE_KEYS_UPPER) && sceneIndex >= 0 && sceneIndex < SESSION_ROWS)
            super.onButton (buttonID, event, velocity);
    }


    /** {@inheritDoc} */
    @Override
    public String getButtonColorID (final ButtonID buttonID)
    {
        final int sceneIndex = buttonID.ordinal () - ButtonID.SCENE1.ordinal ();
        if (!this.hasFacet (ControllerViewFacet.SESSION_SCENE_KEYS_UPPER) || sceneIndex < 0 || sceneIndex >= SESSION_ROWS)
            return AbstractFeatureGroup.BUTTON_COLOR_OFF;
        return super.getButtonColorID (buttonID);
    }


    /** {@inheritDoc} */
    @Override
    public void onOctaveDown (final ButtonEvent event)
    {
        this.changeDrumOctave (event, false);
    }


    /** {@inheritDoc} */
    @Override
    public void onOctaveUp (final ButtonEvent event)
    {
        this.changeDrumOctave (event, true);
    }


    /** {@inheritDoc} */
    @Override
    public boolean isOctaveUpButtonOn ()
    {
        return this.surface.isDrumControllerActive () && this.scales.canScrollDrumOctaveUp ();
    }


    /** {@inheritDoc} */
    @Override
    public boolean isOctaveDownButtonOn ()
    {
        return this.surface.isDrumControllerActive () && this.scales.canScrollDrumOctaveDown ();
    }


    /** {@inheritDoc} */
    @Override
    public void resetOctave ()
    {
        if (!this.surface.isDrumControllerActive ())
            return;

        final int previousOffset = this.scales.getDrumOffset ();
        this.keyManager.clearPressedKeys ();
        this.scales.resetDrumOctave ();
        this.updateNoteMapping ();
        this.model.getDrumDevice ().getDrumPadBank ().scrollTo (this.scales.getDrumOffset (), true);
        if (previousOffset != this.scales.getDrumOffset ())
            this.controls.onDrumOffsetChanged ();
    }


    private void changeDrumOctave (final ButtonEvent event, final boolean up)
    {
        if (event != ButtonEvent.DOWN || !this.surface.isDrumControllerActive ())
            return;

        final int previousOffset = this.scales.getDrumOffset ();
        final int offset = this.surface.isShiftPressed () ? 4 : this.scales.getDrumDefaultOffset ();
        this.keyManager.clearPressedKeys ();
        if (up)
            this.scales.incDrumOffset (offset);
        else
            this.scales.decDrumOffset (offset);
        this.updateNoteMapping ();
        this.surface.getDisplay ().notify (this.scales.getDrumRangeText ());
        this.model.getDrumDevice ().getDrumPadBank ().scrollTo (this.scales.getDrumOffset (), false);
        if (previousOffset != this.scales.getDrumOffset ())
            this.controls.onDrumOffsetChanged ();
    }


    private boolean hasFacet (final ControllerViewFacet facet)
    {
        return this.surface.getControllerWorkspaceHost ().hasFacet (facet);
    }
}
