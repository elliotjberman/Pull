// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.ui.page;

import de.mossgrabers.pull.core.api.DevicePageState;
import de.mossgrabers.pull.core.api.DevicePageState.*;
import de.mossgrabers.pull.core.testing.DevicePageGallery;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DevicePageRendererTest
{
    @Test
    void observedBankMismatchClearsParametersAndFooterWithoutChangingTheUpAffordance ()
    {
        final var example = DevicePageGallery.examples ().stream ().filter (item -> item.id ().equals ("device-bank-unavailable")).findFirst ().orElseThrow ();
        final var page = DevicePageProjector.project (example.state ());
        assertTrue (page.controls ().isEmpty ()); assertTrue (page.footer ().cells ().isEmpty ());
        assertEquals ("Up", page.upper ().get (7).label ());
        assertTrue (page.message ().contains ("Waiting"));
    }

    @Test
    void knobEightTouchAndShiftExposeTheExistingFourthSendWithoutOptimisticParameterValues ()
    {
        final var original = DevicePageGallery.sample (Kind.LAYER_SEND);
        assertEquals ("Up", DevicePageProjector.project (original).upper ().get (7).label ());
        final var touch = new Selection (false, false, true, true, true, 8, 3, 0, false, true, false, 0, "");
        final var touched = new DevicePageState (original.kind (), original.device (), original.channels (), original.selectedChannel (), original.parameters (), original.sends (), touch);
        final var page = DevicePageProjector.project (touched);
        assertEquals ("Echo", page.upper ().get (7).label ()); assertTrue (page.upper ().get (7).selected ());
        assertEquals (original.parameters ().get (0).value (), page.controls ().get (0).value ());
    }

    @Test
    void detailFlagsRequireTheCursorAndFrozenActionTargetToAgree ()
    {
        final var original = DevicePageGallery.sample (Kind.TRACK_DETAILS);
        final var target = new Channel (true, original.selection ().actionTargetId (), "Observed owner", "AUDIO", original.selectedChannel ().color (), false, false, true, false, false, true, false, false, 0, 0);
        final var state = new DevicePageState (Kind.TRACK_DETAILS, Device.empty (), original.channels (), target, List.of (), List.of (), original.selection ());
        final var page = DevicePageProjector.project (state);
        assertEquals ("Audio Track: Observed owner", page.heading ());
        assertFalse (page.lower ().get (0).selected ()); assertTrue (page.lower ().get (2).selected ());
        assertTrue (page.lower ().get (4).selected ()); assertTrue (page.footer ().cells ().isEmpty ());
        final var mismatch = DevicePageGallery.examples ().stream ().filter (example -> example.id ().equals ("track-details-target-mismatch")).findFirst ().orElseThrow ();
        final var hidden = DevicePageProjector.project (mismatch.state ());
        assertTrue (hidden.heading ().isEmpty ()); assertTrue (hidden.toggles ().isEmpty ());
        assertTrue (hidden.lower ().stream ().allMatch (choice -> choice.label ().isEmpty ()));
        assertEquals ("Waiting for track target...", hidden.message ());
    }

    @Test
    void deviceBankAndSiblingRowsRemainDistinct ()
    {
        final var original = DevicePageGallery.sample (Kind.PARAMETERS);
        final var siblings = DevicePageProjector.project (original);
        assertEquals ("Polymer", siblings.upper ().get (1).label ()); assertEquals (7, siblings.footer ().cells ().size ());
        final var bank = DevicePageGallery.examples ().stream ().filter (item -> item.id ().equals ("device-parameter-banks")).findFirst ().orElseThrow ();
        final var page = DevicePageProjector.project (bank.state ());
        assertEquals ("Pin Device", page.upper ().get (5).label ()); assertEquals ("Envelope", page.lower ().get (2).label ());
        assertTrue (page.lower ().get (2).selected ()); assertTrue (page.footer ().cells ().isEmpty ());
    }
}
