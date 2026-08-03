// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.shell.runtime;

import com.bitwig.extension.controller.api.ControllerHost;
import com.bitwig.extension.controller.api.CursorTrack;
import com.bitwig.extension.controller.api.Device;
import com.bitwig.extension.controller.api.DeviceBank;
import com.bitwig.extension.controller.api.Parameter;
import com.bitwig.extension.controller.api.SpecificBitwigDevice;

import de.mossgrabers.pull.core.api.ParameterTargetId;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.LongSupplier;


/**
 * Owns the selected track's managed Drum Pitch helper.
 *
 * <p>The helper is Bitwig's native Bend Note FX. A missing helper is inserted directly by UUID on
 * the first write. Because native insertion exposes no script-owned device identifier, ownership
 * is acquired only from the exact later topology transition and is then persisted in Bitwig's
 * document state. Existing Bend devices are never adopted by name, position, or configuration
 * alone.</p>
 *
 * <p>All Bitwig proxies are created eagerly during extension initialization. The Bend scan is
 * bounded to {@value #HELPER_SCAN_CAPACITY} top-level matches. Device topology is accepted only
 * after the selected-track observer's settle barrier, and every command remains a request until
 * subscribed state confirms it. The shell exposes Bend's full physical normalized range; the
 * reloadable core owns any product-level range mapping.</p>
 */
final class SelectedTrackDrumPitchHost implements SelectedTrackParameterHost
{
    static final String PAGE_NAME = "Pull";
    static final String PARAMETER_NAME = "Drum Pitch";
    static final String HELPER_PRESET_NAME = "Pull Drum Pitch Helper v1";
    static final String HELPER_PRESET_CREATOR = "DrivenByMoss Pull";
    static final int HELPER_SCAN_CAPACITY = 16;

    private static final UUID DRUM_MACHINE_DEVICE_ID = UUID.fromString ("8ea97e45-0255-40fd-bc7e-94419741e9d1");
    private static final UUID BEND_DEVICE_ID = UUID.fromString ("6aec6e78-9c1e-4c0b-8a88-0c2c37890a1d");
    private static final ParameterTargetId TARGET_ID = new ParameterTargetId (0);
    private static final double DEFAULT_NORMALIZED = 0.5;
    private static final double VALUE_TOLERANCE = 0.000001;
    private static final long TOPOLOGY_SETTLE_MILLIS = 200;
    private static final long INSERT_RETRY_NANOS = 10_000_000_000L;
    private static final long CONFIGURATION_RETRY_NANOS = 1_000_000_000L;
    private static final long OWNERSHIP_RETRY_NANOS = 1_000_000_000L;
    private static final long VALUE_RETRY_NANOS = 250_000_000L;
    private static final int MAX_INSERT_ATTEMPTS_PER_TRACK = 2;
    private static final int MAX_TRACKED_INSERTIONS = 32;

    static final List<ConfigurationSpec> CANONICAL_CONFIGURATION = List.of (
        new ConfigurationSpec ("CONTENTS/DELAY_ON", 1),
        new ConfigurationSpec ("CONTENTS/ENV_DELAY_SYNC", 0),
        new ConfigurationSpec ("CONTENTS/ENV_DELAY_SECONDS", 2),
        new ConfigurationSpec ("CONTENTS/SYNC", 0),
        new ConfigurationSpec ("CONTENTS/DURATION_IN_SECONDS", 2),
        new ConfigurationSpec ("CONTENTS/BEND", 0),
        new ConfigurationSpec ("CONTENTS/OFFSET", 0),
        new ConfigurationSpec ("CONTENTS/DURATION_IN_BEATS", 16),
        new ConfigurationSpec ("CONTENTS/ENV_DELAY_BEATS", 16),
        new ConfigurationSpec ("CONTENTS/ENV_DELAY_BEATS_OFFSET", 0));

    private final Adapter adapter;
    private final DrumPitchOwnershipStore ownershipStore;
    private final LongSupplier clock;
    private final Consumer<String> warningSink;
    private final Map<String, InsertionAttempt> insertionAttempts = new LinkedHashMap<> ();

    private State state = State.empty ();
    private StructuralIdentity observedIdentity;
    private long generation;
    private String activeTrackId = "";
    private Double pendingNormalizedValue;
    private long nextValueAttemptNanos;
    private long nextConfigurationAttemptNanos;
    private long nextOwnershipAttemptNanos;
    private boolean insertionCapacityWarningReported;
    private boolean configurationFailureReported;
    private boolean ownershipFailureReported;
    private boolean ownershipStateWarningReported;
    private boolean valueFailureReported;


    /**
     * Create the API-21 device matchers, banks, and parameter proxies on a shared selected-track
     * cursor during extension initialization.
     *
     * @param host Bitwig controller host
     * @param selectedTrack Shared selected-track cursor
     */
    SelectedTrackDrumPitchHost (final ControllerHost host, final CursorTrack selectedTrack)
    {
        this (host, selectedTrack, message -> { });
    }


    /**
     * Create the live managed helper host with a recoverable-warning sink.
     *
     * @param host Bitwig controller host
     * @param selectedTrack Shared selected-track cursor
     * @param warningSink Recoverable diagnostic sink
     */
    SelectedTrackDrumPitchHost (final ControllerHost host, final CursorTrack selectedTrack, final Consumer<String> warningSink)
    {
        this (new LiveAdapter (
            Objects.requireNonNull (host, "host"),
            Objects.requireNonNull (selectedTrack, "selectedTrack")),
            new DrumPitchOwnershipStore.Document (host),
            System::nanoTime,
            warningSink);
    }


    /**
     * Deterministic model-free test seam.
     *
     * @param adapter Subscribed device adapter
     */
    SelectedTrackDrumPitchHost (final Adapter adapter)
    {
        this (adapter, DrumPitchOwnershipStore.Empty.INSTANCE, System::nanoTime, message -> { });
    }


    /**
     * Deterministic model-free test seam with a monotonic clock.
     *
     * @param adapter Subscribed device adapter
     * @param clock Monotonic nanosecond clock
     */
    SelectedTrackDrumPitchHost (final Adapter adapter, final LongSupplier clock)
    {
        this (adapter, DrumPitchOwnershipStore.Empty.INSTANCE, clock, message -> { });
    }


    /**
     * Deterministic model-free test seam with diagnostics.
     *
     * @param adapter Subscribed device adapter
     * @param clock Monotonic nanosecond clock
     * @param warningSink Recoverable diagnostic sink
     */
    SelectedTrackDrumPitchHost (final Adapter adapter, final LongSupplier clock, final Consumer<String> warningSink)
    {
        this (adapter, DrumPitchOwnershipStore.Empty.INSTANCE, clock, warningSink);
    }


    /**
     * Deterministic model-free seam with explicit document ownership state.
     *
     * @param adapter Subscribed device adapter
     * @param ownershipStore Subscribed document ownership store
     * @param clock Monotonic nanosecond clock
     * @param warningSink Recoverable diagnostic sink
     */
    SelectedTrackDrumPitchHost (final Adapter adapter, final DrumPitchOwnershipStore ownershipStore, final LongSupplier clock, final Consumer<String> warningSink)
    {
        this.adapter = Objects.requireNonNull (adapter, "adapter");
        this.ownershipStore = Objects.requireNonNull (ownershipStore, "ownershipStore");
        this.clock = Objects.requireNonNull (clock, "clock");
        this.warningSink = Objects.requireNonNull (warningSink, "warningSink");
    }


    /** {@inheritDoc} */
    @Override
    public boolean refresh ()
    {
        final State previous = this.state;
        final DrumPitchOwnershipStore.Snapshot ownership = Objects.requireNonNull (this.ownershipStore.snapshot (), "drum-pitch ownership snapshot");
        final HostSample sample = Objects.requireNonNull (this.adapter.sample (), "drum-pitch sample");
        this.accept (sample, ownership);
        this.reconcile (sample, ownership);
        return !previous.equals (this.state);
    }


    /** {@inheritDoc} */
    @Override
    public State state ()
    {
        return this.state;
    }


    /** {@inheritDoc} */
    @Override
    public void setImmediately (final long catalogGeneration, final ParameterTargetId targetId, final double normalizedValue)
    {
        if (!TARGET_ID.equals (Objects.requireNonNull (targetId, "targetId")))
            throw new IllegalArgumentException ("Drum Pitch target is unavailable");
        requireNormalized (normalizedValue);
        if (catalogGeneration != this.generation)
            throw new IllegalArgumentException ("Parameter-catalog generation is stale");

        final Slot published = this.state.slots ().getFirst ();
        if (!published.exists () || !published.coherent ())
            throw new IllegalArgumentException ("Drum Pitch target is not coherent and available");

        final StructuralIdentity expectedIdentity = this.observedIdentity;
        final DrumPitchOwnershipStore.Snapshot freshOwnership = Objects.requireNonNull (this.ownershipStore.snapshot (), "drum-pitch ownership snapshot");
        final HostSample freshSample = Objects.requireNonNull (this.adapter.sample (), "drum-pitch sample");
        final StructuralIdentity freshIdentity = StructuralIdentity.from (freshSample, freshOwnership);
        if (expectedIdentity == null || !expectedIdentity.equals (freshIdentity) || !isConfirmed (freshSample))
        {
            this.accept (freshSample, freshOwnership);
            throw new IllegalStateException ("Drum Pitch target identity changed before write");
        }

        final Resolution resolution = this.resolve (freshSample, freshOwnership);
        final boolean insertionCanProgress = resolution.kind () != ResolutionKind.NEEDS_INSERTION || this.insertionCanProgress (freshSample.trackId ());
        if ((resolution.kind () != ResolutionKind.NEEDS_INSERTION && resolution.kind () != ResolutionKind.READY) || !insertionCanProgress)
        {
            this.accept (freshSample, freshOwnership);
            throw new IllegalStateException ("Drum Pitch target became unavailable before write");
        }

        this.pendingNormalizedValue = Double.valueOf (normalizedValue);
        this.submitPending (freshSample, resolution, true);
    }


    private void accept (final HostSample sample, final DrumPitchOwnershipStore.Snapshot ownership)
    {
        if (ownership.loaded () && !ownership.valid () && !this.ownershipStateWarningReported)
        {
            this.ownershipStateWarningReported = true;
            this.warn ("document ownership registry is malformed; managed pitch is disabled");
        }
        else if (ownership.valid ())
            this.ownershipStateWarningReported = false;

        final String sampledTrackId = sample.trackExists () ? sample.trackId () : "";
        if (!sampledTrackId.equals (this.activeTrackId))
        {
            this.activeTrackId = sampledTrackId;
            this.pendingNormalizedValue = null;
            this.nextValueAttemptNanos = 0;
            this.nextConfigurationAttemptNanos = 0;
            this.nextOwnershipAttemptNanos = 0;
            this.configurationFailureReported = false;
            this.ownershipFailureReported = false;
            this.valueFailureReported = false;
        }

        final boolean confirmed = isConfirmed (sample);
        final Resolution resolution = confirmed ? this.resolve (sample, ownership) : Resolution.unavailable ();
        if (confirmed && resolution.kind () == ResolutionKind.READY)
        {
            this.insertionAttempts.remove (sample.trackId ());
            if (this.insertionAttempts.size () < MAX_TRACKED_INSERTIONS)
                this.insertionCapacityWarningReported = false;
        }

        final StructuralIdentity identity = StructuralIdentity.from (sample, ownership);
        if (!identity.equals (this.observedIdentity))
        {
            this.observedIdentity = identity;
            this.generation = Math.incrementExact (this.generation);
        }

        final boolean insertionCanProgress = resolution.kind () != ResolutionKind.NEEDS_INSERTION || this.insertionCanProgress (sample.trackId ());
        if (!insertionCanProgress)
            this.pendingNormalizedValue = null;
        final boolean available = confirmed && insertionCanProgress && (resolution.kind () == ResolutionKind.NEEDS_INSERTION || resolution.kind () == ResolutionKind.READY);
        final double normalizedValue = resolution.kind () == ResolutionKind.READY ? requireNormalized (resolution.helper ().semitonesNormalized ()) : DEFAULT_NORMALIZED;
        final Slot slot = new Slot (TARGET_ID, PAGE_NAME, PARAMETER_NAME, available, normalizedValue, confirmed);
        this.state = new State (this.generation, sampledTrackId, PAGE_NAME, List.of (slot));
    }


    private void reconcile (final HostSample sample, final DrumPitchOwnershipStore.Snapshot ownership)
    {
        if (!isConfirmed (sample))
            return;

        final Resolution resolution = this.resolve (sample, ownership);
        if (resolution.kind () == ResolutionKind.NEEDS_CONFIGURATION)
        {
            this.repairConfiguration (resolution.helper ());
            return;
        }
        if (resolution.kind () == ResolutionKind.NEEDS_OWNERSHIP_SAVE)
        {
            this.saveOwnership (sample, resolution.helper ());
            return;
        }

        if (this.pendingNormalizedValue == null)
            return;

        if (resolution.kind () == ResolutionKind.READY && near (resolution.helper ().semitonesNormalized (), this.pendingNormalizedValue.doubleValue ()))
        {
            this.pendingNormalizedValue = null;
            return;
        }
        this.submitPending (sample, resolution, false);
    }


    private void submitPending (final HostSample sample, final Resolution resolution, final boolean immediateValue)
    {
        switch (resolution.kind ())
        {
            case NEEDS_INSERTION:
                this.requestInsertion (sample);
                break;
            case WAITING_FOR_METADATA:
            case WAITING_FOR_PARAMETERS:
            case NEEDS_CONFIGURATION:
            case NEEDS_OWNERSHIP_SAVE:
                break;
            case READY:
                this.requestValue (resolution.helper (), immediateValue);
                break;
            case UNAVAILABLE:
                this.pendingNormalizedValue = null;
                break;
        }
    }


    private void requestInsertion (final HostSample sample)
    {
        final String trackId = sample.trackId ();
        final long now = this.clock.getAsLong ();
        InsertionAttempt attempt = this.insertionAttempts.get (trackId);
        if (attempt == null)
        {
            if (this.insertionAttempts.size () >= MAX_TRACKED_INSERTIONS)
                return;
            attempt = InsertionAttempt.at (sample);
            this.insertionAttempts.put (trackId, attempt);
        }
        else if (!attempt.baselineStillPossible (sample))
        {
            if (!attempt.topologyConflictReported)
            {
                attempt.topologyConflictReported = true;
                this.warn ("helper insertion topology changed before ownership could be proven on track " + trackId);
            }
            return;
        }
        if (attempt.attempts >= MAX_INSERT_ATTEMPTS_PER_TRACK || now < attempt.retryAtNanos)
            return;

        attempt.attempts++;
        attempt.retryAtNanos = deadline (now, INSERT_RETRY_NANOS);
        try
        {
            this.adapter.insertHelperBeforeDrum ();
        }
        catch (final RuntimeException ex)
        {
            this.warn ("helper insertion failed", ex);
        }
    }


    private void saveOwnership (final HostSample sample, final HelperSample helper)
    {
        final long now = this.clock.getAsLong ();
        if (now < this.nextOwnershipAttemptNanos)
            return;
        this.nextOwnershipAttemptNanos = deadline (now, OWNERSHIP_RETRY_NANOS);
        try
        {
            this.ownershipStore.save (new DrumPitchOwnershipStore.Ownership (
                sample.trackId (),
                helper.position (),
                sample.firstDrum ().position (),
                DrumPitchOwnershipStore.CONFIGURATION_VERSION));
            this.ownershipFailureReported = false;
        }
        catch (final RuntimeException ex)
        {
            if (!this.ownershipFailureReported)
            {
                this.ownershipFailureReported = true;
                this.warn ("helper ownership could not be saved", ex);
            }
        }
    }


    private boolean insertionCanProgress (final String trackId)
    {
        final InsertionAttempt attempt = this.insertionAttempts.get (trackId);
        if (attempt == null)
        {
            if (this.insertionAttempts.size () < MAX_TRACKED_INSERTIONS)
                return true;
            if (!this.insertionCapacityWarningReported)
            {
                this.insertionCapacityWarningReported = true;
                this.warn ("helper insertion is unavailable because the bounded track-attempt registry is full");
            }
            return false;
        }

        if (attempt.topologyConflictReported)
            return false;

        if (attempt.attempts < MAX_INSERT_ATTEMPTS_PER_TRACK || this.clock.getAsLong () < attempt.retryAtNanos)
            return true;
        if (!attempt.exhaustionWarningReported)
        {
            attempt.exhaustionWarningReported = true;
            this.warn ("helper insertion did not appear after " + MAX_INSERT_ATTEMPTS_PER_TRACK + " attempts on track " + trackId);
        }
        return false;
    }


    private void repairConfiguration (final HelperSample helper)
    {
        final long now = this.clock.getAsLong ();
        if (now < this.nextConfigurationAttemptNanos)
            return;
        this.nextConfigurationAttemptNanos = deadline (now, CONFIGURATION_RETRY_NANOS);
        try
        {
            if (helper.enabled () && !helper.hasCanonicalConfiguration ())
                this.adapter.setHelperEnabled (helper.bankIndex (), false);
            else if (!helper.hasCanonicalConfiguration ())
                this.adapter.configureHelper (helper.bankIndex ());
            else
                this.adapter.setHelperEnabled (helper.bankIndex (), true);
            this.configurationFailureReported = false;
        }
        catch (final RuntimeException ex)
        {
            if (!this.configurationFailureReported)
            {
                this.configurationFailureReported = true;
                this.warn ("helper configuration repair failed", ex);
            }
        }
    }


    private void requestValue (final HelperSample helper, final boolean immediate)
    {
        final long now = this.clock.getAsLong ();
        if (!immediate && now < this.nextValueAttemptNanos)
            return;
        this.nextValueAttemptNanos = deadline (now, VALUE_RETRY_NANOS);
        try
        {
            this.adapter.setSemitonesNormalized (helper.bankIndex (), this.pendingNormalizedValue.doubleValue ());
            this.valueFailureReported = false;
        }
        catch (final RuntimeException ex)
        {
            if (!this.valueFailureReported)
            {
                this.valueFailureReported = true;
                this.warn ("pitch write failed", ex);
            }
        }
    }


    private void warn (final String message)
    {
        try
        {
            this.warningSink.accept ("Managed Drum Pitch: " + message);
        }
        catch (final RuntimeException ignored)
        {
            // Diagnostics cannot change controller ownership or retry state.
        }
    }


    private void warn (final String message, final RuntimeException failure)
    {
        final String detail = failure.getMessage ();
        this.warn (message + ": " + failure.getClass ().getSimpleName () + (detail == null || detail.isBlank () ? "" : ": " + detail));
    }


    private Resolution resolve (final HostSample sample, final DrumPitchOwnershipStore.Snapshot ownership)
    {
        if (!sample.trackExists () || sample.trackId ().isEmpty () || !sample.canHoldNoteData () || sample.drumCount () < 1 || !sample.firstDrum ().exists () || sample.helperCount () > HELPER_SCAN_CAPACITY)
            return Resolution.unavailable ();
        if (!ownership.loaded () || !ownership.valid ())
            return Resolution.unavailable ();

        HelperSample branded = null;
        boolean metadataHydrating = false;
        for (final HelperSample helper: sample.helpers ())
        {
            switch (helper.ownership ())
            {
                case CONFLICT:
                    return Resolution.unavailable ();
                case HYDRATING:
                    metadataHydrating = true;
                    break;
                case OWNED:
                    if (branded != null || helper.position () >= sample.firstDrum ().position ())
                        return Resolution.unavailable ();
                    branded = helper;
                    break;
                case UNOWNED:
                    break;
            }
        }

        final DrumPitchOwnershipStore.Ownership record = ownership.forTrack (sample.trackId ()).orElse (null);
        final HelperSample recorded = record == null ? null : helperAtRecordedPosition (sample, record);
        if (branded != null && recorded != null && branded.bankIndex () != recorded.bankIndex ())
            return Resolution.unavailable ();

        HelperSample owned = branded != null ? branded : recorded;
        boolean mustSaveOwnership = false;
        if (owned == null && metadataHydrating)
            return new Resolution (ResolutionKind.WAITING_FOR_METADATA, null);
        if (owned == null)
        {
            final InsertionAttempt attempt = this.insertionAttempts.get (sample.trackId ());
            if (attempt != null)
            {
                owned = attempt.provisionalHelper (sample);
                if (owned == null && !attempt.baselineStillPossible (sample))
                    return Resolution.unavailable ();
                mustSaveOwnership = owned != null;
            }
        }

        if (metadataHydrating)
            return new Resolution (ResolutionKind.WAITING_FOR_METADATA, owned);
        if (owned == null)
        {
            if (!sample.insertionSupported () || sample.helperCount () >= HELPER_SCAN_CAPACITY)
                return Resolution.unavailable ();
            return new Resolution (ResolutionKind.NEEDS_INSERTION, null);
        }
        if (owned.position () >= sample.firstDrum ().position ())
            return Resolution.unavailable ();
        if (!owned.parametersAvailable ())
            return new Resolution (ResolutionKind.WAITING_FOR_PARAMETERS, owned);
        if (!owned.enabled () || !owned.hasCanonicalConfiguration ())
            return new Resolution (ResolutionKind.NEEDS_CONFIGURATION, owned);
        if (recorded != null && (record.helperPosition () != owned.position () || record.drumPosition () != sample.firstDrum ().position ()))
            mustSaveOwnership = true;
        if (mustSaveOwnership)
            return new Resolution (ResolutionKind.NEEDS_OWNERSHIP_SAVE, owned);
        return new Resolution (ResolutionKind.READY, owned);
    }


    private static HelperSample helperAtRecordedPosition (final HostSample sample, final DrumPitchOwnershipStore.Ownership ownership)
    {
        final int delta = sample.firstDrum ().position () - ownership.drumPosition ();
        final long expected = (long) ownership.helperPosition () + delta;
        if (expected < 0 || expected >= sample.firstDrum ().position ())
            return null;
        HelperSample match = null;
        for (final HelperSample helper: sample.helpers ())
        {
            if (helper.position () != expected)
                continue;
            if (match != null)
                return null;
            match = helper;
        }
        return match;
    }


    private static boolean isConfirmed (final HostSample sample)
    {
        return sample.topologySettled () && isComplete (sample);
    }


    private static boolean isComplete (final HostSample sample)
    {
        if (sample.trackExists () != !sample.trackId ().isEmpty () || sample.drumCount () < 0 || sample.helperCount () < 0 || sample.helperCount () > HELPER_SCAN_CAPACITY)
            return false;
        if (sample.drumCount () == 0)
        {
            if (sample.firstDrum ().exists ())
                return false;
        }
        else if (!sample.firstDrum ().exists () || sample.firstDrum ().position () < 0)
            return false;

        if (sample.helpers ().size () != sample.helperCount ())
            return false;
        final Set<Integer> positions = new HashSet<> ();
        for (int index = 0; index < sample.helpers ().size (); index++)
        {
            final HelperSample helper = sample.helpers ().get (index);
            if (helper.bankIndex () != index || !helper.exists () || helper.position () < 0 || !positions.add (Integer.valueOf (helper.position ())))
                return false;
            if (helper.configuration ().size () != CANONICAL_CONFIGURATION.size ())
                return false;
            for (int configurationIndex = 0; configurationIndex < CANONICAL_CONFIGURATION.size (); configurationIndex++)
            {
                final ConfigurationSample actual = helper.configuration ().get (configurationIndex);
                final ConfigurationSpec expected = CANONICAL_CONFIGURATION.get (configurationIndex);
                if (!expected.parameterId ().equals (actual.parameterId ()))
                    return false;
            }
        }
        return true;
    }


    private static double requireNormalized (final double value)
    {
        if (!Double.isFinite (value) || value < 0 || value > 1)
            throw new IllegalArgumentException ("value must be finite and in [0, 1]");
        return value;
    }


    private static boolean near (final double actual, final double expected)
    {
        return Math.abs (actual - expected) <= VALUE_TOLERANCE;
    }


    private static long deadline (final long now, final long delay)
    {
        return now > Long.MAX_VALUE - delay ? Long.MAX_VALUE : now + delay;
    }


    /** Model-free adapter for subscribed host state and device commands. */
    interface Adapter
    {
        HostSample sample ();


        void insertHelperBeforeDrum ();


        void configureHelper (int helperIndex);


        void setHelperEnabled (int helperIndex, boolean enabled);


        void setSemitonesNormalized (int helperIndex, double normalizedValue);
    }


    /** One complete subscribed selected-track/device sample. */
    record HostSample (boolean topologySettled, boolean insertionSupported, boolean trackExists, String trackId, boolean canHoldNoteData, int drumCount, DeviceSample firstDrum, int helperCount, List<HelperSample> helpers)
    {
        HostSample
        {
            trackId = trackId == null ? "" : trackId;
            firstDrum = Objects.requireNonNull (firstDrum, "firstDrum");
            helpers = List.copyOf (Objects.requireNonNull (helpers, "helpers"));
        }
    }


    /** One subscribed first-Drum-Machine sample. */
    record DeviceSample (boolean exists, int position)
    {
        static DeviceSample missing ()
        {
            return new DeviceSample (false, -1);
        }
    }


    /** Canonical raw setting for one managed Bend semantic parameter. */
    record ConfigurationSpec (String parameterId, double expectedRaw)
    {
        ConfigurationSpec
        {
            parameterId = Objects.requireNonNull (parameterId, "parameterId");
            if (!Double.isFinite (expectedRaw))
                throw new IllegalArgumentException ("expectedRaw must be finite");
        }
    }


    /** One subscribed managed Bend semantic parameter. */
    record ConfigurationSample (String parameterId, boolean exists, double rawValue)
    {
        ConfigurationSample
        {
            parameterId = Objects.requireNonNull (parameterId, "parameterId");
            if (!Double.isFinite (rawValue))
                throw new IllegalArgumentException ("rawValue must be finite");
        }
    }


    /** One subscribed native Bend candidate. */
    record HelperSample (int bankIndex, boolean exists, int position, String presetName, String presetCreator, boolean enabled, boolean semitonesExists, double semitonesNormalized, List<ConfigurationSample> configuration)
    {
        HelperSample
        {
            presetName = presetName == null ? "" : presetName;
            presetCreator = presetCreator == null ? "" : presetCreator;
            if (!Double.isFinite (semitonesNormalized))
                throw new IllegalArgumentException ("semitonesNormalized must be finite");
            configuration = List.copyOf (Objects.requireNonNull (configuration, "configuration"));
        }


        private Ownership ownership ()
        {
            final boolean nameMatches = HELPER_PRESET_NAME.equals (this.presetName);
            final boolean creatorMatches = HELPER_PRESET_CREATOR.equals (this.presetCreator);
            if (nameMatches && creatorMatches)
                return Ownership.OWNED;
            if (nameMatches || creatorMatches)
                return this.presetName.isEmpty () || this.presetCreator.isEmpty () ? Ownership.HYDRATING : Ownership.CONFLICT;
            return Ownership.UNOWNED;
        }


        private boolean parametersAvailable ()
        {
            if (!this.semitonesExists ())
                return false;
            for (final ConfigurationSample parameter: this.configuration)
            {
                if (!parameter.exists ())
                    return false;
            }
            return true;
        }


        private boolean hasCanonicalConfiguration ()
        {
            if (this.configuration.size () != CANONICAL_CONFIGURATION.size ())
                return false;
            for (int index = 0; index < CANONICAL_CONFIGURATION.size (); index++)
            {
                final ConfigurationSample actual = this.configuration.get (index);
                final ConfigurationSpec expected = CANONICAL_CONFIGURATION.get (index);
                if (!expected.parameterId ().equals (actual.parameterId ()) || !actual.exists () || !near (actual.rawValue (), expected.expectedRaw ()))
                    return false;
            }
            return true;
        }
    }


    private enum Ownership
    {
        OWNED,
        HYDRATING,
        CONFLICT,
        UNOWNED
    }


    private enum ResolutionKind
    {
        NEEDS_INSERTION,
        WAITING_FOR_METADATA,
        WAITING_FOR_PARAMETERS,
        NEEDS_CONFIGURATION,
        NEEDS_OWNERSHIP_SAVE,
        READY,
        UNAVAILABLE
    }


    private record Resolution (ResolutionKind kind, HelperSample helper)
    {
        private Resolution
        {
            kind = Objects.requireNonNull (kind, "kind");
        }


        private static Resolution unavailable ()
        {
            return new Resolution (ResolutionKind.UNAVAILABLE, null);
        }
    }


    private static final class InsertionAttempt
    {
        private final int baselineDrumPosition;
        private final List<Integer> baselineHelperPositions;
        private int attempts;
        private long retryAtNanos;
        private boolean exhaustionWarningReported;
        private boolean topologyConflictReported;


        private InsertionAttempt (final int baselineDrumPosition, final List<Integer> baselineHelperPositions)
        {
            this.baselineDrumPosition = baselineDrumPosition;
            this.baselineHelperPositions = List.copyOf (baselineHelperPositions);
        }


        private static InsertionAttempt at (final HostSample sample)
        {
            final List<Integer> positions = new ArrayList<> (sample.helpers ().size ());
            for (final HelperSample helper: sample.helpers ())
                positions.add (Integer.valueOf (helper.position ()));
            return new InsertionAttempt (sample.firstDrum ().position (), positions);
        }


        private boolean baselineStillPossible (final HostSample sample)
        {
            if (sample.firstDrum ().position () != this.baselineDrumPosition || sample.helpers ().size () != this.baselineHelperPositions.size ())
                return false;
            for (int index = 0; index < this.baselineHelperPositions.size (); index++)
            {
                if (sample.helpers ().get (index).position () != this.baselineHelperPositions.get (index).intValue ())
                    return false;
            }
            return true;
        }


        private HelperSample provisionalHelper (final HostSample sample)
        {
            if (sample.firstDrum ().position () != this.baselineDrumPosition + 1 || sample.helpers ().size () != this.baselineHelperPositions.size () + 1)
                return null;

            final List<Integer> expected = new ArrayList<> (this.baselineHelperPositions.size ());
            for (final Integer position: this.baselineHelperPositions)
                expected.add (Integer.valueOf (position.intValue () < this.baselineDrumPosition ? position.intValue () : position.intValue () + 1));

            HelperSample inserted = null;
            for (final HelperSample helper: sample.helpers ())
            {
                if (helper.position () == this.baselineDrumPosition)
                {
                    if (inserted != null)
                        return null;
                    inserted = helper;
                    continue;
                }
                if (!expected.remove (Integer.valueOf (helper.position ())))
                    return null;
            }
            return expected.isEmpty () ? inserted : null;
        }
    }


    private record ConfigurationIdentity (String parameterId, boolean exists, double rawValue)
    {
    }


    private record HelperIdentity (int bankIndex, boolean exists, int position, String presetName, String presetCreator, boolean enabled, boolean semitonesExists, List<ConfigurationIdentity> configuration)
    {
        private HelperIdentity
        {
            configuration = List.copyOf (configuration);
        }
    }


    private record StructuralIdentity (boolean topologySettled, boolean insertionSupported, boolean trackExists, String trackId, boolean canHoldNoteData, int drumCount, DeviceSample firstDrum, int helperCount, List<HelperIdentity> helpers, DrumPitchOwnershipStore.Snapshot ownership)
    {
        private StructuralIdentity
        {
            trackId = Objects.requireNonNull (trackId, "trackId");
            firstDrum = Objects.requireNonNull (firstDrum, "firstDrum");
            helpers = List.copyOf (Objects.requireNonNull (helpers, "helpers"));
            ownership = Objects.requireNonNull (ownership, "ownership");
        }


        private static StructuralIdentity from (final HostSample sample, final DrumPitchOwnershipStore.Snapshot ownership)
        {
            final List<HelperIdentity> helpers = new ArrayList<> (sample.helpers ().size ());
            for (final HelperSample helper: sample.helpers ())
            {
                final List<ConfigurationIdentity> configuration = new ArrayList<> (helper.configuration ().size ());
                for (final ConfigurationSample parameter: helper.configuration ())
                    configuration.add (new ConfigurationIdentity (parameter.parameterId (), parameter.exists (), parameter.rawValue ()));
                helpers.add (new HelperIdentity (
                    helper.bankIndex (),
                    helper.exists (),
                    helper.position (),
                    helper.presetName (),
                    helper.presetCreator (),
                    helper.enabled (),
                    helper.semitonesExists (),
                    configuration));
            }
            return new StructuralIdentity (
                sample.topologySettled (),
                sample.insertionSupported (),
                sample.trackExists (),
                sample.trackId (),
                sample.canHoldNoteData (),
                sample.drumCount (),
                sample.firstDrum (),
                sample.helperCount (),
                helpers,
                ownership);
        }
    }


    /** Live API-21 adapter. All proxies are created eagerly in its constructor. */
    private static final class LiveAdapter implements Adapter
    {
        private static final String SEMITONES_PARAMETER = "CONTENTS/SEMITONES";

        private final CursorTrack selectedTrack;
        private final DeviceBank drumBank;
        private final Device firstDrum;
        private final DeviceBank helperBank;
        private final List<HelperProxy> helpers;
        private long topologyGeneration;
        private String observedTrackId = "";
        private boolean topologySettled;


        private LiveAdapter (final ControllerHost host, final CursorTrack selectedTrack)
        {
            this.selectedTrack = selectedTrack;
            this.drumBank = selectedTrack.createDeviceBank (1);
            this.drumBank.setDeviceMatcher (host.createBitwigDeviceMatcher (DRUM_MACHINE_DEVICE_ID));
            this.firstDrum = this.drumBank.getItemAt (0);
            this.helperBank = selectedTrack.createDeviceBank (HELPER_SCAN_CAPACITY);
            this.helperBank.setDeviceMatcher (host.createBitwigDeviceMatcher (BEND_DEVICE_ID));

            selectedTrack.exists ().markInterested ();
            selectedTrack.channelId ().markInterested ();
            selectedTrack.canHoldNoteData ().markInterested ();
            this.drumBank.itemCount ().markInterested ();
            this.firstDrum.exists ().markInterested ();
            this.firstDrum.position ().markInterested ();
            this.helperBank.itemCount ().markInterested ();

            final List<HelperProxy> subscribedHelpers = new ArrayList<> (HELPER_SCAN_CAPACITY);
            for (int index = 0; index < HELPER_SCAN_CAPACITY; index++)
            {
                final Device device = this.helperBank.getItemAt (index);
                final SpecificBitwigDevice specific = device.createSpecificBitwigDevice (BEND_DEVICE_ID);
                final Parameter semitones = specific.createParameter (SEMITONES_PARAMETER);
                final List<ConfigurationProxy> configuration = new ArrayList<> (CANONICAL_CONFIGURATION.size ());
                for (final ConfigurationSpec specification: CANONICAL_CONFIGURATION)
                    configuration.add (new ConfigurationProxy (specification, specific.createParameter (specification.parameterId ())));
                final HelperProxy helper = new HelperProxy (device, semitones, configuration);
                helper.markInterested ();
                subscribedHelpers.add (helper);
            }
            this.helpers = List.copyOf (subscribedHelpers);

            selectedTrack.channelId ().addValueObserver (trackId -> this.armTopologySettle (host, trackId));
            this.armTopologySettle (host, selectedTrack.channelId ().get ());
        }


        private void armTopologySettle (final ControllerHost host, final String trackId)
        {
            this.observedTrackId = trackId == null ? "" : trackId;
            this.topologySettled = false;
            final String expectedTrackId = this.observedTrackId;
            final long expectedGeneration = Math.incrementExact (this.topologyGeneration);
            host.scheduleTask (() ->
            {
                if (expectedGeneration == this.topologyGeneration && expectedTrackId.equals (this.observedTrackId) && expectedTrackId.equals (this.selectedTrack.channelId ().get ()))
                    this.topologySettled = true;
            }, TOPOLOGY_SETTLE_MILLIS);
        }


        /** {@inheritDoc} */
        @Override
        public HostSample sample ()
        {
            final int drumCount = Math.max (0, this.drumBank.itemCount ().get ());
            final int helperCount = Math.max (0, this.helperBank.itemCount ().get ());
            final int sampledHelpers = Math.min (helperCount, HELPER_SCAN_CAPACITY);
            final List<HelperSample> helperSamples = new ArrayList<> (sampledHelpers);
            for (int index = 0; index < sampledHelpers; index++)
                helperSamples.add (this.helpers.get (index).sample (index));

            return new HostSample (
                this.topologySettled,
                true,
                this.selectedTrack.exists ().get (),
                this.selectedTrack.channelId ().get (),
                this.selectedTrack.canHoldNoteData ().get (),
                drumCount,
                new DeviceSample (this.firstDrum.exists ().get (), this.firstDrum.position ().get ()),
                helperCount,
                helperSamples);
        }


        /** {@inheritDoc} */
        @Override
        public void insertHelperBeforeDrum ()
        {
            this.firstDrum.beforeDeviceInsertionPoint ().insertBitwigDevice (BEND_DEVICE_ID);
        }


        /** {@inheritDoc} */
        @Override
        public void configureHelper (final int helperIndex)
        {
            this.helpers.get (helperIndex).configure ();
        }


        /** {@inheritDoc} */
        @Override
        public void setHelperEnabled (final int helperIndex, final boolean enabled)
        {
            this.helpers.get (helperIndex).device ().isEnabled ().set (enabled);
        }


        /** {@inheritDoc} */
        @Override
        public void setSemitonesNormalized (final int helperIndex, final double normalizedValue)
        {
            this.helpers.get (helperIndex).semitones ().value ().setImmediately (normalizedValue);
        }
    }


    /** Eager proxies for one filtered native Bend candidate. */
    private record HelperProxy (Device device, Parameter semitones, List<ConfigurationProxy> configuration)
    {
        private HelperProxy
        {
            device = Objects.requireNonNull (device, "device");
            semitones = Objects.requireNonNull (semitones, "semitones");
            configuration = List.copyOf (Objects.requireNonNull (configuration, "configuration"));
        }


        private void markInterested ()
        {
            this.device.exists ().markInterested ();
            this.device.position ().markInterested ();
            this.device.presetName ().markInterested ();
            this.device.presetCreator ().markInterested ();
            this.device.isEnabled ().markInterested ();
            this.semitones.exists ().markInterested ();
            this.semitones.value ().markInterested ();
            for (final ConfigurationProxy parameter: this.configuration)
                parameter.markInterested ();
        }


        private HelperSample sample (final int bankIndex)
        {
            final double normalized = this.semitones.exists ().get () ? this.semitones.value ().get () : DEFAULT_NORMALIZED;
            final List<ConfigurationSample> configurationSample = new ArrayList<> (this.configuration.size ());
            for (final ConfigurationProxy parameter: this.configuration)
                configurationSample.add (parameter.sample ());
            return new HelperSample (
                bankIndex,
                this.device.exists ().get (),
                this.device.position ().get (),
                this.device.presetName ().get (),
                this.device.presetCreator ().get (),
                this.device.isEnabled ().get (),
                this.semitones.exists ().get (),
                Double.isFinite (normalized) ? normalized : DEFAULT_NORMALIZED,
                configurationSample);
        }


        private void configure ()
        {
            for (final ConfigurationProxy parameter: this.configuration)
                parameter.setCanonical ();
        }
    }


    /** Eager proxy for one managed native Bend semantic parameter. */
    private record ConfigurationProxy (ConfigurationSpec specification, Parameter parameter)
    {
        private ConfigurationProxy
        {
            specification = Objects.requireNonNull (specification, "specification");
            parameter = Objects.requireNonNull (parameter, "parameter");
        }


        private void markInterested ()
        {
            this.parameter.exists ().markInterested ();
            this.parameter.value ().markInterested ();
        }


        private ConfigurationSample sample ()
        {
            final double raw = this.parameter.exists ().get () ? this.parameter.value ().getRaw () : this.specification.expectedRaw ();
            return new ConfigurationSample (
                this.specification.parameterId (),
                this.parameter.exists ().get (),
                Double.isFinite (raw) ? raw : this.specification.expectedRaw ());
        }


        private void setCanonical ()
        {
            this.parameter.value ().setRaw (this.specification.expectedRaw ());
        }
    }
}
