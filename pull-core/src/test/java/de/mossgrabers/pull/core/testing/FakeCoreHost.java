// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.testing;

import de.mossgrabers.pull.core.api.ClipCatalogSnapshot;
import de.mossgrabers.pull.core.api.ClipTargetId;
import de.mossgrabers.pull.core.api.ControlId;
import de.mossgrabers.pull.core.api.ControllerBridgeSnapshot;
import de.mossgrabers.pull.core.api.ControllerCore;
import de.mossgrabers.pull.core.api.ControllerSnapshot;
import de.mossgrabers.pull.core.api.SelectedTrackSnapshot;
import de.mossgrabers.pull.core.api.ShellCapabilities;
import de.mossgrabers.pull.core.api.StateEnvelope;
import de.mossgrabers.pull.core.api.TimerId;
import de.mossgrabers.pull.core.api.event.ButtonInputEvent;
import de.mossgrabers.pull.core.api.event.ControllerInputEvent;
import de.mossgrabers.pull.core.api.event.InputKind;
import de.mossgrabers.pull.core.api.event.InputPhase;
import de.mossgrabers.pull.core.api.event.SnapshotChangedEvent;
import de.mossgrabers.pull.core.api.event.TimerElapsedEvent;
import de.mossgrabers.pull.core.api.event.TouchInputEvent;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Small deterministic stand-in for the stable shell.
 */
final class FakeCoreHost
{
    private static final int MAX_TIMER_DISPATCHES_PER_ADVANCE = 10_000;

    private final ControllerCore core;
    private final FakeMonotonicTime time = new FakeMonotonicTime ();
    private final RecordingEffectExecutor effectExecutor = new RecordingEffectExecutor ();
    private final Set<ControlId> pressedControls = new LinkedHashSet<> ();
    private final Set<ControlId> touchedControls = new LinkedHashSet<> ();
    private final ShellCapabilities capabilities;
    private ClipCatalogSnapshot clipCatalog;
    private Map<ControlId, ClipTargetId> armedClipTargets;
    private Map<ControlId, ClipTargetId> clipLaunchSessionTargets;
    private Optional<ControlId> activeClipLaunchOwner;
    private ControllerBridgeSnapshot bridge = ControllerBridgeSnapshot.empty ();
    private long revision;
    private long eventSequence;


    /**
     * Constructor.
     *
     * @param core The core under test
     * @param capabilities Fake shell capabilities
     */
    FakeCoreHost (final ControllerCore core, final ShellCapabilities capabilities)
    {
        this (core, capabilities, ClipCatalogSnapshot.empty (), Map.of (), Set.of ());
    }


    /**
     * Constructor with authoritative state used to exercise startup hydration.
     *
     * @param core The core under test
     * @param capabilities Fake shell capabilities
     * @param clipCatalog Initial selected-track clip catalog
     * @param armedClipTargets Initially armed clip targets
     * @param pressedControls Initially held controls
     */
    FakeCoreHost (final ControllerCore core, final ShellCapabilities capabilities, final ClipCatalogSnapshot clipCatalog, final Map<ControlId, ClipTargetId> armedClipTargets, final Set<ControlId> pressedControls)
    {
        this (core, capabilities, clipCatalog, armedClipTargets, Optional.empty (), pressedControls);
    }


    /**
     * Constructor with complete authoritative state used to exercise startup hydration.
     *
     * @param core The core under test
     * @param capabilities Fake shell capabilities
     * @param clipCatalog Initial selected-track clip catalog
     * @param armedClipTargets Initially armed clip targets
     * @param activeClipLaunchOwner Initially active shell-managed clip session owner
     * @param pressedControls Initially held controls
     */
    FakeCoreHost (final ControllerCore core, final ShellCapabilities capabilities, final ClipCatalogSnapshot clipCatalog, final Map<ControlId, ClipTargetId> armedClipTargets, final Optional<ControlId> activeClipLaunchOwner, final Set<ControlId> pressedControls)
    {
        this (core, capabilities, clipCatalog, armedClipTargets, initialSessionTargets (armedClipTargets, activeClipLaunchOwner), activeClipLaunchOwner, pressedControls);
    }


    /**
     * Constructor with complete authoritative clip-session state used to exercise startup
     * hydration and retained-owner behavior.
     *
     * @param core The core under test
     * @param capabilities Fake shell capabilities
     * @param clipCatalog Initial selected-track clip catalog
     * @param armedClipTargets Initially armed clip targets
     * @param clipLaunchSessionTargets Initially retained clip-session targets
     * @param activeClipLaunchOwner Initially active shell-managed clip session owner
     * @param pressedControls Initially held controls
     */
    FakeCoreHost (final ControllerCore core, final ShellCapabilities capabilities, final ClipCatalogSnapshot clipCatalog, final Map<ControlId, ClipTargetId> armedClipTargets, final Map<ControlId, ClipTargetId> clipLaunchSessionTargets, final Optional<ControlId> activeClipLaunchOwner, final Set<ControlId> pressedControls)
    {
        this.core = Objects.requireNonNull (core, "core");
        this.capabilities = Objects.requireNonNull (capabilities, "capabilities");
        this.clipCatalog = Objects.requireNonNull (clipCatalog, "clipCatalog");
        this.armedClipTargets = Map.copyOf (Objects.requireNonNull (armedClipTargets, "armedClipTargets"));
        this.clipLaunchSessionTargets = Map.copyOf (Objects.requireNonNull (clipLaunchSessionTargets, "clipLaunchSessionTargets"));
        this.activeClipLaunchOwner = Objects.requireNonNull (activeClipLaunchOwner, "activeClipLaunchOwner");
        this.pressedControls.addAll (Objects.requireNonNull (pressedControls, "pressedControls"));
    }


    /**
     * Start the core.
     *
     * @param previousState Optional checkpoint
     */
    void start (final Optional<StateEnvelope> previousState)
    {
        this.effectExecutor.apply (this.core.start (this.snapshot (), previousState));
    }


    /**
     * Deliver a press or release after updating authoritative held state.
     *
     * @param controlId The control
     * @param pressed True for pressed
     */
    void button (final ControlId controlId, final boolean pressed)
    {
        if (pressed)
            this.pressedControls.add (controlId);
        else
            this.pressedControls.remove (controlId);

        this.revision++;
        this.eventSequence++;
        this.effectExecutor.apply (this.core.handle (new ButtonInputEvent (this.eventSequence, this.time.nowNanos (), controlId, pressed), this.snapshot ()));
    }


    /**
     * Deliver one normalized controller-button transition after updating authoritative held state.
     *
     * @param controlId The physical control
     * @param pressed True for pressed
     */
    void controllerButton (final ControlId controlId, final boolean pressed)
    {
        if (pressed)
            this.pressedControls.add (controlId);
        else
            this.pressedControls.remove (controlId);

        this.revision++;
        this.eventSequence++;
        this.effectExecutor.apply (this.core.handle (new ControllerInputEvent (
            this.eventSequence,
            this.time.nowNanos (),
            controlId,
            InputKind.BUTTON,
            pressed ? InputPhase.BEGIN : InputPhase.END,
            pressed ? 127 : 0), this.snapshot ()));
    }


    /**
     * Deliver a touch transition after updating authoritative held state.
     *
     * @param controlId The control
     * @param touched True for touched
     */
    void touch (final ControlId controlId, final boolean touched)
    {
        if (touched)
            this.touchedControls.add (controlId);
        else
            this.touchedControls.remove (controlId);

        this.revision++;
        this.eventSequence++;
        this.effectExecutor.apply (this.core.handle (new TouchInputEvent (this.eventSequence, this.time.nowNanos (), controlId, touched), this.snapshot ()));
    }


    /**
     * Replace the selected-track clip catalog and notify the core.
     *
     * @param clipCatalog The new catalog
     */
    void clipCatalog (final ClipCatalogSnapshot clipCatalog)
    {
        this.clipCatalog = Objects.requireNonNull (clipCatalog, "clipCatalog");
        this.snapshotChanged ();
    }


    /**
     * Replace the shell's verified armed bindings and notify the core.
     *
     * @param armedClipTargets The armed bindings
     */
    void armedClipTargets (final Map<ControlId, ClipTargetId> armedClipTargets)
    {
        this.armedClipTargets = Map.copyOf (Objects.requireNonNull (armedClipTargets, "armedClipTargets"));
        this.snapshotChanged ();
    }


    /**
     * Replace the shell's authoritative active clip-session owner and notify the core.
     *
     * @param activeClipLaunchOwner Active owner, if a momentary session exists
     */
    void activeClipLaunchOwner (final Optional<ControlId> activeClipLaunchOwner)
    {
        this.activeClipLaunchOwner = Objects.requireNonNull (activeClipLaunchOwner, "activeClipLaunchOwner");
        this.clipLaunchSessionTargets = this.effectExecutor.clipLaunchSessionTargets ();
        this.snapshotChanged ();
    }


    /**
     * Replace authoritative selected-track state and notify the core.
     *
     * @param selectedTrack Selected-track state
     */
    void selectedTrack (final SelectedTrackSnapshot selectedTrack)
    {
        this.bridge = new ControllerBridgeSnapshot (
            this.bridge.transport (),
            Objects.requireNonNull (selectedTrack, "selectedTrack"),
            this.bridge.layout (),
            this.bridge.drum ());
        this.snapshotChanged ();
    }


    /**
     * Replace the complete authoritative clip-launch session and notify the core.
     *
     * @param clipLaunchSessionTargets Retained targets by owner
     * @param activeClipLaunchOwner Active owner, if the session is non-empty
     */
    void clipLaunchSession (final Map<ControlId, ClipTargetId> clipLaunchSessionTargets, final Optional<ControlId> activeClipLaunchOwner)
    {
        this.clipLaunchSessionTargets = Map.copyOf (Objects.requireNonNull (clipLaunchSessionTargets, "clipLaunchSessionTargets"));
        this.activeClipLaunchOwner = Objects.requireNonNull (activeClipLaunchOwner, "activeClipLaunchOwner");
        this.snapshotChanged ();
    }


    private void snapshotChanged ()
    {
        this.revision++;
        this.eventSequence++;
        this.effectExecutor.apply (this.core.handle (new SnapshotChangedEvent (this.eventSequence, this.time.nowNanos ()), this.snapshot ()));
    }


    /**
     * Advance fake time and serially deliver timers due at the resulting time. Each event is
     * stamped with the final advanced time, and effects are applied before selecting the next
     * timer so callbacks may cancel or replace one another.
     *
     * @param duration The duration
     */
    void advance (final Duration duration)
    {
        this.time.advance (duration);
        int dispatchCount = 0;
        while (true)
        {
            final Optional<TimerId> timerId = this.effectExecutor.takeNextDueTimer (this.time.nowNanos ());
            if (timerId.isEmpty ())
                return;
            if (dispatchCount >= MAX_TIMER_DISPATCHES_PER_ADVANCE)
                throw new IllegalStateException ("Timer dispatch limit exceeded");

            dispatchCount++;
            this.revision++;
            this.eventSequence++;
            this.effectExecutor.apply (this.core.handle (new TimerElapsedEvent (this.eventSequence, this.time.nowNanos (), timerId.get ()), this.snapshot ()));
        }
    }


    /**
     * Get the current checkpoint.
     *
     * @return The checkpoint
     */
    StateEnvelope checkpoint ()
    {
        return this.core.checkpoint ();
    }


    /**
     * Stop the core.
     */
    void stop ()
    {
        this.core.stop ();
    }


    /**
     * Get the fake effect executor for assertions.
     *
     * @return The executor
     */
    RecordingEffectExecutor effects ()
    {
        return this.effectExecutor;
    }


    /**
     * Get current fake time.
     *
     * @return Monotonic nanoseconds
     */
    long nowNanos ()
    {
        return this.time.nowNanos ();
    }


    private ControllerSnapshot snapshot ()
    {
        return new ControllerSnapshot (this.revision, this.time.nowNanos (), this.capabilities, this.bridge, this.clipCatalog, this.armedClipTargets, this.clipLaunchSessionTargets, this.activeClipLaunchOwner, this.pressedControls, this.touchedControls);
    }


    private static Map<ControlId, ClipTargetId> initialSessionTargets (final Map<ControlId, ClipTargetId> armedClipTargets, final Optional<ControlId> activeClipLaunchOwner)
    {
        final Optional<ControlId> activeOwner = Objects.requireNonNull (activeClipLaunchOwner, "activeClipLaunchOwner");
        if (activeOwner.isEmpty ())
            return Map.of ();
        final ClipTargetId target = Objects.requireNonNull (Objects.requireNonNull (armedClipTargets, "armedClipTargets").get (activeOwner.get ()), "active owner target");
        return Map.of (activeOwner.get (), target);
    }
}
