// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.api.*;
import de.mossgrabers.pull.core.api.effect.*;
import de.mossgrabers.pull.core.api.event.*;
import de.mossgrabers.pull.core.api.output.RgbColor;
import de.mossgrabers.pull.core.runtime.PullCoreProvider;
import de.mossgrabers.pull.core.view.CompiledWorkspace;
import de.mossgrabers.pull.core.view.ControllerView;
import de.mossgrabers.pull.core.view.ResolvedControllerAction;
import org.junit.jupiter.api.Test;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class TransportSettingsViewsTest
{
    @Test
    void metronomeModifiersAreReadAtReleaseAndTogglesWaitForReadback ()
    {
        final Fixture f = new Fixture (pages -> List.of (new MetronomeControlView (new AuthoritativeBooleanToggle<> (), pages)));
        f.edge ("METRONOME", InputPhase.BEGIN);
        f.pressed.add (PushControlIds.button ("SHIFT"));
        assertTrue (f.edge ("METRONOME", InputPhase.LONG).effects ().isEmpty ());
        assertEquals (List.of (new SetTransportSettingEffect ("project-a", TransportSetting.TICK_PLAYBACK, true)), f.edge ("METRONOME", InputPhase.END).effects ());
        f.edge ("METRONOME", InputPhase.BEGIN);
        assertTrue (f.edge ("METRONOME", InputPhase.END).effects ().isEmpty ());
        f.ticks = true;
        assertEquals (List.of (new SetTransportSettingEffect ("project-a", TransportSetting.TICK_PLAYBACK, false)), f.tick ().effects ());
        f.pressed.clear ();
        f.edge ("METRONOME", InputPhase.BEGIN);
        final CoreResult release = f.edge ("METRONOME", InputPhase.END);
        assertTrue (release.effects ().contains (new SetProjectTransportStateEffect ("project-a", "project-a", TransportState.METRONOME, true)));
        assertEquals (new RgbColor (60, 60, 60), release.desiredOutput ().lights ().get (PushControlIds.button ("METRONOME")));
        f.metronome = true;
        assertEquals (new RgbColor (255, 255, 255), f.tick ().desiredOutput ().lights ().get (PushControlIds.button ("METRONOME")));
    }

    @Test
    void tapAndMetronomeShareOneAuthoritativeToggleLane ()
    {
        final AuthoritativeBooleanToggle<String> lane = new AuthoritativeBooleanToggle<> ();
        final Fixture f = new Fixture (pages -> List.of (new TapTempoView (lane), new MetronomeControlView (lane, pages)));
        f.pressed.add (PushControlIds.button ("SHIFT"));
        f.edge ("TAP_TEMPO", InputPhase.BEGIN);
        assertEquals (1, f.edge ("TAP_TEMPO", InputPhase.END).effects ().size ());
        f.pressed.clear ();
        f.edge ("METRONOME", InputPhase.BEGIN);
        assertTrue (f.edge ("METRONOME", InputPhase.END).effects ().isEmpty ());
        f.metronome = true;
        assertEquals (List.of (new SetProjectTransportStateEffect ("project-a", "project-a", TransportState.METRONOME, false)), f.tick ().effects ());
    }

    @Test
    void automationLongReleaseReturnsLocallyAndPreservesDeleteSuppression ()
    {
        final Fixture f = new Fixture (pages -> List.of (new AutomationControlView (new AutomationControlState (), pages)));
        f.edge ("AUTOMATION", InputPhase.BEGIN);
        assertTrue (f.edge ("AUTOMATION", InputPhase.LONG).effects ().isEmpty ());
        assertEquals ("AUTOMATION", f.navigation.legacyAlias ());
        assertTrue (f.edge ("AUTOMATION", InputPhase.END).effects ().isEmpty ());
        f.mode = "AUTOMATION";
        f.temporary = true;
        f.generation++;
        assertTrue (f.tick ().effects ().isEmpty ());
        assertEquals ("TRACK", f.navigation.legacyAlias ());
        f.edge ("AUTOMATION", InputPhase.BEGIN);
        f.edge ("AUTOMATION", InputPhase.LONG);
        f.pressed.add (PushControlIds.button ("DELETE"));
        assertEquals (List.of (new ConsumeControllerButtonEffect (PushControlIds.button ("DELETE"))), f.edge ("AUTOMATION", InputPhase.END).effects ());
        assertTrue (f.tick ().effects ().isEmpty ());
        final CoreResult reset = f.edge ("AUTOMATION", InputPhase.BEGIN);
        assertTrue (reset.effects ().contains (new ResetAutomationOverridesEffect ("project-a")));
    }

    @Test
    void automationShiftChangesLightButUsesTheSameWriteActuator ()
    {
        final Fixture f = new Fixture (pages -> List.of (new AutomationControlView (new AutomationControlState (), pages)));
        f.edge ("AUTOMATION", InputPhase.BEGIN);
        f.pressed.add (PushControlIds.button ("SHIFT"));
        final CoreResult release = f.edge ("AUTOMATION", InputPhase.END);
        assertEquals (List.of (new SetAutomationWriteEffect ("project-a", true)), release.effects ());
        assertEquals (new RgbColor (30, 30, 30), release.desiredOutput ().lights ().get (PushControlIds.button ("AUTOMATION")));
        f.writing = true;
        assertEquals (new RgbColor (89, 29, 0), f.tick ().desiredOutput ().lights ().get (PushControlIds.button ("AUTOMATION")));
        f.pressed.clear ();
        assertEquals (new RgbColor (255, 0, 0), f.tick ().desiredOutput ().lights ().get (PushControlIds.button ("AUTOMATION")));
    }

    @Test
    void automationPageWaitsForRawModeBeforeEnablingWriteAndReadCancelsQueuedEnable ()
    {
        final AutomationControlState state = new AutomationControlState ();
        final Fixture f = new Fixture (pages -> List.of (new AutomationControlView (state, pages), new TransportSettingsPageView (true, state)));
        f.edge ("ROW1_3", InputPhase.BEGIN);
        final CoreResult mode = f.edge ("ROW1_3", InputPhase.END);
        assertEquals (List.of (new SetAutomationModeEffect ("project-a", AutomationWriteMode.TOUCH)), mode.effects ());
        assertEquals (new RgbColor (255, 255, 255), mode.desiredOutput ().lights ().get (PushControlIds.button ("ROW1_1")));
        assertTrue (f.tick ().effects ().isEmpty ());
        f.automationMode = AutomationWriteMode.TOUCH;
        assertEquals (List.of (new SetAutomationWriteEffect ("project-a", true)), f.tick ().effects ());
        f.writing = true;
        assertEquals (new RgbColor (255, 255, 255), f.tick ().desiredOutput ().lights ().get (PushControlIds.button ("ROW1_3")));
        f.edge ("ROW1_4", InputPhase.BEGIN);
        f.edge ("ROW1_4", InputPhase.END);
        f.edge ("ROW1_1", InputPhase.BEGIN);
        assertTrue (f.edge ("ROW1_1", InputPhase.END).effects ().isEmpty ());
        f.automationMode = AutomationWriteMode.WRITE;
        assertEquals (List.of (new SetAutomationWriteEffect ("project-a", false)), f.tick ().effects ());
    }

    @Test
    void metronomeVolumeUsesTheIndependentSlotAndCalibratedShiftResponse ()
    {
        final Fixture f = new Fixture (new TransportSettingsPageView (false, new AutomationControlState ()));
        final ParameterTargetRef target = new ParameterTargetRef (ParameterTargetKind.LIVE, "metronome-volume", 0);
        f.parameters = Map.of (ParameterSlot.METRONOME_VOLUME, new ParameterTargetSnapshot (target, "Metronome Volume", 512, 512, "-6 dB", -1, 0));
        final CoreResult normal = f.turn ("KNOB8", 2);
        assertEquals (List.of (new AdjustParameterValueEffect (target, 2)), normal.effects ());
        final var texts = normal.desiredOutput ().display ().commands ().stream ().flatMap (command -> command instanceof de.mossgrabers.pull.core.api.output.DisplayCommand.TextAt text ? java.util.stream.Stream.of (text.text ()) : command instanceof de.mossgrabers.pull.core.api.output.DisplayCommand.TextBox text ? java.util.stream.Stream.of (text.text ()) : java.util.stream.Stream.empty ()).toList ();
        assertTrue (texts.contains ("-6"));
        assertTrue (texts.contains ("dB"));
        f.pressed.add (PushControlIds.button ("SHIFT"));
        assertEquals (List.of (new AdjustParameterValueEffect (target, 1)), f.turn ("KNOB8", 2).effects ());
        assertTrue (f.turn ("KNOB1", 2).effects ().isEmpty ());
        f.parameters = Map.of ();
        assertTrue (f.turn ("KNOB8", 2).effects ().isEmpty ());
    }

    @Test
    void completeMetronomePageOwnsFooterAndOnlyItsDeclaredKnobAndChoicesAct ()
    {
        final Fixture f = new Fixture (new TransportSettingsPageView (false, new AutomationControlState ()));
        assertTrue (f.initial.desiredControllerState ().workspace ().facets ().isEmpty (), "the page is independent of stable workspace registration");
        assertEquals (16, f.initial.desiredOutput ().lights ().size ());
        assertTrue (f.initial.desiredOutput ().display ().isPresent ());
        assertEquals (ParameterSlot.METRONOME_VOLUME, f.workspace.parameterSlotOrNull (PushControlIds.continuous ("KNOB8")));
        assertTrue (f.edge ("ROW1_4", InputPhase.END).effects ().isEmpty ());
        f.edge ("ROW1_4", InputPhase.BEGIN);
        assertEquals (List.of (new SetPreRollEffect ("project-a", PreRoll.FOUR_BARS)), f.edge ("ROW1_4", InputPhase.END).effects ());
        f.edge ("ROW1_6", InputPhase.BEGIN);
        assertEquals (List.of (new SetTransportSettingEffect ("project-a", TransportSetting.METRONOME_DURING_PRE_ROLL, true)), f.edge ("ROW1_6", InputPhase.END).effects ());
        f.edge ("ROW2_1", InputPhase.BEGIN);
        assertTrue (f.edge ("ROW2_1", InputPhase.END).effects ().isEmpty ());
        f.edge ("ROW1_2", InputPhase.BEGIN);
        f.project = "project-b";
        assertTrue (f.edge ("ROW1_2", InputPhase.END).effects ().isEmpty ());
    }

    @Test
    void deferredMetronomeReleaseKeepsItsModifierAndProjectDecision ()
    {
        final Fixture f = new Fixture (pages -> List.of (new MetronomeControlView (new AuthoritativeBooleanToggle<> (), pages)));
        final var action = f.resolve ("METRONOME");
        f.pressed.add (PushControlIds.button ("SHIFT"));
        f.edge ("METRONOME", InputPhase.END);
        f.pressed.clear ();
        assertEquals (List.of (new SetTransportSettingEffect ("project-a", TransportSetting.TICK_PLAYBACK, true)), f.dispatch (action).effects ());
        final var stale = f.resolve ("METRONOME");
        f.edge ("METRONOME", InputPhase.END);
        f.project = "project-b";
        assertTrue (f.dispatch (stale).effects ().isEmpty (), "a released request must not be retargeted to a new project");
    }

    @Test
    void deleteConsumptionPrecedesAdmissionWhileItsResetWaitsAndLaterEdgesStillConsume ()
    {
        final Fixture f = new Fixture (pages -> List.of (new AutomationControlView (new AutomationControlState (), pages)));
        final var delete = PushControlIds.button ("DELETE");
        f.pressed.add (delete);
        final var action = f.resolve ("AUTOMATION");
        assertEquals (List.of (new ConsumeControllerButtonEffect (delete)), action.immediateEffects ());
        assertEquals (List.of (new ConsumeControllerButtonEffect (delete)), f.edge ("AUTOMATION", InputPhase.LONG).effects ());
        assertEquals (List.of (new ConsumeControllerButtonEffect (delete)), f.edge ("AUTOMATION", InputPhase.END).effects ());
        f.pressed.clear ();
        assertEquals (List.of (new ConsumeControllerButtonEffect (delete), new ResetAutomationOverridesEffect ("project-a")), f.dispatch (action).effects ());
        assertTrue (f.tick ().effects ().isEmpty ());
    }

    @Test
    void deleteBeginPreservesOnlyTheStillOwnedPriorReturn ()
    {
        for (final boolean observeBeforeSecondPress: List.of (false, true))
        {
            final Fixture f = new Fixture (pages -> List.of (new AutomationControlView (new AutomationControlState (), pages)));
            final ControlId delete = PushControlIds.button ("DELETE");
            f.edge ("AUTOMATION", InputPhase.BEGIN);
            f.edge ("AUTOMATION", InputPhase.LONG);
            f.pressed.add (delete);
            f.edge ("AUTOMATION", InputPhase.END);
            if (observeBeforeSecondPress)
            {
                f.mode = "AUTOMATION";
                f.temporary = true;
                f.generation++;
                assertTrue (f.tick ().effects ().isEmpty ());
                f.mode = "PAN";
                f.navigation.select (f.navigation.resolve ("PAN"));
                f.generation++;
                f.tick ();
            }
            f.edge ("AUTOMATION", InputPhase.BEGIN);
            f.pressed.remove (delete);
            final CoreResult release = f.edge ("AUTOMATION", InputPhase.END);
            if (observeBeforeSecondPress)
            {
                assertTrue (release.effects ().isEmpty (), "an intervening page retired the prior slot");
                assertEquals ("PAN", f.navigation.legacyAlias ());
            }
            else
            {
                assertTrue (release.effects ().isEmpty ());
                f.mode = "AUTOMATION";
                f.temporary = true;
                f.generation++;
                assertTrue (f.tick ().effects ().isEmpty ());
                assertEquals ("TRACK", f.navigation.legacyAlias ());
            }
        }
    }

    private static final class Fixture
    {
        private final PageNavigation navigation = PageNavigation.defaults ();
        private final CompiledWorkspace workspace;
        private final Set<ControlId> pressed = new HashSet<> ();
        private long sequence;
        private long generation = 1;
        private String mode = "TRACK";
        private boolean temporary;
        private String project = "project-a";
        private boolean metronome;
        private boolean ticks;
        private boolean writing;
        private AutomationWriteMode automationMode = AutomationWriteMode.LATCH;
        private Map<ParameterSlot, ParameterTargetSnapshot> parameters = Map.of ();
        private final CoreResult initial;
        private Fixture (final ControllerView... views) { this (pages -> List.of (views)); }
        private Fixture (final java.util.function.Function<ControllerPageTransitions, List<ControllerView>> factory) { this.workspace = CompiledWorkspace.compile ("test", factory.apply (new ControllerPageTransitions (this.navigation))); this.initial = this.workspace.start (this.snapshot ()); }
        private CoreResult edge (final String button, final InputPhase phase)
        {
            final ControlId id = PushControlIds.button (button);
            if (phase == InputPhase.BEGIN) this.pressed.add (id);
            if (phase == InputPhase.END) this.pressed.remove (id);
            this.sequence++;
            final var input = new ControllerInputEvent (this.sequence, this.sequence, id, InputKind.BUTTON, phase, phase == InputPhase.END ? 0 : 127);
            final var action = this.workspace.resolveAction (input, this.snapshot ());
            return action == null ? this.workspace.handle (input, this.snapshot ()) : this.dispatch (action);
        }
        private ResolvedControllerAction resolve (final String button)
        {
            final ControlId id = PushControlIds.button (button);
            this.pressed.add (id);
            this.sequence++;
            return this.workspace.resolveAction (new ControllerInputEvent (this.sequence, this.sequence, id, InputKind.BUTTON, InputPhase.BEGIN, 127), this.snapshot ());
        }
        private CoreResult dispatch (final ResolvedControllerAction action) { return this.workspace.handleAction (action, this.snapshot ()); }
        private CoreResult tick () { this.sequence++; return this.workspace.handle (new ControllerTickEvent (this.sequence, this.sequence), this.snapshot ()); }
        private CoreResult turn (final String knob, final long delta) { this.sequence++; return this.workspace.handle (new ControllerInputEvent (this.sequence, this.sequence, PushControlIds.continuous (knob), InputKind.RELATIVE, InputPhase.UPDATE, delta), this.snapshot ()); }
        private ControllerSnapshot snapshot ()
        {
            final ControllerBridgeSnapshot bridge = new ControllerBridgeSnapshot (new TransportSnapshot (true, true, false, false, false, false, false, this.metronome, false, 120, 0, 4, 4), SelectedTrackSnapshot.empty (), SessionBankSnapshot.empty (), new ControllerLayoutSnapshot (this.generation, "PLAY", this.mode, false, false, 0, GridPressureConfiguration.OFF, DesiredNoteInputTranslation.unowned (), this.temporary ? "TRACK" : this.mode, "TRACK", this.temporary), NoteViewSnapshot.empty (), NoteRepeatSnapshot.empty (), DrumContextSnapshot.empty (), new ParameterBridgeSnapshot (this.parameters, Map.of ()), ControllerMappingFeedbackSnapshot.empty (), MasterSnapshot.empty (), new ProjectSnapshot (true, this.project, "Project", true, false, false, false), new AutomationSnapshot (this.project, this.writing, false, this.automationMode), new EncoderConfigurationSnapshot (true, 1024, 1, 0, -50), CurrentTrackBankSnapshot.empty (), new TransportSettingsSnapshot (this.project, this.ticks, PreRoll.NONE, false));
            return new ControllerSnapshot (this.sequence, this.sequence, new PullCoreProvider ().descriptor ().requiredCapabilities (), bridge, ClipCatalogSnapshot.empty (), Map.of (), Map.of (), Optional.empty (), this.pressed, Set.of ());
        }
    }
}
