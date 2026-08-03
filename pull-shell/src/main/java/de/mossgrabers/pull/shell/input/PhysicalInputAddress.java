// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.shell.input;

import java.util.Objects;

/**
 * One registered physical control and input-kind pair. A control may expose more than one kind,
 * such as a pad edge and per-pad pressure.
 *
 * @param control Physical control key
 * @param kind Input kind
 * @param <C> Control key type
 */
public record PhysicalInputAddress<C> (C control, InputKind kind)
{
    /**
     * Validate an input address.
     */
    public PhysicalInputAddress
    {
        control = Objects.requireNonNull (control, "control");
        kind = Objects.requireNonNull (kind, "kind");
    }
}
