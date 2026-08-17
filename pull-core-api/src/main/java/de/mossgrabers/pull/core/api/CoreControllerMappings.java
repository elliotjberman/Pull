// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api;

import java.util.List;


/** Stable semantic mapping endpoints installed by the parent shell. */
public final class CoreControllerMappings
{
    /** Four Drum Controller control endpoints in left-to-right view order. */
    public static final List<ControllerMappingId> DRUM_CONTROL_PADS = List.of (
        new ControllerMappingId ("drum-controller.control.1"),
        new ControllerMappingId ("drum-controller.control.2"),
        new ControllerMappingId ("drum-controller.control.3"),
        new ControllerMappingId ("drum-controller.control.4"));


    private CoreControllerMappings ()
    {
        // Utility class
    }
}
