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
        assertEquals (new RgbColor (190, 235, 247), ((DisplayCommand.TextBox) f.view.render (f.snapshot ()).display ().commands ().get (1)).color ());
        f.touch (8, InputPhase.END);
        assertEquals (before, f.view.render (f.snapshot ()).display ());
        assertEquals (16, touched.desiredOutput ().lights ().size ());
        assertTrue (touched.desiredOutput ().lights ().values ().stream ().allMatch (new RgbColor (0, 0, 0)::equals));
    }

    @ParameterizedTest
    @ValueSource (ints = {1, 2, 3, 4, 5, 6, 7, 8})
    void lowerRowCapturesBeginTargetAndSelectsAfterReleaseAndDeferredAdmission (final int column)
    {
        final Fixture f = new Fixture (true);
        f.pressed.addAll (Set.of (PushControlIds.button ("DELETE"), PushControlIds.button ("RECORD"), PushControlIds.button ("SELECT"), PushControlIds.button ("SHIFT"), PushControlIds.button ("STOP_CLIP")));
        final String row = "ROW1_" + column;
        final var action = f.resolve (row);
        assertTrue (f.edge (row, InputPhase.LONG).effects ().isEmpty ());
        assertTrue (f.edge (row, InputPhase.END).effects ().isEmpty ());
        assertEquals (List.of (new CurrentTrackActionEffect (new CurrentTrackTarget (1, "main", column - 1, "track-" + (column - 1)), CurrentTrackActionEffect.Action.SELECT)), f.dispatch (action).effects ());
        final var changed = f.resolve (row);
        f.edge (row, InputPhase.END);
        f.bankGeneration++;
        assertTrue (f.dispatch (changed).effects ().isEmpty ());
    }

    @ParameterizedTest
    @ValueSource (booleans = {false, true})
    void rowSelectionStaysCancelledAfterItsBankReappears (final boolean deferred)
    {
        final Fixture f = new Fixture (true);
        final var action = f.resolve ("ROW1_3");
        if (!deferred) assertTrue (f.dispatch (action).effects ().isEmpty ());
        f.bankGeneration++;
        f.tick ();
        f.bankGeneration--;
        f.tick ();
        assertTrue (f.edge ("ROW1_3", InputPhase.END).effects ().isEmpty ());
        if (deferred) assertTrue (f.dispatch (action).effects ().isEmpty ());
        assertTrue (f.edge ("ROW1_3", InputPhase.BEGIN).effects ().isEmpty ());
        assertEquals (List.of (new CurrentTrackActionEffect (new CurrentTrackTarget (1, "main", 2, "track-2"), CurrentTrackActionEffect.Action.SELECT)), f.edge ("ROW1_3", InputPhase.END).effects ());
    }

    @ParameterizedTest
    @ValueSource (booleans = {false, true})
    void rowSelectionStaysCancelledAfterItsPageReappears (final boolean deferred)
    {
        final Fixture f = new Fixture (true);
        final var action = f.resolve ("ROW1_3");
        if (!deferred) assertTrue (f.dispatch (action).effects ().isEmpty ());
        f.navigation.select (LegacyPageAliases.reference (PageId.FRAME));
        f.tick ();
        f.navigation.select (LegacyPageAliases.reference (PageId.TRACK));
        f.tick ();
        assertTrue (f.edge ("ROW1_3", InputPhase.END).effects ().isEmpty ());
        if (deferred) assertTrue (f.dispatch (action).effects ().isEmpty ());
        assertTrue (f.edge ("ROW1_3", InputPhase.BEGIN).effects ().isEmpty ());
        assertEquals (List.of (new CurrentTrackActionEffect (new CurrentTrackTarget (1, "main", 2, "track-2"), CurrentTrackActionEffect.Action.SELECT)), f.edge ("ROW1_3", InputPhase.END).effects ());
    }

    @ParameterizedTest
    @ValueSource (ints = {4, 8})
    void stopChordStopsOnlyTheCapturedSessionTrackAndConsumesStopRelease (final int scenes)
    {
        final Fixture f = new Fixture (true, new SessionBankShape (8, scenes));
        f.pressed.add (PushControlIds.button ("SHIFT"));
        f.edge ("STOP_CLIP", InputPhase.BEGIN);
        final CoreResult press = f.edge ("ROW1_3", InputPhase.BEGIN);
        assertEquals (List.of (new ConsumeControllerButtonEffect (PushControlIds.button ("ROW1_3")), new StopSessionTrackEffect (1, f.sessionShape, 2, "track-2", true)), press.effects ());
        assertTrue (f.edge ("ROW1_3", InputPhase.LONG).effects ().isEmpty ());
        assertTrue (f.edge ("ROW1_3", InputPhase.END).effects ().isEmpty ());
        assertTrue (f.edge ("STOP_CLIP", InputPhase.END).effects ().isEmpty ());
    }

    @Test
    void deferredStopDoesNotReviveAfterSessionBindingReturns ()
    {
        final Fixture f = new Fixture (true, new SessionBankShape (8, 8));
        f.pressed.add (PushControlIds.button ("SHIFT"));
        f.edge ("STOP_CLIP", InputPhase.BEGIN);
        final var action = f.resolve ("ROW1_3");
        f.sessionGeneration++;
        f.tick ();
        f.sessionGeneration--;
        f.tick ();
        f.edge ("ROW1_3", InputPhase.END);
        assertTrue (f.dispatch (action).effects ().stream ().noneMatch (effect -> effect instanceof StopSessionTrackEffect || effect instanceof CurrentTrackActionEffect));
        assertTrue (f.edge ("STOP_CLIP", InputPhase.END).effects ().isEmpty ());
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

    @Test
    void unavailableAccentCancelsTouchAndDoesNotResumeWhenSettingsReturn ()
    {
        final Fixture f = new Fixture (true);
        final var untouched = f.view.render (f.snapshot ()).display ();
        f.touch (8, InputPhase.BEGIN);
        f.available = false;
        f.tick ();
        f.available = true;
        f.tick ();
        assertTrue (f.turn (8, 1).effects ().isEmpty ());
        assertEquals (untouched, f.view.render (f.snapshot ()).display ());
        f.touch (8, InputPhase.END);
        f.touch (8, InputPhase.BEGIN);
        assertEquals (List.of (new SetControllerIntegerSettingEffect (SetControllerIntegerSettingEffect.Setting.ACCENT_VELOCITY, 65)), f.turn (8, 1).effects ());
    }

    @Test
    void unavailableAccentCancelsToggleAndRelinquishesOnlyAnAlreadyOpenedTemporaryPage ()
    {
        final Fixture f = new Fixture (false);
        f.edge ("ACCENT", InputPhase.BEGIN);
        f.available = false;
        f.tick ();
        f.available = true;
        f.tick ();
        assertTrue (f.edge ("ACCENT", InputPhase.END).effects ().isEmpty ());
        f.edge ("ACCENT", InputPhase.BEGIN);
        f.edge ("ACCENT", InputPhase.LONG);
        assertEquals ("ACCENT", f.navigation.legacyAlias ());
        f.available = false;
        f.tick ();
        assertEquals ("TRACK", f.navigation.legacyAlias ());
        f.available = true;
        f.tick ();
        f.edge ("ACCENT", InputPhase.END);
        assertEquals ("TRACK", f.navigation.legacyAlias ());
        f.edge ("ACCENT", InputPhase.BEGIN);
        assertEquals (List.of (new SetControllerBooleanSettingEffect (SetControllerBooleanSettingEffect.Setting.ACCENT_ENABLED, true)), f.edge ("ACCENT", InputPhase.END).effects ());
    }

    @Test
    void deferredAccentCannotOpenAfterItsSettingsDisappear ()
    {
        final Fixture f = new Fixture (false);
        final ResolvedControllerAction action = f.resolve ("ACCENT");
        f.edge ("ACCENT", InputPhase.LONG);
        f.edge ("ACCENT", InputPhase.END);
        f.available = false;
        f.tick ();
        f.available = true;
        f.tick ();
        assertEquals ("TRACK", f.navigation.legacyAlias ());
        f.edge ("ACCENT", InputPhase.BEGIN);
        f.edge ("ACCENT", InputPhase.LONG);
        assertEquals ("ACCENT", f.navigation.legacyAlias ());
        assertTrue (f.dispatch (action).effects ().isEmpty ());
        assertEquals ("ACCENT", f.navigation.legacyAlias ());
        f.edge ("ACCENT", InputPhase.END);
        assertEquals ("TRACK", f.navigation.legacyAlias ());
        f.edge ("ACCENT", InputPhase.BEGIN);
        assertEquals (List.of (new SetControllerBooleanSettingEffect (SetControllerBooleanSettingEffect.Setting.ACCENT_ENABLED, true)), f.edge ("ACCENT", InputPhase.END).effects ());
    }

    private static final class Fixture
    {
        private final PageNavigation navigation = PageNavigation.defaults ();
        private final SessionStopGesture fullStop = new SessionStopGesture ();
        private final ControllerView view;
        private final RoutedWorkspace workspace;
        private final SessionBankShape sessionShape;
        private final Set<ControlId> pressed = new HashSet<> ();
        private long sequence;
        private long bankGeneration = 1;
        private long sessionGeneration = 1;
        private boolean available = true;
        private boolean enabled;
        private int velocity = 64;
        private Fixture (final boolean page) { this (page, SessionBankShape.empty ()); }
        private Fixture (final boolean page, final SessionBankShape sessionShape)
        {
            this.sessionShape = sessionShape;
            this.view = page ? new AccentPageView (this.fullStop, this.navigation) : new AccentControlView (new ControllerPageTransitions (this.navigation));
            this.workspace = new RoutedWorkspace (CompiledWorkspace.compile ("test", sessionShape, sessionShape.isPresent () ? List.of (this.view, scenesView (sessionShape, this.fullStop)) : List.of (this.view)));
            this.workspace.start (this.snapshot ());
        }
        private static ControllerView scenesView (final SessionBankShape shape, final SessionStopGesture stop) { return shape.scenes () == 8 ? SessionView.full (stop) : SessionView.upper (true, stop); }
        private ControllerInputEvent input (final ControlId id, final InputKind kind, final InputPhase phase, final long value) { this.sequence++; return new ControllerInputEvent (this.sequence, this.sequence, id, kind, phase, value); }
        private ResolvedControllerAction resolve (final String button) { this.pressed.add (PushControlIds.button (button)); return this.workspace.resolveAction (this.input (PushControlIds.button (button), InputKind.BUTTON, InputPhase.BEGIN, 127), this.snapshot ()); }
        private CoreResult dispatch (final ResolvedControllerAction action) { return this.workspace.handleAction (action, this.snapshot ()); }
        private CoreResult edge (final String button, final InputPhase phase)
        {
            final ControlId control = PushControlIds.button (button);
            if (phase == InputPhase.BEGIN) this.pressed.add (control);
            if (phase == InputPhase.END) this.pressed.remove (control);
            final var input = this.input (control, InputKind.BUTTON, phase, phase == InputPhase.END ? 0 : 127);
            return this.workspace.handle (input, this.snapshot ());
        }
        private CoreResult turn (final int knob, final long value) { return this.workspace.handle (this.input (PushControlIds.continuous ("KNOB" + knob), InputKind.RELATIVE, InputPhase.UPDATE, value), this.snapshot ()); }
        private CoreResult touch (final int knob, final InputPhase phase) { return this.workspace.handle (this.input (PushControlIds.continuous ("KNOB" + knob), InputKind.TOUCH, phase, phase == InputPhase.END ? 0 : 127), this.snapshot ()); }
        private CoreResult tick () { this.sequence++; return this.workspace.handle (new ControllerTickEvent (this.sequence, this.sequence), this.snapshot ()); }
        private String displayedValue () { return ((DisplayCommand.TextBox) this.view.render (this.snapshot ()).display ().commands ().get (2)).text (); }
        private ControllerSnapshot snapshot ()
        {
            final var tracks = IntStream.range (0, 8).mapToObj (i -> new SessionTrackSnapshot ("track-" + i, i, "Track " + i, true, i == 0, true, false, false, false, false, SessionTrackType.AUDIO, new RgbColor (255, 255, 255))).toList ();
            final var current = new CurrentTrackBankSnapshot (this.bankGeneration, "main", 0, tracks.stream ().map (track -> new CurrentTrackSnapshot (track, false, 0, 0)).toList (), "track-0", false, 1, false);
            final var bank = this.sessionShape.isPresent () ? new SessionBankSnapshot (this.sessionGeneration, this.sessionShape, 0, 0, tracks) : SessionBankSnapshot.empty ();
            final var settings = this.available ? new ControllerSettingsSnapshot (true, false, "VOLUME", 0, CursorSendBankSnapshot.empty (), this.enabled, this.velocity) : ControllerSettingsSnapshot.empty ();
            final var layout = new ControllerLayoutSnapshot (1, "PLAY", "TRACK", false, false, 0, GridPressureConfiguration.OFF);
            final var bridge = new ControllerBridgeSnapshot (TransportSnapshot.empty (), SelectedTrackSnapshot.empty (), bank, layout, NoteViewSnapshot.empty (), NoteRepeatSnapshot.empty (), DrumContextSnapshot.empty (), ParameterBridgeSnapshot.empty (), ControllerMappingFeedbackSnapshot.empty (), MasterSnapshot.empty (), ProjectSnapshot.empty (), AutomationSnapshot.empty (), new EncoderConfigurationSnapshot (true, 1024, 10, 80, -35), current, TransportSettingsSnapshot.empty (), settings);
            return new ControllerSnapshot (this.sequence, this.sequence, new PullCoreProvider ().descriptor ().requiredCapabilities (), bridge, ClipCatalogSnapshot.empty (), Map.of (), Map.of (), Optional.empty (), this.pressed, Set.of ());
        }
    }
}
