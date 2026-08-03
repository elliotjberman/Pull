// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api;

/**
 * Stable-shell disposition for a routed controller input.
 */
public enum InputRouteMode
{
    /** Deliver the input to the core and continue legacy dispatch. */
    OBSERVE,

    /** Deliver the input to the core and suppress legacy dispatch for the gesture. */
    EXCLUSIVE
}
