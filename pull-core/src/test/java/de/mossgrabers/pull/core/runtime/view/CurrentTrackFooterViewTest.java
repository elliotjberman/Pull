// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.api.*;
import de.mossgrabers.pull.core.api.effect.*;
import de.mossgrabers.pull.core.api.event.*;
import de.mossgrabers.pull.core.api.output.*;
import de.mossgrabers.pull.core.view.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CurrentTrackFooterViewTest
{
    private static final ControlId ROW = PushControlIds.button ("ROW1_1");
    private static final RgbColor BLUE = new RgbColor (0, 80, 255);

    @Test
    void selectsAllEightTracksOnReleaseAndNeverOptimisticallyChangesFeedback ()
    {
        for (int index = 0; index < 8; index++)
        {
            final Fixture f = new Fixture ();
            f.row = PushControlIds.button ("ROW1_" + (index + 1));
            final var before = f.view.render (f.snapshot ());
            assertTrue (f.begin (false).isEmpty ());
            assertEquals (List.of (new CurrentTrackActionEffect (f.target (index), CurrentTrackActionEffect.Action.SELECT)), f.edge (InputPhase.END));
            assertEquals (before, f.view.render (f.snapshot ()));
        }
    }

    @ParameterizedTest
    @ValueSource (strings = { "DUPLICATE", "DELETE", "RECORD", "SELECT" })
    void releaseModifierPrecedenceAndConsumptionAreComplete (final String first)
    {
        final Fixture f = new Fixture ();
        f.begin (false);
        final List<String> precedence = List.of ("DUPLICATE", "DELETE", "RECORD", "SELECT");
        for (int index = precedence.indexOf (first); index < precedence.size (); index++) f.press (precedence.get (index));
        f.press ("SHIFT");
        final List<CoreEffect> effects = f.edge (InputPhase.END);
        assertEquals (new ConsumeControllerButtonEffect (PushControlIds.button (first)), effects.getFirst ());
        switch (first)
        {
            case "DUPLICATE" -> assertEquals (new CurrentTrackActionEffect (f.target (0), CurrentTrackActionEffect.Action.DUPLICATE), effects.getLast ());
            case "DELETE" -> assertEquals (new CurrentTrackActionEffect (f.target (0), CurrentTrackActionEffect.Action.REMOVE), effects.getLast ());
            case "RECORD" -> assertEquals (new SetCurrentTrackBooleanEffect (f.target (0), SetCurrentTrackBooleanEffect.Property.RECORD_ARMED, true), effects.getLast ());
            case "SELECT" -> assertEquals (1, effects.size (), "framework multiselect is deliberately a no-op");
        }
    }

    @Test
    void missingTrackStillConsumesHeldModifierAndUnmatchedReleaseIsInert ()
    {
        final Fixture f = new Fixture ();
        f.exists = false;
        f.press ("RECORD");
        assertTrue (f.edge (InputPhase.END).isEmpty ());
        f.begin (false);
        assertEquals (List.of (new ConsumeControllerButtonEffect (PushControlIds.button ("RECORD"))), f.edge (InputPhase.END));
        assertTrue (f.consumption.takeConsumed (PushControlIds.button ("RECORD")));
    }

    @Test
    void modifiersAndTargetAreCapturedAtReleaseRatherThanBegin ()
    {
        final Fixture f = new Fixture ();
        f.press ("DUPLICATE");
        f.begin (false);
        f.pressed.clear ();
        f.channel = "new-visible";
        f.bankGeneration++;
        assertEquals (List.of (new CurrentTrackActionEffect (f.target (0), CurrentTrackActionEffect.Action.SELECT)), f.edge (InputPhase.END));
    }

    @Test
    void selectedGroupExpandsThenEntersWithoutTreatingVisibilityAsPlaybackAcknowledgement ()
    {
        final Fixture f = new Fixture ();
        f.selected = true;
        f.group = true;
        f.begin (false);
        assertEquals (List.of (
            new SetCurrentTrackBooleanEffect (f.target (0), SetCurrentTrackBooleanEffect.Property.GROUP_EXPANDED, true),
            new CurrentTrackActionEffect (f.target (0), CurrentTrackActionEffect.Action.ENTER_SELECTED_GROUP)), f.edge (InputPhase.END));
        assertFalse (f.expanded);
        f.cursor = "pinned-other";
        f.begin (false);
        assertTrue (f.edge (InputPhase.END).isEmpty ());
    }

    @Test
    void shiftGroupToggleUsesReleaseModifierAndWaitsForAuthoritativeParity ()
    {
        final Fixture f = new Fixture ();
        f.selected = true;
        f.group = true;
        f.begin (false);
        f.press ("SHIFT");
        final var open = new SetCurrentTrackBooleanEffect (f.target (0), SetCurrentTrackBooleanEffect.Property.GROUP_EXPANDED, true);
        assertEquals (List.of (open), f.edge (InputPhase.END));
        f.begin (false);
        assertTrue (f.edge (InputPhase.END).isEmpty ());
        assertTrue (f.tick ().isEmpty ());
        f.expanded = true;
        assertEquals (List.of (new SetCurrentTrackBooleanEffect (f.target (0), SetCurrentTrackBooleanEffect.Property.GROUP_EXPANDED, false)), f.tick ());
    }

    @Test
    void selectedNonGroupSelectsTheLegacyDevicePageInCore ()
    {
        final Fixture f = new Fixture ();
        f.selected = true;
        f.begin (false);
        assertTrue (f.edge (InputPhase.END).isEmpty ());
        assertEquals ("DEVICE_PARAMS", f.pages.legacyAlias ());
    }

    @Test
    void deferredDeviceEntryRequiresTheOriginalCorePage ()
    {
        for (final boolean hiddenChange: List.of (false, true))
        {
            final Fixture f = new Fixture ();
            f.selected = true;
            f.begin (true);
            assertTrue (f.edge (InputPhase.END).isEmpty ());
            f.layout = new ControllerLayoutSnapshot (hiddenChange ? 4 : 5, "PLAY", "TRACK", false, false, 36, GridPressureConfiguration.OFF, DesiredNoteInputTranslation.unowned (), "TRACK", hiddenChange ? "VOLUME" : "", false);
            f.pages.select (f.pages.resolve (hiddenChange ? "VOLUME" : "FRAME"));
            assertTrue (f.dispatch ().isEmpty ());
            assertNotEquals ("DEVICE_PARAMS", f.pages.legacyAlias ());
            f.begin (false);
            assertTrue (f.edge (InputPhase.END).isEmpty ());
            assertEquals ("DEVICE_PARAMS", f.pages.legacyAlias ());
        }
    }

    @Test
    void longNavigatesParentOnceAndSuppressesReleaseWithOrWithoutAvailableParent ()
    {
        for (final boolean available: List.of (true, false))
        {
            final Fixture f = new Fixture ();
            f.parent = available;
            f.begin (false);
            final var effects = f.edge (InputPhase.LONG);
            assertEquals (new ConsumeControllerButtonEffect (ROW), effects.getFirst ());
            assertEquals (available ? 2 : 1, effects.size ());
            if (available) assertEquals (new NavigateTrackParentEffect (8, "track-0"), effects.getLast ());
            assertTrue (f.edge (InputPhase.LONG).isEmpty ());
            assertTrue (f.edge (InputPhase.END).isEmpty ());
        }
    }

    @Test
    void deferredReleaseConsumesCurrentModifierBeforeBarrierAndNeverReplaysConsumptionLater ()
    {
        final Fixture f = new Fixture ();
        f.begin (true);
        f.press ("DELETE");
        assertEquals (List.of (new ConsumeControllerButtonEffect (PushControlIds.button ("DELETE"))), f.edge (InputPhase.END));
        f.pressed.clear (); // The modifier releases before Snapback restores its host parameter.
        assertEquals (List.of (new CurrentTrackActionEffect (f.target (0), CurrentTrackActionEffect.Action.REMOVE)), f.dispatch ());
        assertTrue (f.dispatch ().isEmpty ());
    }

    @Test
    void deferredEndCapturesTargetAndCancelsIfItRebindsBeforeDispatch ()
    {
        final Fixture f = new Fixture ();
        f.begin (true);
        assertTrue (f.edge (InputPhase.END).isEmpty ());
        f.channel = "other";
        f.bankGeneration++;
        assertTrue (f.dispatch ().isEmpty ());
    }

    @Test
    void deferredLongAndEndRetainOnlyParentIntentAndConsumeRowAtPhysicalLong ()
    {
        final Fixture f = new Fixture ();
        f.parent = true;
        f.begin (true);
        assertEquals (List.of (new ConsumeControllerButtonEffect (ROW)), f.edge (InputPhase.LONG));
        assertTrue (f.edge (InputPhase.END).isEmpty ());
        assertEquals (List.of (new NavigateTrackParentEffect (8, "track-0")), f.dispatch ());
        assertTrue (f.dispatch ().isEmpty ());
    }

    @Test
    void departureCancelsDeferredGesturesAndQueuedToggles ()
    {
        final Fixture f = new Fixture ();
        f.begin (true);
        f.edge (InputPhase.END);
        f.view.deactivate ();
        assertTrue (f.dispatch ().isEmpty ());
        f.press ("RECORD");
        f.begin (false);
        f.edge (InputPhase.END);
        f.begin (false);
        f.edge (InputPhase.END);
        f.view.deactivate ();
        f.armed = true;
        assertTrue (f.tick ().isEmpty ());
        assertFalse (f.view.executionRequirements ().ticksRequested ());
    }

    @Test
    void armRequestsWaitForReadbackAndLightsUseOnlyLaterObservedHostState ()
    {
        final Fixture f = new Fixture ();
        f.press ("RECORD");
        f.begin (false);
        f.edge (InputPhase.END);
        assertEquals (BLUE, f.view.render (f.snapshot ()).lights ().get (ROW));
        f.begin (false);
        assertEquals (1, f.edge (InputPhase.END).size (), "queued parity emits only modifier consumption");
        assertTrue (f.tick ().isEmpty ());
        f.armed = true;
        assertEquals (new RgbColor (255, 0, 0), f.view.render (f.snapshot ()).lights ().get (ROW));
        assertEquals (List.of (new SetCurrentTrackBooleanEffect (f.target (0), SetCurrentTrackBooleanEffect.Property.RECORD_ARMED, false)), f.tick ());
        assertEquals (new RgbColor (255, 0, 0), f.view.render (f.snapshot ()).lights ().get (ROW));
        f.armed = false;
        f.tick ();
        assertEquals (BLUE, f.view.render (f.snapshot ()).lights ().get (ROW));
        assertFalse (f.view.executionRequirements ().ticksRequested ());
    }

    @Test
    void footerPreservesPinGroupInactiveAndBoundedNameFeedback ()
    {
        final Fixture f = new Fixture ();
        f.selected = true;
        f.pinned = true;
        f.name = "A very long instrument name";
        final ViewOutput output = f.view.render (f.snapshot ());
        assertTrue (output.display ().commands ().stream ().anyMatch (command -> command instanceof DisplayCommand.Icon icon && icon.icon () == DisplayIcon.PIN));
        assertTrue (output.display ().commands ().stream ().anyMatch (command -> command instanceof DisplayCommand.TextBox text && text.text ().equals ("A very long ")));
        f.pinned = false;
        f.group = true;
        f.expanded = true;
        assertTrue (f.view.render (f.snapshot ()).display ().commands ().stream ().anyMatch (command -> command instanceof DisplayCommand.Icon icon && icon.icon () == DisplayIcon.GROUP_TRACK_OPEN));
        f.active = false;
        assertEquals (new RgbColor (0, 0, 0), f.view.render (f.snapshot ()).lights ().get (ROW));
        f.exists = false;
        assertFalse (f.view.render (f.snapshot ()).display ().commands ().stream ().anyMatch (command -> command instanceof DisplayCommand.TextBox text && text.text ().equals ("A very long ")));
    }

    @Test
    void stopHeldOutsideSessionDoesNotChangeNormalFooterSemantics ()
    {
        final Fixture f = new Fixture ();
        f.press ("STOP_CLIP");
        assertTrue (f.begin (false).isEmpty ());
        assertEquals (List.of (new CurrentTrackActionEffect (f.target (0), CurrentTrackActionEffect.Action.SELECT)), f.edge (InputPhase.END));
    }

    private static final class Fixture
    {
        final ButtonGestureConsumption consumption = new ButtonGestureConsumption (Set.of (PushControlIds.button ("RECORD")));
        final SessionStopGesture stop = new SessionStopGesture ();
        final PageNavigation pages = PageNavigation.defaults ();
        final CurrentTrackFooterView view = new CurrentTrackFooterView (this.consumption, this.stop, this.pages);
        final CompiledWorkspace workspace;
        SessionBankSnapshot session = SessionBankSnapshot.empty ();
        final Set<ControlId> pressed = new HashSet<> ();
        ControlId row = ROW;
        String channel = "track-0";
        String cursor = "track-0";
        String name = "Instrument";
        long bankGeneration = 2;
        long sequence = 1;
        boolean selected;
        boolean exists = true;
        boolean active = true;
        boolean armed;
        boolean group;
        boolean expanded;
        boolean pinned;
        boolean parent;
        ResolvedControllerAction deferred;
        ControllerLayoutSnapshot layout = new ControllerLayoutSnapshot (4, "PLAY", "TRACK", false, false, 36, GridPressureConfiguration.OFF);

        Fixture ()
        {
            final ControllerView top = new ControllerView ()
            {
                public String id () { return "blank-top"; }
                public ViewProfile profile () { return ViewProfile.fixed ("default", Set.of (new SurfaceClaim (SurfaceArea.DISPLAY_PARAMETERS, SurfaceClaim.Kind.OUTPUT)), Set.of ()); }
                public ViewOutput render (final ControllerSnapshot snapshot) { return new ViewOutput (Map.of (), Map.of (), new ControllerDisplayScene (960, 143, List.of (new DisplayCommand.Rectangle (0, 0, 960, 143, new RgbColor (0, 0, 0))))); }
            };
            this.workspace = CompiledWorkspace.compile ("footer", List.of (top, this.view));
            this.workspace.start (this.snapshot ());
        }
        void press (final String button) { this.pressed.add (PushControlIds.button (button)); }
        List<CoreEffect> begin (final boolean defer)
        {
            this.pressed.add (this.row);
            final var input = this.input (InputPhase.BEGIN);
            this.deferred = this.workspace.resolveAction (input, this.snapshot ());
            return defer ? List.of () : this.dispatch ();
        }
        List<CoreEffect> dispatch () { return this.workspace.handleAction (this.deferred, this.snapshot ()).effects (); }
        List<CoreEffect> edge (final InputPhase phase)
        {
            if (phase == InputPhase.END) this.pressed.remove (this.row);
            return this.workspace.handle (this.input (phase), this.snapshot ()).effects ();
        }
        List<CoreEffect> tick ()
        {
            this.sequence++;
            return this.workspace.handle (new ControllerTickEvent (this.sequence, this.sequence), this.snapshot ()).effects ();
        }
        ControllerInputEvent input (final InputPhase phase)
        {
            this.sequence++;
            return new ControllerInputEvent (this.sequence, this.sequence, this.row, InputKind.BUTTON, phase, phase == InputPhase.END ? 0 : 127);
        }
        CurrentTrackTarget target (final int index) { return new CurrentTrackTarget (this.bankGeneration, "bank-a", index, index == 0 ? this.channel : "track-" + index); }
        ControllerSnapshot snapshot ()
        {
            final List<CurrentTrackSnapshot> tracks = new ArrayList<> ();
            for (int index = 0; index < 8; index++)
            {
                if (index == 0 && !this.exists) tracks.add (CurrentTrackSnapshot.empty ());
                else tracks.add (new CurrentTrackSnapshot (new SessionTrackSnapshot (index == 0 ? this.channel : "track-" + index, index, index == 0 ? this.name : "Track " + index, true, index == 0 && this.selected, this.active, index == 0 && this.armed, false, false, false, this.group ? SessionTrackType.GROUP : SessionTrackType.INSTRUMENT, BLUE), this.group && this.expanded, 0, 0));
            }
            final var bank = new CurrentTrackBankSnapshot (this.bankGeneration, "bank-a", 0, tracks, this.cursor, this.pinned, 8, this.parent);
            final var empty = ControllerBridgeSnapshot.empty ();
            final var bridge = new ControllerBridgeSnapshot (empty.transport (), empty.selectedTrack (), SessionBankSnapshot.empty (), this.layout, empty.noteView (), empty.noteRepeat (), empty.drum (), empty.parameters (), empty.controllerMappingFeedback (), empty.master (), empty.project (), empty.automation (), empty.encoderConfiguration (), bank);
            return new ControllerSnapshot (this.sequence, this.sequence, new ShellCapabilities (Map.of ()), bridge, ClipCatalogSnapshot.empty (), Map.of (), Map.of (), Optional.empty (), this.pressed, Set.of ());
        }
    }
}
