// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.shell.runtime;

import com.bitwig.extension.controller.api.ClipLauncherSlot;
import com.bitwig.extension.controller.api.ClipLauncherSlotBank;
import com.bitwig.extension.controller.api.ControllerHost;
import com.bitwig.extension.controller.api.CursorTrack;
import com.bitwig.extension.controller.api.Track;

import de.mossgrabers.bitwig.framework.daw.data.TrackImpl;
import de.mossgrabers.framework.daw.IModel;
import de.mossgrabers.framework.daw.data.ITrack;
import de.mossgrabers.pull.core.api.CatalogClip;
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
import java.util.Set;


/**
 * Maintains a complete clip catalog for the selected track and parks one private, pinned Bitwig
 * cursor behind each physical fill control.
 */
final class SelectedTrackFillClipHost implements DrumFillClipHost
{
    static final int SCANNER_PAGE_SIZE = 8;
    static final int ACTUATOR_COUNT = 8;

    private final Adapter adapter;
    private final List<ActuatorState> actuators;
    private final Map<ControlId, ActuatorState> actuatorsByControl;
    private final Map<Integer, ClipTargetId> targetIdsByScene = new HashMap<> ();

    private ClipCatalogSnapshot clipCatalog = ClipCatalogSnapshot.empty ();
    private Map<ClipTargetId, TargetCoordinate> targets = Map.of ();
    private Map<ControlId, ClipTargetId> desiredBindings = Map.of ();
    private Map<ControlId, ClipTargetId> armedClipTargets = Map.of ();
    private ScanCycle scanCycle;
    private String selectedTrackId = "";
    private int publishedSceneCount = -1;
    private long nextTargetValue;
    private long generation;


    /**
     * Create all stable Bitwig proxies. This constructor must run during extension initialization.
     *
     * @param host Bitwig controller host
     */
    SelectedTrackFillClipHost (final ControllerHost host)
    {
        this (new LiveAdapter (Objects.requireNonNull (host, "host")));
    }


    /**
     * Deterministic model-free test seam.
     *
     * @param adapter Private scanner and actuator adapter
     */
    SelectedTrackFillClipHost (final Adapter adapter)
    {
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
    void connect (final IModel model)
    {
        this.adapter.connect (Objects.requireNonNull (model, "model"));
    }


    /** {@inheritDoc} */
    @Override
    public boolean refresh ()
    {
        final ClipCatalogSnapshot oldCatalog = this.clipCatalog;
        final Map<ControlId, ClipTargetId> oldArmedTargets = this.armedClipTargets;

        this.refreshSelectedTrack ();
        this.advanceScanner ();
        for (final ActuatorState actuator: this.actuators)
            actuator.advance ();
        this.updateArmedClipTargets ();

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


    private void refreshSelectedTrack ()
    {
        final SelectedTrackSample selected = Objects.requireNonNull (this.adapter.selectedTrack (), "selected-track sample");
        final String observedId = selected.exists () ? selected.trackId () : "";
        if (observedId.equals (this.selectedTrackId))
            return;

        this.beginGeneration (observedId);
    }


    private void beginGeneration (final String trackId)
    {
        this.generation = Math.incrementExact (this.generation);
        this.selectedTrackId = Objects.requireNonNull (trackId, "trackId");
        this.clipCatalog = new ClipCatalogSnapshot (this.generation, List.of ());
        this.targets = Map.of ();
        this.targetIdsByScene.clear ();
        this.desiredBindings = Map.of ();
        this.publishedSceneCount = -1;
        for (final ActuatorState actuator: this.actuators)
            actuator.selectionChanged ();
        this.updateArmedClipTargets ();

        if (trackId.isEmpty ())
        {
            this.scanCycle = null;
            return;
        }

        this.scanCycle = new ScanCycle (trackId, -1);
        this.requestScannerPage (0);
    }


    private void advanceScanner ()
    {
        final ScanCycle cycle = this.scanCycle;
        if (cycle == null || this.selectedTrackId.isEmpty ())
            return;

        final ScannerSample sample = Objects.requireNonNull (this.adapter.scannerSample (), "scanner sample");
        if (!sample.trackExists () || !cycle.trackId ().equals (sample.trackId ()))
        {
            cycle.clearPending ();
            this.requestScannerPage (cycle.pageStart ());
            return;
        }

        final int sceneCount = sample.sceneCount ();
        if (this.publishedSceneCount >= 0 && sceneCount != this.publishedSceneCount)
        {
            // Absolute scene coordinates may now identify different clips. Fence all idle state
            // before beginning the replacement sweep.
            this.beginGeneration (this.selectedTrackId);
            return;
        }

        if (cycle.expectedSceneCount () < 0)
            cycle.setExpectedSceneCount (sceneCount);
        else if (sceneCount != cycle.expectedSceneCount ())
        {
            this.scanCycle = new ScanCycle (this.selectedTrackId, sceneCount);
            this.requestScannerPage (0);
            return;
        }

        if (!isCoherentPage (sample, cycle.pageStart (), sceneCount))
        {
            cycle.clearPending ();
            this.requestScannerPage (cycle.pageStart ());
            return;
        }

        if (!sample.equals (cycle.pendingSample ()))
        {
            cycle.setPendingSample (sample);
            return;
        }

        cycle.clearPending ();
        cycle.accept (sample);
        final int nextPage = cycle.pageStart () + SCANNER_PAGE_SIZE;
        if (nextPage < sceneCount)
        {
            cycle.setPageStart (nextPage);
            this.requestScannerPage (nextPage);
            return;
        }

        this.publishCatalog (cycle);
        this.scanCycle = new ScanCycle (this.selectedTrackId, sceneCount);
        this.requestScannerPage (0);
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


    private void requestScannerPage (final int pageStart)
    {
        if (this.adapter.selectScannerTrack (this.selectedTrackId))
            this.adapter.moveScanner (pageStart);
    }


    private void publishCatalog (final ScanCycle cycle)
    {
        final List<CatalogClip> clips = new ArrayList<> (cycle.observedClips ().size ());
        final Map<ClipTargetId, TargetCoordinate> publishedTargets = new HashMap<> (cycle.observedClips ().size ());
        for (final ObservedClip observed: cycle.observedClips ())
        {
            final ClipTargetId targetId = this.targetIdsByScene.computeIfAbsent (Integer.valueOf (observed.sceneIndex ()), ignored -> this.nextTargetId ());
            clips.add (new CatalogClip (targetId, observed.name ()));
            publishedTargets.put (targetId, new TargetCoordinate (this.generation, cycle.trackId (), observed.sceneIndex (), observed.name ()));
        }

        this.clipCatalog = new ClipCatalogSnapshot (this.generation, clips);
        this.targets = Map.copyOf (publishedTargets);
        this.publishedSceneCount = cycle.expectedSceneCount ();
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


        private void selectionChanged ()
        {
            this.setDesired (null);
        }


        private void setDesired (final DesiredBinding binding)
        {
            if (Objects.equals (binding, this.desired))
                return;

            this.desired = binding;
            if (this.lockOwner != null)
                return;

            if (binding == null)
            {
                this.parked = null;
                this.matchingSamples = 0;
                this.ready = false;
                return;
            }
            this.requestPark (binding.coordinate ());
        }


        private void requestPark (final TargetCoordinate coordinate)
        {
            this.parked = coordinate;
            this.matchingSamples = 0;
            this.ready = false;
            if (SelectedTrackFillClipHost.this.adapter.selectActuatorTrack (this.index, coordinate.trackId ()))
                SelectedTrackFillClipHost.this.adapter.moveActuator (this.index, coordinate.sceneIndex ());
        }


        private void advance ()
        {
            if (this.lockOwner != null || this.desired == null)
                return;
            if (!this.desired.coordinate ().equals (this.parked))
            {
                this.requestPark (this.desired.coordinate ());
                return;
            }

            final ActuatorSample sample = Objects.requireNonNull (SelectedTrackFillClipHost.this.adapter.actuatorSample (this.index), "actuator sample");
            if (matches (sample, this.parked))
            {
                this.matchingSamples = Math.min (2, this.matchingSamples + 1);
                this.ready = this.matchingSamples >= 2;
                return;
            }

            this.matchingSamples = 0;
            this.ready = false;
            if (!sample.trackExists () || !sample.pinned () || !this.parked.trackId ().equals (sample.trackId ()) || sample.sceneIndex () != this.parked.sceneIndex ())
                this.requestPark (this.parked);
        }


        private ClipTargetId armedTarget ()
        {
            return this.ready && this.desired != null && this.desired.coordinate ().equals (this.parked) ? this.desired.targetId () : null;
        }


        private ActuatorLaunchTarget prepare (final ClipTargetId targetId, final TargetCoordinate coordinate)
        {
            if (!this.ready || this.desired == null || !targetId.equals (this.desired.targetId ()) || !coordinate.equals (this.parked))
                throw new IllegalStateException ("Clip actuator is no longer armed");
            return new ActuatorLaunchTarget (this, targetId, coordinate);
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
                throw new IllegalStateException ("Prepared clip actuator is no longer armed");

            target.pressAttempted = true;
            target.launchPolicy = launchPolicy;
            this.lockOwner = target;
            SelectedTrackFillClipHost.this.adapter.pressActuator (this.index, launchPolicy);
        }


        private void release (final ActuatorLaunchTarget target)
        {
            if (target.retired || target.releaseAttempted || !target.pressAttempted)
                return;
            if (this.lockOwner != target)
                throw new IllegalStateException ("Clip actuator lease ownership changed before release");

            // The Bitwig call is a command, not an acknowledgement. Keep the actuator locked on
            // the exact slot until the runtime observes playback state and explicitly retires it.
            SelectedTrackFillClipHost.this.adapter.releaseActuator (this.index, target.launchPolicy.releaseTrigger ());
            target.releaseAttempted = true;
        }


        private DrumFillClipHost.PlaybackState playbackState (final ActuatorLaunchTarget target)
        {
            if (target.retired)
                throw new IllegalStateException ("Retired clip target has no authoritative playback state");
            if (!target.pressAttempted || this.lockOwner != target)
                throw new IllegalStateException ("Clip actuator is not leased by this target");

            final ActuatorSample sample = Objects.requireNonNull (SelectedTrackFillClipHost.this.adapter.actuatorSample (this.index), "actuator sample");
            return new DrumFillClipHost.PlaybackState (sample.playing (), sample.playbackQueued (), sample.stopQueued ());
        }


        private void retire (final ActuatorLaunchTarget target)
        {
            if (target.retired)
                return;
            if (target.pressAttempted && this.lockOwner != target)
                throw new IllegalStateException ("Clip actuator lease ownership changed before retirement");

            target.retired = true;
            if (this.lockOwner == target)
                this.lockOwner = null;

            if (this.desired == null)
            {
                this.parked = null;
                this.matchingSamples = 0;
                this.ready = false;
            }
            else if (!this.desired.coordinate ().equals (this.parked))
            {
                try
                {
                    this.requestPark (this.desired.coordinate ());
                }
                catch (final RuntimeException ignored)
                {
                    // Retirement is a no-throw ownership boundary. Leave an explicit idle state;
                    // the next refresh will retry parking the current desired binding.
                    this.parked = null;
                    this.matchingSamples = 0;
                    this.ready = false;
                }
            }
        }
    }


    private final class ActuatorLaunchTarget implements LaunchTarget
    {
        private final ActuatorState actuator;
        private final ClipTargetId targetId;
        private final TargetCoordinate coordinate;

        private boolean pressAttempted;
        private boolean releaseAttempted;
        private boolean retired;
        private ClipLaunchPolicy launchPolicy;


        private ActuatorLaunchTarget (final ActuatorState actuator, final ClipTargetId targetId, final TargetCoordinate coordinate)
        {
            this.actuator = actuator;
            this.targetId = targetId;
            this.coordinate = coordinate;
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
        return sample.trackExists () && sample.pinned () && coordinate.trackId ().equals (sample.trackId ()) && sample.sceneIndex () == coordinate.sceneIndex () && sample.slotExists () && sample.hasContent () && coordinate.name ().equals (sample.name ());
    }


    private static String safe (final String value)
    {
        return value == null ? "" : value;
    }


    /** Model-free shell adapter used by the deterministic state-machine tests. */
    interface Adapter
    {
        default void connect (final IModel model)
        {
            // Model-free adapters do not need a framework model.
        }


        SelectedTrackSample selectedTrack ();


        boolean selectScannerTrack (String expectedTrackId);


        void moveScanner (int sceneStart);


        ScannerSample scannerSample ();


        boolean selectActuatorTrack (int actuatorIndex, String expectedTrackId);


        void moveActuator (int actuatorIndex, int sceneIndex);


        ActuatorSample actuatorSample (int actuatorIndex);


        void pressActuator (int actuatorIndex, ClipLaunchPolicy launchPolicy);


        void releaseActuator (int actuatorIndex, ClipReleaseTrigger releaseTrigger);
    }


    /** Current framework-selected track identity. */
    record SelectedTrackSample (String trackId, boolean exists)
    {
        SelectedTrackSample
        {
            trackId = safe (trackId);
        }
    }


    /** One complete scanner-bank sample. */
    record ScannerSample (String trackId, boolean trackExists, int sceneCount, int windowStart, List<SlotSample> slots)
    {
        ScannerSample
        {
            trackId = safe (trackId);
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
    record ActuatorSample (String trackId, boolean trackExists, boolean pinned, int sceneIndex, String name, boolean slotExists, boolean hasContent, boolean playing, boolean playbackQueued, boolean stopQueued)
    {
        ActuatorSample
        {
            trackId = safe (trackId);
            name = safe (name);
        }
    }


    private record ObservedClip (int sceneIndex, String name)
    {
        private ObservedClip
        {
            name = Objects.requireNonNull (name, "name");
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


    private static final class ScanCycle
    {
        private final String trackId;
        private final List<ObservedClip> observedClips = new ArrayList<> ();

        private int expectedSceneCount;
        private int pageStart;
        private ScannerSample pendingSample;


        private ScanCycle (final String trackId, final int expectedSceneCount)
        {
            this.trackId = trackId;
            this.expectedSceneCount = expectedSceneCount;
        }


        private String trackId ()
        {
            return this.trackId;
        }


        private int expectedSceneCount ()
        {
            return this.expectedSceneCount;
        }


        private void setExpectedSceneCount (final int expectedSceneCount)
        {
            this.expectedSceneCount = expectedSceneCount;
        }


        private int pageStart ()
        {
            return this.pageStart;
        }


        private void setPageStart (final int pageStart)
        {
            this.pageStart = pageStart;
        }


        private ScannerSample pendingSample ()
        {
            return this.pendingSample;
        }


        private void setPendingSample (final ScannerSample sample)
        {
            this.pendingSample = sample;
        }


        private void clearPending ()
        {
            this.pendingSample = null;
        }


        private List<ObservedClip> observedClips ()
        {
            return this.observedClips;
        }


        private void accept (final ScannerSample sample)
        {
            final int requiredSlots = Math.min (SCANNER_PAGE_SIZE, Math.max (0, this.expectedSceneCount - this.pageStart));
            for (int index = 0; index < requiredSlots; index++)
            {
                final SlotSample slot = sample.slots ().get (index);
                if (slot.exists () && slot.hasContent ())
                    this.observedClips.add (new ObservedClip (slot.sceneIndex (), slot.name ()));
            }
        }
    }


    /** Live Bitwig adapter. All object proxies are created eagerly in its constructor. */
    private static final class LiveAdapter implements Adapter
    {
        private final CursorTrack scanner;
        private final ClipLauncherSlotBank scannerSlots;
        private final List<ClipLauncherSlot> scannerSlotItems;
        private final List<CursorTrack> actuatorTracks;
        private final List<ClipLauncherSlotBank> actuatorSlotBanks;
        private final List<ClipLauncherSlot> actuatorSlots;

        private Track selectedTrack;


        private LiveAdapter (final ControllerHost host)
        {
            this.scanner = host.createCursorTrack ("PULL_FILL_SCANNER", "Pull Fill Scanner", 0, SCANNER_PAGE_SIZE, false);
            this.scannerSlots = this.scanner.clipLauncherSlotBank ();
            this.scannerSlotItems = markInterested (this.scanner, this.scannerSlots, SCANNER_PAGE_SIZE);

            final List<CursorTrack> tracks = new ArrayList<> (ACTUATOR_COUNT);
            final List<ClipLauncherSlotBank> banks = new ArrayList<> (ACTUATOR_COUNT);
            final List<ClipLauncherSlot> slots = new ArrayList<> (ACTUATOR_COUNT);
            for (int index = 0; index < ACTUATOR_COUNT; index++)
            {
                final int displayNumber = index + 1;
                final CursorTrack actuator = host.createCursorTrack ("PULL_FILL_ACTUATOR_" + displayNumber, "Pull Fill Actuator " + displayNumber, 0, 1, false);
                final ClipLauncherSlotBank bank = actuator.clipLauncherSlotBank ();
                tracks.add (actuator);
                banks.add (bank);
                slots.add (markInterested (actuator, bank, 1).get (0));
            }
            this.actuatorTracks = List.copyOf (tracks);
            this.actuatorSlotBanks = List.copyOf (banks);
            this.actuatorSlots = List.copyOf (slots);
        }


        /** {@inheritDoc} */
        @Override
        public void connect (final IModel model)
        {
            final ITrack frameworkTrack = model.getCursorTrack ();
            if (!(frameworkTrack instanceof final TrackImpl track))
                throw new IllegalArgumentException ("Selected-track fill scanning requires the Bitwig TrackImpl model");

            this.selectedTrack = track.getTrack ();
            this.selectedTrack.exists ().markInterested ();
            this.selectedTrack.channelId ().markInterested ();
        }


        /** {@inheritDoc} */
        @Override
        public SelectedTrackSample selectedTrack ()
        {
            final Track track = this.selectedTrack;
            return track == null ? new SelectedTrackSample ("", false) : new SelectedTrackSample (track.channelId ().get (), track.exists ().get ());
        }


        /** {@inheritDoc} */
        @Override
        public boolean selectScannerTrack (final String expectedTrackId)
        {
            return this.selectTrack (this.scanner, expectedTrackId);
        }


        /** {@inheritDoc} */
        @Override
        public void moveScanner (final int sceneStart)
        {
            this.scannerSlots.scrollPosition ().set (sceneStart);
        }


        /** {@inheritDoc} */
        @Override
        public ScannerSample scannerSample ()
        {
            final List<SlotSample> slots = new ArrayList<> (SCANNER_PAGE_SIZE);
            for (final ClipLauncherSlot slot: this.scannerSlotItems)
                slots.add (new SlotSample (slot.sceneIndex ().get (), slot.name ().get (), slot.exists ().get (), slot.hasContent ().get ()));
            return new ScannerSample (this.scanner.channelId ().get (), this.scanner.exists ().get (), Math.max (0, this.scannerSlots.itemCount ().get ()), this.scannerSlots.scrollPosition ().get (), slots);
        }


        /** {@inheritDoc} */
        @Override
        public boolean selectActuatorTrack (final int actuatorIndex, final String expectedTrackId)
        {
            return this.selectTrack (this.actuatorTracks.get (actuatorIndex), expectedTrackId);
        }


        /** {@inheritDoc} */
        @Override
        public void moveActuator (final int actuatorIndex, final int sceneIndex)
        {
            this.actuatorSlotBanks.get (actuatorIndex).scrollPosition ().set (sceneIndex);
        }


        /** {@inheritDoc} */
        @Override
        public ActuatorSample actuatorSample (final int actuatorIndex)
        {
            final CursorTrack track = this.actuatorTracks.get (actuatorIndex);
            final ClipLauncherSlot slot = this.actuatorSlots.get (actuatorIndex);
            return new ActuatorSample (
                track.channelId ().get (),
                track.exists ().get (),
                track.isPinned ().get (),
                slot.sceneIndex ().get (),
                slot.name ().get (),
                slot.exists ().get (),
                slot.hasContent ().get (),
                slot.isPlaying ().get (),
                slot.isPlaybackQueued ().get (),
                slot.isStopQueued ().get ());
        }


        /** {@inheritDoc} */
        @Override
        public void pressActuator (final int actuatorIndex, final ClipLaunchPolicy launchPolicy)
        {
            this.actuatorSlots.get (actuatorIndex).launchWithOptions (
                BitwigClipLaunchMapper.quantization (launchPolicy.quantization ()),
                BitwigClipLaunchMapper.mode (launchPolicy.mode ()));
        }


        /** {@inheritDoc} */
        @Override
        public void releaseActuator (final int actuatorIndex, final ClipReleaseTrigger releaseTrigger)
        {
            switch (Objects.requireNonNull (releaseTrigger, "releaseTrigger"))
            {
                case MAIN -> this.actuatorSlots.get (actuatorIndex).launchRelease ();
                case ALTERNATE -> this.actuatorSlots.get (actuatorIndex).launchReleaseAlt ();
            }
        }


        private boolean selectTrack (final CursorTrack cursor, final String expectedTrackId)
        {
            final Track track = this.selectedTrack;
            if (track == null || !track.exists ().get () || !expectedTrackId.equals (safe (track.channelId ().get ())))
                return false;

            cursor.isPinned ().set (false);
            cursor.selectChannel (track);
            cursor.isPinned ().set (true);
            return true;
        }


        private static List<ClipLauncherSlot> markInterested (final CursorTrack track, final ClipLauncherSlotBank bank, final int pageSize)
        {
            track.exists ().markInterested ();
            track.channelId ().markInterested ();
            track.isPinned ().markInterested ();
            bank.scrollPosition ().markInterested ();
            bank.itemCount ().markInterested ();

            final List<ClipLauncherSlot> slots = new ArrayList<> (pageSize);
            for (int index = 0; index < pageSize; index++)
            {
                final ClipLauncherSlot slot = bank.getItemAt (index);
                slot.exists ().markInterested ();
                slot.sceneIndex ().markInterested ();
                slot.name ().markInterested ();
                slot.hasContent ().markInterested ();
                slot.isPlaying ().markInterested ();
                slot.isPlaybackQueued ().markInterested ();
                slot.isStopQueued ().markInterested ();
                slots.add (slot);
            }
            return List.copyOf (slots);
        }
    }
}
