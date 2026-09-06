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
    void localEntryAndReturnNeedNoHostLayoutAcknowledgement ()
    {
        for (final String button: List.of ("ACCENT", "AUTOMATION", "MASTERTRACK"))
        {
            final Fixture f = new Fixture ();
            f.edge (button, InputPhase.BEGIN);
            assertTrue (f.edge (button, InputPhase.LONG).isEmpty ());
            assertEquals (page (button), f.navigation.legacyAlias ());
            assertTrue (f.navigation.state ().temporary ().isPresent ());
            assertTrue (f.edge (button, InputPhase.END).isEmpty ());
            assertEquals ("TRACK", f.navigation.legacyAlias ());
        }
    }

    @Test
    void repeatedLongGesturesHaveIndependentTemporaryOwners ()
    {
        for (final String button: List.of ("ACCENT", "AUTOMATION", "MASTERTRACK"))
        {
            final Fixture f = new Fixture ();
            f.edge (button, InputPhase.BEGIN);
            f.edge (button, InputPhase.LONG);
            final long first = f.navigation.state ().temporaryToken ();
            f.edge (button, InputPhase.END);
            f.edge (button, InputPhase.BEGIN);
            f.edge (button, InputPhase.LONG);
            assertTrue (f.navigation.state ().temporaryToken () > first);
            f.tick ();
            assertEquals (page (button), f.navigation.legacyAlias ());
            f.edge (button, InputPhase.END);
            assertEquals ("TRACK", f.navigation.legacyAlias ());
        }
    }

    @Test
    void everyCrossControlHoldSupersedesOnlyThePreviousTemporaryOwner ()
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
                f.edge (older, InputPhase.END);
                assertEquals (page (newer), f.navigation.legacyAlias ());
                f.edge (newer, InputPhase.END);
                assertEquals (newer.equals ("METRONOME") ? "TRANSPORT" : "TRACK", f.navigation.legacyAlias ());
            }
    }

    @Test
    void metronomeLatchesAndASecondPlainPressRestores ()
    {
        final Fixture f = new Fixture ();
        f.edge ("METRONOME", InputPhase.BEGIN);
        f.edge ("METRONOME", InputPhase.LONG);
        f.edge ("METRONOME", InputPhase.END);
        assertEquals ("TRANSPORT", f.navigation.legacyAlias ());
        f.edge ("METRONOME", InputPhase.BEGIN);
        f.edge ("METRONOME", InputPhase.END);
        assertEquals ("TRACK", f.navigation.legacyAlias ());
    }

    @Test
    void localHoldsSurviveElapsedTimeAndNativeMappingUpdates ()
    {
        final Fixture f = new Fixture ();
        f.edge ("ACCENT", InputPhase.BEGIN);
        f.edge ("ACCENT", InputPhase.LONG);
        f.time += 20_000_000_000L;
        f.generation++;
        f.drumBase = 36;
        f.tick ();
        assertEquals ("ACCENT", f.navigation.legacyAlias ());
        f.edge ("ACCENT", InputPhase.END);
        assertEquals ("TRACK", f.navigation.legacyAlias ());
    }

    @Test
    void deferredEntriesRejectInterveningPageOrWorkspaceChanges ()
    {
        for (final String button: List.of ("ACCENT", "AUTOMATION", "MASTERTRACK", "METRONOME"))
            for (final boolean workspaceChange: List.of (false, true))
            {
                final Fixture f = new Fixture ();
                final var action = f.resolve (button);
                f.edge (button, InputPhase.LONG);
                f.edge (button, InputPhase.END);
                if (workspaceChange) f.navigation.workspaceChanged (1, f.navigation.resolve ("PAN"));
                else f.observe ("PAN", false);
                f.dispatch (action);
                f.tick ();
                assertEquals ("PAN", f.navigation.legacyAlias ());
            }
    }

    @Test
    void deferredPageRequestsUseLatestPhysicalPageIntentWhenAdmittedTogether ()
    {
        final Fixture f = new Fixture ();
        final var first = f.resolve ("ACCENT");
        f.edge ("ACCENT", InputPhase.LONG);
        f.edge ("ACCENT", InputPhase.END);
        final var second = f.resolve ("MASTERTRACK");
        f.edge ("MASTERTRACK", InputPhase.END);
        final var third = f.resolve ("AUTOMATION");
        f.edge ("AUTOMATION", InputPhase.LONG);
        f.dispatch (first);
        f.dispatch (second);
        assertEquals ("TRACK", f.navigation.legacyAlias ());
        f.dispatch (third);
        assertEquals ("AUTOMATION", f.navigation.legacyAlias ());
        f.edge ("AUTOMATION", InputPhase.END);
        assertEquals ("TRACK", f.navigation.legacyAlias ());
    }

    @Test
    void shortPageIntentsAlsoSupersedeOlderUnsubmittedRequests ()
    {
        final Fixture f = new Fixture ();
        final var first = f.resolve ("MASTERTRACK");
        f.edge ("MASTERTRACK", InputPhase.END);
        final var second = f.resolve ("MASTERTRACK");
        f.edge ("MASTERTRACK", InputPhase.END);
        f.dispatch (first);
        assertEquals ("TRACK", f.navigation.legacyAlias ());
        f.dispatch (second);
        assertEquals ("MASTER", f.navigation.legacyAlias ());
    }

    @Test
    void interveningTemporaryReplacementPermanentlyRetiresOldReturn ()
    {
        final Fixture f = new Fixture ();
        f.edge ("ACCENT", InputPhase.BEGIN);
        f.edge ("ACCENT", InputPhase.LONG);
        f.observe ("PAN", true);
        f.tick ();
        f.observe ("ACCENT", true);
        f.edge ("ACCENT", InputPhase.END);
        assertEquals ("ACCENT", f.navigation.legacyAlias ());
    }

    private static String page (final String button) { return switch (button) { case "MASTERTRACK" -> "FRAME"; case "METRONOME" -> "TRANSPORT"; default -> button; }; }

    private static final class Fixture
    {
        private final PageNavigation navigation = PageNavigation.defaults ();
        private final ControllerPageTransitions pages = new ControllerPageTransitions (this.navigation);
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
        private void observe (final String mode, final boolean temporary)
        {
            if (temporary) this.navigation.temporary (this.navigation.origin (), this.navigation.resolve (mode));
            else this.navigation.select (this.navigation.resolve (mode));
        }
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
