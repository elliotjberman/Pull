// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.api.ControllerPageRef;
import de.mossgrabers.pull.core.api.ClipCatalogSnapshot;
import de.mossgrabers.pull.core.api.ShellCapabilities;
import java.util.Map;
import java.util.Set;
import de.mossgrabers.pull.core.api.ControllerSnapshot;
import de.mossgrabers.pull.core.api.DesiredControllerPageState;
import de.mossgrabers.pull.core.api.LegacyControllerPageRequest;
import de.mossgrabers.pull.core.api.LegacyControllerPageRequests;
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
    void bootstrapRequestsUseTheEmptyShellProjectionOrigin ()
    {
        assertEquals (0, this.pages.revision ());
        assertEquals (ControllerPageRef.core ("track", "TRACK"), this.pages.visible ());
        this.apply (this.pages.resolveLegacyActions (inbox (request (1, 0, 0, SELECT, "DEVICE_PARAMS"))));
        assertEquals (ControllerPageRef.legacy ("DEVICE_PARAMS"), this.pages.visible ());
        assertEquals (1, this.pages.state ().acknowledgedRequestSequence ());
    }

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
    void anOutOfOrderDispatchCannotAcknowledgeAnUndispatchedPrefix ()
    {
        final var actions = this.pages.resolveLegacyActions (inbox (request (1, 0, 0, SELECT, "DEVICE_PARAMS"), request (2, 0, 0, SELECT, "PAN")));
        assertThrows (IllegalStateException.class, () -> this.apply (List.of (actions.get (1))));
        assertEquals (0, this.pages.state ().acknowledgedRequestSequence ());
        this.apply (actions);
        assertEquals (2, this.pages.state ().acknowledgedRequestSequence ());
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
    void workspaceChangeInvalidatesOldOriginsEvenWhenThePageIsUnchanged ()
    {
        final var before = this.pages.origin ();
        this.pages.workspaceChanged (1, this.pages.visible ());
        assertFalse (this.pages.select (before, this.pages.resolve ("PAN")));
        assertEquals (0, this.pages.temporary (before, this.pages.resolve ("FRAME")));
        assertFalse (this.pages.restore (before));
    }

    @Test
    void checkpointRestorationPreservesReferencesAndAdvancesTemporaryTokens ()
    {
        final var newPage = ControllerPageRef.core ("new-looper-page");
        this.pages.select (newPage);
        this.pages.temporary (this.pages.origin (), this.pages.resolve ("TRANSPORT"));
        final var checkpoint = this.pages.state ();
        final var replacement = PageNavigation.defaults ();
        replacement.restoreState (checkpoint);
        assertEquals (checkpoint, replacement.state ());
        assertTrue (replacement.temporary (replacement.origin (), replacement.resolve ("FRAME")) > checkpoint.temporaryToken ());
        replacement.releaseTemporary (replacement.state ().temporaryToken ());
        assertEquals (newPage, replacement.visible ());
    }

    @Test
    void hydrationRetiresPreviouslyResolvedCompatibilityClosures ()
    {
        final var old = this.pages.resolveLegacyActions (inbox (request (1, 0, 0, SELECT, "DEVICE_PARAMS")));
        this.pages.restoreState (new DesiredControllerPageState (0, this.pages.resolve ("PAN"), ControllerPageRef.none (), Optional.empty (), 0));
        this.apply (old);
        assertEquals (this.pages.resolve ("PAN"), this.pages.visible ());
        assertEquals (0, this.pages.state ().acknowledgedRequestSequence ());
        this.apply (this.pages.resolveLegacyActions (inbox (request (1, 0, 0, SELECT, "DEVICE_PARAMS"))));
        assertEquals (this.pages.resolve ("DEVICE_PARAMS"), this.pages.visible ());
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

    private void apply (final List<ResolvedControllerAction> actions)
    {
        for (final var action: actions) this.dispatch.dispatchAction (action, SNAPSHOT);
    }
    private static LegacyControllerPageRequests inbox (final LegacyControllerPageRequest... requests) { return new LegacyControllerPageRequests (List.of (requests)); }
    private static LegacyControllerPageRequest request (final long sequence, final long revision, final long token, final LegacyControllerPageRequest.Operation operation, final String mode) { return new LegacyControllerPageRequest (sequence, revision, token, operation, mode); }
}
