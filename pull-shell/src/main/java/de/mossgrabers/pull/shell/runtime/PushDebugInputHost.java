// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.shell.runtime;

import de.mossgrabers.controller.ableton.push.controller.PushControlSurface;
import de.mossgrabers.framework.controller.ButtonID;
import de.mossgrabers.framework.controller.ContinuousID;
import de.mossgrabers.framework.controller.hardware.IHwButton;
import de.mossgrabers.framework.controller.hardware.IHwContinuousControl;
import de.mossgrabers.framework.utils.ButtonEvent;
import de.mossgrabers.pull.core.api.ControlId;
import de.mossgrabers.pull.core.api.PushControlIds;
import de.mossgrabers.pull.shell.PushDebugging;
import de.mossgrabers.pull.shell.input.InputKind;
import de.mossgrabers.pull.shell.input.InputPhase;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;
import java.util.stream.Stream;


/** Bounded local browser ingress which reuses the permanent Push input bindings and arbitrator. */
final class PushDebugInputHost implements AutoCloseable
{
    static final String INFO_FILE          = "surface-input-info.json";
    static final String STATUS_FILE        = "surface-input-status.json";
    static final String REQUEST_DIRECTORY = "surface-input-requests";

    private static final String REQUEST_PREFIX = "input-";
    private static final String REQUEST_SUFFIX = ".txt";
    private static final int MAX_REQUEST_BYTES = 512;
    private static final int MAX_QUEUED_REQUESTS = 64;
    private static final int MAX_REQUESTS_PER_TICK = 16;
    private static final long POLL_INTERVAL_MILLIS = 20;
    private static final long ACTIVE_LEASE_NANOS = TimeUnit.SECONDS.toNanos (5);

    private final Path requestDirectory;
    private final Path infoPath;
    private final Path statusPath;
    private final String session;
    private final InputSurface surface;
    private final PushDebugNavigationHost.GestureAdmission admission;
    private final ScheduledExecutorService worker;
    private final LongSupplier nanoTime;
    private final ConcurrentLinkedQueue<Incoming> incoming = new ConcurrentLinkedQueue<> ();
    private final AtomicInteger queuedRequests = new AtomicInteger ();
    private final AtomicReference<Status> outgoing = new AtomicReference<> ();
    private final AtomicBoolean initialized = new AtomicBoolean ();
    private final AtomicBoolean closed = new AtomicBoolean ();
    private final AtomicBoolean closedInfoWritten = new AtomicBoolean ();

    private ActiveEdge active;
    private ControlId pressuredPad;
    private long pressureExpiresAtNanos;


    static PushDebugInputHost createIfEnabled (final PushControlSurface surface, final PushDebugNavigationHost.GestureAdmission admission, final PadControllerInput padControllerInput)
    {
        if (!PushDebugging.isEnabled ())
            return null;

        final ScheduledExecutorService worker = PushDebugging.createWorker ("Pull debug browser input transport");
        final PushDebugInputHost host = new PushDebugInputHost (
            PushDebugging.directory (),
            new PushInputSurface (surface, padControllerInput),
            admission,
            worker,
            System::nanoTime);
        worker.scheduleWithFixedDelay (host::pollFilesSafely, 0, POLL_INTERVAL_MILLIS, TimeUnit.MILLISECONDS);
        return host;
    }


    PushDebugInputHost (final Path debugDirectory, final InputSurface surface, final PushDebugNavigationHost.GestureAdmission admission, final LongSupplier nanoTime)
    {
        this (debugDirectory, surface, admission, null, nanoTime);
    }


    private PushDebugInputHost (final Path debugDirectory, final InputSurface surface, final PushDebugNavigationHost.GestureAdmission admission, final ScheduledExecutorService worker, final LongSupplier nanoTime)
    {
        final Path directory = Objects.requireNonNull (debugDirectory, "debugDirectory");
        this.requestDirectory = directory.resolve (REQUEST_DIRECTORY);
        this.infoPath = directory.resolve (INFO_FILE);
        this.statusPath = directory.resolve (STATUS_FILE);
        this.session = UUID.randomUUID ().toString ().replace ("-", "");
        this.surface = Objects.requireNonNull (surface, "surface");
        this.admission = Objects.requireNonNull (admission, "admission");
        this.worker = worker;
        this.nanoTime = Objects.requireNonNull (nanoTime, "nanoTime");
    }


    /** Drain browser requests on Bitwig's controller thread. */
    void tick ()
    {
        if (this.closed.get ())
            return;
        if (this.worker == null)
            this.pollFilesSafely ();

        this.expireOrCompleteActive ();
        this.expirePressure ();
        final List<Incoming> requests = new ArrayList<> (MAX_REQUESTS_PER_TICK);
        final Map<ControlId, Integer> pendingPressure = new LinkedHashMap<> ();
        final Map<ControlId, Integer> pendingRelative = new LinkedHashMap<> ();
        for (int count = 0; count < MAX_REQUESTS_PER_TICK; count++)
        {
            final Incoming request = this.incoming.poll ();
            if (request == null)
                break;
            this.queuedRequests.decrementAndGet ();
            if (request.kind () == InputKind.POLY_PRESSURE)
            {
                pendingRelative.clear ();
                final Integer existing = pendingPressure.putIfAbsent (request.control (), requests.size ());
                if (existing == null)
                    requests.add (request);
                else
                    requests.set (existing.intValue (), request);
            }
            else if (request.kind () == InputKind.RELATIVE)
            {
                pendingPressure.clear ();
                final Integer existing = pendingRelative.putIfAbsent (request.control (), requests.size ());
                if (existing == null)
                    requests.add (request);
                else
                {
                    final Incoming previous = requests.get (existing.intValue ());
                    requests.set (existing.intValue (), new Incoming (
                        request.session (),
                        request.requestID (),
                        request.control (),
                        request.kind (),
                        request.phase (),
                        previous.value () + request.value ()));
                }
            }
            else
            {
                pendingPressure.clear ();
                pendingRelative.clear ();
                requests.add (request);
            }
        }
        for (final Incoming request: requests)
            this.handle (request);

        if (this.worker == null)
            this.pollFilesSafely ();
    }


    /** Release a browser-owned edge before an input-route or core-generation invalidation. */
    void cancelActive (final String reason)
    {
        final ActiveEdge owned = this.active;
        if (owned != null)
            this.releaseActive (owned, "RELEASED", Objects.requireNonNull (reason, "reason"));
        else
            this.neutralizePressureBestEffort ();
    }


    String sessionForTest ()
    {
        return this.session;
    }


    private void handle (final Incoming request)
    {
        if (!this.session.equals (request.session ()))
        {
            this.fail (request, "debug input session is stale");
            return;
        }
        if (request.phase () == DebugPhase.KEEPALIVE)
        {
            if (this.active == null || this.active.releasing || !this.active.matches (request))
            {
                this.fail (request, "no matching browser input is held");
                return;
            }
            this.active.expiresAtNanos = Math.addExact (this.nanoTime.getAsLong (), ACTIVE_LEASE_NANOS);
            this.succeed (request);
            return;
        }
        if (!validShape (request) || !this.surface.supports (request.control (), request.kind ()))
        {
            this.fail (request, "input is not installed for that control and kind");
            return;
        }

        if (request.kind () == InputKind.POLY_PRESSURE)
        {
            this.handlePressure (request);
            return;
        }
        if (request.kind () == InputKind.RELATIVE)
        {
            this.handleRelative (request);
            return;
        }
        if (request.phase () == DebugPhase.BEGIN)
        {
            this.beginEdge (request);
            return;
        }
        this.endEdge (request);
    }


    private void beginEdge (final Incoming request)
    {
        if (this.active != null || this.surface.isActive (request.control (), request.kind ()))
        {
            this.fail (request, "another physical or browser input is already held");
            return;
        }

        final ActiveEdge candidate = new ActiveEdge (request.requestID (), request.control (), request.kind (), Math.addExact (this.nanoTime.getAsLong (), ACTIVE_LEASE_NANOS));
        try
        {
            if (!this.admission.tryBeginDebugInput ( () -> this.triggerEdge (request.control (), request.kind (), InputPhase.BEGIN, request.value ())))
            {
                this.fail (request, "controller input is busy");
                return;
            }
            this.active = candidate;
            this.succeed (request);
        }
        catch (final RuntimeException ex)
        {
            this.fail (request, "could not begin input: " + PushDebugging.sanitize (ex.getMessage ()));
        }
    }


    private void endEdge (final Incoming request)
    {
        final ActiveEdge owned = this.active;
        if (owned == null || owned.releasing || !owned.matches (request))
        {
            this.fail (request, "no matching browser input is held");
            return;
        }

        try
        {
            this.neutralizePressure (request.control ());
            this.admission.endDebugInput ( () -> this.triggerEdge (request.control (), request.kind (), InputPhase.END, 0));
            owned.releasing = true;
            this.succeed (request);
            this.expireOrCompleteActive ();
        }
        catch (final RuntimeException ex)
        {
            this.releaseActive (owned, "FAILED", "could not end input: " + PushDebugging.sanitize (ex.getMessage ()));
        }
    }


    private void handlePressure (final Incoming request)
    {
        final ActiveEdge owned = this.active;
        try
        {
            if (this.pressuredPad != null && !this.pressuredPad.equals (request.control ()))
                this.neutralizePressure (this.pressuredPad);
            if (owned != null)
            {
                if (owned.releasing || owned.kind != InputKind.PAD || !owned.control.equals (request.control ()))
                {
                    this.fail (request, "pressure is fenced to the browser-held pad");
                    return;
                }
                this.triggerPressure (request.control (), request.value ());
                this.rememberPressure (request.control (), request.value ());
                this.succeed (request);
                return;
            }
            if (!this.admission.trySubmit ( () -> this.triggerPressure (request.control (), request.value ())))
            {
                this.fail (request, "controller input is busy");
                return;
            }
            this.rememberPressure (request.control (), request.value ());
            this.succeed (request);
        }
        catch (final RuntimeException ex)
        {
            this.fail (request, "could not apply pressure: " + PushDebugging.sanitize (ex.getMessage ()));
        }
    }


    private void handleRelative (final Incoming request)
    {
        final ActiveEdge owned = this.active;
        try
        {
            if (request.value () == 0)
            {
                this.succeed (request);
                return;
            }
            if (owned != null)
            {
                if (owned.releasing || owned.kind != InputKind.TOUCH || !owned.control.equals (request.control ()))
                {
                    this.fail (request, "relative motion is fenced to the browser-touched control");
                    return;
                }
                this.surface.trigger (request.control (), InputKind.RELATIVE, InputPhase.CHANGE, request.value ());
                this.succeed (request);
                return;
            }
            if (!this.admission.trySubmit ( () -> this.surface.trigger (request.control (), InputKind.RELATIVE, InputPhase.CHANGE, request.value ())))
            {
                this.fail (request, "controller input is busy");
                return;
            }
            this.succeed (request);
        }
        catch (final RuntimeException ex)
        {
            this.fail (request, "could not turn control: " + PushDebugging.sanitize (ex.getMessage ()));
        }
    }


    private void expireOrCompleteActive ()
    {
        final ActiveEdge owned = this.active;
        if (owned == null)
            return;
        if (owned.releasing)
        {
            if (this.admission.debugInputRouteIdle ())
            {
                this.admission.completeDebugInput ();
                this.active = null;
            }
            return;
        }
        if (this.nanoTime.getAsLong () >= owned.expiresAtNanos)
            this.releaseActive (owned, "RELEASED", "browser input lease expired");
    }


    private void expirePressure ()
    {
        if (this.pressuredPad == null)
            return;
        final ActiveEdge owned = this.active;
        if (owned != null && !owned.releasing && owned.kind == InputKind.PAD && owned.control.equals (this.pressuredPad))
            return;
        if (this.nanoTime.getAsLong () < this.pressureExpiresAtNanos)
            return;
        if (this.active != null)
        {
            this.neutralizePressureBestEffort ();
            return;
        }
        try
        {
            this.admission.trySubmit (this::neutralizePressureBestEffort);
        }
        catch (final RuntimeException ignored)
        {
            // A failed debug-only neutralization must never escape onto Bitwig's controller tick.
        }
    }


    private void releaseActive (final ActiveEdge owned, final String state, final String message)
    {
        try
        {
            this.neutralizePressureBestEffort ();
            if (!owned.releasing)
                this.admission.endDebugInput ( () -> this.triggerEdge (owned.control, owned.kind, InputPhase.END, 0));
        }
        catch (final RuntimeException ignored)
        {
            // Releasing the exact established control is best-effort during failure or shutdown.
        }
        finally
        {
            this.admission.completeDebugInput ();
            this.active = null;
        }
        this.outgoing.set (new Status (owned.requestID, state, owned.control.value (), owned.kind.name (), "END", 0, message));
    }


    private void triggerEdge (final ControlId control, final InputKind kind, final InputPhase phase, final int value)
    {
        if (kind != InputKind.PAD)
        {
            this.surface.trigger (control, kind, phase, value);
            return;
        }

        if (phase == InputPhase.BEGIN)
        {
            this.surface.trigger (control, kind, phase, value);
            try
            {
                this.surface.triggerNoteInput (control, kind, phase, value);
            }
            catch (final RuntimeException failure)
            {
                try
                {
                    this.surface.trigger (control, kind, InputPhase.END, 0);
                }
                catch (final RuntimeException cleanupFailure)
                {
                    failure.addSuppressed (cleanupFailure);
                }
                try
                {
                    this.surface.triggerNoteInput (control, kind, InputPhase.END, 0);
                }
                catch (final RuntimeException cleanupFailure)
                {
                    failure.addSuppressed (cleanupFailure);
                }
                throw failure;
            }
            return;
        }

        RuntimeException failure = null;
        try
        {
            this.surface.trigger (control, kind, phase, value);
        }
        catch (final RuntimeException ex)
        {
            failure = ex;
        }
        try
        {
            this.surface.triggerNoteInput (control, kind, phase, value);
        }
        catch (final RuntimeException ex)
        {
            if (failure == null)
                failure = ex;
            else
                failure.addSuppressed (ex);
        }
        if (failure != null)
            throw failure;
    }


    private void triggerPressure (final ControlId control, final int value)
    {
        this.surface.trigger (control, InputKind.POLY_PRESSURE, InputPhase.CHANGE, value);
        try
        {
            this.surface.triggerNoteInput (control, InputKind.POLY_PRESSURE, InputPhase.CHANGE, value);
        }
        catch (final RuntimeException failure)
        {
            try
            {
                this.surface.trigger (control, InputKind.POLY_PRESSURE, InputPhase.CHANGE, 0);
                this.surface.triggerNoteInput (control, InputKind.POLY_PRESSURE, InputPhase.CHANGE, 0);
            }
            catch (final RuntimeException cleanupFailure)
            {
                failure.addSuppressed (cleanupFailure);
            }
            throw failure;
        }
    }


    private void rememberPressure (final ControlId control, final int value)
    {
        if (value == 0)
        {
            this.pressuredPad = null;
            this.pressureExpiresAtNanos = 0;
            return;
        }
        this.pressuredPad = control;
        this.pressureExpiresAtNanos = Math.addExact (this.nanoTime.getAsLong (), ACTIVE_LEASE_NANOS);
    }


    private void neutralizePressure (final ControlId control)
    {
        if (this.pressuredPad == null || !this.pressuredPad.equals (control))
            return;
        try
        {
            this.triggerPressure (control, 0);
        }
        finally
        {
            this.pressuredPad = null;
            this.pressureExpiresAtNanos = 0;
        }
    }


    private void neutralizePressureBestEffort ()
    {
        final ControlId control = this.pressuredPad;
        if (control == null)
            return;
        try
        {
            this.neutralizePressure (control);
        }
        catch (final RuntimeException ignored)
        {
            // The exact debug-owned pressure state has been retired; terminal cleanup is best-effort.
        }
    }


    private void succeed (final Incoming request)
    {
        this.outgoing.set (Status.from (request, "APPLIED", ""));
    }


    private void fail (final Incoming request, final String message)
    {
        this.outgoing.set (Status.from (request, "FAILED", message));
    }


    private void pollFilesSafely ()
    {
        try
        {
            if (this.initialized.compareAndSet (false, true))
                this.initializeFiles ();
            final Status status = this.outgoing.getAndSet (null);
            if (status != null)
                this.writeStatus (status);
            if (this.closed.get ())
            {
                if (this.closedInfoWritten.compareAndSet (false, true))
                {
                    this.clearRequestFiles ();
                    this.writeInfo (false);
                }
                return;
            }
            this.readRequests ();
        }
        catch (final IOException | RuntimeException ignored)
        {
            // The local page reports a timeout; browser transport failure cannot affect input.
        }
    }


    private void initializeFiles () throws IOException
    {
        Files.createDirectories (this.infoPath.getParent ());
        if (Files.exists (this.requestDirectory, LinkOption.NOFOLLOW_LINKS))
        {
            if (Files.isSymbolicLink (this.requestDirectory) || !Files.isDirectory (this.requestDirectory, LinkOption.NOFOLLOW_LINKS))
                throw new IOException ("debug input queue is not a directory");
        }
        else
            Files.createDirectory (this.requestDirectory);
        this.clearRequestFiles ();
        Files.deleteIfExists (this.statusPath);
        this.writeInfo (true);
    }


    private void readRequests () throws IOException
    {
        final int available = MAX_QUEUED_REQUESTS - this.queuedRequests.get ();
        if (available <= 0)
            return;

        final List<Path> paths;
        try (Stream<Path> stream = Files.list (this.requestDirectory))
        {
            paths = stream
                .filter (PushDebugInputHost::isRequestFile)
                .sorted (Comparator.comparing (path -> path.getFileName ().toString ()))
                .limit (available)
                .toList ();
        }
        for (final Path path: paths)
        {
            final Incoming request = this.readRequest (path);
            if (request != null)
            {
                this.incoming.add (request);
                this.queuedRequests.incrementAndGet ();
            }
        }
    }


    private Incoming readRequest (final Path path) throws IOException
    {
        try
        {
            if (Files.size (path) > MAX_REQUEST_BYTES)
            {
                this.outgoing.set (Status.invalid ("request exceeds " + MAX_REQUEST_BYTES + " bytes"));
                return null;
            }
            final String [] fields = Files.readString (path).strip ().split ("\\t", -1);
            if (fields.length != 6 || !PushDebugging.isIdentifier (fields[0]) || !PushDebugging.isIdentifier (fields[1]) || !validControl (fields[2]))
            {
                this.outgoing.set (Status.invalid ("invalid browser input request"));
                return null;
            }
            final InputKind kind = InputKind.valueOf (fields[3]);
            final DebugPhase phase = DebugPhase.valueOf (fields[4]);
            final int value = Integer.parseInt (fields[5]);
            if (kind == InputKind.RELATIVE && (value == 0 || value < -63 || value > 63))
                throw new IllegalArgumentException ("relative value must be -63..-1 or 1..63");
            if (kind != InputKind.RELATIVE && (value < 0 || value > 127))
                throw new IllegalArgumentException ("value must be 0..127");
            return new Incoming (fields[0], fields[1], new ControlId (fields[2]), kind, phase, value);
        }
        catch (final IllegalArgumentException ex)
        {
            this.outgoing.set (Status.invalid (PushDebugging.sanitize (ex.getMessage ())));
            return null;
        }
        finally
        {
            Files.deleteIfExists (path);
        }
    }


    private void clearRequestFiles () throws IOException
    {
        if (!Files.isDirectory (this.requestDirectory, LinkOption.NOFOLLOW_LINKS))
            return;
        final List<Path> paths;
        try (Stream<Path> stream = Files.list (this.requestDirectory))
        {
            paths = stream.filter (PushDebugInputHost::isRequestFile).toList ();
        }
        for (final Path path: paths)
            Files.deleteIfExists (path);
    }


    private void writeInfo (final boolean connected) throws IOException
    {
        final String content = "{\"connected\":" + connected + ",\"session\":\"" + this.session + "\"}\n";
        writeAtomically (this.infoPath, content);
    }


    private void writeStatus (final Status status) throws IOException
    {
        final StringBuilder json = new StringBuilder (256);
        json.append ("{\"requestId\":");
        appendString (json, status.requestID ());
        json.append (",\"state\":");
        appendString (json, status.state ());
        json.append (",\"control\":");
        appendString (json, status.control ());
        json.append (",\"kind\":");
        appendString (json, status.kind ());
        json.append (",\"phase\":");
        appendString (json, status.phase ());
        json.append (",\"value\":").append (status.value ());
        json.append (",\"message\":");
        appendString (json, status.message ());
        writeAtomically (this.statusPath, json.append ("}\n").toString ());
    }


    private static void writeAtomically (final Path path, final String content) throws IOException
    {
        final Path temporary = path.resolveSibling (path.getFileName () + ".tmp");
        Files.writeString (temporary, content);
        PushDebugging.replaceAtomically (temporary, path);
    }


    private static void appendString (final StringBuilder json, final String value)
    {
        json.append ('\"');
        for (int index = 0; index < value.length (); index++)
        {
            final char character = value.charAt (index);
            if (character == '\"' || character == '\\')
                json.append ('\\');
            json.append (character < 0x20 ? ' ' : character);
        }
        json.append ('\"');
    }


    private static boolean isRequestFile (final Path path)
    {
        final String name = path.getFileName ().toString ();
        return name.startsWith (REQUEST_PREFIX) && name.endsWith (REQUEST_SUFFIX) && !Files.isSymbolicLink (path) && Files.isRegularFile (path, LinkOption.NOFOLLOW_LINKS);
    }


    private static boolean validControl (final String value)
    {
        return value.startsWith ("push.") && PushDebugging.isIdentifier (value);
    }


    private static boolean validShape (final Incoming request)
    {
        if (request.kind () == InputKind.POLY_PRESSURE || request.kind () == InputKind.RELATIVE)
            return request.phase () == DebugPhase.CHANGE;
        return (request.kind () == InputKind.BUTTON || request.kind () == InputKind.PAD || request.kind () == InputKind.TOUCH) &&
            (request.phase () == DebugPhase.BEGIN || request.phase () == DebugPhase.END);
    }


    @Override
    public void close ()
    {
        if (!this.closed.compareAndSet (false, true))
            return;
        this.cancelActive ("extension is closing");
        this.incoming.clear ();
        this.queuedRequests.set (0);
        if (this.worker == null)
            this.pollFilesSafely ();
        else
            PushDebugging.shutdownWorker (this.worker, this::pollFilesSafely);
    }


    interface InputSurface
    {
        boolean supports (ControlId control, InputKind kind);

        boolean isActive (ControlId control, InputKind kind);

        void trigger (ControlId control, InputKind kind, InputPhase phase, int value);

        void triggerNoteInput (ControlId control, InputKind kind, InputPhase phase, int value);
    }


    @FunctionalInterface
    interface PadControllerInput
    {
        void trigger (ControlId control, InputPhase phase, int value);
    }


    private static final class PushInputSurface implements InputSurface
    {
        private final PushControlSurface surface;
        private final PadControllerInput padControllerInput;
        private final Map<ControlId, EdgeControl> edges = new LinkedHashMap<> ();
        private final Map<ControlId, IHwContinuousControl> touches = new LinkedHashMap<> ();
        private final Map<ControlId, IHwContinuousControl> relatives = new LinkedHashMap<> ();


        private PushInputSurface (final PushControlSurface surface, final PadControllerInput padControllerInput)
        {
            this.surface = Objects.requireNonNull (surface, "surface");
            this.padControllerInput = Objects.requireNonNull (padControllerInput, "padControllerInput");
            for (final Map.Entry<ButtonID, IHwButton> entry: surface.getButtons ().entrySet ())
            {
                if (entry.getValue ().getCommand () == null)
                    continue;
                final int padIndex = entry.getKey ().ordinal () - ButtonID.PAD1.ordinal () + 1;
                final InputKind kind = padIndex >= 1 && padIndex <= 64 ? InputKind.PAD : InputKind.BUTTON;
                final ControlId control = kind == InputKind.PAD ? PushControlIds.pad (padIndex) : PushControlIds.button (entry.getKey ().name ());
                this.edges.put (control, new EdgeControl (kind, entry.getValue ()));
            }
            for (final ContinuousID id: ContinuousID.values ())
            {
                final IHwContinuousControl control = surface.getContinuous (id);
                if (control != null && control.getTouchCommand () != null)
                    this.touches.put (PushControlIds.continuous (id.name ()), control);
                if (control != null && control.getCommand () != null)
                    this.relatives.put (PushControlIds.continuous (id.name ()), control);
            }
        }


        @Override
        public boolean supports (final ControlId control, final InputKind kind)
        {
            final EdgeControl edge = this.edges.get (control);
            if (kind == InputKind.BUTTON || kind == InputKind.PAD)
                return edge != null && edge.kind == kind;
            if (kind == InputKind.TOUCH)
                return this.touches.containsKey (control);
            if (kind == InputKind.RELATIVE)
                return this.relatives.containsKey (control);
            return kind == InputKind.POLY_PRESSURE && padIndex (control) > 0;
        }


        @Override
        public boolean isActive (final ControlId control, final InputKind kind)
        {
            if (kind == InputKind.TOUCH)
                return this.touches.get (control).isTouched ();
            final EdgeControl edge = this.edges.get (control);
            return edge != null && edge.button.isPressed ();
        }


        @Override
        public void trigger (final ControlId control, final InputKind kind, final InputPhase phase, final int value)
        {
            if (kind == InputKind.TOUCH)
                this.touches.get (control).triggerTouch (phase == InputPhase.BEGIN);
            else if (kind == InputKind.PAD)
                this.padControllerInput.trigger (control, phase, value);
            else if (kind == InputKind.RELATIVE)
            {
                int remaining = value;
                while (remaining != 0)
                {
                    final int chunk = Math.max (-63, Math.min (63, remaining));
                    this.relatives.get (control).handleValue (chunk / 63.0);
                    remaining -= chunk;
                }
            }
            else if (kind == InputKind.POLY_PRESSURE)
                this.surface.triggerDebugPadPressure (padIndex (control), value);
            else
                this.edges.get (control).button.trigger (phase == InputPhase.BEGIN ? ButtonEvent.DOWN : ButtonEvent.UP, value / 127.0);
            this.surface.observeDebugInput (
                control,
                de.mossgrabers.pull.core.api.event.InputKind.valueOf (kind.name ()),
                PushControllerInputBridge.toCorePhase (phase),
                value);
        }


        @Override
        public void triggerNoteInput (final ControlId control, final InputKind kind, final InputPhase phase, final int value)
        {
            final int status = kind == InputKind.POLY_PRESSURE ? 0xA0 : phase == InputPhase.BEGIN ? 0x90 : 0x80;
            this.surface.triggerDebugPadNoteInput (status, padIndex (control), value);
        }


        private static int padIndex (final ControlId control)
        {
            final String prefix = "push.pad.";
            if (!control.value ().startsWith (prefix))
                return -1;
            try
            {
                final int index = Integer.parseInt (control.value ().substring (prefix.length ()));
                return index >= 1 && index <= 64 ? index : -1;
            }
            catch (final NumberFormatException ex)
            {
                return -1;
            }
        }


        private record EdgeControl (InputKind kind, IHwButton button)
        {
        }
    }


    private enum DebugPhase
    {
        BEGIN,
        CHANGE,
        END,
        KEEPALIVE
    }


    private record Incoming (String session, String requestID, ControlId control, InputKind kind, DebugPhase phase, int value)
    {
    }


    private record Status (String requestID, String state, String control, String kind, String phase, int value, String message)
    {
        private static Status from (final Incoming request, final String state, final String message)
        {
            return new Status (request.requestID (), state, request.control ().value (), request.kind ().name (), request.phase ().name (), request.value (), PushDebugging.sanitize (message));
        }


        private static Status invalid (final String message)
        {
            return new Status ("invalid", "FAILED", "-", "-", "-", 0, PushDebugging.sanitize (message));
        }
    }


    private static final class ActiveEdge
    {
        private final String requestID;
        private final ControlId control;
        private final InputKind kind;
        private long expiresAtNanos;
        private boolean releasing;


        private ActiveEdge (final String requestID, final ControlId control, final InputKind kind, final long expiresAtNanos)
        {
            this.requestID = requestID;
            this.control = control;
            this.kind = kind;
            this.expiresAtNanos = expiresAtNanos;
        }


        private boolean matches (final Incoming request)
        {
            return this.control.equals (request.control ()) && this.kind == request.kind ();
        }
    }
}
