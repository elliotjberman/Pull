// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2017-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.controller.ableton.push.mode.track;

import java.util.ArrayList;
import java.util.List;

import de.mossgrabers.controller.ableton.push.controller.PushControlSurface;
import de.mossgrabers.framework.controller.display.IGraphicDisplay;
import de.mossgrabers.framework.daw.IModel;
import de.mossgrabers.framework.daw.data.ICursorTrack;
import de.mossgrabers.framework.daw.data.ITrack;
import de.mossgrabers.framework.daw.data.bank.ITrackBank;
import de.mossgrabers.framework.graphics.canvas.component.TrackMixerComponent;
import de.mossgrabers.framework.graphics.canvas.component.TrackMixerComponent.MenuData;
import de.mossgrabers.framework.graphics.canvas.component.TrackMixerComponent.ParameterData;
import de.mossgrabers.framework.graphics.canvas.component.TrackMixerComponent.TrackData;
import de.mossgrabers.framework.mode.Modes;
import de.mossgrabers.framework.parameter.IParameter;
import de.mossgrabers.framework.parameterprovider.track.CrossfadeParameterProvider;
import de.mossgrabers.framework.utils.Pair;


/**
 * Mode for editing the cross-fade setting of all tracks.
 *
 * @author Jürgen Moßgraber
 */
public class CrossfadeMode extends AbstractTrackMode
{
    /**
     * Constructor.
     *
     * @param surface The control surface
     * @param model The model
     */
    public CrossfadeMode (final PushControlSurface surface, final IModel model)
    {
        super (Modes.NAME_CROSSFADE, surface, model);

        this.setParameterProvider (new CrossfadeParameterProvider (model));
    }


    /** {@inheritDoc} */
    @Override
    public void updateDisplay2 (final IGraphicDisplay display)
    {
        this.updateMenuItems (GLOBAL_CROSSFADER_MENU);

        final List<MenuData> menus = new ArrayList<> (8);
        final List<ParameterData> parameters = new ArrayList<> (8);
        final List<TrackData> tracks = new ArrayList<> (8);
        final ITrackBank trackBank = this.model.getCurrentTrackBank ();
        final ICursorTrack cursorTrack = this.model.getCursorTrack ();
        for (int i = 0; i < 8; i++)
        {
            final Pair<String, Boolean> menuItem = this.menu.get (i);
            menus.add (new MenuData (menuItem.getKey ().trim (), menuItem.getValue ().booleanValue ()));

            final ITrack track = trackBank.getItem (i);
            if (track.doesExist ())
            {
                final IParameter crossfader = track.getCrossfadeParameter ();
                parameters.add (new ParameterData ("Crossfader", crossfader.getValue (), crossfader.getModulatedValue (), crossfader.getDisplayedValue (8), track.isActivated ()));
            }
            else
                parameters.add (new ParameterData ("", -1, -1, "", false));

            tracks.add (new TrackData (track.doesExist () ? track.getName (12) : "", this.updateType (track), track.getColor (), track.isSelected (), track.isActivated (), track.isSelected () && cursorTrack.isPinned ()));
        }

        display.addElement (new TrackMixerComponent (menus, parameters, tracks, 0, 0));
    }

}
