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
import de.mossgrabers.framework.daw.data.bank.ITrackBank;
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
        if (this.surface.isSessionNavigationActive ())
        {
            this.scrollTracks (false);
            return;
        }

        final IMode activeMode = this.surface.getModeManager ().getActive ();
        if (activeMode != null)
            activeMode.selectPreviousItemPage ();
    }


    /** {@inheritDoc} */
    @Override
    protected void scrollRight ()
    {
        if (this.surface.isSessionNavigationActive ())
        {
            this.scrollTracks (true);
            return;
        }

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

        if (this.surface.isSessionNavigationActive ())
        {
            this.scrollStates.setCanScrollLeft (this.canScrollTracks (false));
            this.scrollStates.setCanScrollRight (this.canScrollTracks (true));
        }
        else
        {
            this.scrollStates.setCanScrollLeft (mode != null && (shiftPressed ? mode.hasPreviousItem () : mode.hasPreviousItemPage ()));
            this.scrollStates.setCanScrollRight (mode != null && (shiftPressed ? mode.hasNextItem () : mode.hasNextItemPage ()));
        }
    }


    private void scrollTracks (final boolean forwards)
    {
        final ITrackBank trackBank = this.model.getCurrentTrackBank ();
        if (!this.surface.isShiftPressed ())
        {
            if (forwards)
                trackBank.selectNextPage ();
            else
                trackBank.selectPreviousPage ();
        }
        else if (forwards)
            trackBank.scrollForwards ();
        else
            trackBank.scrollBackwards ();
    }


    private boolean canScrollTracks (final boolean forwards)
    {
        final ITrackBank trackBank = this.model.getCurrentTrackBank ();
        if (!this.surface.isShiftPressed ())
            return forwards ? trackBank.canScrollPageForwards () : trackBank.canScrollPageBackwards ();
        return forwards ? trackBank.canScrollForwards () : trackBank.canScrollBackwards ();
    }
}
