// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.controller.ableton.push.view;

import de.mossgrabers.controller.ableton.push.PushConfiguration;
import de.mossgrabers.controller.ableton.push.controller.PushControlSurface;
import de.mossgrabers.framework.controller.ButtonID;
import de.mossgrabers.framework.controller.grid.IPadGrid;
import de.mossgrabers.framework.daw.IModel;
import de.mossgrabers.framework.featuregroup.AbstractFeatureGroup;
import de.mossgrabers.framework.featuregroup.AbstractView;
import de.mossgrabers.framework.utils.ButtonEvent;
import de.mossgrabers.framework.view.Views;


/**
 * Semantically inert stable adapter for the core-owned Clip Timeline workspace.
 *
 * <p>The inherited view ID remains the stable shell's bounded view-selection anchor. All grid and
 * scene input policy and all visible feedback are exclusively owned by the reloadable core.</p>
 */
public final class ClipTimelineViewAdapter extends AbstractView<PushControlSurface, PushConfiguration>
{
    /** Constructor. */
    public ClipTimelineViewAdapter (final PushControlSurface surface, final IModel model)
    {
        super (Views.NAME_CLIP_LENGTH, surface, model);
    }


    /** {@inheritDoc} */
    @Override
    public void onGridNote (final int note, final int velocity)
    {
        // EXCLUSIVE core routing owns every grid edge while this adapter is active.
    }


    /** {@inheritDoc} */
    @Override
    public void onGridPressure (final int note, final int value)
    {
        // EXCLUSIVE core routing owns framework pressure while native musical notes remain routed.
    }


    /** {@inheritDoc} */
    @Override
    public void onButton (final ButtonID buttonID, final ButtonEvent event, final int velocity)
    {
        // EXCLUSIVE core routing owns every Scene-button edge while this adapter is active.
    }


    /** {@inheritDoc} */
    @Override
    public void drawGrid ()
    {
        final IPadGrid grid = this.surface.getPadGrid ();
        for (int row = 0; row < 8; row++)
        {
            for (int column = 0; column < 8; column++)
                grid.lightEx (column, row, IPadGrid.GRID_OFF);
        }
    }


    /** {@inheritDoc} */
    @Override
    public void updateNoteMapping ()
    {
        // Mechanical stable NoteInput translation only; no note-layout policy remains here.
        this.delayedUpdateNoteMapping (EMPTY_TABLE);
    }


    /** {@inheritDoc} */
    @Override
    public String getButtonColorID (final ButtonID buttonID)
    {
        return AbstractFeatureGroup.BUTTON_COLOR_OFF;
    }
}
