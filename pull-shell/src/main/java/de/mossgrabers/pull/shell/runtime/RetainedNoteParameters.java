// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.shell.runtime;

import com.bitwig.extension.controller.api.*;
import de.mossgrabers.bitwig.framework.daw.CursorClipImpl;
import de.mossgrabers.bitwig.framework.daw.StepInfoImpl;
import de.mossgrabers.framework.daw.clip.NotePosition;
import de.mossgrabers.framework.daw.clip.StepState;
import de.mossgrabers.framework.mode.INoteEditor;
import de.mossgrabers.pull.core.api.*;
import java.util.*;
import java.util.function.*;

/** One pool-owned 128-step by 128-key clip window, with at most 128 selected note cells.
 * API 25 has no grid-geometry acknowledgement or stable note ID. Grid commands precede reads;
 * independent source agreement gates acquisition, and observed deletion retires the owner. */
final class RetainedNoteParameters
{
    private static final String NAMESPACE = "note-parameters";
    private final RetainedCursorPool pool;
    private final PinnableCursorClip clip;
    private final int slot;
    private final Supplier<INoteEditor> editor;
    private final Supplier<String> project;
    private final Map<CursorClipImpl, BooleanValue> sources = new IdentityHashMap<> ();
    private Selection selected;
    private RetainedCursorPool.Handle handle;
    private long generation;
    private long revision;
    private long acquiredRevision;
    private boolean submitted;
    private boolean ready;
    private int confirmations;
    private Map<ParameterSlot, Value> values = Map.of ();

    record Cell (int channel, int step, int key)
    {
        NotePosition position () { return new NotePosition (this.channel, this.step, this.key); }
    }
    private record Selection (CursorClipImpl source, String project, String track, int scene, long revision,
                              int firstStep, double stepSize, long selectionRevision, List<Cell> cells) { }
    record Value (String owner, long generation, NoteParameterRole role, DoubleSupplier read,
                  DoubleConsumer write, BooleanSupplier current, double minimum, double maximum) { }

    RetainedNoteParameters (final RetainedCursorPool pool, final Map<Integer, CursorTrack> tracks,
                            final Supplier<INoteEditor> editor, final Supplier<String> project)
    {
        this.pool = pool;
        this.editor = editor;
        this.project = project;
        final var entry = tracks.entrySet ().iterator ().next ();
        this.slot = entry.getKey ();
        this.clip = entry.getValue ().createLauncherCursorClip ("PULL_RETAINED_NOTES", "Pull Retained Notes", 128, 128);
        this.clip.exists ().markInterested ();
        this.clip.isPinned ().markInterested ();
        this.clip.getTrack ().channelId ().markInterested ();
        this.clip.clipLauncherSlot ().sceneIndex ().markInterested ();
        this.clip.exists ().addValueObserver (ignored -> this.revision++);
        this.clip.isPinned ().addValueObserver (ignored -> this.revision++);
        this.clip.getTrack ().channelId ().addValueObserver (ignored -> this.revision++);
        this.clip.clipLauncherSlot ().sceneIndex ().addValueObserver (ignored -> this.revision++);
        this.clip.addNoteStepObserver (step -> {
            if (this.ready && step.state () == NoteStep.State.Empty && this.selected.cells.contains (new Cell (step.channel (), step.x (), step.y ()))) this.revision++;
        });
    }

    /** Called only during extension initialization, after the legacy editor windows exist. */
    void registerSource (final CursorClipImpl source)
    {
        final BooleanValue equal = this.clip.createEqualsValue (source.getClip ());
        equal.markInterested ();
        equal.addValueObserver (ignored -> { if (this.selected != null && this.selected.source == source) this.revision++; });
        source.getClip ().addNoteStepObserver (step -> {
            if (this.selected != null && this.selected.source == source && step.state () == NoteStep.State.Empty && this.selected.cells.contains (new Cell (step.channel (), step.x (), step.y ()))) this.revision++;
        });
        this.sources.put (source, equal);
    }

    void request (final boolean active)
    {
        final Selection next = active ? this.selection () : null;
        if (!Objects.equals (this.selected, next) || this.ready && !this.current ())
        {
            this.selected = next;
            this.generation++;
            this.handle = null;
            this.submitted = false;
            this.ready = false;
            this.confirmations = 0;
            this.values = Map.of ();
        }
        this.pool.reconcile (NAMESPACE, this.selected == null ? List.of () : List.of (
            new RetainedCursorPool.Request (this.owner (), this.selected.track, RetainedCursorPool.Profile.NOTE_EDITOR)));
    }

    /** Acquisition advances only on later host ticks, never while preparing an effect. */
    void tick ()
    {
        if (this.selected == null || this.ready || !this.selected.equals (this.selection ())) return;
        final var acquired = this.pool.lookup (NAMESPACE, this.owner ());
        if (acquired.status () != RetainedCursorPool.Status.READY || acquired.handle ().slot () != this.slot) return;
        this.handle = acquired.handle ();
        if (!this.submitted)
        {
            this.clip.isPinned ().set (false);
            this.clip.selectClip (this.selected.source.getClip ());
            this.clip.isPinned ().set (true);
            this.clip.setStepSize (this.selected.stepSize);
            this.clip.scrollToStep (this.selected.firstStep);
            this.clip.scrollToKey (0);
            this.submitted = true;
            this.acquiredRevision = this.revision;
            return;
        }
        if (!this.aligned () || !this.coherent ()) { this.confirmations = 0; return; }
        if (this.acquiredRevision != this.revision) this.confirmations = 0;
        this.acquiredRevision = this.revision;
        if (++this.confirmations < 2) return;
        this.ready = true;
        final Map<ParameterSlot, Value> values = new LinkedHashMap<> ();
        final long generation = this.generation;
        for (int index = 0; index < this.selected.cells.size (); index++)
        {
            final Cell cell = this.selected.cells.get (index);
            for (final NoteParameterRole role: NoteParameterRole.values ())
                values.put (role.slot (index), new Value (this.owner (), generation, role, () -> read (this.step (cell), role),
                    value -> write (this.step (cell), role, value), () -> this.generation == generation && this.current () && this.step (cell).state () == NoteStep.State.NoteOn, minimum (role), maximum (role)));
        }
        this.values = Map.copyOf (values);
    }

    Map<ParameterSlot, Value> values () { return this.current () ? this.values : Map.of (); }
    String owner () { return "retained-notes:" + this.generation; }
    String currentOwner () { return this.current () ? this.owner () : ""; }

    private Selection selection ()
    {
        final INoteEditor editor = this.editor.get ();
        if (editor == null || !(editor.getClip () instanceof CursorClipImpl source) || !this.sources.containsKey (source) || !source.doesExist ()) return null;
        final var nativeSource = source.getClip ();
        final String track = nativeSource.getTrack ().channelId ().get ();
        final int scene = nativeSource.clipLauncherSlot ().sceneIndex ().get ();
        final var notes = editor.getNotes ();
        if (track == null || track.isBlank () || scene < 0 || notes.isEmpty () || notes.size () > ParameterSlot.NOTE_CAPACITY || source.getNumSteps () > 128) return null;
        final List<Cell> cells = notes.stream ().map (position -> new Cell (position.getChannel (), position.getStep (), position.getNote ())).toList ();
        if (cells.stream ().anyMatch (cell -> cell.channel < 0 || cell.channel > 15 || cell.step < 0 || cell.step >= source.getNumSteps () || cell.key < 0 || cell.key > 127 || source.getObservedStep (cell.position ()).getState () != StepState.START)) return null;
        return new Selection (source, this.project.get (), track, scene, source.getTargetRevision (), source.getEditPage () * source.getNumSteps (), source.getStepLength (), editor.getSelectionRevision (), cells);
    }

    private boolean aligned ()
    {
        return this.handle != null && this.pool.valid (this.handle) && this.clip.exists ().get () && this.clip.isPinned ().get () &&
            this.selected.track.equals (this.clip.getTrack ().channelId ().get ()) && this.selected.scene == this.clip.clipLauncherSlot ().sceneIndex ().get () && this.sources.get (this.selected.source).get ();
    }
    private boolean current ()
    {
        if (!this.ready || this.acquiredRevision != this.revision || this.selected == null) return false;
        final INoteEditor editor = this.editor.get ();
        return editor != null && editor.getClip () == this.selected.source && editor.getSelectionRevision () == this.selected.selectionRevision &&
            this.selected.source.getTargetRevision () == this.selected.revision && Objects.equals (this.selected.project, this.project.get ()) && this.aligned ();
    }
    private NoteStep step (final Cell cell) { return this.clip.getStep (cell.channel, cell.step, cell.key); }

    private boolean coherent ()
    {
        for (final Cell cell: this.selected.cells)
        {
            final NoteStep retained = this.step (cell);
            if (retained.state () != NoteStep.State.NoteOn) return false;
            final StepInfoImpl value = new StepInfoImpl ();
            value.updateData (retained);
            if (!PushEditingPageObserver.data (value).equals (PushEditingPageObserver.data (this.selected.source.getObservedStep (cell.position ())))) return false;
        }
        return true;
    }

    static double minimum (final NoteParameterRole role)
    {
        return switch (role) { case PAN, TIMBRE, REPEAT_CURVE, REPEAT_VELOCITY_CURVE, REPEAT_VELOCITY_END -> -1; case TRANSPOSE -> -96; case REPEAT_COUNT -> -127; case RECURRENCE_LENGTH -> 1; default -> 0; };
    }
    static double maximum (final NoteParameterRole role)
    {
        return switch (role) { case DURATION -> Double.MAX_VALUE; case TRANSPOSE -> 96; case REPEAT_COUNT -> 127; case RECURRENCE_LENGTH -> 8; case OCCURRENCE -> NoteOccurrence.values ().length - 1; default -> 1; };
    }
    static double read (final NoteStep step, final NoteParameterRole role)
    {
        return switch (role)
        {
            case DURATION -> step.duration (); case MUTE -> step.isMuted () ? 1 : 0; case VELOCITY -> step.velocity (); case VELOCITY_SPREAD -> step.velocitySpread (); case RELEASE_VELOCITY -> step.releaseVelocity ();
            case CHANCE -> step.chance (); case OCCURRENCE -> step.occurrence ().ordinal (); case RECURRENCE_LENGTH -> step.recurrenceLength ();
            case GAIN -> step.gain () / 2; case PAN -> step.pan (); case TRANSPOSE -> step.transpose (); case TIMBRE -> step.timbre (); case PRESSURE -> step.pressure ();
            case REPEAT_COUNT -> step.repeatCount (); case REPEAT_CURVE -> step.repeatCurve (); case REPEAT_VELOCITY_CURVE -> step.repeatVelocityCurve (); case REPEAT_VELOCITY_END -> step.repeatVelocityEnd ();
        };
    }
    static void write (final NoteStep step, final NoteParameterRole role, final double value)
    {
        switch (role)
        {
            case DURATION -> step.setDuration (value); case MUTE -> step.setIsMuted (value > 0.5); case VELOCITY -> step.setVelocity (value); case VELOCITY_SPREAD -> step.setVelocitySpread (value); case RELEASE_VELOCITY -> step.setReleaseVelocity (value);
            case CHANCE -> step.setChance (value); case OCCURRENCE -> step.setOccurrence (NoteOccurrence.values ()[(int) Math.round (value)]); case RECURRENCE_LENGTH -> step.setRecurrence ((int) Math.round (value), step.recurrenceMask ());
            case GAIN -> step.setGain (value); case PAN -> step.setPan (value); case TRANSPOSE -> step.setTranspose (value); case TIMBRE -> step.setTimbre (value); case PRESSURE -> step.setPressure (value);
            case REPEAT_COUNT -> step.setRepeatCount ((int) Math.round (value)); case REPEAT_CURVE -> step.setRepeatCurve (value); case REPEAT_VELOCITY_CURVE -> step.setRepeatVelocityCurve (value); case REPEAT_VELOCITY_END -> step.setRepeatVelocityEnd (value);
        }
    }
}
