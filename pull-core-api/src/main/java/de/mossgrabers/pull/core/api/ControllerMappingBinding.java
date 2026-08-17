// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api;

import java.util.Objects;


/**
 * One active projection from a physical controller input to a permanent semantic Bitwig mapping
 * endpoint.
 *
 * @param physicalControl Physical input that currently drives the endpoint
 * @param mappingId Stable semantic endpoint identity
 */
public record ControllerMappingBinding (ControlId physicalControl, ControllerMappingId mappingId)
{
    /** Validate the complete projection. */
    public ControllerMappingBinding
    {
        physicalControl = Objects.requireNonNull (physicalControl, "physicalControl");
        mappingId = Objects.requireNonNull (mappingId, "mappingId");
    }
}
