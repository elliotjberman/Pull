// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.shell.runtime;

import de.mossgrabers.framework.controller.hardware.IHwButton;
import de.mossgrabers.pull.core.api.ControlId;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;


/** Applies a complete view-scoped activation lease to separate mapping-only hardware actions. */
final class HardwareMappingActivationHost
{
    private final Map<ControlId, IHwButton> buttons;
    private final Predicate<ControlId> lifecycleIdle;
    private final Set<ControlId> active = new LinkedHashSet<> ();
    private final Set<ControlId> releasing = new LinkedHashSet<> ();

    private Set<ControlId> desired = Set.of ();


    HardwareMappingActivationHost (final Map<ControlId, IHwButton> buttons, final Predicate<ControlId> lifecycleIdle)
    {
        this.buttons = Map.copyOf (Objects.requireNonNull (buttons, "buttons"));
        this.lifecycleIdle = Objects.requireNonNull (lifecycleIdle, "lifecycleIdle");
    }


    /** Replace the complete desired set, applying each control once that control's gesture is idle. */
    void request (final Set<ControlId> controls)
    {
        final Set<ControlId> requested = Set.copyOf (Objects.requireNonNull (controls, "controls"));
        if (!this.buttons.keySet ().containsAll (requested))
            throw new IllegalArgumentException ("Requested hardware mapping is not installed");
        this.desired = requested;
        if (this.active.equals (this.desired) && this.releasing.isEmpty ())
            return;

        for (final Map.Entry<ControlId, IHwButton> entry: this.buttons.entrySet ())
        {
            final ControlId control = entry.getKey ();
            final boolean shouldBeActive = this.desired.contains (control);
            final boolean idle = this.lifecycleIdle.test (control);
            if (shouldBeActive)
            {
                if (this.active.contains (control) || !idle)
                    continue;
                entry.getValue ().rebind ();
                this.releasing.remove (control);
                this.active.add (control);
                continue;
            }
            if (this.active.remove (control))
            {
                if (idle)
                    entry.getValue ().unbind ();
                else
                {
                    entry.getValue ().unbindPress ();
                    this.releasing.add (control);
                }
            }
            else if (idle && this.releasing.remove (control))
            {
                entry.getValue ().unbind ();
            }
        }
    }


    /** Get the host actions currently admitting new presses. */
    Set<ControlId> activeMappings ()
    {
        return Set.copyOf (this.active);
    }
}
