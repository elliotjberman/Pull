// (c) 2017-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.controller.ableton.push.view;

import de.mossgrabers.controller.ableton.push.PushConfiguration;
import de.mossgrabers.controller.ableton.push.controller.PushControlSurface;
import de.mossgrabers.framework.controller.grid.IPadGrid;
import de.mossgrabers.framework.daw.IModel;
import de.mossgrabers.framework.featuregroup.AbstractView;
import de.mossgrabers.framework.utils.ButtonEvent;
import de.mossgrabers.framework.view.TransposeView;
import de.mossgrabers.pull.core.api.SessionBankShape;

/** Permanent neutral adapter for the core-owned Session surface. */
public class SessionView extends AbstractView<PushControlSurface, PushConfiguration> implements TransposeView
{
    public static final SessionBankShape SESSION_BANK_SHAPE = new SessionBankShape (8, 8);

    public SessionView (final PushControlSurface surface, final IModel model)
    {
        this ("Session", surface, model);
    }

    protected SessionView (final String name, final PushControlSurface surface, final IModel model)
    {
        super (name, surface, model);
    }

    @Override
    public void onActivate ()
    {
        final SessionBankShape shape = this.getSessionBankShape ();
        if (shape.isPresent ())
            this.surface.getSessionBankRegistry ().activate (shape);
        super.onActivate ();
    }

    protected SessionBankShape getSessionBankShape () { return SESSION_BANK_SHAPE; }

    @Override
    public void onGridNote (final int note, final int velocity) { }

    @Override
    public void onGridPressure (final int note, final int value) { }

    @Override
    public void drawGrid ()
    {
        final IPadGrid padGrid = this.surface.getPadGrid ();
        for (int y = 0; y < padGrid.getRows (); y++)
            for (int x = 0; x < padGrid.getCols (); x++)
                padGrid.lightEx (x, y, IPadGrid.GRID_OFF);
    }

    @Override
    public void onOctaveDown (final ButtonEvent event) { }

    @Override
    public void onOctaveUp (final ButtonEvent event) { }

    @Override
    public boolean isOctaveUpButtonOn () { return false; }

    @Override
    public boolean isOctaveDownButtonOn () { return false; }

    @Override
    public void resetOctave () { }
}
