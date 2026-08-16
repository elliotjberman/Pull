// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.shell.runtime;

import com.bitwig.extension.controller.api.ControllerHost;
import com.bitwig.extension.controller.api.Parameter;
import com.bitwig.extension.controller.api.UserControlBank;

import de.mossgrabers.pull.core.api.UserControlBankSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;


/** Fixed eager Bitwig user-control topology used by reloadable controller views. */
final class BoundedUserControlHost
{
    private final List<Parameter> controls;


    BoundedUserControlHost (final ControllerHost host)
    {
        final UserControlBank bank = Objects.requireNonNull (host, "host").createUserControls (UserControlBankSnapshot.CAPACITY);
        final List<Parameter> created = new ArrayList<> (UserControlBankSnapshot.CAPACITY);
        for (int slot = 0; slot < UserControlBankSnapshot.CAPACITY; slot++)
        {
            final Parameter control = bank.getControl (slot);
            control.setLabel ("Drum Control " + (slot + 1));
            control.value ().markInterested ();
            created.add (control);
        }
        this.controls = List.copyOf (created);
    }


    UserControlBankSnapshot snapshot ()
    {
        return new UserControlBankSnapshot (true, this.controls.stream ().map (control -> Double.valueOf (clamp (control.get ()))).toList ());
    }


    void set (final int slot, final double normalizedValue)
    {
        this.controls.get (slot).set (normalizedValue);
    }


    private static double clamp (final double value)
    {
        return Math.max (0, Math.min (1, value));
    }
}
