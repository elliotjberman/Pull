// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.api.*;
import de.mossgrabers.pull.core.api.effect.*;
import de.mossgrabers.pull.core.api.event.*;
import de.mossgrabers.pull.core.api.output.*;
import de.mossgrabers.pull.core.runtime.PullCoreProvider;
import de.mossgrabers.pull.core.view.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.*;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.*;

class AccentViewsTest
{
    @Test
    void shortToggleAndLightWaitForObservedConfigurationAndPreserveQueuedToggle ()
    {
        final Fixture f = new Fixture (false);
        f.edge ("ACCENT", InputPhase.BEGIN);
        final CoreResult release = f.edge ("ACCENT", InputPhase.END);
        assertEquals (List.of (new SetControllerBooleanSettingEffect (SetControllerBooleanSettingEffect.Setting.ACCENT_ENABLED, true)), release.effects ());
        assertEquals (new RgbColor (60, 60, 60), release.desiredOutput ().lights ().get (PushControlIds.button ("ACCENT")));
        f.edge ("ACCENT", InputPhase.BEGIN);
        assertTrue (f.edge ("ACCENT", InputPhase.END).effects ().isEmpty ());
        f.enabled = true;
        final CoreResult observed = f.tick ();
        assertEquals (List.of (new SetControllerBooleanSettingEffect (SetControllerBooleanSettingEffect.Setting.ACCENT_ENABLED, false)), observed.effects ());
        assertEquals (new RgbColor (255, 255, 255), observed.desiredOutput ().lights ().get (PushControlIds.button ("ACCENT")));
    }

    @Test
    void deferredLongAndEndCompleteLocallyAfterAdmission ()
    {
        final Fixture f = new Fixture (false);
        final ResolvedControllerAction action = f.resolve ("ACCENT");
        assertEquals (Set.of (ControllerStateScope.ACTIVE_PARAMETERS), action.intent ().invalidates ());
        assertTrue (f.edge ("ACCENT", InputPhase.LONG).effects ().isEmpty ());
        assertTrue (f.edge ("ACCENT", InputPhase.END).effects ().isEmpty ());
        assertTrue (f.dispatch (action).effects ().isEmpty ());
        assertEquals ("TRACK", f.navigation.legacyAlias ());
        assertTrue (f.tick ().effects ().isEmpty ());
        f.observe ("PAN", false);
        assertTrue (f.tick ().effects ().isEmpty ());
        f.observe ("ACCENT", false);
        assertTrue (f.tick ().effects ().isEmpty ());
        f.observe ("ACCENT", true);
        assertTrue (f.tick ().effects ().isEmpty ());
        assertEquals ("ACCENT", f.navigation.legacyAlias (), "retired return cannot close a newer page");
        assertTrue (f.tick ().effects ().isEmpty ());
    }

    @Test
    void staleAdmissionAndDeactivationCannotRevivePageRequests ()
    {
        final Fixture f = new Fixture (false);
        final var stale = f.resolve ("ACCENT");
        f.edge ("ACCENT", InputPhase.LONG);
        f.edge ("ACCENT", InputPhase.END);
        f.observe ("PAN", false);
        assertTrue (f.dispatch (stale).effects ().isEmpty ());
        final var retired = f.resolve ("ACCENT");
        f.edge ("ACCENT", InputPhase.END);
        f.view.deactivate ();
        assertTrue (f.dispatch (retired).effects ().isEmpty ());
    }

    @Test
    void aNewAccentOwnerReturnsLocallyAndOldReturnsStayRetired ()
    {
        final Fixture f = new Fixture (false);
        f.observe ("ACCENT", true);
        f.edge ("ACCENT", InputPhase.BEGIN);
        f.edge ("ACCENT", InputPhase.LONG);
        assertTrue (f.edge ("ACCENT", InputPhase.END).effects ().isEmpty ());
        assertEquals ("TRACK", f.navigation.legacyAlias ());
        f.observe ("TRACK", false);
        f.edge ("ACCENT", InputPhase.BEGIN);
        f.edge ("ACCENT", InputPhase.LONG);
        f.edge ("ACCENT", InputPhase.END);
        f.sequence += 5_000_000_000L;
        assertTrue (f.tick ().effects ().isEmpty ());
        f.observe ("ACCENT", true);
        assertTrue (f.tick ().effects ().isEmpty ());
    }

    @ParameterizedTest
    @ValueSource (ints = {1, 2, 3, 4, 5, 6, 7, 8})
    void everyKnobUsesFixedCalibrationAndObservedValueDespiteModifiers (final int knob)
    {
        final Fixture f = new Fixture (true);
        f.pressed.addAll (Set.of (PushControlIds.button ("SHIFT"), PushControlIds.button ("DELETE"), PushControlIds.button ("SELECT")));
        assertEquals (List.of (new SetControllerIntegerSettingEffect (SetControllerIntegerSettingEffect.Setting.ACCENT_VELOCITY, 66)), f.turn (knob, 2).effects ());
        assertEquals ("64", f.displayedValue ());
        assertTrue (f.turn (knob, 3).effects ().isEmpty ());
        f.velocity = 66;
        assertEquals (List.of (new SetControllerIntegerSettingEffect (SetControllerIntegerSettingEffect.Setting.ACCENT_VELOCITY, 69)), f.tick ().effects ());
        assertEquals ("66", f.displayedValue ());
        f.velocity = 69;
        f.tick ();
        assertEquals (List.of (new SetControllerIntegerSettingEffect (SetControllerIntegerSettingEffect.Setting.ACCENT_VELOCITY, 127)), f.turn (knob, Long.MAX_VALUE).effects ());
        f.velocity = 127;
        f.tick ();
        assertEquals (List.of (new SetControllerIntegerSettingEffect (SetControllerIntegerSettingEffect.Setting.ACCENT_VELOCITY, 1)), f.turn (knob, Long.MIN_VALUE).effects ());
    }

    @Test
    void touchPresentationMatchesLegacyOnlyEighthColumnAndKeepsRowsDark ()
    {
        final Fixture f = new Fixture (true);
        final var before = f.view.render (f.snapshot ()).display ();
        for (int index = 1; index <= 7; index++)
        {
            f.touch (index, InputPhase.BEGIN);
            assertEquals (before, f.view.render (f.snapshot ()).display ());
        }
        final var touched = f.touch (8, InputPhase.BEGIN);
        assertNotEquals (before, f.view.render (f.snapshot ()).display ());
        assertTrue (touched.effects ().isEmpty ());
        assertEquals (new RgbColor (190, 235, 247), ((DisplayCommand.TextAt) f.view.render (f.snapshot ()).display ().commands ().get (1)).color ());
        f.touch (8, InputPhase.END);
        assertEquals (before, f.view.render (f.snapshot ()).display ());
        assertEquals (16, touched.desiredOutput ().lights ().size ());
        assertTrue (touched.desiredOutput ().lights ().values ().stream ().allMatch (new RgbColor (0, 0, 0)::equals));
    }

    @Test
    void accentRingPreservesLegacyDisplayRangeAndExactGeometry ()
    {
        final Fixture f = new Fixture (true);
        for (final int velocity: new int[] {1, 64, 127})
        {
            f.velocity = velocity;
            final var commands = f.view.render (f.snapshot ()).display ().commands ();
            final DisplayCommand.DottedArc foreground = (DisplayCommand.DottedArc) commands.get (4);
            final double ratio = (velocity * 1023 / 127) / 1024.0;
            assertEquals (-260 * ratio, foreground.sweepDegrees ());
            assertEquals (873, foreground.centerX ());
            assertEquals (105, foreground.centerY ());
            assertEquals (25, foreground.radius ());
            assertEquals (Math.max (2, (int) Math.ceil (220 * ratio)), foreground.steps ());
        }
    }

    @Test
    void lowerRowCapturesBeginTargetAndSelectsAfterReleaseAndDeferredAdmission ()
    {
        final Fixture f = new Fixture (true);
        f.pressed.addAll (Set.of (PushControlIds.button ("DELETE"), PushControlIds.button ("RECORD"), PushControlIds.button ("SELECT"), PushControlIds.button ("SHIFT")));
        final var action = f.resolve ("ROW1_3");
        assertTrue (f.edge ("ROW1_3", InputPhase.LONG).effects ().isEmpty ());
        assertTrue (f.edge ("ROW1_3", InputPhase.END).effects ().isEmpty ());
        assertEquals (List.of (new CurrentTrackActionEffect (new CurrentTrackTarget (1, "main", 2, "track-2"), CurrentTrackActionEffect.Action.SELECT)), f.dispatch (action).effects ());
        final var changed = f.resolve ("ROW1_3");
        f.edge ("ROW1_3", InputPhase.END);
        f.bankGeneration++;
        assertTrue (f.dispatch (changed).effects ().isEmpty ());
    }

    @Test
    void sessionStopChordConsumesBothSharedTokensAndStopsExactBeginTrackWithoutSelection ()
    {
        final Fixture f = new Fixture (true);
        f.session = true;
        f.pressed.add (PushControlIds.button ("STOP_CLIP"));
        f.fullStop.begin ();
        final var action = f.resolve ("ROW1_2");
        assertEquals (ControllerActionId.STOP_VISIBLE_SESSION_TRACK, action.intent ().action ());
        assertTrue (f.fullStop.takeConsumed ());
        assertEquals (List.of (new ConsumeControllerButtonEffect (PushControlIds.button ("ROW1_2"))), action.immediateEffects ());
        f.edge ("ROW1_2", InputPhase.END);
        assertEquals (List.of (new StopSessionTrackEffect (1, new SessionBankShape (8, 8), 1, "track-1", true)), f.workspace.dispatchAction (action, f.snapshot ()));
        assertTrue (f.tick ().effects ().isEmpty ());
    }

    @Test
    void unavailableSettingsAndChangedExternalValueCancelUnsentRelativeIntent ()
    {
        final Fixture f = new Fixture (true);
        f.turn (1, 2);
        f.turn (1, 3);
        f.velocity = 99;
        assertTrue (f.tick ().effects ().isEmpty ());
        f.velocity = 66;
        assertTrue (f.tick ().effects ().isEmpty ());
        f.available = false;
        assertTrue (f.turn (1, 1).effects ().isEmpty ());
        assertEquals (1, f.view.render (f.snapshot ()).display ().commands ().size ());
    }

    private static final class Fixture
    {
        private final PageNavigation navigation = PageNavigation.defaults ();
        private final SessionStopGesture fullStop = new SessionStopGesture ();
        private final ControllerView view;
        private final CompiledWorkspace workspace;
        private final Set<ControlId> pressed = new HashSet<> ();
        private long sequence;
        private long generation = 1;
        private long bankGeneration = 1;
        private String mode = "TRACK";
        private boolean temporary;
        private boolean available = true;
        private boolean enabled;
        private boolean session;
        private int velocity = 64;
        private Fixture (final boolean page) { this.view = page ? new AccentPageView (this.fullStop) : new AccentControlView (new ControllerPageTransitions (this.navigation)); this.workspace = CompiledWorkspace.compile ("test", List.of (this.view)); this.workspace.start (this.snapshot ()); }
        private void observe (final String mode, final boolean temporary) { if (temporary) this.navigation.temporary (this.navigation.origin (), this.navigation.resolve (mode)); else this.navigation.select (this.navigation.resolve (mode)); }
        private ControllerInputEvent input (final ControlId id, final InputKind kind, final InputPhase phase, final long value) { this.sequence++; return new ControllerInputEvent (this.sequence, this.sequence, id, kind, phase, value); }
        private ResolvedControllerAction resolve (final String button) { this.pressed.add (PushControlIds.button (button)); return this.workspace.resolveAction (this.input (PushControlIds.button (button), InputKind.BUTTON, InputPhase.BEGIN, 127), this.snapshot ()); }
        private CoreResult dispatch (final ResolvedControllerAction action) { return this.workspace.handleAction (action, this.snapshot ()); }
        private CoreResult edge (final String button, final InputPhase phase) { if (phase == InputPhase.BEGIN) return this.dispatch (this.resolve (button)); if (phase == InputPhase.END) this.pressed.remove (PushControlIds.button (button)); return this.workspace.handle (this.input (PushControlIds.button (button), InputKind.BUTTON, phase, phase == InputPhase.END ? 0 : 127), this.snapshot ()); }
        private CoreResult turn (final int knob, final long value) { return this.workspace.handle (this.input (PushControlIds.continuous ("KNOB" + knob), InputKind.RELATIVE, InputPhase.UPDATE, value), this.snapshot ()); }
        private CoreResult touch (final int knob, final InputPhase phase) { return this.workspace.handle (this.input (PushControlIds.continuous ("KNOB" + knob), InputKind.TOUCH, phase, phase == InputPhase.END ? 0 : 127), this.snapshot ()); }
        private CoreResult tick () { this.sequence++; return this.workspace.handle (new ControllerTickEvent (this.sequence, this.sequence), this.snapshot ()); }
        private String displayedValue () { return ((DisplayCommand.TextAt) this.view.render (this.snapshot ()).display ().commands ().get (2)).text (); }
        private ControllerSnapshot snapshot ()
        {
            final var tracks = IntStream.range (0, 8).mapToObj (i -> new SessionTrackSnapshot ("track-" + i, i, "Track " + i, true, i == 0, true, false, false, false, false, SessionTrackType.AUDIO, new RgbColor (255, 255, 255))).toList ();
            final var current = new CurrentTrackBankSnapshot (this.bankGeneration, "main", 0, tracks.stream ().map (track -> new CurrentTrackSnapshot (track, false, 0, 0)).toList (), "track-0", false, 1, false);
            final var bank = this.session ? new SessionBankSnapshot (1, new SessionBankShape (8, 8), 0, 0, tracks) : SessionBankSnapshot.empty ();
            final var settings = this.available ? new ControllerSettingsSnapshot (true, false, "VOLUME", 0, CursorSendBankSnapshot.empty (), this.enabled, this.velocity) : ControllerSettingsSnapshot.empty ();
            final var layout = new ControllerLayoutSnapshot (this.generation, "PLAY", this.mode, false, false, 0, GridPressureConfiguration.OFF, DesiredNoteInputTranslation.unowned (), this.temporary ? "TRACK" : this.mode, "TRACK", this.temporary);
            final var bridge = new ControllerBridgeSnapshot (TransportSnapshot.empty (), SelectedTrackSnapshot.empty (), bank, layout, NoteViewSnapshot.empty (), NoteRepeatSnapshot.empty (), DrumContextSnapshot.empty (), ParameterBridgeSnapshot.empty (), ControllerMappingFeedbackSnapshot.empty (), MasterSnapshot.empty (), ProjectSnapshot.empty (), AutomationSnapshot.empty (), new EncoderConfigurationSnapshot (true, 1024, 10, 80, -35), current, TransportSettingsSnapshot.empty (), settings);
            return new ControllerSnapshot (this.sequence, this.sequence, new PullCoreProvider ().descriptor ().requiredCapabilities (), bridge, ClipCatalogSnapshot.empty (), Map.of (), Map.of (), Optional.empty (), this.pressed, Set.of ());
        }
    }
}
