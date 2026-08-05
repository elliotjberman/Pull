// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2017-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.framework.featuregroup;

import de.mossgrabers.framework.controller.ButtonID;
import de.mossgrabers.framework.utils.ButtonEvent;
import de.mossgrabers.framework.utils.KeyManager;


/**
 * Interface to a view. A view contains a grid of pads and a number of buttons to which commands can
 * be assigned.
 *
 * @author Jürgen Moßgraber
 */
public interface IView extends IFeatureGroup
{
    /**
     * Pressure changed on the grid. The active view always receives this event; its note mapping
     * and fixed footprint determine whether the pressure has a musical destination.
     *
     * @param note The physical pad note, or -1 for aggregate channel pressure
     * @param value Pressure in the range 0-127
     */
    void onGridPressure (int note, int value);


    /**
     * Draw the pad grid.
     */
    void drawGrid ();


    /**
     * A pad has been pressed or released.
     *
     * @param note The note of the pad
     * @param velocity The velocity of the press
     */
    void onGridNote (int note, int velocity);


    /**
     * Long press actions on grid pads
     *
     * @param note The long pressed note
     */
    void onGridNoteLongPress (int note);


    /**
     * A button event occurred.
     *
     * @param buttonID The ID of the button
     * @param event The button event
     * @param velocity The velocity with which the button was pressed (0-127)
     */
    void onButton (ButtonID buttonID, ButtonEvent event, int velocity);


    /**
     * Hook to update all button LEDs, displays, etc.
     */
    void updateControlSurface ();


    /**
     * Update the note mapping of the grid pads.
     */
    void updateNoteMapping ();


    /**
     * Selects a track in the current page of the current track bank and makes the track visible in
     * the DAW.
     *
     * @param index The index of the track in the page
     */
    void selectTrack (int index);


    /**
     * Get the key manager.
     *
     * @return The key manager
     */
    KeyManager getKeyManager ();
}
