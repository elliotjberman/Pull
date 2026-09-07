// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.api.*;
import de.mossgrabers.pull.core.api.effect.AdjustParameterValueEffect;
import de.mossgrabers.pull.core.api.effect.SetAutomationWriteEffect;
import de.mossgrabers.pull.core.api.event.*;
import de.mossgrabers.pull.core.api.output.DisplayCommand;
import de.mossgrabers.pull.core.api.output.RgbColor;
import de.mossgrabers.pull.core.view.CompiledWorkspace;
import de.mossgrabers.pull.core.view.RoutedWorkspace;
import de.mossgrabers.pull.core.view.ControllerView;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ParameterOwnerAlignmentTest
{
    private static final ControlId KNOB = PushControlIds.continuous ("KNOB1");
    private static final Set<ControlId> MODIFIERS = Set.of (PushControlIds.button ("DELETE"), PushControlIds.button ("SHIFT"), PushControlIds.button ("SELECT"));
    private static final RgbColor COLOR = new RgbColor (12, 100, 255);


    @Test
    void independentlyAdvancedParametersCannotCaptureSnapbackWriteResetTouchOrRenderUnderAnOldOwner ()
    {
        for (final Page page: Page.values ())
        {
            final Fixture fixture = new Fixture (page);
            fixture.workspace.start (snapshot (page, "a", "a", false, false, false));
            // Parameter reconciliation can publish B before the selected/Master/Automation domains advance.
            final ControllerSnapshot mixed = snapshot (page, "a", "b", false, true, false);
            assertNull (fixture.workspace.parameterSlotOrNull (KNOB, mixed), page.name ());
            assertTrue (fixture.workspace.handle (motion (), mixed).effects ().isEmpty (), page.name ());
            final CoreResult begin = fixture.workspace.handle (touch (InputPhase.BEGIN), snapshot (page, "a", "b", true, true, false));
            assertTrue (begin.effects ().isEmpty (), page.name ());
            assertTrue (begin.desiredParameterTouches ().targets ().isEmpty (), page.name ());
            assertFalse (hasParameterLabel (begin), page.name ());
            assertTrue (fixture.workspace.handle (touch (InputPhase.END), mixed).effects ().isEmpty (), "Rejected BEGIN must not acquire automation release policy");

            // Only later authoritative owner advancement makes the new parameter usable.
            final ControllerSnapshot advanced = snapshot (page, "b", "b", false, false, false);
            assertEquals (page.slot, fixture.workspace.parameterSlotOrNull (KNOB, advanced));
            final CoreResult applied = fixture.workspace.handle (motion (), advanced);
            assertTrue (applied.effects ().stream ().anyMatch (AdjustParameterValueEffect.class::isInstance), page.name ());
            assertTrue (hasParameterLabel (applied), page.name ());
        }
    }


    @Test
    void contradictionCancelsTouchAndFinishesItsAutomationBeforePhysicalRelease ()
    {
        for (final Page page: Page.values ())
        {
            final Fixture fixture = new Fixture (page);
            fixture.workspace.start (snapshot (page, "a", "a", false, false, false));
            final CoreResult begin = fixture.workspace.handle (touch (InputPhase.BEGIN), snapshot (page, "a", "a", true, false, false));
            assertEquals (Map.of (KNOB, target (page, "a")), begin.desiredParameterTouches ().targets ());
            final CoreResult cancelled = fixture.workspace.activate (snapshot (page, "a", "b", true, false, false));
            assertTrue (cancelled.desiredParameterTouches ().targets ().isEmpty ());
            assertEquals (List.of (new SetAutomationWriteEffect ("project-a", false)), cancelled.effects (), page.name ());
            final CoreResult end = fixture.workspace.handle (touch (InputPhase.END), snapshot (page, "a", "b", false, false, false));
            assertTrue (end.effects ().isEmpty (), page.name ());
            assertTrue (fixture.workspace.handle (touch (InputPhase.END), snapshot (page, "a", "b", false, false, false)).effects ().isEmpty ());
        }
    }


    @Test
    void missingClassifierIsRejectedEvenWhenTheOpaqueReferenceLooksUnchanged ()
    {
        for (final Page page: Page.values ())
        {
            final Fixture fixture = new Fixture (page);
            final ControllerSnapshot unknown = snapshot (page, "a", "a", false, false, true);
            fixture.workspace.start (unknown);
            assertNull (fixture.workspace.parameterSlotOrNull (KNOB, unknown));
            assertTrue (fixture.workspace.handle (motion (), unknown).effects ().isEmpty ());
            assertFalse (hasParameterLabel (fixture.workspace.activate (unknown)));
        }
    }


    @Test
    void masterRejectsContradictoryAutomationProjectAndWrongParameterRole ()
    {
        final Fixture fixture = new Fixture (Page.MASTER);
        final ControllerSnapshot coherent = snapshot (Page.MASTER, "a", "a", false, false, false);
        fixture.workspace.start (coherent);
        final ControllerBridgeSnapshot bridge = coherent.bridge ();
        final ControllerSnapshot wrongAutomation = copy (coherent, bridge.parameters (), new AutomationSnapshot ("project-b", true, true));
        assertNull (fixture.workspace.parameterSlotOrNull (KNOB, wrongAutomation));
        assertTrue (fixture.workspace.handle (motion (), wrongAutomation).effects ().isEmpty ());
        // Even an unassigned Master encoder must not admit release policy for a different project.
        final ControlId emptyKnob = PushControlIds.continuous ("KNOB5");
        assertTrue (fixture.workspace.handle (new ControllerInputEvent (1, 1, emptyKnob, InputKind.TOUCH, InputPhase.BEGIN, 127), wrongAutomation).effects ().isEmpty ());
        assertTrue (fixture.workspace.handle (new ControllerInputEvent (1, 1, emptyKnob, InputKind.TOUCH, InputPhase.END, 0), wrongAutomation).effects ().isEmpty ());
        final ParameterTargetSnapshot volume = bridge.parameters ().slots ().get (Page.MASTER.slot);
        final ParameterTargetSnapshot wrongRole = new ParameterTargetSnapshot (volume.target (), volume.name (), volume.value (), volume.modulatedValue (), volume.displayedValue (), volume.numberOfSteps (), volume.tolerance (), volume.enabled (), new ParameterTargetIdentitySnapshot ("project-master", "project-a", 0, 1));
        assertNull (fixture.workspace.parameterSlotOrNull (KNOB, copy (coherent, new ParameterBridgeSnapshot (Map.of (Page.MASTER.slot, wrongRole), Map.of (), java.util.Set.of ()), bridge.automation ())));
    }


    private static boolean hasParameterLabel (final CoreResult result)
    {
        return result.desiredOutput ().display ().commands ().stream ().anyMatch (command ->
            command instanceof DisplayCommand.TextAt text && "VALUE".equals (text.text ()) ||
                command instanceof DisplayCommand.TextBox box && "VALUE".equals (box.text ()));
    }


    private static ControllerInputEvent motion () { return new ControllerInputEvent (1, 1, KNOB, InputKind.RELATIVE, InputPhase.UPDATE, 1); }
    private static ControllerInputEvent touch (final InputPhase phase) { return new ControllerInputEvent (1, 1, KNOB, InputKind.TOUCH, phase, phase == InputPhase.END ? 0 : 127); }
    private static ParameterTargetRef target (final Page page, final String epoch) { return new ParameterTargetRef (ParameterTargetKind.LIVE, page.name () + epoch, 1); }


    private static ControllerSnapshot snapshot (final Page page, final String ownerEpoch, final String parameterEpoch, final boolean touched, final boolean modifiers, final boolean unclassified)
    {
        final String owner = page == Page.TRACK ? "track-" + parameterEpoch : "project-" + parameterEpoch;
        final ParameterTargetIdentitySnapshot identity = unclassified ? ParameterTargetIdentitySnapshot.empty () : new ParameterTargetIdentitySnapshot (page.domain, owner, 0, 0);
        final ParameterTargetSnapshot parameter = new ParameterTargetSnapshot (target (page, parameterEpoch), "VALUE", 64, 64, "VALUE", 128, 0, Optional.empty (), identity);
        final SelectedTrackSnapshot selected = new SelectedTrackSnapshot (1, "track-" + ownerEpoch, "Track", 0, "Instrument", true, false, false, true, false, true, false, TrackMonitorMode.AUTO, false, false, false, false, 0.5, 0.5, COLOR);
        final MasterSnapshot master = new MasterSnapshot (true, "project-" + ownerEpoch, "Project", true, false, false, false, false, "Master", COLOR, true, true, false, 0, 0);
        final ControllerBridgeSnapshot empty = ControllerBridgeSnapshot.empty ();
        final ControllerBridgeSnapshot bridge = new ControllerBridgeSnapshot (empty.transport (), selected, empty.sessionBank (), empty.layout (), empty.noteView (), empty.noteRepeat (), empty.drum (), new ParameterBridgeSnapshot (Map.of (page.slot, parameter), Map.of (), java.util.Set.of ()), empty.controllerMappingFeedback (), master, empty.project (), new AutomationSnapshot ("project-" + ownerEpoch, true, true));
        return new ControllerSnapshot (1, 1, new ShellCapabilities (Map.of ()), bridge, ClipCatalogSnapshot.empty (), Map.of (), Map.of (), Optional.empty (), modifiers ? MODIFIERS : Set.of (), touched ? Set.of (KNOB) : Set.of ());
    }


    private static ControllerSnapshot copy (final ControllerSnapshot original, final ParameterBridgeSnapshot parameters, final AutomationSnapshot automation)
    {
        final ControllerBridgeSnapshot b = original.bridge ();
        final ControllerBridgeSnapshot bridge = new ControllerBridgeSnapshot (b.transport (), b.selectedTrack (), b.sessionBank (), b.layout (), b.noteView (), b.noteRepeat (), b.drum (), parameters, b.controllerMappingFeedback (), b.master (), b.project (), automation);
        return new ControllerSnapshot (1, 1, original.capabilities (), bridge, original.clipCatalog (), original.armedClipTargets (), original.clipLaunchSessionTargets (), original.activeClipLaunchOwner (), original.pressedControls (), original.touchedControls ());
    }


    private enum Page
    {
        TRACK (ParameterSlot.SELECTED_TRACK_VOLUME, "channel-volume"), PROJECT (ParameterSlot.projectRemote (0), "project-remote"), MASTER (ParameterSlot.MASTER_MIX_VOLUME, "project-master");
        private final ParameterSlot slot;
        private final String domain;
        Page (final ParameterSlot slot, final String domain) { this.slot = slot; this.domain = domain; }
    }


    private static final class Fixture
    {
        private final RoutedWorkspace workspace;
        private Fixture (final Page page)
        {
            final ControllerView view = switch (page)
            {
                case TRACK -> new TrackMixerControlsView (new TrackMixerPageState (), false);
                case PROJECT -> new ProjectMacroControlsView ();
                case MASTER -> new MasterControlView ();
            };
            this.workspace = new RoutedWorkspace (CompiledWorkspace.compile (page.name (), page == Page.MASTER ? List.of (view) : List.of (view, new TrackSelectionStripView ())));
        }
    }
}
