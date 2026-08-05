// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.runtime;

import de.mossgrabers.pull.core.api.BridgeSubscription;
import de.mossgrabers.pull.core.api.ClipCatalogSnapshot;
import de.mossgrabers.pull.core.api.ControlId;
import de.mossgrabers.pull.core.api.ControllerBridgeSnapshot;
import de.mossgrabers.pull.core.api.ControllerLayoutSnapshot;
import de.mossgrabers.pull.core.api.ControllerSnapshot;
import de.mossgrabers.pull.core.api.CoreResult;
import de.mossgrabers.pull.core.api.DesiredBridgeSubscriptions;
import de.mossgrabers.pull.core.api.DesiredControllerWorkspace;
import de.mossgrabers.pull.core.api.DesiredInputRoutes;
import de.mossgrabers.pull.core.api.DrumContextSnapshot;
import de.mossgrabers.pull.core.api.InputRouteMode;
import de.mossgrabers.pull.core.api.ParameterBridgeSnapshot;
import de.mossgrabers.pull.core.api.ParameterSlot;
import de.mossgrabers.pull.core.api.ParameterTargetRef;
import de.mossgrabers.pull.core.api.ParameterTargetSnapshot;
import de.mossgrabers.pull.core.api.PushControlIds;
import de.mossgrabers.pull.core.api.SelectedTrackSnapshot;
import de.mossgrabers.pull.core.api.ShellCapabilities;
import de.mossgrabers.pull.core.api.TransportSnapshot;
import de.mossgrabers.pull.core.api.effect.SetParameterValueEffect;
import de.mossgrabers.pull.core.api.event.ControllerInputEvent;
import de.mossgrabers.pull.core.api.event.ControllerTickEvent;
import de.mossgrabers.pull.core.api.event.InputKind;
import de.mossgrabers.pull.core.api.event.InputPhase;
import de.mossgrabers.pull.core.api.event.ParameterMutationEvent;
import de.mossgrabers.pull.core.api.output.DesiredHardwareOutput;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


class SnapbackSessionTest
{
    private static final ControlId SHIFT = PushControlIds.button ("SHIFT");
    private static final ControlId KNOB1 = PushControlIds.continuous ("KNOB1");
    private static final ControlId KNOB2 = PushControlIds.continuous ("KNOB2");
    private static final ParameterTargetRef FIRST = new ParameterTargetRef ("live", "first", 1);
    private static final ParameterTargetRef SECOND = new ParameterTargetRef ("live", "second", 2);


    @Test
    void capturesEachAuthoritativeBaselineOnlyOnceAndRestoresBothTargets ()
    {
        final SnapbackSession session = startedSession (parameters (100, 200));
        session.handle (mutation (2, KNOB1, ParameterSlot.active (0), FIRST, 100), snapshot (parameters (99, 200), Set.of (SHIFT)));
        session.handle (mutation (3, KNOB2, ParameterSlot.active (1), SECOND, 200), snapshot (parameters (50, 200), Set.of (SHIFT)));
        session.handle (mutation (4, KNOB1, ParameterSlot.active (0), FIRST, 50), snapshot (parameters (50, 175), Set.of (SHIFT)));

        CoreResult result = session.decorate (CoreResult.empty (), List.of ());
        assertEquals (Map.of (FIRST, 100.0, SECOND, 200.0), result.desiredParameterLeases ().baselines ());

        session.handle (button (5, SHIFT, InputPhase.END), snapshot (parameters (90, 175), Set.of ()));
        session.handle (tick (6), snapshot (parameters (90, 175), Set.of ()));
        final SnapbackSession.Update restore = session.handle (tick (7), snapshot (parameters (90, 175), Set.of ()));
        result = session.decorate (CoreResult.empty (), restore.effects ());

        assertEquals (Set.of (
            new SetParameterValueEffect (FIRST, 100),
            new SetParameterValueEffect (SECOND, 200)), Set.copyOf (result.effects ()));
        assertEquals (InputRouteMode.SUPPRESS_STABLE, result.desiredInputRoutes ().modeOrNull (KNOB1, InputKind.RELATIVE));

        session.handle (tick (8), snapshot (parameters (100, 200), Set.of ()));
        final SnapbackSession.Update complete = session.handle (tick (9), snapshot (parameters (100, 200), Set.of ()));
        result = session.decorate (CoreResult.empty (), complete.effects ());
        assertTrue (result.desiredParameterLeases ().baselines ().isEmpty ());
        assertTrue (!result.desiredBridgeSubscriptions ().includes (BridgeSubscription.PARAMETERS));
    }


    @Test
    void navigationWaitsForRestoreAcknowledgementBeforeItsCoreHalfIsReleased ()
    {
        final SnapbackSession session = startedSession (parameters (100, 200));
        session.handle (mutation (2, KNOB1, ParameterSlot.active (0), FIRST, 100), snapshot (parameters (100, 200), Set.of (SHIFT)));
        final ControllerInputEvent navigation = button (3, PushControlIds.button ("ROW1_1"), InputPhase.BEGIN);

        final SnapbackSession.Update deferred = session.handle (navigation, snapshot (parameters (40, 200), Set.of (SHIFT)));
        CoreResult result = session.decorate (CoreResult.empty (), deferred.effects ());
        assertTrue (deferred.intercepted ());
        assertTrue (deferred.releasedInputs ().isEmpty ());
        assertEquals (InputRouteMode.DEFER_STABLE, result.desiredInputRoutes ().modeOrNull (navigation.controlId (), InputKind.BUTTON));

        session.handle (tick (4), snapshot (parameters (40, 200), Set.of (SHIFT)));
        session.handle (tick (5), snapshot (parameters (40, 200), Set.of (SHIFT)));
        session.handle (tick (6), snapshot (parameters (100, 200), Set.of (SHIFT)));
        final SnapbackSession.Update complete = session.handle (tick (7), snapshot (parameters (100, 200), Set.of (SHIFT)));
        result = session.decorate (CoreResult.empty (), complete.effects ());

        assertEquals (List.of (navigation), complete.releasedInputs ());
        assertEquals (null, result.desiredInputRoutes ().modeOrNull (navigation.controlId (), InputKind.BUTTON));
        assertTrue (result.desiredBridgeSubscriptions ().includes (BridgeSubscription.PARAMETERS));
    }


    @Test
    void restorationBarrierUsesTheInstalledPageControls ()
    {
        final SnapbackSession session = startedSession (parameters (100, 200));
        session.handle (mutation (2, KNOB1, ParameterSlot.active (0), FIRST, 100), snapshot (parameters (100, 200), Set.of (SHIFT)));

        final CoreResult result = session.decorate (CoreResult.empty (), List.of ());

        assertEquals (InputRouteMode.DEFER_STABLE, result.desiredInputRoutes ().modeOrNull (PushControlIds.button ("PAGE_LEFT"), InputKind.BUTTON));
        assertEquals (InputRouteMode.DEFER_STABLE, result.desiredInputRoutes ().modeOrNull (PushControlIds.button ("PAGE_RIGHT"), InputKind.BUTTON));
        assertEquals (null, result.desiredInputRoutes ().modeOrNull (PushControlIds.button ("DEVICE_LEFT"), InputKind.BUTTON));
        assertEquals (null, result.desiredInputRoutes ().modeOrNull (PushControlIds.button ("DEVICE_RIGHT"), InputKind.BUTTON));
    }


    @Test
    void hotReloadHydratesStableRetainedTargetsAndFinishesTheirRestore ()
    {
        final SnapbackSession session = new SnapbackSession ();
        final ParameterBridgeSnapshot retained = new ParameterBridgeSnapshot (
            parameters (40, 200).slots (),
            Map.of (FIRST, 100.0));
        session.start (snapshot (retained, Set.of ()));

        session.handle (tick (1), snapshot (retained, Set.of ()));
        final SnapbackSession.Update restore = session.handle (tick (2), snapshot (retained, Set.of ()));
        assertEquals (List.of (new SetParameterValueEffect (FIRST, 100)), restore.effects ());

        final ParameterBridgeSnapshot restored = new ParameterBridgeSnapshot (parameters (100, 200).slots (), Map.of (FIRST, 100.0));
        session.handle (tick (3), snapshot (restored, Set.of ()));
        session.handle (tick (4), snapshot (restored, Set.of ()));
        assertTrue (session.decorate (CoreResult.empty (), List.of ()).desiredParameterLeases ().baselines ().isEmpty ());
    }


    @Test
    void delayedDriftAfterOneBaselineSampleRequestsTheRestoreAgain ()
    {
        final SnapbackSession session = startedSession (parameters (100, 200));
        session.handle (mutation (2, KNOB1, ParameterSlot.active (0), FIRST, 100), snapshot (parameters (100, 200), Set.of (SHIFT)));
        session.handle (button (3, SHIFT, InputPhase.END), snapshot (parameters (40, 200), Set.of ()));
        session.handle (tick (4), snapshot (parameters (40, 200), Set.of ()));
        session.handle (tick (5), snapshot (parameters (40, 200), Set.of ()));

        session.handle (tick (6), snapshot (parameters (100, 200), Set.of ()));
        assertTrue (session.handle (tick (7), snapshot (parameters (94, 200), Set.of ())).effects ().isEmpty ());
        assertEquals (
            List.of (new SetParameterValueEffect (FIRST, 100)),
            session.handle (tick (8), snapshot (parameters (94, 200), Set.of ())).effects ());
    }


    @Test
    void restoreTimeoutReleasesNavigationWithoutAnUnleasedFinalWrite ()
    {
        final SnapbackSession session = startedSession (parameters (100, 200));
        session.handle (mutation (2, KNOB1, ParameterSlot.active (0), FIRST, 100), snapshot (parameters (100, 200), Set.of (SHIFT)));
        final ControllerInputEvent navigation = button (3, PushControlIds.button ("ARROW_RIGHT"), InputPhase.BEGIN);
        session.handle (navigation, snapshot (parameters (40, 200), Set.of (SHIFT)));
        session.handle (tick (4), snapshot (parameters (40, 200), Set.of (SHIFT)));
        session.handle (tick (5), snapshot (parameters (40, 200), Set.of (SHIFT)));

        SnapbackSession.Update update = null;
        for (int sequence = 6; sequence < 22; sequence++)
            update = session.handle (tick (sequence), snapshot (parameters (40, 200), Set.of (SHIFT)));

        assertEquals (List.of (navigation), update.releasedInputs ());
        assertTrue (update.effects ().isEmpty ());
        final CoreResult result = session.decorate (CoreResult.empty (), update.effects ());
        assertTrue (result.desiredParameterLeases ().baselines ().isEmpty ());
    }


    @Test
    void repressDuringRestorationBeginsAFreshSessionOnlyAfterAcknowledgement ()
    {
        final SnapbackSession session = startedSession (parameters (100, 200));
        session.handle (mutation (2, KNOB1, ParameterSlot.active (0), FIRST, 100), snapshot (parameters (100, 200), Set.of (SHIFT)));
        session.handle (button (3, SHIFT, InputPhase.END), snapshot (parameters (40, 200), Set.of ()));
        session.handle (button (4, SHIFT, InputPhase.BEGIN), snapshot (parameters (40, 200), Set.of (SHIFT)));
        session.handle (tick (5), snapshot (parameters (40, 200), Set.of (SHIFT)));
        session.handle (tick (6), snapshot (parameters (40, 200), Set.of (SHIFT)));
        session.handle (tick (7), snapshot (parameters (100, 200), Set.of (SHIFT)));
        session.handle (tick (8), snapshot (parameters (100, 200), Set.of (SHIFT)));

        CoreResult result = session.decorate (CoreResult.empty (), List.of ());
        assertTrue (result.desiredParameterLeases ().baselines ().isEmpty ());
        assertTrue (result.desiredBridgeSubscriptions ().includes (BridgeSubscription.PARAMETERS));

        session.handle (mutation (9, KNOB2, ParameterSlot.active (1), SECOND, 200), snapshot (parameters (100, 200), Set.of (SHIFT)));
        result = session.decorate (CoreResult.empty (), List.of ());
        assertEquals (Map.of (SECOND, 200.0), result.desiredParameterLeases ().baselines ());
    }


    private static SnapbackSession startedSession (final ParameterBridgeSnapshot parameters)
    {
        final SnapbackSession session = new SnapbackSession ();
        session.start (snapshot (parameters, Set.of ()));
        session.handle (button (1, SHIFT, InputPhase.BEGIN), snapshot (parameters, Set.of (SHIFT)));
        return session;
    }


    private static ParameterMutationEvent mutation (final long sequence, final ControlId control, final ParameterSlot slot, final ParameterTargetRef target, final double value)
    {
        return new ParameterMutationEvent (sequence, sequence, control, slot, new ParameterTargetSnapshot (target, value, 0.5));
    }


    private static ControllerInputEvent button (final long sequence, final ControlId control, final InputPhase phase)
    {
        return new ControllerInputEvent (sequence, sequence, control, InputKind.BUTTON, phase, phase == InputPhase.END ? 0 : 127);
    }


    private static ControllerTickEvent tick (final long sequence)
    {
        return new ControllerTickEvent (sequence, sequence);
    }


    private static ParameterBridgeSnapshot parameters (final double first, final double second)
    {
        return new ParameterBridgeSnapshot (Map.of (
            ParameterSlot.active (0), new ParameterTargetSnapshot (FIRST, first, 0.5),
            ParameterSlot.active (1), new ParameterTargetSnapshot (SECOND, second, 0.5)), Map.of ());
    }


    private static ControllerSnapshot snapshot (final ParameterBridgeSnapshot parameters, final Set<ControlId> pressed)
    {
        final ControllerBridgeSnapshot bridge = new ControllerBridgeSnapshot (
            TransportSnapshot.empty (),
            SelectedTrackSnapshot.empty (),
            ControllerLayoutSnapshot.empty (),
            DrumContextSnapshot.empty (),
            parameters);
        return new ControllerSnapshot (0, 0, ShellCapabilities.empty (), bridge, ClipCatalogSnapshot.empty (), Map.of (), Map.of (), Optional.empty (), pressed, Set.of ());
    }
}
