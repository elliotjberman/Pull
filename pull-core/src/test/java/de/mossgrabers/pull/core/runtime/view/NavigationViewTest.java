// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.api.*;
import de.mossgrabers.pull.core.api.effect.CurrentTrackNavigationEffect;
import de.mossgrabers.pull.core.api.effect.CurrentTrackNavigationEffect.Operation;
import de.mossgrabers.pull.core.api.event.*;
import de.mossgrabers.pull.core.api.output.RgbColor;
import de.mossgrabers.pull.core.runtime.PullCoreProvider;
import de.mossgrabers.pull.core.view.CompiledWorkspace;
import org.junit.jupiter.api.Test;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class NavigationViewTest
{
    @Test
    void mixerArrowsScrollOneTrackAndShiftPagesWithoutReordering ()
    {
        final Fixture fixture = new Fixture (NavigationView.Horizontal.MIXER);
        assertEquals (Operation.TRACK_SCROLL_PREVIOUS, fixture.press ("LEFT", false));
        assertEquals (Operation.TRACK_SCROLL_NEXT, fixture.press ("RIGHT", false));
        assertEquals (Operation.TRACK_PAGE_PREVIOUS, fixture.press ("LEFT", true));
        assertEquals (Operation.TRACK_PAGE_NEXT, fixture.press ("RIGHT", true));
    }

    @Test
    void vsLiveArrowsScrollOneTrackAndShiftPages ()
    {
        final Fixture fixture = new Fixture (NavigationView.Horizontal.SESSION);
        assertEquals (Operation.TRACK_SCROLL_PREVIOUS, fixture.press ("LEFT", false));
        assertEquals (Operation.TRACK_SCROLL_NEXT, fixture.press ("RIGHT", false));
        assertEquals (Operation.TRACK_PAGE_PREVIOUS, fixture.press ("LEFT", true));
        assertEquals (Operation.TRACK_PAGE_NEXT, fixture.press ("RIGHT", true));
    }

    @Test
    void allPagesStepScenesAndShiftPagesScenesWhileNonMixerHorizontalIsInert ()
    {
        for (final NavigationView.Horizontal mode: NavigationView.Horizontal.values ())
        {
            final Fixture fixture = new Fixture (mode);
            assertEquals (Operation.SCENE_SCROLL_PREVIOUS, fixture.press ("UP", false));
            assertEquals (Operation.SCENE_SCROLL_NEXT, fixture.press ("DOWN", false));
            assertEquals (Operation.SCENE_PAGE_PREVIOUS, fixture.press ("UP", true));
            assertEquals (Operation.SCENE_PAGE_NEXT, fixture.press ("DOWN", true));
        }
        final Fixture inert = new Fixture (NavigationView.Horizontal.INERT);
        assertTrue (inert.edge ("LEFT", InputPhase.BEGIN, false).effects ().isEmpty ());
        assertTrue (inert.edge ("RIGHT", InputPhase.BEGIN, true).effects ().isEmpty ());
    }

    @Test
    void lightsFollowObservedItemOrPageAvailabilityForTheCurrentModifier ()
    {
        for (final NavigationView.Horizontal mode: List.of (NavigationView.Horizontal.MIXER, NavigationView.Horizontal.SESSION))
        {
            final Fixture fixture = new Fixture (mode);
            fixture.trackNavigation = new BankNavigationSnapshot (16, false, true, true, false);
            fixture.sceneNavigation = new BankNavigationSnapshot (16, true, false, false, true);
            final RgbColor on = new RgbColor (60, 60, 60);
            final RgbColor off = new RgbColor (0, 0, 0);
            assertEquals (Map.of (button ("LEFT"), off, button ("RIGHT"), on, button ("UP"), on, button ("DOWN"), off), fixture.workspace.activate (fixture.snapshot (false)).desiredOutput ().lights ());
            assertEquals (Map.of (button ("LEFT"), on, button ("RIGHT"), off, button ("UP"), off, button ("DOWN"), on), fixture.workspace.activate (fixture.snapshot (true)).desiredOutput ().lights ());
            // Input is still submitted at the bank edge; a light is feedback, not command permission.
            assertEquals (Operation.TRACK_SCROLL_PREVIOUS, fixture.press ("LEFT", false));
            assertEquals (off, fixture.workspace.activate (fixture.snapshot (false)).desiredOutput ().lights ().get (button ("LEFT")));
            // Only a later explicit host observation changes the output.
            fixture.trackNavigation = new BankNavigationSnapshot (16, true, false, true, false);
            assertEquals (on, fixture.workspace.activate (fixture.snapshot (false)).desiredOutput ().lights ().get (button ("LEFT")));
        }
    }

    @Test
    void deferredHorizontalActionRetainsTheExactObservedWindowAndModifier ()
    {
        final Fixture fixture = new Fixture (NavigationView.Horizontal.MIXER);
        final var snapshot = fixture.snapshot (true);
        final var action = fixture.workspace.resolveAction (new ControllerInputEvent (1, 1, button ("LEFT"), InputKind.BUTTON, InputPhase.BEGIN, 127), snapshot);
        assertEquals (Set.of (ControllerStateScope.ACTIVE_PARAMETERS), action.intent ().invalidates ());
        fixture.navigationGeneration = 22;
        final var effects = fixture.workspace.dispatchAction (action, fixture.snapshot (false));
        assertEquals (List.of (new CurrentTrackNavigationEffect (11, "bank-a", Operation.TRACK_PAGE_PREVIOUS)), effects);
    }

    @Test
    void endsLongsAndUnavailableBanksDoNotIssueCommands ()
    {
        final Fixture fixture = new Fixture (NavigationView.Horizontal.MIXER);
        for (final String direction: List.of ("LEFT", "RIGHT", "UP", "DOWN"))
        {
            assertTrue (fixture.edge (direction, InputPhase.LONG, false).effects ().isEmpty ());
            assertTrue (fixture.edge (direction, InputPhase.END, false).effects ().isEmpty ());
        }
        fixture.available = false;
        assertTrue (fixture.edge ("LEFT", InputPhase.BEGIN, false).effects ().isEmpty ());
        assertTrue (fixture.edge ("UP", InputPhase.BEGIN, false).effects ().isEmpty ());
    }

    private static ControlId button (final String direction) { return PushControlIds.button ("ARROW_" + direction); }

    private static final class Fixture
    {
        private final CompiledWorkspace workspace;
        private long sequence;
        private long navigationGeneration = 11;
        private boolean available = true;
        private BankNavigationSnapshot trackNavigation = BankNavigationSnapshot.empty ();
        private BankNavigationSnapshot sceneNavigation = BankNavigationSnapshot.empty ();
        private Fixture (final NavigationView.Horizontal mode)
        {
            this.workspace = CompiledWorkspace.compile ("navigation", List.of (new NavigationView (mode)));
            this.workspace.start (this.snapshot (false));
        }
        private Operation press (final String direction, final boolean shift)
        {
            return assertInstanceOf (CurrentTrackNavigationEffect.class, this.edge (direction, InputPhase.BEGIN, shift).effects ().getFirst ()).operation ();
        }
        private CoreResult edge (final String direction, final InputPhase phase, final boolean shift)
        {
            this.sequence++;
            final var input = new ControllerInputEvent (this.sequence, this.sequence, button (direction), InputKind.BUTTON, phase, phase == InputPhase.END ? 0 : 127);
            final var snapshot = this.snapshot (shift);
            final var action = this.workspace.resolveAction (input, snapshot);
            return action == null ? this.workspace.handle (input, snapshot) : this.workspace.handleAction (action, snapshot);
        }
        private ControllerSnapshot snapshot (final boolean shift)
        {
            final var bank = this.available ? new CurrentTrackBankSnapshot (1, "bank-a", 0, Collections.nCopies (8, CurrentTrackSnapshot.empty ()), "cursor-a", true, 1, false, this.trackNavigation, this.sceneNavigation, 0, this.navigationGeneration) : CurrentTrackBankSnapshot.empty ();
            final var empty = ControllerBridgeSnapshot.empty ();
            final var bridge = new ControllerBridgeSnapshot (empty.transport (), empty.selectedTrack (), empty.sessionBank (), empty.layout (), empty.noteView (), empty.noteRepeat (), empty.drum (), empty.parameters (), empty.controllerMappingFeedback (), empty.master (), empty.project (), empty.automation (), empty.encoderConfiguration (), bank);
            return new ControllerSnapshot (this.sequence, this.sequence, new PullCoreProvider ().descriptor ().requiredCapabilities (), bridge, ClipCatalogSnapshot.empty (), Map.of (), Map.of (), Optional.empty (), shift ? Set.of (PushControlIds.button ("SHIFT")) : Set.of (), Set.of ());
        }
    }
}
