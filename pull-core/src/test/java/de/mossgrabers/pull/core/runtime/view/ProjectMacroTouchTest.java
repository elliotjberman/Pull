// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.api.AutomationSnapshot;
import de.mossgrabers.pull.core.api.BridgeSubscription;
import de.mossgrabers.pull.core.api.ClipCatalogSnapshot;
import de.mossgrabers.pull.core.api.ControlId;
import de.mossgrabers.pull.core.api.ControllerBridgeSnapshot;
import de.mossgrabers.pull.core.api.ControllerSnapshot;
import de.mossgrabers.pull.core.api.CoreResult;
import de.mossgrabers.pull.core.api.InputRouteMode;
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
import de.mossgrabers.pull.core.view.CompiledWorkspace;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectMacroTouchTest
{
    private static final ControlId KNOB = PushControlIds.continuous ("KNOB1");
    private static final ControlId DELETE = PushControlIds.button ("DELETE");
    private static final ParameterTargetRef TARGET = new ParameterTargetRef (ParameterTargetKind.LIVE, "project-a-macro", 1);
    private static final AutomationSnapshot WRITING = new AutomationSnapshot ("project-a", true, true);

    @Test
    void claimsCompleteTouchAndResetsBeforeRequestingExactTouch ()
    {
        final Fixture fixture = new Fixture ();
        final CoreResult initial = fixture.project.start (snapshot (TARGET, WRITING, false, false));
        assertEquals (InputRouteMode.EXCLUSIVE, initial.desiredInputRoutes ().modeOrNull (KNOB, InputKind.TOUCH));
        assertTrue (initial.desiredBridgeSubscriptions ().includes (BridgeSubscription.AUTOMATION));
        final CoreResult begin = fixture.project.handle (touch (InputPhase.BEGIN), snapshot (TARGET, WRITING, true, true));
        assertEquals (List.of (new ConsumeControllerButtonEffect (DELETE), new ResetParameterEffect (TARGET)), begin.effects ());
        assertEquals (Map.of (KNOB, TARGET), begin.desiredParameterTouches ().targets ());
        final CoreResult release = fixture.project.handle (touch (InputPhase.END), snapshot (TARGET, WRITING, false, false));
        assertTrue (release.desiredParameterTouches ().targets ().isEmpty ());
        assertEquals (List.of (new SetAutomationWriteEffect ("project-a", false)), release.effects ());
        assertTrue (fixture.project.handle (touch (InputPhase.END), snapshot (TARGET, WRITING, false, false)).effects ().isEmpty ());
    }

    @Test
    void touchEmphasisAndParameterValueFollowTheSnapshotInsteadOfTheRequestedEffect ()
    {
        final ProjectMacroControlsView view = new ProjectMacroControlsView ();
        final ControllerSnapshot untouched = snapshot (TARGET, WRITING, false, false);
        view.start (untouched);
        final var before = view.render (untouched).display ();
        view.handle (touch (InputPhase.BEGIN), snapshot (TARGET, WRITING, false, true));
        assertEquals (before, view.render (untouched).display ());
        assertNotEquals (before, view.render (snapshot (TARGET, WRITING, true, false)).display ());
        assertEquals (64, untouched.bridge ().parameters ().slots ().get (ParameterSlot.projectRemote (0)).value ());
    }


    @Test
    void releaseUsesCurrentPreferenceAndAuthoritativeWritingState ()
    {
        for (final AutomationSnapshot automation: List.of (new AutomationSnapshot ("project-a", true, false), new AutomationSnapshot ("project-a", false, true), AutomationSnapshot.empty ()))
        {
            final Fixture fixture = new Fixture ();
            fixture.project.start (snapshot (TARGET, WRITING, false, false));
            fixture.project.handle (touch (InputPhase.BEGIN), snapshot (TARGET, WRITING, true, false));
            assertTrue (fixture.project.handle (touch (InputPhase.END), snapshot (TARGET, automation, false, false)).effects ().isEmpty ());
        }
    }

    @Test
    void absentParameterStillConsumesDeleteAndPreservesReleasePreference ()
    {
        final Fixture fixture = new Fixture ();
        fixture.project.start (snapshot (null, WRITING, false, false));
        final CoreResult begin = fixture.project.handle (touch (InputPhase.BEGIN), snapshot (null, WRITING, true, true));
        assertEquals (List.of (new ConsumeControllerButtonEffect (DELETE)), begin.effects ());
        assertTrue (begin.desiredParameterTouches ().targets ().isEmpty ());
        assertEquals (List.of (new SetAutomationWriteEffect ("project-a", false)), fixture.project.handle (touch (InputPhase.END), snapshot (null, WRITING, false, false)).effects ());
    }

    private static ControllerInputEvent touch (final InputPhase phase)
    {
        return new ControllerInputEvent (1, 1, KNOB, InputKind.TOUCH, phase, phase == InputPhase.END ? 0 : 127);
    }

    private static ControllerSnapshot snapshot (final ParameterTargetRef target, final AutomationSnapshot automation, final boolean touched, final boolean delete)
    {
        final ControllerBridgeSnapshot empty = ControllerBridgeSnapshot.empty ();
        final ParameterBridgeSnapshot parameters = target == null ? ParameterBridgeSnapshot.empty () : new ParameterBridgeSnapshot (Map.of (ParameterSlot.projectRemote (0), new ParameterTargetSnapshot (target, "Cutoff", 64, 65, "64 units", 128, 0, Optional.empty (), new ParameterTargetIdentitySnapshot ("project-remote", "project-a", 0, 0))), Map.of ());
        final ControllerBridgeSnapshot bridge = new ControllerBridgeSnapshot (empty.transport (), empty.selectedTrack (), empty.sessionBank (), empty.layout (), empty.noteView (), empty.noteRepeat (), empty.drum (), parameters, empty.controllerMappingFeedback (), empty.master (), empty.project (), automation);
        return new ControllerSnapshot (1, 1, new ShellCapabilities (Map.of ()), bridge, new ClipCatalogSnapshot (0, List.of ()), Map.of (), Map.of (), Optional.empty (), delete ? Set.of (DELETE) : Set.of (), touched ? Set.of (KNOB) : Set.of ());
    }

    private static final class Fixture
    {
        private final ParameterTouchSession session = new ParameterTouchSession ();
        private final CompiledWorkspace project = CompiledWorkspace.compile ("project", List.of (new ProjectMacroControlsView (this.session), new TrackSelectionStripView ()));
    }
}
