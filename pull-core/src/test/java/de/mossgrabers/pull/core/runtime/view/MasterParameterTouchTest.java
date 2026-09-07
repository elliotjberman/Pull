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
import de.mossgrabers.pull.core.view.RoutedWorkspace;
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
            final RoutedWorkspace master = new RoutedWorkspace (CompiledWorkspace.compile ("Master", List.of (new MasterControlView ())));
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
    void delayedProjectActionIsCancelledAcrossProjectChangeAndCannotRevive ()
    {
        final ControlId engine = PushControlIds.button ("ROW2_5");
        final ControllerInputEvent begin = new ControllerInputEvent (1, 1, engine, InputKind.BUTTON, InputPhase.BEGIN, 1);
        final RoutedWorkspace workspace = new RoutedWorkspace (CompiledWorkspace.compile ("Master", List.of (new MasterControlView ())));
        final ControllerSnapshot original = snapshot (Set.of (), Set.of (engine));
        workspace.start (original);
        final var delayed = workspace.resolveAction (begin, original);
        workspace.activate (snapshot ("project-b", Set.of (), Set.of (engine)));
        workspace.activate (original);
        assertTrue (workspace.dispatchAction (delayed, original).isEmpty ());
        assertTrue (workspace.handle (begin, original).effects ().isEmpty ());
        workspace.handle (new ControllerInputEvent (2, 2, engine, InputKind.BUTTON, InputPhase.END, 0), snapshot (Set.of (), Set.of ()));
        assertEquals (List.of (new de.mossgrabers.pull.core.api.effect.SetProjectEngineEffect ("project-a", false)), workspace.handle (begin, original).effects ());
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
        return snapshot ("project-a", touched, pressed);
    }

    private static ControllerSnapshot snapshot (final String project, final Set<ControlId> touched, final Set<ControlId> pressed)
    {
        final Map<ParameterSlot, ParameterTargetSnapshot> slots = new LinkedHashMap<> ();
        for (int index = 0; index < 4; index++)
            slots.put (SLOTS.get (index), new ParameterTargetSnapshot (target (index), "Parameter " + index, 64, 64, "64", 128, 0, Optional.empty (), new ParameterTargetIdentitySnapshot ("project-master", project, 0, index)));
        final ControllerBridgeSnapshot empty = ControllerBridgeSnapshot.empty ();
        final MasterSnapshot master = new MasterSnapshot (true, project, "Project", true, false, false, false, false, "Master", new RgbColor (0, 100, 255), true, true, false, 0, 0);
        final ControllerBridgeSnapshot bridge = new ControllerBridgeSnapshot (empty.transport (), empty.selectedTrack (), empty.sessionBank (), empty.layout (), empty.noteView (), empty.noteRepeat (), empty.drum (), new ParameterBridgeSnapshot (slots, Map.of (), java.util.Set.of ()), empty.controllerMappingFeedback (), master, empty.project (), new AutomationSnapshot (project, true, true));
        return new ControllerSnapshot (1, 1, new ShellCapabilities (Map.of ()), bridge, new ClipCatalogSnapshot (0, List.of ()), Map.of (), Map.of (), Optional.empty (), pressed, touched);
    }
}
