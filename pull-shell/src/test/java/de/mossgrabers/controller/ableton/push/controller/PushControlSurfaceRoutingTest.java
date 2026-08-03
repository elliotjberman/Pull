// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.controller.ableton.push.controller;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;


/**
 * Tests drum capability, layout ownership and legacy Session ribbon policy as separate state.
 */
class PushControlSurfaceRoutingTest
{
    @Test
    void targetApplicabilityRequiresAllAuthoritativeCapabilities ()
    {
        assertFalse (PushControlSurface.isDrumTargetCapable (false, false, false));
        assertFalse (PushControlSurface.isDrumTargetCapable (true, false, true));
        assertFalse (PushControlSurface.isDrumTargetCapable (true, true, false));
        assertTrue (PushControlSurface.isDrumTargetCapable (true, true, true));
    }


    @Test
    void modelMustRepresentTheSameTrackAsTheDirectNoteTarget ()
    {
        assertFalse (PushControlSurface.isDrumModelAligned (false, "track-a", "track-a"));
        assertFalse (PushControlSurface.isDrumModelAligned (true, "", ""));
        assertFalse (PushControlSurface.isDrumModelAligned (true, null, null));
        assertFalse (PushControlSurface.isDrumModelAligned (true, "track-a", "track-b"));
        assertTrue (PushControlSurface.isDrumModelAligned (true, "track-a", "track-a"));

        assertFalse (PushControlSurface.isDrumControllerApplicable (false, false, false));
        assertFalse (PushControlSurface.isDrumControllerApplicable (true, false, true));
        assertFalse (PushControlSurface.isDrumControllerApplicable (true, true, false));
        assertTrue (PushControlSurface.isDrumControllerApplicable (true, true, true));
    }


    @Test
    void newDrumGesturesRequireBothLayoutOwnershipAndReconciledEngagement ()
    {
        assertFalse (PushControlSurface.isDrumControllerActive (false, false));
        assertFalse (PushControlSurface.isDrumControllerActive (true, false));
        assertFalse (PushControlSurface.isDrumControllerActive (false, true));
        assertTrue (PushControlSurface.isDrumControllerActive (true, true));
    }


    @Test
    void sessionRetainsItsExplicitRawPitchbendPolicy ()
    {
        assertFalse (PushControlSurface.isRawPitchbendRoutingActive (false, false));
        assertTrue (PushControlSurface.isRawPitchbendRoutingActive (true, false));
        assertTrue (PushControlSurface.isRawPitchbendRoutingActive (false, true));
        assertTrue (PushControlSurface.isRawPitchbendRoutingActive (true, true));

        assertFalse (PushControlSurface.shouldRouteRawPitchbend (false, false));
        assertTrue (PushControlSurface.shouldRouteRawPitchbend (true, false));
        assertTrue (PushControlSurface.shouldRouteRawPitchbend (false, true));
        assertTrue (PushControlSurface.shouldRouteRawPitchbend (true, true));
    }
}
