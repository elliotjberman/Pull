// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2017-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.framework.controller.display;

import de.mossgrabers.framework.controller.hardware.IHwGraphicsDisplay;
import de.mossgrabers.framework.daw.clip.INoteClip;
import de.mossgrabers.framework.daw.clip.NotePosition;
import de.mossgrabers.framework.graphics.IBitmap;
import de.mossgrabers.framework.graphics.canvas.component.IComponent;


/**
 * Interface to a graphics display.
 *
 * @author Jürgen Moßgraber
 */
public interface IGraphicDisplay extends IDisplay
{
    /**
     * Send the message to the display process.
     */
    void send ();


    /**
     * Set a notification message on the display, which overlays the current content.
     *
     * @param message The text to display
     */
    void setNotificationMessage (String message);


    /**
     * Set a MIDI clip to display in a piano roll.
     *
     * @param clip The clip to display
     * @param quartersPerMeasure The quarters of a measure
     * @param activePosition The position of a note which should be marked as active
     */
    void setMidiClipElement (INoteClip clip, int quartersPerMeasure, NotePosition activePosition);


    /**
     * Set a message on the display.
     *
     * @param column The column in which to display the message
     * @param text The text to display
     * @return The display
     */
    public IGraphicDisplay setMessage (int column, String text);


    /**
     * Adds an empty element.
     */
    void addEmptyElement ();


    /**
     * Add an element (column) to the display.
     *
     * @param component The component to add
     */
    void addElement (IComponent component);


    /**
     * Assign a proxy to the hardware display, which gets filled by this graphics display.
     *
     * @param display The hardware display
     */
    void setHardwareDisplay (IHwGraphicsDisplay display);


    /**
     * Get the hardware display.
     *
     * @return The hardware display
     */
    IHwGraphicsDisplay getHardwareDisplay ();


    /**
     * Get the bitmap into which the image gets drawn.
     *
     * @return The bitmap
     */
    IBitmap getImage ();
}
