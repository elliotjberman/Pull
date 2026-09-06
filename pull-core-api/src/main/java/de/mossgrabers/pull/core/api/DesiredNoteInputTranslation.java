// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;


/** Complete, bounded native note-input translation; an owned silent table also blocks legacy writers. */
public record DesiredNoteInputTranslation (boolean owned, List<Integer> keyTranslation, List<Integer> velocityTranslation)
{
    private static final DesiredNoteInputTranslation UNOWNED = new DesiredNoteInputTranslation (false, List.of (), List.of ());
    private static final DesiredNoteInputTranslation SILENT = new DesiredNoteInputTranslation (true, Collections.nCopies (128, Integer.valueOf (-1)), IntStream.range (0, 128).boxed ().toList ());


    public DesiredNoteInputTranslation
    {
        keyTranslation = List.copyOf (Objects.requireNonNull (keyTranslation, "keyTranslation"));
        velocityTranslation = List.copyOf (Objects.requireNonNull (velocityTranslation, "velocityTranslation"));
        if (keyTranslation.size () != (owned ? 128 : 0) || velocityTranslation.size () != (owned ? 128 : 0))
            throw new IllegalArgumentException ("Owned note translation requires exactly 128 keys and velocities");
        if (keyTranslation.stream ().anyMatch (value -> value.intValue () < -1 || value.intValue () > 127) || velocityTranslation.stream ().anyMatch (value -> value.intValue () < 0 || value.intValue () > 127))
            throw new IllegalArgumentException ("Note translation contains a value outside MIDI range");
        for (int key = 0; key < keyTranslation.size (); key++)
        {
            if ((key < 36 || key > 99) && keyTranslation.get (key).intValue () >= 0)
                throw new IllegalArgumentException ("Native translation can enable only the installed Push pad notes 36..99");
        }
    }


    public static DesiredNoteInputTranslation unowned ()
    {
        return UNOWNED;
    }


    public static DesiredNoteInputTranslation silent ()
    {
        return SILENT;
    }


    public boolean allowsNotes ()
    {
        return this.keyTranslation.stream ().anyMatch (value -> value.intValue () >= 0);
    }
}
