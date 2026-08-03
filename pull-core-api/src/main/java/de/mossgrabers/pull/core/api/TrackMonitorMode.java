// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api;

/**
 * Host-independent selected-track monitoring mode.
 */
public enum TrackMonitorMode
{
    /** Monitoring is disabled. */
    OFF,

    /** Monitoring follows the host's automatic policy. */
    AUTO,

    /** Monitoring is always enabled. */
    ON
}
