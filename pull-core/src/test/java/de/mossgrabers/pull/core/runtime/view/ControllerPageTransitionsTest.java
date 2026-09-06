// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.api.*;
import de.mossgrabers.pull.core.api.effect.*;
import de.mossgrabers.pull.core.api.event.*;
import de.mossgrabers.pull.core.runtime.PullCoreProvider;
import de.mossgrabers.pull.core.view.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ControllerPageTransitionsTest
{
    @Test
    void repeatedLongPressReplacesOldReturnDebtBeforeTheSecondEntryIsObserved ()
    {
        for (final String button: List.of ("ACCENT", "AUTOMATION", "MASTERTRACK"))
        {
            final Fixture f = new Fixture ();
            f.edge (button, InputPhase.BEGIN);
            f.edge (button, InputPhase.LONG);
            assertTrue (f.edge (button, InputPhase.END).isEmpty ());
            f.edge (button, InputPhase.BEGIN);
            f.edge (button, InputPhase.LONG);
            f.observe (page (button), true);
            assertTrue (f.tick ().isEmpty (), "the earlier END must not close the newer physical hold");
            assertEquals (List.of (SelectControllerModeEffect.restore (2)), f.edge (button, InputPhase.END));
            assertTrue (f.tick ().isEmpty ());
        }
    }

    @Test
    void everyCrossControlLongPressSupersedesOnlyThePreviousTemporaryOwner ()
    {
        for (final String older: List.of ("ACCENT", "AUTOMATION", "MASTERTRACK", "METRONOME"))
            for (final String newer: List.of ("ACCENT", "AUTOMATION", "MASTERTRACK", "METRONOME"))
            {
                if (older.equals (newer)) continue;
                final Fixture f = new Fixture ();
                f.edge (older, InputPhase.BEGIN);
                f.edge (older, InputPhase.LONG);
                f.edge (newer, InputPhase.BEGIN);
                f.edge (newer, InputPhase.LONG);
                f.observe (page (newer), true);
                assertTrue (f.edge (older, InputPhase.END).isEmpty (), older + " must not restore over " + newer);
                assertTrue (f.tick ().isEmpty ());
                assertEquals (newer.equals ("METRONOME") ? List.of () : List.of (SelectControllerModeEffect.restore (2)), f.edge (newer, InputPhase.END));
            }
    }

    @Test
    void acknowledgedLongHoldsSurviveTheMissingAcknowledgementTimeout ()
    {
        for (final String button: List.of ("ACCENT", "AUTOMATION", "MASTERTRACK"))
        {
            final Fixture f = new Fixture ();
            f.edge (button, InputPhase.BEGIN);
            f.edge (button, InputPhase.LONG);
            f.observe (page (button), true);
            assertTrue (f.tick ().isEmpty ());
            f.time += 20_000_000_000L;
            f.generation++;
            f.drumBase = 36;
            f.translation = new DesiredNoteInputTranslation (true, java.util.stream.IntStream.range (0, 128).map (index -> index >= 36 && index <= 99 ? index : -1).boxed ().toList (), java.util.stream.IntStream.range (0, 128).map (index -> index == 0 ? 0 : 64).boxed ().toList ());
            assertTrue (f.tick ().isEmpty ());
            assertEquals (List.of (SelectControllerModeEffect.restore (3)), f.edge (button, InputPhase.END));
        }
    }

    @Test
    void missingAcknowledgementExpiresWithoutClosingARecentlyUnrelatedPage ()
    {
        final Fixture f = new Fixture ();
        f.edge ("ACCENT", InputPhase.BEGIN);
        f.edge ("ACCENT", InputPhase.LONG);
        f.edge ("ACCENT", InputPhase.END);
        f.time += 5_000_000_000L;
        assertTrue (f.tick ().isEmpty ());
        f.observe ("ACCENT", true);
        assertTrue (f.tick ().isEmpty ());
    }

    @Test
    void deferredEntriesRejectEveryChangedPartOfTheirFrozenOrigin ()
    {
        for (final String button: List.of ("ACCENT", "AUTOMATION", "MASTERTRACK", "METRONOME"))
            for (final boolean hiddenChange: List.of (false, true))
            {
                final Fixture f = new Fixture ();
                final var action = f.resolve (button);
                f.edge (button, InputPhase.LONG);
                f.edge (button, InputPhase.END);
                if (hiddenChange) f.previous = "VOLUME";
                else f.observe ("PAN", false);
                assertTrue (f.dispatch (action).isEmpty (), "do not emit an effect the parent will reject as stale");
                assertTrue (f.tick ().isEmpty ());
            }
    }

    @Test
    void deferredShortPageEffectsAreCancelledWithoutRetargeting ()
    {
        for (final String button: List.of ("MASTERTRACK", "METRONOME"))
        {
            final Fixture f = new Fixture ();
            if (button.equals ("METRONOME")) f.observe ("TRANSPORT", true);
            final var action = f.resolve (button);
            f.edge (button, InputPhase.END);
            f.observe ("PAN", false);
            assertTrue (f.dispatch (action).isEmpty ());
        }
    }

    @Test
    void deferredPageRequestsUseTheLatestPhysicalPageIntentWhenAdmittedTogether ()
    {
        final Fixture f = new Fixture ();
        final var first = f.resolve ("ACCENT");
        f.edge ("ACCENT", InputPhase.LONG);
        f.edge ("ACCENT", InputPhase.END);
        final var second = f.resolve ("MASTERTRACK");
        f.edge ("MASTERTRACK", InputPhase.END);
        final var third = f.resolve ("AUTOMATION");
        f.edge ("AUTOMATION", InputPhase.LONG);
        final List<CoreEffect> effects = new ArrayList<> ();
        effects.addAll (f.dispatch (first));
        effects.addAll (f.dispatch (second));
        effects.addAll (f.dispatch (third));
        assertEquals (List.of (new SelectControllerModeEffect (1, "AUTOMATION", SelectControllerModeEffect.Operation.TEMPORARY)), effects);
        f.observe ("AUTOMATION", true);
        assertTrue (f.tick ().isEmpty ());
        assertEquals (List.of (SelectControllerModeEffect.restore (2)), f.edge ("AUTOMATION", InputPhase.END));
    }

    @Test
    void shortPageIntentsAlsoSupersedeAnOlderUnsubmittedShortRequest ()
    {
        final Fixture f = new Fixture ();
        final var first = f.resolve ("MASTERTRACK");
        f.edge ("MASTERTRACK", InputPhase.END);
        final var second = f.resolve ("MASTERTRACK");
        f.edge ("MASTERTRACK", InputPhase.END);
        assertTrue (f.dispatch (first).isEmpty ());
        assertEquals (List.of (new SelectControllerModeEffect (1, "MASTER")), f.dispatch (second));
    }

    @Test
    void acknowledgementMustBelongToTheRequestedUnderlyingAndPreviousMode ()
    {
        final Fixture f = new Fixture ();
        f.edge ("ACCENT", InputPhase.BEGIN);
        f.edge ("ACCENT", InputPhase.LONG);
        f.edge ("ACCENT", InputPhase.END);
        f.observe ("ACCENT", true);
        f.previous = "PAN";
        assertTrue (f.tick ().isEmpty ());
        f.previous = "DEVICE_PARAMS";
        f.active = "VOLUME";
        assertTrue (f.tick ().isEmpty ());
        f.active = "TRACK";
        assertEquals (List.of (SelectControllerModeEffect.restore (2)), f.tick ());
    }

    @Test
    void acknowledgedReturnIsRetiredByAnInterveningPageButNotNativeMapUpdates ()
    {
        for (final String button: List.of ("ACCENT", "AUTOMATION", "MASTERTRACK"))
        {
            final Fixture f = new Fixture ();
            f.edge (button, InputPhase.BEGIN);
            f.edge (button, InputPhase.LONG);
            f.observe (page (button), true);
            f.tick ();
            f.generation++;
            assertTrue (f.tick ().isEmpty (), "an unrelated layout generation alone does not lose the slot");
            f.observe ("PAN", true);
            f.tick ();
            f.observe (page (button), true);
            assertTrue (f.edge (button, InputPhase.END).isEmpty (), "returning to the same page cannot revive retired ownership");
        }
    }

    private static String page (final String button) { return switch (button) { case "MASTERTRACK" -> "FRAME"; case "METRONOME" -> "TRANSPORT"; default -> button; }; }

    private static final class Fixture
    {
        private final ControllerPageTransitions pages = new ControllerPageTransitions ();
        private final CompiledWorkspace workspace = CompiledWorkspace.compile ("globals", List.of (
            new AccentControlView (this.pages), new MasterButtonView (this.pages),
            new AutomationControlView (new AutomationControlState (), this.pages),
            new MetronomeControlView (new AuthoritativeBooleanToggle<> (), this.pages)));
        private long sequence;
        private long time;
        private long generation = 1;
        private String mode = "TRACK";
        private String active = "TRACK";
        private String previous = "DEVICE_PARAMS";
        private boolean temporary;
        private int drumBase;
        private DesiredNoteInputTranslation translation = DesiredNoteInputTranslation.unowned ();
        private final Set<ControlId> pressed = new HashSet<> ();
        private Fixture () { this.workspace.start (this.snapshot ()); }
        private void observe (final String mode, final boolean temporary) { this.mode = mode; this.temporary = temporary; if (!temporary) this.active = mode; this.generation++; }
        private ControllerInputEvent input (final String button, final InputPhase phase)
        {
            final ControlId control = PushControlIds.button (button);
            if (phase == InputPhase.BEGIN) this.pressed.add (control);
            if (phase == InputPhase.END) this.pressed.remove (control);
            this.sequence++; this.time++;
            return new ControllerInputEvent (this.sequence, this.time, control, InputKind.BUTTON, phase, phase == InputPhase.END ? 0 : 127);
        }
        private ResolvedControllerAction resolve (final String button) { return this.workspace.resolveAction (this.input (button, InputPhase.BEGIN), this.snapshot ()); }
        private List<CoreEffect> dispatch (final ResolvedControllerAction action) { return this.workspace.dispatchAction (action, this.snapshot ()); }
        private List<CoreEffect> edge (final String button, final InputPhase phase) { return phase == InputPhase.BEGIN ? this.dispatch (this.resolve (button)) : this.workspace.handle (this.input (button, phase), this.snapshot ()).effects (); }
        private List<CoreEffect> tick () { this.sequence++; this.time++; return this.workspace.handle (new ControllerTickEvent (this.sequence, this.time), this.snapshot ()).effects (); }
        private ControllerSnapshot snapshot ()
        {
            final var e = ControllerBridgeSnapshot.empty ();
            final var layout = new ControllerLayoutSnapshot (this.generation, "PLAY", this.mode, false, false, this.drumBase, GridPressureConfiguration.OFF, this.translation, this.active, this.previous, this.temporary);
            final var bridge = new ControllerBridgeSnapshot (e.transport (), e.selectedTrack (), e.sessionBank (), layout, e.noteView (), e.noteRepeat (), e.drum (), e.parameters (), e.controllerMappingFeedback (), e.master (), e.project (), new AutomationSnapshot ("project-a", false, false), e.encoderConfiguration (), e.currentTrackBank (), e.transportSettings (), new ControllerSettingsSnapshot (true, false, "VOLUME", 0, CursorSendBankSnapshot.empty (), false, 64));
            return new ControllerSnapshot (this.sequence, this.time, new PullCoreProvider ().descriptor ().requiredCapabilities (), bridge, ClipCatalogSnapshot.empty (), Map.of (), Map.of (), Optional.empty (), this.pressed, Set.of ());
        }
    }
}
