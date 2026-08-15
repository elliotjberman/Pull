// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2017-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.framework.controller.grid;

import de.mossgrabers.framework.controller.color.ColorManager;
import de.mossgrabers.framework.daw.midi.IMidiOutput;


/**
 * Implementation of a grid of pads.
 *
 * @author Jürgen Moßgraber
 */
public class PadGridImpl implements IPadGrid
{
    private static final PadColor OFF_COLOR = PadColor.registered (GRID_OFF);

    protected static final int   NUM_NOTES = 128;

    protected final IMidiOutput  output;
    protected final ColorManager colorManager;

    protected LightInfo []       padStates;

    private final PadColor []    requestedColors      = new PadColor [NUM_NOTES];
    private final PadColor []    requestedBlinkColors = new PadColor [NUM_NOTES];
    private final int []         resolvedColors       = new int [NUM_NOTES];
    private final int []         resolvedBlinkColors  = new int [NUM_NOTES];

    protected int                rows;
    protected int                columns;
    protected int                startNote;
    protected int                endNote;


    /**
     * Constructor.
     *
     * @param colorManager The color manager for accessing specific colors to use
     * @param output The MIDI output which can address the pad states
     */
    public PadGridImpl (final ColorManager colorManager, final IMidiOutput output)
    {
        this (colorManager, output, 8, 8, 36);
    }


    /**
     * Constructor.
     *
     * @param colorManager The color manager for accessing specific colors to use
     * @param output The MIDI output which can address the pad states
     * @param rows The number of rows of the grid
     * @param columns The number of columns of the grid
     * @param startNote The start note of the grid
     */
    public PadGridImpl (final ColorManager colorManager, final IMidiOutput output, final int rows, final int columns, final int startNote)
    {
        this.colorManager = colorManager;
        this.output = output;
        this.rows = rows;
        this.columns = columns;
        this.startNote = startNote;
        this.endNote = this.startNote + this.rows * this.columns - 1;

        // Note: Even if the grid contains less than 128 pads it is more efficient to use
        // the 128 note values the pads understand
        this.padStates = new LightInfo [NUM_NOTES];
        for (int i = 0; i < NUM_NOTES; i++)
            this.padStates[i] = new LightInfo ();
    }


    /** {@inheritDoc} */
    @Override
    public void light (final int note, final int color)
    {
        this.writeLight (note, color, this.colorManager.getColorIndex (GRID_OFF), false);
    }


    /** {@inheritDoc} */
    @Override
    public void light (final int note, final PadColor color)
    {
        this.writeLight (note, color, null, false);
    }


    /** {@inheritDoc} */
    @Override
    public void light (final int note, final int color, final int blinkColor, final boolean fast)
    {
        this.writeLight (note, color, blinkColor < 0 ? this.colorManager.getColorIndex (GRID_OFF) : blinkColor, fast);
    }


    /** {@inheritDoc} */
    @Override
    public void lightEx (final int x, final int y, final int color)
    {
        this.lightEx (x, y, color, -1, false);
    }


    /** {@inheritDoc} */
    @Override
    public void lightEx (final int x, final int y, final int color, final int blinkColor, final boolean fast)
    {
        final int off = (this.rows - 1) * this.columns + this.startNote;
        this.writeLight (off + x - this.columns * y, color, blinkColor < 0 ? this.colorManager.getColorIndex (GRID_OFF) : blinkColor, fast);
    }


    /** {@inheritDoc} */
    @Override
    public void lightEx (final int x, final int y, final PadColor color)
    {
        this.lightEx (x, y, color, null, false);
    }


    /** {@inheritDoc} */
    @Override
    public void lightEx (final int x, final int y, final PadColor color, final PadColor blinkColor, final boolean fast)
    {
        final int off = (this.rows - 1) * this.columns + this.startNote;
        this.writeLight (off + x - this.columns * y, color, blinkColor, fast);
    }


    /** {@inheritDoc} */
    @Override
    public void light (final int note, final String colorID)
    {
        this.light (note, colorID, null, false);
    }


    /** {@inheritDoc} */
    @Override
    public void lightEx (final int x, final int y, final String colorID)
    {
        this.lightEx (x, y, colorID, null, false);
    }


    /** {@inheritDoc} */
    @Override
    public void light (final int note, final String colorID, final String blinkColorID, final boolean fast)
    {
        this.writeLight (note, PadColor.registered (colorID), blinkColorID == null ? null : PadColor.registered (blinkColorID), fast);
    }


    /** {@inheritDoc} */
    @Override
    public void lightEx (final int x, final int y, final String colorID, final String blinkColorID, final boolean fast)
    {
        this.lightEx (x, y, PadColor.registered (colorID), blinkColorID == null ? null : PadColor.registered (blinkColorID), fast);
    }


    /**
     * Set the lighting state of a pad.
     *
     * @param note The note in the array (0-127)
     * @param color The color or brightness to set
     * @param blinkColor The state to make a pad blink
     * @param fast Blinking is fast if true
     */
    private void writeLight (final int note, final PadColor color, final PadColor blinkColor, final boolean fast)
    {
        this.writeLight (note, this.resolveColor (note, color, this.requestedColors, this.resolvedColors), this.resolveColor (note, blinkColor == null ? OFF_COLOR : blinkColor, this.requestedBlinkColors, this.resolvedBlinkColors), fast);
    }


    private void writeLight (final int note, final int color, final int blinkColor, final boolean fast)
    {
        this.padStates[note].setColors (color, blinkColor, fast);
    }


    private int resolveColor (final int note, final PadColor color, final PadColor [] requested, final int [] resolved)
    {
        if (!color.equals (requested[note]))
        {
            requested[note] = color;
            resolved[note] = this.resolveColor (color);
        }
        return resolved[note];
    }


    /**
     * Resolve a pad color immediately before the indexed pad state is written.
     *
     * @param color The unresolved pad color
     * @return The controller-specific color index
     */
    protected final int resolveColor (final PadColor color)
    {
        return switch (color)
        {
            case final PadColor.Indexed indexed -> indexed.index ();
            case final PadColor.Registered registered -> this.colorManager.getColorIndex (registered.id ());
            case final PadColor.Rgb rgb -> this.colorManager.getColorIndex (rgb.color ());
        };
    }


    /** {@inheritDoc} */
    @Override
    public void forceFlush (final int note)
    {
        this.padStates[note].setColors (0, 0, false);
    }


    /** {@inheritDoc} */
    @Override
    public void forceFlush ()
    {
        for (int i = this.startNote; i <= this.endNote; i++)
            this.padStates[i].setColors (0, 0, false);
    }


    /** {@inheritDoc} */
    @Override
    public LightInfo getLightInfo (final int note)
    {
        return this.padStates[note];
    }


    /** {@inheritDoc} */
    @Override
    public void sendState (final int note)
    {
        final LightInfo state = note < this.padStates.length ? this.padStates[note] : new LightInfo ();
        final int [] translated = this.translateToController (note);
        final int color = state.getColor ();
        // MPE?
        final int channel = translated[0] < 0 ? 0 : translated[0];
        this.sendNoteState (channel, translated[1], color < 0 ? 0 : color);
        final int blinkColor = state.getBlinkColor ();
        if (blinkColor > 0 && blinkColor < 128)
            this.sendBlinkState (channel, translated[1], blinkColor, state.isFast ());
    }


    /**
     * Send the note/pad update to the controller.
     *
     * @param channel The channel
     * @param note The note
     * @param color The color
     */
    protected void sendNoteState (final int channel, final int note, final int color)
    {
        this.output.sendNoteEx (channel, note, color);
    }


    /**
     * Set the given pad/note to blink.
     *
     * @param channel The channel
     * @param note The note
     * @param blinkColor The color to use for blinking
     * @param fast Blink fast or slow
     */
    protected void sendBlinkState (final int channel, final int note, final int blinkColor, final boolean fast)
    {
        this.output.sendNoteEx (fast ? 14 : 10, note, blinkColor);
    }


    /** {@inheritDoc} */
    @Override
    public void turnOff ()
    {
        for (int i = this.startNote; i <= this.endNote; i++)
        {
            this.light (i, OFF_COLOR);
            this.sendState (i);
        }
    }


    /** {@inheritDoc} */
    @Override
    public int translateToGrid (final int note)
    {
        return note;
    }


    /** {@inheritDoc} */
    @Override
    public int [] translateToController (final int note)
    {
        return new int []
        {
            0,
            note
        };
    }


    /** {@inheritDoc} */
    @Override
    public int getRows ()
    {
        return this.rows;
    }


    /** {@inheritDoc} */
    @Override
    public int getCols ()
    {
        return this.columns;
    }


    /** {@inheritDoc} */
    @Override
    public int getStartNote ()
    {
        return this.startNote;
    }
}
