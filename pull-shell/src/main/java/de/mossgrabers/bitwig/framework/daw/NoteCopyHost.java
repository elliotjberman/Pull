// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.bitwig.framework.daw;

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
    // Initialization-owned topology: four simultaneous copies per editor grid, with a three-second
    // observed-host deadline. Saturation refuses a copy; an acquired cursor never follows selection.
    private static final int CAPACITY = 4;
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
        final int scene = this.source.clipLauncherSlot ().sceneIndex ().get ();
        if (trackId == null || trackId.isBlank () || scene < 0)
            return;
        for (final Lane lane: this.lanes)
            if (lane.position != null && trackId.equals (lane.trackId) && scene == lane.scene
                && near ((page * this.width + destination.getStep ()) * stepSize, (lane.page * this.width + lane.position.getStep ()) * lane.stepSize)
                && destination.getChannel () == lane.position.getChannel ()
                && destination.getNote () == lane.position.getNote ())
            {
                this.host.error ("Note copy destination is already busy.");
                return;
            }
        for (final Lane lane: this.lanes)
            if (lane.position == null)
            {
                lane.start (destination, page, stepSize, value, trackId, scene);
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

    private final class Lane
    {
        private final CursorTrack track;
        private final PinnableCursorClip clip;
        private final BooleanValue sameClip;
        private NotePosition position;
        private IStepInfo value;
        private String trackId;
        private String projectId;
        private int scene;
        private int page;
        private double stepSize;
        private int phase;
        private int aligned;
        private int polls;
        private long generation;
        private long revision;
        private long submittedRevision;
        private boolean observedCreated;
        private boolean removed;

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
                if (this.position != null && step.channel () == this.position.getChannel ()
                    && step.x () == this.position.getStep () && step.y () == this.position.getNote ())
                {
                    this.revision++;
                    if (this.phase >= 2)
                    {
                        if (step.state () == NoteStep.State.NoteOn)
                            this.observedCreated = true;
                        else if (this.observedCreated && step.state () == NoteStep.State.Empty)
                            this.removed = true;
                    }
                    NoteStepDebug.recordObserved ("copy", this.trackId, this.scene, step, true);
                }
            });
        }

        void start (final NotePosition destination, final int page, final double stepSize, final IStepInfo value, final String trackId, final int scene)
        {
            this.position = new NotePosition (destination);
            this.value = value.createCopy ();
            this.trackId = trackId;
            this.projectId = NoteCopyHost.this.projectIdentity.get ();
            this.scene = scene;
            this.page = page;
            this.stepSize = stepSize;
            this.phase = 0;
            this.aligned = 0;
            this.polls = 0;
            this.observedCreated = false;
            this.removed = false;
            this.generation++;
            this.track.selectChannel (NoteCopyHost.this.source.getTrack ());
            this.track.isPinned ().set (true);
            trace ("COPY_REQUEST", "");
            schedule ();
        }

        private void schedule ()
        {
            final long expected = this.generation;
            NoteCopyHost.this.host.scheduleTask (() -> {
                if (!NoteCopyHost.this.closed && this.position != null && this.generation == expected)
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
            final boolean held = this.phase == 2 && SelectionDebug.noteCopiesPaused ();
            if (!java.util.Objects.equals (this.projectId, NoteCopyHost.this.projectIdentity.get ()))
            {
                retire ("project changed");
                return;
            }
            if (!held && ++this.polls > MAX_POLLS)
            {
                retire ("host acknowledgement timed out");
                return;
            }
            if (this.phase < 2 && !sourceUnchanged ())
            {
                retire ("selection changed before capture");
                return;
            }
            if (this.phase == 0)
            {
                if (this.trackId.equals (this.track.channelId ().get ()) && this.track.isPinned ().get ())
                {
                    this.clip.selectClip (NoteCopyHost.this.source);
                    this.clip.isPinned ().set (true);
                    this.clip.setStepSize (this.stepSize);
                    this.clip.scrollToStep (this.page * NoteCopyHost.this.width);
                    this.clip.scrollToKey (0);
                    this.phase = 1;
                }
            }
            else if (this.phase == 1)
            {
                this.aligned = retained () && this.sameClip.get () ? this.aligned + 1 : 0;
                if (this.aligned >= 2)
                {
                    this.submittedRevision = this.revision;
                    this.phase = 2;
                    this.clip.setStep (this.position.getChannel (), this.position.getStep (), this.position.getNote (),
                        (int) (this.value.getVelocity () * 127), this.value.getDuration ());
                    trace ("COPY_CREATED", "submitted=true");
                }
            }
            else if (this.removed || !retained ())
            {
                retire ("destination lost");
                return;
            }
            else
            {
                final NoteStep note = this.clip.getStep (this.position.getChannel (), this.position.getStep (), this.position.getNote ());
                if (this.phase == 2 && !held && this.revision > this.submittedRevision && note.state () == NoteStep.State.NoteOn
                    && near (note.velocity (), (int) (this.value.getVelocity () * 127) / 127.0) && near (note.duration (), this.value.getDuration ()))
                {
                    if (expressionsMatch (note))
                    {
                        NoteStepDebug.recordObserved ("copy-complete", this.trackId, this.scene, note);
                        retire ("complete");
                        return;
                    }
                    this.submittedRevision = this.revision;
                    this.phase = 3;
                    note.setVelocity (this.value.getVelocity ());
                    // Framework snapshots store raw Bitwig gain / 2 (StepInfoImpl.updateData).
                    note.setGain (this.value.getGain () * 2);
                    note.setPan (this.value.getPan ());
                    note.setPressure (this.value.getPressure ());
                    note.setReleaseVelocity (this.value.getReleaseVelocity ());
                    note.setTimbre (this.value.getTimbre ());
                    note.setTranspose (this.value.getTranspose ());
                    trace ("COPY_EXPRESSIONS", "submitted=true");
                }
                else if (this.phase == 3 && this.revision > this.submittedRevision && note.state () == NoteStep.State.NoteOn && expressionsMatch (note))
                {
                    NoteStepDebug.recordObserved ("copy-complete", this.trackId, this.scene, note);
                    retire ("complete");
                    return;
                }
            }
            schedule ();
        }

        private boolean expressionsMatch (final NoteStep note)
        {
            return near (note.velocity (), this.value.getVelocity ()) && near (note.gain (), this.value.getGain () * 2)
                && near (note.pan (), this.value.getPan ()) && near (note.pressure (), this.value.getPressure ())
                && near (note.releaseVelocity (), this.value.getReleaseVelocity ()) && near (note.timbre (), this.value.getTimbre ())
                && near (note.transpose (), this.value.getTranspose ());
        }

        private void trace (final String kind, final String detail)
        {
            SelectionDebug.record (kind, "track=" + this.trackId + " scene=" + this.scene + " page=" + this.page
                + " x=" + this.position.getStep () + " y=" + this.position.getNote () + " " + detail);
        }

        private void retire (final String reason)
        {
            if (this.position == null)
                return;
            trace ("COPY_END", reason);
            this.position = null;
            this.value = null;
            this.generation++;
            // No callbacks or restoration are scheduled after extension exit.
            this.clip.isPinned ().set (false);
            this.track.isPinned ().set (false);
        }
    }

    private static boolean near (final double a, final double b)
    {
        return Math.abs (a - b) < 0.0001;
    }
}
