// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.bitwig.framework.daw;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import com.bitwig.extension.controller.api.BooleanValue;
import com.bitwig.extension.controller.api.ControllerHost;
import com.bitwig.extension.controller.api.CursorTrack;
import com.bitwig.extension.controller.api.NoteStep;
import com.bitwig.extension.controller.api.PinnableCursorClip;
import de.mossgrabers.framework.daw.IHost;
import de.mossgrabers.framework.daw.clip.IStepInfo;
import de.mossgrabers.framework.daw.clip.NotePosition;
import de.mossgrabers.pull.shell.NoteStepDebug;
import de.mossgrabers.pull.shell.SelectionDebug;

/** Executes the existing two-part note copy on an independently retained Launcher target. */
final class NoteCopyHost implements AutoCloseable
{
    // Initialization-owned topology: four captured clip windows per editor grid, each carrying at
    // most 128 pending note cells. Each batch has a 150-poll observed-host deadline; appending
    // notes does not extend it. An acquired cursor never follows selection.
    private static final int CAPACITY = 4;
    private static final int NOTE_CAPACITY = 128;
    private static final int MAX_POLLS = 150;
    private final IHost host;
    private final PinnableCursorClip source;
    private final Supplier<String> projectIdentity;
    private final int width;
    private final int height;
    private final Lane[] lanes = new Lane[CAPACITY];
    private boolean closed;

    NoteCopyHost (final IHost host, final ControllerHost controllerHost, final PinnableCursorClip source,
                  final String id, final int width, final int height, final Supplier<String> projectIdentity)
    {
        this.host = host;
        this.source = source;
        this.projectIdentity = projectIdentity;
        this.width = width;
        this.height = height;
        source.exists ().markInterested ();
        source.getTrack ().channelId ().markInterested ();
        source.clipLauncherSlot ().sceneIndex ().markInterested ();
        for (int i = 0; i < this.lanes.length; i++)
            this.lanes[i] = new Lane (controllerHost, id + ".copy." + i);
    }

    void copy (final NotePosition destination, final int page, final double stepSize, final IStepInfo value)
    {
        if (this.closed || !this.source.exists ().get () || page < 0 || !Double.isFinite (stepSize) || stepSize <= 0
            || destination.getChannel () < 0 || destination.getChannel () >= 16 || destination.getStep () < 0
            || destination.getStep () >= this.width || destination.getNote () < 0 || destination.getNote () >= this.height)
            return;
        final String trackId = this.source.getTrack ().channelId ().get ();
        final String projectId = this.projectIdentity.get ();
        final int scene = this.source.clipLauncherSlot ().sceneIndex ().get ();
        if (trackId == null || trackId.isBlank () || scene < 0)
            return;
        for (final Lane lane: this.lanes)
            if (lane.contains (destination, page, stepSize, projectId, trackId, scene))
            {
                this.host.error ("Note copy destination is already busy.");
                return;
            }
        for (final Lane lane: this.lanes)
            if (lane.matchesWindow (projectId, trackId, scene, page, stepSize))
            {
                if (!lane.sourceUnchanged () || lane.phase == LanePhase.CAPTURED && (!lane.retained () || !lane.sameClip.get ()))
                    this.host.error ("Note copy target changed; wait for its pending copies.");
                else if (lane.notes.size () >= NOTE_CAPACITY)
                    this.host.error ("Note copy window capacity reached; wait for the pending copies.");
                else
                    lane.append (destination, value);
                return;
            }
        for (final Lane lane: this.lanes)
            if (lane.phase == LanePhase.IDLE)
            {
                lane.start (destination, page, stepSize, value, projectId, trackId, scene);
                return;
            }
        this.host.error ("Note copy capacity reached; wait for the pending copies.");
    }

    @Override
    public void close ()
    {
        this.closed = true;
        for (final Lane lane: this.lanes)
            lane.retire ("closed");
    }

    private enum LanePhase { IDLE, ACQUIRING_TRACK, ACQUIRING_CLIP, CAPTURED }

    private enum NotePhase { QUEUED, WAITING_FOR_NOTE, WAITING_FOR_EXPRESSIONS }

    private record Cell (int channel, int step, int note)
    {
        private Cell (final NotePosition position)
        {
            this (position.getChannel (), position.getStep (), position.getNote ());
        }
    }

    private static final class PendingNote
    {
        private final Cell cell;
        private final IStepInfo value;
        private NotePhase phase = NotePhase.QUEUED;
        private long revision;
        private long submittedRevision;
        private boolean observedCreated;
        private boolean removed;

        private PendingNote (final NotePosition destination, final IStepInfo value)
        {
            this.cell = new Cell (destination);
            this.value = value.createCopy ();
        }
    }

    private final class Lane
    {
        private final CursorTrack track;
        private final PinnableCursorClip clip;
        private final BooleanValue sameClip;
        private final Map<Cell, PendingNote> notes = new LinkedHashMap<> ();
        private String trackId;
        private String projectId;
        private int scene;
        private int page;
        private double stepSize;
        private LanePhase phase = LanePhase.IDLE;
        private int aligned;
        private int polls;
        private long generation;

        Lane (final ControllerHost controllerHost, final String id)
        {
            this.track = controllerHost.createCursorTrack (id, "Note copy", 0, 0, false);
            this.clip = this.track.createLauncherCursorClip (id, "Note copy", NoteCopyHost.this.width, NoteCopyHost.this.height);
            this.sameClip = this.clip.createEqualsValue (NoteCopyHost.this.source);
            this.sameClip.markInterested ();
            this.track.channelId ().markInterested ();
            this.track.isPinned ().markInterested ();
            this.clip.exists ().markInterested ();
            this.clip.getTrack ().channelId ().markInterested ();
            this.clip.clipLauncherSlot ().sceneIndex ().markInterested ();
            this.clip.isPinned ().markInterested ();
            this.clip.addNoteStepObserver (step -> {
                if (this.notes.isEmpty ())
                    return;
                final PendingNote pending = this.notes.get (new Cell (step.channel (), step.x (), step.y ()));
                if (pending != null)
                {
                    pending.revision++;
                    if (pending.phase != NotePhase.QUEUED)
                    {
                        if (step.state () == NoteStep.State.NoteOn)
                            pending.observedCreated = true;
                        else if (pending.observedCreated && step.state () == NoteStep.State.Empty)
                            pending.removed = true;
                    }
                    NoteStepDebug.recordObserved ("copy", this.trackId, this.scene, step, true);
                }
            });
        }

        private boolean contains (final NotePosition destination, final int page, final double stepSize,
                                  final String projectId, final String trackId, final int scene)
        {
            if (this.phase == LanePhase.IDLE || !Objects.equals (projectId, this.projectId) || !trackId.equals (this.trackId) || scene != this.scene)
                return false;
            final double beat = ((double) page * NoteCopyHost.this.width + destination.getStep ()) * stepSize;
            for (final Cell cell: this.notes.keySet ())
                if (cell.channel () == destination.getChannel () && cell.note () == destination.getNote ()
                    && near (beat, ((double) this.page * NoteCopyHost.this.width + cell.step ()) * this.stepSize))
                    return true;
            return false;
        }

        private boolean matchesWindow (final String projectId, final String trackId, final int scene, final int page, final double stepSize)
        {
            return this.phase != LanePhase.IDLE && Objects.equals (projectId, this.projectId)
                && trackId.equals (this.trackId) && scene == this.scene && page == this.page && Double.compare (stepSize, this.stepSize) == 0;
        }

        private void append (final NotePosition destination, final IStepInfo value)
        {
            final PendingNote pending = new PendingNote (destination, value);
            this.notes.put (pending.cell, pending);
            if (SelectionDebug.recording ())
                trace ("COPY_REQUEST", pending, "velocity=" + pending.value.getVelocity () + " gain=" + pending.value.getGain () * 2
                    + " pan=" + pending.value.getPan () + " pressure=" + pending.value.getPressure ()
                    + " releaseVelocity=" + pending.value.getReleaseVelocity () + " timbre=" + pending.value.getTimbre ()
                    + " transpose=" + pending.value.getTranspose ());
        }

        void start (final NotePosition destination, final int page, final double stepSize, final IStepInfo value,
                    final String projectId, final String trackId, final int scene)
        {
            this.trackId = trackId;
            this.projectId = projectId;
            this.scene = scene;
            this.page = page;
            this.stepSize = stepSize;
            this.phase = LanePhase.ACQUIRING_TRACK;
            this.aligned = 0;
            this.polls = 0;
            this.generation++;
            append (destination, value);
            this.track.selectChannel (NoteCopyHost.this.source.getTrack ());
            this.track.isPinned ().set (true);
            schedule ();
        }

        private void schedule ()
        {
            final long expected = this.generation;
            NoteCopyHost.this.host.scheduleTask (() -> {
                if (!NoteCopyHost.this.closed && this.phase != LanePhase.IDLE && this.generation == expected)
                    poll ();
            }, 20);
        }

        private boolean retained ()
        {
            return this.clip.exists ().get () && this.track.isPinned ().get () && this.clip.isPinned ().get ()
                && this.trackId.equals (this.track.channelId ().get ())
                && this.trackId.equals (this.clip.getTrack ().channelId ().get ())
                && this.scene == this.clip.clipLauncherSlot ().sceneIndex ().get ();
        }

        private boolean sourceUnchanged ()
        {
            return NoteCopyHost.this.source.exists ().get ()
                && this.trackId.equals (NoteCopyHost.this.source.getTrack ().channelId ().get ())
                && this.scene == NoteCopyHost.this.source.clipLauncherSlot ().sceneIndex ().get ();
        }

        private void poll ()
        {
            // The opt-in diagnostic hold has its own bounded deadline. It changes no ordinary delay.
            final boolean held = SelectionDebug.noteCopiesPaused () && this.notes.values ().stream ().anyMatch (note -> note.phase == NotePhase.WAITING_FOR_NOTE);
            if (!Objects.equals (this.projectId, NoteCopyHost.this.projectIdentity.get ()))
            {
                retire ("project changed");
                return;
            }
            if (!held && ++this.polls > MAX_POLLS)
            {
                retire ("host acknowledgement timed out");
                return;
            }
            if (this.phase != LanePhase.CAPTURED && !sourceUnchanged ())
            {
                retire ("selection changed before capture");
                return;
            }
            if (this.phase == LanePhase.ACQUIRING_TRACK)
            {
                if (this.trackId.equals (this.track.channelId ().get ()) && this.track.isPinned ().get ())
                {
                    this.clip.selectClip (NoteCopyHost.this.source);
                    this.clip.isPinned ().set (true);
                    this.clip.setStepSize (this.stepSize);
                    this.clip.scrollToStep (this.page * NoteCopyHost.this.width);
                    this.clip.scrollToKey (0);
                    this.phase = LanePhase.ACQUIRING_CLIP;
                }
            }
            else if (this.phase == LanePhase.ACQUIRING_CLIP)
            {
                this.aligned = retained () && this.sameClip.get () ? this.aligned + 1 : 0;
                if (this.aligned >= 2)
                {
                    this.phase = LanePhase.CAPTURED;
                    advanceNotes (held);
                }
            }
            else if (!retained ())
            {
                retire ("destination lost");
                return;
            }
            else
            {
                advanceNotes (held);
            }
            if (this.notes.isEmpty ())
                retire ("complete");
            else
                schedule ();
        }

        private void advanceNotes (final boolean held)
        {
            for (final Iterator<PendingNote> iterator = this.notes.values ().iterator (); iterator.hasNext ();)
                if (advance (iterator.next (), held))
                    iterator.remove ();
        }

        private boolean advance (final PendingNote pending, final boolean held)
        {
            if (pending.removed)
            {
                trace ("COPY_END", pending, "destination lost");
                return true;
            }
            final Cell cell = pending.cell;
            final IStepInfo value = pending.value;
            if (pending.phase == NotePhase.QUEUED)
            {
                pending.submittedRevision = pending.revision;
                pending.phase = NotePhase.WAITING_FOR_NOTE;
                this.clip.setStep (cell.channel (), cell.step (), cell.note (), (int) (value.getVelocity () * 127), value.getDuration ());
                trace ("COPY_CREATED", pending, "submitted=true");
                return false;
            }
            final NoteStep note = this.clip.getStep (cell.channel (), cell.step (), cell.note ());
            if (pending.revision <= pending.submittedRevision || note.state () != NoteStep.State.NoteOn)
                return false;
            if (pending.phase == NotePhase.WAITING_FOR_NOTE)
            {
                if (held || !near (note.velocity (), (int) (value.getVelocity () * 127) / 127.0) || !near (note.duration (), value.getDuration ()))
                    return false;
                if (!expressionsMatch (note, value))
                {
                    pending.submittedRevision = pending.revision;
                    pending.phase = NotePhase.WAITING_FOR_EXPRESSIONS;
                    note.setVelocity (value.getVelocity ());
                    // Framework snapshots store raw Bitwig gain / 2 (StepInfoImpl.updateData).
                    note.setGain (value.getGain () * 2);
                    note.setPan (value.getPan ());
                    note.setPressure (value.getPressure ());
                    note.setReleaseVelocity (value.getReleaseVelocity ());
                    note.setTimbre (value.getTimbre ());
                    note.setTranspose (value.getTranspose ());
                    trace ("COPY_EXPRESSIONS", pending, "submitted=true");
                    return false;
                }
            }
            else if (!expressionsMatch (note, value))
                return false;
            NoteStepDebug.recordObserved ("copy-complete", this.trackId, this.scene, note);
            trace ("COPY_END", pending, "complete");
            return true;
        }

        private void trace (final String kind, final PendingNote pending, final String detail)
        {
            SelectionDebug.record (kind, "track=" + this.trackId + " scene=" + this.scene + " page=" + this.page
                + " x=" + pending.cell.step () + " y=" + pending.cell.note () + " " + detail);
        }

        private void retire (final String reason)
        {
            if (this.phase == LanePhase.IDLE)
                return;
            for (final PendingNote pending: this.notes.values ())
                trace ("COPY_END", pending, reason);
            this.notes.clear ();
            this.phase = LanePhase.IDLE;
            this.generation++;
            // No callbacks or restoration are scheduled after extension exit.
            this.clip.isPinned ().set (false);
            this.track.isPinned ().set (false);
        }
    }

    private static boolean expressionsMatch (final NoteStep note, final IStepInfo value)
    {
        return near (note.velocity (), value.getVelocity ()) && near (note.gain (), value.getGain () * 2)
            && near (note.pan (), value.getPan ()) && near (note.pressure (), value.getPressure ())
            && near (note.releaseVelocity (), value.getReleaseVelocity ()) && near (note.timbre (), value.getTimbre ())
            && near (note.transpose (), value.getTranspose ());
    }

    private static boolean near (final double a, final double b)
    {
        return Math.abs (a - b) < 0.0001;
    }
}
