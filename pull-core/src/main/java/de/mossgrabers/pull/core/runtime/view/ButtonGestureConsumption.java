// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.api.ControlId;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/** Bounded core-owned modifier consumption shared by the gesture's participating views. */
public final class ButtonGestureConsumption
{
    private final Set<ControlId> registered;
    private final Set<ControlId> consumed = new HashSet<> ();

    public ButtonGestureConsumption (final Set<ControlId> registered)
    {
        this.registered = Set.copyOf (Objects.requireNonNull (registered, "registered"));
        if (this.registered.size () > 16)
            throw new IllegalArgumentException ("at most sixteen shared button gestures are supported");
    }

    void begin (final ControlId control)
    {
        this.requireRegistered (control);
        this.consumed.remove (control);
    }

    void consume (final ControlId control)
    {
        this.requireRegistered (control);
        this.consumed.add (control);
    }

    boolean takeConsumed (final ControlId control)
    {
        this.requireRegistered (control);
        return this.consumed.remove (control);
    }

    private void requireRegistered (final ControlId control)
    {
        if (!this.registered.contains (control))
            throw new IllegalArgumentException ("button gesture is outside the registered set");
    }
}
