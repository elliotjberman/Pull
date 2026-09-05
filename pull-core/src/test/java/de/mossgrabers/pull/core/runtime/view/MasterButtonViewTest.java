// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.api.*;
import de.mossgrabers.pull.core.api.effect.*;
import de.mossgrabers.pull.core.api.event.*;
import de.mossgrabers.pull.core.api.output.RgbColor;
import de.mossgrabers.pull.core.runtime.PullCoreProvider;
import de.mossgrabers.pull.core.view.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MasterButtonViewTest
{
    private static final ControlId BUTTON = PushControlIds.button ("MASTERTRACK");

    @Test
    void shortReleaseSelectsOnlyTheMasterPageAndRestoresOnlyFromExactMasterMode ()
    {
        for (final String mode: List.of ("TRACK", "DEVICE_PARAMS", "MASTER", "MASTER_TEMP", "TRANSPORT"))
        {
            final Fixture f = new Fixture ();
            f.mode = mode;
            assertTrue (f.edge (InputPhase.BEGIN).effects ().isEmpty ());
            final CoreResult release = f.edge (InputPhase.END);
            assertEquals (List.of (mode.equals ("MASTER") ? SelectControllerModeEffect.restore (1) : new SelectControllerModeEffect (1, "MASTER")), release.effects ());
            assertTrue (release.effects ().stream ().allMatch (SelectControllerModeEffect.class::isInstance));
        }
    }

    @Test
    void modifiersDoNotChangeTheGestureAndShortReleaseReadsTheCurrentMode ()
    {
        final Fixture f = new Fixture ();
        f.modifiers = Set.of (PushControlIds.button ("SHIFT"), PushControlIds.button ("SELECT"), PushControlIds.button ("DELETE"));
        f.edge (InputPhase.BEGIN);
        f.observe ("MASTER", false);
        assertEquals (List.of (SelectControllerModeEffect.restore (2)), f.edge (InputPhase.END).effects ());
    }

    @Test
    void longReleaseWaitsForActualTemporaryFrameObservation ()
    {
        final Fixture f = new Fixture ();
        f.edge (InputPhase.BEGIN);
        assertEquals (List.of (new SelectControllerModeEffect (1, "FRAME", SelectControllerModeEffect.Operation.TEMPORARY)), f.edge (InputPhase.LONG).effects ());
        assertTrue (f.edge (InputPhase.END).effects ().isEmpty ());
        assertTrue (f.tick ().effects ().isEmpty ());
        f.observe ("FRAME", false);
        assertTrue (f.tick ().effects ().isEmpty ());
        f.observe ("FRAME", true);
        assertEquals (List.of (SelectControllerModeEffect.restore (3)), f.tick ().effects ());
        assertTrue (f.tick ().effects ().isEmpty ());
    }

    @Test
    void alreadyTemporaryFrameUsesALaterSampleEvenWithoutGenerationChange ()
    {
        final Fixture f = new Fixture ();
        f.mode = "FRAME";
        f.temporary = true;
        f.edge (InputPhase.BEGIN);
        f.edge (InputPhase.LONG);
        assertEquals (List.of (SelectControllerModeEffect.restore (1)), f.edge (InputPhase.END).effects ());
    }

    @Test
    void deferredBeginRetainsLongAndEndWithoutIssuingReturnBesideEntry ()
    {
        final Fixture f = new Fixture ();
        final var action = f.resolve ();
        assertEquals (Set.of (ControllerStateScope.ACTIVE_PARAMETERS), action.intent ().invalidates ());
        assertTrue (f.edge (InputPhase.LONG).effects ().isEmpty ());
        assertTrue (f.edge (InputPhase.END).effects ().isEmpty ());
        assertEquals (List.of (new SelectControllerModeEffect (1, "FRAME", SelectControllerModeEffect.Operation.TEMPORARY)), f.dispatch (action).effects ());
        assertTrue (f.tick ().effects ().isEmpty ());
        f.observe ("FRAME", true);
        assertEquals (List.of (SelectControllerModeEffect.restore (2)), f.tick ().effects ());
    }

    @Test
    void deferredShortReleaseKeepsItsExactReleaseOrigin ()
    {
        final Fixture f = new Fixture ();
        final var action = f.resolve ();
        f.observe ("MASTER", false);
        assertTrue (f.edge (InputPhase.END).effects ().isEmpty ());
        f.observe ("DEVICE_PARAMS", false);
        assertEquals (List.of (SelectControllerModeEffect.restore (2)), f.dispatch (action).effects ());
    }

    @Test
    void browserSuppressesIndividualEdgesIncludingLongReleaseButLeavingItAllowsLaterEdges ()
    {
        final Fixture f = new Fixture ();
        f.mode = "BROWSER";
        assertTrue (f.edge (InputPhase.BEGIN).effects ().isEmpty ());
        assertTrue (f.edge (InputPhase.LONG).effects ().isEmpty ());
        assertTrue (f.edge (InputPhase.END).effects ().isEmpty ());
        f.edge (InputPhase.BEGIN);
        f.observe ("TRACK", false);
        assertEquals (List.of (new SelectControllerModeEffect (2, "MASTER")), f.edge (InputPhase.END).effects ());
        f.edge (InputPhase.BEGIN);
        f.edge (InputPhase.LONG);
        f.observe ("FRAME", true);
        f.tick ();
        f.observe ("BROWSER", true);
        assertTrue (f.edge (InputPhase.END).effects ().isEmpty ());
        f.observe ("FRAME", true);
        assertTrue (f.tick ().effects ().isEmpty ());
    }

    @Test
    void nestedTemporaryPagesAndExternalChangesKeepTheManagersSingleRestoreSlot ()
    {
        final Fixture f = new Fixture ();
        f.mode = "AUTOMATION";
        f.temporary = true;
        f.edge (InputPhase.BEGIN);
        f.edge (InputPhase.LONG);
        f.observe ("FRAME", true);
        f.tick ();
        f.observe ("TRANSPORT", true);
        assertEquals (List.of (SelectControllerModeEffect.restore (3)), f.edge (InputPhase.END).effects ());
    }

    @Test
    void retainedGestureSurvivesPageReplacementAndOldGenerationReleaseIsInert ()
    {
        final Fixture f = new Fixture ();
        final var action = f.resolve ();
        f.dispatch (action);
        f.edge (InputPhase.LONG);
        final var next = CompiledWorkspace.compile ("next", List.of (f.retained));
        f.workspace.deactivateExcept (next);
        next.start (f.snapshot ());
        f.observe ("FRAME", true);
        assertEquals (List.of (SelectControllerModeEffect.restore (2)), next.handle (f.input (InputPhase.END), f.snapshot ()).effects ());
        f.view.deactivate ();
        assertTrue (f.dispatch (action).effects ().isEmpty ());
        assertTrue (f.edge (InputPhase.END).effects ().isEmpty ());
    }

    @Test
    void rejectedFrameEntryExpiresWithoutRestoringAnUnrelatedPage ()
    {
        final Fixture f = new Fixture ();
        f.edge (InputPhase.BEGIN);
        f.edge (InputPhase.LONG);
        f.edge (InputPhase.END);
        f.observe ("DEVICE_PARAMS", false);
        f.time += 5_000_000_000L;
        assertTrue (f.tick ().effects ().isEmpty ());
        assertFalse (f.view.executionRequirements ().ticksRequested ());
    }

    @Test
    void masterLightPreservesMasterTemporaryAndFrameStatesWithoutOptimism ()
    {
        final Fixture f = new Fixture ();
        f.edge (InputPhase.BEGIN);
        assertEquals (new RgbColor (60, 60, 60), f.edge (InputPhase.END).desiredOutput ().lights ().get (BUTTON));
        for (final String mode: List.of ("MASTER", "MASTER_TEMP", "FRAME"))
        {
            f.observe (mode, false);
            assertEquals (new RgbColor (255, 255, 255), f.tick ().desiredOutput ().lights ().get (BUTTON));
        }
        f.observe ("BROWSER", true);
        assertEquals (new RgbColor (60, 60, 60), f.tick ().desiredOutput ().lights ().get (BUTTON));
    }

    private static final class Fixture
    {
        private final MasterButtonView view = new MasterButtonView ();
        private final RetainedControllerView retained = new RetainedControllerView (this.view);
        private final CompiledWorkspace workspace = CompiledWorkspace.compile ("master-button", List.of (this.retained));
        private String mode = "TRACK";
        private boolean temporary;
        private long generation = 1;
        private long sequence;
        private long time;
        private Set<ControlId> modifiers = Set.of ();
        private Fixture () { this.workspace.start (this.snapshot ()); }
        private void observe (final String mode, final boolean temporary) { this.mode = mode; this.temporary = temporary; this.generation++; }
        private ControllerInputEvent input (final InputPhase phase) { this.sequence++; this.time++; return new ControllerInputEvent (this.sequence, this.time, BUTTON, InputKind.BUTTON, phase, phase == InputPhase.END ? 0 : 127); }
        private ResolvedControllerAction resolve () { return this.workspace.resolveAction (this.input (InputPhase.BEGIN), this.snapshot ()); }
        private CoreResult dispatch (final ResolvedControllerAction action) { return this.workspace.handleAction (action, this.snapshot ()); }
        private CoreResult edge (final InputPhase phase) { return phase == InputPhase.BEGIN ? this.dispatch (this.resolve ()) : this.workspace.handle (this.input (phase), this.snapshot ()); }
        private CoreResult tick () { this.sequence++; this.time++; return this.workspace.handle (new ControllerTickEvent (this.sequence, this.time), this.snapshot ()); }
        private ControllerSnapshot snapshot ()
        {
            final var e = ControllerBridgeSnapshot.empty ();
            final var layout = new ControllerLayoutSnapshot (this.generation, "PLAY", this.mode, false, false, 0, GridPressureConfiguration.OFF, DesiredNoteInputTranslation.unowned (), this.temporary ? "TRACK" : this.mode, "DEVICE_PARAMS", this.temporary);
            final var bridge = new ControllerBridgeSnapshot (e.transport (), e.selectedTrack (), e.sessionBank (), layout, e.noteView (), e.noteRepeat (), e.drum (), e.parameters (), e.controllerMappingFeedback (), e.master (), e.project ());
            return new ControllerSnapshot (this.sequence, this.time, new PullCoreProvider ().descriptor ().requiredCapabilities (), bridge, ClipCatalogSnapshot.empty (), Map.of (), Map.of (), Optional.empty (), this.modifiers, Set.of ());
        }
    }
}
