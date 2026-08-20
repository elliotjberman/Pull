// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api;


/**
 * Bounded controller-state scopes which semantic actions may invalidate.
 */
public enum ControllerStateScope
{
    /** Parameter bindings selected by the active controller context. */
    ACTIVE_PARAMETERS,
    /** Playback state in the active bounded Session window. */
    SESSION_PLAYBACK
}
