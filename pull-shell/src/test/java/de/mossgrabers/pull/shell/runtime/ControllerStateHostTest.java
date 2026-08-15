// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.shell.runtime;

import de.mossgrabers.framework.daw.midi.SelectedTrackMonitorMode;
import de.mossgrabers.framework.daw.midi.SelectedTrackNoteTargetSnapshot;
import de.mossgrabers.pull.core.api.ControllerNoteView;
import de.mossgrabers.pull.core.api.DesiredControllerLayout;
import de.mossgrabers.pull.core.api.DesiredControllerState;
import de.mossgrabers.pull.core.api.DesiredControllerWorkspace;
import de.mossgrabers.pull.core.api.DesiredNoteInputRoute;
import de.mossgrabers.pull.core.api.DesiredNotePerformance;
import de.mossgrabers.pull.core.api.ControllerViewFacet;
import de.mossgrabers.pull.core.api.SessionBankShape;
import de.mossgrabers.pull.shell.input.InputKind;
import de.mossgrabers.pull.shell.input.InputPhase;
import de.mossgrabers.pull.shell.input.InputRoute;
import de.mossgrabers.pull.shell.input.PhysicalControlRegistry;
import de.mossgrabers.pull.shell.input.PhysicalInputRouter;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;


/** Tests the stable lifecycle boundary shared by every composed musical controller state. */
class ControllerStateHostTest
{
    @Test
    void entersByRoutingBeforeLayoutAndReplaysWithoutRouteChurn ()
    {
        final Fixture fixture = new Fixture ();
        final DesiredControllerState performance = fixture.performance (ControllerNoteView.PLAY);

        fixture.host.apply (performance);
        assertEquals (List.of ("route:on", "workspace:", "layout:PLAY"), fixture.events);
        assertTrue (fixture.target.routeActive);

        fixture.events.clear ();
        fixture.host.apply (performance);
        assertEquals (List.of ("workspace:", "layout:PLAY"), fixture.events);
        assertTrue (fixture.target.routeActive);
    }


    @Test
    void exitsByNeutralizingLayoutAndDefersDetachUntilInputIsIdle ()
    {
        final Fixture fixture = new Fixture ();
        fixture.host.apply (fixture.performance (ControllerNoteView.PLAY));
        fixture.events.clear ();
        fixture.idle.set (false);

        fixture.host.apply (DesiredControllerState.empty ());
        fixture.host.refresh ();
        assertEquals (List.of ("layout:NEUTRAL"), fixture.events);
        assertTrue (fixture.target.routeActive);

        fixture.idle.set (true);
        fixture.host.refresh ();
        assertEquals (List.of ("layout:NEUTRAL", "midi:neutral", "route:off", "workspace:", "layout:NONE"), fixture.events);
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
        assertEquals (List.of ("layout:NEUTRAL", "midi:neutral", "route:off", "workspace:invalidate"), fixture.events);
        assertFalse (fixture.target.routeActive);

        fixture.events.clear ();
        fixture.host.apply (fixture.performance (ControllerNoteView.DRUM_PAD));
        assertEquals (List.of ("layout:NEUTRAL"), fixture.events);
        assertFalse (fixture.target.routeActive);

        fixture.events.clear ();
        fixture.idle.set (true);
        fixture.host.refresh ();
        assertEquals (List.of ("route:on", "workspace:", "layout:DRUM_PAD"), fixture.events);
        assertTrue (fixture.target.routeActive);
    }


    @Test
    void requestedTargetDisagreementDetachesThePreviouslyValidRoute ()
    {
        final Fixture fixture = new Fixture ();
        fixture.host.apply (fixture.performance (ControllerNoteView.PLAY));
        fixture.events.clear ();

        fixture.host.apply (state (ControllerNoteView.PLAY, 2, "track-b"));

        assertEquals (List.of ("layout:NEUTRAL", "midi:neutral", "route:off", "workspace:invalidate"), fixture.events);
        assertFalse (fixture.target.routeActive);
    }


    @Test
    void invalidationNeutralizesAndDetachesImmediately ()
    {
        final Fixture fixture = new Fixture ();
        fixture.host.apply (fixture.performance (ControllerNoteView.PLAY));
        fixture.events.clear ();
        fixture.idle.set (false);

        fixture.host.invalidate ();

        assertEquals (List.of ("layout:NEUTRAL", "midi:neutral", "route:off", "workspace:invalidate"), fixture.events);
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
        final ControllerStateHost host = new ControllerStateHost (target, new RecordingSurface (events), () -> events.add ("midi:neutral"));
        host.setInputLifecycleIdle (() -> inputs.gesturesIdle (input -> input.kind () == InputKind.PAD));
        host.apply (state (ControllerNoteView.PLAY, 1, "track-a"));

        inputs.route (pad, InputKind.PAD, InputPhase.BEGIN, 100, () -> { });
        host.apply (DesiredControllerState.empty ());
        assertTrue (target.routeActive);

        inputs.route (pad, InputKind.PAD, InputPhase.END, 0, () -> { });
        host.refresh ();
        assertFalse (target.routeActive);
        assertEquals (List.of ("route:on", "workspace:", "layout:PLAY", "layout:NEUTRAL", "midi:neutral", "route:off", "workspace:", "layout:NONE"), events);
    }


    @Test
    void neutralizationFailureCannotKeepTheRouteAttached ()
    {
        final List<String> events = new ArrayList<> ();
        final MutableTarget target = new MutableTarget (events);
        final ControllerStateHost host = new ControllerStateHost (target, new RecordingSurface (events), () -> {
            throw new IllegalStateException ("broken neutralizer");
        });
        host.apply (state (ControllerNoteView.PLAY, 1, "track-a"));

        assertThrows (IllegalStateException.class, host::invalidate);
        assertFalse (target.routeActive);
    }


    @Test
    void compositeDrumStateRoutesBeforeWorkspaceAndReplacesAFullNoteLayoutWithoutRouteChurn ()
    {
        final Fixture fixture = new Fixture ();
        final DesiredControllerWorkspace composite = new DesiredControllerWorkspace ("VS", Set.of (ControllerViewFacet.DRUM_CONTROLLER_LOWER), SessionBankShape.empty ());
        final DesiredControllerState state = new DesiredControllerState (composite, new DesiredNotePerformance (DesiredControllerLayout.empty (), DesiredNoteInputRoute.selectedTrack (1, "track-a")));

        fixture.host.apply (fixture.performance (ControllerNoteView.PLAY));
        fixture.events.clear ();
        fixture.host.apply (state);

        assertEquals (List.of ("workspace:VS", "layout:NONE"), fixture.events);
        assertTrue (fixture.target.routeActive);
    }


    @Test
    void refreshDoesNotReassertAnAppliedWorkspaceOverAnExternalMasterLayout ()
    {
        final Fixture fixture = new Fixture ();
        final DesiredControllerWorkspace composite = new DesiredControllerWorkspace ("VS", Set.of (ControllerViewFacet.DRUM_CONTROLLER_LOWER), SessionBankShape.empty ());
        fixture.host.apply (new DesiredControllerState (composite, new DesiredNotePerformance (DesiredControllerLayout.empty (), DesiredNoteInputRoute.selectedTrack (1, "track-a"))));
        fixture.events.clear ();

        fixture.host.refresh ();

        assertTrue (fixture.events.isEmpty ());
    }


    @Test
    void enteringLayoutFailureRollsBackTheJustSubmittedRoute ()
    {
        final Fixture fixture = new Fixture ();
        fixture.surface.failView = ControllerNoteView.PLAY;

        assertThrows (IllegalStateException.class, () -> fixture.host.apply (fixture.performance (ControllerNoteView.PLAY)));

        assertFalse (fixture.target.routeActive);
        assertEquals (List.of ("route:on", "workspace:", "layout:PLAY", "layout:NEUTRAL", "midi:neutral", "route:off", "workspace:invalidate"), fixture.events);
    }


    @Test
    void targetMismatchDetachesEvenWhenSafeLayoutApplicationFails ()
    {
        final Fixture fixture = new Fixture ();
        fixture.host.apply (fixture.performance (ControllerNoteView.PLAY));
        fixture.events.clear ();
        fixture.surface.failNeutral = true;
        fixture.target.generation = 2;
        fixture.target.channelID = "track-b";

        assertThrows (IllegalStateException.class, fixture.host::refresh);

        assertFalse (fixture.target.routeActive);
        assertTrue (fixture.events.contains ("route:off"));
    }


    private static final class Fixture
    {
        private final List<String> events = new ArrayList<> ();
        private final AtomicBoolean idle = new AtomicBoolean (true);
        private final MutableTarget target = new MutableTarget (this.events);
        private final RecordingSurface surface = new RecordingSurface (this.events);
        private final ControllerStateHost host = new ControllerStateHost (this.target, this.surface, () -> this.events.add ("midi:neutral"));


        private Fixture ()
        {
            this.host.setInputLifecycleIdle (this.idle::get);
        }


        private DesiredControllerState performance (final ControllerNoteView view)
        {
            return state (view, this.target.generation, this.target.channelID);
        }
    }


    private static DesiredControllerState state (final ControllerNoteView view, final long generation, final String channelId)
    {
        return new DesiredControllerState (DesiredControllerWorkspace.empty (), new DesiredNotePerformance (DesiredControllerLayout.note (view), DesiredNoteInputRoute.selectedTrack (generation, channelId)));
    }


    private static final class RecordingSurface implements ControllerStateHost.Surface
    {
        private final List<String> events;
        private ControllerNoteView failView;
        private boolean failNeutral;


        private RecordingSurface (final List<String> events)
        {
            this.events = events;
        }


        @Override
        public DesiredControllerWorkspace prepareWorkspace (final DesiredControllerWorkspace workspace)
        {
            return workspace;
        }


        @Override
        public DesiredControllerLayout prepareLayout (final DesiredControllerLayout layout)
        {
            return layout;
        }


        @Override
        public void applyWorkspace (final DesiredControllerWorkspace workspace)
        {
            this.events.add ("workspace:" + workspace.name ());
        }


        @Override
        public void applyLayout (final DesiredControllerLayout layout)
        {
            this.events.add ("layout:" + (layout.neutralizing () ? "NEUTRAL" : layout.noteView ().name ()));
            if (layout.neutralizing () && this.failNeutral || this.failView != null && layout.noteView () == this.failView)
                throw new IllegalStateException ("broken layout");
        }


        @Override
        public void invalidate ()
        {
            this.events.add ("workspace:invalidate");
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
