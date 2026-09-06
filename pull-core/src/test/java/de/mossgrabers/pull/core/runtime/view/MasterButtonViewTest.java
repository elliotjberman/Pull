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
    void shortReleaseSelectsMasterAndSecondPressRestoresItsHistory ()
    {
        final Fixture f = new Fixture ();
        f.observe ("DEVICE_PARAMS", false);
        f.edge (InputPhase.BEGIN);
        assertTrue (f.edge (InputPhase.END).effects ().isEmpty ());
        assertEquals ("MASTER", f.navigation.legacyAlias ());
        f.edge (InputPhase.BEGIN);
        f.edge (InputPhase.END);
        assertEquals ("DEVICE_PARAMS", f.navigation.legacyAlias ());
    }

    @Test
    void modifiersDoNotChangeGestureAndShortReleaseReadsCurrentPage ()
    {
        final Fixture f = new Fixture ();
        f.modifiers = Set.of (PushControlIds.button ("SHIFT"), PushControlIds.button ("SELECT"), PushControlIds.button ("DELETE"));
        f.edge (InputPhase.BEGIN);
        f.observe ("MASTER", false);
        f.edge (InputPhase.END);
        assertEquals ("TRACK", f.navigation.legacyAlias ());
    }

    @Test
    void deferredLongAndEndAreBothAppliedLocallyOnAdmission ()
    {
        final Fixture f = new Fixture ();
        final var action = f.resolve ();
        assertEquals (Set.of (ControllerStateScope.ACTIVE_PARAMETERS), action.intent ().invalidates ());
        f.edge (InputPhase.LONG);
        f.edge (InputPhase.END);
        assertEquals ("TRACK", f.navigation.legacyAlias ());
        assertTrue (f.dispatch (action).effects ().isEmpty ());
        assertEquals ("TRACK", f.navigation.legacyAlias ());
        assertTrue (f.navigation.state ().temporary ().isEmpty ());
        assertFalse (f.view.executionRequirements ().ticksRequested ());
    }

    @Test
    void deferredShortReleaseIsFencedToItsReleasePage ()
    {
        final Fixture f = new Fixture ();
        final var action = f.resolve ();
        f.observe ("MASTER", false);
        f.edge (InputPhase.END);
        f.observe ("DEVICE_PARAMS", false);
        f.dispatch (action);
        assertEquals ("DEVICE_PARAMS", f.navigation.legacyAlias ());
    }

    @Test
    void browserSuppressesEdgesButLeavingItAllowsLaterRelease ()
    {
        final Fixture f = new Fixture ();
        f.observe ("BROWSER", false);
        f.edge (InputPhase.BEGIN);
        f.edge (InputPhase.LONG);
        f.edge (InputPhase.END);
        assertEquals ("BROWSER", f.navigation.legacyAlias ());
        f.edge (InputPhase.BEGIN);
        f.observe ("TRACK", false);
        f.edge (InputPhase.END);
        assertEquals ("MASTER", f.navigation.legacyAlias ());
        f.edge (InputPhase.BEGIN);
        f.edge (InputPhase.LONG);
        f.observe ("BROWSER", true);
        f.edge (InputPhase.END);
        assertEquals ("BROWSER", f.navigation.legacyAlias ());
        f.observe ("FRAME", true);
        f.tick ();
        assertEquals ("FRAME", f.navigation.legacyAlias ());
    }

    @Test
    void retainedGestureSurvivesPageCompositionAndRetiredGenerationIsInert ()
    {
        final Fixture f = new Fixture ();
        final var action = f.resolve ();
        f.dispatch (action);
        f.edge (InputPhase.LONG);
        final var next = CompiledWorkspace.compile ("next", List.of (f.retained));
        f.workspace.deactivateExcept (next);
        next.start (f.snapshot ());
        next.handle (f.input (InputPhase.END), f.snapshot ());
        assertEquals ("TRACK", f.navigation.legacyAlias ());
        f.view.deactivate ();
        f.dispatch (action);
        f.edge (InputPhase.END);
        assertEquals ("TRACK", f.navigation.legacyAlias ());
    }

    @Test
    void lightReflectsCorePageStateWhileHostLayoutRemainsUnchanged ()
    {
        final Fixture f = new Fixture ();
        f.edge (InputPhase.BEGIN);
        assertEquals (new RgbColor (255, 255, 255), f.edge (InputPhase.END).desiredOutput ().lights ().get (BUTTON));
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
        private final PageNavigation navigation = PageNavigation.defaults ();
        private final MasterButtonView view = new MasterButtonView (new ControllerPageTransitions (this.navigation));
        private final RetainedControllerView retained = new RetainedControllerView (this.view);
        private final CompiledWorkspace workspace = CompiledWorkspace.compile ("master-button", List.of (this.retained));
        private String mode = "TRACK";
        private boolean temporary;
        private long generation = 1;
        private long sequence;
        private long time;
        private Set<ControlId> modifiers = Set.of ();
        private Fixture () { this.workspace.start (this.snapshot ()); }
        private void observe (final String mode, final boolean temporary) { if (temporary) this.navigation.temporary (this.navigation.origin (), this.navigation.resolve (mode)); else this.navigation.select (this.navigation.resolve (mode)); }
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
