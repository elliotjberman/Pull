// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api;

import java.util.Objects;

/**
 * Common bounded state installed in the stable controller bridge.
 *
 * @param transport Transport state
 * @param selectedTrack Private selection-following track state
 * @param layout Visible layout and reconciled applicability state
 * @param drum Selected-track drum window
 */
public record ControllerBridgeSnapshot (TransportSnapshot transport, SelectedTrackSnapshot selectedTrack, ControllerLayoutSnapshot layout, DrumContextSnapshot drum)
{
    private static final ControllerBridgeSnapshot EMPTY = new ControllerBridgeSnapshot (TransportSnapshot.empty (), SelectedTrackSnapshot.empty (), ControllerLayoutSnapshot.empty (), DrumContextSnapshot.empty ());


    /**
     * Validate the bridge snapshot.
     */
    public ControllerBridgeSnapshot
    {
        transport = Objects.requireNonNull (transport, "transport");
        selectedTrack = Objects.requireNonNull (selectedTrack, "selectedTrack");
        layout = Objects.requireNonNull (layout, "layout");
        drum = Objects.requireNonNull (drum, "drum");
    }


    /**
     * Get unavailable common bridge state.
     *
     * @return Empty bridge state
     */
    public static ControllerBridgeSnapshot empty ()
    {
        return EMPTY;
    }
}
