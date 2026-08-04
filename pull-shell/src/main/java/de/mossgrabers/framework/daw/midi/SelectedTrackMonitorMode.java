// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.framework.daw.midi;


/**
 * Monitor mode of the private selection-following track target.
 */
public enum SelectedTrackMonitorMode
{
    /** Monitoring is disabled. */
    OFF,

    /** Monitoring is enabled. */
    ON,

    /** Monitoring follows Bitwig's automatic policy. */
    AUTO
}
