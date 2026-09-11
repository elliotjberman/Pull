// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.shell.runtime;

import de.mossgrabers.pull.shell.SelectionDebug;
import com.bitwig.extension.controller.api.ClipLauncherSlot;
import com.bitwig.extension.controller.api.ClipLauncherSlotBank;
import com.bitwig.extension.controller.api.Track;

import de.mossgrabers.bitwig.framework.daw.data.TrackImpl;
import de.mossgrabers.framework.daw.IModel;
import de.mossgrabers.framework.daw.midi.ISelectedTrackNoteTarget;
import de.mossgrabers.pull.core.api.CatalogClip;
import de.mossgrabers.pull.core.api.ClipScanSnapshot;
import de.mossgrabers.pull.core.api.DesiredClipScan;
import de.mossgrabers.pull.core.api.ClipCatalogSnapshot;
import de.mossgrabers.pull.core.api.ClipTargetId;
import de.mossgrabers.pull.core.api.ControlId;
import de.mossgrabers.pull.core.api.CoreControls;
import de.mossgrabers.pull.core.api.effect.ClipLaunchPolicy;
import de.mossgrabers.pull.core.api.effect.ClipReleaseTrigger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;


/**
 * Observes core-requested clip pages and leases provisioned pool slot windows for exact launch
 * cleanup. Track acquisition and retention belong to the shared cursor pool.
 */
final class SelectedTrackFillClipHost implements DrumFillClipHost
{
    static final int SCANNER_PAGE_SIZE = 8;
    static final int ACTUATOR_COUNT = 8;

    private static final String POOL_NAMESPACE = "fill-clips";
    private static final String SCANNER_OWNER = "scanner";

    private final RetainedCursorPool pool;
    private final Adapter adapter;
    private RetainedCursorPool.Handle scannerHandle;
    private final List<ActuatorState> actuators;
    private final Map<ControlId, ActuatorState> actuatorsByControl;
    private final Map<Integer, ClipTargetId> targetIdsByScene = new HashMap<> ();

    private ClipCatalogSnapshot clipCatalog = ClipCatalogSnapshot.empty ();
    private Map<ClipTargetId, TargetCoordinate> targets = Map.of ();
    private Map<ControlId, ClipTargetId> desiredBindings = Map.of ();
    private Map<ControlId, ClipTargetId> armedClipTargets = Map.of ();
    private DesiredClipScan desiredScan = DesiredClipScan.inactive ();
    private ScannerSample pendingSample;
    private long selectedTargetGeneration;
    private final java.util.SortedMap<Integer, SlotSample> observedSlots = new java.util.TreeMap<> ();
    private String selectedTrackId = "";
    private int publishedSceneCount = -1;
    private long nextTargetValue;
    private long generation;
    private final java.util.function.BooleanSupplier scannerPaused;


    /**
     * Consume the clip profiles provisioned during extension initialization.
     *
     * @param host Shared retained cursor resources
     */
    SelectedTrackFillClipHost (final RetainedCursorHost host)
    {
        this (host.pool (), new LiveAdapter (host));
    }


    /**
     * Deterministic model-free test seam.
     *
     * @param pool Shared track acquisition and retained-resource owner
     * @param adapter Clip slot observations and effects
     */
    SelectedTrackFillClipHost (final RetainedCursorPool pool, final Adapter adapter)
    {
        this (pool, adapter, SelectionDebug::scannerPaused);
    }


    SelectedTrackFillClipHost (final RetainedCursorPool pool, final Adapter adapter, final java.util.function.BooleanSupplier scannerPaused)
    {
        this.pool = Objects.requireNonNull (pool, "pool");
        this.scannerPaused = Objects.requireNonNull (scannerPaused, "scannerPaused");
        this.adapter = Objects.requireNonNull (adapter, "adapter");

        final List<ControlId> controls = CoreControls.drumFills ();
        if (controls.size () != ACTUATOR_COUNT)
            throw new IllegalStateException ("The shell requires exactly eight drum-fill controls");

        final List<ActuatorState> states = new ArrayList<> (ACTUATOR_COUNT);
        final Map<ControlId, ActuatorState> byControl = new HashMap<> (ACTUATOR_COUNT);
        for (int index = 0; index < ACTUATOR_COUNT; index++)
        {
            final ActuatorState state = new ActuatorState (index, controls.get (index));
            states.add (state);
            byControl.put (state.control (), state);
        }
        this.actuators = List.copyOf (states);
        this.actuatorsByControl = Map.copyOf (byControl);
    }


    /**
     * Attach the already-created framework model to the stable Bitwig proxies.
     *
     * @param model Stable model
     */
    void connect (final IModel model, final ISelectedTrackNoteTarget selectedTarget)
    {
        this.adapter.connect (Objects.requireNonNull (model, "model"), Objects.requireNonNull (selectedTarget, "selectedTarget"));
    }


    /** {@inheritDoc} */
    @Override
    public boolean refresh ()
    {
        final ClipCatalogSnapshot oldCatalog = this.clipCatalog;
        final Map<ControlId, ClipTargetId> oldArmedTargets = this.armedClipTargets;

        this.refreshSelectedTrack ();
        this.reconcilePool ();
        if (this.scannerPaused.getAsBoolean ())
            this.pendingSample = null;
        else
            this.advanceScanner ();
        for (final ActuatorState actuator: this.actuators)
            actuator.advance ();
        this.updateArmedClipTargets ();
        if (SelectionDebug.recording ())
            SelectionDebug.record ("SCAN_SAMPLE", "request=" + this.desiredScan + " observed=" + this.clipCatalog.scan () + " catalogGeneration=" + this.generation + " clips=" + this.clipCatalog.clips ().size () + " armed=" + this.armedClipTargets.size ());

        return !oldCatalog.equals (this.clipCatalog) || !oldArmedTargets.equals (this.armedClipTargets);
    }


    /** {@inheritDoc} */
    @Override
    public ClipCatalogSnapshot clipCatalog ()
    {
        return this.clipCatalog;
    }


    /** {@inheritDoc} */
    @Override
    public void setDesiredBindings (final long catalogGeneration, final Map<ControlId, ClipTargetId> bindings)
    {
        if (catalogGeneration != this.generation)
            throw new IllegalArgumentException ("Clip-catalog generation is stale");

        final Map<ControlId, ClipTargetId> copiedBindings = Map.copyOf (Objects.requireNonNull (bindings, "bindings"));
        if (!this.actuatorsByControl.keySet ().containsAll (copiedBindings.keySet ()))
            throw new IllegalArgumentException ("Clip bindings contain an unsupported control");
        if (new HashSet<> (copiedBindings.values ()).size () != copiedBindings.size ())
            throw new IllegalArgumentException ("Clip bindings must target distinct catalog clips");
        for (final ClipTargetId targetId: copiedBindings.values ())
        {
            if (!this.targets.containsKey (targetId))
                throw new IllegalArgumentException ("Clip binding targets an unknown catalog clip");
        }

        this.desiredBindings = copiedBindings;
        this.reconcileActuators ();
        this.updateArmedClipTargets ();
    }


    /** {@inheritDoc} */
    @Override
    public Map<ControlId, ClipTargetId> armedClipTargets ()
    {
        return this.armedClipTargets;
    }


    /** {@inheritDoc} */
    @Override
    public LaunchTarget prepare (final ControlId owner, final long catalogGeneration, final ClipTargetId targetId)
    {
        Objects.requireNonNull (owner, "owner");
        Objects.requireNonNull (targetId, "targetId");
        if (catalogGeneration != this.generation)
            throw new IllegalArgumentException ("Clip-catalog generation is stale");

        final ActuatorState actuator = this.actuatorsByControl.get (owner);
        if (actuator == null)
            throw new IllegalArgumentException ("Unsupported clip-launch owner");
        if (!targetId.equals (this.armedClipTargets.get (owner)))
            throw new IllegalArgumentException ("Clip target is not armed for this control");

        final TargetCoordinate coordinate = this.targets.get (targetId);
        if (coordinate == null)
            throw new IllegalArgumentException ("Unknown clip target");
        return actuator.prepare (targetId, coordinate);
    }


    @Override
    public void setDesiredScan (final DesiredClipScan scan)
    {
        this.desiredScan = Objects.requireNonNull (scan, "scan");
    }


    private void refreshSelectedTrack ()
    {
        final SelectedTrackSample selected = Objects.requireNonNull (this.adapter.selectedTrack (), "selected-track sample");
        final boolean matches = this.desiredScan.active () && selected.exists () &&
            selected.generation () == this.desiredScan.targetGeneration () && selected.trackId ().equals (this.desiredScan.channelId ());
        final String observedId = matches ? selected.trackId () : "";
        final long targetGeneration = matches ? selected.generation () : 0;
        if (!observedId.equals (this.selectedTrackId) || targetGeneration != this.selectedTargetGeneration)
            this.beginGeneration (observedId, targetGeneration);
    }


    private void beginGeneration (final String trackId, final long targetGeneration)
    {
        this.generation = Math.incrementExact (this.generation);
        this.selectedTrackId = trackId;
        this.selectedTargetGeneration = targetGeneration;
        this.clipCatalog = new ClipCatalogSnapshot (this.generation, List.of ());
        this.targets = Map.of ();
        this.targetIdsByScene.clear ();
        this.observedSlots.clear ();
        this.desiredBindings = Map.of ();
        this.publishedSceneCount = -1;
        this.pendingSample = null;
        this.scannerHandle = null;
        for (final ActuatorState actuator: this.actuators)
            actuator.selectionChanged ();
        this.updateArmedClipTargets ();
    }


    private void advanceScanner ()
    {
        if (this.selectedTrackId.isEmpty ())
            return;
        final int requestedPage = this.desiredScan.sceneStart ();
        final RetainedCursorPool.Result retained = this.pool.lookup (POOL_NAMESPACE, SCANNER_OWNER);
        if (retained.status () != RetainedCursorPool.Status.READY || !this.pool.writable (retained.handle ()))
        {
            this.pendingSample = null;
            this.scannerHandle = null;
            this.clipCatalog = new ClipCatalogSnapshot (this.generation, this.clipCatalog.clips (),
                new ClipScanSnapshot (this.selectedTargetGeneration, this.selectedTrackId, Math.max (0, this.publishedSceneCount), requestedPage, SCANNER_PAGE_SIZE, false));
            return;
        }
        if (!retained.handle ().equals (this.scannerHandle))
        {
            this.scannerHandle = retained.handle ();
            this.pendingSample = null;
        }
        final ScannerSample sample = Objects.requireNonNull (this.adapter.scannerSample (this.scannerHandle), "scanner sample");
        if (this.publishedSceneCount >= 0 && sample.sceneCount () != this.publishedSceneCount)
        {
            // Scene insertions/deletions invalidate absolute coordinates and idle actuators.
            this.beginGeneration (this.selectedTrackId, this.selectedTargetGeneration);
            this.publishScan (sample, false);
            return;
        }
        this.publishedSceneCount = sample.sceneCount ();
        if (!isCoherentPage (sample, requestedPage, sample.sceneCount ()))
        {
            this.pendingSample = null;
            this.publishScan (sample, false);
            if (sample.windowStart () != requestedPage)
                this.adapter.moveScanner (this.scannerHandle, requestedPage);
            return;
        }
        if (!sample.equals (this.pendingSample))
        {
            this.pendingSample = sample;
            this.publishScan (sample, false);
            return;
        }
        if (!this.clipCatalog.scan ().ready () || this.clipCatalog.scan ().sceneStart () != requestedPage)
            this.publishCatalog (sample);
    }


    private void publishScan (final ScannerSample sample, final boolean ready)
    {
        this.clipCatalog = new ClipCatalogSnapshot (this.generation, this.clipCatalog.clips (),
            new ClipScanSnapshot (this.selectedTargetGeneration, this.selectedTrackId, sample.sceneCount (), Math.max (0, sample.windowStart ()), SCANNER_PAGE_SIZE, ready));
    }


    private static boolean isCoherentPage (final ScannerSample sample, final int expectedStart, final int sceneCount)
    {
        if (sample.windowStart () != expectedStart || sample.slots ().size () != SCANNER_PAGE_SIZE)
            return false;

        final int requiredSlots = Math.min (SCANNER_PAGE_SIZE, Math.max (0, sceneCount - expectedStart));
        for (int index = 0; index < requiredSlots; index++)
        {
            if (sample.slots ().get (index).sceneIndex () != expectedStart + index)
                return false;
        }
        return true;
    }


    private void publishCatalog (final ScannerSample sample)
    {
        for (final SlotSample slot: sample.slots ())
        {
            if (slot.sceneIndex () < sample.windowStart () || slot.sceneIndex () >= sample.sceneCount ())
                continue;
            if (slot.exists () && slot.hasContent ())
                this.observedSlots.put (Integer.valueOf (slot.sceneIndex ()), slot);
            else
            {
                this.observedSlots.remove (Integer.valueOf (slot.sceneIndex ()));
                this.targetIdsByScene.remove (Integer.valueOf (slot.sceneIndex ()));
            }
        }
        final List<CatalogClip> clips = new ArrayList<> (this.observedSlots.size ());
        final Map<ClipTargetId, TargetCoordinate> publishedTargets = new HashMap<> (this.observedSlots.size ());
        for (final SlotSample observed: this.observedSlots.values ())
        {
            final ClipTargetId targetId = this.targetIdsByScene.computeIfAbsent (Integer.valueOf (observed.sceneIndex ()), ignored -> this.nextTargetId ());
            clips.add (new CatalogClip (targetId, observed.name ()));
            publishedTargets.put (targetId, new TargetCoordinate (this.generation, this.selectedTrackId, observed.sceneIndex (), observed.name ()));
        }
        this.clipCatalog = new ClipCatalogSnapshot (this.generation, clips);
        this.targets = Map.copyOf (publishedTargets);
        this.publishScan (sample, true);
        if (SelectionDebug.recording ())
            SelectionDebug.record ("CATALOG_PAGE", "target=" + this.selectedTrackId + " page=" + sample.windowStart () + " slots=" + sample.slots ());
        this.reconcileActuators ();
    }


    private ClipTargetId nextTargetId ()
    {
        final ClipTargetId result = new ClipTargetId (this.nextTargetValue);
        this.nextTargetValue = Math.incrementExact (this.nextTargetValue);
        return result;
    }


    private void reconcileActuators ()
    {
        for (final ActuatorState actuator: this.actuators)
        {
            final ClipTargetId targetId = this.desiredBindings.get (actuator.control ());
            final TargetCoordinate coordinate = targetId == null ? null : this.targets.get (targetId);
            actuator.setDesired (coordinate == null ? null : new DesiredBinding (targetId, coordinate));
        }
        this.reconcilePool ();
    }


    private void reconcilePool ()
    {
        final List<RetainedCursorPool.Request> requests = new ArrayList<> (ACTUATOR_COUNT + 1);
        if (!this.selectedTrackId.isEmpty () && !this.scannerPaused.getAsBoolean ())
            requests.add (new RetainedCursorPool.Request (SCANNER_OWNER, this.selectedTrackId, RetainedCursorPool.Profile.CLIP_SCAN));
        for (final ActuatorState actuator: this.actuators)
        {
            final TargetCoordinate coordinate = actuator.lockOwner != null ? actuator.lockOwner.coordinate : actuator.desired == null ? null : actuator.desired.coordinate ();
            if (coordinate != null)
                requests.add (new RetainedCursorPool.Request (actuator.owner (), coordinate.trackId (), RetainedCursorPool.Profile.CLIP_ACTUATOR));
        }
        this.pool.reconcile (POOL_NAMESPACE, requests);
    }


    private void updateArmedClipTargets ()
    {
        final Map<ControlId, ClipTargetId> result = new LinkedHashMap<> (ACTUATOR_COUNT);
        for (final ActuatorState actuator: this.actuators)
        {
            final ClipTargetId targetId = actuator.armedTarget ();
            if (targetId != null)
                result.put (actuator.control (), targetId);
        }
        this.armedClipTargets = Collections.unmodifiableMap (result);
    }


    private final class ActuatorState
    {
        private final int index;
        private final ControlId control;

        private DesiredBinding desired;
        private TargetCoordinate parked;
        private RetainedCursorPool.Handle handle;
        private ActuatorLaunchTarget lockOwner;
        private int matchingSamples;
        private boolean ready;


        private ActuatorState (final int index, final ControlId control)
        {
            this.index = index;
            this.control = control;
        }


        private ControlId control ()
        {
            return this.control;
        }


        private String owner ()
        {
            return "actuator-" + this.index;
        }


        private void selectionChanged ()
        {
            this.setDesired (null);
        }


        private void setDesired (final DesiredBinding binding)
        {
            if (Objects.equals (binding, this.desired))
                return;
            this.desired = binding;
            if (this.lockOwner == null)
                this.clearReadiness ();
        }


        private void clearReadiness ()
        {
            this.parked = null;
            this.handle = null;
            this.matchingSamples = 0;
            this.ready = false;
        }


        private void requestScene (final RetainedCursorPool.Handle retained, final TargetCoordinate coordinate)
        {
            this.clearReadiness ();
            SelectedTrackFillClipHost.this.adapter.moveActuator (retained, coordinate.sceneIndex ());
            this.handle = retained;
            this.parked = coordinate;
        }


        private void advance ()
        {
            if (this.lockOwner != null || this.desired == null)
                return;
            final RetainedCursorPool.Result retained = SelectedTrackFillClipHost.this.pool.lookup (POOL_NAMESPACE, this.owner ());
            if (retained.status () != RetainedCursorPool.Status.READY || !SelectedTrackFillClipHost.this.pool.writable (retained.handle ()))
            {
                this.clearReadiness ();
                return;
            }
            if (!retained.handle ().equals (this.handle) || !this.desired.coordinate ().equals (this.parked))
            {
                this.requestScene (retained.handle (), this.desired.coordinate ());
                return;
            }

            final ActuatorSample sample = Objects.requireNonNull (SelectedTrackFillClipHost.this.adapter.actuatorSample (this.handle), "actuator sample");
            if (matches (sample, this.parked))
            {
                this.matchingSamples = Math.min (2, this.matchingSamples + 1);
                this.ready = this.matchingSamples >= 2;
                return;
            }

            this.matchingSamples = 0;
            this.ready = false;
            if (sample.sceneIndex () != this.parked.sceneIndex ())
                this.requestScene (this.handle, this.parked);
        }


        private ClipTargetId armedTarget ()
        {
            return this.ready && this.desired != null && this.desired.coordinate ().equals (this.parked) &&
                SelectedTrackFillClipHost.this.pool.writable (this.handle) ? this.desired.targetId () : null;
        }


        private ActuatorLaunchTarget prepare (final ClipTargetId targetId, final TargetCoordinate coordinate)
        {
            if (!this.ready || this.desired == null || !targetId.equals (this.desired.targetId ()) || !coordinate.equals (this.parked))
                throw new IllegalStateException ("Clip actuator is no longer armed");
            if (!SelectedTrackFillClipHost.this.pool.writable (this.handle) || !matches (SelectedTrackFillClipHost.this.adapter.actuatorSample (this.handle), coordinate))
                throw new IllegalStateException ("Retained clip target changed before preparation");
            return new ActuatorLaunchTarget (this, targetId, coordinate, this.handle);
        }


        private void press (final ActuatorLaunchTarget target, final ClipLaunchPolicy launchPolicy)
        {
            Objects.requireNonNull (launchPolicy, "launchPolicy");
            if (target.retired)
                throw new IllegalStateException ("Retired clip target cannot be pressed");
            if (target.releaseAttempted)
                throw new IllegalStateException ("Release-requested clip target cannot be pressed");
            if (target.pressAttempted)
            {
                if (!launchPolicy.equals (target.launchPolicy))
                    throw new IllegalStateException ("Pressed clip target cannot change launch policy");
                return;
            }
            if (this.lockOwner != null && this.lockOwner != target)
                throw new IllegalStateException ("Clip actuator is already leased");
            if (!this.ready || !target.coordinate.equals (this.parked))
                throw new DrumFillClipHost.TargetUnavailableException ("Prepared clip actuator is no longer armed");

            final SelectedTrackSample selected = SelectedTrackFillClipHost.this.adapter.selectedTrack ();
            if (!selected.exists () || selected.generation () != SelectedTrackFillClipHost.this.selectedTargetGeneration ||
                !target.coordinate.trackId ().equals (selected.trackId ()) || target.coordinate.generation () != SelectedTrackFillClipHost.this.generation)
                throw new DrumFillClipHost.TargetUnavailableException ("Selected clip target changed before launch");

            if (!SelectedTrackFillClipHost.this.pool.writable (target.handle) || !matches (SelectedTrackFillClipHost.this.adapter.actuatorSample (target.handle), target.coordinate) ||
                !SelectedTrackFillClipHost.this.pool.retain (target.handle))
                throw new DrumFillClipHost.TargetUnavailableException ("Retained clip target changed before launch");

            target.pressAttempted = true;
            target.launchPolicy = launchPolicy;
            this.lockOwner = target;
            SelectedTrackFillClipHost.this.adapter.pressActuator (target.handle, launchPolicy);
        }


        private void release (final ActuatorLaunchTarget target)
        {
            if (target.retired || target.releaseAttempted || !target.pressAttempted)
                return;
            if (this.lockOwner != target)
                throw new IllegalStateException ("Clip actuator lease ownership changed before release");

            // The Bitwig call is a command, not an acknowledgement. Keep the actuator locked on
            // the exact slot until the runtime observes playback state and explicitly retires it.
            this.exactSample (target);
            SelectedTrackFillClipHost.this.adapter.releaseActuator (target.handle, target.launchPolicy.releaseTrigger ());
            target.releaseAttempted = true;
        }


        private DrumFillClipHost.PlaybackState playbackState (final ActuatorLaunchTarget target)
        {
            if (target.retired)
                throw new IllegalStateException ("Retired clip target has no authoritative playback state");
            if (!target.pressAttempted || this.lockOwner != target)
                throw new IllegalStateException ("Clip actuator is not leased by this target");

            final ActuatorSample sample = this.exactSample (target);
            return new DrumFillClipHost.PlaybackState (sample.playing (), sample.playbackQueued (), sample.stopQueued ());
        }


        private ActuatorSample exactSample (final ActuatorLaunchTarget target)
        {
            if (!SelectedTrackFillClipHost.this.pool.valid (target.handle))
                throw new DrumFillClipHost.TargetUnavailableException ("Retained clip target is no longer addressable");
            final ActuatorSample sample = Objects.requireNonNull (SelectedTrackFillClipHost.this.adapter.actuatorSample (target.handle), "actuator sample");
            if (!matches (sample, target.coordinate))
                throw new DrumFillClipHost.TargetUnavailableException ("Retained clip slot changed before cleanup");
            return sample;
        }


        private void retire (final ActuatorLaunchTarget target)
        {
            if (target.retired)
                return;
            if (target.pressAttempted && this.lockOwner != target)
                throw new IllegalStateException ("Clip actuator lease ownership changed before retirement");

            target.retired = true;
            if (this.lockOwner == target)
            {
                this.lockOwner = null;
                SelectedTrackFillClipHost.this.pool.release (target.handle);
            }
            if (this.lockOwner == null)
                this.clearReadiness ();
            // Reconciliation and host commands run on the next refresh. Retirement itself only
            // relinquishes exact ownership and never navigates a mutable proxy.
        }
    }


    private final class ActuatorLaunchTarget implements LaunchTarget
    {
        private final ActuatorState actuator;
        private final ClipTargetId targetId;
        private final TargetCoordinate coordinate;
        private final RetainedCursorPool.Handle handle;

        private boolean pressAttempted;
        private boolean releaseAttempted;
        private boolean retired;
        private ClipLaunchPolicy launchPolicy;


        private ActuatorLaunchTarget (final ActuatorState actuator, final ClipTargetId targetId, final TargetCoordinate coordinate, final RetainedCursorPool.Handle handle)
        {
            this.actuator = actuator;
            this.targetId = targetId;
            this.coordinate = coordinate;
            this.handle = handle;
        }


        /** {@inheritDoc} */
        @Override
        public ClipTargetId targetId ()
        {
            return this.targetId;
        }


        /** {@inheritDoc} */
        @Override
        public void press (final ClipLaunchPolicy launchPolicy)
        {
            this.actuator.press (this, launchPolicy);
        }


        /** {@inheritDoc} */
        @Override
        public void release ()
        {
            this.actuator.release (this);
        }


        /** {@inheritDoc} */
        @Override
        public DrumFillClipHost.PlaybackState playbackState ()
        {
            return this.actuator.playbackState (this);
        }


        /** {@inheritDoc} */
        @Override
        public void retire ()
        {
            this.actuator.retire (this);
        }
    }


    private static boolean matches (final ActuatorSample sample, final TargetCoordinate coordinate)
    {
        return sample.sceneIndex () == coordinate.sceneIndex () && sample.slotExists () && sample.hasContent () && coordinate.name ().equals (sample.name ());
    }


    private static String safe (final String value)
    {
        return value == null ? "" : value;
    }


    /** Model-free shell adapter used by the deterministic state-machine tests. */
    interface Adapter
    {
        default void connect (final IModel model, final ISelectedTrackNoteTarget selectedTarget)
        {
            // Model-free adapters do not need a framework model.
        }


        SelectedTrackSample selectedTrack ();


        void moveScanner (RetainedCursorPool.Handle handle, int sceneStart);


        ScannerSample scannerSample (RetainedCursorPool.Handle handle);


        void moveActuator (RetainedCursorPool.Handle handle, int sceneIndex);


        ActuatorSample actuatorSample (RetainedCursorPool.Handle handle);


        void pressActuator (RetainedCursorPool.Handle handle, ClipLaunchPolicy launchPolicy);


        void releaseActuator (RetainedCursorPool.Handle handle, ClipReleaseTrigger releaseTrigger);
    }


    /** Current framework-selected track identity. */
    record SelectedTrackSample (String trackId, boolean exists, long generation)
    {
        SelectedTrackSample
        {
            trackId = safe (trackId);
        }
    }


    /** One complete scanner-bank sample. */
    record ScannerSample (int sceneCount, int windowStart, List<SlotSample> slots)
    {
        ScannerSample
        {
            if (sceneCount < 0)
                throw new IllegalArgumentException ("sceneCount must not be negative");
            slots = List.copyOf (Objects.requireNonNull (slots, "slots"));
        }
    }


    /** One scanner slot. */
    record SlotSample (int sceneIndex, String name, boolean exists, boolean hasContent)
    {
        SlotSample
        {
            name = safe (name);
        }
    }


    /** One parked actuator sample. */
    record ActuatorSample (int sceneIndex, String name, boolean slotExists, boolean hasContent, boolean playing, boolean playbackQueued, boolean stopQueued)
    {
        ActuatorSample
        {
            name = safe (name);
        }
    }


    private record TargetCoordinate (long generation, String trackId, int sceneIndex, String name)
    {
        private TargetCoordinate
        {
            trackId = Objects.requireNonNull (trackId, "trackId");
            name = Objects.requireNonNull (name, "name");
        }
    }


    private record DesiredBinding (ClipTargetId targetId, TargetCoordinate coordinate)
    {
        private DesiredBinding
        {
            targetId = Objects.requireNonNull (targetId, "targetId");
            coordinate = Objects.requireNonNull (coordinate, "coordinate");
        }
    }


    /** Clip operations over eagerly provisioned pool resources; no cursor acquisition policy. */
    private static final class LiveAdapter implements Adapter
    {
        private final RetainedCursorHost resources;
        private Track selectedTrack;
        private ISelectedTrackNoteTarget selectedTarget;


        private LiveAdapter (final RetainedCursorHost resources)
        {
            this.resources = Objects.requireNonNull (resources, "resources");
        }


        @Override
        public void connect (final IModel model, final ISelectedTrackNoteTarget selectedTarget)
        {
            if (!(model.getCursorTrack () instanceof final TrackImpl track))
                throw new IllegalArgumentException ("Selected-track fill scanning requires the Bitwig TrackImpl model");
            this.selectedTarget = Objects.requireNonNull (selectedTarget, "selectedTarget");
            this.selectedTrack = track.getTrack ();
            this.selectedTrack.exists ().markInterested ();
            this.selectedTrack.channelId ().markInterested ();
        }


        @Override
        public SelectedTrackSample selectedTrack ()
        {
            final Track track = this.selectedTrack;
            final ISelectedTrackNoteTarget target = this.selectedTarget;
            final boolean aligned = target != null && target.doesExist () && track != null && track.exists ().get () && target.getChannelID ().equals (track.channelId ().get ());
            return new SelectedTrackSample (target == null ? "" : target.getChannelID (), aligned, target == null ? 0 : target.getGeneration ());
        }


        @Override
        public void moveScanner (final RetainedCursorPool.Handle handle, final int sceneStart)
        {
            this.resources.clipSlots (handle).scrollPosition ().set (sceneStart);
        }


        @Override
        public ScannerSample scannerSample (final RetainedCursorPool.Handle handle)
        {
            final ClipLauncherSlotBank bank = this.resources.clipSlots (handle);
            final List<SlotSample> slots = new ArrayList<> (SCANNER_PAGE_SIZE);
            for (int index = 0; index < SCANNER_PAGE_SIZE; index++)
            {
                final ClipLauncherSlot slot = bank.getItemAt (index);
                slots.add (new SlotSample (slot.sceneIndex ().get (), slot.name ().get (), slot.exists ().get (), slot.hasContent ().get ()));
            }
            return new ScannerSample (Math.max (0, bank.itemCount ().get ()), bank.scrollPosition ().get (), slots);
        }


        @Override
        public void moveActuator (final RetainedCursorPool.Handle handle, final int sceneIndex)
        {
            this.resources.clipSlots (handle).scrollPosition ().set (sceneIndex);
        }


        @Override
        public ActuatorSample actuatorSample (final RetainedCursorPool.Handle handle)
        {
            final ClipLauncherSlot slot = this.resources.clipSlots (handle).getItemAt (0);
            return new ActuatorSample (slot.sceneIndex ().get (), slot.name ().get (), slot.exists ().get (), slot.hasContent ().get (),
                slot.isPlaying ().get (), slot.isPlaybackQueued ().get (), slot.isStopQueued ().get ());
        }


        @Override
        public void pressActuator (final RetainedCursorPool.Handle handle, final ClipLaunchPolicy launchPolicy)
        {
            this.resources.clipSlots (handle).getItemAt (0).launchWithOptions (
                BitwigClipLaunchMapper.quantization (launchPolicy.quantization ()), BitwigClipLaunchMapper.mode (launchPolicy.mode ()));
        }


        @Override
        public void releaseActuator (final RetainedCursorPool.Handle handle, final ClipReleaseTrigger releaseTrigger)
        {
            final ClipLauncherSlot slot = this.resources.clipSlots (handle).getItemAt (0);
            switch (Objects.requireNonNull (releaseTrigger, "releaseTrigger"))
            {
                case MAIN -> slot.launchRelease ();
                case ALTERNATE -> slot.launchReleaseAlt ();
            }
        }
    }
}
