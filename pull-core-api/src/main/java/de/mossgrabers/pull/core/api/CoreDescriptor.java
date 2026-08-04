// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api;

import java.util.Objects;

/**
 * Compatibility metadata for a controller core.
 *
 * @param apiVersion The exact required core API version
 * @param buildId The immutable build identifier
 * @param stateSchema The checkpoint schema identifier
 * @param stateSchemaVersion The checkpoint schema version
 * @param requiredCapabilities Shell capabilities required by this core
 */
public record CoreDescriptor (int apiVersion, String buildId, String stateSchema, int stateSchemaVersion, ShellCapabilities requiredCapabilities)
{
    /**
     * Validate and defensively retain descriptor values.
     */
    public CoreDescriptor
    {
        if (apiVersion <= 0)
            throw new IllegalArgumentException ("apiVersion must be positive");
        if (stateSchemaVersion <= 0)
            throw new IllegalArgumentException ("stateSchemaVersion must be positive");

        buildId = requireIdentifier (buildId, "buildId");
        stateSchema = requireIdentifier (stateSchema, "stateSchema");
        requiredCapabilities = Objects.requireNonNull (requiredCapabilities, "requiredCapabilities");
    }


    private static String requireIdentifier (final String value, final String name)
    {
        Objects.requireNonNull (value, name);
        if (value.isBlank ())
            throw new IllegalArgumentException (name + " must not be blank");
        return value;
    }
}
