// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.api.ClipCatalogSnapshot;
import de.mossgrabers.pull.core.api.ControlId;
import de.mossgrabers.pull.core.api.ControllerBridgeSnapshot;
import de.mossgrabers.pull.core.api.ControllerCore;
import de.mossgrabers.pull.core.api.ControllerLayoutSnapshot;
import de.mossgrabers.pull.core.api.ControllerSnapshot;
import de.mossgrabers.pull.core.api.CoreResult;
import de.mossgrabers.pull.core.api.DrumContextSnapshot;
import de.mossgrabers.pull.core.api.InputRouteMode;
import de.mossgrabers.pull.core.api.MasterSnapshot;
import de.mossgrabers.pull.core.api.ParameterBridgeSnapshot;
import de.mossgrabers.pull.core.api.ProjectSnapshot;
import de.mossgrabers.pull.core.api.PushControlIds;
import de.mossgrabers.pull.core.api.SelectedTrackSnapshot;
import de.mossgrabers.pull.core.api.TransportSnapshot;
import de.mossgrabers.pull.core.api.effect.SetProjectTransportStateEffect;
import de.mossgrabers.pull.core.api.effect.ShowHostNotificationEffect;
import de.mossgrabers.pull.core.api.effect.TapTempoEffect;
import de.mossgrabers.pull.core.api.effect.TransportState;
import de.mossgrabers.pull.core.api.event.ControllerInputEvent;
import de.mossgrabers.pull.core.api.event.ControllerTickEvent;
import de.mossgrabers.pull.core.api.event.InputKind;
import de.mossgrabers.pull.core.api.event.InputPhase;
import de.mossgrabers.pull.core.api.output.RgbColor;
import de.mossgrabers.pull.core.runtime.PullCoreProvider;
import de.mossgrabers.pull.core.view.CompiledWorkspace;
import de.mossgrabers.pull.core.view.RetainedControllerView;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;


class TapTempoViewTest
{
    private static final ControlId TAP = PushControlIds.button ("TAP_TEMPO");
    private static final ControlId SHIFT = PushControlIds.button ("SHIFT");


    @Test
    void pressRequestsNativeTapAndLaterTickNotifiesReadbackWithCompleteLightAndCadence ()
    {
        final Fixture fixture = new Fixture ();
        final CoreResult start = fixture.initial;
        assertEquals (InputRouteMode.EXCLUSIVE, start.desiredInputRoutes ().modeOrNull (TAP, InputKind.BUTTON));
        assertEquals (new RgbColor (60, 60, 60), start.desiredOutput ().lights ().get (TAP));
        assertFalse (start.executionRequirements ().ticksRequested ());

        final CoreResult down = fixture.edge (InputPhase.BEGIN);
        assertEquals (List.of (new TapTempoEffect ("project-a")), down.effects ());
        assertEquals (new RgbColor (255, 255, 255), down.desiredOutput ().lights ().get (TAP));
        assertTrue (down.executionRequirements ().ticksRequested ());
        assertTrue (fixture.edge (InputPhase.LONG).effects ().isEmpty ());
        assertTrue (fixture.edge (InputPhase.END).effects ().isEmpty (), "release cannot stand in for later host sampling");
        fixture.tempo = 123.456;
        final CoreResult later = fixture.tick ();
        assertEquals (List.of (new ShowHostNotificationEffect ("Tempo: 123.46")), later.effects ());
        assertFalse (later.executionRequirements ().ticksRequested ());
        assertTrue (fixture.tick ().effects ().isEmpty ());
    }


    @Test
    void shiftedReleaseTogglesOnlyAfterAnAdmittedBeginAndSerializesRapidToggles ()
    {
        final Fixture fixture = new Fixture ();
        fixture.pressed.add (SHIFT);
        assertTrue (fixture.edge (InputPhase.END).effects ().isEmpty ());
        assertTrue (fixture.edge (InputPhase.BEGIN).effects ().isEmpty ());
        assertEquals (List.of (metronome (true)), fixture.edge (InputPhase.END).effects ());
        assertFalse (fixture.metronome);
        fixture.edge (InputPhase.BEGIN);
        assertTrue (fixture.edge (InputPhase.END).effects ().isEmpty ());
        assertTrue (fixture.tick ().effects ().isEmpty ());
        fixture.metronome = true;
        assertEquals (List.of (metronome (false)), fixture.tick ().effects ());
        assertTrue (fixture.tick ().executionRequirements ().ticksRequested ());
        fixture.metronome = false;
        assertFalse (fixture.tick ().executionRequirements ().ticksRequested ());
    }


    @Test
    void shiftIsReadAtEachEdgeAndDuplicateBeginDoesNotTapTwice ()
    {
        final Fixture fixture = new Fixture ();
        assertEquals (List.of (new TapTempoEffect ("project-a")), fixture.edge (InputPhase.BEGIN).effects ());
        assertTrue (fixture.edge (InputPhase.BEGIN).effects ().isEmpty ());
        fixture.pressed.add (SHIFT);
        assertEquals (List.of (metronome (true)), fixture.edge (InputPhase.END).effects ());

        final Fixture shiftedStart = new Fixture ();
        shiftedStart.pressed.add (SHIFT);
        assertTrue (shiftedStart.edge (InputPhase.BEGIN).effects ().isEmpty ());
        shiftedStart.pressed.clear ();
        assertTrue (shiftedStart.edge (InputPhase.END).effects ().isEmpty ());
    }


    @Test
    void projectChangeAndInactiveEngineCancelPendingNotice ()
    {
        final Fixture fixture = new Fixture ();
        fixture.edge (InputPhase.BEGIN);
        fixture.project = "project-b";
        assertTrue (fixture.tick ().effects ().isEmpty ());
        assertFalse (fixture.tick ().executionRequirements ().ticksRequested ());
        fixture.edge (InputPhase.END);
        fixture.engine = false;
        assertTrue (fixture.edge (InputPhase.BEGIN).effects ().isEmpty ());
        assertFalse (fixture.tick ().executionRequirements ().ticksRequested ());
    }


    @Test
    void productionCoreMergesViewCadenceInsteadOfReplacingItWithPlaybackCadence ()
    {
        final Fixture fixture = new Fixture ();
        final ControllerCore core = new PullCoreProvider ().create ();
        core.start (fixture.snapshot (), Optional.empty ());
        fixture.pressed.add (TAP);
        final CoreResult result = core.handle (new ControllerInputEvent (1, 1, TAP, InputKind.BUTTON, InputPhase.BEGIN, 127), fixture.snapshot ());
        assertTrue (result.effects ().contains (new TapTempoEffect ("project-a")));
        assertTrue (result.executionRequirements ().ticksRequested ());
        fixture.tempo = 98.2;
        assertTrue (core.handle (new ControllerTickEvent (2, 2), fixture.snapshot ()).effects ().contains (new ShowHostNotificationEffect ("Tempo: 98.20")));
    }


    private static SetProjectTransportStateEffect metronome (final boolean enabled)
    {
        return new SetProjectTransportStateEffect ("project-a", "project-a", TransportState.METRONOME, enabled);
    }


    private static final class Fixture
    {
        private final CompiledWorkspace workspace = CompiledWorkspace.compile ("tap", List.of (new RetainedControllerView (new TapTempoView ())));
        private final Set<ControlId> pressed = new HashSet<> ();
        private String project = "project-a";
        private boolean engine = true;
        private boolean metronome;
        private double tempo = 120;
        private long sequence;
        private final CoreResult initial;


        private Fixture ()
        {
            this.initial = this.workspace.start (this.snapshot ());
        }


        private CoreResult edge (final InputPhase phase)
        {
            if (phase == InputPhase.BEGIN)
                this.pressed.add (TAP);
            else if (phase == InputPhase.END)
                this.pressed.remove (TAP);
            this.sequence++;
            return this.workspace.handle (new ControllerInputEvent (this.sequence, this.sequence, TAP, InputKind.BUTTON, phase, phase == InputPhase.END ? 0 : 127), this.snapshot ());
        }


        private CoreResult tick ()
        {
            this.sequence++;
            return this.workspace.handle (new ControllerTickEvent (this.sequence, this.sequence), this.snapshot ());
        }


        private ControllerSnapshot snapshot ()
        {
            final TransportSnapshot transport = new TransportSnapshot (true, this.engine, false, false, false, false, false, this.metronome, false, this.tempo, 0, 4, 4);
            final ControllerBridgeSnapshot bridge = new ControllerBridgeSnapshot (transport, SelectedTrackSnapshot.empty (), ControllerLayoutSnapshot.empty (), DrumContextSnapshot.empty (), ParameterBridgeSnapshot.empty (), MasterSnapshot.empty (), new ProjectSnapshot (true, this.project, "Project", this.engine, false, false, false));
            return new ControllerSnapshot (this.sequence, this.sequence, new PullCoreProvider ().descriptor ().requiredCapabilities (), bridge, ClipCatalogSnapshot.empty (), Map.of (), Map.of (), Optional.empty (), this.pressed, Set.of ());
        }
    }
}
