// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.controller.ableton.push.controller;

import de.mossgrabers.framework.featuregroup.IMode;
import de.mossgrabers.framework.mode.Modes;
import de.mossgrabers.pull.core.api.*;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PushControllerPageManagerTest
{
    @Test
    void temporaryHandleFencesReplacementUntilItsOnceOnlyReturnIsAcknowledged ()
    {
        final var manager = manager ();
        final var handle = manager.beginTemporary (Modes.MASTER_TEMP);
        final var entry = manager.requests (true).requests ().getFirst ();
        assertEquals (LegacyControllerPageRequest.Operation.BEGIN_TEMPORARY, entry.operation ());
        manager.apply (new DesiredControllerPageState (1, ControllerPageRef.core ("track", "TRACK"), ControllerPageRef.none (), Optional.of (new ControllerTemporaryPage (7, ControllerPageRef.core ("master", "MASTER_TEMP"))), entry.sequence ()));
        assertFalse (manager.canReplaceCore (), "an acknowledged entry still has a physical owner");
        handle.close ();
        handle.close ();
        final var returns = manager.requests (true).requests ();
        assertEquals (1, returns.size ());
        assertEquals (entry.sequence (), returns.getFirst ().temporaryRequestSequence ());
        manager.apply (state (2, ControllerPageRef.core ("track", "TRACK"), ControllerPageRef.none (), returns.getFirst ().sequence ()));
        assertTrue (manager.canReplaceCore ());
    }

    @Test
    void temporaryHandlesFromFaultedGenerationsCannotEmitIntoTheReplacement ()
    {
        final var manager = manager ();
        final var handle = manager.beginTemporary (Modes.MASTER_TEMP);
        manager.invalidate ();
        manager.activateConsumer (2);
        handle.close ();
        assertTrue (manager.requests (true).requests ().isEmpty ());
        assertTrue (manager.canReplaceCore ());
    }

    @Test
    void requestsReplayWithoutChangingProjectionUntilTheCoreAcknowledgesAPrefix ()
    {
        final var manager = manager ();
        manager.apply (state (1, ControllerPageRef.core ("mixer", "TRACK"), ControllerPageRef.none (), 0));
        manager.setActive (Modes.DEVICE_PARAMS);
        manager.setTemporary (Modes.BROWSER);
        final var inbox = manager.requests (true);
        assertEquals (inbox, manager.requests (true));
        assertEquals (Modes.TRACK, manager.getActiveID ());
        assertFalse (manager.isIdle ());
        manager.apply (state (2, ControllerPageRef.legacy ("DEVICE_PARAMS"), ControllerPageRef.core ("mixer", "TRACK"), 1));
        assertEquals (List.of (inbox.requests ().get (1)), manager.requests (true).requests ());
        manager.apply (new DesiredControllerPageState (3, manager.pageState ().selected (), manager.pageState ().previous (), Optional.of (new ControllerTemporaryPage (7, ControllerPageRef.legacy ("BROWSER"))), 2));
        assertTrue (manager.isIdle ());
        assertEquals (Modes.BROWSER, manager.getActiveID ());
        assertEquals (Modes.DEVICE_PARAMS, manager.getActiveIDIgnoreTemporary ());
        assertEquals (Modes.TRACK, manager.getPreviousID ());
    }

    @Test
    void oldDeactivationRequestsKeepOldOriginWhileActivationUsesNewOrigin ()
    {
        final var manager = manager ();
        manager.register (Modes.BROWSER, mode (() -> { }, manager::restore));
        manager.register (Modes.DEVICE_PARAMS, mode (() -> manager.setPreviousID (Modes.TRACK), () -> { }));
        manager.apply (new DesiredControllerPageState (5, ControllerPageRef.core ("opaque"), ControllerPageRef.none (), Optional.of (new ControllerTemporaryPage (13, ControllerPageRef.legacy ("BROWSER"))), 0));
        manager.apply (state (6, ControllerPageRef.legacy ("DEVICE_PARAMS"), ControllerPageRef.core ("opaque"), 0));
        final var requests = manager.requests (true).requests ();
        assertEquals (2, requests.size ());
        assertEquals (5, requests.get (0).originPageRevision ());
        assertEquals (13, requests.get (0).originTemporaryToken ());
        assertEquals (6, requests.get (1).originPageRevision ());
        assertEquals (0, requests.get (1).originTemporaryToken ());
        assertEquals (Modes.DEVICE_PARAMS, manager.getActiveID ());
    }

    @Test
    void lifecycleQueriesSeeTheCompleteNewProjectionBeforeForeignCallbacks ()
    {
        final var manager = manager ();
        final List<String> observations = new ArrayList<> ();
        manager.register (Modes.BROWSER, mode (() -> { }, () -> observations.add (manager.pageState ().effectivePage ().id ())));
        manager.apply (state (1, ControllerPageRef.legacy ("BROWSER"), ControllerPageRef.none (), 0));
        manager.apply (state (2, ControllerPageRef.core ("new-page"), ControllerPageRef.legacy ("BROWSER"), 0));
        assertEquals (List.of ("new-page"), observations);
    }

    @Test
    void replayDoesNotChurnLegacyActivationOrGenericCoreActivation ()
    {
        final var manager = new PushControllerPageManager ();
        manager.activateConsumer (1);
        final AtomicInteger starts = new AtomicInteger ();
        final AtomicInteger stops = new AtomicInteger ();
        manager.installCoreAdapter (mode (starts::incrementAndGet, stops::incrementAndGet));
        manager.apply (state (1, ControllerPageRef.core ("a"), ControllerPageRef.none (), 0));
        manager.apply (manager.pageState ());
        manager.apply (state (2, ControllerPageRef.core ("b"), ControllerPageRef.core ("a"), 0));
        assertEquals (1, starts.get ());
        assertEquals (0, stops.get ());
    }

    @Test
    void capturedOpaquePageReturnDoesNotUseAnEnumAlias ()
    {
        final var manager = manager ();
        final var opaque = ControllerPageRef.core ("my-new-page");
        manager.apply (state (1, opaque, ControllerPageRef.none (), 0));
        final var captured = manager.captureSelectedPage ();
        manager.apply (state (2, ControllerPageRef.legacy ("DEVICE_PARAMS"), opaque, 0));
        manager.requestCapturedPage (captured);
        final var request = manager.requests (true).requests ().get (0);
        assertEquals (LegacyControllerPageRequest.Operation.SELECT_CAPTURED, request.operation ());
        assertEquals (opaque, request.capturedTarget ());
        assertEquals (2, request.originPageRevision ());
    }

    @Test
    void delayedLegacyCallbackRetainsItsSchedulingOrigin ()
    {
        final var manager = manager ();
        manager.apply (state (1, ControllerPageRef.legacy ("DEVICE_PARAMS"), ControllerPageRef.none (), 0));
        final Runnable delayed = manager.freezeRequestOrigin (() -> manager.setActive (Modes.BROWSER));
        manager.apply (state (2, ControllerPageRef.core ("newer"), ControllerPageRef.none (), 0));
        delayed.run ();
        assertEquals (1, manager.requests (true).requests ().get (0).originPageRevision ());
    }

    @Test
    void notificationRunsOnceAfterRequestAcknowledgementAndSeesTheCommittedPage ()
    {
        final var manager = manager ();
        final List<Modes> notified = new ArrayList<> ();
        manager.apply (state (1, ControllerPageRef.core ("core", "TRACK"), ControllerPageRef.none (), 0));
        manager.setActive (Modes.DEVICE_PARAMS);
        manager.afterPageRequest (() -> notified.add (manager.getActiveID ()));
        assertTrue (notified.isEmpty ());
        final var next = state (2, ControllerPageRef.legacy ("DEVICE_PARAMS"), manager.pageState ().effectivePage (), 1);
        manager.apply (next);
        manager.apply (next);
        assertEquals (List.of (Modes.DEVICE_PARAMS), notified);
    }

    @Test
    void nestedProjectionRetainsTheLatestCompleteState ()
    {
        final var manager = manager ();
        manager.register (Modes.BROWSER, mode (() -> { }, () -> manager.apply (state (3, ControllerPageRef.core ("nested"), ControllerPageRef.none (), 0))));
        manager.apply (state (1, ControllerPageRef.legacy ("BROWSER"), ControllerPageRef.none (), 0));
        manager.apply (state (2, ControllerPageRef.legacy ("DEVICE_PARAMS"), ControllerPageRef.none (), 0));
        assertEquals ("nested", manager.pageState ().effectivePage ().id ());
        assertTrue (manager.isIdle ());
    }

    @Test
    void faultBlankingCannotLeaveReentrantBrowserRestoreDebtOrReviveLegacy ()
    {
        final var manager = manager ();
        final IMode generic = manager.getActive ();
        manager.register (Modes.BROWSER, mode (() -> { }, manager::restore));
        manager.apply (state (1, ControllerPageRef.legacy ("BROWSER"), ControllerPageRef.none (), 0));
        manager.invalidate ();
        assertSame (generic, manager.getActive ());
        assertEquals (ControllerPageRef.none (), manager.pageState ().effectivePage ());
        assertTrue (manager.isIdle ());
    }

    @Test
    void reentrantFaultDuringDeactivationCannotActivateOrDeactivateTheUnenteredDestination ()
    {
        final var manager = manager ();
        final AtomicInteger destinationStarts = new AtomicInteger ();
        final AtomicInteger destinationStops = new AtomicInteger ();
        manager.register (Modes.BROWSER, mode (() -> { }, manager::invalidate));
        manager.register (Modes.DEVICE_PARAMS, mode (destinationStarts::incrementAndGet, destinationStops::incrementAndGet));
        manager.apply (state (1, ControllerPageRef.legacy ("BROWSER"), ControllerPageRef.none (), 0));
        manager.apply (state (2, ControllerPageRef.legacy ("DEVICE_PARAMS"), ControllerPageRef.none (), 0));
        assertEquals (0, destinationStarts.get ());
        assertEquals (0, destinationStops.get ());
        assertEquals (ControllerPageRef.none (), manager.pageState ().effectivePage ());
        assertTrue (manager.isIdle ());
    }

    @Test
    void faultRecoveryRetiresThePendingPrefixAndDisablesRequestsUntilANewConsumerCommits ()
    {
        final var manager = manager ();
        manager.apply (state (1, ControllerPageRef.core ("before"), ControllerPageRef.none (), 0));
        manager.setActive (Modes.DEVICE_PARAMS);
        manager.apply (state (2, ControllerPageRef.legacy ("DEVICE_PARAMS"), ControllerPageRef.none (), 1));
        final Runnable oldCallback = manager.freezeRequestOrigin (() -> manager.setActive (Modes.BROWSER));
        manager.setActive (Modes.BROWSER);
        manager.invalidate ();
        manager.setActive (Modes.BROWSER);
        assertTrue (manager.canReplaceCore ());
        assertTrue (manager.requests (true).requests ().isEmpty ());
        assertEquals (2, manager.requests (true).retiredSequence ());
        manager.activateConsumer (2);
        manager.apply (state (0, ControllerPageRef.core ("recovered"), ControllerPageRef.none (), 2));
        oldCallback.run ();
        assertTrue (manager.requests (true).requests ().isEmpty ());
        manager.setActive (Modes.DEVICE_PARAMS);
        assertEquals (3, manager.requests (true).requests ().get (0).sequence ());
        assertEquals (0, manager.requests (true).requests ().get (0).originPageRevision ());
        manager.apply (state (1, ControllerPageRef.legacy ("DEVICE_PARAMS"), ControllerPageRef.none (), 3));
        assertTrue (manager.isIdle ());
    }

    @Test
    void healthyReplacementRetiresDelayedLegacyCallbacksButPreservesTheParentRequestPrefix ()
    {
        final var manager = manager ();
        manager.setActive (Modes.DEVICE_PARAMS);
        manager.apply (state (1, ControllerPageRef.legacy ("DEVICE_PARAMS"), ControllerPageRef.none (), 1));
        final Runnable delayed = manager.freezeRequestOrigin (() -> manager.setActive (Modes.BROWSER));
        manager.activateConsumer (2);
        delayed.run ();
        assertTrue (manager.requests (true).requests ().isEmpty ());
        assertEquals (1, manager.requests (false).retiredSequence ());
        assertThrows (IllegalArgumentException.class, () -> manager.prepare (state (0, ControllerPageRef.core ("fresh"), ControllerPageRef.none (), 0)));
    }

    @Test
    void inboxBoundAndAcknowledgementDoNotSilentlyDropOrSkipRequests ()
    {
        final var manager = manager ();
        for (int i = 0; i < LegacyControllerPageRequests.CAPACITY; i++) manager.setActive (Modes.DEVICE_PARAMS);
        assertThrows (IllegalStateException.class, () -> manager.setActive (Modes.BROWSER));
        assertEquals (64, manager.requests (true).requests ().size ());
        assertThrows (IllegalArgumentException.class, () -> manager.prepare (state (1, ControllerPageRef.core ("a"), ControllerPageRef.none (), 65)));
        assertThrows (IllegalArgumentException.class, () -> new LegacyControllerPageRequests (List.of (new LegacyControllerPageRequest (1, 0, 0, LegacyControllerPageRequest.Operation.RESTORE, ""), new LegacyControllerPageRequest (3, 0, 0, LegacyControllerPageRequest.Operation.RESTORE, ""))));
    }

    private static PushControllerPageManager manager ()
    {
        final var manager = new PushControllerPageManager ();
        manager.activateConsumer (1);
        manager.installCoreAdapter (mode (() -> { }, () -> { }));
        for (final Modes mode: List.of (Modes.TRACK, Modes.MASTER_TEMP, Modes.DEVICE_PARAMS, Modes.BROWSER)) manager.register (mode, mode (() -> { }, () -> { }));
        return manager;
    }
    private static DesiredControllerPageState state (final long revision, final ControllerPageRef selected, final ControllerPageRef previous, final long acknowledgement)
    {
        return new DesiredControllerPageState (revision, selected, previous, Optional.empty (), acknowledgement);
    }
    private static IMode mode (final Runnable activated, final Runnable deactivated)
    {
        return (IMode) Proxy.newProxyInstance (IMode.class.getClassLoader (), new Class<?>[] { IMode.class }, (proxy, method, arguments) -> {
            if (method.getName ().equals ("onActivate")) activated.run ();
            if (method.getName ().equals ("onDeactivate")) deactivated.run ();
            if (method.getReturnType () == boolean.class) return false;
            if (method.getReturnType () == int.class) return 0;
            return null;
        });
    }
}
