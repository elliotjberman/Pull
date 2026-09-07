// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2017-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.controller.ableton.push.command.trigger;

import de.mossgrabers.controller.ableton.push.PushConfiguration;
import de.mossgrabers.controller.ableton.push.controller.PushControlSurface;
import de.mossgrabers.framework.command.trigger.Direction;
import de.mossgrabers.framework.command.trigger.mode.CursorCommand;
import de.mossgrabers.framework.daw.IModel;
import de.mossgrabers.framework.daw.data.bank.ISceneBank;
import de.mossgrabers.controller.ableton.push.mode.CorePageMode;
import de.mossgrabers.framework.utils.ButtonEvent;
import de.mossgrabers.framework.featuregroup.IMode;


/**
 * Command for cursor arrow keys.
 *
 * @author Jürgen Moßgraber
 */
public class PushCursorCommand extends CursorCommand<PushControlSurface, PushConfiguration>
{
    /**
     * Constructor.
     *
     * @param direction The direction of the pushed cursor arrow
     * @param model The model
     * @param surface The surface
     */
    public PushCursorCommand (final Direction direction, final IModel model, final PushControlSurface surface)
    {
        super (direction, model, surface, false);
    }


    /** The permanent binding stays inert for a declared core navigation footprint. */
    @Override
    public void execute (final ButtonEvent event, final int velocity)
    {
        if (!this.isCoreNavigationAdapter ())
            super.execute (event, velocity);
    }


    /** Owned core light output is arbitrated by the permanent surface light transport. */
    @Override
    public boolean canScroll ()
    {
        return !this.isCoreNavigationAdapter () && super.canScroll ();
    }


    private boolean isCoreNavigationAdapter ()
    {
        return this.surface.getModeManager ().getActive () instanceof CorePageMode || CorePageMode.containsSessionNavigationInput (
            de.mossgrabers.pull.core.api.PushControlIds.button ("ARROW_" + this.direction.name ()), this.surface.isSessionLayoutActive (), this.surface.isSessionNavigationActive ());
    }


    /**
     * Scroll scenes up.
     */
    @Override
    protected void scrollUp ()
    {
        final ISceneBank sceneBank = this.getSceneBank ();
        if (this.surface.isShiftPressed ())
            sceneBank.selectPreviousPage ();
        else
            sceneBank.scrollBackwards ();
    }


    /**
     * Scroll scenes down.
     */
    @Override
    protected void scrollDown ()
    {
        final ISceneBank sceneBank = this.getSceneBank ();
        if (this.surface.isShiftPressed ())
            sceneBank.selectNextPage ();
        else
            sceneBank.scrollForwards ();
    }


    /** {@inheritDoc} */
    @Override
    protected void scrollLeft ()
    {
        final IMode activeMode = this.surface.getModeManager ().getActive ();
        if (activeMode != null)
            activeMode.selectPreviousItemPage ();
    }


    /** {@inheritDoc} */
    @Override
    protected void scrollRight ()
    {
        final IMode activeMode = this.surface.getModeManager ().getActive ();
        if (activeMode != null)
            activeMode.selectNextItemPage ();
    }


    /** {@inheritDoc} */
    @Override
    protected ISceneBank getSceneBank ()
    {
        return this.model.getCurrentTrackBank ().getSceneBank ();
    }


    /** {@inheritDoc} */
    @Override
    protected void updateArrowStates ()
    {
        final ISceneBank sceneBank = this.getSceneBank ();
        final IMode mode = this.surface.getModeManager ().getActive ();
        final boolean shiftPressed = this.surface.isShiftPressed ();

        if (shiftPressed)
        {
            this.scrollStates.setCanScrollUp (sceneBank.canScrollPageBackwards ());
            this.scrollStates.setCanScrollDown (sceneBank.canScrollPageForwards ());
        }
        else
        {
            this.scrollStates.setCanScrollUp (sceneBank.canScrollBackwards ());
            this.scrollStates.setCanScrollDown (sceneBank.canScrollForwards ());
        }

        this.scrollStates.setCanScrollLeft (mode != null && (shiftPressed ? mode.hasPreviousItem () : mode.hasPreviousItemPage ()));
        this.scrollStates.setCanScrollRight (mode != null && (shiftPressed ? mode.hasNextItem () : mode.hasNextItemPage ()));
    }
}
