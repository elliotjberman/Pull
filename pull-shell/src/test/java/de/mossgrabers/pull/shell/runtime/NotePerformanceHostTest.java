// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.shell.runtime;

import de.mossgrabers.framework.daw.midi.SelectedTrackMonitorMode;
import de.mossgrabers.framework.daw.midi.SelectedTrackNoteTargetSnapshot;
import de.mossgrabers.pull.core.api.ControllerNoteView;
import de.mossgrabers.pull.core.api.DesiredControllerLayout;
import de.mossgrabers.pull.core.api.DesiredNoteInputRoute;
import de.mossgrabers.pull.core.api.DesiredNotePerformance;
import de.mossgrabers.pull.shell.input.InputKind;
import de.mossgrabers.pull.shell.input.InputPhase;
import de.mossgrabers.pull.shell.input.InputRoute;
import de.mossgrabers.pull.shell.input.PhysicalControlRegistry;
import de.mossgrabers.pull.shell.input.PhysicalInputRouter;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;


/** Tests the stable lifecycle boundary shared by every selected-track Note viewer. */
class NotePerformanceHostTest
{
    @Test
    void entersByRoutingBeforeLayoutAndReplaysWithoutRouteChurn ()
    {
        final Fixture fixture = new Fixture ();
        final DesiredNotePerformance performance = fixture.performance (ControllerNoteView.PLAY);

        fixture.host.apply (performance);
        assertEquals (List.of ("route:on", "layout:PLAY"), fixture.events);
        assertTrue (fixture.target.routeActive);

        fixture.events.clear ();
        fixture.host.apply (performance);
        assertEquals (List.of ("layout:PLAY"), fixture.events);
        assertTrue (fixture.target.routeActive);
    }


    @Test
    void exitsByNeutralizingLayoutAndDefersDetachUntilInputIsIdle ()
    {
        final Fixture fixture = new Fixture ();
        fixture.host.apply (fixture.performance (ControllerNoteView.PLAY));
        fixture.events.clear ();
        fixture.idle.set (false);

        fixture.host.apply (DesiredNotePerformance.inactive ());
        fixture.host.refresh ();
        assertEquals (List.of ("layout:NEUTRAL"), fixture.events);
        assertTrue (fixture.target.routeActive);

        fixture.idle.set (true);
        fixture.host.refresh ();
        assertEquals (List.of ("layout:NEUTRAL", "midi:neutral", "route:off"), fixture.events);
        assertFalse (fixture.target.routeActive);
    }


    @Test
    void selectionDisagreementFailsClosedAndNewTargetWaitsForIdleAlignment ()
    {
        final Fixture fixture = new Fixture ();
        fixture.host.apply (fixture.performance (ControllerNoteView.PLAY));
        fixture.events.clear ();
        fixture.idle.set (false);
        fixture.target.generation = 2;
        fixture.target.channelID = "track-b";

        fixture.host.refresh ();
        assertEquals (List.of ("layout:NEUTRAL", "midi:neutral", "route:off"), fixture.events);
        assertFalse (fixture.target.routeActive);

        fixture.events.clear ();
        fixture.host.apply (fixture.performance (ControllerNoteView.DRUM_PAD));
        assertEquals (List.of ("layout:NEUTRAL"), fixture.events);
        assertFalse (fixture.target.routeActive);

        fixture.events.clear ();
        fixture.idle.set (true);
        fixture.host.refresh ();
        assertEquals (List.of ("route:on", "layout:DRUM_PAD"), fixture.events);
        assertTrue (fixture.target.routeActive);
    }


    @Test
    void invalidationNeutralizesAndDetachesImmediately ()
    {
        final Fixture fixture = new Fixture ();
        fixture.host.apply (fixture.performance (ControllerNoteView.PLAY));
        fixture.events.clear ();
        fixture.idle.set (false);

        fixture.host.invalidate ();

        assertEquals (List.of ("layout:NEUTRAL", "midi:neutral", "route:off"), fixture.events);
        assertFalse (fixture.target.routeActive);
    }


    @Test
    void heldStableOnlyMusicalPadKeepsTheRouteUntilItsNoteOff ()
    {
        final List<String> events = new ArrayList<> ();
        final MutableTarget target = new MutableTarget (events);
        final String pad = "push.pad.1";
        final PhysicalInputRouter<String> inputs = new PhysicalInputRouter<> (
            PhysicalControlRegistry.<String>builder (1).register (pad, InputKind.PAD).build (),
            (ignoredControl, ignoredKind) -> InputRoute.NONE,
            ignored -> { });
        final NotePerformanceHost host = new NotePerformanceHost (target, layout -> layout, layout -> events.add ("layout:" + (layout.neutralizing () ? "NEUTRAL" : layout.noteView ().name ())), () -> events.add ("midi:neutral"));
        host.setInputLifecycleIdle (() -> inputs.gesturesIdle (input -> input.kind () == InputKind.PAD));
        host.apply (new DesiredNotePerformance (DesiredControllerLayout.note (ControllerNoteView.PLAY), DesiredNoteInputRoute.selectedTrack (1, "track-a")));

        inputs.route (pad, InputKind.PAD, InputPhase.BEGIN, 100, () -> { });
        host.apply (DesiredNotePerformance.inactive ());
        assertTrue (target.routeActive);

        inputs.route (pad, InputKind.PAD, InputPhase.END, 0, () -> { });
        host.refresh ();
        assertFalse (target.routeActive);
        assertEquals (List.of ("route:on", "layout:PLAY", "layout:NEUTRAL", "midi:neutral", "route:off"), events);
    }


    @Test
    void neutralizationFailureCannotKeepTheRouteAttached ()
    {
        final List<String> events = new ArrayList<> ();
        final MutableTarget target = new MutableTarget (events);
        final NotePerformanceHost host = new NotePerformanceHost (target, layout -> layout, layout -> { }, () -> {
            throw new IllegalStateException ("broken neutralizer");
        });
        host.apply (new DesiredNotePerformance (DesiredControllerLayout.note (ControllerNoteView.PLAY), DesiredNoteInputRoute.selectedTrack (1, "track-a")));

        assertThrows (IllegalStateException.class, host::invalidate);
        assertFalse (target.routeActive);
    }


    private static final class Fixture
    {
        private final List<String> events = new ArrayList<> ();
        private final AtomicBoolean idle = new AtomicBoolean (true);
        private final MutableTarget target = new MutableTarget (this.events);
        private final NotePerformanceHost host = new NotePerformanceHost (this.target, layout -> layout, layout -> this.events.add ("layout:" + (layout.neutralizing () ? "NEUTRAL" : layout.noteView ().name ())), () -> this.events.add ("midi:neutral"));


        private Fixture ()
        {
            this.host.setInputLifecycleIdle (this.idle::get);
        }


        private DesiredNotePerformance performance (final ControllerNoteView view)
        {
            return new DesiredNotePerformance (DesiredControllerLayout.note (view), DesiredNoteInputRoute.selectedTrack (this.target.generation, this.target.channelID));
        }
    }


    private static final class MutableTarget extends SelectedTrackNoteTargetAdapter
    {
        private final List<String> events;
        private boolean routeActive;


        private MutableTarget (final List<String> events)
        {
            this.events = events;
        }


        @Override
        public void submitNoteInputRoute (final boolean active)
        {
            this.events.add (active ? "route:on" : "route:off");
            this.routeActive = active;
        }


        @Override
        public SelectedTrackNoteTargetSnapshot snapshot ()
        {
            return new SelectedTrackNoteTargetSnapshot (this.generation, this.channelID, true, "Track", 0, 0, 0, "Instrument", 0, true, false, false, false, true, false, SelectedTrackMonitorMode.AUTO, false, false, false, false, true, 0.5, 0.5);
        }
    }
}
