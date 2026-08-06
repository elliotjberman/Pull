// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.runtime;

import de.mossgrabers.pull.core.api.BridgeSubscription;
import de.mossgrabers.pull.core.api.ClipCatalogSnapshot;
import de.mossgrabers.pull.core.api.ControlId;
import de.mossgrabers.pull.core.api.ControllerActionBinding;
import de.mossgrabers.pull.core.api.ControllerActionId;
import de.mossgrabers.pull.core.api.ControllerBridgeSnapshot;
import de.mossgrabers.pull.core.api.ControllerLayoutSnapshot;
import de.mossgrabers.pull.core.api.ControllerSnapshot;
import de.mossgrabers.pull.core.api.ControllerStateScope;
import de.mossgrabers.pull.core.api.CoreResult;
import de.mossgrabers.pull.core.api.DrumContextSnapshot;
import de.mossgrabers.pull.core.api.ParameterBridgeSnapshot;
import de.mossgrabers.pull.core.api.ParameterSlot;
import de.mossgrabers.pull.core.api.ParameterTargetKind;
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
import de.mossgrabers.pull.core.view.CompiledWorkspace;
import de.mossgrabers.pull.core.view.ControllerView;
import de.mossgrabers.pull.core.view.ResolvedControllerAction;
import de.mossgrabers.pull.core.view.ViewProfile;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;


class SnapbackSessionTest
{
    private static final ControlId SHIFT = PushControlIds.button ("SHIFT");
    private static final ControlId KNOB1 = PushControlIds.continuous ("KNOB1");
    private static final ControlId KNOB2 = PushControlIds.continuous ("KNOB2");
    private static final ControlId NAVIGATION = PushControlIds.button ("TEST_NAVIGATION");
    private static final ParameterTargetRef FIRST = new ParameterTargetRef (ParameterTargetKind.LIVE, "first", 1);
    private static final ParameterTargetRef SECOND = new ParameterTargetRef (ParameterTargetKind.LIVE, "second", 2);


    @Test
    void capturesEachExactPreMutationBaselineOnceAndRestoresBothTargets ()
    {
        final SnapbackSession session = startedSession (parameters (100, 200));
        session.handle (mutation (2, KNOB1, FIRST, 100), snapshot (parameters (99, 200), Set.of (SHIFT)), ParameterSlot.active (0));
        session.handle (mutation (3, KNOB2, SECOND, 200), snapshot (parameters (50, 199), Set.of (SHIFT)), ParameterSlot.active (1));
        session.handle (mutation (4, KNOB1, FIRST, 50), snapshot (parameters (49, 175), Set.of (SHIFT)), ParameterSlot.active (0));

        CoreResult result = session.decorate (CoreResult.empty (), List.of ());
        assertEquals (Map.of (FIRST, 100.0, SECOND, 200.0), result.desiredParameterInteraction ().baselines ());
        assertTrue (result.desiredParameterInteraction ().acceptsMutations ());

        session.handle (button (5, SHIFT, InputPhase.END), snapshot (parameters (90, 175), Set.of ()), null);
        session.handle (tick (6), snapshot (parameters (90, 175), Set.of ()), null);
        final SnapbackSession.Update restore = session.handle (tick (7), snapshot (parameters (90, 175), Set.of ()), null);
        result = session.decorate (CoreResult.empty (), restore.effects ());

        assertEquals (Set.of (
            new SetParameterValueEffect (FIRST, 100),
            new SetParameterValueEffect (SECOND, 200)), Set.copyOf (result.effects ()));
        assertEquals (Set.of (FIRST, SECOND), result.desiredParameterInteraction ().blockedMutations ());

        session.handle (tick (8), snapshot (parameters (100, 200), Set.of ()), null);
        final SnapbackSession.Update complete = session.handle (tick (9), snapshot (parameters (100, 200), Set.of ()), null);
        result = session.decorate (CoreResult.empty (), complete.effects ());
        assertTrue (result.desiredParameterInteraction ().baselines ().isEmpty ());
        assertFalse (result.desiredBridgeSubscriptions ().includes (BridgeSubscription.PARAMETERS));
    }


    @Test
    void capturesAndBlocksCoreOwnedRelativeMutationThroughTheSameSession ()
    {
        final SnapbackSession session = startedSession (parameters (100, 200));

        final SnapbackSession.Update first = session.handle (
            relative (2, KNOB1, 4),
            snapshot (parameters (100, 200), Set.of (SHIFT)),
            ParameterSlot.active (0));
        session.handle (
            relative (3, KNOB1, 6),
            snapshot (parameters (80, 200), Set.of (SHIFT)),
            ParameterSlot.active (0));

        assertFalse (first.intercepted ());
        assertEquals (
            Map.of (FIRST, 100.0),
            session.decorate (CoreResult.empty (), List.of ()).desiredParameterInteraction ().baselines ());

        session.handle (button (4, SHIFT, InputPhase.END), snapshot (parameters (70, 200), Set.of ()), null);
        final SnapbackSession.Update blocked = session.handle (
            relative (5, KNOB1, 1),
            snapshot (parameters (70, 200), Set.of ()),
            ParameterSlot.active (0));
        assertTrue (blocked.intercepted ());

        session.handle (tick (6), snapshot (parameters (70, 200), Set.of ()), null);
        assertEquals (
            List.of (new SetParameterValueEffect (FIRST, 100)),
            session.handle (tick (7), snapshot (parameters (70, 200), Set.of ()), null).effects ());
    }


    @Test
    void semanticActionWaitsForRestoreAcknowledgementBeforeRelease ()
    {
        final SnapbackSession session = startedSession (parameters (100, 200));
        session.handle (mutation (2, KNOB1, FIRST, 100), snapshot (parameters (40, 200), Set.of (SHIFT)), ParameterSlot.active (0));

        final ResolvedControllerAction navigation = action (3, NAVIGATION);
        final SnapbackSession.Update deferred = session.handleAction (navigation, snapshot (parameters (40, 200), Set.of (SHIFT)));
        CoreResult result = session.decorate (CoreResult.empty (), deferred.effects ());
        assertTrue (deferred.intercepted ());
        assertTrue (deferred.releasedActions ().isEmpty ());
        assertEquals (1, result.desiredParameterInteraction ().pendingActionCount ());
        assertTrue (result.desiredParameterInteraction ().blockedActions ().contains (ControllerStateScope.ACTIVE_PARAMETERS));

        session.handle (tick (4), snapshot (parameters (40, 200), Set.of (SHIFT)), null);
        session.handle (tick (5), snapshot (parameters (40, 200), Set.of (SHIFT)), null);
        session.handle (tick (6), snapshot (parameters (100, 200), Set.of (SHIFT)), null);
        final SnapbackSession.Update complete = session.handle (tick (7), snapshot (parameters (100, 200), Set.of (SHIFT)), null);
        result = session.decorate (CoreResult.empty (), complete.effects ());

        assertEquals (List.of (navigation), complete.releasedActions ());
        assertEquals (0, result.desiredParameterInteraction ().pendingActionCount ());
        assertTrue (result.desiredBridgeSubscriptions ().includes (BridgeSubscription.PARAMETERS));
    }


    @Test
    void physicalControlMustMapToTheAuthoritativeExactTarget ()
    {
        final SnapbackSession session = startedSession (parameters (100, 200));

        session.handle (mutation (2, KNOB1, FIRST, 100), snapshot (parameters (99, 200), Set.of (SHIFT)), ParameterSlot.active (1));

        assertTrue (session.decorate (CoreResult.empty (), List.of ()).desiredParameterInteraction ().baselines ().isEmpty ());
    }


    @Test
    void hotReloadHydratesRetainedTargetsAndFinishesTheirRestore ()
    {
        final SnapbackSession session = new SnapbackSession ();
        final ParameterBridgeSnapshot retained = new ParameterBridgeSnapshot (
            parameters (40, 200).slots (),
            Map.of (FIRST, 100.0));
        session.start (snapshot (retained, Set.of ()));

        session.handle (tick (1), snapshot (retained, Set.of ()), null);
        final SnapbackSession.Update restore = session.handle (tick (2), snapshot (retained, Set.of ()), null);
        assertEquals (List.of (new SetParameterValueEffect (FIRST, 100)), restore.effects ());

        final ParameterBridgeSnapshot restored = new ParameterBridgeSnapshot (parameters (100, 200).slots (), Map.of (FIRST, 100.0));
        session.handle (tick (3), snapshot (restored, Set.of ()), null);
        session.handle (tick (4), snapshot (restored, Set.of ()), null);
        assertTrue (session.decorate (CoreResult.empty (), List.of ()).desiredParameterInteraction ().baselines ().isEmpty ());
    }


    @Test
    void delayedDriftAfterOneBaselineSampleRequestsTheRestoreAgain ()
    {
        final SnapbackSession session = startedSession (parameters (100, 200));
        session.handle (mutation (2, KNOB1, FIRST, 100), snapshot (parameters (40, 200), Set.of (SHIFT)), ParameterSlot.active (0));
        session.handle (button (3, SHIFT, InputPhase.END), snapshot (parameters (40, 200), Set.of ()), null);
        session.handle (tick (4), snapshot (parameters (40, 200), Set.of ()), null);
        session.handle (tick (5), snapshot (parameters (40, 200), Set.of ()), null);

        session.handle (tick (6), snapshot (parameters (100, 200), Set.of ()), null);
        assertTrue (session.handle (tick (7), snapshot (parameters (94, 200), Set.of ()), null).effects ().isEmpty ());
        assertEquals (
            List.of (new SetParameterValueEffect (FIRST, 100)),
            session.handle (tick (8), snapshot (parameters (94, 200), Set.of ()), null).effects ());
    }


    @Test
    void restoreTimeoutReleasesSemanticActionWithoutAnUnleasedFinalWrite ()
    {
        final SnapbackSession session = startedSession (parameters (100, 200));
        session.handle (mutation (2, KNOB1, FIRST, 100), snapshot (parameters (40, 200), Set.of (SHIFT)), ParameterSlot.active (0));
        final ResolvedControllerAction navigation = action (3, NAVIGATION);
        session.handleAction (navigation, snapshot (parameters (40, 200), Set.of (SHIFT)));
        session.handle (tick (4), snapshot (parameters (40, 200), Set.of (SHIFT)), null);
        session.handle (tick (5), snapshot (parameters (40, 200), Set.of (SHIFT)), null);

        SnapbackSession.Update update = null;
        for (int sequence = 6; sequence < 22; sequence++)
            update = session.handle (tick (sequence), snapshot (parameters (40, 200), Set.of (SHIFT)), null);

        assertEquals (List.of (navigation), update.releasedActions ());
        assertTrue (update.effects ().isEmpty ());
        assertTrue (session.decorate (CoreResult.empty (), update.effects ()).desiredParameterInteraction ().baselines ().isEmpty ());
    }


    @Test
    void repressDuringRestorationBeginsFreshSessionOnlyAfterAcknowledgement ()
    {
        final SnapbackSession session = startedSession (parameters (100, 200));
        session.handle (mutation (2, KNOB1, FIRST, 100), snapshot (parameters (40, 200), Set.of (SHIFT)), ParameterSlot.active (0));
        session.handle (button (3, SHIFT, InputPhase.END), snapshot (parameters (40, 200), Set.of ()), null);
        session.handle (button (4, SHIFT, InputPhase.BEGIN), snapshot (parameters (40, 200), Set.of (SHIFT)), null);
        session.handle (tick (5), snapshot (parameters (40, 200), Set.of (SHIFT)), null);
        session.handle (tick (6), snapshot (parameters (40, 200), Set.of (SHIFT)), null);
        session.handle (tick (7), snapshot (parameters (100, 200), Set.of (SHIFT)), null);
        session.handle (tick (8), snapshot (parameters (100, 200), Set.of (SHIFT)), null);

        CoreResult result = session.decorate (CoreResult.empty (), List.of ());
        assertTrue (result.desiredParameterInteraction ().baselines ().isEmpty ());
        assertTrue (result.desiredBridgeSubscriptions ().includes (BridgeSubscription.PARAMETERS));

        session.handle (mutation (9, KNOB2, SECOND, 200), snapshot (parameters (100, 199), Set.of (SHIFT)), ParameterSlot.active (1));
        result = session.decorate (CoreResult.empty (), List.of ());
        assertEquals (Map.of (SECOND, 200.0), result.desiredParameterInteraction ().baselines ());
    }


    private static SnapbackSession startedSession (final ParameterBridgeSnapshot parameters)
    {
        final SnapbackSession session = new SnapbackSession ();
        session.start (snapshot (parameters, Set.of ()));
        session.handle (button (1, SHIFT, InputPhase.BEGIN), snapshot (parameters, Set.of (SHIFT)), null);
        return session;
    }


    private static ResolvedControllerAction action (final long sequence, final ControlId control)
    {
        final ControllerSnapshot snapshot = snapshot (parameters (100, 200), Set.of (SHIFT));
        return CompiledWorkspace.compile ("test-actions", List.of (new ActionView (control))).resolveAction (button (sequence, control, InputPhase.BEGIN), snapshot);
    }


    private static ParameterMutationEvent mutation (final long sequence, final ControlId control, final ParameterTargetRef target, final double value)
    {
        return new ParameterMutationEvent (sequence, sequence, control, new ParameterTargetSnapshot (target, value, 0.5));
    }


    private static ControllerInputEvent button (final long sequence, final ControlId control, final InputPhase phase)
    {
        return new ControllerInputEvent (sequence, sequence, control, InputKind.BUTTON, phase, phase == InputPhase.END ? 0 : 127);
    }


    private static ControllerInputEvent relative (final long sequence, final ControlId control, final long delta)
    {
        return new ControllerInputEvent (sequence, sequence, control, InputKind.RELATIVE, InputPhase.UPDATE, delta);
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


    private record ActionView (ControlId control) implements ControllerView
    {
        @Override
        public String id ()
        {
            return "test-action";
        }


        @Override
        public ViewProfile profile ()
        {
            return ViewProfile.fixed ("test-action", Set.of (), Set.of ());
        }


        @Override
        public Set<ControllerActionBinding> actionBindings ()
        {
            return Set.of (new ControllerActionBinding (
                this.control,
                InputKind.BUTTON,
                ControllerActionId.NAVIGATE_SELECTED_TARGET,
                Set.of (ControllerStateScope.ACTIVE_PARAMETERS)));
        }
    }
}
