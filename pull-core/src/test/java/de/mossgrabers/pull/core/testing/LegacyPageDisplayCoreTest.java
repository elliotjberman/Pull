// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.testing;

import de.mossgrabers.pull.core.api.*;
import de.mossgrabers.pull.core.api.event.InputKind;
import de.mossgrabers.pull.core.api.output.DisplayCommand;
import de.mossgrabers.pull.core.api.output.RgbColor;
import de.mossgrabers.pull.core.runtime.PullCoreProvider;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

class LegacyPageDisplayCoreTest
{
    @Test
    void realPageRoutingUsesObservedDataAndBlanksAnOldPageUntilItsReplacementIsObserved ()
    {
        final var provider = new PullCoreProvider ();
        final var host = new FakeCoreHost (provider.create (), provider.descriptor ().requiredCapabilities ());
        host.initialBridge (bridge ("FIXED", new OptionPageState.FixedLength (2)));
        host.start (Optional.empty ());
        assertFalse (host.effects ().desiredBridgeSubscriptions ().includes (BridgeSubscription.CONTROLLER_PAGE_DISPLAY));
        host.requestPage (LegacyControllerPageRequest.Operation.SELECT, "FIXED");
        assertTrue (host.effects ().desiredBridgeSubscriptions ().includes (BridgeSubscription.CONTROLLER_PAGE_DISPLAY));
        assertTrue (text (host).contains ("New Clip Length"));
        host.requestPage (LegacyControllerPageRequest.Operation.SELECT, "GROOVE");
        assertTrue (text (host).isEmpty (), "the previous page cannot draw under the newly selected page");
        host.bridge (bridge ("GROOVE", new EditingPageState.Groove (true, List.of ())));
        assertTrue (text (host).contains ("Groove"));
        assertFalse (text (host).contains ("New Clip Length"));
        host.bridge (bridge ("GROOVE", EditingPageState.empty ()));
        assertTrue (text (host).isEmpty (), "missing observations cannot restore the removed shell rendering");
        host.requestPage (LegacyControllerPageRequest.Operation.SELECT, "TRACK");
        assertFalse (host.effects ().desiredBridgeSubscriptions ().includes (BridgeSubscription.CONTROLLER_PAGE_DISPLAY), "inactive domains stop sampling");
    }

    @Test
    void deferredPianoRollReleasesOnlyTheDisplayAndKeepsObservingItsReturnToClipControls ()
    {
        final var provider = new PullCoreProvider ();
        final var host = new FakeCoreHost (provider.create (), provider.descriptor ().requiredCapabilities ());
        host.initialBridge (sessionBridge ("CLIP", clip (false)));
        host.start (Optional.empty ());
        host.requestPage (LegacyControllerPageRequest.Operation.SELECT, "CLIP");
        assertTrue (text (host).contains ("Play Start"));
        final var routes = host.effects ().desiredInputRoutes ().routes ();
        final var lights = host.effects ().desiredOutput ().lights ();
        final var workspace = host.effects ().desiredControllerWorkspace ();
        assertEquals (Optional.of (InputRouteMode.EXCLUSIVE), host.effects ().desiredInputRoutes ().mode (PushControlIds.pad (57), InputKind.PAD));
        assertEquals (new RgbColor (62, 160, 255), lights.get (PushControlIds.pad (57)), "the observed Session clip remains visible beneath the independent page");

        host.bridge (sessionBridge ("CLIP", clip (true)));
        for (int tick = 0; tick < 3; tick++)
        {
            assertFalse (host.effects ().desiredOutput ().display ().isPresent (), "only the explicitly deferred piano roll leaves its display unclaimed");
            assertEquals (routes, host.effects ().desiredInputRoutes ().routes ());
            assertEquals (lights, host.effects ().desiredOutput ().lights ());
            assertEquals (workspace.sessionBankShape (), host.effects ().desiredControllerWorkspace ().sessionBankShape ());
            assertEquals (workspace.facets (), host.effects ().desiredControllerWorkspace ().facets ());
            assertTrue (host.effects ().desiredBridgeSubscriptions ().includes (BridgeSubscription.CONTROLLER_PAGE_DISPLAY), "the toggle must continue receiving host read-back while its display is deferred");
            host.controllerTick ();
        }

        host.bridge (sessionBridge ("CLIP", clip (false)));
        assertTrue (host.effects ().desiredOutput ().display ().isPresent ());
        assertTrue (text (host).contains ("Play Start"));
        assertEquals (routes, host.effects ().desiredInputRoutes ().routes ());
        assertEquals (lights, host.effects ().desiredOutput ().lights ());
    }

    @Test
    void missingOrMismatchedPianoRollObservationsNeverReleaseTheCoreDisplay ()
    {
        final var provider = new PullCoreProvider ();
        final var host = new FakeCoreHost (provider.create (), provider.descriptor ().requiredCapabilities ());
        host.initialBridge (bridge ("GROOVE", clip (true)));
        host.start (Optional.empty ());
        host.requestPage (LegacyControllerPageRequest.Operation.SELECT, "CLIP");
        assertBlankOwnedDisplay (host);

        host.bridge (bridge ("CLIP", EditingPageState.empty ()));
        assertBlankOwnedDisplay (host);

        host.requestPage (LegacyControllerPageRequest.Operation.SELECT, "GROOVE");
        host.bridge (bridge ("CLIP", clip (true)));
        assertBlankOwnedDisplay (host);
    }

    private static void assertBlankOwnedDisplay (final FakeCoreHost host)
    {
        assertTrue (host.effects ().desiredOutput ().display ().isPresent (), "a mismatch must be black, not a handoff to legacy drawing");
        assertTrue (text (host).isEmpty ());
    }

    private static EditingPageState.Clip clip (final boolean pianoRoll)
    {
        return new EditingPageState.Clip (true, false, 0, 4, 0, 4, true, false, 100, 4, List.of (), Collections.nCopies (8, false), pianoRoll);
    }

    private static ControllerBridgeSnapshot sessionBridge (final String mode, final ControllerPageDisplayState state)
    {
        final var e = bridge (mode, state);
        final var color = new RgbColor (62, 160, 255);
        final var shape = new SessionBankShape (8, 8);
        final var tracks = new ArrayList<> (Collections.nCopies (8, SessionTrackSnapshot.empty ()));
        tracks.set (0, new SessionTrackSnapshot ("track-1", 0, "Drums", true, true, true, false, false, false, false, SessionTrackType.INSTRUMENT, color));
        final var slots = new ArrayList<> (Collections.nCopies (64, SessionSlotSnapshot.empty ()));
        slots.set (0, new SessionSlotSnapshot (true, 0, "Observed clip", true, false, false, false, false, false, false, false, color));
        final var clips = new SessionClipWindowSnapshot (shape, true, slots, Collections.nCopies (8, SessionSceneSnapshot.empty ()), BankNavigationSnapshot.empty (), BankNavigationSnapshot.empty ());
        final var bank = new SessionBankSnapshot (1, shape, 0, 0, tracks, clips);
        final var layout = new ControllerLayoutSnapshot (1, "SESSION", "CLIP", false, false, 0, GridPressureConfiguration.OFF);
        final var project = new ProjectSnapshot (true, "project-1", "Session", true, false, false, false);
        return new ControllerBridgeSnapshot (e.transport (), e.selectedTrack (), bank, layout, e.noteView (), e.noteRepeat (), e.drum (), e.parameters (), e.controllerMappingFeedback (), e.master (), project, e.automation (), e.encoderConfiguration (), e.currentTrackBank (), e.transportSettings (), e.controllerSettings (), e.applicationUi (), e.controllerPages (), e.browser (), e.controllerHardware (), e.pageDisplay ());
    }

    private static List<String> text (final FakeCoreHost host)
    {
        return host.effects ().desiredOutput ().display ().commands ().stream ().filter (DisplayCommand.TextBox.class::isInstance).map (DisplayCommand.TextBox.class::cast).map (DisplayCommand.TextBox::text).toList ();
    }
    private static ControllerBridgeSnapshot bridge (final String mode, final ControllerPageDisplayState state)
    {
        final var e = ControllerBridgeSnapshot.empty ();
        return new ControllerBridgeSnapshot (e.transport (), e.selectedTrack (), e.sessionBank (), e.layout (), e.noteView (), e.noteRepeat (), e.drum (), e.parameters (), e.controllerMappingFeedback (), e.master (), e.project (), e.automation (), e.encoderConfiguration (), e.currentTrackBank (), e.transportSettings (), e.controllerSettings (), e.applicationUi (), e.controllerPages (), e.browser (), e.controllerHardware (), new ControllerPageDisplaySnapshot (mode, state));
    }
}
