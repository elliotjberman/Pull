// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.shell.runtime;

import com.bitwig.extension.controller.api.ControllerHost;

import de.mossgrabers.framework.daw.IModel;
import de.mossgrabers.framework.controller.valuechanger.IValueChanger;
import de.mossgrabers.framework.daw.midi.ISelectedTrackNoteTarget;
import de.mossgrabers.framework.daw.midi.MidiShortCallback;
import de.mossgrabers.framework.daw.midi.MidiConstants;
import de.mossgrabers.framework.utils.ButtonEvent;
import de.mossgrabers.controller.ableton.push.controller.PushControlSurface;
import de.mossgrabers.pull.core.api.ControlId;
import de.mossgrabers.pull.core.api.CoreControls;
import de.mossgrabers.pull.core.api.event.ButtonInputEvent;
import de.mossgrabers.pull.core.api.event.ControllerInputEvent;
import de.mossgrabers.pull.core.api.event.CoreEvent;
import de.mossgrabers.pull.shell.input.PhysicalInputEvent;
import de.mossgrabers.pull.core.api.output.RgbColor;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;


/**
 * Stable facade joining the permanent Bitwig/Push graph to the reloadable controller core.
 */
public final class ReloadableControllerRuntime implements AutoCloseable
{
    private static final long SLOW_TICK_NANOS = 10_000_000L;
    private static final long SLOW_TICK_WARNING_INTERVAL_NANOS = 5_000_000_000L;
    private static final int [] FILL_PAD_NOTES =
    {
        48,
        49,
        50,
        51,
        56,
        57,
        58,
        59,
        64,
        65,
        66,
        67
    };
    private static final List<ControlId> FILL_CONTROLS = CoreControls.drumFills ();
    private static final RgbColor OFF = new RgbColor (0, 0, 0);

    private final RuntimeLog log;
    private final ControllerHost controllerHost;

    private SelectedTrackFillClipHost clipHost;
    private DrumFillRuntimeEnvironment environment;
    private CoreReloadSupervisor supervisor;
    private PushControllerInputBridge inputBridge;
    private Predicate<CoreEvent> eventHandler = event -> false;
    private final Set<ControlId> rawReleasedGestures = new HashSet<> ();
    private boolean started;
    private boolean closed;
    private boolean drainingControllerInputs;
    private long lastSlowTickWarningNanos = Long.MIN_VALUE;


    /**
     * Constructor.
     *
     * @param host The stable Bitwig controller host
     */
    public ReloadableControllerRuntime (final ControllerHost host)
    {
        final ControllerHost checkedHost = Objects.requireNonNull (host, "host");
        this.controllerHost = checkedHost;
        this.log = new HostRuntimeLog (checkedHost);
    }


    /**
     * Deterministic shell-test seam which avoids creating a Bitwig host or reload watcher.
     *
     * @param environment The stable runtime environment
     * @param log The runtime log
     * @param eventHandler Active-core event handler
     */
    ReloadableControllerRuntime (final DrumFillRuntimeEnvironment environment, final RuntimeLog log, final Predicate<CoreEvent> eventHandler)
    {
        this.controllerHost = null;
        this.clipHost = null;
        this.environment = Objects.requireNonNull (environment, "environment");
        this.log = Objects.requireNonNull (log, "log");
        this.eventHandler = Objects.requireNonNull (eventHandler, "eventHandler");
    }


    /**
     * Create the permanent fill scanner/actuators and attach the already-created model graph. The
     * setup calls this from inside Bitwig's extension-init phase.
     *
     * @param model The stable model
     * @param selectedTarget Private selection-following target
     * @param noteInputMidiSender Raw MIDI sender for Bitwig's ordinary controller note input
     * @param surface Stable Push surface
     * @param valueChanger Stable value converter
     */
    public void connect (final IModel model, final ISelectedTrackNoteTarget selectedTarget, final MidiShortCallback noteInputMidiSender, final PushControlSurface surface, final IValueChanger valueChanger)
    {
        if (this.closed)
            throw new IllegalStateException ("Reloadable controller runtime is closed");
        if (this.environment != null)
            throw new IllegalStateException ("Reloadable controller runtime is already connected");

        if (this.controllerHost == null)
            throw new IllegalStateException ("Reloadable controller runtime has no Bitwig host");

        this.clipHost = new SelectedTrackFillClipHost (this.controllerHost);
        this.clipHost.connect (Objects.requireNonNull (model, "model"));
        final BoundedControllerBridge controllerBridge = new BoundedControllerBridge (
            model,
            Objects.requireNonNull (selectedTarget, "selectedTarget"),
            Objects.requireNonNull (noteInputMidiSender, "noteInputMidiSender"),
            Objects.requireNonNull (surface, "surface"),
            Objects.requireNonNull (valueChanger, "valueChanger"));
        this.environment = new DrumFillRuntimeEnvironment (this.clipHost, controllerBridge, this.log, System::nanoTime);
        this.supervisor = new CoreReloadSupervisor (this.environment, this.log);
        this.eventHandler = this.supervisor::handle;
    }


    /**
     * Start the loader after the stable controller graph has completed startup.
     */
    public void start ()
    {
        if (this.started || this.closed)
            return;
        if (this.environment == null)
            throw new IllegalStateException ("Reloadable controller runtime is not connected");

        this.environment.refresh ();
        if (this.supervisor != null)
            this.supervisor.start ();
        this.started = true;
    }


    /**
     * Wrap the complete practical Push input set after normal command registration. This changes
     * command behavior only; it does not add duplicate Bitwig hardware bindings.
     *
     * @param surface Stable Push surface
     * @param valueChanger Relative-value decoder
     */
    public void installControllerInputBridge (final PushControlSurface surface, final IValueChanger valueChanger)
    {
        if (this.closed)
            throw new IllegalStateException ("Reloadable controller runtime is closed");
        if (this.environment == null)
            throw new IllegalStateException ("Reloadable controller runtime is not connected");
        if (this.started)
            throw new IllegalStateException ("Controller inputs must be installed before runtime startup");
        if (this.inputBridge != null)
            throw new IllegalStateException ("Controller input bridge is already installed");

        this.inputBridge = new PushControllerInputBridge (
            Objects.requireNonNull (surface, "surface"),
            Objects.requireNonNull (valueChanger, "valueChanger"),
            this.environment::desiredInputRoutes,
            this::handleControllerInput,
            () -> this.supervisor == null ? 0 : this.supervisor.activeGeneration ());
        this.environment.setInputRouteValidator (this.inputBridge::supports);
    }


    /**
     * Refresh stable state and drain reload work on Bitwig's controller thread.
     */
    public void tick ()
    {
        if (!this.started || this.closed)
            return;

        final long startedAt = System.nanoTime ();
        final boolean snapshotChanged = this.environment.refresh ();
        if (this.inputBridge != null)
        {
            this.drainingControllerInputs = true;
            try
            {
                this.inputBridge.flush ();
            }
            finally
            {
                this.drainingControllerInputs = false;
            }
        }
        if (this.supervisor != null)
            this.supervisor.tick ();
        if (snapshotChanged)
        {
            final long deliveredRevision = this.environment.snapshotRevision ();
            if (this.eventHandler.test (this.environment.snapshotChangedEvent ()))
                this.environment.acknowledgeSnapshotChange (deliveredRevision);
        }
        this.reportSlowTick (startedAt);
    }


    /**
     * Route a physical fill pad before the stable active-view dispatch.
     *
     * @param drumControlsActive True when the drum layout owns its controls and the selected target
     *            supports the drum controller
     * @param event The physical button event
     * @param note The physical grid note
     * @return True when the reloadable runtime owns the event
     */
    public boolean routeGridEvent (final boolean drumControlsActive, final ButtonEvent event, final int note)
    {
        Objects.requireNonNull (event, "event");
        final ControlId control = controlForNote (note);
        if (control == null || this.environment == null || this.closed)
            return false;
        if (event == ButtonEvent.UP && this.rawReleasedGestures.remove (control))
            return true;
        if (event == ButtonEvent.DOWN)
        {
            // If Bitwig consumed the prior command-layer UP, its raw safety marker survives only
            // until the next gesture on this exact pad.
            this.rawReleasedGestures.remove (control);
        }

        final boolean held = this.environment.isFillPressed (control);
        if (!drumControlsActive && !held)
            return false;

        if (event == ButtonEvent.LONG || event == ButtonEvent.DOWN && held)
            return true;

        this.environment.refresh ();
        if (event == ButtonEvent.DOWN)
        {
            final ButtonInputEvent input = this.environment.setFillPressed (control, true);
            if (this.started)
                this.eventHandler.test (input);
            return true;
        }

        if (event == ButtonEvent.UP && held)
        {
            final ButtonInputEvent input = this.environment.setFillPressed (control, false);
            try
            {
                if (this.started)
                    this.eventHandler.test (input);
            }
            finally
            {
                // Physical state is authoritative even when no core is active or a candidate
                // result is rejected. Only the one active lease can request a native Return.
                this.environment.safetyRelease (control);
            }
            return true;
        }

        return event == ButtonEvent.UP;
    }


    /**
     * Route a physical MIDI release before the hardware-button command layer can consume its UP
     * event. This protects the permanent fill controls. Generic button arbitration sits below the
     * consumed-command gate and therefore receives its ordinary release without a raw-MIDI
     * workaround.
     *
     * @param drumControlsActive True when the drum layout owns its controls and the selected target
     *            supports the drum controller
     * @param status MIDI status byte
     * @param data1 First MIDI data byte
     * @param data2 Second MIDI data byte
     */
    public void routePhysicalMidiRelease (final boolean drumControlsActive, final int status, final int data1, final int data2)
    {
        final int command = status & 0xF0;
        final boolean fillPadChannel = (status & 0x0F) == 0 && isFillPad (data1);
        final boolean released = command == MidiConstants.CMD_NOTE_OFF || command == MidiConstants.CMD_NOTE_ON && data2 == 0;
        final ControlId control = fillPadChannel && released ? controlForNote (data1) : null;
        final boolean held = control != null && this.environment != null && !this.closed && this.environment.isFillPressed (control);
        if (held && this.routeGridEvent (drumControlsActive, ButtonEvent.UP, data1))
            this.rawReleasedGestures.add (control);
    }


    /**
     * Route pressure and pedal MIDI through the normalized controller seam.
     *
     * @param status MIDI status
     * @param data1 First MIDI data byte
     * @param data2 Second MIDI data byte
     * @param stableDispatch Existing stable surface MIDI handling
     * @return True when the bridge handled the message and any allowed stable dispatch
     */
    public boolean routeControllerMidi (final int status, final int data1, final int data2, final Runnable stableDispatch)
    {
        return this.inputBridge != null && !this.closed && this.inputBridge.routeMidi (status, data1, data2, Objects.requireNonNull (stableDispatch, "stableDispatch"));
    }


    /**
     * Get the physical fill-pad notes in bottom-up row-major order.
     *
     * @return A defensive copy of the fill-pad notes
     */
    public static int [] fillPadNotes ()
    {
        return FILL_PAD_NOTES.clone ();
    }


    /**
     * Test whether a grid note is a permanent fill-control binding.
     *
     * @param note The physical grid note
     * @return True for a fill pad
     */
    public static boolean isFillPad (final int note)
    {
        return controlForNote (note) != null;
    }


    /**
     * Get the current core-owned light for a physical fill pad.
     *
     * @param note The physical grid note
     * @return The hardware-independent RGB color
     */
    public RgbColor fillLightColor (final int note)
    {
        final ControlId control = controlForNote (note);
        return control == null || this.environment == null || this.closed ? OFF : this.environment.fillLightColor (control);
    }


    /**
     * Get the output generation used to force a complete replay after core activation.
     *
     * @return The output generation, or zero before connection
     */
    public long outputGeneration ()
    {
        return this.environment == null || this.closed ? 0 : this.environment.outputGeneration ();
    }


    /** {@inheritDoc} */
    @Override
    public void close ()
    {
        if (this.closed)
            return;

        this.closed = true;
        this.rawReleasedGestures.clear ();
        this.inputBridge = null;
        try
        {
            if (this.supervisor != null)
                this.supervisor.close ();
        }
        finally
        {
            if (this.environment != null)
            {
                for (final ControlId control: FILL_CONTROLS)
                    this.environment.safetyRelease (control);
            }
        }
    }


    private static ControlId controlForNote (final int note)
    {
        for (int index = 0; index < FILL_PAD_NOTES.length; index++)
        {
            if (FILL_PAD_NOTES[index] == note)
                return FILL_CONTROLS.get (index);
        }
        return null;
    }


    private void handleControllerInput (final PhysicalInputEvent<ControlId> event)
    {
        if (this.environment == null || this.closed)
            return;
        if (!this.drainingControllerInputs)
            this.environment.refresh ();

        final ControllerInputEvent input = this.environment.controllerInput (
            event.control (),
            de.mossgrabers.pull.core.api.event.InputKind.valueOf (event.kind ().name ()),
            event.phase () == de.mossgrabers.pull.shell.input.InputPhase.CHANGE ? de.mossgrabers.pull.core.api.event.InputPhase.UPDATE : de.mossgrabers.pull.core.api.event.InputPhase.valueOf (event.phase ().name ()),
            event.value ());
        if (!this.started)
            return;
        if (this.supervisor == null)
            this.eventHandler.test (input);
        else
            this.supervisor.handle (event.ownerGeneration (), input);
    }


    private void reportSlowTick (final long startedAt)
    {
        final long now = System.nanoTime ();
        final long elapsed = now - startedAt;
        if (elapsed <= SLOW_TICK_NANOS || this.lastSlowTickWarningNanos != Long.MIN_VALUE && now - this.lastSlowTickWarningNanos < SLOW_TICK_WARNING_INTERVAL_NANOS)
            return;

        this.lastSlowTickWarningNanos = now;
        try
        {
            this.log.warn ("Reloadable bridge tick took " + elapsed / 1_000_000.0 + " ms");
        }
        catch (final RuntimeException ignored)
        {
            // Diagnostics cannot change controller behavior.
        }
    }
}
