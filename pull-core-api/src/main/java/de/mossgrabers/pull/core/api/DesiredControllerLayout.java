// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api;

import java.util.Objects;


/** Complete replayable request for the stable note-controller layout. */
public record DesiredControllerLayout (ControllerNoteView noteView, boolean neutralizing)
{
    private static final DesiredControllerLayout EMPTY = new DesiredControllerLayout (ControllerNoteView.NONE, false);
    private static final DesiredControllerLayout NEUTRAL = new DesiredControllerLayout (ControllerNoteView.NONE, true);


    /** Validate the layout. */
    public DesiredControllerLayout
    {
        noteView = Objects.requireNonNull (noteView, "noteView");
        if (neutralizing && noteView.isPresent ())
            throw new IllegalArgumentException ("a neutral layout cannot select a note view");
    }


    /** Request one installed note-controller view. */
    public static DesiredControllerLayout note (final ControllerNoteView noteView)
    {
        final ControllerNoteView checked = Objects.requireNonNull (noteView, "noteView");
        if (!checked.isPresent ())
            throw new IllegalArgumentException ("note view must be present");
        return new DesiredControllerLayout (checked, false);
    }


    /** Leave the established stable layout untouched. */
    public static DesiredControllerLayout empty ()
    {
        return EMPTY;
    }


    /** Explicitly leave musical pad control for the safe Session layout. */
    public static DesiredControllerLayout neutral ()
    {
        return NEUTRAL;
    }


    /** Test whether the core currently owns the note-controller layout. */
    public boolean isPresent ()
    {
        return this.noteView.isPresent ();
    }
}
