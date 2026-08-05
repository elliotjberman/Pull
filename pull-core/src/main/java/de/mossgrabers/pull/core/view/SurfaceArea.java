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
    /** Twelve fill pads at columns 4-7 and rows 1-3 of the lower grid half. */
    DRUM_FILL_PADS (gridRectangle (4, 1, 4, 3), Set.copyOf (CoreControls.DRUM_FILLS), Set.of (InputKind.PAD, InputKind.POLY_PRESSURE)),
    /** Push Record button. */
    RECORD_BUTTON (button (0), Set.of (PushControlIds.button ("RECORD")), Set.of (InputKind.BUTTON)),
    /** Push Shift modifier. */
    SHIFT_MODIFIER (button (1), Set.of (PushControlIds.button ("SHIFT")), Set.of (InputKind.BUTTON)),
    /** Push Select modifier. */
    SELECT_MODIFIER (button (2), Set.of (PushControlIds.button ("SELECT")), Set.of (InputKind.BUTTON)),
    /** Push Session button. */
    SESSION_BUTTON (button (3), Set.of (PushControlIds.button ("SESSION")), Set.of (InputKind.BUTTON)),
    /** Push Note button. */
    NOTE_BUTTON (button (4), Set.of (PushControlIds.button ("NOTE")), Set.of (InputKind.BUTTON));

    private final Set<HardwareElement> footprint;
    private final Set<ControlId>       controls;
    private final Set<InputKind>       inputKinds;


    SurfaceArea (final Set<HardwareElement> footprint, final Set<ControlId> controls, final Set<InputKind> inputKinds)
    {
        this.footprint = Set.copyOf (footprint);
        this.controls = Set.copyOf (controls);
        this.inputKinds = Set.copyOf (Objects.requireNonNull (inputKinds, "inputKinds"));
        if (this.inputKinds.isEmpty ())
            throw new IllegalArgumentException ("inputKinds must not be empty");
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


    private static Set<HardwareElement> button (final int index)
    {
        return Set.of (new HardwareElement (ElementType.BUTTON, index));
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


    private enum ElementType
    {
        BUTTON,
        GRID_PAD
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
