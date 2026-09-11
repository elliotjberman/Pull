// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.shell.runtime;

import de.mossgrabers.framework.controller.valuechanger.TwosComplementValueChanger;
import de.mossgrabers.framework.daw.IModel;
import de.mossgrabers.framework.daw.IProject;
import de.mossgrabers.framework.daw.ITransport;
import de.mossgrabers.framework.daw.data.ITrack;
import de.mossgrabers.framework.daw.data.bank.ITrackBank;
import de.mossgrabers.framework.daw.midi.ISelectedTrackNoteTarget;
import de.mossgrabers.framework.parameter.IParameter;
import de.mossgrabers.pull.core.api.DesiredParameterBanks;
import de.mossgrabers.pull.core.api.DesiredParameterInteraction;
import de.mossgrabers.pull.core.api.DesiredParameterTouches;
import de.mossgrabers.pull.core.api.ParameterBankId;
import de.mossgrabers.pull.core.api.ParameterSlot;
import de.mossgrabers.pull.core.api.PushControlIds;
import de.mossgrabers.pull.core.api.effect.AdjustParameterValueEffect;
import de.mossgrabers.pull.core.api.effect.SetParameterValueEffect;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static de.mossgrabers.pull.shell.testing.TestProxies.proxy;
import static de.mossgrabers.pull.shell.testing.TestProxies.relaxedProxy;
import static de.mossgrabers.pull.shell.testing.TestProxies.relaxedValue;
import static org.junit.jupiter.api.Assertions.*;

/** Parameter behavior with independent retained actuators and separately advanced host read-back. */
class RetainedTrackParameterTest
{
    private static final DesiredParameterBanks SELECTED = new DesiredParameterBanks (Set.of (ParameterBankId.SELECTED_TRACK));
    private static final DesiredParameterBanks VOLUMES = new DesiredParameterBanks (Set.of (ParameterBankId.TRACK_VOLUME));

    @Test
    void selectedMixWaitsForAcquisitionAndSurvivesVisibleBankNavigation ()
    {
        final Fixture fixture = new Fixture ();
        fixture.host.refresh (SELECTED);
        assertEquals (Set.of ("a"), fixture.requested);
        assertTrue (fixture.host.snapshot ().slots ().isEmpty (), "submitted acquisition cannot publish an old parameter sample");
        fixture.ready ("a");
        fixture.host.refresh (SELECTED);
        final var target = fixture.host.snapshot ().slots ().get (ParameterSlot.SELECTED_TRACK_VOLUME).target ();
        assertEquals (32, fixture.host.snapshot ().targetOrNull (target).value ());

        fixture.visible = "b";
        fixture.host.refresh (SELECTED);
        assertEquals (target, fixture.host.snapshot ().slots ().get (ParameterSlot.SELECTED_TRACK_VOLUME).target ());
        final int requests = fixture.requestCount;
        fixture.host.apply (fixture.host.prepare (new AdjustParameterValueEffect (target, 5)));
        assertEquals (requests, fixture.requestCount, "prepare/apply must not reselect a cursor");
        assertEquals (37, fixture.a.submitted);
        assertEquals (0, fixture.b.writes);
        fixture.host.refresh (SELECTED);
        assertEquals (32, fixture.host.snapshot ().targetOrNull (target).value (), "command submission is not host acknowledgement");
        fixture.a.advance ();
        fixture.host.refresh (SELECTED);
        assertEquals (37, fixture.host.snapshot ().targetOrNull (target).value ());
    }

    @Test
    void touchAndIndicationCleanupKeepOldCursorUntilExactRelease ()
    {
        final Fixture fixture = new Fixture ();
        fixture.ready ("a");
        fixture.host.refresh (VOLUMES);
        final var target = fixture.host.snapshot ().slots ().get (ParameterSlot.trackVolume (0)).target ();
        fixture.host.applyIndications (Set.of (ParameterSlot.trackVolume (0)));
        fixture.host.acquireTouches (fixture.host.prepareTouches (new DesiredParameterTouches (Map.of (PushControlIds.continuous ("KNOB1"), target)), VOLUMES));
        assertEquals (List.of ("indication:true", "touch:true"), fixture.a.events);

        fixture.visible = "b";
        fixture.host.refresh (VOLUMES);
        assertEquals (Set.of ("a", "b"), fixture.requested, "cleanup demand must retain the outgoing cursor before reassigning the visible slot");
        assertEquals (List.of ("indication:true", "touch:true", "touch:false", "indication:false"), fixture.a.events);
        assertTrue (fixture.b.events.isEmpty ());
        assertTrue (fixture.host.snapshot ().touchLeases ().isEmpty ());
        fixture.host.refresh (VOLUMES);
        assertEquals (Set.of ("b"), fixture.requested, "retired cleanup must not reserve the pool forever");
    }

    @Test
    void reboundOrReacquiredAssignmentRejectsPreparedWritesAndCannotReceiveCleanup ()
    {
        final Fixture fixture = new Fixture ();
        fixture.ready ("a");
        fixture.host.refresh (SELECTED);
        final var target = fixture.host.snapshot ().slots ().get (ParameterSlot.SELECTED_TRACK_VOLUME).target ();
        final var prepared = fixture.host.prepare (new AdjustParameterValueEffect (target, 5));
        fixture.host.acquireTouches (fixture.host.prepareTouches (new DesiredParameterTouches (Map.of (PushControlIds.continuous ("KNOB1"), target)), SELECTED));

        fixture.invalidate ("a");
        assertThrows (IllegalStateException.class, () -> fixture.host.apply (prepared));
        fixture.host.refresh (SELECTED);
        assertTrue (fixture.host.snapshot ().slots ().isEmpty ());
        assertEquals (List.of ("touch:true"), fixture.a.events, "unknown replacement targets cannot receive the prior owner's release");
        assertEquals (0, fixture.a.writes);
        fixture.ready ("a");
        fixture.host.refresh (SELECTED);
        assertNotEquals (target, fixture.host.snapshot ().slots ().get (ParameterSlot.SELECTED_TRACK_VOLUME).target ());
        assertThrows (IllegalStateException.class, () -> fixture.host.apply (prepared));
    }

    @Test
    void retainedBaselineRestoresOriginalTrackAfterBankNavigationAndWaitsForReadBack ()
    {
        final Fixture fixture = new Fixture ();
        fixture.ready ("a");
        fixture.host.refresh (SELECTED);
        final var target = fixture.host.snapshot ().slots ().get (ParameterSlot.SELECTED_TRACK_VOLUME).target ();
        final var interaction = new DesiredParameterInteraction (1, false, Map.of (target, 32.0), Set.of (), Set.of (), 0);
        final var leases = fixture.host.prepareLeases (interaction, SELECTED);
        fixture.host.applyLeases (leases, SELECTED);
        fixture.host.apply (fixture.host.prepare (new AdjustParameterValueEffect (target, 9)));
        fixture.a.advance ();
        fixture.visible = "b";
        fixture.host.refresh (SELECTED);
        fixture.host.apply (fixture.host.prepare (new SetParameterValueEffect (target, 32), leases));
        fixture.host.refresh (SELECTED);
        assertEquals (41, fixture.host.snapshot ().targetOrNull (target).value ());
        assertEquals (0, fixture.b.writes);
        fixture.a.advance ();
        fixture.host.refresh (SELECTED);
        assertEquals (32, fixture.host.snapshot ().targetOrNull (target).value ());
        fixture.host.invalidate ();
        assertEquals (Set.of (), fixture.requested);
    }

    @Test
    void selectionChangeRejectsFurtherEditsWithoutAuthorizingOffscreenEditing ()
    {
        final Fixture fixture = new Fixture ();
        fixture.ready ("a");
        fixture.host.refresh (SELECTED);
        final var target = fixture.host.snapshot ().slots ().get (ParameterSlot.SELECTED_TRACK_VOLUME).target ();
        final var prepared = fixture.host.prepare (new AdjustParameterValueEffect (target, 5));
        fixture.selected = "b";
        fixture.selectedGeneration++;
        assertThrows (IllegalStateException.class, () -> fixture.host.apply (prepared));
        fixture.host.refresh (SELECTED);
        assertTrue (fixture.host.snapshot ().slots ().isEmpty ());
        assertEquals (Set.of ("b"), fixture.requested);
        assertEquals (0, fixture.a.writes);
    }

    private static final class Fixture implements RetainedTrackParameters
    {
        private final DeferredParameter a = new DeferredParameter (32);
        private final DeferredParameter b = new DeferredParameter (80);
        private final Map<String, TrackMix> acquired = new HashMap<> ();
        private final Map<String, Long> generations = new HashMap<> ();
        private Set<String> requested = Set.of ();
        private int requestCount;
        private String selected = "a";
        private String visible = "a";
        private long selectedGeneration = 1;
        private final ParameterTargetHost host;

        private Fixture ()
        {
            final var changer = new TwosComplementValueChanger (128, 1);
            final ISelectedTrackNoteTarget selection = proxy (ISelectedTrackNoteTarget.class, (object, method, arguments) -> switch (method.getName ())
            {
                case "doesExist" -> Boolean.TRUE;
                case "getChannelID" -> this.selected;
                case "getGeneration" -> Long.valueOf (this.selectedGeneration);
                default -> relaxedValue (method.getReturnType ());
            });
            final ITrack track = proxy (ITrack.class, (object, method, arguments) -> switch (method.getName ())
            {
                case "doesExist" -> Boolean.TRUE;
                case "getChannelID" -> this.visible;
                default -> relaxedValue (method.getReturnType ());
            });
            final ITrackBank bank = proxy (ITrackBank.class, (object, method, arguments) -> switch (method.getName ())
            {
                case "getPageSize" -> Integer.valueOf (1);
                case "getItem" -> track;
                default -> relaxedValue (method.getReturnType ());
            });
            final IProject project = proxy (IProject.class, (object, method, arguments) -> "getIdentity".equals (method.getName ()) ? "project-a" : relaxedValue (method.getReturnType ()));
            final IModel model = proxy (IModel.class, (object, method, arguments) -> switch (method.getName ())
            {
                case "getTransport" -> relaxedProxy (ITransport.class);
                case "getProject" -> project;
                case "getValueChanger" -> changer;
                case "getCurrentTrackBank" -> bank;
                default -> relaxedValue (method.getReturnType ());
            });
            this.host = new ParameterTargetHost (ParameterTargetHostTest.emptySurface (changer), model, selection, new RuntimeLog ()
            {
                @Override public void info (final String message) { }
                @Override public void warn (final String message) { }
            }, this);
        }

        @Override
        public void requestTracks (final Set<String> trackIds)
        {
            this.requested = Set.copyOf (trackIds);
            this.requestCount++;
            this.acquired.keySet ().retainAll (trackIds);
        }
        @Override public TrackMix lookup (final String trackId) { return this.acquired.get (trackId); }

        private void ready (final String trackId)
        {
            final long generation = this.generations.merge (trackId, Long.valueOf (1), (old, increment) -> Long.valueOf (old.longValue () + 1)).longValue ();
            final IParameter parameter = ("a".equals (trackId) ? this.a : this.b).parameter;
            this.acquired.put (trackId, new TrackMix (trackId, generation, parameter, parameter,
                () -> this.acquired.containsKey (trackId) && this.generations.get (trackId).longValue () == generation));
        }

        private void invalidate (final String trackId) { this.acquired.remove (trackId); }
    }

    private static final class DeferredParameter
    {
        private int observed;
        private int submitted;
        private int writes;
        private final List<String> events = new ArrayList<> ();
        private final IParameter parameter;

        private DeferredParameter (final int value)
        {
            this.observed = value;
            this.submitted = value;
            this.parameter = proxy (IParameter.class, (object, method, arguments) -> switch (method.getName ())
            {
                case "doesExist" -> Boolean.TRUE;
                case "getName" -> "Volume";
                case "getValue", "getModulatedValue" -> Integer.valueOf (this.observed);
                case "getDisplayedValue" -> Integer.toString (this.observed);
                case "getNumberOfSteps" -> Integer.valueOf (128);
                case "inc" -> { this.submitted = this.observed + (int) Math.round (((Number) arguments[0]).doubleValue ()); this.writes++; yield null; }
                case "setValueImmediatly" -> { this.submitted = ((Number) arguments[0]).intValue (); this.writes++; yield null; }
                case "touchValue" -> { this.events.add ("touch:" + arguments[0]); yield null; }
                case "setIndication" -> { this.events.add ("indication:" + arguments[0]); yield null; }
                default -> relaxedValue (method.getReturnType ());
            });
        }

        private void advance () { this.observed = this.submitted; }
    }
}
