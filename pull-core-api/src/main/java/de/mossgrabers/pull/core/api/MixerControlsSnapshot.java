// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;


/** Bounded controls for one pure reloadable mixer-render request. */
public record MixerControlsSnapshot (List<MixerControlSnapshot> controls)
{
    private static final MixerControlsSnapshot EMPTY = new MixerControlsSnapshot (List.of ());


    /** Validate and copy the bounded request. */
    public MixerControlsSnapshot
    {
        controls = List.copyOf (Objects.requireNonNull (controls, "controls"));
        if (controls.size () > ParameterSlot.BANK_SIZE)
            throw new IllegalArgumentException ("mixer render requests support at most eight controls");
        final Set<Integer> columns = new HashSet<> ();
        for (final MixerControlSnapshot control: controls)
        {
            if (!columns.add (Integer.valueOf (control.column ())))
                throw new IllegalArgumentException ("mixer render requests require unique columns");
        }
    }


    /** Get an empty render request. */
    public static MixerControlsSnapshot empty ()
    {
        return EMPTY;
    }
}
