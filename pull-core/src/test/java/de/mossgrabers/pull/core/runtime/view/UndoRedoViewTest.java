// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.api.ClipCatalogSnapshot;
import de.mossgrabers.pull.core.api.ControlId;
import de.mossgrabers.pull.core.api.ControllerBridgeSnapshot;
import de.mossgrabers.pull.core.api.ControllerLayoutSnapshot;
import de.mossgrabers.pull.core.api.ControllerSnapshot;
import de.mossgrabers.pull.core.api.CoreResult;
import de.mossgrabers.pull.core.api.DrumContextSnapshot;
import de.mossgrabers.pull.core.api.InputRouteMode;
import de.mossgrabers.pull.core.api.MasterSnapshot;
import de.mossgrabers.pull.core.api.ParameterBridgeSnapshot;
import de.mossgrabers.pull.core.api.ProjectSnapshot;
import de.mossgrabers.pull.core.api.PushControlIds;
import de.mossgrabers.pull.core.api.SelectedTrackSnapshot;
import de.mossgrabers.pull.core.api.TransportSnapshot;
import de.mossgrabers.pull.core.api.effect.ProjectHistoryAction;
import de.mossgrabers.pull.core.api.effect.ProjectHistoryEffect;
import de.mossgrabers.pull.core.api.event.ControllerInputEvent;
import de.mossgrabers.pull.core.api.event.InputKind;
import de.mossgrabers.pull.core.api.event.InputPhase;
import de.mossgrabers.pull.core.api.output.RgbColor;
import de.mossgrabers.pull.core.runtime.PullCoreProvider;
import de.mossgrabers.pull.core.view.CompiledWorkspace;
import de.mossgrabers.pull.core.view.RoutedWorkspace;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


class UndoRedoViewTest
{
    private static final ControlId UNDO = PushControlIds.button ("UNDO");
    private static final ControlId SHIFT = PushControlIds.button ("SHIFT");


    @Test
    void releaseUsesCurrentShiftAndAllOtherEdgesAreInert ()
    {
        final Fixture fixture = new Fixture ();
        assertEquals (InputRouteMode.EXCLUSIVE, fixture.initial.desiredInputRoutes ().modeOrNull (UNDO, InputKind.BUTTON));
        assertTrue (fixture.edge (InputPhase.END).effects ().isEmpty ());
        assertTrue (fixture.edge (InputPhase.BEGIN).effects ().isEmpty ());
        assertTrue (fixture.edge (InputPhase.LONG).effects ().isEmpty ());
        assertEquals (List.of (new ProjectHistoryEffect ("project-a", ProjectHistoryAction.UNDO)), fixture.edge (InputPhase.END).effects ());
        fixture.edge (InputPhase.BEGIN);
        fixture.pressed.add (SHIFT);
        assertEquals (List.of (new ProjectHistoryEffect ("project-a", ProjectHistoryAction.REDO)), fixture.edge (InputPhase.END).effects ());
        fixture.edge (InputPhase.BEGIN);
        fixture.pressed.remove (SHIFT);
        assertEquals (List.of (new ProjectHistoryEffect ("project-a", ProjectHistoryAction.UNDO)), fixture.edge (InputPhase.END).effects ());
    }


    @Test
    void availabilityAndHeldFeedbackRenderReadbackInsteadOfAssumingTheRequestWorked ()
    {
        final Fixture fixture = new Fixture ();
        assertEquals (new RgbColor (60, 60, 60), fixture.initial.desiredOutput ().lights ().get (UNDO));
        assertEquals (new RgbColor (255, 255, 255), fixture.edge (InputPhase.BEGIN).desiredOutput ().lights ().get (UNDO));
        assertEquals (new RgbColor (60, 60, 60), fixture.edge (InputPhase.END).desiredOutput ().lights ().get (UNDO));
        fixture.canUndo = false;
        assertEquals (new RgbColor (0, 0, 0), fixture.edge (InputPhase.BEGIN).desiredOutput ().lights ().get (UNDO));
        assertTrue (fixture.edge (InputPhase.END).effects ().isEmpty ());
        fixture.pressed.add (SHIFT);
        assertEquals (new RgbColor (255, 255, 255), fixture.edge (InputPhase.BEGIN).desiredOutput ().lights ().get (UNDO));
        fixture.canRedo = false;
        assertTrue (fixture.edge (InputPhase.END).effects ().isEmpty ());
    }


    @Test
    void projectChangeCancelsReleaseUntilFreshPress ()
    {
        final Fixture fixture = new Fixture ();
        fixture.edge (InputPhase.BEGIN);
        fixture.project = "project-b";
        assertTrue (fixture.edge (InputPhase.END).effects ().isEmpty ());
        fixture.edge (InputPhase.BEGIN);
        assertEquals (List.of (new ProjectHistoryEffect ("project-b", ProjectHistoryAction.UNDO)), fixture.edge (InputPhase.END).effects ());
    }

    @Test
    void pendingProjectNavigationDisablesActionAndFeedback ()
    {
        final Fixture fixture = new Fixture ();
        fixture.edge (InputPhase.BEGIN);
        fixture.pending = true;
        final CoreResult release = fixture.edge (InputPhase.END);
        assertTrue (release.effects ().isEmpty ());
        assertEquals (new RgbColor (0, 0, 0), release.desiredOutput ().lights ().get (UNDO));
    }


    private static final class Fixture
    {
        private final RoutedWorkspace workspace = new RoutedWorkspace (CompiledWorkspace.compile ("history", List.of (new UndoRedoView ())));
        private final Set<ControlId> pressed = new HashSet<> ();
        private boolean canUndo = true;
        private boolean canRedo = true;
        private boolean pending;
        private String project = "project-a";
        private long sequence;
        private final CoreResult initial = this.workspace.start (this.snapshot ());


        private CoreResult edge (final InputPhase phase)
        {
            if (phase == InputPhase.BEGIN)
                this.pressed.add (UNDO);
            else if (phase == InputPhase.END)
                this.pressed.remove (UNDO);
            this.sequence++;
            return this.workspace.handle (new ControllerInputEvent (this.sequence, this.sequence, UNDO, InputKind.BUTTON, phase, phase == InputPhase.END ? 0 : 127), this.snapshot ());
        }


        private ControllerSnapshot snapshot ()
        {
            final ProjectSnapshot project = new ProjectSnapshot (true, this.project, "Project", false, false, false, this.pending, this.canUndo, this.canRedo);
            final ControllerBridgeSnapshot bridge = new ControllerBridgeSnapshot (TransportSnapshot.empty (), SelectedTrackSnapshot.empty (), ControllerLayoutSnapshot.empty (), DrumContextSnapshot.empty (), ParameterBridgeSnapshot.empty (), MasterSnapshot.empty (), project);
            return new ControllerSnapshot (this.sequence, this.sequence, new PullCoreProvider ().descriptor ().requiredCapabilities (), bridge, ClipCatalogSnapshot.empty (), Map.of (), Map.of (), Optional.empty (), this.pressed, Set.of ());
        }
    }
}
