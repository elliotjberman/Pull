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
 * @param value Absolute value emitted by the next matching physical press
 * @param context Observed selected target and document-storage ownership
 */
public record ControllerMappingBinding (ControlId physicalControl, ControllerMappingId mappingId, ControllerMappingValue value, ControllerMappingContext context)
{
    /** Create a maximum-value binding. */
    public ControllerMappingBinding (final ControlId physicalControl, final ControllerMappingId mappingId)
    {
        this (physicalControl, mappingId, ControllerMappingValue.MAXIMUM, ControllerMappingContext.empty ());
    }


    /** Create an ownerless compatibility binding. */
    public ControllerMappingBinding (final ControlId physicalControl, final ControllerMappingId mappingId, final ControllerMappingValue value)
    {
        this (physicalControl, mappingId, value, ControllerMappingContext.empty ());
    }


    /** Validate the complete projection. */
    public ControllerMappingBinding
    {
        physicalControl = Objects.requireNonNull (physicalControl, "physicalControl");
        mappingId = Objects.requireNonNull (mappingId, "mappingId");
        value = Objects.requireNonNull (value, "value");
        context = Objects.requireNonNull (context, "context");
    }
}
