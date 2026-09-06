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
import java.util.*;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.*;

class SendMixerControlsViewTest
{
    private static final ControlId SHIFT = PushControlIds.button ("SHIFT");
    private static final ControlId SELECT = PushControlIds.button ("SELECT");
    private static final ControlId DELETE = PushControlIds.button ("DELETE");
    private static final RgbColor COLOR = new RgbColor (30, 120, 240);

    @Test
    void allEightPagesDeclareOneEightTrackColumnAndUseTheSharedMenu ()
    {
        for (int send = 0; send < 8; send++)
        {
            final Fixture f = new Fixture (send);
            final var result = f.workspace.activate (f.snapshot ());
            assertEquals ("SEND" + (send + 1), f.pages.legacyAlias ());
            assertEquals (Set.of (ParameterBankId.trackSend (send)), result.desiredParameterBanks ().banks ());
            assertEquals (16, result.desiredOutput ().lights ().size ());
            assertEquals (143, f.view.render (f.snapshot ()).display ().height ());
            assertEquals (160, result.desiredOutput ().display ().height ());
            for (int track = 0; track < 8; track++) assertEquals (ParameterSlot.trackSend (send, track), f.workspace.parameterSlotOrNull (knob (track), f.snapshot ()));
            assertTrue (result.effects ().isEmpty ());
        }
        assertThrows (IllegalArgumentException.class, () -> GlobalMixerControlsView.send (8, new ParameterTouchSession ()));
    }

    @Test
    void allSendEncodersUseOrdinaryCalibratedRelativeResponse ()
    {
        final Fixture f = new Fixture (3);
        assertEquals (List.of (new AdjustParameterValueEffect (f.target (4), 20)), f.turn (4, 2).effects ());
        f.pressed = Set.of (SHIFT);
        assertEquals (List.of (new AdjustParameterValueEffect (f.target (4), 2)), f.turn (4, 2).effects ());
        assertEquals (List.of (new AdjustParameterValueEffect (f.target (4), -3)), f.turn (4, -3).effects ());
    }

    @Test
    void deleteShiftSelectTouchPreservesResetAcquireConsumeToggleOrder ()
    {
        final Fixture f = new Fixture (7);
        f.pressed = Set.of (DELETE, SHIFT, SELECT);
        final var result = f.touch (2, InputPhase.BEGIN);
        assertEquals (List.of (
            new ConsumeControllerButtonEffect (DELETE), new ResetParameterEffect (f.target (2)),
            new AcquireParameterTouchEffect (knob (2), f.target (2)), new ConsumeControllerButtonEffect (SELECT),
            new SetParameterEnabledEffect (f.target (2), false)), result.effects ());
        assertEquals (Map.of (knob (2), f.target (2)), result.desiredParameterTouches ().targets ());
        assertTrue (f.touch (2, InputPhase.BEGIN).effects ().isEmpty ());
        assertTrue (f.touch (2, InputPhase.END).desiredParameterTouches ().targets ().isEmpty ());
    }

    @Test
    void absentSendStillConsumesBothModifiersAndAutomationReleaseSurvivesPageDeparture ()
    {
        final Fixture f = new Fixture (0);
        f.missing.add (0);
        f.writing = true;
        f.pressed = Set.of (DELETE, SHIFT, SELECT);
        final var router = new de.mossgrabers.pull.core.view.InputGestureRouter ();
        f.touched.add (knob (0));
        final var begin = router.capture (f.input (knob (0), InputKind.TOUCH, InputPhase.BEGIN, 127), f.workspace);
        assertEquals (List.of (new ConsumeControllerButtonEffect (DELETE), new ConsumeControllerButtonEffect (SELECT)), router.dispatch (begin, f.snapshot ()));
        router.finish (begin, f.workspace);
        final var next = CompiledWorkspace.compile ("replacement-page", List.of ());
        router.transition (f.workspace, next);
        next.start (f.snapshot ());
        f.touched.clear ();
        final var end = router.capture (f.input (knob (0), InputKind.TOUCH, InputPhase.END, 0), next);
        assertEquals (List.of (new SetAutomationWriteEffect ("project-a", false)), router.dispatch (end, f.snapshot ()));
        router.finish (end, next);
    }

    @Test
    void repeatedEnableRequestsWaitForReadbackAndDoNotChangeDisplayEarly ()
    {
        final Fixture f = new Fixture (1);
        f.pressed = Set.of (SHIFT, SELECT);
        final var before = f.workspace.activate (f.snapshot ()).desiredOutput ().display ();
        assertTrue (f.touch (0, InputPhase.BEGIN).effects ().contains (new SetParameterEnabledEffect (f.target (0), false)));
        assertEquals (before, f.workspace.activate (f.snapshot ()).desiredOutput ().display ());
        f.touch (0, InputPhase.END);
        assertTrue (f.touch (0, InputPhase.BEGIN).effects ().stream ().noneMatch (SetParameterEnabledEffect.class::isInstance));
        f.touch (0, InputPhase.END);
        f.enabled = false;
        final var acknowledged = f.tick ();
        assertEquals (List.of (new SetParameterEnabledEffect (f.target (0), true)), acknowledged.effects ());
        assertNotEquals (before, acknowledged.desiredOutput ().display ());
    }

    @Test
    void ownerDisagreementRejectsActionsRenderingAndSnapbackBindings ()
    {
        final Fixture f = new Fixture (2);
        f.wrongOwner = true;
        assertNull (f.workspace.parameterSlotOrNull (knob (0), f.snapshot ()));
        assertTrue (f.turn (0, 1).effects ().isEmpty ());
        assertTrue (f.workspace.activate (f.snapshot ()).desiredOutput ().display ().commands ().stream ().noneMatch (DisplayCommand.DottedArc.class::isInstance));
        f.pressed = Set.of (DELETE);
        assertTrue (f.touch (0, InputPhase.BEGIN).effects ().isEmpty ());
        assertTrue (f.workspace.activate (f.snapshot ()).desiredParameterTouches ().targets ().isEmpty ());
    }

    @Test
    void sendRoleChangesRetireOldDesiredTouchesAndQueuedEnableIntent ()
    {
        final Fixture f = new Fixture (0);
        f.pressed = Set.of (SHIFT, SELECT);
        f.touch (0, InputPhase.BEGIN);
        f.touch (0, InputPhase.END);
        f.touch (0, InputPhase.BEGIN);
        f.targetEpoch++;
        f.enabled = false;
        final var result = f.tick ();
        assertTrue (result.effects ().isEmpty ());
        assertTrue (result.desiredParameterTouches ().targets ().isEmpty ());
        assertFalse (f.view.executionRequirements ().ticksRequested ());
    }

    @Test
    void normalSendGraphicsUseModulatedRingAndNameIsNotAPrintedLabel ()
    {
        final Fixture f = new Fixture (0);
        final var scene = f.workspace.activate (f.snapshot ()).desiredOutput ().display ();
        final var rings = scene.commands ().stream ().filter (DisplayCommand.DottedArc.class::isInstance).map (DisplayCommand.DottedArc.class::cast).toList ();
        assertEquals (16, rings.size ());
        assertEquals (-195, rings.get (1).sweepDegrees (), 1e-9);
        assertEquals (COLOR, rings.get (1).color ());
        assertFalse (texts (scene).contains ("Reverb"));
        assertTrue (texts (scene).contains ("-12.0"));
        assertTrue (texts (scene).contains ("dB"));
        f.enabled = false;
        final var disabled = f.workspace.activate (f.snapshot ()).desiredOutput ().display ().commands ().stream ().filter (DisplayCommand.DottedArc.class::isInstance).map (DisplayCommand.DottedArc.class::cast).toList ();
        assertEquals (new RgbColor (52, 52, 52), disabled.get (1).color ());
    }

    @Test
    void inheritedNameHeuristicUsesZeroVuFadersAndHostTextForPanNamedSends ()
    {
        final Fixture f = new Fixture (0);
        f.name = "Volume FX";
        var scene = f.workspace.activate (f.snapshot ()).desiredOutput ().display ();
        assertTrue (scene.commands ().stream ().noneMatch (DisplayCommand.DottedArc.class::isInstance));
        assertTrue (scene.commands ().contains (new DisplayCommand.Rectangle (8, 60, 24, 70, new RgbColor (63, 63, 63))));
        assertTrue (scene.commands ().contains (new DisplayCommand.Rectangle (68, 77.5, 13, 1, COLOR)));
        f.name = "Pan";
        scene = f.workspace.activate (f.snapshot ()).desiredOutput ().display ();
        assertTrue (scene.commands ().contains (new DisplayCommand.RoundedRectangle (8, 104, 82, 4, 2, new RgbColor (63, 63, 63))));
        assertTrue (texts (scene).contains ("-12.0"));
        assertFalse (texts (scene).contains ("L 50"));
    }

    @Test
    void sendPageMenuFreezesItsSelectionAndCancelsIfTheLayoutChanges ()
    {
        for (final boolean changeLayout: List.of (false, true))
        {
            final Fixture f = new Fixture (5);
            f.menuOffset = 4;
            final var input = f.input (PushControlIds.button ("ROW2_5"), InputKind.BUTTON, InputPhase.BEGIN, 127);
            final var action = f.workspace.resolveAction (input, f.snapshot ());
            f.menuOffset = 0;
            if (changeLayout) f.pages.select (f.pages.resolve ("FRAME"));
            assertEquals (changeLayout ? List.of () : List.of (new SetControllerModeSettingEffect (SetControllerModeSettingEffect.Setting.GLOBAL_MIX_MODE, "SEND6")), f.workspace.dispatchAction (action, f.snapshot ()));
            assertEquals (new RgbColor (255, 255, 255), f.workspace.activate (f.snapshot ()).desiredOutput ().lights ().get (PushControlIds.button ("ROW2_7")));
        }
    }

    private static ControlId knob (final int index) { return PushControlIds.continuous ("KNOB" + (index + 1)); }
    private static List<String> texts (final ControllerDisplayScene scene) { return scene.commands ().stream ().flatMap (command -> command instanceof DisplayCommand.TextAt text ? java.util.stream.Stream.of (text.text ()) : command instanceof DisplayCommand.TextBox text ? java.util.stream.Stream.of (text.text ()) : java.util.stream.Stream.empty ()).toList (); }

    private static final class Fixture
    {
        private final PageNavigation pages = PageNavigation.defaults ();
        private final int sendIndex;
        private final ParameterTouchSession session = new ParameterTouchSession ();
        private final GlobalMixerControlsView view;
        private final CompiledWorkspace workspace;
        private final Set<Integer> missing = new HashSet<> ();
        private final Set<ControlId> touched = new HashSet<> ();
        private Set<ControlId> pressed = Set.of ();
        private boolean enabled = true;
        private boolean writing;
        private boolean wrongOwner;
        private long targetEpoch = 1;
        private long layoutGeneration = 1;
        private long sequence;
        private String name = "Reverb";
        private int menuOffset;

        private Fixture (final int sendIndex)
        {
            this.sendIndex = sendIndex;
            this.view = GlobalMixerControlsView.send (sendIndex, this.session, this.pages);
            this.pages.select (this.pages.resolve ("SEND" + (sendIndex + 1)));
            this.workspace = CompiledWorkspace.compile ("send", List.of (this.view, new CurrentTrackFooterView ()));
            this.workspace.start (this.snapshot ());
        }
        private ParameterTargetRef target (final int track) { return new ParameterTargetRef (ParameterTargetKind.LIVE, "send-" + this.sendIndex + "-track-" + track, this.targetEpoch); }
        private ControllerInputEvent input (final ControlId control, final InputKind kind, final InputPhase phase, final long value) { this.sequence++; return new ControllerInputEvent (this.sequence, this.sequence, control, kind, phase, value); }
        private CoreResult turn (final int track, final long delta) { return this.workspace.handle (this.input (knob (track), InputKind.RELATIVE, InputPhase.UPDATE, delta), this.snapshot ()); }
        private CoreResult touch (final int track, final InputPhase phase) { if (phase == InputPhase.END) this.touched.remove (knob (track)); else this.touched.add (knob (track)); return this.workspace.handle (this.input (knob (track), InputKind.TOUCH, phase, phase == InputPhase.END ? 0 : 127), this.snapshot ()); }
        private CoreResult tick () { this.sequence++; return this.workspace.handle (new ControllerTickEvent (this.sequence, this.sequence), this.snapshot ()); }
        private ControllerSnapshot snapshot ()
        {
            final var e = ControllerBridgeSnapshot.empty ();
            final var tracks = new ArrayList<CurrentTrackSnapshot> ();
            final Map<ParameterSlot, ParameterTargetSnapshot> slots = new LinkedHashMap<> ();
            for (int track = 0; track < 8; track++)
            {
                tracks.add (new CurrentTrackSnapshot (new SessionTrackSnapshot ("track-" + track, track, "Track " + track, true, track == 0, true, false, false, false, false, SessionTrackType.AUDIO, COLOR), false, 1, 1));
                if (!this.missing.contains (track)) slots.put (ParameterSlot.trackSend (this.sendIndex, track), new ParameterTargetSnapshot (this.target (track), this.name, 256, 768, "-12.0 dB", -1, 0, Optional.of (Boolean.valueOf (this.enabled)), new ParameterTargetIdentitySnapshot ("channel-send", this.wrongOwner ? "other" : "track-" + track, 0, this.sendIndex)));
            }
            final var bank = new CurrentTrackBankSnapshot (1, "main", 0, tracks, "track-0", false, 0, false);
            final var settings = new ControllerSettingsSnapshot (true, true, "SEND" + (this.sendIndex + 1), this.menuOffset, new CursorSendBankSnapshot (1, "cursor", 0, IntStream.range (0, 8).mapToObj (index -> new CursorSendBankSnapshot.Send (true, "FX " + index)).toList ()));
            final var bridge = new ControllerBridgeSnapshot (e.transport (), e.selectedTrack (), e.sessionBank (), new ControllerLayoutSnapshot (this.layoutGeneration, "PLAY", "SEND" + (this.sendIndex + 1), false, false, 0, GridPressureConfiguration.OFF), e.noteView (), e.noteRepeat (), e.drum (), new ParameterBridgeSnapshot (slots, Map.of ()), e.controllerMappingFeedback (), e.master (), new ProjectSnapshot (true, "project-a", "A", true, false, false, false), new AutomationSnapshot ("project-a", this.writing, true), new EncoderConfigurationSnapshot (true, 1024, 10, 0, -90), bank, e.transportSettings (), settings);
            return new ControllerSnapshot (this.sequence, this.sequence, new PullCoreProvider ().descriptor ().requiredCapabilities (), bridge, ClipCatalogSnapshot.empty (), Map.of (), Map.of (), Optional.empty (), this.pressed, this.touched);
        }
    }
}
