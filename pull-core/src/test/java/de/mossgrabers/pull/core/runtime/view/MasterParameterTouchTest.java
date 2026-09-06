// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.api.AutomationSnapshot;
import de.mossgrabers.pull.core.api.ClipCatalogSnapshot;
import de.mossgrabers.pull.core.api.ControlId;
import de.mossgrabers.pull.core.api.ControllerBridgeSnapshot;
import de.mossgrabers.pull.core.api.ControllerSnapshot;
import de.mossgrabers.pull.core.api.CoreResult;
import de.mossgrabers.pull.core.api.InputRouteMode;
import de.mossgrabers.pull.core.api.MasterSnapshot;
import de.mossgrabers.pull.core.api.ParameterBankId;
import de.mossgrabers.pull.core.api.ParameterBridgeSnapshot;
import de.mossgrabers.pull.core.api.ParameterSlot;
import de.mossgrabers.pull.core.api.ParameterTargetKind;
import de.mossgrabers.pull.core.api.ParameterTargetIdentitySnapshot;
import de.mossgrabers.pull.core.api.ParameterTargetRef;
import de.mossgrabers.pull.core.api.ParameterTargetSnapshot;
import de.mossgrabers.pull.core.api.PushControlIds;
import de.mossgrabers.pull.core.api.ShellCapabilities;
import de.mossgrabers.pull.core.api.effect.ConsumeControllerButtonEffect;
import de.mossgrabers.pull.core.api.effect.ResetParameterEffect;
import de.mossgrabers.pull.core.api.effect.SetAutomationWriteEffect;
import de.mossgrabers.pull.core.api.event.ControllerInputEvent;
import de.mossgrabers.pull.core.api.event.InputKind;
import de.mossgrabers.pull.core.api.event.InputPhase;
import de.mossgrabers.pull.core.api.output.RgbColor;
import de.mossgrabers.pull.core.view.CompiledWorkspace;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MasterParameterTouchTest
{
    private static final ControlId DELETE = PushControlIds.button ("DELETE");
    private static final List<ParameterSlot> SLOTS = List.of (ParameterSlot.MASTER_MIX_VOLUME, ParameterSlot.MASTER_MIX_PAN, ParameterSlot.CUE_VOLUME, ParameterSlot.CUE_MIX);

    @Test
    void completeEightEncoderProfilePreservesResetTouchAndReleaseForEveryRole ()
    {
        for (int index = 0; index < 8; index++)
        {
            final ControlId knob = knob (index);
            final CompiledWorkspace master = CompiledWorkspace.compile ("Master", List.of (new MasterControlView ()));
            final CoreResult initial = master.start (snapshot (Set.of (), Set.of ()));
            assertEquals (InputRouteMode.EXCLUSIVE, initial.desiredInputRoutes ().modeOrNull (knob, InputKind.TOUCH));
            assertTrue (initial.desiredParameterBanks ().includes (ParameterBankId.MASTER));
            final CoreResult begin = master.handle (touch (knob, InputPhase.BEGIN), snapshot (Set.of (knob), Set.of (DELETE, PushControlIds.button ("SHIFT"), PushControlIds.button ("SELECT"))));
            if (index < 4)
            {
                assertEquals (List.of (new ConsumeControllerButtonEffect (DELETE), new ResetParameterEffect (target (index))), begin.effects ());
                assertEquals (Map.of (knob, target (index)), begin.desiredParameterTouches ().targets ());
            }
            else
            {
                assertEquals (List.of (new ConsumeControllerButtonEffect (DELETE)), begin.effects ());
                assertTrue (begin.desiredParameterTouches ().targets ().isEmpty ());
            }
            assertTrue (master.handle (touch (knob, InputPhase.LONG), snapshot (Set.of (knob), Set.of ())).effects ().isEmpty ());
            final CoreResult end = master.handle (touch (knob, InputPhase.END), snapshot (Set.of (), Set.of ()));
            assertTrue (end.desiredParameterTouches ().targets ().isEmpty ());
            assertEquals (List.of (new SetAutomationWriteEffect ("project-a", false)), end.effects ());
        }
    }

    @Test
    void coreOwnedDisplayPreservesHostColoredPolicyAndResetDoesNotInventAValue ()
    {
        final MasterControlView view = new MasterControlView ();
        final ControllerSnapshot untouched = snapshot (Set.of (), Set.of ());
        view.start (untouched);
        final var before = view.render (untouched).display ();
        view.handle (touch (knob (0), InputPhase.BEGIN), snapshot (Set.of (), Set.of (DELETE)));
        assertEquals (before, view.render (untouched).display ());
        assertEquals (before, view.render (snapshot (Set.of (knob (0)), Set.of ())).display ());
    }

    @Test
    void departureRetainsExactMasterTouchAndOriginalReleaseFinishesGesture ()
    {
        final ParameterTouchSession session = new ParameterTouchSession ();
        final CompiledWorkspace master = CompiledWorkspace.compile ("Master", List.of (new MasterControlView (session)));
        final CompiledWorkspace other = CompiledWorkspace.compile ("Other", List.of ());
        master.start (snapshot (Set.of (), Set.of ()));
        final var router = new de.mossgrabers.pull.core.view.InputGestureRouter ();
        final var held = snapshot (Set.of (knob (0)), Set.of ());
        final var begin = router.capture (touch (knob (0), InputPhase.BEGIN), master);
        router.dispatch (begin, held);
        router.finish (begin, master);
        router.transition (master, other);
        assertEquals (Map.of (knob (0), target (0)), router.decorate (other, other.start (held), held).desiredParameterTouches ().targets ());
        final var released = snapshot (Set.of (), Set.of ());
        router.reconcile (other, released);
        final var end = router.capture (touch (knob (0), InputPhase.END), other);
        assertEquals (List.of (new SetAutomationWriteEffect ("project-a", false)), router.dispatch (end, released));
        router.finish (end, other);
        assertTrue (master.activate (held).desiredParameterTouches ().targets ().isEmpty ());
    }

    private static ControllerInputEvent touch (final ControlId knob, final InputPhase phase)
    {
        return new ControllerInputEvent (1, 1, knob, InputKind.TOUCH, phase, phase == InputPhase.END ? 0 : 127);
    }

    private static ControlId knob (final int index)
    {
        return PushControlIds.continuous ("KNOB" + (index + 1));
    }

    private static ParameterTargetRef target (final int index)
    {
        return new ParameterTargetRef (ParameterTargetKind.LIVE, "master-" + index, 1);
    }

    private static ControllerSnapshot snapshot (final Set<ControlId> touched, final Set<ControlId> pressed)
    {
        final Map<ParameterSlot, ParameterTargetSnapshot> slots = new LinkedHashMap<> ();
        for (int index = 0; index < 4; index++)
            slots.put (SLOTS.get (index), new ParameterTargetSnapshot (target (index), "Parameter " + index, 64, 64, "64", 128, 0, Optional.empty (), new ParameterTargetIdentitySnapshot ("project-master", "project-a", 0, index)));
        final ControllerBridgeSnapshot empty = ControllerBridgeSnapshot.empty ();
        final MasterSnapshot master = new MasterSnapshot (true, "project-a", "Project", true, false, false, false, false, "Master", new RgbColor (0, 100, 255), true, true, false, 0, 0);
        final ControllerBridgeSnapshot bridge = new ControllerBridgeSnapshot (empty.transport (), empty.selectedTrack (), empty.sessionBank (), empty.layout (), empty.noteView (), empty.noteRepeat (), empty.drum (), new ParameterBridgeSnapshot (slots, Map.of ()), empty.controllerMappingFeedback (), master, empty.project (), new AutomationSnapshot ("project-a", true, true));
        return new ControllerSnapshot (1, 1, new ShellCapabilities (Map.of ()), bridge, new ClipCatalogSnapshot (0, List.of ()), Map.of (), Map.of (), Optional.empty (), pressed, touched);
    }
}
