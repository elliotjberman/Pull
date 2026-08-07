// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api.output;

import de.mossgrabers.pull.core.api.ParameterSlot;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;


/** Bounded column-local scenes returned by the pure mixer renderer. */
public record MixerControlsDisplay (List<MixerControlDisplay> controls)
{
    private static final MixerControlsDisplay EMPTY = new MixerControlsDisplay (List.of ());


    /** Validate and copy the bounded result. */
    public MixerControlsDisplay
    {
        controls = List.copyOf (Objects.requireNonNull (controls, "controls"));
        if (controls.size () > ParameterSlot.BANK_SIZE)
            throw new IllegalArgumentException ("mixer render results support at most eight controls");
        final Set<Integer> columns = new HashSet<> ();
        for (final MixerControlDisplay control: controls)
        {
            if (!columns.add (Integer.valueOf (control.column ())))
                throw new IllegalArgumentException ("mixer render results require unique columns");
        }
    }


    /** Get an empty render result. */
    public static MixerControlsDisplay empty ()
    {
        return EMPTY;
    }
}
