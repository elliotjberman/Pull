// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.api.*;
import de.mossgrabers.pull.core.api.effect.*;
import de.mossgrabers.pull.core.api.event.*;
import de.mossgrabers.pull.core.runtime.PullCoreProvider;
import org.junit.jupiter.api.Test;
import java.util.*;
import static de.mossgrabers.pull.core.api.NoteParameterRole.*;
import static org.junit.jupiter.api.Assertions.*;

/** Runs the real page composition and shared gesture/Snapback lifecycle; host advancement is explicit. */
class NoteParameterControlsViewTest
{
    private static final ControlId SHIFT = PushControlIds.button ("SHIFT");
    private static final ControlId DELETE = PushControlIds.button ("DELETE");

    @Test
    void everyPageEditsEachSelectedNoteAndResetPreservesSpacers ()
    {
        final Map<String, NoteParameterRole[]> pages = Map.of (
            "NOTE", new NoteParameterRole[] {DURATION, MUTE, VELOCITY, VELOCITY_SPREAD, RELEASE_VELOCITY, CHANCE, OCCURRENCE, RECURRENCE_LENGTH},
            "EXPRESSIONS", new NoteParameterRole[] {DURATION, MUTE, null, GAIN, PAN, TRANSPOSE, TIMBRE, PRESSURE},
            "REPEAT", new NoteParameterRole[] {DURATION, MUTE, null, REPEAT_COUNT, REPEAT_CURVE, REPEAT_VELOCITY_CURVE, REPEAT_VELOCITY_END, null},
            "RECCURRENCE_PATTERN", new NoteParameterRole[] {null, null, null, null, null, null, null, RECURRENCE_LENGTH});
        pages.forEach ((page, roles) -> {
            final Fixture f = new Fixture ();
            f.page = page;
            f.tick ();
            for (int column = 0; column < 8; column++)
            {
                f.turn (column, 1);
                final var effects = f.result.effects ();
                if (roles[column] == null) assertTrue (effects.isEmpty ());
                else
                {
                    assertEquals (2, effects.size ());
                    for (int cell = 0; cell < 2; cell++)
                        assertEquals (f.target (roles[column], cell), ((SetCurrentParameterValueEffect) effects.get (cell)).target ());
                }
                f.pressed = Set.of (DELETE);
                f.touch (column, InputPhase.BEGIN);
                assertEquals (new ConsumeControllerButtonEffect (DELETE), f.result.effects ().getFirst ());
                assertEquals (roles[column] == null ? 1 : 3, f.result.effects ().size ());
                assertTrue (f.result.desiredParameterTouches ().targets ().isEmpty (), "note cells are not native touchable parameters");
                f.touch (column, InputPhase.END);
                f.pressed = Set.of ();
            }
        });
    }

    @Test
    void turnsAccumulateBeforeAcknowledgementAndFeedbackWaitsForTheHost ()
    {
        final Fixture f = new Fixture ();
        final var before = f.result.desiredOutput ().display ();
        f.turn (2, 1);
        assertEquals (.2 + 10.0 / 1023, f.request (0), 0.000001);
        assertEquals (.7 + 10.0 / 1023, f.request (1), 0.000001);
        assertEquals (before, f.result.desiredOutput ().display ());
        f.touch (0, InputPhase.BEGIN);
        f.touch (0, InputPhase.END); // Another knob's release must not discard pending velocity motion.
        f.turn (2, 1);
        assertEquals (.2 + 20.0 / 1023, f.request (0), 0.000001);
        final double next = f.request (0);
        f.applied.put (VELOCITY.slot (0), next);
        f.tick ();
        assertNotEquals (before, f.result.desiredOutput ().display ());
        f.turn (2, 1);
        assertEquals (next + 10.0 / 1023, f.request (0), 0.000001);
    }

    @Test
    void discreteParametersKeepCallbackOrderAtBounds ()
    {
        final Fixture f = new Fixture ();
        f.applied.put (OCCURRENCE.slot (0), 10.0);
        f.applied.put (OCCURRENCE.slot (1), 0.0);
        f.tick ();
        f.turn (6, 7, -7);
        assertEquals (9, f.request (0));
        assertEquals (0, f.request (1));
        f.turn (0, -127, 127);
        assertEquals (1, f.request (0));
    }

    @Test
    void shiftRestoresEachNotesOwnBaselineOnlyAfterReadback ()
    {
        final Fixture f = new Fixture ();
        f.shift (InputPhase.BEGIN);
        f.turn (2, 2);
        assertEquals (.2 + 2.0 / 1023, f.request (0), 0.000001);
        final var baselines = Map.of (f.target (VELOCITY, 0), .2, f.target (VELOCITY, 1), .7);
        assertEquals (baselines, f.result.desiredParameterInteraction ().baselines ());
        f.applied.put (VELOCITY.slot (0), .3);
        f.applied.put (VELOCITY.slot (1), .8);
        f.tick ();
        f.shift (InputPhase.END);
        f.hostTick ();
        assertTrue (f.result.effects ().isEmpty ());
        f.hostTick ();
        final Set<CoreEffect> restore = new HashSet<> (f.result.effects ());
        assertEquals (Set.of (new SetParameterValueEffect (f.target (VELOCITY, 0), .2), new SetParameterValueEffect (f.target (VELOCITY, 1), .7)), restore);
        assertEquals (baselines, f.result.desiredParameterInteraction ().baselines ());
        f.applied.put (VELOCITY.slot (0), .2);
        f.applied.put (VELOCITY.slot (1), .7);
        f.hostTick ();
        f.hostTick ();
        assertTrue (f.result.desiredParameterInteraction ().baselines ().isEmpty ());
    }

    @Test
    void aHeldEncoderCannotAdoptANewSelectionPageOrIncompleteGroup ()
    {
        for (int change = 0; change < 3; change++)
        {
            final Fixture f = new Fixture ();
            f.touch (2, InputPhase.BEGIN);
            if (change == 0) f.owner = "replacement";
            else if (change == 1) f.page = "EXPRESSIONS";
            else f.missing = VELOCITY.slot (1);
            f.tick ();
            f.turn (2, 1);
            assertTrue (f.result.effects ().isEmpty ());
            f.owner = "notes";
            f.page = "NOTE";
            f.missing = null;
            f.tick ();
            f.turn (2, 1);
            assertTrue (f.result.effects ().isEmpty (), "returning to the old state cannot revive the gesture");
            f.touch (2, InputPhase.END);
            f.touch (2, InputPhase.BEGIN);
            f.turn (2, 1);
            assertEquals (2, f.result.effects ().size ());
        }
    }

    private static ControlId knob (final int index) { return PushControlIds.continuous ("KNOB" + (index + 1)); }

    private static final class Fixture
    {
        private final ControllerCore core = new PullCoreProvider ().create ();
        private final Map<ParameterSlot, Double> applied = new LinkedHashMap<> ();
        private String owner = "notes";
        private String page = "NOTE";
        private ParameterSlot missing;
        private Set<ControlId> pressed = Set.of ();
        private Set<ControlId> touched = Set.of ();
        private long revision;
        private LegacyControllerPageRequests requests = LegacyControllerPageRequests.empty ();
        private CoreResult result;

        Fixture ()
        {
            for (int cell = 0; cell < 2; cell++)
                for (final var role: NoteParameterRole.values ())
                    this.applied.put (role.slot (cell), role == VELOCITY ? cell == 0 ? .2 : .7 : role == DURATION || role == RECURRENCE_LENGTH ? 1.0 : 0.0);
            this.result = this.core.start (this.snapshot (), Optional.empty ());
            final var state = this.result.desiredControllerState ().page ();
            this.requests = new LegacyControllerPageRequests (state.acknowledgedRequestSequence (), List.of (new LegacyControllerPageRequest (
                state.acknowledgedRequestSequence () + 1, state.revision (), state.temporaryToken (), LegacyControllerPageRequest.Operation.SELECT, "NOTE")));
            this.tick ();
            this.requests = new LegacyControllerPageRequests (this.result.desiredControllerState ().page ().acknowledgedRequestSequence (), List.of ());
        }
        ParameterTargetRef target (final NoteParameterRole role, final int cell) { return new ParameterTargetRef (ParameterTargetKind.LIVE, this.owner + ":" + role.slot (cell).index (), 1); }
        double request (final int cell) { return ((SetCurrentParameterValueEffect) this.result.effects ().get (cell)).value (); }
        void tick () { this.revision++; this.result = this.core.handle (new SnapshotChangedEvent (this.revision, this.revision), this.snapshot ()); }
        void hostTick () { this.revision++; this.result = this.core.handle (new ControllerTickEvent (this.revision, this.revision), this.snapshot ()); }
        void shift (final InputPhase phase)
        {
            this.pressed = phase == InputPhase.END ? Set.of () : Set.of (SHIFT);
            this.input (SHIFT, InputKind.BUTTON, phase, phase == InputPhase.END ? 0 : 127, List.of ());
        }
        void touch (final int index, final InputPhase phase)
        {
            this.touched = phase == InputPhase.END ? Set.of () : Set.of (knob (index));
            this.input (knob (index), InputKind.TOUCH, phase, phase == InputPhase.END ? 0 : 127, List.of ());
        }
        void turn (final int index, final long... values)
        {
            this.input (knob (index), InputKind.RELATIVE, InputPhase.UPDATE, Arrays.stream (values).sum (), Arrays.stream (values).boxed ().toList ());
        }
        void input (final ControlId control, final InputKind kind, final InputPhase phase, final long value, final List<Long> samples)
        {
            this.revision++;
            this.result = this.core.handle (new ControllerInputEvent (this.revision, this.revision, control, kind, phase, value, samples), this.snapshot ());
        }
        ControllerSnapshot snapshot ()
        {
            final Map<ParameterSlot, ParameterTargetSnapshot> parameters = new LinkedHashMap<> ();
            this.applied.forEach ((slot, value) -> {
                if (slot.equals (this.missing)) return;
                final int cell = slot.index () / NoteParameterRole.values ().length;
                final var role = NoteParameterRole.values ()[slot.index () % NoteParameterRole.values ().length];
                parameters.put (slot, new ParameterTargetSnapshot (this.target (role, cell), role.name (), value, -1, value.toString (), 128, .000001,
                    Optional.empty (), new ParameterTargetIdentitySnapshot ("note-attribute", this.owner, cell, role.ordinal ())));
            });
            final var data = new EditingPageState.NoteData (1, false, this.applied.get (VELOCITY.slot (0)), 0, 1, false, 1, false, "ALWAYS", false, 1, 1, .5, 0, 0, 0, 0, false, 0, 0, 0, 0);
            final var note = new EditingPageState.Note (true, this.page, 2, 0, 60, 4, 24, false, Collections.nCopies (8, false), data, this.owner);
            final var e = ControllerBridgeSnapshot.empty ();
            final var bridge = new ControllerBridgeSnapshot (e.transport (), e.selectedTrack (), e.sessionBank (), e.layout (), e.noteView (), e.noteRepeat (), e.drum (),
                new ParameterBridgeSnapshot (parameters, Map.of (), Set.of ()), e.controllerMappingFeedback (), e.master (), e.project (),
                new AutomationSnapshot ("project", false, true), new EncoderConfigurationSnapshot (true, 1024, 10, 0, -90), e.currentTrackBank (), e.transportSettings (), e.controllerSettings (), e.applicationUi (),
                this.requests, e.browser (), e.controllerHardware (), new ControllerPageDisplaySnapshot ("NOTE", note));
            return new ControllerSnapshot (this.revision, this.revision, new PullCoreProvider ().descriptor ().requiredCapabilities (), bridge,
                ClipCatalogSnapshot.empty (), Map.of (), Map.of (), Optional.empty (), this.pressed, this.touched);
        }
    }
}
