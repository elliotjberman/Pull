// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2017-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.controller.ableton.push.mode.track;

import java.util.ArrayList;
import java.util.List;

import de.mossgrabers.controller.ableton.push.controller.PushControlSurface;
import de.mossgrabers.framework.controller.ButtonID;
import de.mossgrabers.framework.controller.display.IGraphicDisplay;
import de.mossgrabers.framework.controller.valuechanger.IValueChanger;
import de.mossgrabers.framework.daw.IModel;
import de.mossgrabers.framework.daw.data.ICursorTrack;
import de.mossgrabers.framework.daw.data.ISend;
import de.mossgrabers.framework.daw.data.ITrack;
import de.mossgrabers.framework.daw.data.bank.ISendBank;
import de.mossgrabers.framework.daw.data.bank.ITrackBank;
import de.mossgrabers.framework.graphics.canvas.component.TrackMixerComponent;
import de.mossgrabers.framework.graphics.canvas.component.TrackMixerComponent.MenuData;
import de.mossgrabers.framework.graphics.canvas.component.TrackMixerComponent.ParameterData;
import de.mossgrabers.framework.graphics.canvas.component.TrackMixerComponent.TrackData;
import de.mossgrabers.framework.parameterprovider.track.SendParameterProvider;
import de.mossgrabers.framework.utils.Pair;


/**
 * Mode for editing a Send volumes.
 *
 * @author Jürgen Moßgraber
 */
public class SendMode extends AbstractTrackMode
{
    private final int sendIndex;


    /**
     * Constructor.
     *
     * @param surface The control surface
     * @param model The model
     * @param sendIndex The index of the send
     */
    public SendMode (final PushControlSurface surface, final IModel model, final int sendIndex)
    {
        super ("Send", surface, model);

        this.sendIndex = sendIndex;

        this.setParameterProvider (new SendParameterProvider (model, this.sendIndex, 0));
    }


    /** {@inheritDoc} */
    @Override
    public void onKnobTouch (final int index, final boolean isTouched)
    {
        super.onKnobTouch (index, isTouched);

        if (isTouched && this.surface.isShiftPressed () && this.surface.isSelectPressed () && this.getParameterProvider ().get (index) instanceof final ISend send)
        {
            this.surface.setTriggerConsumed (ButtonID.SELECT);
            send.toggleEnabled ();
        }
    }


    /** {@inheritDoc} */
    @Override
    public void updateDisplay2 (final IGraphicDisplay display)
    {
        this.updateMenuItems (2 + this.sendIndex);

        final List<MenuData> menus = new ArrayList<> (8);
        final List<ParameterData> parameters = new ArrayList<> (8);
        final List<TrackData> tracks = new ArrayList<> (8);
        final ITrackBank trackBank = this.model.getCurrentTrackBank ();
        final ICursorTrack cursorTrack = this.model.getCursorTrack ();
        final IValueChanger valueChanger = this.model.getValueChanger ();
        for (int i = 0; i < 8; i++)
        {
            final Pair<String, Boolean> menuItem = this.menu.get (i);
            menus.add (new MenuData (menuItem.getKey ().trim (), menuItem.getValue ().booleanValue ()));

            final ITrack track = trackBank.getItem (i);
            final ISendBank sendBank = track.getSendBank ();
            final ISend send = this.sendIndex < sendBank.getPageSize () ? sendBank.getItem (this.sendIndex) : null;
            if (track.doesExist () && send != null && send.doesExist ())
            {
                parameters.add (new ParameterData (send.getName (), valueChanger.toDisplayValue (send.getValue ()), valueChanger.toDisplayValue (send.getModulatedValue ()), send.getDisplayedValue (8), track.isActivated () && send.isEnabled ()));
            }
            else
                parameters.add (new ParameterData ("", -1, -1, "", false));

            tracks.add (new TrackData (track.doesExist () ? track.getName (12) : "", this.updateType (track), track.getColor (), track.isSelected (), track.isActivated (), track.isSelected () && cursorTrack.isPinned ()));
        }

        display.addElement (new TrackMixerComponent (menus, parameters, tracks, 0, 0));
    }
}
