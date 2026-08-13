// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2017-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.framework.controller.display;

import de.mossgrabers.framework.controller.color.ColorEx;
import de.mossgrabers.framework.controller.hardware.IHwGraphicsDisplay;
import de.mossgrabers.framework.daw.clip.INoteClip;
import de.mossgrabers.framework.daw.clip.NotePosition;
import de.mossgrabers.framework.daw.resource.ChannelType;
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
     * Show the debug window for the graphics display.
     */
    void showDebugWindow ();


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
     * Adds an empty element.
     *
     * @param hasSmallEmptyMenu If true draws an empty small menu
     */
    void addEmptyElement (boolean hasSmallEmptyMenu);


    /**
     * Adds a parameter element without top and bottom menu.
     *
     * @param parameterName The name to display for the parameter
     * @param parameterValue The numeric value of the parameter
     * @param parameterValueStr The textual form of the parameter
     * @param parameterIsActive The parameter is currently edited
     * @param parameterModulatedValue The modulated numeric value
     */
    void addParameterElement (String parameterName, int parameterValue, String parameterValueStr, boolean parameterIsActive, int parameterModulatedValue);


    /**
     * Adds a parameter element.
     *
     * @param topMenu The text of the top menu
     * @param isTopMenuOn True if the top menu is selected
     * @param bottomMenu The text of the bottom menu
     * @param type The channel type
     * @param bottomMenuColor A background color for the menu
     * @param isBottomMenuOn True if the bottom menu is selected
     * @param parameterName The name to display for the parameter
     * @param parameterValue The numeric value of the parameter
     * @param parameterValueStr The textual form of the parameter
     * @param parameterIsActive The parameter is currently edited
     * @param parameterModulatedValue The modulated numeric value
     */
    void addParameterElement (String topMenu, boolean isTopMenuOn, String bottomMenu, ChannelType type, ColorEx bottomMenuColor, boolean isBottomMenuOn, String parameterName, int parameterValue, String parameterValueStr, boolean parameterIsActive, int parameterModulatedValue);


    /**
     * Adds a parameter element.
     *
     * @param topMenu The text of the top menu
     * @param isTopMenuOn True if the top menu is selected
     * @param bottomMenu The text of the bottom menu
     * @param bottomMenuColor A background color for the menu
     * @param isBottomMenuOn True if the bottom menu is selected
     * @param parameterName The name to display for the parameter
     * @param parameterValue The numeric value of the parameter
     * @param parameterValueStr The textual form of the parameter
     * @param parameterIsActive The parameter is currently edited
     * @param parameterModulatedValue The modulated numeric value
     */
    void addParameterElementWithPlainMenu (String topMenu, boolean isTopMenuOn, String bottomMenu, ColorEx bottomMenuColor, boolean isBottomMenuOn, String parameterName, int parameterValue, String parameterValueStr, boolean parameterIsActive, int parameterModulatedValue);


    /**
     * Adds a parameter element.
     *
     * @param topMenu The text of the top menu
     * @param isTopMenuOn True if the top menu is selected
     * @param bottomMenu The text of the bottom menu
     * @param deviceName The name of the device
     * @param bottomMenuColor A background color for the menu
     * @param isBottomMenuOn True if the bottom menu is selected
     * @param parameterName The name to display for the parameter
     * @param parameterValue The numeric value of the parameter
     * @param parameterValueStr The textual form of the parameter
     * @param parameterIsActive The parameter is currently edited
     * @param parameterModulatedValue The modulated numeric value
     */
    void addParameterElement (String topMenu, boolean isTopMenuOn, String bottomMenu, String deviceName, ColorEx bottomMenuColor, boolean isBottomMenuOn, String parameterName, int parameterValue, String parameterValueStr, boolean parameterIsActive, int parameterModulatedValue);


    /**
     * Add an options element to the message.
     *
     * @param headerTopName A text on the top
     * @param menuTopName The text for the top menu
     * @param isMenuTopSelected True if the top menu is selected (on)
     * @param headerBottomName A text on the bottom
     * @param menuBottomName The text for the bottom menu
     * @param isMenuBottomSelected True if the bottom menu is selected (on)
     * @param useSmallTopMenu If true use small menus
     */
    void addOptionElement (String headerTopName, String menuTopName, boolean isMenuTopSelected, String headerBottomName, String menuBottomName, boolean isMenuBottomSelected, boolean useSmallTopMenu);


    /**
     * Add an options element to the message.
     *
     * @param headerTopName A text on the top
     * @param menuTopName The text for the top menu
     * @param isMenuTopSelected True if the top menu is selected (on)
     * @param menuTopColor The color to use for the background top menu, may be null
     * @param headerBottomName A text on the bottom
     * @param menuBottomName The text for the bottom menu
     * @param isMenuBottomSelected True if the bottom menu is selected (on)
     * @param menuBottomColor The color to use for the background bottom menu, may be null
     * @param useSmallTopMenu If true use small menus
     */
    void addOptionElement (String headerTopName, String menuTopName, boolean isMenuTopSelected, ColorEx menuTopColor, String headerBottomName, String menuBottomName, boolean isMenuBottomSelected, ColorEx menuBottomColor, boolean useSmallTopMenu);


    /**
     * Add an options element to the message.
     *
     * @param headerTopName A text on the top
     * @param menuTopName The text for the top menu
     * @param isMenuTopSelected True if the top menu is selected (on)
     * @param menuTopColor The color to use for the background top menu, may be null
     * @param headerBottomName A text on the bottom
     * @param menuBottomName The text for the bottom menu
     * @param isMenuBottomSelected True if the bottom menu is selected (on)
     * @param menuBottomColor The color to use for the background bottom menu, may be null
     * @param useSmallTopMenu If true use small menus
     * @param isBottomHeaderSelected True to draw the lower header selected
     */
    void addOptionElement (String headerTopName, String menuTopName, boolean isMenuTopSelected, ColorEx menuTopColor, String headerBottomName, String menuBottomName, boolean isMenuBottomSelected, ColorEx menuBottomColor, boolean useSmallTopMenu, boolean isBottomHeaderSelected);


    /**
     * Add a list element to the message with one selected item.
     *
     * @param displaySize The number of items to display in the list
     * @param elements The list with all items
     * @param selectedIndex The selected index in the list
     */
    void addListElement (int displaySize, String [] elements, int selectedIndex);


    /**
     * Add a list element to the message.
     *
     * @param items Must contain X number of texts
     * @param selected Must contain X number of states
     */
    void addListElement (String [] items, boolean [] selected);


    /**
     * Add an element (column) to the display.
     *
     * @param component The component to add
     */
    void addElement (IComponent component);


    /**
     * Draws a graph. The data parameter provides the input data. The index of the array is the
     * x-axis and the value the y-axis. Each distinct value is connected with a line to the next
     * value. The graph is adjusted to the given bounding rectangle (x,y,width,height).
     *
     * @param x The left side of the bounding box
     * @param y The upper side of the bounding box
     * @param width The width of the bounding box
     * @param height The height of the bounding box
     * @param color The color of the line
     * @param data The data to draw
     * @param maxValue The maximum y-value of the data
     */
    void addGraphOverlay (int x, int y, int width, int height, ColorEx color, int [] data, int maxValue);


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
