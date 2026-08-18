// (c) 2017-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.controller.ableton.push.view;

import java.util.Arrays;

import de.mossgrabers.controller.ableton.push.PushConfiguration;
import de.mossgrabers.controller.ableton.push.controller.PushControlSurface;
import de.mossgrabers.framework.daw.IModel;
import de.mossgrabers.framework.daw.data.IDrumDevice;
import de.mossgrabers.framework.featuregroup.IView;
import de.mossgrabers.framework.scale.Scales;


/**
 * Stable lifecycle and note-mapping adapter for the reloadable Drum controller.
 */
public final class DrumPadControls
{
    private static final int LOWER_GRID_ROWS = 4;

    private final PushControlSurface surface;
    private final PushConfiguration configuration;
    private final IModel             model;
    private final Scales             scales;
    private boolean                  active;
    private boolean                  controllerEngaged;
    private IDrumDevice              engagedDrumDevice;


    /**
     * Constructor.
     *
     * @param surface The Push surface
     * @param model The model
     */
    public DrumPadControls (final PushControlSurface surface, final IModel model)
    {
        this.surface = surface;
        this.configuration = surface.getConfiguration ();
        this.model = model;
        this.scales = model.getScales ();
    }


    /**
     * Activate the performance controls.
     */
    public void activate ()
    {
        if (this.active)
            return;

        this.active = true;
        this.reconcileControllerState ();
    }


    /**
     * Deactivate the performance controls and restore the repeat and velocity state they
     * temporarily override.
     */
    public void deactivate ()
    {
        if (!this.active)
            return;

        this.active = false;
        this.reconcileControllerState ();
    }


    /**
     * Test whether the controls are active in their current host view.
     *
     * @return True if active
     */
    public boolean isActive ()
    {
        return this.active;
    }


    /**
     * Test whether capability read-back has been reconciled and the controller is engaged.
     *
     * @return True after the engage transition and before the disengage transition
     */
    public boolean isControllerEngaged ()
    {
        return this.controllerEngaged;
    }


    /**
     * Reconcile the performance controls with authoritative target capability and model identity.
     * This is polled from the controller flush so asynchronous proxy changes and Track Pin changes
     * cannot leave the controls engaged against a stale framework cursor.
     */
    public void reconcileControllerState ()
    {
        final boolean shouldEngage = this.active && this.surface.isDrumControllerApplicable ();
        final IDrumDevice candidate = shouldEngage ? this.model.getDrumDevice () : null;
        final boolean candidateChanged = shouldEngage && candidate != this.engagedDrumDevice;
        if (shouldEngage == this.controllerEngaged && !candidateChanged)
            return;

        final boolean wasEngaged = this.controllerEngaged;

        if (this.engagedDrumDevice != null && this.engagedDrumDevice != candidate)
            this.engagedDrumDevice.getDrumPadBank ().setIndication (false);
        if (candidate != null)
        {
            candidate.getDrumPadBank ().scrollTo (this.scales.getDrumOffset (), false);
            candidate.getDrumPadBank ().setIndication (true);
        }

        this.controllerEngaged = shouldEngage;
        this.engagedDrumDevice = candidate;

        if (shouldEngage)
        {
            if (!wasEngaged)
                this.surface.setVelocityTranslationTable (Scales.getIdentityMatrix ());
        }
        else
            this.restoreVelocityTranslation ();

        if (wasEngaged != shouldEngage)
        {
            final IView activeView = this.surface.getViewManager ().getActive ();
            if (activeView != null)
                activeView.updateNoteMapping ();
        }
    }


    /**
     * Test whether this component owns a physical grid note.
     *
     * @param note The physical grid note
     * @return True if the note belongs to the complete lower four-row controller
     */
    public boolean ownsGridNote (final int note)
    {
        final int index = note - this.surface.getPadGrid ().getStartNote ();
        if (index < 0)
            return false;

        return index < this.surface.getPadGrid ().getCols () * LOWER_GRID_ROWS;
    }


    private void restoreVelocityTranslation ()
    {
        final int [] velocityTable = Scales.getIdentityMatrix ();
        if (this.configuration.isAccentActive ())
        {
            Arrays.fill (velocityTable, Math.min (127, Math.max (0, this.configuration.getFixedAccentValue ())));
            velocityTable[0] = 0;
        }
        this.surface.setVelocityTranslationTable (velocityTable);
    }


}
