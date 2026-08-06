// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.shell.runtime;

import de.mossgrabers.controller.ableton.push.controller.PushControlSurface;
import de.mossgrabers.framework.controller.ButtonID;
import de.mossgrabers.framework.controller.hardware.IHwButton;
import de.mossgrabers.framework.utils.ButtonEvent;
import de.mossgrabers.pull.shell.PushDebugging;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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
 * <p>The client supplies a short plan made only from safe navigation gestures and authoritative
 * view predicates. The stable shell validates and executes that generic plan without owning any
 * named-view recipe policy.</p>
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


    static PushDebugNavigationHost createIfEnabled (final PushControlSurface surface, final GestureAdmission admission)
    {
        if (!PushDebugging.isEnabled ())
            return null;

        final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor (task -> {
            final Thread thread = new Thread (task, "Pull debug navigation transport");
            thread.setDaemon (true);
            return thread;
        });
        final PushDebugNavigationHost host = new PushDebugNavigationHost (
            PushDebugging.directory (), new PushNavigationSurface (surface), admission, worker);
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
                    this.pending = new PendingNavigation (request.requestID (), request.label (), request.steps ());
                else
                    this.publish (request.requestID (), "FAILED", request.label (), this.surface.observe (), request.failure ());
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
        this.pending.tick++;
        if (this.pending.tick >= TIMEOUT_TICKS)
        {
            this.complete ("FAILED", "timed out waiting for authoritative controller state", observed);
            return;
        }

        while (this.pending.stepIndex < this.pending.steps.size ())
        {
            final Step step = this.pending.steps.get (this.pending.stepIndex);
            if (step.submitted)
            {
                if (!step.postcondition ().matches (observed))
                    return;
                this.pending.stepIndex++;
                continue;
            }
            if (step.postcondition ().matches (observed))
            {
                this.pending.stepIndex++;
                continue;
            }
            if (!this.admission.isIdle () || !step.gesture ().controlsFree (this.surface))
                return;

            try
            {
                if (this.admission.trySubmit ( () -> step.gesture ().trigger (this.surface)))
                    step.submitted = true;
            }
            catch (final RuntimeException ex)
            {
                this.complete ("FAILED", "could not submit navigation gesture: " + sanitize (ex.getMessage ()), observed);
            }
            return;
        }

        if (!this.pending.finalPredicate.matches (observed) || !this.admission.isIdle () || !this.pending.controlsFree (this.surface))
        {
            this.pending.stableSamples = 0;
            return;
        }
        this.pending.stableSamples++;
        if (this.pending.stableSamples >= REQUIRED_STABLE_SAMPLES)
            this.complete ("READY", "", observed);
    }


    private void complete (final String state, final String message, final ObservedNavigation observed)
    {
        final PendingNavigation completed = this.pending;
        this.pending = null;
        this.publish (completed.requestID, state, completed.label, observed, message);
    }


    private void publish (final String requestID, final String state, final String label, final ObservedNavigation observed, final String message)
    {
        this.outgoing.set (new Status (requestID, state, label, observed, message));
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
        if (fields.length < 3 || fields.length > MAX_STEPS + 2 || !isIdentifier (fields[0]) || !isIdentifier (fields[1]))
            return Incoming.failed ("invalid", "", "expected '<request-id> <label> <step>...'");
        try
        {
            final List<Step> steps = new ArrayList<> (fields.length - 2);
            for (int index = 2; index < fields.length; index++)
                steps.add (Step.parse (fields[index]));
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
        final String content = String.join ("\t",
            status.requestID (),
            status.state (),
            status.label (),
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
            this.publish (this.pending.requestID, "FAILED", this.pending.label, observed, "extension is closing");
            this.pending = null;
        }
        final Incoming queued = this.incoming.getAndSet (null);
        if (queued != null)
            this.publish (queued.requestID (), "FAILED", queued.label (), observed, "extension is closing");

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


    private static boolean isIdentifier (final String value)
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


    private enum NavigationGesture
    {
        NOTE (ButtonID.NOTE),
        TRACK (ButtonID.TRACK),
        MASTERTRACK (ButtonID.MASTERTRACK),
        SESSION (ButtonID.SESSION),
        SHIFT_SESSION (ButtonID.SHIFT, ButtonID.SESSION);

        private final ButtonID      modifier;
        private final ButtonID      button;
        private final Set<ButtonID> controls;


        NavigationGesture (final ButtonID button)
        {
            this (null, button);
        }


        NavigationGesture (final ButtonID modifier, final ButtonID button)
        {
            this.modifier = modifier;
            this.button = button;
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
                throw new IllegalArgumentException ("unsupported navigation gesture '" + sanitize (value) + "'");
            }
        }


        boolean controlsFree (final NavigationSurface surface)
        {
            return this.controls.stream ().noneMatch (surface::isPressed);
        }


        void trigger (final NavigationSurface surface)
        {
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


    private record NavigationPredicate (
        Set<String> allowedViews,
        Set<String> deniedViews,
        Set<String> allowedModes,
        Set<String> deniedModes,
        Boolean workspaceActive)
    {
        private static final NavigationPredicate ANY = new NavigationPredicate (Set.of (), Set.of (), Set.of (), Set.of (), null);


        static NavigationPredicate parse (final String value)
        {
            if ("*".equals (value))
                return ANY;

            Set<String> allowedViews = Set.of ();
            Set<String> deniedViews = Set.of ();
            Set<String> allowedModes = Set.of ();
            Set<String> deniedModes = Set.of ();
            Boolean workspace = null;
            for (final String term: value.split (","))
            {
                final boolean denied = term.contains ("!=");
                final String [] parts = term.split (denied ? "!=" : "=", 2);
                if (parts.length != 2 || parts[1].isEmpty ())
                    throw new IllegalArgumentException ("invalid navigation predicate '" + sanitize (value) + "'");
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
                    case "workspace" -> {
                        if (denied || workspace != null || !Set.of ("true", "false").contains (parts[1]))
                            throw new IllegalArgumentException ("invalid workspace predicate '" + sanitize (term) + "'");
                        workspace = Boolean.valueOf (parts[1]);
                    }
                    default -> throw new IllegalArgumentException ("unsupported navigation predicate field '" + sanitize (parts[0]) + "'");
                }
            }
            return new NavigationPredicate (allowedViews, deniedViews, allowedModes, deniedModes, workspace);
        }


        boolean matches (final ObservedNavigation observed)
        {
            return (this.allowedViews.isEmpty () || this.allowedViews.contains (observed.viewID ())) &&
                !this.deniedViews.contains (observed.viewID ()) &&
                (this.allowedModes.isEmpty () || this.allowedModes.contains (observed.modeID ())) &&
                !this.deniedModes.contains (observed.modeID ()) &&
                (this.workspaceActive == null || this.workspaceActive.booleanValue () == observed.workspaceActive ());
        }


        private static Set<String> parseIDs (final String value)
        {
            final Set<String> identifiers = new HashSet<> ();
            for (final String identifier: value.split ("\\|"))
            {
                if (!isIdentifier (identifier))
                    throw new IllegalArgumentException ("invalid navigation state identifier '" + sanitize (identifier) + "'");
                identifiers.add (identifier);
            }
            return Set.copyOf (identifiers);
        }
    }


    private static final class Step
    {
        private final NavigationGesture   gesture;
        private final NavigationPredicate postcondition;
        private boolean                   submitted;


        private Step (final NavigationGesture gesture, final NavigationPredicate postcondition)
        {
            this.gesture = gesture;
            this.postcondition = postcondition;
        }


        static Step parse (final String value)
        {
            final String [] fields = value.split ("/", 2);
            if (fields.length != 2)
                throw new IllegalArgumentException ("invalid navigation step '" + sanitize (value) + "'");
            return new Step (
                NavigationGesture.parse (fields[0]),
                NavigationPredicate.parse (fields[1]));
        }


        NavigationGesture gesture ()
        {
            return this.gesture;
        }


        NavigationPredicate postcondition ()
        {
            return this.postcondition;
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


        private PendingNavigation (final String requestID, final String label, final List<Step> steps)
        {
            this.requestID = requestID;
            this.label = label;
            this.steps = steps;
            this.finalPredicate = steps.get (steps.size () - 1).postcondition ();
            final Set<ButtonID> allControls = new HashSet<> ();
            for (final Step step: steps)
                allControls.addAll (step.gesture ().controls);
            this.controls = Set.copyOf (allControls);
        }


        private boolean controlsFree (final NavigationSurface surface)
        {
            return this.controls.stream ().noneMatch (surface::isPressed);
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
            return new Incoming (requestID, label, List.of (), sanitize (failure));
        }
    }


    private record Status (String requestID, String state, String label, ObservedNavigation observed, String message)
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
