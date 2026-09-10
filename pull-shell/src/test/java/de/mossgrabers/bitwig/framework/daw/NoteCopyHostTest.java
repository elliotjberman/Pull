// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.bitwig.framework.daw;

import com.bitwig.extension.callback.NoteStepChangedCallback;
import com.bitwig.extension.controller.api.*;
import de.mossgrabers.framework.daw.IHost;
import de.mossgrabers.framework.daw.clip.DefaultStepInfo;
import de.mossgrabers.framework.daw.clip.NotePosition;
import de.mossgrabers.framework.daw.clip.StepState;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static de.mossgrabers.pull.shell.testing.TestProxies.defaultValue;
import static de.mossgrabers.pull.shell.testing.TestProxies.proxy;
import static org.junit.jupiter.api.Assertions.*;

class NoteCopyHostTest
{
    private static final NotePosition DESTINATION = new NotePosition (0, 2, 60);

    @Test
    void delayedCreationCopiesExpressionsToOriginalClipAfterEditorMoves ()
    {
        final FakeHost f = new FakeHost ();
        final DefaultStepInfo source = source ();
        final NotePosition destination = new NotePosition (DESTINATION);
        f.copy.copy (destination, 0, .25, source);
        destination.setStep (5);
        source.setPressure (.99);
        f.awaitCreationSubmission (1);
        f.selectEditor (f.b);

        // Twenty controller polls represent 400 ms: elapsed time alone is never an acknowledgement.
        f.poll (20);
        assertTrue (f.expressions.isEmpty ());
        f.applyHostCommands ();
        f.publishCursorState ();
        f.poll (10);
        assertTrue (f.expressions.isEmpty (), "getStep read-back without a fresh note event is not creation acknowledgement");

        f.publishNotes ();
        f.finish ();
        assertEquals (Map.of ("velocity", .63, "gain", .9, "pan", -.25, "pressure", .2,
            "releaseVelocity", .36, "timbre", .4, "transpose", -7.5), f.a.notes.get (key (DESTINATION)).values);
        assertTrue (f.b.notes.isEmpty (), "the newly selected clip must remain untouched");
        assertTrue (f.expressions.stream ().allMatch (write -> write.clip == f.a));
        assertTrue (f.a.notes.keySet ().stream ().allMatch (position -> position.step == 2), "copy freezes caller-owned coordinates");
    }

    @Test
    void preexistingMatchingDestinationDoesNotAcknowledgeNewCreation ()
    {
        final FakeHost f = new FakeHost ();
        final NativeNote old = new NativeNote ((int) (.63 * 127) / 127.0, .5);
        f.a.notes.put (key (DESTINATION), old);
        f.publishCursorState ();
        f.copy.copy (DESTINATION, 0, .25, source ());
        // Initial cursor population can arrive during acquisition, before the copy's setStep request.
        for (int i = 0; i < 10 && f.creationSubmissions == 0; i++)
        {
            f.applyHostCommands ();
            f.publishCursorState ();
            f.publishNotes ();
            f.poll (1);
        }
        assertEquals (1, f.creationSubmissions);
        f.poll (20);
        assertTrue (f.expressions.isEmpty (), "a pre-request NoteOn must not unlock expression writes");
        f.applyHostCommands ();
        f.publishCursorState ();
        f.poll (10);
        assertTrue (f.expressions.isEmpty (), "an identical overwrite without a new note event remains unacknowledged");
        f.publishNotes ();
        f.finish ();
        assertFalse (f.expressions.isEmpty ());
    }

    @Test
    void noteOrClipDeletionCancelsPendingExpressions ()
    {
        for (final boolean deleteClip: List.of (false, true))
        {
            final FakeHost f = new FakeHost ();
            f.copy.copy (DESTINATION, 0, .25, source ());
            f.awaitCreationSubmission (1);
            f.applyHostCommands ();
            f.publishCursorState ();
            f.publishNotes ();
            if (deleteClip) f.a.exists = false;
            else f.a.notes.remove (key (DESTINATION));
            f.publishCursorState ();
            f.publishNotes ();
            f.poll (1);
            if (!deleteClip)
            {
                // A later edit at the same coordinates is a new note, not our original copy.
                f.a.notes.put (key (DESTINATION), new NativeNote ((int) (.63 * 127) / 127.0, .5));
                f.publishCursorState ();
                f.publishNotes ();
            }
            f.selectEditor (f.b);
            f.finish ();
            assertTrue (f.expressions.isEmpty (), "deletion must cancel rather than write to a replacement target");
            assertTrue (f.b.notes.isEmpty ());
        }
    }

    @Test
    void unacknowledgedWindowsBoundCapacityAndAcknowledgedCopiesReleaseIt ()
    {
        final FakeHost f = new FakeHost ();
        for (int page = 0; page < 4; page++) f.copy.copy (DESTINATION, page, .25, source ());
        f.awaitCreationSubmission (4);
        f.applyHostCommands ();
        f.publishCursorState ();
        f.publishNotes ();
        f.poll (3);
        assertFalse (f.expressions.isEmpty ());
        // Submitted expressions do not free a lane before their later observed values arrive.
        f.copy.copy (DESTINATION, 4, .25, source ());
        f.poll (10);
        assertEquals (4, f.creations.size (), "a fifth pending window must not silently rebind an occupied cursor");
        f.finish ();
        f.copy.copy (DESTINATION, 5, .25, source ());
        f.awaitCreationSubmission (5);
        f.finish ();
        assertTrue (f.a.notes.containsKey (new Position (0, 42, 60)));
        assertFalse (f.a.notes.containsKey (new Position (0, 34, 60)));
    }

    @Test
    void alreadyMatchingExpressionsReleaseCapacityWithoutANoopSetterNotification ()
    {
        final FakeHost f = new FakeHost ();
        final DefaultStepInfo unchanged = new DefaultStepInfo ();
        unchanged.setState (StepState.START);
        unchanged.setDuration (.5);
        unchanged.setVelocity (64 / 127.0);
        // Fake native creation initializes the other expression values to zero, matching this source.
        for (int page = 0; page < 4; page++) f.copy.copy (DESTINATION, page, .25, unchanged);
        f.awaitCreationSubmission (4);
        f.applyHostCommands ();
        f.publishCursorState ();
        f.publishNotes ();
        f.poll (3);
        assertTrue (f.expressions.isEmpty (), "matching authoritative values need no redundant writes");
        // No further note observer is delivered: no-op native setters need not generate a change.
        f.copy.copy (DESTINATION, 4, .25, unchanged);
        f.awaitCreationSubmission (5);
    }

    @Test
    void fullWindowAndSeparateWindowCompleteAfterEditorSelectionChanges ()
    {
        final FakeHost f = new FakeHost ();
        for (int i = 0; i < 128; i++)
            f.copy.copy (new NotePosition (0, i / 16, 48 + i % 16), 0, .25, source ());
        f.copy.copy (new NotePosition (0, 0, 80), 0, .25, source ());
        f.copy.copy (new NotePosition (0, 0, 81), 1, .25, source ());
        f.awaitCreationSubmission (129);
        f.selectEditor (f.b);
        f.finish ();
        assertEquals (129, f.a.notes.size ());
        assertFalse (f.a.notes.containsKey (new Position (0, 0, 80)), "a full window must refuse overflow rather than silently consume another cursor");
        assertTrue (f.a.notes.containsKey (new Position (0, 8, 81)), "a separate window can still copy while the first window is full");
        for (final NativeNote note: f.a.notes.values ())
            assertEquals (Map.of ("velocity", .63, "gain", .9, "pan", -.25, "pressure", .2,
                "releaseVelocity", .36, "timbre", .4, "transpose", -7.5), note.values);
        assertTrue (f.b.notes.isEmpty ());
        assertTrue (f.expressions.stream ().allMatch (write -> write.clip == f.a));
    }

    @Test
    void deletingOnePendingNoteDoesNotCancelItsSurvivingBatchMember ()
    {
        final FakeHost f = new FakeHost ();
        final NotePosition survivor = new NotePosition (0, 2, 61);
        f.copy.copy (DESTINATION, 0, .25, source ());
        f.copy.copy (survivor, 0, .25, source ());
        f.awaitCreationSubmission (2);
        f.applyHostCommands ();
        f.publishCursorState ();
        f.publishNotes ();
        f.a.notes.remove (key (DESTINATION));
        f.publishCursorState ();
        f.publishNotes ();
        f.poll (1);
        final NativeNote replacement = new NativeNote ((int) (.63 * 127) / 127.0, .5);
        replacement.values.put ("pressure", .88);
        f.a.notes.put (key (DESTINATION), replacement);
        f.selectEditor (f.b);
        f.finish ();
        assertEquals (.88, f.a.notes.get (key (DESTINATION)).values.get ("pressure"));
        assertEquals (.2, f.a.notes.get (key (survivor)).values.get ("pressure"));
        assertTrue (f.expressions.stream ().noneMatch (write -> write.position.equals (key (DESTINATION))));
    }

    @Test
    void pageAndResolutionStayCapturedAndEquivalentDestinationCannotBeReservedTwice ()
    {
        final FakeHost f = new FakeHost ();
        f.copy.copy (DESTINATION, 2, .125, source ());
        f.awaitCreationSubmission (1);
        f.editor.clip.scrollToStep (64);
        f.editor.clip.setStepSize (1.0);
        // (page 2 * 8 + step 2) * .125 equals (page 1 * 8 + step 1) * .25.
        f.copy.copy (new NotePosition (0, 1, 60), 1, .25, source ());
        f.finish ();
        assertEquals (1, f.creations.size (), "different grid coordinates can still address the same pending note");
        assertEquals (new Position (0, 18, 60), f.creations.get (0).position);
        assertEquals (List.of (.125), f.creationStepSizes);
        assertEquals (.2, f.a.notes.get (new Position (0, 18, 60)).values.get ("pressure"));
    }

    @Test
    void queuedGeometryIsAppliedBeforeCreationWithoutBecomingNoteAcknowledgement ()
    {
        final FakeHost f = new FakeHost ();
        f.copy.copy (DESTINATION, 2, .125, source ());
        f.applyHostCommands ();
        f.publishCursorState ();
        f.poll (1);
        // Deliver clip selection and pinning while its geometry commands remain queued.
        f.commands.remove (0).run ();
        f.commands.remove (0).run ();
        f.publishCursorState ();
        f.poll (2);
        assertEquals (1, f.creationSubmissions);
        assertTrue (f.creations.isEmpty (), "submission must not resolve coordinates or apply the note");

        f.applyHostCommands ();
        final Position expected = new Position (0, 18, 60);
        assertEquals (expected, f.creations.get (0).position);
        assertEquals (List.of (.125), f.creationStepSizes);
        assertTrue (f.a.notes.containsKey (expected));
        f.poll (1);
        assertTrue (f.expressions.isEmpty (), "applied geometry and note creation are not observed completion");
        f.publishCursorState ();
        f.poll (1);
        assertTrue (f.expressions.isEmpty (), "matching getters still need a fresh note observation");
        f.publishNotes ();
        f.finish ();
        assertEquals (.2, f.a.notes.get (expected).values.get ("pressure"));
    }

    @Test
    void projectSwitchCannotReuseMatchingTrackAndSceneIdentity ()
    {
        final FakeHost f = new FakeHost ();
        f.copy.copy (DESTINATION, 0, .25, source ());
        f.awaitCreationSubmission (1);
        f.applyHostCommands ();
        f.publishCursorState ();
        f.publishNotes ();
        final ClipState clone = new ClipState (f.a.track, f.a.scene);
        clone.notes.put (key (DESTINATION), new NativeNote ((int) (.63 * 127) / 127.0, .5));
        f.projectId = "project-b";
        f.cursors.forEach (cursor -> cursor.target = clone);
        f.publishCursorState ();
        f.publishNotes ();
        f.finish ();
        assertTrue (f.expressions.isEmpty (), "a copied project can expose the same track UUID and scene coordinates");
        assertEquals (Map.of ("velocity", (int) (.63 * 127) / 127.0), clone.notes.get (key (DESTINATION)).values);
    }

    @Test
    void selectionChangeBeforeAcquisitionCannotCopyIntoReplacementClip ()
    {
        final FakeHost f = new FakeHost ();
        f.copy.copy (DESTINATION, 0, .25, source ());
        f.selectEditor (f.b);
        f.finish ();
        assertTrue (f.b.notes.isEmpty ());
        assertTrue (f.expressions.stream ().allMatch (write -> write.clip == f.a));
    }

    @Test
    void closeStopsPendingCallbacksBeforeTheyCanWriteExpressions ()
    {
        final FakeHost f = new FakeHost ();
        f.copy.copy (DESTINATION, 0, .25, source ());
        f.awaitCreationSubmission (1);
        f.copy.close ();
        f.applyHostCommands ();
        f.publishCursorState ();
        f.publishNotes ();
        f.poll (200);
        assertTrue (f.expressions.isEmpty ());
        assertTrue (f.scheduled.isEmpty (), "closed copies must not perpetually reschedule polling");
    }

    private static DefaultStepInfo source ()
    {
        final DefaultStepInfo source = new DefaultStepInfo ();
        source.setState (StepState.START);
        source.setDuration (.5);
        source.setVelocity (.63);
        source.setGain (.45);
        source.setPan (-.25);
        source.setPressure (.2);
        source.setReleaseVelocity (.36);
        source.setTimbre (.4);
        source.setTranspose (-7.5);
        return source;
    }

    private record Position (int channel, int step, int note) { }
    private record Write (ClipState clip, Position position, String field, double value) { }
    private static Position key (final NotePosition p) { return new Position (p.getChannel (), p.getStep (), p.getNote ()); }

    private static final class ClipState
    {
        final String track;
        final int scene;
        boolean exists = true;
        final Map<Position, NativeNote> notes = new LinkedHashMap<> ();
        ClipState (final String track, final int scene) { this.track = track; this.scene = scene; }
    }

    private static final class NativeNote
    {
        final double duration;
        final Map<String, Double> values = new LinkedHashMap<> ();
        NativeNote (final double velocity, final double duration) { this.duration = duration; this.values.put ("velocity", velocity); }
        NativeNote copy () { final NativeNote copy = new NativeNote (0, this.duration); copy.values.clear (); copy.values.putAll (this.values); return copy; }
    }

    /** A Bitwig submission never changes observed state. Tests explicitly advance both boundaries. */
    private static final class FakeHost
    {
        final ClipState a = new ClipState ("track-a", 1);
        final ClipState b = new ClipState ("track-b", 3);
        final List<Runnable> commands = new ArrayList<> ();
        final List<Runnable> scheduled = new ArrayList<> ();
        final List<Write> creations = new ArrayList<> ();
        final List<Double> creationStepSizes = new ArrayList<> ();
        final List<Write> expressions = new ArrayList<> ();
        final List<NativeCursor> cursors = new ArrayList<> ();
        final Map<Object, NativeCursor> nativeCursors = new IdentityHashMap<> ();
        final NativeCursor editor = new NativeCursor ();
        int creationSubmissions;
        String projectId = "project-a";
        final NoteCopyHost copy;

        FakeHost ()
        {
            final IHost host = proxy (IHost.class, (p, method, args) -> {
                if ("scheduleTask".equals (method.getName ())) this.scheduled.add ((Runnable) args[0]);
                return empty (method.getReturnType ());
            });
            final ControllerHost controller = proxy (ControllerHost.class, (p, method, args) -> {
                if ("createCursorTrack".equals (method.getName ())) return new NativeCursor ().track;
                return empty (method.getReturnType ());
            });
            this.copy = new NoteCopyHost (host, controller, this.editor.clip, "note-copy-test", 8, 128, () -> this.projectId);
            this.applyHostCommands ();
            this.publishCursorState ();
        }

        void selectEditor (final ClipState target)
        {
            this.editor.target = target;
            this.publishCursorState ();
        }

        void poll (final int count)
        {
            for (int i = 0; i < count; i++)
            {
                final List<Runnable> due = new ArrayList<> (this.scheduled);
                this.scheduled.clear ();
                due.forEach (Runnable::run);
            }
        }

        void applyHostCommands ()
        {
            final List<Runnable> pending = new ArrayList<> (this.commands);
            this.commands.clear ();
            pending.forEach (Runnable::run);
        }

        void publishCursorState () { this.cursors.forEach (NativeCursor::publish); }
        void publishNotes () { this.cursors.forEach (NativeCursor::notifyNotes); }

        void awaitCreationSubmission (final int count)
        {
            for (int i = 0; i < 30 && this.creationSubmissions < count; i++)
            {
                this.applyHostCommands ();
                this.publishCursorState ();
                this.poll (1);
            }
            assertEquals (count, this.creationSubmissions, "copy did not acquire its target and submit note creation");
        }

        void finish ()
        {
            for (int i = 0; i < 30; i++)
            {
                this.applyHostCommands ();
                this.publishCursorState ();
                this.publishNotes ();
                this.poll (1);
            }
        }

        private final class NativeCursor
        {
            ClipState target = FakeHost.this.a;
            ClipState observedTarget = this.target;
            boolean trackPinned;
            boolean clipPinned;
            boolean observedTrackPinned;
            boolean observedClipPinned;
            boolean observedExists = true;
            int page;
            double stepSize = .25;
            final Map<Position, NativeNote> observedNotes = new HashMap<> ();
            final List<NoteStepChangedCallback> observers = new ArrayList<> ();
            final List<Position> requestedNotes = new ArrayList<> ();
            final CursorTrack track;
            PinnableCursorClip clip;

            NativeCursor ()
            {
                this.track = proxy (CursorTrack.class, (p, method, args) -> switch (method.getName ()) {
                    case "createLauncherCursorClip" -> this.clip;
                    case "channelId" -> value (StringValue.class, () -> this.observedTarget.track, null);
                    case "isPinned" -> value (SettableBooleanValue.class, () -> this.observedTrackPinned, v -> FakeHost.this.commands.add (() -> this.trackPinned = (Boolean) v));
                    case "exists", "canHoldNoteData" -> value (BooleanValue.class, () -> this.observedExists, null);
                    case "selectChannel" -> { final NativeCursor source = FakeHost.this.nativeCursors.get (args[0]); final ClipState captured = source.target; FakeHost.this.commands.add (() -> this.target = captured); yield null; }
                    case "createEqualsValue" -> this.sameAs (args[0]);
                    default -> empty (method.getReturnType ());
                });
                this.clip = proxy (PinnableCursorClip.class, (p, method, args) -> switch (method.getName ()) {
                    case "getTrack" -> this.track;
                    case "exists" -> value (BooleanValue.class, () -> this.observedExists, null);
                    case "isPinned" -> value (SettableBooleanValue.class, () -> this.observedClipPinned, v -> FakeHost.this.commands.add (() -> this.clipPinned = (Boolean) v));
                    case "clipLauncherSlot" -> proxy (ClipLauncherSlot.class, (slot, operation, arguments) -> "sceneIndex".equals (operation.getName ()) ? value (IntegerValue.class, () -> this.observedTarget.scene, null) : empty (operation.getReturnType ()));
                    case "selectClip" -> { final NativeCursor source = FakeHost.this.nativeCursors.get (args[0]); final ClipState captured = source.target; FakeHost.this.commands.add (() -> this.target = captured); yield null; }
                    case "createEqualsValue" -> this.sameAs (args[0]);
                    case "addNoteStepObserver" -> { this.observers.add ((NoteStepChangedCallback) args[0]); yield null; }
                    case "scrollToStep" -> { final int requested = (Integer) args[0]; FakeHost.this.commands.add (() -> this.page = requested); yield null; }
                    case "setStepSize" -> { final double requested = (Double) args[0]; FakeHost.this.commands.add (() -> this.stepSize = requested); yield null; }
                    case "getStep" -> { final Position position = this.position (args); if (!this.requestedNotes.contains (position)) this.requestedNotes.add (position); yield this.note (position); }
                    case "setStep" -> { this.create (args); yield null; }
                    default -> empty (method.getReturnType ());
                });
                FakeHost.this.cursors.add (this);
                FakeHost.this.nativeCursors.put (this.clip, this);
                FakeHost.this.nativeCursors.put (this.track, this);
            }

            Object sameAs (final Object other)
            {
                return value (BooleanValue.class, () -> this.observedTarget == FakeHost.this.nativeCursors.get (other).observedTarget, null);
            }

            Position position (final Object [] args) { return new Position ((Integer) args[0], this.page + (Integer) args[1], (Integer) args[2]); }

            void create (final Object [] args)
            {
                final double velocity = ((Integer) args[3]).doubleValue () / 127;
                final double duration = (Double) args[4];
                FakeHost.this.creationSubmissions++;
                // Bitwig resolves the target and grid on its document thread, after earlier commands.
                FakeHost.this.commands.add (() -> {
                    final Position position = this.position (args);
                    FakeHost.this.creations.add (new Write (this.target, position, "create", velocity));
                    FakeHost.this.creationStepSizes.add (this.stepSize);
                    if (!this.requestedNotes.contains (position)) this.requestedNotes.add (position);
                    if (this.target.exists) this.target.notes.put (position, new NativeNote (velocity, duration));
                });
            }

            NoteStep note (final Position position)
            {
                return proxy (NoteStep.class, (p, method, args) -> {
                    final NativeNote observed = this.observedNotes.get (position);
                    return switch (method.getName ()) {
                        case "channel" -> position.channel;
                        case "x" -> position.step - this.page;
                        case "y" -> position.note;
                        case "state" -> observed == null ? NoteStep.State.Empty : NoteStep.State.NoteOn;
                        case "duration" -> observed == null ? 0.0 : observed.duration;
                        case "occurrence" -> NoteOccurrence.values ()[0];
                        case "velocity", "gain", "pan", "pressure", "releaseVelocity", "timbre", "transpose" -> observed == null ? 0.0 : observed.values.getOrDefault (method.getName (), 0.0);
                        default -> {
                            if (method.getName ().startsWith ("set") && args != null && args.length == 1 && args[0] instanceof Double)
                            {
                                final String field = Character.toLowerCase (method.getName ().charAt (3)) + method.getName ().substring (4);
                                final ClipState captured = this.target;
                                final double amount = (Double) args[0];
                                FakeHost.this.expressions.add (new Write (captured, position, field, amount));
                                // Live Bitwig calibration: setGain accepts normalized gain; gain() reports twice that value.
                                FakeHost.this.commands.add (() -> { final NativeNote note = captured.notes.get (position); if (captured.exists && note != null) note.values.put (field, "gain".equals (field) ? amount * 2 : amount); });
                            }
                            yield empty (method.getReturnType ());
                        }
                    };
                });
            }

            void publish ()
            {
                this.observedTarget = this.target;
                this.observedTrackPinned = this.trackPinned;
                this.observedClipPinned = this.clipPinned;
                this.observedExists = this.target.exists;
                this.observedNotes.clear ();
                if (this.observedExists) this.target.notes.forEach ((position, note) -> this.observedNotes.put (position, note.copy ()));
            }

            void notifyNotes ()
            {
                final List<Position> positions = new ArrayList<> (this.requestedNotes);
                this.observedNotes.keySet ().stream ().filter (position -> !positions.contains (position)).forEach (positions::add);
                for (final Position position: positions)
                    for (final NoteStepChangedCallback observer: this.observers) observer.noteStepChanged (this.note (position));
            }
        }
    }

    private static <T> T value (final Class<T> type, final Supplier<?> observed, final Consumer<Object> request)
    {
        return proxy (type, (p, method, args) -> {
            if ("get".equals (method.getName ())) return observed.get ();
            if ("set".equals (method.getName ()) && request != null) request.accept (args[0]);
            return empty (method.getReturnType ());
        });
    }

    private static Object empty (final Class<?> type)
    {
        if (type == String.class) return "";
        return type.isInterface () ? proxy (type, (p, method, args) -> empty (method.getReturnType ())) : defaultValue (type);
    }
}
