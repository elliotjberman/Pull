// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.controller.ableton.push.parameter;

import java.util.Objects;


/**
 * Identity and generation of one parameter target known to the stable shell.
 *
 * @param domain Target domain
 * @param identity Identity within the domain
 * @param generation Actuator generation
 */
record ParameterTargetRef (String domain, String identity, long generation)
{
    /**
     * Validate the target reference.
     */
    public ParameterTargetRef
    {
        domain = requireText (domain, "domain");
        identity = requireText (identity, "identity");
        if (generation < 0)
            throw new IllegalArgumentException ("generation must not be negative");
    }


    private static String requireText (final String value, final String name)
    {
        final String checked = Objects.requireNonNull (value, name).strip ();
        if (checked.isEmpty ())
            throw new IllegalArgumentException (name + " must not be blank");
        return checked;
    }
}
