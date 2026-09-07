// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.testing;

import de.mossgrabers.pull.core.api.*;
import de.mossgrabers.pull.core.runtime.PullCoreProvider;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

class BrowserPageCoreIntegrationTest
{
    private static final ParameterTargetRef TARGET = new ParameterTargetRef (ParameterTargetKind.LIVE, "browser-tempo", 1);

    @Test
    void freshCoreAdoptsAnAlreadyOpenBrowserFromItsInitialObservation ()
    {
        final var host = host ();
        host.initialBridge (bridge (1, true, 100));
        host.start (Optional.empty ());
        host.controllerTick ();
        assertEquals ("BROWSER", page (host));
        host.bridge (bridge (2, false, 100));
        assertEquals ("TRACK", page (host));
    }

    @Test
    void browserClosedWhileSnapbackRestoresCannotOpenAfterTheHostAcknowledgement ()
    {
        final var host = host ();
        host.initialBridge (bridge (1, false, 100));
        host.start (Optional.empty ());
        host.controllerButton (PushControlIds.button ("SHIFT"), true);
        host.parameterMutation (PushControlIds.continuous ("TEMPO"), new ParameterTargetSnapshot (TARGET, 100, 0));
        host.bridge (bridge (1, false, 40));
        host.controllerButton (PushControlIds.button ("SHIFT"), false);
        assertEquals (Map.of (TARGET, 100.0), host.effects ().desiredParameterInteraction ().baselines ());
        host.bridge (bridge (2, true, 40));
        assertEquals (1, host.effects ().desiredParameterInteraction ().pendingActionCount ());
        assertEquals ("TRACK", page (host));
        host.controllerTick ();
        host.controllerTick ();
        host.bridge (bridge (3, false, 40));
        assertEquals ("TRACK", page (host));
        // Only later authoritative parameter read-back releases the old Browser open action.
        host.bridge (bridge (3, false, 100));
        host.controllerTick ();
        host.controllerTick ();
        assertEquals (0, host.effects ().desiredParameterInteraction ().pendingActionCount ());
        assertEquals ("TRACK", page (host));
        assertTrue (host.effects ().desiredControllerPage ().temporary ().isEmpty ());
    }

    @Test
    void actualCoreCheckpointRestoresOpenBrowserReturnOwnership ()
    {
        final var before = host ();
        before.initialBridge (bridge (1, false, 100));
        before.start (Optional.empty ());
        before.bridge (bridge (2, true, 100));
        assertEquals ("BROWSER", page (before));
        final long token = before.effects ().desiredControllerPage ().temporaryToken ();
        final var after = host ();
        after.initialBridge (bridge (2, true, 100));
        after.start (Optional.of (before.checkpoint ()));
        after.controllerTick ();
        assertEquals (token, after.effects ().desiredControllerPage ().temporaryToken ());
        after.bridge (bridge (3, false, 100));
        assertEquals ("TRACK", page (after));
    }

    private static FakeCoreHost host () { final var provider = new PullCoreProvider (); return new FakeCoreHost (provider.create (), provider.descriptor ().requiredCapabilities ()); }
    private static String page (final FakeCoreHost host) { return host.effects ().desiredControllerPage ().effectivePage ().legacyAlias (); }
    private static ControllerBridgeSnapshot bridge (final long generation, final boolean active, final double value)
    {
        final var e = ControllerBridgeSnapshot.empty ();
        return new ControllerBridgeSnapshot (e.transport (), e.selectedTrack (), e.sessionBank (), e.layout (), e.noteView (), e.noteRepeat (), e.drum (), new ParameterBridgeSnapshot (Map.of (ParameterSlot.TEMPO, new ParameterTargetSnapshot (TARGET, value, 0)), Map.of (), java.util.Set.of ()), e.controllerMappingFeedback (), e.master (), e.project (), e.automation (), e.encoderConfiguration (), e.currentTrackBank (), e.transportSettings (), e.controllerSettings (), e.applicationUi (), e.controllerPages (), new BrowserSnapshot (generation, active));
    }
}
