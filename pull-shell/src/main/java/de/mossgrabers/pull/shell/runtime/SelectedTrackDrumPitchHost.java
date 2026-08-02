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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.LongSupplier;


/**
 * Owns the selected track's managed Drum Pitch helper.
 *
 * <p>The helper is the exact branded Pull preset for Bitwig's native Bend Note FX. It may live
 * anywhere before the first top-level Drum Machine. Other Bend devices are user-owned and are
 * never adopted or changed. A missing helper is inserted from a neutral bundled preset on the
 * first write when that preset is available.</p>
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
    static final String HELPER_PRESET_NAME = BundledDrumPitchPreset.PRESET_NAME;
    static final String HELPER_PRESET_CREATOR = BundledDrumPitchPreset.PRESET_CREATOR;
    static final int HELPER_SCAN_CAPACITY = 16;

    private static final UUID DRUM_MACHINE_DEVICE_ID = UUID.fromString ("8ea97e45-0255-40fd-bc7e-94419741e9d1");
    private static final UUID BEND_DEVICE_ID = UUID.fromString ("6aec6e78-9c1e-4c0b-8a88-0c2c37890a1d");
    private static final ParameterTargetId TARGET_ID = new ParameterTargetId (0);
    private static final double DEFAULT_NORMALIZED = 0.5;
    private static final double VALUE_TOLERANCE = 0.000001;
    private static final long TOPOLOGY_SETTLE_MILLIS = 200;
    private static final long INSERT_RETRY_NANOS = 10_000_000_000L;
    private static final long CONFIGURATION_RETRY_NANOS = 1_000_000_000L;
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
    private boolean insertionCapacityWarningReported;
    private boolean configurationFailureReported;
    private boolean valueFailureReported;


    /**
     * Create the API-21 device matchers, banks, and parameter proxies on a shared selected-track
     * cursor during extension initialization.
     *
     * @param host Bitwig controller host
     * @param selectedTrack Shared selected-track cursor
     * @param helperPresetPath Optional absolute path to the materialized branded helper preset
     */
    SelectedTrackDrumPitchHost (final ControllerHost host, final CursorTrack selectedTrack, final Optional<Path> helperPresetPath)
    {
        this (host, selectedTrack, helperPresetPath, message -> { });
    }


    /**
     * Create the live managed helper host with a recoverable-warning sink.
     *
     * @param host Bitwig controller host
     * @param selectedTrack Shared selected-track cursor
     * @param helperPresetPath Optional absolute path to the materialized branded helper preset
     * @param warningSink Recoverable diagnostic sink
     */
    SelectedTrackDrumPitchHost (final ControllerHost host, final CursorTrack selectedTrack, final Optional<Path> helperPresetPath, final Consumer<String> warningSink)
    {
        this (new LiveAdapter (
            Objects.requireNonNull (host, "host"),
            Objects.requireNonNull (selectedTrack, "selectedTrack"),
            usableHelperPresetPath (helperPresetPath)),
            System::nanoTime,
            warningSink);
    }


    /**
     * Compatibility overload for callers that have a materialized preset path.
     *
     * @param host Bitwig controller host
     * @param selectedTrack Shared selected-track cursor
     * @param helperPresetPath Materialized helper preset path, or {@code null} when unavailable
     */
    SelectedTrackDrumPitchHost (final ControllerHost host, final CursorTrack selectedTrack, final Path helperPresetPath)
    {
        this (host, selectedTrack, Optional.ofNullable (helperPresetPath));
    }


    /**
     * Deterministic model-free test seam.
     *
     * @param adapter Subscribed device adapter
     */
    SelectedTrackDrumPitchHost (final Adapter adapter)
    {
        this (adapter, System::nanoTime, message -> { });
    }


    /**
     * Deterministic model-free test seam with a monotonic clock.
     *
     * @param adapter Subscribed device adapter
     * @param clock Monotonic nanosecond clock
     */
    SelectedTrackDrumPitchHost (final Adapter adapter, final LongSupplier clock)
    {
        this (adapter, clock, message -> { });
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
        this.adapter = Objects.requireNonNull (adapter, "adapter");
        this.clock = Objects.requireNonNull (clock, "clock");
        this.warningSink = Objects.requireNonNull (warningSink, "warningSink");
    }


    /** {@inheritDoc} */
    @Override
    public boolean refresh ()
    {
        final State previous = this.state;
        final HostSample sample = Objects.requireNonNull (this.adapter.sample (), "drum-pitch sample");
        this.accept (sample);
        this.reconcile (sample);
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
        final HostSample freshSample = Objects.requireNonNull (this.adapter.sample (), "drum-pitch sample");
        final StructuralIdentity freshIdentity = StructuralIdentity.from (freshSample);
        if (expectedIdentity == null || !expectedIdentity.equals (freshIdentity) || !isConfirmed (freshSample))
        {
            this.accept (freshSample);
            throw new IllegalStateException ("Drum Pitch target identity changed before write");
        }

        final Resolution resolution = resolve (freshSample);
        final boolean insertionCanProgress = resolution.kind () != ResolutionKind.NEEDS_INSERTION || this.insertionCanProgress (freshSample.trackId ());
        if ((resolution.kind () != ResolutionKind.NEEDS_INSERTION && resolution.kind () != ResolutionKind.READY) || !insertionCanProgress)
        {
            this.accept (freshSample);
            throw new IllegalStateException ("Drum Pitch target became unavailable before write");
        }

        this.pendingNormalizedValue = Double.valueOf (normalizedValue);
        this.submitPending (freshSample, resolution, true);
    }


    private void accept (final HostSample sample)
    {
        final String sampledTrackId = sample.trackExists () ? sample.trackId () : "";
        if (!sampledTrackId.equals (this.activeTrackId))
        {
            this.activeTrackId = sampledTrackId;
            this.pendingNormalizedValue = null;
            this.nextValueAttemptNanos = 0;
            this.nextConfigurationAttemptNanos = 0;
            this.configurationFailureReported = false;
            this.valueFailureReported = false;
        }

        final boolean confirmed = isConfirmed (sample);
        if (confirmed && containsOwnedHelper (sample))
        {
            this.insertionAttempts.remove (sample.trackId ());
            if (this.insertionAttempts.size () < MAX_TRACKED_INSERTIONS)
                this.insertionCapacityWarningReported = false;
        }

        final StructuralIdentity identity = StructuralIdentity.from (sample);
        if (!identity.equals (this.observedIdentity))
        {
            this.observedIdentity = identity;
            this.generation = Math.incrementExact (this.generation);
        }

        final Resolution resolution = confirmed ? resolve (sample) : Resolution.unavailable ();
        final boolean insertionCanProgress = resolution.kind () != ResolutionKind.NEEDS_INSERTION || this.insertionCanProgress (sample.trackId ());
        if (!insertionCanProgress)
            this.pendingNormalizedValue = null;
        final boolean available = confirmed && insertionCanProgress && (resolution.kind () == ResolutionKind.NEEDS_INSERTION || resolution.kind () == ResolutionKind.READY);
        final double normalizedValue = resolution.kind () == ResolutionKind.READY ? requireNormalized (resolution.helper ().semitonesNormalized ()) : DEFAULT_NORMALIZED;
        final Slot slot = new Slot (TARGET_ID, PAGE_NAME, PARAMETER_NAME, available, normalizedValue, confirmed);
        this.state = new State (this.generation, sampledTrackId, PAGE_NAME, List.of (slot));
    }


    private void reconcile (final HostSample sample)
    {
        if (!isConfirmed (sample))
            return;

        final Resolution resolution = resolve (sample);
        if (resolution.kind () == ResolutionKind.NEEDS_CONFIGURATION)
        {
            this.repairConfiguration (resolution.helper ());
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
                this.requestInsertion (sample.trackId ());
                break;
            case WAITING_FOR_METADATA:
            case WAITING_FOR_PARAMETERS:
            case NEEDS_CONFIGURATION:
                break;
            case READY:
                this.requestValue (resolution.helper (), immediateValue);
                break;
            case UNAVAILABLE:
                this.pendingNormalizedValue = null;
                break;
        }
    }


    private void requestInsertion (final String trackId)
    {
        final long now = this.clock.getAsLong ();
        InsertionAttempt attempt = this.insertionAttempts.get (trackId);
        if (attempt == null)
        {
            if (this.insertionAttempts.size () >= MAX_TRACKED_INSERTIONS)
                return;
            attempt = new InsertionAttempt ();
            this.insertionAttempts.put (trackId, attempt);
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


    private static Resolution resolve (final HostSample sample)
    {
        if (!sample.trackExists () || sample.trackId ().isEmpty () || !sample.canHoldNoteData () || sample.drumCount () < 1 || !sample.firstDrum ().exists () || sample.helperCount () > HELPER_SCAN_CAPACITY)
            return Resolution.unavailable ();

        HelperSample owned = null;
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
                    if (owned != null)
                        return Resolution.unavailable ();
                    owned = helper;
                    break;
                case UNOWNED:
                    break;
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
        return new Resolution (ResolutionKind.READY, owned);
    }


    private static boolean containsOwnedHelper (final HostSample sample)
    {
        for (final HelperSample helper: sample.helpers ())
        {
            if (helper.ownership () == Ownership.OWNED)
                return true;
        }
        return false;
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


    private static Optional<Path> usableHelperPresetPath (final Optional<Path> helperPresetPath)
    {
        final Optional<Path> candidate = Objects.requireNonNull (helperPresetPath, "helperPresetPath");
        if (candidate.isEmpty () || !candidate.get ().isAbsolute ())
            return Optional.empty ();
        final Path normalized = candidate.get ().normalize ();
        return Files.isRegularFile (normalized) ? Optional.of (normalized) : Optional.empty ();
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
            if (this.presetName.isEmpty () && this.presetCreator.isEmpty ())
                return Ownership.HYDRATING;
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
        private int attempts;
        private long retryAtNanos;
        private boolean exhaustionWarningReported;
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


    private record StructuralIdentity (boolean topologySettled, boolean insertionSupported, boolean trackExists, String trackId, boolean canHoldNoteData, int drumCount, DeviceSample firstDrum, int helperCount, List<HelperIdentity> helpers)
    {
        private StructuralIdentity
        {
            trackId = Objects.requireNonNull (trackId, "trackId");
            firstDrum = Objects.requireNonNull (firstDrum, "firstDrum");
            helpers = List.copyOf (Objects.requireNonNull (helpers, "helpers"));
        }


        private static StructuralIdentity from (final HostSample sample)
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
                helpers);
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
        private final Optional<Path> helperPresetPath;

        private long topologyGeneration;
        private String observedTrackId = "";
        private boolean topologySettled;


        private LiveAdapter (final ControllerHost host, final CursorTrack selectedTrack, final Optional<Path> helperPresetPath)
        {
            this.selectedTrack = selectedTrack;
            this.helperPresetPath = helperPresetPath;
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
                this.helperPresetPath.isPresent (),
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
            final Path path = this.helperPresetPath.orElseThrow (() -> new IllegalStateException ("Drum Pitch helper preset is unavailable"));
            this.firstDrum.beforeDeviceInsertionPoint ().insertFile (path.toString ());
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
