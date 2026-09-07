// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.api.ControllerPageRef;
import de.mossgrabers.pull.core.api.ClipCatalogSnapshot;
import de.mossgrabers.pull.core.api.ShellCapabilities;
import java.util.Map;
import java.util.Set;
import de.mossgrabers.pull.core.api.ControllerSnapshot;
import de.mossgrabers.pull.core.api.LegacyControllerPageRequest;
import de.mossgrabers.pull.core.api.LegacyControllerPageRequests;
import de.mossgrabers.pull.core.api.ControllerTemporaryPage;
import de.mossgrabers.pull.core.api.DesiredControllerPageState;
import de.mossgrabers.pull.core.api.ParameterSlot;
import de.mossgrabers.pull.core.view.CompiledWorkspace;
import de.mossgrabers.pull.core.view.ResolvedControllerAction;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import static de.mossgrabers.pull.core.api.LegacyControllerPageRequest.Operation.*;
import static org.junit.jupiter.api.Assertions.*;

class PageNavigationTest
{
    private final PageNavigation pages = PageNavigation.defaults ();
    private final CompiledWorkspace dispatch = CompiledWorkspace.compile ("page-actions", List.of ());

    private static final ControllerSnapshot SNAPSHOT = new ControllerSnapshot (0, 0, new ShellCapabilities (Map.of ()), ClipCatalogSnapshot.empty (), Map.of (), Set.of (), Set.of ());

    PageNavigationTest () { this.dispatch.start (SNAPSHOT); }

    @Test
    void compatibilityBatchIsOrderedAndReplayDoesNotDispatchItTwice ()
    {
        final var inbox = inbox (request (1, 0, 0, SELECT, "DEVICE_PARAMS"), request (2, 0, 0, SET_PREVIOUS, "PAN"), request (3, 0, 0, RESTORE, ""));
        final List<ResolvedControllerAction> actions = this.pages.resolveLegacyActions (inbox);
        assertEquals (0, this.pages.state ().acknowledgedRequestSequence ());
        assertTrue (this.pages.resolveLegacyActions (inbox).isEmpty ());
        this.apply (actions);
        assertEquals (this.pages.resolve ("PAN"), this.pages.visible ());
        assertEquals (3, this.pages.state ().acknowledgedRequestSequence ());
        final long revision = this.pages.revision ();
        this.apply (actions);
        assertEquals (revision, this.pages.revision ());
        assertTrue (this.pages.resolveLegacyActions (inbox).isEmpty ());
    }

    @Test
    void sameOriginBatchMayArriveAcrossSuccessiveSnapshots ()
    {
        this.apply (this.pages.resolveLegacyActions (inbox (request (1, 0, 0, SELECT, "DEVICE_PARAMS"))));
        this.apply (this.pages.resolveLegacyActions (inbox (request (2, 0, 0, TEMPORARY, "BROWSER"), request (3, 0, 0, RESTORE, ""))));
        assertEquals (this.pages.resolve ("DEVICE_PARAMS"), this.pages.visible ());
        assertEquals (3, this.pages.state ().acknowledgedRequestSequence ());
    }

    @Test
    void interveningCoreNavigationCancelsAndAcknowledgesTheOldBatch ()
    {
        final var actions = this.pages.resolveLegacyActions (inbox (request (1, 0, 0, SELECT, "DEVICE_PARAMS"), request (2, 0, 0, TEMPORARY, "BROWSER")));
        this.pages.select (this.pages.resolve ("PAN"));
        this.apply (actions);
        assertEquals (this.pages.resolve ("PAN"), this.pages.visible ());
        assertEquals (2, this.pages.state ().acknowledgedRequestSequence ());
    }

    @Test
    void temporaryReplacementHasOneOwnerAndReturnsToThePersistentPage ()
    {
        this.pages.select (this.pages.resolve ("PAN"));
        final long accent = this.pages.temporary (this.pages.origin (), this.pages.resolve ("ACCENT"));
        final long frame = this.pages.temporary (this.pages.origin (), this.pages.resolve ("FRAME"));
        assertFalse (this.pages.releaseTemporary (accent));
        assertEquals (this.pages.resolve ("FRAME"), this.pages.visible ());
        assertTrue (this.pages.releaseTemporary (frame));
        assertEquals (this.pages.resolve ("PAN"), this.pages.visible ());
        assertFalse (this.pages.releaseTemporary (frame));
    }

    @Test
    void legacyDeviceAndBrowserReturnToAnArbitraryCorePageWithoutAnAlias ()
    {
        final ControllerPageRef newPage = ControllerPageRef.core ("new-looper-page");
        this.pages.select (newPage);
        final long origin = this.pages.revision ();
        this.apply (this.pages.resolveLegacyActions (inbox (request (1, origin, 0, SELECT, "DEVICE_PARAMS"))));
        this.apply (this.pages.resolveLegacyActions (inbox (request (2, this.pages.revision (), 0, TEMPORARY, "BROWSER"))));
        this.apply (this.pages.resolveLegacyActions (inbox (request (3, this.pages.revision (), this.pages.state ().temporaryToken (), RESTORE, ""))));
        this.apply (this.pages.resolveLegacyActions (inbox (new LegacyControllerPageRequest (4, this.pages.revision (), 0, SELECT_CAPTURED, "", newPage))));
        assertEquals (newPage, this.pages.visible ());
    }

    @Test
    void legacyDeviceMomentaryEntryAndCapturedReturnShareTheirOriginalBatch ()
    {
        final ControllerPageRef exact = ControllerPageRef.core ("new-looper-page");
        this.pages.select (exact);
        final long origin = this.pages.revision ();
        this.apply (this.pages.resolveLegacyActions (inbox (request (1, origin, 0, SELECT, "DEVICE_PARAMS"),
            new LegacyControllerPageRequest (2, origin, 0, SELECT_CAPTURED, "", exact))));
        assertEquals (exact, this.pages.visible ());
        assertEquals (2, this.pages.state ().acknowledgedRequestSequence ());
    }

    @Test
    void legacyTemporaryEntryAndReturnMayBothPrecedeTheFirstProjection ()
    {
        this.apply (this.pages.resolveLegacyActions (inbox (request (1, 0, 0, TEMPORARY, "BROWSER"), request (2, 0, 0, RESTORE, ""))));
        assertEquals (this.pages.resolve ("TRACK"), this.pages.visible ());
        assertTrue (this.pages.state ().temporary ().isEmpty ());
        assertEquals (2, this.pages.state ().acknowledgedRequestSequence ());
    }

    @Test
    void staleTemporaryReturnCannotDismissAReplacementWithTheSamePageName ()
    {
        final long oldToken = this.pages.temporary (this.pages.origin (), this.pages.resolve ("BROWSER"));
        final var restore = this.pages.resolveLegacyActions (inbox (request (1, this.pages.revision (), oldToken, RESTORE, "")));
        final long currentToken = this.pages.temporary (this.pages.origin (), this.pages.resolve ("BROWSER"));
        this.apply (restore);
        assertEquals (currentToken, this.pages.state ().temporaryToken ());
        assertEquals (1, this.pages.state ().acknowledgedRequestSequence ());
    }

    @Test
    void heldTemporaryEntryAndReleaseBeforeProjectionHaveOneExactOwner ()
    {
        this.apply (this.pages.resolveLegacyActions (inbox (
            request (1, 0, 0, BEGIN_TEMPORARY, "SCALE_LAYOUT"),
            new LegacyControllerPageRequest (2, 0, 0, END_TEMPORARY, "", ControllerPageRef.none (), 1))));
        assertEquals ("TRACK", this.pages.legacyAlias ());
    }

    @Test
    void delayedHeldReturnDoesNotDismissANewerTemporaryOwner ()
    {
        this.apply (this.pages.resolveLegacyActions (inbox (request (1, 0, 0, BEGIN_TEMPORARY, "SCALE_LAYOUT"))));
        final long newer = this.pages.temporary (this.pages.origin (), this.pages.resolve ("ACCENT"));
        this.apply (this.pages.resolveLegacyActions (inbox (new LegacyControllerPageRequest (2, 0, 0, END_TEMPORARY, "", ControllerPageRef.none (), 1))));
        assertEquals (newer, this.pages.state ().temporaryToken ());
    }

    @Test
    void cancelledReturnRetiresTheHandleButKeepsTheLatchedPage ()
    {
        this.apply (this.pages.resolveLegacyActions (inbox (request (1, 0, 0, BEGIN_TEMPORARY, "MASTER_TEMP"),
            new LegacyControllerPageRequest (2, 0, 0, CANCEL_TEMPORARY, "", ControllerPageRef.none (), 1))));
        assertEquals ("MASTER_TEMP", this.pages.legacyAlias ());
        this.apply (this.pages.resolveLegacyActions (inbox (new LegacyControllerPageRequest (3, 0, 0, END_TEMPORARY, "", ControllerPageRef.none (), 1))));
        assertEquals ("MASTER_TEMP", this.pages.legacyAlias ());
    }

    @Test
    void conditionalHeldEntrySeesEarlierQueuedScaleToggle ()
    {
        this.apply (this.pages.resolveLegacyActions (inbox (request (1, 0, 0, TOGGLE_TEMPORARY, "SCALES"),
            new LegacyControllerPageRequest (2, 0, 0, BEGIN_TEMPORARY, "SCALE_LAYOUT", ControllerPageRef.legacy ("SCALES")))));
        assertEquals ("SCALE_LAYOUT", this.pages.legacyAlias ());
        this.apply (this.pages.resolveLegacyActions (inbox (new LegacyControllerPageRequest (3, 0, 0, END_TEMPORARY, "", ControllerPageRef.none (), 2))));
        assertEquals ("TRACK", this.pages.legacyAlias ());
    }

    @Test
    void repeatedToggleRequestsBeforeProjectionOpenThenClose ()
    {
        this.apply (this.pages.resolveLegacyActions (inbox (request (1, 0, 0, TOGGLE_TEMPORARY, "SETUP"), request (2, 0, 0, TOGGLE_TEMPORARY, "SETUP"))));
        assertEquals ("TRACK", this.pages.legacyAlias ());
    }

    @Test
    void hydrationPromotesLegacySelectedAndTemporaryInfoWithoutChangingTheirOwnership ()
    {
        final var setup = ControllerPageRef.legacy ("SETUP");
        final var info = this.pages.resolve ("INFO");
        this.pages.restoreState (new DesiredControllerPageState (17, ControllerPageRef.legacy ("INFO"), setup,
            Optional.of (new ControllerTemporaryPage (29, ControllerPageRef.legacy ("INFO"))), 12, Set.of (ParameterSlot.TEMPO)));

        assertEquals (new DesiredControllerPageState (17, info, setup, Optional.of (new ControllerTemporaryPage (29, info)), 12, Set.of (ParameterSlot.TEMPO)), this.pages.state ());
        assertFalse (this.pages.releaseTemporary (28));
        assertTrue (this.pages.releaseTemporary (29));
        assertEquals (info, this.pages.visible ());
        assertTrue (this.pages.temporary (this.pages.origin (), setup) > 29, "hydration must not reuse a saved temporary owner token");
    }

    @Test
    void hydrationPromotesLegacyPreviousInfoSoSetupReturnsToItsCorePage ()
    {
        this.pages.restoreState (new DesiredControllerPageState (9, ControllerPageRef.legacy ("SETUP"), ControllerPageRef.legacy ("INFO"), Optional.empty (), 4));

        assertEquals (this.pages.resolve ("INFO"), this.pages.state ().previous ());
        this.apply (this.pages.resolveLegacyActions (inbox (request (5, 9, 0, RESTORE, ""))));
        assertEquals (this.pages.resolve ("INFO"), this.pages.visible ());
        assertEquals (5, this.pages.state ().acknowledgedRequestSequence ());
    }

    @Test
    void hydrationPreservesOpaqueCorePagesEvenWhenTheyCarryAMigratedLegacyAlias ()
    {
        final var saved = new DesiredControllerPageState (13, ControllerPageRef.core ("future-selected", "INFO"), ControllerPageRef.core ("future-previous", "INFO"),
            Optional.of (new ControllerTemporaryPage (21, ControllerPageRef.core ("future-temporary", "INFO"))), 7, Set.of (ParameterSlot.TEMPO));
        this.pages.restoreState (saved);

        assertEquals (saved, this.pages.state (), "an alias must not rewrite a core page's exact identity");
    }

    private void apply (final List<ResolvedControllerAction> actions)
    {
        for (final var action: actions) this.dispatch.dispatchAction (action, SNAPSHOT);
    }
    private static LegacyControllerPageRequests inbox (final LegacyControllerPageRequest... requests) { return new LegacyControllerPageRequests (List.of (requests)); }
    private static LegacyControllerPageRequest request (final long sequence, final long revision, final long token, final LegacyControllerPageRequest.Operation operation, final String mode) { return new LegacyControllerPageRequest (sequence, revision, token, operation, mode); }
}
