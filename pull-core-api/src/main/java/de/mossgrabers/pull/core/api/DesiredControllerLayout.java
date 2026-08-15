// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api;

import java.util.Objects;


/** Complete replayable request for the stable note-controller layout. */
public record DesiredControllerLayout (ControllerNoteView noteView)
{
    private static final DesiredControllerLayout EMPTY = new DesiredControllerLayout (ControllerNoteView.NONE);


    /** Validate the layout. */
    public DesiredControllerLayout
    {
        noteView = Objects.requireNonNull (noteView, "noteView");
    }


    /** Request one installed note-controller view. */
    public static DesiredControllerLayout note (final ControllerNoteView noteView)
    {
        final ControllerNoteView checked = Objects.requireNonNull (noteView, "noteView");
        if (!checked.isPresent ())
            throw new IllegalArgumentException ("note view must be present");
        return new DesiredControllerLayout (checked);
    }


    /** Leave the established stable layout untouched. */
    public static DesiredControllerLayout empty ()
    {
        return EMPTY;
    }


    /** Test whether the core currently owns the note-controller layout. */
    public boolean isPresent ()
    {
        return this.noteView.isPresent ();
    }
}
