// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.shell.runtime;

import de.mossgrabers.controller.ableton.push.controller.PushControlSurface;
import de.mossgrabers.framework.controller.ButtonID;
import de.mossgrabers.framework.controller.hardware.IHwButton;
import de.mossgrabers.framework.utils.ButtonEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;


/**
 * Local, bounded debugger navigation for closed-loop Push display development.
 *
 * <p>File transport runs on an owned worker. The controller thread admits each recipe only while
 * the permanent input router and every recipe control are idle, submits it once through the
 * installed input arbitrator, and reports success only after two later authoritative layout
 * samples.</p>
 */
final class PushDebugNavigationHost implements AutoCloseable
{
    static final String REQUEST_FILE = "navigate-request.txt";
    static final String STATUS_FILE  = "navigate-status.txt";

    private static final int  MAX_REQUEST_BYTES       = 256;
    private static final int  REQUIRED_STABLE_SAMPLES = 2;
    private static final int  TIMEOUT_TICKS           = 50;
    private static final long POLL_INTERVAL_MILLIS    = 100;
    private static final long SHUTDOWN_WAIT_MILLIS    = 250;

    private final Path                         requestPath;
    private final Path                         statusPath;
    private final NavigationSurface            surface;
    private final GestureAdmission             admission;
    private final ScheduledExecutorService     worker;
    private final AtomicReference<Incoming>    incoming = new AtomicReference<> ();
    private final AtomicReference<Status>      outgoing = new AtomicReference<> ();
    private final AtomicBoolean                closed = new AtomicBoolean ();

    private PendingNavigation pending;


    PushDebugNavigationHost (final PushControlSurface surface, final GestureAdmission admission)
    {
        this (
            Path.of (System.getProperty ("user.home"), ".drivenbymoss", "pull", "debug"),
            new PushNavigationSurface (surface),
            admission,
            Executors.newSingleThreadScheduledExecutor (task -> {
                final Thread thread = new Thread (task, "Pull debug navigation transport");
                thread.setDaemon (true);
                return thread;
            }));
        this.worker.scheduleWithFixedDelay (this::pollFilesSafely, 0, POLL_INTERVAL_MILLIS, TimeUnit.MILLISECONDS);
    }


    PushDebugNavigationHost (final Path debugDirectory, final NavigationSurface surface)
    {
        this (debugDirectory, surface, DirectAdmission.INSTANCE, null);
    }


    PushDebugNavigationHost (final Path debugDirectory, final NavigationSurface surface, final GestureAdmission admission)
    {
        this (debugDirectory, surface, admission, null);
    }


    private PushDebugNavigationHost (
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
                    this.pending = new PendingNavigation (request.requestID (), request.target ());
                else
                    this.publish (request.requestID (), "FAILED", "", this.surface.observe (), request.failure ());
            }
        }

        if (this.pending != null)
            this.advance ();

        if (this.worker == null)
            this.pollFilesSafely ();
    }


    private void advance ()
    {
        final ObservedNavigation observed = this.surface.observe ();
        if (this.pending.target.matches (observed))
        {
            if (!this.admission.isIdle () || !this.pending.target.controlsFree (this.surface))
            {
                this.pending.stableSamples = 0;
                return;
            }
            this.pending.tick++;
            this.pending.stableSamples++;
            if (this.pending.stableSamples >= REQUIRED_STABLE_SAMPLES)
                this.complete ("READY", "", observed);
            return;
        }

        this.pending.tick++;
        this.pending.stableSamples = 0;
        if (this.pending.tick >= TIMEOUT_TICKS)
        {
            this.complete ("FAILED", "timed out waiting for authoritative controller state", observed);
            return;
        }

        final Runnable gesture = this.pending.target.nextGesture (this.pending.step, this.surface, observed);
        if (gesture == null || !this.pending.target.controlsFree (this.surface))
            return;

        try
        {
            if (this.admission.trySubmit (gesture))
                this.pending.step++;
        }
        catch (final RuntimeException ex)
        {
            this.complete ("FAILED", "could not submit navigation gesture: " + sanitize (ex.getMessage ()), observed);
        }
    }


    private void complete (final String state, final String message, final ObservedNavigation observed)
    {
        final PendingNavigation completed = this.pending;
        this.pending = null;
        this.publish (completed.requestID, state, completed.target.protocolName (), observed, message);
    }


    private void publish (final String requestID, final String state, final String target, final ObservedNavigation observed, final String message)
    {
        this.outgoing.set (new Status (requestID, state, target, observed, message));
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
            return Incoming.failed ("invalid", "request exceeds " + MAX_REQUEST_BYTES + " bytes");
        }

        final String request = Files.readString (this.requestPath).strip ();
        Files.deleteIfExists (this.requestPath);
        final String [] fields = request.split ("\\s+", 2);
        if (fields.length != 2 || !isRequestID (fields[0]))
            return Incoming.failed ("invalid", "expected '<request-id> <target>'");
        try
        {
            return Incoming.ready (fields[0], NavigationTarget.parse (fields[1]));
        }
        catch (final IllegalArgumentException ex)
        {
            return Incoming.failed (fields[0], ex.getMessage ());
        }
    }


    private void writeStatus (final Status status) throws IOException
    {
        Files.createDirectories (this.statusPath.getParent ());
        final ObservedNavigation observed = status.observed ();
        final String content = String.join ("\t",
            status.requestID (),
            status.state (),
            status.target (),
            observed.viewID (),
            observed.modeID (),
            Boolean.toString (observed.workspaceActive ()),
            sanitize (status.message ())) + "\n";
        final Path temporaryStatus = this.statusPath.resolveSibling (this.statusPath.getFileName () + ".tmp");
        Files.writeString (temporaryStatus, content);
        try
        {
            Files.move (temporaryStatus, this.statusPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        }
        catch (final IOException ex)
        {
            Files.move (temporaryStatus, this.statusPath, StandardCopyOption.REPLACE_EXISTING);
        }
    }


    @Override
    public void close ()
    {
        if (!this.closed.compareAndSet (false, true))
            return;

        final ObservedNavigation observed = this.surface.observe ();
        if (this.pending != null)
        {
            this.publish (this.pending.requestID, "FAILED", this.pending.target.protocolName (), observed, "extension is closing");
            this.pending = null;
        }
        final Incoming queued = this.incoming.getAndSet (null);
        if (queued != null)
            this.publish (queued.requestID (), "FAILED", queued.target () == null ? "" : queued.target ().protocolName (), observed, "extension is closing");

        if (this.worker == null)
        {
            this.pollFilesSafely ();
            return;
        }

        this.worker.execute (this::pollFilesSafely);
        this.worker.shutdown ();
        try
        {
            if (!this.worker.awaitTermination (SHUTDOWN_WAIT_MILLIS, TimeUnit.MILLISECONDS))
                this.worker.shutdownNow ();
        }
        catch (final InterruptedException ex)
        {
            this.worker.shutdownNow ();
            Thread.currentThread ().interrupt ();
        }
    }


    private static boolean isRequestID (final String value)
    {
        if (value.isEmpty () || value.length () > 80)
            return false;
        for (int index = 0; index < value.length (); index++)
        {
            final char character = value.charAt (index);
            if (!Character.isLetterOrDigit (character) && character != '.' && character != '_' && character != '-')
                return false;
        }
        return true;
    }


    private static String sanitize (final String value)
    {
        return value == null ? "" : value.replace ('\t', ' ').replace ('\r', ' ').replace ('\n', ' ');
    }


    interface GestureAdmission
    {
        boolean isIdle ();

        boolean trySubmit (Runnable gesture);


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


    record ObservedNavigation (String viewID, String modeID, boolean workspaceActive)
    {
        ObservedNavigation
        {
            viewID = Objects.requireNonNull (viewID, "viewID");
            modeID = Objects.requireNonNull (modeID, "modeID");
        }
    }


    private enum NavigationTarget
    {
        MIX (ButtonID.NOTE, ButtonID.TRACK, ButtonID.SHIFT),
        MASTER (ButtonID.MASTERTRACK, ButtonID.SHIFT),
        PROJECT_MACROS (ButtonID.SHIFT, ButtonID.SESSION),
        SESSION (ButtonID.SESSION, ButtonID.SHIFT);

        private final Set<ButtonID> controls;


        NavigationTarget (final ButtonID... controls)
        {
            this.controls = Set.copyOf (EnumSet.of (controls[0], controls));
        }


        String protocolName ()
        {
            return this.name ().toLowerCase (Locale.ROOT).replace ('_', '-');
        }


        boolean matches (final ObservedNavigation observed)
        {
            return switch (this)
            {
                case MIX -> !observed.workspaceActive () && "TRACK".equals (observed.modeID ());
                case MASTER -> "MASTER".equals (observed.modeID ()) || "MASTER_TEMP".equals (observed.modeID ());
                case PROJECT_MACROS -> observed.workspaceActive () && "WORKSPACE".equals (observed.modeID ()) && "WORKSPACE".equals (observed.viewID ());
                case SESSION -> !observed.workspaceActive () && "SESSION".equals (observed.viewID ()) &&
                    !"WORKSPACE".equals (observed.modeID ()) && !"MASTER".equals (observed.modeID ()) && !"MASTER_TEMP".equals (observed.modeID ());
            };
        }


        boolean controlsFree (final NavigationSurface surface)
        {
            return this.controls.stream ().noneMatch (surface::isPressed);
        }


        Runnable nextGesture (final int step, final NavigationSurface surface, final ObservedNavigation observed)
        {
            return switch (this)
            {
                case MIX -> {
                    if (observed.workspaceActive () && step == 0)
                        yield () -> surface.click (ButtonID.NOTE);
                    if (!observed.workspaceActive () && step <= 1)
                        yield () -> surface.click (ButtonID.TRACK);
                    yield null;
                }
                case MASTER -> step == 0 ? () -> surface.click (ButtonID.MASTERTRACK) : null;
                case PROJECT_MACROS -> step == 0 ? () -> chord (surface, ButtonID.SHIFT, ButtonID.SESSION) : null;
                case SESSION -> step == 0 ? () -> surface.click (ButtonID.SESSION) : null;
            };
        }


        static NavigationTarget parse (final String value)
        {
            final String normalized = Objects.requireNonNull (value, "value").strip ().toUpperCase (Locale.ROOT).replace ('-', '_');
            try
            {
                return valueOf (normalized);
            }
            catch (final IllegalArgumentException ex)
            {
                throw new IllegalArgumentException ("unsupported target '" + sanitize (value) + "' (expected mix, master, project-macros, or session)");
            }
        }


        private static void chord (final NavigationSurface surface, final ButtonID modifier, final ButtonID button)
        {
            try
            {
                surface.trigger (modifier, ButtonEvent.DOWN);
                surface.click (button);
            }
            finally
            {
                surface.trigger (modifier, ButtonEvent.UP);
            }
        }
    }


    private static final class PendingNavigation
    {
        private final String           requestID;
        private final NavigationTarget target;
        private int                    tick;
        private int                    stableSamples;
        private int                    step;


        private PendingNavigation (final String requestID, final NavigationTarget target)
        {
            this.requestID = requestID;
            this.target = target;
        }
    }


    private record Incoming (String requestID, NavigationTarget target, String failure)
    {
        private static Incoming ready (final String requestID, final NavigationTarget target)
        {
            return new Incoming (requestID, target, "");
        }


        private static Incoming failed (final String requestID, final String failure)
        {
            return new Incoming (requestID, null, sanitize (failure));
        }
    }


    private record Status (String requestID, String state, String target, ObservedNavigation observed, String message)
    {
    }


    private static final class PushNavigationSurface implements NavigationSurface
    {
        private final PushControlSurface surface;


        private PushNavigationSurface (final PushControlSurface surface)
        {
            this.surface = Objects.requireNonNull (surface, "surface");
        }


        @Override
        public ObservedNavigation observe ()
        {
            final Object view = this.surface.getViewManager ().getActiveID ();
            final Object mode = this.surface.getModeManager ().getActiveID ();
            return new ObservedNavigation (
                view == null ? "" : view.toString (),
                mode == null ? "" : mode.toString (),
                this.surface.getControllerWorkspaceHost ().isActive ());
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


        private IHwButton requireButton (final ButtonID button)
        {
            final IHwButton hardwareButton = this.surface.getButton (button);
            if (hardwareButton == null || hardwareButton.getCommand () == null)
                throw new IllegalStateException ("Push debug navigation button is unavailable: " + button);
            return hardwareButton;
        }
    }
}
