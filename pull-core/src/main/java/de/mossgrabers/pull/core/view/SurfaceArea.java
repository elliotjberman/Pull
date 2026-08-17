// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.view;

import de.mossgrabers.pull.core.api.ControlId;
import de.mossgrabers.pull.core.api.CoreControls;
import de.mossgrabers.pull.core.api.PushControlIds;
import de.mossgrabers.pull.core.api.event.ButtonInputEvent;
import de.mossgrabers.pull.core.api.event.ControllerInputEvent;
import de.mossgrabers.pull.core.api.event.CoreEvent;
import de.mossgrabers.pull.core.api.event.InputKind;
import de.mossgrabers.pull.core.api.event.TouchInputEvent;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;


/**
 * Fixed, named Push 2 regions used by reloadable views.
 *
 * <p>The private atomic footprint lets the compiler detect overlap between a whole region and a
 * named subregion without exposing arbitrary coordinates to workspace configuration.</p>
 */
public enum SurfaceArea
{
    /** Eight top encoders, including turn and touch input. */
    ENCODERS (union (range (ElementType.ENCODER_TURN, 0, 8), range (ElementType.ENCODER_TOUCH, 0, 8)), continuousControls ("KNOB", 8), Set.of (InputKind.RELATIVE, InputKind.TOUCH)),
    /** Relative turn input from the eight top encoders. */
    ENCODER_TURNS (range (ElementType.ENCODER_TURN, 0, 8), continuousControls ("KNOB", 8), Set.of (InputKind.RELATIVE)),
    /** Touch input from the eight top encoders. */
    ENCODER_TOUCHES (range (ElementType.ENCODER_TOUCH, 0, 8), continuousControls ("KNOB", 8), Set.of (InputKind.TOUCH)),
    /** Eight parameter cells aligned with the top encoders. */
    DISPLAY_PARAMETERS (range (ElementType.DISPLAY_PARAMETER, 0, 8), Set.of (), Set.of ()),
    /** Eight lower display cells aligned with the lower soft keys. */
    DISPLAY_BOTTOM_STRIP (range (ElementType.DISPLAY_BOTTOM, 0, 8), Set.of (), Set.of ()),
    /** Eight buttons above the display. */
    SOFT_KEYS_UPPER (range (ElementType.BUTTON, 108, 8), buttonControls ("ROW2_", 1, 8), Set.of (InputKind.BUTTON)),
    /** Eight buttons below the display. */
    SOFT_KEYS_LOWER (range (ElementType.BUTTON, 100, 8), buttonControls ("ROW1_", 1, 8), Set.of (InputKind.BUTTON)),
    /** Upper four rows of the pad grid. */
    GRID_UPPER (gridRectangle (0, 4, 8, 4), gridControls (0, 4, 8, 4), Set.of (InputKind.PAD, InputKind.POLY_PRESSURE)),
    /** Pad press/release edges for the upper four grid rows. */
    GRID_UPPER_PAD_EDGES (gridRectangle (0, 4, 8, 4), gridControls (0, 4, 8, 4), Set.of (InputKind.PAD)),
    /** Complete lower four rows of the pad grid. */
    GRID_LOWER (gridRectangle (0, 0, 8, 4), gridControls (0, 0, 8, 4), Set.of (InputKind.PAD, InputKind.POLY_PRESSURE)),
    /** Pad press/release edges for the lower four grid rows. */
    GRID_LOWER_PAD_EDGES (gridRectangle (0, 0, 8, 4), gridControls (0, 0, 8, 4), Set.of (InputKind.PAD)),
    /** Playable four-by-four drum block in the lower-left grid quadrant. */
    DRUM_PLAY_PADS (gridRectangle (0, 0, 4, 4), gridControls (0, 0, 4, 4), Set.of (InputKind.PAD, InputKind.POLY_PRESSURE)),
    /** Four momentary drum-rate pads at columns 4-7 on the bottom row. */
    DRUM_RATE_PADS (gridRectangle (4, 0, 4, 1), gridControls (4, 0, 4, 1), Set.of (InputKind.PAD, InputKind.POLY_PRESSURE)),
    /** Eight fill pads at columns 4-7 and rows 1-2 of the lower grid half. */
    DRUM_FILL_PADS (gridRectangle (4, 1, 4, 2), Set.copyOf (CoreControls.DRUM_FILLS), Set.of (InputKind.PAD, InputKind.POLY_PRESSURE)),
    /** Four Bitwig-manually-mappable control pads at columns 4-7 on row 3. */
    DRUM_CONTROL_PADS (gridRectangle (4, 3, 4, 1), Set.copyOf (CoreControls.DRUM_CONTROL_PADS), Set.of (InputKind.PAD)),
    /** Four scene keys aligned to the upper Session grid. */
    SCENE_KEYS_UPPER (range (ElementType.BUTTON, 200, 4), buttonControls ("SCENE", 1, 4), Set.of (InputKind.BUTTON)),
    /** Four scene keys aligned to the lower grid half. */
    SCENE_KEYS_LOWER (range (ElementType.BUTTON, 204, 4), buttonControls ("SCENE", 5, 4), Set.of (InputKind.BUTTON)),
    /** Four directional arrow keys. */
    NAVIGATION_ARROWS (range (ElementType.BUTTON, 300, 4), namedButtonControls ("ARROW_LEFT", "ARROW_RIGHT", "ARROW_UP", "ARROW_DOWN"), Set.of (InputKind.BUTTON)),
    /** Page-left and page-right buttons. */
    NAVIGATION_PAGE (range (ElementType.BUTTON, 304, 2), namedButtonControls ("PAGE_LEFT", "PAGE_RIGHT"), Set.of (InputKind.BUTTON)),
    /** Octave-down and octave-up buttons. */
    NAVIGATION_OCTAVE (range (ElementType.BUTTON, 306, 2), namedButtonControls ("OCTAVE_DOWN", "OCTAVE_UP"), Set.of (InputKind.BUTTON)),
    /** Touch strip input and output mode. */
    TOUCH_STRIP (range (ElementType.CONTINUOUS, 0, 1), Set.of (PushControlIds.continuous ("TOUCHSTRIP")), Set.of (InputKind.ABSOLUTE, InputKind.TOUCH)),
    /** Dedicated tempo encoder. */
    TEMPO_ENCODER (range (ElementType.CONTINUOUS, 1, 1), Set.of (PushControlIds.continuous ("TEMPO")), Set.of (InputKind.RELATIVE, InputKind.TOUCH)),
    /** Dedicated master encoder while it addresses master volume. */
    MASTER_ENCODER (range (ElementType.CONTINUOUS, 2, 1), Set.of (PushControlIds.continuous ("MASTER_KNOB")), Set.of (InputKind.RELATIVE, InputKind.TOUCH)),
    /** Aggregate pressure shared by the complete pad grid. */
    GRID_CHANNEL_PRESSURE (range (ElementType.GRID_PRESSURE, 0, 1), Set.of (PushControlIds.CHANNEL_PRESSURE), Set.of (InputKind.CHANNEL_PRESSURE)),
    /** Push Record button. */
    RECORD_BUTTON (range (ElementType.BUTTON, 0, 1), Set.of (PushControlIds.button ("RECORD")), Set.of (InputKind.BUTTON)),
    /** Push Play button. */
    PLAY_BUTTON (range (ElementType.BUTTON, 5, 1), Set.of (PushControlIds.button ("PLAY")), Set.of (InputKind.BUTTON)),
    /** Push Shift modifier. */
    SHIFT_MODIFIER (range (ElementType.BUTTON, 1, 1), Set.of (PushControlIds.button ("SHIFT")), Set.of (InputKind.BUTTON)),
    /** Push Select modifier. */
    SELECT_MODIFIER (range (ElementType.BUTTON, 2, 1), Set.of (PushControlIds.button ("SELECT")), Set.of (InputKind.BUTTON)),
    /** Push Session button. */
    SESSION_BUTTON (range (ElementType.BUTTON, 3, 1), Set.of (PushControlIds.button ("SESSION")), Set.of (InputKind.BUTTON)),
    /** Push Note button. */
    NOTE_BUTTON (range (ElementType.BUTTON, 4, 1), Set.of (PushControlIds.button ("NOTE")), Set.of (InputKind.BUTTON)),
    /** Push Layout button. */
    LAYOUT_BUTTON (range (ElementType.BUTTON, 6, 1), Set.of (PushControlIds.button ("LAYOUT")), Set.of (InputKind.BUTTON)),
    /** Push Stop Clip button, semantically owned by an active Session view. */
    STOP_CLIP_BUTTON (range (ElementType.BUTTON, 7, 1), Set.of (PushControlIds.button ("STOP_CLIP")), Set.of (InputKind.BUTTON)),
    /** Push Mute button, targeting the authoritative selected track. */
    MUTE_BUTTON (range (ElementType.BUTTON, 8, 1), Set.of (PushControlIds.button ("MUTE")), Set.of (InputKind.BUTTON)),
    /** Push Solo button, targeting the authoritative selected track. */
    SOLO_BUTTON (range (ElementType.BUTTON, 9, 1), Set.of (PushControlIds.button ("SOLO")), Set.of (InputKind.BUTTON));

    private final Set<HardwareElement> footprint;
    private final Set<ControlId>       controls;
    private final Set<InputKind>       inputKinds;


    SurfaceArea (final Set<HardwareElement> footprint, final Set<ControlId> controls, final Set<InputKind> inputKinds)
    {
        this.footprint = Set.copyOf (footprint);
        this.controls = Set.copyOf (controls);
        this.inputKinds = Set.copyOf (Objects.requireNonNull (inputKinds, "inputKinds"));
    }


    /**
     * Test whether this region intersects another fixed region.
     *
     * @param other Other region
     * @return True if at least one atomic hardware element is shared
     */
    public boolean overlaps (final SurfaceArea other)
    {
        Objects.requireNonNull (other, "other");
        for (final HardwareElement element: this.footprint)
        {
            if (other.footprint.contains (element))
                return true;
        }
        return false;
    }


    /**
     * Test whether an event belongs to this region.
     *
     * @param event Normalized core event
     * @return True if this region contains the event's control and kind
     */
    public boolean contains (final CoreEvent event)
    {
        Objects.requireNonNull (event, "event");
        if (event instanceof final ButtonInputEvent buttonEvent)
            return this.controls.contains (buttonEvent.controlId ());
        if (event instanceof final ControllerInputEvent inputEvent)
            return this.inputKinds.contains (inputEvent.kind ()) && this.controls.contains (inputEvent.controlId ());
        if (event instanceof final TouchInputEvent touchEvent)
            return this.inputKinds.contains (InputKind.TOUCH) && this.controls.contains (touchEvent.controlId ());
        return false;
    }


    /**
     * Get the logical controls permanently associated with this region.
     *
     * @return Immutable controls
     */
    public Set<ControlId> controls ()
    {
        return this.controls;
    }


    /**
     * Get the normalized input kinds for this region.
     *
     * @return Immutable input kinds
     */
    public Set<InputKind> inputKinds ()
    {
        return this.inputKinds;
    }


    private static Set<HardwareElement> range (final ElementType type, final int start, final int count)
    {
        final Set<HardwareElement> elements = new LinkedHashSet<> ();
        for (int index = start; index < start + count; index++)
            elements.add (new HardwareElement (type, index));
        return elements;
    }


    @SafeVarargs
    private static Set<HardwareElement> union (final Set<HardwareElement>... sets)
    {
        final Set<HardwareElement> elements = new LinkedHashSet<> ();
        for (final Set<HardwareElement> set: sets)
            elements.addAll (set);
        return elements;
    }


    private static Set<HardwareElement> gridRectangle (final int left, final int bottom, final int width, final int height)
    {
        final Set<HardwareElement> elements = new LinkedHashSet<> ();
        for (int row = bottom; row < bottom + height; row++)
        {
            for (int column = left; column < left + width; column++)
                elements.add (new HardwareElement (ElementType.GRID_PAD, row * 8 + column));
        }
        return elements;
    }


    private static Set<ControlId> gridControls (final int left, final int bottom, final int width, final int height)
    {
        final Set<ControlId> controls = new LinkedHashSet<> ();
        for (int row = bottom; row < bottom + height; row++)
        {
            for (int column = left; column < left + width; column++)
                controls.add (PushControlIds.pad (row * 8 + column + 1));
        }
        return controls;
    }


    private static Set<ControlId> continuousControls (final String prefix, final int count)
    {
        final Set<ControlId> controls = new LinkedHashSet<> ();
        for (int index = 1; index <= count; index++)
            controls.add (PushControlIds.continuous (prefix + index));
        return controls;
    }


    private static Set<ControlId> buttonControls (final String prefix, final int start, final int count)
    {
        final Set<ControlId> controls = new LinkedHashSet<> ();
        for (int index = start; index < start + count; index++)
            controls.add (PushControlIds.button (prefix + index));
        return controls;
    }


    private static Set<ControlId> namedButtonControls (final String... names)
    {
        final Set<ControlId> controls = new LinkedHashSet<> ();
        for (final String name: names)
            controls.add (PushControlIds.button (name));
        return controls;
    }


    private enum ElementType
    {
        BUTTON,
        CONTINUOUS,
        ENCODER_TURN,
        ENCODER_TOUCH,
        GRID_PAD,
        GRID_PRESSURE,
        DISPLAY_PARAMETER,
        DISPLAY_BOTTOM
    }


    private record HardwareElement (ElementType type, int index)
    {
        private HardwareElement
        {
            Objects.requireNonNull (type, "type");
            if (index < 0)
                throw new IllegalArgumentException ("index must not be negative");
        }
    }
}
