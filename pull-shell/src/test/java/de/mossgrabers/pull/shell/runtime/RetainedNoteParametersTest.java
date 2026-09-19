// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.shell.runtime;

import com.bitwig.extension.callback.NoteStepChangedCallback;
import com.bitwig.extension.controller.api.*;
import de.mossgrabers.bitwig.framework.daw.CursorClipImpl;
import de.mossgrabers.framework.controller.valuechanger.IValueChanger;
import de.mossgrabers.framework.daw.IHost;
import de.mossgrabers.framework.daw.clip.NotePosition;
import de.mossgrabers.framework.mode.NoteEditor;
import de.mossgrabers.pull.core.api.*;
import de.mossgrabers.pull.core.api.effect.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.function.Supplier;
import static de.mossgrabers.pull.shell.testing.TestProxies.*;
import static org.junit.jupiter.api.Assertions.*;

class RetainedNoteParametersTest
{
    @Test
    void acquisitionWaitsForClipIdentityAndIndependentNoteReadback ()
    {
        final Fixture f = new Fixture ();
        f.host.request (true);
        f.tick (); // Parent becomes ready and submits clip selection/geometry.
        f.tick ();
        assertTrue (f.host.values ().isEmpty ());
        f.child.applySelection ();
        f.child.notes.put (60, .9); // Right clip identity, stale expression data.
        f.tick ();
        f.tick ();
        assertTrue (f.host.values ().isEmpty ());
        f.child.notes.put (60, .2);
        f.tick ();
        assertTrue (f.host.values ().isEmpty ());
        f.tick ();
        assertEquals (.2, f.host.values ().get (NoteParameterRole.VELOCITY.slot (0)).read ().getAsDouble ());
        assertEquals (.7, f.host.values ().get (NoteParameterRole.VELOCITY.slot (1)).read ().getAsDouble ());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource (value = NoteParameterRole.class, names = {"VELOCITY", "GAIN"})
    void exactNoteWritesAndRestorationUseLaterReadbackAndSeparateBaselines (final NoteParameterRole role)
    {
        final Fixture f = new Fixture ();
        f.acquire ();
        final var changer = new de.mossgrabers.framework.controller.valuechanger.TwosComplementValueChanger (1024, 10);
        final var model = proxy (de.mossgrabers.framework.daw.IModel.class, (p, method, args) -> switch (method.getName ()) {
            case "getValueChanger" -> changer;
            default -> empty (method.getReturnType ());
        });
        final var parameters = new ParameterTargetHost (ParameterTargetHostTest.emptySurface (changer), model, new RuntimeLog () {
            public void info (final String text) { }
            public void warn (final String text) { }
        });
        parameters.installNoteParameters (f.host);
        final var banks = new DesiredParameterBanks (Set.of (ParameterBankId.NOTE));
        parameters.refresh (banks);
        final var first = parameters.snapshot ().slots ().get (role.slot (0));
        final var second = parameters.snapshot ().slots ().get (role.slot (1));
        parameters.prepareLeases (DesiredParameterInteraction.empty (), DesiredParameterBanks.empty ());
        assertEquals (first.target (), parameters.snapshot ().slots ().get (role.slot (0)).target (), "a candidate without notes cannot replace committed identities");
        final var baselines = Map.of (first.target (), first.value (), second.target (), second.value ());
        final var leases = parameters.prepareLeases (new DesiredParameterInteraction (1, true, baselines, Set.of (), Set.of (), 0), banks);
        parameters.applyLeases (leases, banks);
        parameters.apply (parameters.prepare (new SetCurrentParameterValueEffect (first.target (), .4)));
        parameters.apply (parameters.prepare (new SetCurrentParameterValueEffect (second.target (), .9)));
        parameters.refresh (banks);
        assertEquals (first.value (), parameters.snapshot ().targetOrNull (first.target ()).value ());
        f.child.applyWrites ();
        parameters.refresh (banks);
        assertEquals (.4, parameters.snapshot ().targetOrNull (first.target ()).value ());
        assertEquals (.9, parameters.snapshot ().targetOrNull (second.target ()).value ());
        baselines.forEach ((target, value) -> parameters.apply (parameters.prepare (new SetParameterValueEffect (target, value), leases)));
        assertEquals (.4, parameters.snapshot ().targetOrNull (first.target ()).value ());
        f.child.applyWrites ();
        parameters.refresh (banks);
        assertEquals (first.value (), parameters.snapshot ().targetOrNull (first.target ()).value ());
        assertEquals (second.value (), parameters.snapshot ().targetOrNull (second.target ()).value ());

        final var prepared = parameters.prepare (new SetCurrentParameterValueEffect (first.target (), .5));
        f.editor.clearNotes ();
        f.editor.addNote (f.source, new NotePosition (0, 0, 60));
        assertThrows (IllegalStateException.class, () -> parameters.apply (prepared));
        assertTrue (f.child.writes.isEmpty () && f.child.gainWrites.isEmpty ());
    }

    @Test
    void gridChangesAndDeleteUndoCannotReviveCapturedCells ()
    {
        for (int mutation = 0; mutation < 5; mutation++)
        {
            final Fixture f = new Fixture ();
            f.acquire ();
            final var old = f.host.values ().get (NoteParameterRole.VELOCITY.slot (0));
            switch (mutation)
            {
                case 0 -> { f.source.scrollToPage (1); f.source.scrollToPage (0); }
                case 1 -> { f.source.setStepLength (.5); f.source.setStepLength (.25); }
                case 2 -> { f.child.deleted = true; f.child.publish (60); f.child.deleted = false; f.child.publish (60); }
                case 3 -> { f.sourceNative.deleted = true; f.sourceNative.publish (60); f.sourceNative.deleted = false; f.sourceNative.publish (60); }
                default -> f.project = "other-project";
            }
            assertFalse (old.current ().getAsBoolean ());
            assertTrue (f.host.values ().isEmpty ());
        }
    }

    private static final class Fixture implements RetainedCursorPool.Host
    {
        private final NativeClip sourceNative = new NativeClip (true);
        private final NativeClip child = new NativeClip (false);
        private final NoteEditor editor = new NoteEditor ();
        private final CursorClipImpl source;
        private final RetainedCursorPool pool;
        private final RetainedNoteParameters host;
        private RetainedCursorPool.Handle assigned;
        private long sample;
        private String project = "project";

        Fixture ()
        {
            this.source = new CursorClipImpl ((IHost) empty (IHost.class), (ControllerHost) empty (ControllerHost.class),
                proxy (CursorTrack.class, (p, method, args) -> method.getName ().equals ("createLauncherCursorClip") ? this.sourceNative.proxy : empty (method.getReturnType ())),
                (IValueChanger) empty (IValueChanger.class), 8, 128, () -> this.project);
            this.sourceNative.publish (60);
            this.sourceNative.publish (61);
            this.editor.addNote (this.source, new NotePosition (0, 0, 60));
            this.editor.addNote (this.source, new NotePosition (0, 0, 61));
            this.pool = new RetainedCursorPool (List.of (RetainedCursorPool.Profile.NOTE_EDITOR), this);
            final CursorTrack track = proxy (CursorTrack.class, (p, method, args) -> method.getName ().equals ("createLauncherCursorClip") ? this.child.proxy : empty (method.getReturnType ()));
            this.host = new RetainedNoteParameters (this.pool, Map.of (0, track), () -> this.editor, () -> this.project);
            this.host.registerSource (this.source);
        }
        void acquire () { this.host.request (true); this.tick (); this.child.applySelection (); this.tick (); this.tick (); assertFalse (this.host.values ().isEmpty ()); }
        void tick () { this.sample++; this.pool.refresh (); this.host.tick (); }
        @Override public RetainedCursorPool.Catalog catalog () { return new RetainedCursorPool.Catalog (1, RetainedCursorPool.Coverage.FULL, 1, 0, 64, List.of ("track")); }
        @Override public boolean assign (final RetainedCursorPool.Handle handle) { this.assigned = handle; return true; }
        @Override public RetainedCursorPool.Observation observe (final RetainedCursorPool.Handle handle) { return new RetainedCursorPool.Observation (this.sample, this.assigned != null, "track", true, this.assigned == null ? 0 : this.assigned.assignmentGeneration ()); }
        @Override public void release (final RetainedCursorPool.Handle handle) { }
    }

    private static final class NativeClip
    {
        private final PinnableCursorClip proxy;
        private final List<NoteStepChangedCallback> observers = new ArrayList<> ();
        private final Map<Integer, Double> notes = new HashMap<> (Map.of (60, .2, 61, .7));
        private final Map<Integer, Double> gains = new HashMap<> (Map.of (60, .4, 61, .8));
        private final Map<Integer, Double> gainWrites = new LinkedHashMap<> ();
        private final Map<Integer, Double> writes = new LinkedHashMap<> ();
        private boolean selected;
        private boolean requested;
        private boolean deleted;

        NativeClip (final boolean source)
        {
            this.selected = source;
            final Track track = proxy (Track.class, (p, method, args) -> method.getName ().equals ("channelId") ? value (StringValue.class, () -> "track") : empty (method.getReturnType ()));
            final ClipLauncherSlot slot = proxy (ClipLauncherSlot.class, (p, method, args) -> method.getName ().equals ("sceneIndex") ? value (IntegerValue.class, () -> 0) : empty (method.getReturnType ()));
            this.proxy = proxy (PinnableCursorClip.class, (p, method, args) -> {
                assertFalse (method.isAnnotationPresent (Deprecated.class));
                return switch (method.getName ()) {
                    case "exists" -> value (BooleanValue.class, () -> this.selected);
                    case "isPinned" -> value (SettableBooleanValue.class, () -> this.selected);
                    case "createEqualsValue" -> value (BooleanValue.class, () -> this.selected);
                    case "selectClip" -> { this.requested = true; yield null; }
                    case "getTrack" -> track;
                    case "clipLauncherSlot" -> slot;
                    case "getStep" -> this.note ((Integer) args[2]);
                    case "addNoteStepObserver" -> { this.observers.add ((NoteStepChangedCallback) args[0]); yield null; }
                    default -> empty (method.getReturnType ());
                };
            });
        }
        void applySelection () { assertTrue (this.requested); this.selected = true; }
        void publish (final int key) { this.observers.forEach (observer -> observer.noteStepChanged (this.note (key))); }
        void applyWrites ()
        {
            this.notes.putAll (this.writes);
            this.gains.putAll (this.gainWrites);
            this.writes.keySet ().forEach (this::publish);
            this.gainWrites.keySet ().forEach (this::publish);
            this.writes.clear ();
            this.gainWrites.clear ();
        }
        NoteStep note (final int key)
        {
            return proxy (NoteStep.class, (p, method, args) -> {
                assertFalse (method.isAnnotationPresent (Deprecated.class));
                return switch (method.getName ()) {
                    case "x", "channel" -> 0;
                    case "y" -> key;
                    case "state" -> this.deleted ? NoteStep.State.Empty : NoteStep.State.NoteOn;
                    case "velocity" -> this.notes.getOrDefault (key, .2);
                    // Measured API 25 runtime contract: native reads are twice the setter scale.
                    case "gain" -> 2 * this.gains.getOrDefault (key, .5);
                    case "setGain" -> { this.gainWrites.put (key, (Double) args[0]); yield null; }
                    case "occurrence" -> NoteOccurrence.ALWAYS;
                    case "recurrenceLength" -> 1;
                    case "setVelocity" -> { this.writes.put (key, (Double) args[0]); yield null; }
                    default -> empty (method.getReturnType ());
                };
            });
        }
    }
    private static <T> T value (final Class<T> type, final Supplier<Object> read)
    {
        return proxy (type, (p, method, args) -> method.getName ().equals ("get") ? read.get () : empty (method.getReturnType ()));
    }
}
