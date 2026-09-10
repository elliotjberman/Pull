// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.bitwig.framework.daw;

import com.bitwig.extension.callback.BooleanValueChangedCallback;
import com.bitwig.extension.callback.IntegerValueChangedCallback;
import com.bitwig.extension.callback.NoteStepChangedCallback;
import com.bitwig.extension.callback.StringValueChangedCallback;
import com.bitwig.extension.controller.api.*;
import de.mossgrabers.framework.controller.valuechanger.IValueChanger;
import de.mossgrabers.framework.daw.IHost;
import de.mossgrabers.framework.daw.clip.NotePosition;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static de.mossgrabers.pull.shell.testing.TestProxies.defaultValue;
import static de.mossgrabers.pull.shell.testing.TestProxies.proxy;
import static org.junit.jupiter.api.Assertions.*;

class CursorClipEditTargetTest
{
    @Test
    void aRetiredTimerCannotJoinANewGestureOrUseTheCallersMutatedPosition ()
    {
        final Fixture f = new Fixture ();
        final NotePosition first = position (60);
        f.clip.startEdit (List.of (first));
        final Runnable oldTimer = f.takeTimer ();
        first.setNote (65);
        f.clip.updateStepVelocity (position (60), .8);
        f.clip.stopEdit ();
        assertEquals (new Write ("track-a", 0, 60, .8), f.writes.get (f.writes.size () - 1));
        f.clip.startEdit (List.of (position (61)));
        f.clip.updateStepVelocity (position (61), .9);
        f.writes.clear ();
        final int timers = f.scheduled.size ();
        oldTimer.run ();
        assertTrue (f.writes.isEmpty ());
        assertEquals (timers, f.scheduled.size (), "a retired callback cannot create another periodic loop");
        f.takeTimer ().run ();
        assertEquals (List.of (new Write ("track-a", 0, 61, .9)), f.writes);
        assertEquals (.2, f.clip.getObservedStep (position (61)).getVelocity (), "writes are not host acknowledgement");
    }

    @Test
    void anObservedTargetExcursionPermanentlyCancelsTheHeldGestureUntilAFreshBegin ()
    {
        final Fixture f = new Fixture ();
        f.clip.startEdit (List.of (position (60)));
        final Runnable timer = f.takeTimer ();
        f.clip.updateStepVelocity (position (60), .8);
        // Deliver the observed excursion after the getter has already returned to A.
        f.trackObservers.forEach (observer -> observer.valueChanged ("track-b"));
        f.trackObservers.forEach (observer -> observer.valueChanged ("track-a"));
        f.writes.clear ();
        f.clip.updateStepVelocity (position (60), .9);
        timer.run ();
        f.clip.stopEdit ();
        assertTrue (f.writes.isEmpty (), "returning to A does not resurrect its cancelled physical gesture");
        f.clip.startEdit (List.of (position (60)));
        f.clip.updateStepVelocity (position (60), .7);
        f.writes.clear ();
        f.takeTimer ().run ();
        assertEquals (List.of (new Write ("track-a", 0, 60, .7)), f.writes);
    }

    @Test
    void targetCancellationPreservesNewClipObservationsRegardlessOfIdentityDeliveryOrder ()
    {
        for (final boolean identityFirst: new boolean [] { true, false })
        {
            final Fixture f = new Fixture ();
            f.clip.startEdit (List.of (position (60)));
            f.clip.updateStepVelocity (position (60), .9);
            final Runnable timer = f.takeTimer ();
            if (identityFirst)
                f.trackId = "track-b";
            // B's host payload can precede either its identity getter or its identity callback.
            f.observe (60, NoteStep.State.NoteOn, .7);
            f.observe (61, NoteStep.State.NoteOn, .45);
            f.trackId = "track-b";
            f.trackObservers.forEach (observer -> observer.valueChanged ("track-b"));
            f.writes.clear ();
            f.clip.updateStepVelocity (position (60), .95);
            timer.run ();
            f.clip.stopEdit ();
            assertTrue (f.writes.isEmpty ());
            assertEquals (.7, f.clip.getStep (position (60)).getVelocity (), "cancellation cannot restore A over B's observed edited cell");
            assertEquals (.7, f.clip.getObservedStep (position (60)).getVelocity ());
            assertEquals (.45, f.clip.getObservedStep (position (61)).getVelocity (), "unrelated B observations survive as well");
        }
    }

    @Test
    void scenePageResolutionProjectAndClipDeletionFencePeriodicAndReleaseWrites ()
    {
        for (int change = 0; change < 6; change++)
        {
            final Fixture f = new Fixture ();
            f.clip.startEdit (List.of (position (60)));
            f.clip.updateStepVelocity (position (60), .8);
            final Runnable timer = f.takeTimer ();
            switch (change)
            {
                case 0 -> { f.selectScene (1); f.selectScene (0); }
                case 1 -> { f.clip.scrollToPage (1); f.clip.scrollToPage (0); }
                case 2 -> { f.clip.setStepLength (.5); f.clip.setStepLength (.25); }
                case 3 -> f.project = "project-b";
                case 4 -> f.trackId = "track-b";
                default -> { f.setExists (false); f.setExists (true); }
            }
            f.writes.clear ();
            f.clip.updateStepVelocity (position (60), .9);
            timer.run ();
            f.clip.stopEdit ();
            assertTrue (f.writes.isEmpty (), "target change " + change + " must cancel writes, including release");
        }
    }

    @Test
    void deletingOneEditedNoteRetiresItsCellWithoutCancellingItsSiblingAndCloseKillsTimers ()
    {
        final Fixture f = new Fixture ();
        f.clip.startEdit (List.of (position (60), position (61)));
        final Runnable deletedTimer = f.takeTimer ();
        final Runnable siblingTimer = f.takeTimer ();
        f.observe (60, NoteStep.State.Empty, 0);
        f.observe (60, NoteStep.State.NoteOn, .4);
        f.clip.updateStepVelocity (position (60), .9);
        f.clip.updateStepVelocity (position (61), .8);
        f.writes.clear ();
        deletedTimer.run ();
        siblingTimer.run ();
        assertEquals (List.of (new Write ("track-a", 0, 61, .8)), f.writes);
        assertEquals (.4, f.clip.getObservedStep (position (60)).getVelocity (), "replacement retains its own observation");
        final Runnable afterClose = f.takeTimer ();
        f.writes.clear ();
        f.clip.close ();
        afterClose.run ();
        assertTrue (f.writes.isEmpty (), "shutdown cannot flush the held gesture or revive a scheduled callback");
    }

    private static NotePosition position (final int note) { return new NotePosition (0, 0, note); }

    private record Write (String track, int scene, int note, double velocity) { }

    private static final class Fixture
    {
        private String trackId = "track-a";
        private String project = "project-a";
        private int scene;
        private boolean exists = true;
        private final List<Runnable> scheduled = new ArrayList<> ();
        private final List<Write> writes = new ArrayList<> ();
        private final List<StringValueChangedCallback> trackObservers = new ArrayList<> ();
        private final List<IntegerValueChangedCallback> sceneObservers = new ArrayList<> ();
        private final List<BooleanValueChangedCallback> existsObservers = new ArrayList<> ();
        private final AtomicReference<NoteStepChangedCallback> noteObserver = new AtomicReference<> ();
        private final Map<Integer, Double> velocities = new HashMap<> ();
        private final Map<Integer, NoteStep.State> states = new HashMap<> ();
        private final CursorClipImpl clip;

        private Fixture ()
        {
            final StringValue trackIdentity = proxy (StringValue.class, (p, method, args) -> switch (method.getName ()) {
                case "get" -> this.trackId;
                case "addValueObserver" -> { this.trackObservers.add ((StringValueChangedCallback) args[0]); yield null; }
                default -> empty (method.getReturnType ());
            });
            final IntegerValue sceneIndex = proxy (IntegerValue.class, (p, method, args) -> switch (method.getName ()) {
                case "get" -> this.scene;
                case "addValueObserver" -> { this.sceneObservers.add ((IntegerValueChangedCallback) args[0]); yield null; }
                default -> empty (method.getReturnType ());
            });
            final BooleanValue clipExists = proxy (BooleanValue.class, (p, method, args) -> switch (method.getName ()) {
                case "get" -> this.exists;
                case "addValueObserver" -> { this.existsObservers.add ((BooleanValueChangedCallback) args[0]); yield null; }
                default -> empty (method.getReturnType ());
            });
            final ClipLauncherSlot slot = proxy (ClipLauncherSlot.class, (p, method, args) -> "sceneIndex".equals (method.getName ()) ? sceneIndex : empty (method.getReturnType ()));
            final Track sourceTrack = proxy (Track.class, (p, method, args) -> "channelId".equals (method.getName ()) ? trackIdentity : empty (method.getReturnType ()));
            final PinnableCursorClip source = proxy (PinnableCursorClip.class, (p, method, args) -> switch (method.getName ()) {
                case "addNoteStepObserver" -> { this.noteObserver.set ((NoteStepChangedCallback) args[0]); yield null; }
                case "getStep" -> note ((Integer) args[2]);
                case "getTrack" -> sourceTrack;
                case "clipLauncherSlot" -> slot;
                case "exists" -> clipExists;
                default -> empty (method.getReturnType ());
            });
            final CursorTrack track = proxy (CursorTrack.class, (p, method, args) -> "createLauncherCursorClip".equals (method.getName ()) ? source : empty (method.getReturnType ()));
            final IHost host = proxy (IHost.class, (p, method, args) -> {
                if ("scheduleTask".equals (method.getName ())) this.scheduled.add ((Runnable) args[0]);
                return empty (method.getReturnType ());
            });
            this.clip = new CursorClipImpl (host, (ControllerHost) empty (ControllerHost.class), track,
                (IValueChanger) empty (IValueChanger.class), 8, 128, () -> this.project);
            observe (60, NoteStep.State.NoteOn, .2);
            observe (61, NoteStep.State.NoteOn, .2);
        }

        private NoteStep note (final int pitch)
        {
            return proxy (NoteStep.class, (p, method, args) -> switch (method.getName ()) {
                case "x", "channel" -> 0;
                case "y" -> pitch;
                case "state" -> this.states.getOrDefault (pitch, NoteStep.State.NoteOn);
                case "velocity" -> this.velocities.getOrDefault (pitch, .2);
                case "occurrence" -> NoteOccurrence.values ()[0];
                case "setVelocity" -> { this.writes.add (new Write (this.trackId, this.scene, pitch, (Double) args[0])); yield null; }
                default -> empty (method.getReturnType ());
            });
        }

        private Runnable takeTimer () { return this.scheduled.remove (0); }
        private void selectScene (final int value) { this.scene = value; this.sceneObservers.forEach (observer -> observer.valueChanged (value)); }
        private void setExists (final boolean value) { this.exists = value; this.existsObservers.forEach (observer -> observer.valueChanged (value)); }
        private void observe (final int pitch, final NoteStep.State state, final double velocity)
        {
            this.states.put (pitch, state);
            this.velocities.put (pitch, velocity);
            this.noteObserver.get ().noteStepChanged (note (pitch));
        }
    }

    private static Object empty (final Class<?> type)
    {
        if (type == String.class) return "";
        return type.isInterface () ? proxy (type, (p, method, args) -> empty (method.getReturnType ())) : defaultValue (type);
    }
}
