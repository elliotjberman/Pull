// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api;

import java.util.Map;
import java.util.Objects;

/**
 * Versioned capabilities implemented by the stable shell.
 *
 * @param versions Capability identifier to positive version
 */
public record ShellCapabilities (Map<String, Integer> versions)
{
    private static final ShellCapabilities EMPTY = new ShellCapabilities (Map.of ());


    /**
     * Validate and copy the capability map.
     */
    public ShellCapabilities
    {
        Objects.requireNonNull (versions, "versions");
        for (final Map.Entry<String, Integer> entry: versions.entrySet ())
        {
            final String identifier = Objects.requireNonNull (entry.getKey (), "capability identifier");
            final Integer version = Objects.requireNonNull (entry.getValue (), "capability version");
            if (identifier.isBlank ())
                throw new IllegalArgumentException ("capability identifier must not be blank");
            if (version.intValue () <= 0)
                throw new IllegalArgumentException ("capability versions must be positive");
        }
        versions = Map.copyOf (versions);
    }


    /**
     * Get an empty capability set.
     *
     * @return The empty capabilities
     */
    public static ShellCapabilities empty ()
    {
        return EMPTY;
    }


    /**
     * Test whether these capabilities satisfy every required capability and version.
     *
     * @param required The required capabilities
     * @return True when all requirements are met
     */
    public boolean supports (final ShellCapabilities required)
    {
        Objects.requireNonNull (required, "required");
        for (final Map.Entry<String, Integer> requirement: required.versions.entrySet ())
        {
            if (this.versions.getOrDefault (requirement.getKey (), Integer.valueOf (0)).intValue () < requirement.getValue ().intValue ())
                return false;
        }
        return true;
    }
}
