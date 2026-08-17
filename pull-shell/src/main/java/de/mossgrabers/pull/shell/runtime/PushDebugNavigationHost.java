// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.shell.runtime;

import de.mossgrabers.controller.ableton.push.controller.PushControlSurface;
import de.mossgrabers.framework.controller.ButtonID;
import de.mossgrabers.framework.controller.hardware.IHwButton;
import de.mossgrabers.framework.daw.midi.SelectedTrackNoteTargetSnapshot;
import de.mossgrabers.framework.daw.midi.SelectedTrackMonitorMode;
import de.mossgrabers.framework.utils.ButtonEvent;
import de.mossgrabers.pull.core.api.ControlId;
import de.mossgrabers.pull.core.api.DesiredNoteInputRoute;
import de.mossgrabers.pull.core.api.InputRouteMode;
import de.mossgrabers.pull.core.api.PushControlIds;
import de.mossgrabers.pull.core.api.output.RgbColor;
import de.mossgrabers.pull.shell.PushDebugging;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;


/**
 * Local, bounded debugger navigation for closed-loop Push display development.
 *
 * <p>The client supplies a short plan made only from explicitly admitted controller gestures,
 * bounded pad-output probes, and authoritative controller-state predicates. A {@code submitted}
 * postcondition is available for a bounded one-shot gesture whose result is verified separately
 * through framebuffer or host readback. The stable shell validates and executes that generic plan
 * without owning named workflow policy.</p>
 */
final class PushDebugNavigationHost implements AutoCloseable
{
    static final String REQUEST_FILE = "navigate-request.txt";
    static final String STATUS_FILE  = "navigate-status.txt";

    private static final int  MAX_REQUEST_BYTES       = 512;
    private static final int  MAX_STEPS               = 4;
    private static final int  REQUIRED_STABLE_SAMPLES = 2;
    private static final int  TIMEOUT_TICKS           = 50;
    private static final long POLL_INTERVAL_MILLIS    = 100;

    private final Path                         requestPath;
    private final Path                         statusPath;
    private final NavigationSurface            surface;
    private final GestureAdmission             admission;
    private final ScheduledExecutorService     worker;
    private final AtomicReference<Incoming>    incoming = new AtomicReference<> ();
    private final AtomicReference<Status>      outgoing = new AtomicReference<> ();
    private final AtomicBoolean                closed = new AtomicBoolean ();

    private PendingNavigation pending;
    private ObservedNavigation lastObservation = ObservedNavigation.unavailable ();


    static PushDebugNavigationHost createIfEnabled (final PushControlSurface surface, final GestureAdmission admission, final Supplier<ControllerBridge.NotePerformanceState> notePerformanceState, final Function<ControlId, ControllerRuntimeEnvironment.DebugLightObservation> lightObservation)
    {
        if (!PushDebugging.isEnabled ())
            return null;

        final ScheduledExecutorService worker = PushDebugging.createWorker ("Pull debug navigation transport");
        final PushDebugNavigationHost host = new PushDebugNavigationHost (
            PushDebugging.directory (), new PushNavigationSurface (surface, notePerformanceState, lightObservation), admission, worker);
        worker.scheduleWithFixedDelay (host::pollFilesSafely, 0, POLL_INTERVAL_MILLIS, TimeUnit.MILLISECONDS);
        return host;
    }


    PushDebugNavigationHost (final Path debugDirectory, final NavigationSurface surface)
    {
        this (debugDirectory, surface, DirectAdmission.INSTANCE, null);
    }


    PushDebugNavigationHost (final Path debugDirectory, final NavigationSurface surface, final GestureAdmission admission)
    {
        this (debugDirectory, surface, admission, null);
    }


    PushDebugNavigationHost (
        final Path debugDirectory,
        final NavigationSurface surface,
        final GestureAdmission admission,
        final ScheduledExecutorService worker)
    {
        final Path directory = Objects.requireNonNull (debugDirectory, "debugDirectory");
        this.requestPath = directory.resolve (REQUEST_FILE);
        this.statusPath = directory.resolve (STATUS_FILE);
        this.surface = Objects.requireNonNull (surface, "surface");
        this.admission = Objects.requireNonNull (admission, "admission");
        this.worker = worker;
    }


    /** Advance controller-owned navigation state without filesystem access in production. */
    void tick ()
    {
        if (this.closed.get ())
            return;
        if (this.worker == null)
            this.pollFilesSafely ();

        if (this.pending == null)
        {
            final Incoming request = this.incoming.getAndSet (null);
            if (request != null)
            {
                if (request.failure ().isEmpty ())
                    this.pending = new PendingNavigation (request.requestID (), request.label (), request.steps ());
                else
                    this.publish (request.requestID (), "FAILED", request.label (), this.observeBestEffort (), request.failure ());
            }
        }

        if (this.pending != null)
        {
            try
            {
                this.advance ();
            }
            catch (final RuntimeException ex)
            {
                if (this.pending != null)
                    this.failPending ("could not observe controller navigation state: " + PushDebugging.sanitize (ex.getMessage ()), this.lastObservation);
            }
        }

        if (this.worker == null)
            this.pollFilesSafely ();
    }


    private void advance ()
    {
        final ObservedNavigation observed = this.observe ();
        this.pending.tick++;
        if (this.pending.tick >= TIMEOUT_TICKS)
        {
            this.failPending ("timed out waiting for authoritative controller state", observed);
            return;
        }

        while (this.pending.stepIndex < this.pending.steps.size ())
        {
            final Step step = this.pending.steps.get (this.pending.stepIndex);
            if (step.padProbe () != null)
            {
                if (!this.advancePadProbe (step, observed))
                    return;
                continue;
            }
            if (this.pending.stepSubmitted)
            {
                if (!step.postcondition ().submissionOnly () && !step.postcondition ().matches (observed))
                    return;
                this.pending.nextStep ();
                continue;
            }
            if (step.postcondition ().matches (observed))
            {
                this.pending.nextStep ();
                continue;
            }
            if (!step.gesture ().contextAvailable (observed))
            {
                this.complete ("FAILED", "gesture is unavailable in the current controller context", observed);
                return;
            }
            if (!this.admission.isIdle () || !step.gesture ().controlsFree (this.surface))
                return;

            try
            {
                if (this.admission.trySubmit ( () -> step.gesture ().triggerChecked (this.surface)))
                    this.pending.stepSubmitted = true;
            }
            catch (final RuntimeException ex)
            {
                this.complete ("FAILED", "could not submit navigation gesture: " + PushDebugging.sanitize (ex.getMessage ()), observed);
            }
            return;
        }

        final boolean finalPostconditionMet = this.pending.finalPredicate.submissionOnly () || this.pending.finalPredicate.matches (observed);
        if (!finalPostconditionMet || !this.admission.isIdle () || !this.pending.controlsFree (this.surface))
        {
            this.pending.stableSamples = 0;
            this.pending.stableObservation = null;
            return;
        }
        if (!observed.equals (this.pending.stableObservation))
        {
            this.pending.stableSamples = 0;
            this.pending.stableObservation = observed;
        }
        this.pending.stableSamples++;
        if (this.pending.stableSamples >= REQUIRED_STABLE_SAMPLES)
            this.complete ("READY", "", observed);
    }


    private boolean advancePadProbe (final Step step, final ObservedNavigation observed)
    {
        final PadProbe probe = step.padProbe ();
        this.pending.completedPadProbe = probe;
        if (!this.pending.stepSubmitted)
        {
            if (!step.postcondition ().matches (observed))
            {
                this.complete ("FAILED", "pad-output probe precondition is not satisfied", observed);
                return false;
            }

            final ObservedCoreLight startingLight = this.surface.coreLight (probe.control ());
            final PadStatus startingStatus = this.observePadStatus (probe, startingLight);
            this.pending.padStatus = startingStatus;
            if (!startingStatus.exclusiveRoute ())
            {
                this.complete ("FAILED", "pad-output probe requires an active EXCLUSIVE core pad route", observed);
                return false;
            }
            if (startingLight.coreGeneration () == 0)
            {
                this.complete ("FAILED", "pad-output probe requires an active core generation", observed);
                return false;
            }
            if (!startingStatus.mappingDesired ())
            {
                this.complete ("FAILED", "pad-output probe requires an active hardware mapping lease", observed);
                return false;
            }
            if (!startingStatus.mappedAvailable () || !startingStatus.mappingActive () || !this.admission.isIdle () || this.surface.isPressed (step.padProbe ().button ()))
                return false;

            final PendingNavigation owner = this.pending;
            final ActivePadProbe active = new ActivePadProbe (probe, observed, startingLight);
            boolean observing = false;
            try
            {
                this.surface.beginPadOutputObservation (probe.oneBasedPad ());
                observing = true;
                if (this.admission.tryBeginDebugInput ( () -> {
                    final ObservedNavigation live = this.observe ();
                    final ObservedCoreLight liveLight = this.surface.coreLight (probe.control ());
                    final PadStatus liveStatus = this.observePadStatus (probe, liveLight);
                    owner.padStatus = liveStatus;
                    if (!observed.equals (live) || !step.postcondition ().matches (live) || liveLight.coreGeneration () != active.coreGeneration || !liveStatus.ready ())
                        throw new IllegalStateException ("pad-output probe lost its context, route, or mapping fence");
                    owner.activePadProbe = active;
                    this.surface.trigger (probe.button (), ButtonEvent.DOWN, probe.velocity () / 127.0);
                }))
                {
                    if (this.pending != owner)
                        return false;
                    owner.stepSubmitted = true;
                    observing = false;
                }
            }
            catch (final RuntimeException ex)
            {
                if (this.pending == owner)
                    this.failPending ("could not submit pad-output probe: " + PushDebugging.sanitize (ex.getMessage ()), observed);
            }
            finally
            {
                if (observing)
                    this.surface.endPadOutputObservation (probe.oneBasedPad ());
            }
            return false;
        }

        final ActivePadProbe active = this.pending.activePadProbe;
        if (active == null)
        {
            this.complete ("FAILED", "pad-output probe lost its owned lifecycle", observed);
            return false;
        }
        if (!active.context.equals (observed) || !step.postcondition ().matches (observed))
        {
            this.failPending ("pad-output probe controller context changed", observed);
            return false;
        }

        final ObservedCoreLight light = this.surface.coreLight (active.probe.control ());
        if (light.coreGeneration () != active.coreGeneration)
        {
            this.failPending ("pad-output probe core generation changed", observed);
            return false;
        }
        if (active.releasing)
        {
            if (light.appliedRevision () < active.releaseAppliedRevision || !this.admission.debugInputRouteIdle ())
            {
                active.releaseSamples = 0;
                return false;
            }
            active.releaseSamples++;
            if (active.releaseSamples < REQUIRED_STABLE_SAMPLES)
                return false;
            this.finishPadProbe (active);
            this.pending.nextStep ();
            return true;
        }
        final PadStatus heldStatus = this.observePadStatus (active.probe, light);
        this.pending.padStatus = heldStatus;
        if (!heldStatus.exclusiveRoute ())
        {
            this.failPending ("pad-output probe route changed while held", observed);
            return false;
        }
        if (!heldStatus.mappingDesired ())
        {
            this.failPending ("pad-output probe hardware mapping lease changed while held", observed);
            return false;
        }
        if (!heldStatus.mappingActive ())
        {
            this.failPending ("pad-output probe hardware mapping activation changed while held", observed);
            return false;
        }
        if (!heldStatus.mappedAvailable ())
        {
            this.failPending ("pad-output probe mapped feedback became unavailable while held", observed);
            return false;
        }
        if (!light.present () || light.appliedRevision () <= active.startingAppliedRevision)
            return false;

        final PushControlSurface.DebugPadOutput output = this.surface.padOutput (active.probe.oneBasedPad ());
        if (!transmissionMatches (output, this.surface.resolvePadColor (light.color ())))
        {
            active.stableSamples = 0;
            active.stableEvidence = null;
            return false;
        }
        final PadEvidence evidence = new PadEvidence (output.midiNote (), light.color (), output.color (), output.blinkColor (), output.fast (), output.base (), output.blink ());
        if (!samePadEvidence (evidence, active.stableEvidence))
        {
            active.stableSamples = 0;
            active.stableEvidence = evidence;
        }
        active.stableSamples++;
        if (active.stableSamples < REQUIRED_STABLE_SAMPLES)
            return false;

        this.pending.padEvidence = evidence;
        this.releasePadProbe (active, light.appliedRevision (), observed);
        return false;
    }


    private PadStatus observePadStatus (final PadProbe probe, final ObservedCoreLight light)
    {
        return new PadStatus (this.admission.debugPadRoute (probe.control ()), light.mappingDesired (), this.admission.debugPadMappingActive (probe.control ()), light.mappedOn ());
    }


    private static boolean transmissionMatches (final PushControlSurface.DebugPadOutput output, final int expectedColor)
    {
        final PushControlSurface.DebugPadTransmission base = output.base ();
        if (output.color () != expectedColor || base.revision () == 0 || base.color () != output.color ())
            return false;
        final int blinkColor = output.blinkColor ();
        if (blinkColor <= 0 || blinkColor >= 128)
            return true;
        final PushControlSurface.DebugPadTransmission blink = output.blink ();
        return blink.revision () > 0 && blink.color () == blinkColor && blink.channel () == (output.fast () ? 14 : 10);
    }


    private static boolean samePadEvidence (final PadEvidence current, final PadEvidence previous)
    {
        if (previous == null)
            return false;
        return current.desiredColor ().equals (previous.desiredColor ()) &&
            current.resolvedColor () == previous.resolvedColor () &&
            current.resolvedBlinkColor () == previous.resolvedBlinkColor () &&
            current.fast () == previous.fast () &&
            sameTransmission (current.base (), previous.base ()) &&
            sameTransmission (current.blink (), previous.blink ());
    }


    private static boolean sameTransmission (final PushControlSurface.DebugPadTransmission current, final PushControlSurface.DebugPadTransmission previous)
    {
        return current.channel () == previous.channel () && current.note () == previous.note () && current.color () == previous.color ();
    }


    private void releasePadProbe (final ActivePadProbe active, final long heldAppliedRevision, final ObservedNavigation observed)
    {
        try
        {
            this.admission.endDebugInput ( () -> this.surface.trigger (active.probe.button (), ButtonEvent.UP, 0));
            final ObservedCoreLight released = this.surface.coreLight (active.probe.control ());
            if (released.coreGeneration () != active.coreGeneration || released.appliedRevision () <= heldAppliedRevision)
                throw new IllegalStateException ("pad UP did not produce a complete applied core result");
            active.releasing = true;
            active.releaseAppliedRevision = released.appliedRevision ();
        }
        catch (final RuntimeException ex)
        {
            this.finishPadProbe (active);
            this.complete ("FAILED", "could not release pad-output probe: " + PushDebugging.sanitize (ex.getMessage ()), observed);
        }
    }


    private void finishPadProbe (final ActivePadProbe active)
    {
        try
        {
            this.surface.endPadOutputObservation (active.probe.oneBasedPad ());
        }
        finally
        {
            this.admission.completeDebugInput ();
            if (this.pending != null)
                this.pending.activePadProbe = null;
        }
    }


    private void failPending (final String message, final ObservedNavigation observed)
    {
        this.releasePendingProbe ();
        this.complete ("FAILED", message, observed);
    }


    void cancelActiveProbe (final String message)
    {
        if (this.pending != null && this.pending.activePadProbe != null)
        {
            final String failure = Objects.requireNonNull (message, "message");
            this.releasePendingProbe ();
            this.complete ("FAILED", failure, this.observeBestEffort ());
        }
    }


    private void releasePendingProbe ()
    {
        final ActivePadProbe pad = this.pending.activePadProbe;
        if (pad == null)
            return;
        try
        {
            if (!pad.releasing)
                this.admission.endDebugInput ( () -> this.surface.trigger (pad.probe.button (), ButtonEvent.UP, 0));
        }
        catch (final RuntimeException ignored)
        {
            // Terminal cleanup is best effort, but the observation and lifecycle must end.
        }
        finally
        {
            this.finishPadProbe (pad);
        }
    }


    private ObservedNavigation observe ()
    {
        this.lastObservation = this.surface.observe ();
        return this.lastObservation;
    }


    private ObservedNavigation observeBestEffort ()
    {
        try
        {
            return this.observe ();
        }
        catch (final RuntimeException ignored)
        {
            return this.lastObservation;
        }
    }


    private static boolean targetMatches (final String trackID, final long generation, final ObservedNavigation observed)
    {
        return generation == observed.selectedTrackGeneration () && trackID.equals (observed.selectedTrackID ());
    }


    private void complete (final String state, final String message, final ObservedNavigation observed)
    {
        final PendingNavigation completed = this.pending;
        this.pending = null;
        this.publish (completed.requestID, state, completed.label, observed, completed.completedPadProbe, completed.padStatus, completed.padEvidence, message);
    }


    private void publish (final String requestID, final String state, final String label, final ObservedNavigation observed, final String message)
    {
        this.publish (requestID, state, label, observed, null, null, null, message);
    }


    private void publish (final String requestID, final String state, final String label, final ObservedNavigation observed, final PadProbe padProbe, final PadStatus padStatus, final PadEvidence padEvidence, final String message)
    {
        this.outgoing.set (new Status (requestID, state, label, observed, padProbe, padStatus, padEvidence, message));
    }


    private void pollFilesSafely ()
    {
        if (this.closed.get () && this.outgoing.get () == null)
            return;
        try
        {
            final Status status = this.outgoing.getAndSet (null);
            if (status != null)
                this.writeStatus (status);
            if (!this.closed.get () && this.incoming.get () == null)
            {
                final Incoming request = this.readRequest ();
                if (request != null && !this.closed.get ())
                    this.incoming.compareAndSet (null, request);
            }
        }
        catch (final IOException | RuntimeException ignored)
        {
            // The protocol client times out; transport failure cannot affect controller behavior.
        }
    }


    private Incoming readRequest () throws IOException
    {
        if (!Files.isRegularFile (this.requestPath, LinkOption.NOFOLLOW_LINKS))
            return null;

        if (Files.size (this.requestPath) > MAX_REQUEST_BYTES)
        {
            Files.deleteIfExists (this.requestPath);
            return Incoming.failed ("invalid", "", "request exceeds " + MAX_REQUEST_BYTES + " bytes");
        }

        final String request = Files.readString (this.requestPath).strip ();
        Files.deleteIfExists (this.requestPath);
        final String [] fields = request.split ("\\t", -1);
        if (fields.length < 3 || fields.length > MAX_STEPS + 2 || !PushDebugging.isIdentifier (fields[0]) || !PushDebugging.isIdentifier (fields[1]))
            return Incoming.failed ("invalid", "", "expected '<request-id> <label> <step>...'");
        try
        {
            final List<Step> steps = new ArrayList<> (fields.length - 2);
            for (int index = 2; index < fields.length; index++)
                steps.add (Step.parse (fields[index]));
            if (steps.stream ().filter (step -> step.padProbe () != null).count () > 1)
                throw new IllegalArgumentException ("a navigation plan may contain at most one pad-output probe");
            return Incoming.ready (fields[0], fields[1], List.copyOf (steps));
        }
        catch (final IllegalArgumentException ex)
        {
            return Incoming.failed (fields[0], fields[1], ex.getMessage ());
        }
    }


    private void writeStatus (final Status status) throws IOException
    {
        Files.createDirectories (this.statusPath.getParent ());
        final ObservedNavigation observed = status.observed ();
        final PadProbe padProbe = status.padProbe ();
        final PadStatus padStatus = status.padStatus ();
        final PadEvidence padEvidence = status.padEvidence ();
        final ControllerBridge.NotePerformanceState notePerformance = observed.notePerformance ();
        final DesiredNoteInputRoute desiredRoute = notePerformance.desired ().inputRoute ();
        final DesiredNoteInputRoute activeRoute = notePerformance.submittedRoute ();
        final String content = String.join ("\t",
            status.requestID (),
            status.state (),
            status.label ().isEmpty () ? "-" : status.label (),
            observed.viewID ().isEmpty () ? "-" : observed.viewID (),
            observed.modeID ().isEmpty () ? "-" : observed.modeID (),
            Boolean.toString (observed.workspaceActive ()),
            Boolean.toString (observed.noteRepeatActive ()),
            Boolean.toString (observed.noteLatchActive ()),
            Integer.toString (observed.selectedTrackPosition ()),
            observed.selectedTrackID ().isEmpty () ? "-" : PushDebugging.sanitize (observed.selectedTrackID ()),
            Long.toString (observed.selectedTrackGeneration ()),
            Boolean.toString (observed.selectedTrackArmed ()),
            observed.selectedTrackMonitorMode ().name (),
            Boolean.toString (observed.selectedTrackCanHoldNotes ()),
            Boolean.toString (notePerformance.available ()),
            Boolean.toString (desiredRoute.active ()),
            desiredRoute.active () ? PushDebugging.sanitize (desiredRoute.targetChannelId ()) : "-",
            desiredRoute.active () ? Long.toString (desiredRoute.targetGeneration ()) : "-",
            Boolean.toString (activeRoute.active ()),
            activeRoute.active () ? PushDebugging.sanitize (activeRoute.targetChannelId ()) : "-",
            activeRoute.active () ? Long.toString (activeRoute.targetGeneration ()) : "-",
            notePerformance.commandedLayout ().noteView ().name (),
            padProbe == null ? "-" : "OUTPUT",
            padProbe == null ? "-" : padProbe.button ().name (),
            padProbe == null ? "-" : padProbe.control ().value (),
            padEvidence == null ? "-" : Integer.toString (padEvidence.midiNote ()),
            padProbe == null ? "-" : Integer.toString (padProbe.velocity ()),
            padProbe == null ? "-" : padStatus == null ? "NONE" : padStatus.routeName (),
            padProbe == null ? "-" : Boolean.toString (padStatus != null && padStatus.mappingDesired ()),
            padProbe == null ? "-" : Boolean.toString (padStatus != null && padStatus.mappingActive ()),
            padProbe == null || padStatus == null || !padStatus.mappedAvailable () ? "-" : Boolean.toString (padStatus.mappedOn ().booleanValue ()),
            padEvidence == null ? "-" : rgb (padEvidence.desiredColor ()),
            padEvidence == null ? "-" : padEvidence.resolvedColor () + ":" + padEvidence.resolvedBlinkColor () + ":" + padEvidence.fast (),
            padEvidence == null ? "-" : transmissions (padEvidence),
            PushDebugging.sanitize (status.message ())) + "\n";
        final Path temporaryStatus = this.statusPath.resolveSibling (this.statusPath.getFileName () + ".tmp");
        Files.writeString (temporaryStatus, content);
        PushDebugging.replaceAtomically (temporaryStatus, this.statusPath);
    }


    private static String rgb (final RgbColor color)
    {
        return String.format ("%02X%02X%02X", Integer.valueOf (color.red ()), Integer.valueOf (color.green ()), Integer.valueOf (color.blue ()));
    }


    private static String transmissions (final PadEvidence evidence)
    {
        final PushControlSurface.DebugPadTransmission base = evidence.base ();
        final PushControlSurface.DebugPadTransmission blink = evidence.blink ();
        return "base=" + transmission (base) + ";blink=" + (blink.revision () == 0 ? "-" : transmission (blink));
    }


    private static String transmission (final PushControlSurface.DebugPadTransmission transmission)
    {
        return transmission.channel () + ":" + transmission.note () + ":" + transmission.color ();
    }


    @Override
    public void close ()
    {
        if (!this.closed.compareAndSet (false, true))
            return;

        if (this.pending != null)
        {
            this.releasePendingProbe ();
            this.complete ("FAILED", "extension is closing", this.observeBestEffort ());
        }
        final Incoming queued = this.incoming.getAndSet (null);
        if (queued != null)
            this.publish (queued.requestID (), "FAILED", queued.label (), this.observeBestEffort (), "extension is closing");

        if (this.worker == null)
        {
            this.pollFilesSafely ();
            return;
        }

        PushDebugging.shutdownWorker (this.worker, this::pollFilesSafely);
    }


    interface GestureAdmission
    {
        boolean isIdle ();

        boolean trySubmit (Runnable gesture);

        default boolean tryBeginDebugInput (final Runnable press)
        {
            return this.trySubmit (press);
        }

        default void endDebugInput (final Runnable release)
        {
            Objects.requireNonNull (release, "release").run ();
        }

        default void completeDebugInput ()
        {
            // Direct test admission owns no asynchronous input lifecycle.
        }

        default InputRouteMode debugPadRoute (final ControlId control)
        {
            return InputRouteMode.EXCLUSIVE;
        }

        default boolean debugPadMappingActive (final ControlId control)
        {
            return true;
        }

        default boolean debugInputRouteIdle ()
        {
            return true;
        }
    }


    private enum DirectAdmission implements GestureAdmission
    {
        INSTANCE;


        @Override
        public boolean isIdle ()
        {
            return true;
        }


        @Override
        public boolean trySubmit (final Runnable gesture)
        {
            Objects.requireNonNull (gesture, "gesture").run ();
            return true;
        }
    }


    interface NavigationSurface
    {
        ObservedNavigation observe ();

        boolean isPressed (ButtonID button);

        void trigger (ButtonID button, ButtonEvent event);

        default void trigger (final ButtonID button, final ButtonEvent event, final double velocity)
        {
            this.trigger (button, event);
        }

        default ObservedCoreLight coreLight (final ControlId control)
        {
            return new ObservedCoreLight (0, 0, null, false, null);
        }

        default void beginPadOutputObservation (final int oneBasedPad)
        {
            throw new IllegalStateException ("Pad output observation is unavailable");
        }

        default PushControlSurface.DebugPadOutput padOutput (final int oneBasedPad)
        {
            throw new IllegalStateException ("Pad output observation is unavailable");
        }

        default int resolvePadColor (final RgbColor color)
        {
            return -1;
        }

        default void endPadOutputObservation (final int oneBasedPad)
        {
            // No observation was installed.
        }


        default void click (final ButtonID button)
        {
            try
            {
                this.trigger (button, ButtonEvent.DOWN);
            }
            finally
            {
                this.trigger (button, ButtonEvent.UP);
            }
        }
    }


    record ObservedNavigation (String viewID, String modeID, boolean workspaceActive, int selectedTrackPosition, String selectedTrackID, long selectedTrackGeneration, boolean selectedTrackCanHoldNotes, boolean noteRepeatActive, boolean noteLatchActive, boolean selectedTrackArmed, SelectedTrackMonitorMode selectedTrackMonitorMode, ControllerBridge.NotePerformanceState notePerformance)
    {
        private static ObservedNavigation unavailable ()
        {
            return new ObservedNavigation ("", "", false, -1, "", 0, false, false, false, false, SelectedTrackMonitorMode.OFF, ControllerBridge.NotePerformanceState.unavailable ());
        }


        ObservedNavigation (final String viewID, final String modeID, final boolean workspaceActive, final int selectedTrackPosition, final String selectedTrackID, final long selectedTrackGeneration, final boolean noteRepeatActive)
        {
            this (viewID, modeID, workspaceActive, selectedTrackPosition, selectedTrackID, selectedTrackGeneration, true, noteRepeatActive, false, false, SelectedTrackMonitorMode.OFF, ControllerBridge.NotePerformanceState.unavailable ());
        }

        ObservedNavigation
        {
            viewID = Objects.requireNonNull (viewID, "viewID");
            modeID = Objects.requireNonNull (modeID, "modeID");
            selectedTrackID = Objects.requireNonNull (selectedTrackID, "selectedTrackID");
            selectedTrackMonitorMode = Objects.requireNonNull (selectedTrackMonitorMode, "selectedTrackMonitorMode");
            notePerformance = Objects.requireNonNull (notePerformance, "notePerformance");
            if (selectedTrackGeneration < 0)
                throw new IllegalArgumentException ("selectedTrackGeneration must not be negative");
        }
    }


    record ObservedCoreLight (long coreGeneration, long appliedRevision, RgbColor color, boolean mappingDesired, Boolean mappedOn)
    {
        private boolean present ()
        {
            return this.color != null;
        }
    }


    private enum NavigationGesture
    {
        NOTE (ButtonID.NOTE),
        TRACK (ButtonID.TRACK),
        MASTERTRACK (ButtonID.MASTERTRACK),
        SESSION (ButtonID.SESSION),
        LAYOUT (ButtonID.LAYOUT),
        PLAY (ButtonID.PLAY),
        ROW1_1 (ButtonID.ROW1_1, NavigationContext.TRACK),
        ROW1_2 (ButtonID.ROW1_2, NavigationContext.TRACK),
        ROW1_3 (ButtonID.ROW1_3, NavigationContext.TRACK),
        ROW1_4 (ButtonID.ROW1_4, NavigationContext.TRACK),
        ROW1_5 (ButtonID.ROW1_5, NavigationContext.TRACK),
        ROW1_6 (ButtonID.ROW1_6, NavigationContext.TRACK),
        ROW1_7 (ButtonID.ROW1_7, NavigationContext.TRACK),
        ROW1_8 (ButtonID.ROW1_8, NavigationContext.TRACK),
        ROW2_5 (ButtonID.ROW2_5, NavigationContext.MASTER),
        ROW2_7 (ButtonID.ROW2_7, NavigationContext.MASTER),
        ROW2_8 (ButtonID.ROW2_8, NavigationContext.MASTER),
        SHIFT_SESSION (ButtonID.SHIFT, ButtonID.SESSION),
        SHIFT_LAYOUT (ButtonID.SHIFT, ButtonID.LAYOUT);

        private final ButtonID          modifier;
        private final ButtonID          button;
        private final NavigationContext context;
        private final Set<ButtonID>     controls;


        NavigationGesture (final ButtonID button)
        {
            this (null, button, NavigationContext.ANY);
        }


        NavigationGesture (final ButtonID button, final NavigationContext context)
        {
            this (null, button, context);
        }


        NavigationGesture (final ButtonID modifier, final ButtonID button)
        {
            this (modifier, button, NavigationContext.ANY);
        }


        NavigationGesture (final ButtonID modifier, final ButtonID button, final NavigationContext context)
        {
            this.modifier = modifier;
            this.button = button;
            this.context = Objects.requireNonNull (context, "context");
            final Set<ButtonID> owned = new HashSet<> (Set.of (ButtonID.SHIFT, button));
            if (modifier != null)
                owned.add (modifier);
            this.controls = Set.copyOf (owned);
        }


        static NavigationGesture parse (final String value)
        {
            final String normalized = value.replace ('+', '_');
            try
            {
                return valueOf (normalized);
            }
            catch (final IllegalArgumentException ex)
            {
                throw new IllegalArgumentException ("unsupported navigation gesture '" + PushDebugging.sanitize (value) + "'");
            }
        }


        boolean controlsFree (final NavigationSurface surface)
        {
            return this.controls.stream ().noneMatch (surface::isPressed);
        }


        boolean contextAvailable (final ObservedNavigation observed)
        {
            return this.context.isAvailable (observed);
        }


        void triggerChecked (final NavigationSurface surface)
        {
            if (!this.contextAvailable (surface.observe ()))
                throw new IllegalStateException ("Push debug gesture lost its authoritative context");
            if (this.modifier == null)
            {
                surface.click (this.button);
                return;
            }
            try
            {
                surface.trigger (this.modifier, ButtonEvent.DOWN);
                surface.click (this.button);
            }
            finally
            {
                surface.trigger (this.modifier, ButtonEvent.UP);
            }
        }
    }


    private enum NavigationContext
    {
        ANY,
        TRACK,
        MASTER;


        boolean isAvailable (final ObservedNavigation observed)
        {
            return switch (this)
            {
                case ANY -> true;
                case TRACK -> !observed.workspaceActive () && "TRACK".equals (observed.modeID ());
                case MASTER -> observed.workspaceActive () && Set.of ("MASTER", "MASTER_TEMP").contains (observed.modeID ());
            };
        }
    }


    private record NavigationPredicate (
        Set<String> allowedViews,
        Set<String> deniedViews,
        Set<String> allowedModes,
        Set<String> deniedModes,
        Boolean workspaceActive,
        Integer selectedTrackPosition,
        String selectedTrackID,
        Boolean noteRepeatActive,
        Boolean noteLatchActive,
        Boolean selectedTrackArmed,
        SelectedTrackMonitorMode selectedTrackMonitorMode,
        NoteRouteExpectation noteRoute,
        boolean submissionOnly)
    {
        private static final NavigationPredicate ANY = new NavigationPredicate (Set.of (), Set.of (), Set.of (), Set.of (), null, null, null, null, null, null, null, null, false);
        private static final NavigationPredicate SUBMITTED = new NavigationPredicate (Set.of (), Set.of (), Set.of (), Set.of (), null, null, null, null, null, null, null, null, true);


        static NavigationPredicate parse (final String value)
        {
            if ("*".equals (value))
                return ANY;
            if ("submitted".equals (value))
                return SUBMITTED;

            Set<String> allowedViews = Set.of ();
            Set<String> deniedViews = Set.of ();
            Set<String> allowedModes = Set.of ();
            Set<String> deniedModes = Set.of ();
            Boolean workspace = null;
            Integer trackPosition = null;
            String trackID = null;
            Boolean repeat = null;
            Boolean latch = null;
            Boolean armed = null;
            SelectedTrackMonitorMode monitor = null;
            NoteRouteExpectation route = null;
            for (final String term: value.split (","))
            {
                final boolean denied = term.contains ("!=");
                final String [] parts = term.split (denied ? "!=" : "=", 2);
                if (parts.length != 2 || parts[1].isEmpty ())
                    throw new IllegalArgumentException ("invalid navigation predicate '" + PushDebugging.sanitize (value) + "'");
                switch (parts[0])
                {
                    case "view" -> {
                        if (denied)
                            deniedViews = parseIDs (parts[1]);
                        else
                            allowedViews = parseIDs (parts[1]);
                    }
                    case "mode" -> {
                        if (denied)
                            deniedModes = parseIDs (parts[1]);
                        else
                            allowedModes = parseIDs (parts[1]);
                    }
                    case "workspace" -> workspace = parseBoolean ("workspace", term, parts[1], denied, workspace);
                    case "track" -> {
                        if (denied || trackPosition != null)
                            throw new IllegalArgumentException ("invalid track predicate '" + PushDebugging.sanitize (term) + "'");
                        try
                        {
                            trackPosition = Integer.valueOf (parts[1]);
                        }
                        catch (final NumberFormatException ex)
                        {
                            throw new IllegalArgumentException ("invalid track predicate '" + PushDebugging.sanitize (term) + "'");
                        }
                        if (trackPosition.intValue () < 0)
                            throw new IllegalArgumentException ("invalid track predicate '" + PushDebugging.sanitize (term) + "'");
                    }
                    case "track-id" -> {
                        if (denied || trackID != null || !PushDebugging.isIdentifier (parts[1]))
                            throw new IllegalArgumentException ("invalid track-id predicate '" + PushDebugging.sanitize (term) + "'");
                        trackID = parts[1];
                    }
                    case "repeat" -> repeat = parseBoolean ("repeat", term, parts[1], denied, repeat);
                    case "latch" -> latch = parseBoolean ("latch", term, parts[1], denied, latch);
                    case "armed" -> armed = parseBoolean ("armed", term, parts[1], denied, armed);
                    case "monitor" -> monitor = parseEnum ("monitor", term, parts[1], denied, monitor, SelectedTrackMonitorMode.class);
                    case "route" -> route = parseEnum ("route", term, parts[1], denied, route, NoteRouteExpectation.class);
                    default -> throw new IllegalArgumentException ("unsupported navigation predicate field '" + PushDebugging.sanitize (parts[0]) + "'");
                }
            }
            if (trackPosition != null && trackID == null)
                throw new IllegalArgumentException ("track position requires an exact track-id predicate");
            return new NavigationPredicate (allowedViews, deniedViews, allowedModes, deniedModes, workspace, trackPosition, trackID, repeat, latch, armed, monitor, route, false);
        }


        boolean matches (final ObservedNavigation observed)
        {
            if (this.submissionOnly)
                return false;
            return (this.allowedViews.isEmpty () || this.allowedViews.contains (observed.viewID ())) &&
                !this.deniedViews.contains (observed.viewID ()) &&
                (this.allowedModes.isEmpty () || this.allowedModes.contains (observed.modeID ())) &&
                !this.deniedModes.contains (observed.modeID ()) &&
                (this.workspaceActive == null || this.workspaceActive.booleanValue () == observed.workspaceActive ()) &&
                (this.selectedTrackPosition == null || this.selectedTrackPosition.intValue () == observed.selectedTrackPosition ()) &&
                (this.selectedTrackID == null || this.selectedTrackID.equals (observed.selectedTrackID ())) &&
                (this.noteRepeatActive == null || this.noteRepeatActive.booleanValue () == observed.noteRepeatActive ()) &&
                (this.noteLatchActive == null || this.noteLatchActive.booleanValue () == observed.noteLatchActive ()) &&
                (this.selectedTrackArmed == null || this.selectedTrackArmed.booleanValue () == observed.selectedTrackArmed ()) &&
                (this.selectedTrackMonitorMode == null || this.selectedTrackMonitorMode == observed.selectedTrackMonitorMode ()) &&
                (this.noteRoute == null || routeMatches (this.noteRoute, observed));
        }


        private static boolean routeMatches (final NoteRouteExpectation expectation, final ObservedNavigation observed)
        {
            final ControllerBridge.NotePerformanceState state = observed.notePerformance ();
            if (!state.available () || !observed.selectedTrackCanHoldNotes () || !state.desired ().layout ().equals (state.commandedLayout ()))
                return false;
            if (expectation == NoteRouteExpectation.OFF)
                return !state.desired ().inputRoute ().active () && !state.submittedRoute ().active () && !state.commandedLayout ().isPresent ();

            return routeMatchesTarget (state.desired ().inputRoute (), observed) &&
                routeMatchesTarget (state.submittedRoute (), observed) &&
                state.commandedLayout ().isPresent () &&
                state.commandedLayout ().noteView ().name ().equals (observed.viewID ());
        }


        private static boolean routeMatchesTarget (final DesiredNoteInputRoute route, final ObservedNavigation observed)
        {
            return route.active () && targetMatches (route.targetChannelId (), route.targetGeneration (), observed);
        }


        private static Set<String> parseIDs (final String value)
        {
            final Set<String> identifiers = new HashSet<> ();
            for (final String identifier: value.split ("\\|"))
            {
                if (!PushDebugging.isIdentifier (identifier))
                    throw new IllegalArgumentException ("invalid navigation state identifier '" + PushDebugging.sanitize (identifier) + "'");
                identifiers.add (identifier);
            }
            return Set.copyOf (identifiers);
        }


        private static Boolean parseBoolean (final String field, final String term, final String value, final boolean denied, final Boolean previous)
        {
            if (denied || previous != null || !Set.of ("true", "false").contains (value))
                throw invalidScalar (field, term);
            return Boolean.valueOf (value);
        }


        private static <E extends Enum<E>> E parseEnum (final String field, final String term, final String value, final boolean denied, final E previous, final Class<E> type)
        {
            if (denied || previous != null)
                throw invalidScalar (field, term);
            try
            {
                return Enum.valueOf (type, value);
            }
            catch (final IllegalArgumentException ex)
            {
                throw invalidScalar (field, term);
            }
        }


        private static IllegalArgumentException invalidScalar (final String field, final String term)
        {
            return new IllegalArgumentException ("invalid " + field + " predicate '" + PushDebugging.sanitize (term) + "'");
        }
    }


    private enum NoteRouteExpectation
    {
        ON,
        OFF
    }


    private record PadProbe (int oneBasedPad, int velocity)
    {
        private static PadProbe parseOrNull (final String value)
        {
            if (!value.startsWith ("PAD_OUTPUT_"))
                return null;
            final String [] fields = value.split ("_", -1);
            if (fields.length != 4)
                throw invalid (value);
            try
            {
                final int oneBasedPad = Integer.parseInt (fields[2]);
                final int velocity = Integer.parseInt (fields[3]);
                if (oneBasedPad < 1 || oneBasedPad > 64 || velocity < 1 || velocity > 127)
                    throw invalid (value);
                return new PadProbe (oneBasedPad, velocity);
            }
            catch (final NumberFormatException ex)
            {
                throw invalid (value);
            }
        }


        private static IllegalArgumentException invalid (final String value)
        {
            return new IllegalArgumentException ("invalid pad-output probe '" + PushDebugging.sanitize (value) + "'");
        }


        private ControlId control ()
        {
            return PushControlIds.pad (this.oneBasedPad);
        }


        private ButtonID button ()
        {
            return ButtonID.get (ButtonID.PAD1, this.oneBasedPad - 1);
        }
    }


    private static final class ActivePadProbe
    {
        private final PadProbe probe;
        private final ObservedNavigation context;
        private final long coreGeneration;
        private final long startingAppliedRevision;
        private int stableSamples;
        private int releaseSamples;
        private boolean releasing;
        private long releaseAppliedRevision;
        private PadEvidence stableEvidence;


        private ActivePadProbe (final PadProbe probe, final ObservedNavigation context, final ObservedCoreLight startingLight)
        {
            this.probe = Objects.requireNonNull (probe, "probe");
            this.context = Objects.requireNonNull (context, "context");
            this.coreGeneration = startingLight.coreGeneration ();
            this.startingAppliedRevision = startingLight.appliedRevision ();
        }
    }


    private record PadEvidence (int midiNote, RgbColor desiredColor, int resolvedColor, int resolvedBlinkColor, boolean fast, PushControlSurface.DebugPadTransmission base, PushControlSurface.DebugPadTransmission blink)
    {
    }


    private record PadStatus (InputRouteMode route, boolean mappingDesired, boolean mappingActive, Boolean mappedOn)
    {
        private boolean exclusiveRoute ()
        {
            return this.route == InputRouteMode.EXCLUSIVE;
        }


        private boolean ready ()
        {
            return this.exclusiveRoute () && this.mappingDesired && this.mappingActive && this.mappedAvailable ();
        }


        private boolean mappedAvailable ()
        {
            return this.mappedOn != null;
        }


        private String routeName ()
        {
            return this.route == null ? "NONE" : this.route.name ();
        }
    }


    private record Step (NavigationGesture gesture, PadProbe padProbe, NavigationPredicate postcondition)
    {
        static Step parse (final String value)
        {
            final String [] fields = value.split ("/", 2);
            if (fields.length != 2)
                throw new IllegalArgumentException ("invalid navigation step '" + PushDebugging.sanitize (value) + "'");
            final NavigationPredicate predicate = NavigationPredicate.parse (fields[1]);
            final PadProbe padProbe = PadProbe.parseOrNull (fields[0]);
            if (padProbe != null)
            {
                if (predicate.allowedViews ().size () != 1 || predicate.allowedModes ().size () != 1 || predicate.workspaceActive () == null)
                    throw new IllegalArgumentException ("pad-output probe requires one exact view, mode, and workspace predicate");
                return new Step (null, padProbe, predicate);
            }
            return new Step (NavigationGesture.parse (fields[0]), null, predicate);
        }
    }


    private static final class PendingNavigation
    {
        private final String              requestID;
        private final String              label;
        private final List<Step>          steps;
        private final NavigationPredicate finalPredicate;
        private final Set<ButtonID>       controls;
        private int                       tick;
        private int                       stableSamples;
        private int                       stepIndex;
        private boolean                   stepSubmitted;
        private ObservedNavigation        stableObservation;
        private ActivePadProbe            activePadProbe;
        private PadProbe                  completedPadProbe;
        private PadStatus                 padStatus;
        private PadEvidence               padEvidence;


        private PendingNavigation (final String requestID, final String label, final List<Step> steps)
        {
            this.requestID = requestID;
            this.label = label;
            this.steps = steps;
            this.finalPredicate = steps.get (steps.size () - 1).postcondition ();
            final Set<ButtonID> allControls = new HashSet<> ();
            for (final Step step: steps)
            {
                if (step.gesture () != null)
                    allControls.addAll (step.gesture ().controls);
                if (step.padProbe () != null)
                    allControls.add (step.padProbe ().button ());
            }
            this.controls = Set.copyOf (allControls);
        }


        private boolean controlsFree (final NavigationSurface surface)
        {
            return this.controls.stream ().noneMatch (surface::isPressed);
        }


        private void nextStep ()
        {
            this.stepIndex++;
            this.stepSubmitted = false;
        }
    }


    private record Incoming (String requestID, String label, List<Step> steps, String failure)
    {
        private static Incoming ready (final String requestID, final String label, final List<Step> steps)
        {
            return new Incoming (requestID, label, steps, "");
        }


        private static Incoming failed (final String requestID, final String label, final String failure)
        {
            return new Incoming (requestID, label, List.of (), PushDebugging.sanitize (failure));
        }
    }


    private record Status (String requestID, String state, String label, ObservedNavigation observed, PadProbe padProbe, PadStatus padStatus, PadEvidence padEvidence, String message)
    {
    }


    private static final class PushNavigationSurface implements NavigationSurface
    {
        private final PushControlSurface                                surface;
        private final Supplier<ControllerBridge.NotePerformanceState> notePerformanceState;
        private final Function<ControlId, ControllerRuntimeEnvironment.DebugLightObservation> lightObservation;


        private PushNavigationSurface (final PushControlSurface surface, final Supplier<ControllerBridge.NotePerformanceState> notePerformanceState, final Function<ControlId, ControllerRuntimeEnvironment.DebugLightObservation> lightObservation)
        {
            this.surface = Objects.requireNonNull (surface, "surface");
            this.notePerformanceState = Objects.requireNonNull (notePerformanceState, "notePerformanceState");
            this.lightObservation = Objects.requireNonNull (lightObservation, "lightObservation");
        }


        @Override
        public ObservedNavigation observe ()
        {
            final Object view = this.surface.getViewManager ().getActiveID ();
            final Object mode = this.surface.getModeManager ().getActiveID ();
            final SelectedTrackNoteTargetSnapshot selectedTrack = this.surface.getAuthoritativeSelectedTrackSnapshot ();
            final de.mossgrabers.framework.daw.midi.INoteRepeat noteRepeat = this.surface.getMidiInput ().getDefaultNoteInput ().getNoteRepeat ();
            return new ObservedNavigation (
                view == null ? "" : view.toString (),
                mode == null ? "" : mode.toString (),
                this.surface.getControllerWorkspaceHost ().isActive (),
                selectedTrack.exists () ? selectedTrack.position () : -1,
                selectedTrack.exists () ? selectedTrack.trackID () : "",
                selectedTrack.generation (),
                selectedTrack.canHoldNotes (),
                noteRepeat.isActive (),
                noteRepeat.isLatchActive (),
                selectedTrack.armed (),
                selectedTrack.monitorMode (),
                this.notePerformanceState.get ());
        }


        @Override
        public boolean isPressed (final ButtonID button)
        {
            return this.requireButton (button).isPressed ();
        }


        @Override
        public void trigger (final ButtonID button, final ButtonEvent event)
        {
            this.requireButton (button).trigger (event);
        }


        @Override
        public void trigger (final ButtonID button, final ButtonEvent event, final double velocity)
        {
            this.requireButton (button).trigger (event, velocity);
        }


        @Override
        public ObservedCoreLight coreLight (final ControlId control)
        {
            final ControllerRuntimeEnvironment.DebugLightObservation observed = this.lightObservation.apply (control);
            return new ObservedCoreLight (observed.coreGeneration (), observed.appliedRevision (), observed.color (), observed.mappingDesired (), observed.mappedOn ());
        }


        @Override
        public void beginPadOutputObservation (final int oneBasedPad)
        {
            this.surface.beginDebugPadObservation (oneBasedPad);
        }


        @Override
        public PushControlSurface.DebugPadOutput padOutput (final int oneBasedPad)
        {
            return this.surface.debugPadOutput (oneBasedPad);
        }


        @Override
        public int resolvePadColor (final RgbColor color)
        {
            return this.surface.resolveDebugPadColor (color);
        }


        @Override
        public void endPadOutputObservation (final int oneBasedPad)
        {
            this.surface.endDebugPadObservation (oneBasedPad);
        }


        private IHwButton requireButton (final ButtonID button)
        {
            final IHwButton hardwareButton = this.surface.getButton (button);
            if (hardwareButton == null || hardwareButton.getCommand () == null)
                throw new IllegalStateException ("Push debug navigation button is unavailable: " + button);
            return hardwareButton;
        }
    }
}
