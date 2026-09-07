// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.runtime;

import de.mossgrabers.pull.core.api.BridgeSubscription;
import de.mossgrabers.pull.core.api.ClipCatalogSnapshot;
import de.mossgrabers.pull.core.api.ControllerBridgeSnapshot;
import de.mossgrabers.pull.core.api.ControllerPageRef;
import de.mossgrabers.pull.core.api.ControllerSnapshot;
import de.mossgrabers.pull.core.api.ControllerTemporaryPage;
import de.mossgrabers.pull.core.api.DesiredControllerPageState;
import de.mossgrabers.pull.core.api.LegacyControllerPageRequest;
import de.mossgrabers.pull.core.api.LegacyControllerPageRequests;
import de.mossgrabers.pull.core.api.event.ControllerTickEvent;
import de.mossgrabers.pull.core.api.output.DisplayCommand;
import de.mossgrabers.pull.core.runtime.view.LegacyPageAliases;
import de.mossgrabers.pull.core.runtime.view.WorkspaceSelection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Checkpoint hydration must keep navigation usable as legacy pages migrate into core. */
class ControllerCheckpointNavigationTest
{
    @Test
    void savedLegacyInfoBehindSetupReturnsToTheCurrentCorePageAfterHydration ()
    {
        final var savedPage = new DesiredControllerPageState (17, ControllerPageRef.legacy ("INFO"), LegacyPageAliases.resolve ("TRACK"),
            Optional.of (new ControllerTemporaryPage (29, ControllerPageRef.legacy ("SETUP"))), 12);
        final var saved = new ControllerCheckpoint (WorkspaceSelection.Id.DEFAULT, WorkspaceSelection.Destination.NONE, WorkspaceSelection.Destination.NONE,
            "", false, false, 0, Optional.of (savedPage)).encode ();
        final var core = new PullCoreProvider ().create ();
        final var started = core.start (snapshot (0, LegacyControllerPageRequests.empty ()), Optional.of (saved));

        assertEquals (LegacyPageAliases.resolve ("INFO"), started.desiredControllerState ().page ().selected ());
        assertEquals (LegacyPageAliases.resolve ("SETUP"), started.desiredControllerState ().page ().effectivePage ());
        assertEquals (29, started.desiredControllerState ().page ().temporaryToken ());
        final var requests = new LegacyControllerPageRequests (List.of (new LegacyControllerPageRequest (1, 17, 29, LegacyControllerPageRequest.Operation.RESTORE, "")));
        final var returned = core.handle (new ControllerTickEvent (1, 1), snapshot (1, requests));

        assertEquals (LegacyPageAliases.resolve ("INFO"), returned.desiredControllerState ().page ().effectivePage ());
        assertTrue (returned.desiredControllerState ().page ().temporary ().isEmpty ());
        assertTrue (returned.desiredBridgeSubscriptions ().includes (BridgeSubscription.CONTROLLER_HARDWARE));
        assertTrue (returned.desiredOutput ().display ().commands ().stream ().anyMatch (command -> command instanceof final DisplayCommand.TextBox text && "Info".equals (text.text ())));
        assertEquals (LegacyPageAliases.resolve ("INFO"), ControllerCheckpoint.decode (Optional.of (core.checkpoint ())).page ().orElseThrow ().selected ());
    }

    private static ControllerSnapshot snapshot (final long revision, final LegacyControllerPageRequests requests)
    {
        final var empty = ControllerBridgeSnapshot.empty ();
        final var bridge = new ControllerBridgeSnapshot (empty.transport (), empty.selectedTrack (), empty.sessionBank (), empty.layout (), empty.noteView (), empty.noteRepeat (), empty.drum (), empty.parameters (), empty.controllerMappingFeedback (), empty.master (), empty.project (), empty.automation (), empty.encoderConfiguration (), empty.currentTrackBank (), empty.transportSettings (), empty.controllerSettings (), empty.applicationUi (), requests, empty.browser (), empty.controllerHardware ());
        return new ControllerSnapshot (revision, revision, new PullCoreProvider ().descriptor ().requiredCapabilities (), bridge, ClipCatalogSnapshot.empty (), Map.of (), Map.of (), Optional.empty (), Set.of (), Set.of ());
    }
}
