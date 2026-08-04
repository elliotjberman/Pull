// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api;

import de.mossgrabers.pull.core.api.event.InputKind;

import java.util.Objects;

/**
 * One physical-control and input-kind pair routed to the reloadable core.
 *
 * @param controlId Stable physical control identity
 * @param kind Input kind routed for this control
 * @param mode Whether the core observes the input or owns it exclusively
 */
public record InputRoute (ControlId controlId, InputKind kind, InputRouteMode mode)
{
    /**
     * Validate the route.
     */
    public InputRoute
    {
        controlId = Objects.requireNonNull (controlId, "controlId");
        kind = Objects.requireNonNull (kind, "kind");
        mode = Objects.requireNonNull (mode, "mode");
    }
}
