// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api;


/**
 * Closed stable-shell parameter actuator domains.
 */
public enum ParameterTargetKind
{
    /** Global actuator whose identity is stable for the extension lifetime. */
    FIXED,
    /** Bounded live parameter actuator identified by one exact target generation. */
    LIVE
}
