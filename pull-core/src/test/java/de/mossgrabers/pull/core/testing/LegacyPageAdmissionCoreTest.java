// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.testing;

import de.mossgrabers.pull.core.api.*;
import de.mossgrabers.pull.core.runtime.PullCoreProvider;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import static de.mossgrabers.pull.core.api.LegacyControllerPageRequest.Operation.*;
import static org.junit.jupiter.api.Assertions.*;

/** Exercises compatibility request admission through the real core's parameter barrier. */
class LegacyPageAdmissionCoreTest
{
    private static final ParameterTargetRef TARGET = new ParameterTargetRef (ParameterTargetKind.LIVE, "legacy-page-tempo", 1);

    @Test
    void ordinaryShiftDoesNotRestoreParametersForItsRejectedScaleLayoutRequest ()
    {
        final var host = capturedShift ();
        final var page = host.effects ().desiredControllerPage ();
        final var request = new LegacyControllerPageRequest (1, page.revision (), page.temporaryToken (), BEGIN_TEMPORARY, "SCALE_LAYOUT", ControllerPageRef.legacy ("SCALES"));
        host.bridge (bridge (40, List.of (request)));
        assertEquals ("TRACK", host.effects ().desiredControllerPage ().effectivePage ().legacyAlias ());
        assertEquals (1, host.effects ().desiredControllerPage ().acknowledgedRequestSequence ());
        assertEquals (0, host.effects ().desiredParameterInteraction ().pendingActionCount ());
        assertTrue (host.effects ().desiredParameterInteraction ().acceptsMutations ());
        assertEquals (Map.of (TARGET, 100.0), host.effects ().desiredParameterInteraction ().baselines ());
        host.controllerTick ();
        host.controllerTick ();
        assertTrue (host.effects ().desiredParameterInteraction ().acceptsMutations ());
    }

    @Test
    void conditionalLayoutWaitsBehindTheScalesEntryWhichMakesItApplicable ()
    {
        final var host = capturedShift ();
        final var page = host.effects ().desiredControllerPage ();
        final var requests = List.of (
            new LegacyControllerPageRequest (1, page.revision (), page.temporaryToken (), TOGGLE_TEMPORARY, "SCALES"),
            new LegacyControllerPageRequest (2, page.revision (), page.temporaryToken (), BEGIN_TEMPORARY, "SCALE_LAYOUT", ControllerPageRef.legacy ("SCALES")));
        host.bridge (bridge (40, requests));
        assertEquals ("TRACK", host.effects ().desiredControllerPage ().effectivePage ().legacyAlias ());
        assertEquals (0, host.effects ().desiredControllerPage ().acknowledgedRequestSequence ());
        assertEquals (2, host.effects ().desiredParameterInteraction ().pendingActionCount ());
        host.controllerTick ();
        host.controllerTick ();
        host.bridge (bridge (100, requests));
        host.controllerTick ();
        host.controllerTick ();
        assertEquals ("SCALE_LAYOUT", host.effects ().desiredControllerPage ().effectivePage ().legacyAlias ());
        assertEquals (2, host.effects ().desiredControllerPage ().acknowledgedRequestSequence ());
        assertEquals (0, host.effects ().desiredParameterInteraction ().pendingActionCount ());
    }

    private static FakeCoreHost capturedShift ()
    {
        final var provider = new PullCoreProvider ();
        final var host = new FakeCoreHost (provider.create (), provider.descriptor ().requiredCapabilities ());
        host.initialBridge (bridge (100, List.of ()));
        host.start (Optional.empty ());
        host.controllerButton (PushControlIds.button ("SHIFT"), true);
        host.parameterMutation (PushControlIds.continuous ("TEMPO"), new ParameterTargetSnapshot (TARGET, 100, 0));
        return host;
    }

    private static ControllerBridgeSnapshot bridge (final double value, final List<LegacyControllerPageRequest> requests)
    {
        final var e = ControllerBridgeSnapshot.empty ();
        return new ControllerBridgeSnapshot (e.transport (), e.selectedTrack (), e.sessionBank (), e.layout (), e.noteView (), e.noteRepeat (), e.drum (), new ParameterBridgeSnapshot (Map.of (ParameterSlot.TEMPO, new ParameterTargetSnapshot (TARGET, value, 0)), Map.of ()), e.controllerMappingFeedback (), e.master (), e.project (), e.automation (), e.encoderConfiguration (), e.currentTrackBank (), e.transportSettings (), e.controllerSettings (), e.applicationUi (), new LegacyControllerPageRequests (requests), e.browser ());
    }
}
