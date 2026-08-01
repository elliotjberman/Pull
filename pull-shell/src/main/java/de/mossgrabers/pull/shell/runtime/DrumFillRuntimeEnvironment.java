// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.shell.runtime;

import de.mossgrabers.pull.core.api.ClipCatalogSnapshot;
import de.mossgrabers.pull.core.api.ClipTargetId;
import de.mossgrabers.pull.core.api.ControlId;
import de.mossgrabers.pull.core.api.ControllerSnapshot;
import de.mossgrabers.pull.core.api.CoreCapabilities;
import de.mossgrabers.pull.core.api.CoreControls;
import de.mossgrabers.pull.core.api.CoreResult;
import de.mossgrabers.pull.core.api.ShellCapabilities;
import de.mossgrabers.pull.core.api.effect.ClipLaunchPolicy;
import de.mossgrabers.pull.core.api.effect.CoreEffect;
import de.mossgrabers.pull.core.api.effect.PressClipTargetEffect;
import de.mossgrabers.pull.core.api.effect.ReactivateClipTargetEffect;
import de.mossgrabers.pull.core.api.effect.ReleaseClipTargetsEffect;
import de.mossgrabers.pull.core.api.event.ButtonInputEvent;
import de.mossgrabers.pull.core.api.event.SnapshotChangedEvent;
import de.mossgrabers.pull.core.api.output.RgbColor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.LongSupplier;


/**
 * Stable-shell state and effect boundary for the reloadable selected-track drum-fill feature.
 */
final class DrumFillRuntimeEnvironment implements CoreRuntimeEnvironment
{
    private static final RgbColor OFF = new RgbColor (0, 0, 0);
    private static final ShellCapabilities CAPABILITIES = new ShellCapabilities (Map.of (
        CoreCapabilities.INPUT_DRUM_FILL, Integer.valueOf (1),
        CoreCapabilities.SNAPSHOT_SELECTED_TRACK_CLIPS, Integer.valueOf (1),
        CoreCapabilities.BINDING_CLIP_TARGET, Integer.valueOf (1),
        CoreCapabilities.SNAPSHOT_CLIP_LAUNCH_SESSION, Integer.valueOf (1),
        CoreCapabilities.EFFECT_CLIP_LAUNCH_HOLD, Integer.valueOf (3),
        CoreCapabilities.OUTPUT_RGB_LIGHT, Integer.valueOf (1)));

    private final DrumFillClipHost clipHost;
    private final RuntimeLog log;
    private final LongSupplier clock;
    private final long timeOrigin;
    private final Set<ControlId> pressedControls = new LinkedHashSet<> ();
    private final FillLaunchSession fillSession = new FillLaunchSession ();

    private ClipCatalogSnapshot clipCatalog;
    private Map<ControlId, ClipTargetId> armedClipTargets;
    private FillSessionView lastObservedSession;
    private Map<ControlId, RgbColor> fillLightColors = offLights ();
    private long pendingSnapshotRevision = -1;
    private long hostSampleRevision;
    private long lastTime;
    private long revision;
    private long eventSequence;
    private long outputGeneration;
    private long committedGeneration;
    private PreparedResult committedResult;


    /**
     * Constructor using the system monotonic clock.
     *
     * @param clipHost Stable selected-track clip host
     * @param log Stable runtime log
     */
    DrumFillRuntimeEnvironment (final DrumFillClipHost clipHost, final RuntimeLog log)
    {
        this (clipHost, log, System::nanoTime);
    }


    /**
     * Constructor with a deterministic clock seam.
     *
     * @param clipHost Stable selected-track clip host
     * @param log Stable runtime log
     * @param clock Monotonic clock
     */
    DrumFillRuntimeEnvironment (final DrumFillClipHost clipHost, final RuntimeLog log, final LongSupplier clock)
    {
        this.clipHost = Objects.requireNonNull (clipHost, "clipHost");
        this.log = Objects.requireNonNull (log, "log");
        this.clock = Objects.requireNonNull (clock, "clock");
        this.timeOrigin = clock.getAsLong ();
        this.clipCatalog = Objects.requireNonNull (clipHost.clipCatalog (), "initial clip catalog");
        this.armedClipTargets = copyHostBindings (clipHost.armedClipTargets ());
        this.lastObservedSession = this.fillSession.view ();
    }


    /** {@inheritDoc} */
    @Override
    public ControllerSnapshot snapshot ()
    {
        return this.createSnapshot (this.now ());
    }


    /**
     * Advance the stable scanner, actuator pool, and retained cleanup work.
     *
     * @return True when the immutable public snapshot changed
     */
    boolean refresh ()
    {
        this.clipHost.refresh ();
        this.hostSampleRevision = Math.incrementExact (this.hostSampleRevision);
        this.fillSession.advance (this.hostSampleRevision);
        final ClipCatalogSnapshot refreshedCatalog = Objects.requireNonNull (this.clipHost.clipCatalog (), "refreshed clip catalog");
        final Map<ControlId, ClipTargetId> refreshedArmedTargets = copyHostBindings (this.clipHost.armedClipTargets ());
        if (!refreshedCatalog.equals (this.clipCatalog) || !refreshedArmedTargets.equals (this.armedClipTargets))
        {
            this.clipCatalog = refreshedCatalog;
            this.armedClipTargets = refreshedArmedTargets;
            this.recordSnapshotChange ();
        }
        this.recordSessionChange ();

        return this.hasPendingSnapshotChange ();
    }


    /**
     * Create an event for a previously observed snapshot change.
     *
     * @return The event
     */
    SnapshotChangedEvent snapshotChangedEvent ()
    {
        return new SnapshotChangedEvent (this.nextEventSequence (), this.now ());
    }


    /**
     * Get the latest immutable snapshot revision available for delivery.
     *
     * @return Current snapshot revision
     */
    long snapshotRevision ()
    {
        return this.revision;
    }


    /**
     * Acknowledge that a core accepted a snapshot containing the supplied revision. A newer
     * change raised while that core result was applied remains pending for a later delivery.
     *
     * @param deliveredRevision Latest revision contained in the accepted snapshot
     */
    void acknowledgeSnapshotChange (final long deliveredRevision)
    {
        if (deliveredRevision < 0)
            throw new IllegalArgumentException ("deliveredRevision must not be negative");
        if (this.hasPendingSnapshotChange () && deliveredRevision >= this.pendingSnapshotRevision)
            this.pendingSnapshotRevision = -1;
    }


    /**
     * Update one authoritative drum-fill input and create its normalized event.
     *
     * @param owner Logical fill-pad owner
     * @param pressed True while that physical pad is held
     * @return The event
     */
    ButtonInputEvent setFillPressed (final ControlId owner, final boolean pressed)
    {
        final ControlId fillOwner = requireFillOwner (owner);
        final boolean changed = pressed ? this.pressedControls.add (fillOwner) : this.pressedControls.remove (fillOwner);
        if (changed)
            this.advanceSnapshotRevision ();
        return new ButtonInputEvent (this.nextEventSequence (), this.now (), fillOwner, pressed);
    }


    /**
     * Test whether one physical fill pad is held.
     *
     * @param owner Logical fill-pad owner
     * @return True when held
     */
    boolean isFillPressed (final ControlId owner)
    {
        return this.pressedControls.contains (requireFillOwner (owner));
    }


    /**
     * Safely release the complete session when the supplied control is active. A retired return
     * ancestor is deliberately left intact until the active owner ends the session.
     *
     * @param owner Logical owner to release
     */
    void safetyRelease (final ControlId owner)
    {
        final ControlId fillOwner = requireFillOwner (owner);
        final LaunchLease pendingRetainedOwner = this.fillSession.pendingRetainedTail ();
        if (pendingRetainedOwner != null && fillOwner.equals (pendingRetainedOwner.owner ()))
            this.fillSession.requestUnwindTo (0, "Safety release during fill-session reactivation", this.hostSampleRevision);
        else if (!this.fillSession.hasPendingUnwind ())
        {
            final LaunchLease lease = this.fillSession.find (fillOwner);
            if (lease != null && lease == this.fillSession.tail ())
                this.fillSession.requestUnwindTo (0, "Safety release", this.hostSampleRevision);
        }
        this.recordSessionChange ();

        if (this.pressedControls.remove (fillOwner))
            this.advanceSnapshotRevision ();
    }


    /**
     * Get one buffered, hardware-independent fill light color.
     *
     * @param owner Logical fill-pad owner
     * @return Desired color
     */
    RgbColor fillLightColor (final ControlId owner)
    {
        return this.fillLightColors.get (requireFillOwner (owner));
    }


    /**
     * Get the core generation that last replaced the complete output buffer.
     *
     * @return The output generation
     */
    long outputGeneration ()
    {
        return this.outputGeneration;
    }


    /** {@inheritDoc} */
    @Override
    public PreparedCoreResult prepare (final CoreResult result)
    {
        Objects.requireNonNull (result, "result");
        final Map<ControlId, RgbColor> preparedColors = prepareOutput (result);
        final Map<ControlId, ClipTargetId> preparedBindings = prepareBindings (result.desiredClipBindings (), this.clipCatalog);
        final List<PreparedAction> preparedActions = this.prepareEffects (result.effects (), preparedBindings);
        return new PreparedResult (preparedColors, this.clipCatalog.generation (), preparedBindings, preparedActions);
    }


    /** {@inheritDoc} */
    @Override
    public void commit (final long generation, final PreparedCoreResult result)
    {
        final PreparedResult prepared = (PreparedResult) result;
        this.committedResult = prepared;
        this.committedGeneration = generation;
        this.fillLightColors = prepared.fillLightColors ();
        this.outputGeneration = generation;
    }


    /** {@inheritDoc} */
    @Override
    public void apply (final long generation)
    {
        if (generation != this.committedGeneration)
            return;

        final PreparedResult prepared = this.committedResult;
        if (prepared == null)
            return;
        this.committedResult = null;

        this.clipHost.setDesiredBindings (prepared.catalogGeneration (), prepared.desiredClipBindings ());
        boolean acquisitionFailed = false;
        for (final PreparedAction action: prepared.actions ())
        {
            if (action instanceof final PreparedPress press)
            {
                if (!acquisitionFailed)
                    acquisitionFailed = !this.applyPress (press);
            }
            else if (action instanceof final PreparedReactivate reactivate)
            {
                if (!acquisitionFailed)
                    acquisitionFailed = !this.applyReactivate (reactivate);
            }
            else if (action instanceof final PreparedRelease release)
                this.applyRelease (release);
        }
        this.recordSessionChange ();
    }


    /** {@inheritDoc} */
    @Override
    public void invalidate (final long generation)
    {
        this.committedResult = null;
        this.committedGeneration = generation;
        this.fillLightColors = offLights ();
        this.outputGeneration = generation;

        try
        {
            this.clipHost.setDesiredBindings (this.clipCatalog.generation (), Map.of ());
        }
        catch (final RuntimeException failure)
        {
            this.warn ("Clearing fill bindings during invalidation failed: " + sanitize (failure));
        }

        // Runtime invalidation is terminal extension shutdown, not an ordinary child-core reload.
        // API 21 offers no post-exit observation window, so this can submit only the current top
        // Return as best-effort cleanup; normal running unwinds continue from later refreshes.
        this.fillSession.requestUnwindTo (0, "Runtime invalidation", this.hostSampleRevision);
        this.recordSessionChange ();

        if (!this.pressedControls.isEmpty ())
        {
            this.pressedControls.clear ();
            this.advanceSnapshotRevision ();
        }
    }


    private static Map<ControlId, RgbColor> prepareOutput (final CoreResult result)
    {
        final Map<ControlId, RgbColor> colors = new LinkedHashMap<> (offLights ());
        for (final Map.Entry<ControlId, RgbColor> light: result.desiredOutput ().lights ().entrySet ())
        {
            final ControlId owner = requireFillOwner (light.getKey ());
            final RgbColor requested = Objects.requireNonNull (light.getValue (), "fill light color");
            colors.put (owner, new RgbColor (requested.red (), requested.green (), requested.blue ()));
        }
        return Map.copyOf (colors);
    }


    private Map<ControlId, ClipTargetId> prepareBindings (final Map<ControlId, ClipTargetId> bindings, final ClipCatalogSnapshot clipCatalog)
    {
        final Map<ControlId, ClipTargetId> copy = new LinkedHashMap<> ();
        final Set<ClipTargetId> catalogTargets = new HashSet<> ();
        clipCatalog.clips ().forEach (clip -> catalogTargets.add (clip.targetId ()));
        final Set<ClipTargetId> targets = new HashSet<> ();
        for (final Map.Entry<ControlId, ClipTargetId> binding: bindings.entrySet ())
        {
            final ControlId owner = requireFillOwner (binding.getKey ());
            final ClipTargetId requested = Objects.requireNonNull (binding.getValue (), "desired clip target");
            final ClipTargetId target = new ClipTargetId (requested.value ());
            if (!catalogTargets.contains (target))
                throw new IllegalArgumentException ("Desired binding targets an unknown catalog clip");
            if (!targets.add (target))
                throw new IllegalArgumentException ("Desired clip targets must be unique");
            copy.put (owner, target);
        }

        for (final LaunchLease lease: this.fillSession.frames ())
        {
            for (final Map.Entry<ControlId, ClipTargetId> binding: copy.entrySet ())
            {
                if (!lease.owner ().equals (binding.getKey ()) && lease.targetId ().equals (binding.getValue ()))
                    throw new IllegalArgumentException ("A retained clip target cannot be rebound to another fill control");
            }
        }
        return Map.copyOf (copy);
    }


    private List<PreparedAction> prepareEffects (final List<CoreEffect> effects, final Map<ControlId, ClipTargetId> desiredBindings)
    {
        final Set<ControlId> owners = new HashSet<> ();
        for (final CoreEffect effect: effects)
        {
            final ControlId owner;
            if (effect instanceof final PressClipTargetEffect press)
                owner = requireFillOwner (press.owner ());
            else if (effect instanceof final ReactivateClipTargetEffect reactivate)
                owner = requireFillOwner (reactivate.owner ());
            else if (effect instanceof final ReleaseClipTargetsEffect release)
                owner = requireFillOwner (release.owner ());
            else
                throw new IllegalArgumentException ("Core requested an unsupported effect " + Objects.requireNonNull (effect, "effect").getClass ().getSimpleName ());

            if (!owners.add (owner))
                throw new IllegalArgumentException ("Core requested multiple clip effects for one owner");
        }

        final List<PreparedAction> actions = new ArrayList<> (effects.size ());
        for (final CoreEffect effect: effects)
        {
            if (effect instanceof final PressClipTargetEffect press)
                actions.add (this.preparePress (press, desiredBindings));
            else if (effect instanceof final ReactivateClipTargetEffect reactivate)
                actions.add (this.prepareReactivate (reactivate));
            else if (effect instanceof final ReleaseClipTargetsEffect release)
                actions.add (this.prepareRelease (release));
        }
        return List.copyOf (actions);
    }


    private PreparedPress preparePress (final PressClipTargetEffect effect, final Map<ControlId, ClipTargetId> desiredBindings)
    {
        final ControlId owner = requireFillOwner (effect.owner ());
        if (!this.pressedControls.contains (owner))
            throw new IllegalStateException ("The fill control is not physically held");
        if (effect.catalogGeneration () != this.clipCatalog.generation ())
            throw new IllegalArgumentException ("Clip-catalog generation is stale");

        final ClipTargetId targetId = new ClipTargetId (effect.target ().value ());
        final LaunchLease existingLease = this.fillSession.find (owner);
        if (existingLease != null)
            throw new IllegalStateException ("A retained fill control must be reactivated without another press");
        for (final LaunchLease lease: this.fillSession.frames ())
        {
            if (targetId.equals (lease.targetId ()))
                throw new IllegalStateException ("A retained clip target cannot be acquired by another fill control");
        }

        if (!targetId.equals (desiredBindings.get (owner)))
            throw new IllegalArgumentException ("New clip press must match this result's desired binding");
        if (!targetId.equals (this.armedClipTargets.get (owner)))
            throw new IllegalArgumentException ("Clip target is not armed for this fill control");

        final DrumFillClipHost.LaunchTarget target = Objects.requireNonNull (this.clipHost.prepare (owner, effect.catalogGeneration (), targetId), "prepared launch target");
        if (!targetId.equals (target.targetId ()))
            throw new IllegalStateException ("Clip host resolved a different target");
        return new PreparedPress (owner, effect.catalogGeneration (), targetId, effect.launchPolicy (), target, this.fillSession.tail ());
    }


    private PreparedReactivate prepareReactivate (final ReactivateClipTargetEffect effect)
    {
        final ControlId owner = requireFillOwner (effect.owner ());
        if (!this.pressedControls.contains (owner))
            throw new IllegalStateException ("The fill control is not physically held");

        final LaunchLease lease = this.fillSession.find (owner);
        if (lease == null)
            throw new IllegalStateException ("The fill control is not retained in the clip-launch session");
        return new PreparedReactivate (owner, lease, this.fillSession.tail ());
    }


    private PreparedRelease prepareRelease (final ReleaseClipTargetsEffect effect)
    {
        final ControlId owner = requireFillOwner (effect.owner ());
        return new PreparedRelease (owner, this.fillSession.find (owner));
    }


    private static ControlId requireFillOwner (final ControlId owner)
    {
        Objects.requireNonNull (owner, "fill owner");
        if (!CoreControls.DRUM_FILLS.contains (owner))
            throw new IllegalArgumentException ("Unsupported fill owner");
        return owner;
    }


    private boolean applyPress (final PreparedPress press)
    {
        if (this.fillSession.hasPendingUnwind ())
        {
            this.warn ("Fill press was discarded because an earlier unwind still requires cleanup; release and press again");
            return false;
        }
        if (this.fillSession.tail () != press.expectedTail ())
        {
            this.warn ("Fill session changed before its prepared press was applied");
            return false;
        }

        final LaunchLease currentLease = this.fillSession.find (press.owner ());
        if (currentLease != null)
        {
            this.warn ("Fill lease was acquired before its prepared press was applied");
            return false;
        }
        if (this.clipCatalog.generation () != press.catalogGeneration ())
        {
            this.warn ("Prepared fill press was discarded after the clip catalog changed");
            return false;
        }

        final int retainedDepth = this.fillSession.size ();
        final LaunchLease lease = new LaunchLease (press.owner (), press.catalogGeneration (), press.targetId (), press.launchPolicy (), press.target ());
        // Retain the new top frame before the failure-prone host call. If the call applies and then
        // throws, ordered compensation can still pop it back to the prior active fill.
        this.fillSession.add (lease);
        try
        {
            press.target ().press (press.launchPolicy ());
        }
        catch (final Throwable failure)
        {
            rethrowFatal (failure);
            this.warn ("Fill target press failed: " + sanitize (failure));
            this.fillSession.requestUnwindTo (retainedDepth, "Partial fill acquisition rollback", this.hostSampleRevision);
            return false;
        }
        return true;
    }


    private boolean applyReactivate (final PreparedReactivate reactivate)
    {
        if (this.fillSession.hasPendingUnwind ())
        {
            this.warn ("Fill reactivation was discarded because an earlier unwind still requires cleanup; release and press again");
            return false;
        }
        if (this.fillSession.tail () != reactivate.expectedTail ())
        {
            this.warn ("Fill session changed before its prepared reactivation was applied");
            return false;
        }

        final LaunchLease currentLease = this.fillSession.find (reactivate.owner ());
        if (currentLease != reactivate.lease ())
        {
            this.warn ("Retained fill lease changed before its reactivation was applied");
            return false;
        }

        final int retainedDepth = this.fillSession.indexOf (currentLease) + 1;
        return retainedDepth == this.fillSession.size () || this.fillSession.requestUnwindTo (retainedDepth, "Fill-session ancestor reactivation", this.hostSampleRevision);
    }


    private void applyRelease (final PreparedRelease release)
    {
        if (this.fillSession.hasPendingUnwind ())
            return;

        final LaunchLease expectedLease = release.lease ();
        if (expectedLease == null || this.fillSession.find (release.owner ()) != expectedLease)
            return;
        if (expectedLease != this.fillSession.tail ())
            return;

        this.fillSession.requestUnwindTo (0, "Fill-session release", this.hostSampleRevision);
    }


    private void advanceSnapshotRevision ()
    {
        this.revision = Math.incrementExact (this.revision);
    }


    private void recordSessionChange ()
    {
        final FillSessionView observed = this.fillSession.view ();
        if (observed.equals (this.lastObservedSession))
            return;

        this.lastObservedSession = observed;
        this.recordSnapshotChange ();
    }


    private void recordSnapshotChange ()
    {
        this.advanceSnapshotRevision ();
        this.pendingSnapshotRevision = this.revision;
    }


    private boolean hasPendingSnapshotChange ()
    {
        return this.pendingSnapshotRevision >= 0;
    }


    private ControllerSnapshot createSnapshot (final long monotonicTimeNanos)
    {
        final FillSessionView session = this.fillSession.view ();
        return new ControllerSnapshot (this.revision, monotonicTimeNanos, CAPABILITIES, this.clipCatalog, this.armedClipTargets, session.targets (), session.activeOwner (), this.pressedControls, Set.of ());
    }


    private long nextEventSequence ()
    {
        this.eventSequence = Math.incrementExact (this.eventSequence);
        return this.eventSequence;
    }


    private long now ()
    {
        final long elapsed = Math.max (0, this.clock.getAsLong () - this.timeOrigin);
        this.lastTime = Math.max (this.lastTime, elapsed);
        return this.lastTime;
    }


    private void warn (final String message)
    {
        try
        {
            this.log.warn (message);
        }
        catch (final RuntimeException ignored)
        {
            // Logging cannot change stable-shell ownership or cleanup.
        }
    }


    private static Map<ControlId, ClipTargetId> copyHostBindings (final Map<ControlId, ClipTargetId> bindings)
    {
        final Map<ControlId, ClipTargetId> copy = new LinkedHashMap<> ();
        for (final Map.Entry<ControlId, ClipTargetId> binding: Objects.requireNonNull (bindings, "armed clip targets").entrySet ())
        {
            final ControlId owner = requireFillOwner (binding.getKey ());
            final ClipTargetId target = Objects.requireNonNull (binding.getValue (), "armed clip target");
            copy.put (owner, new ClipTargetId (target.value ()));
        }
        return Map.copyOf (copy);
    }


    private static Map<ControlId, RgbColor> offLights ()
    {
        final Map<ControlId, RgbColor> colors = new LinkedHashMap<> ();
        for (final ControlId owner: CoreControls.DRUM_FILLS)
            colors.put (owner, OFF);
        return Map.copyOf (colors);
    }


    private static String sanitize (final Throwable failure)
    {
        final String message = failure.getMessage ();
        return failure.getClass ().getSimpleName () + (message == null || message.isBlank () ? "" : ": " + message);
    }


    private static void rethrowFatal (final Throwable failure)
    {
        if (failure instanceof final VirtualMachineError virtualMachineError)
            throw virtualMachineError;
    }


    /**
     * Ordered fill frames above the opaque playback state that Bitwig owned before the first
     * fill. Bitwig owns that base target (a launcher clip or Arrangement), so the shell preserves
     * it by unwinding one observed Return transition at a time instead of trying to identify or
     * relaunch it.
     */
    private final class FillLaunchSession
    {
        private static final int BASE_RESTORATION_STABLE_SAMPLES = 2;

        private final List<LaunchLease> frames = new ArrayList<> ();

        private int pendingRetainedDepth = -1;
        private int consecutiveBaseSamples;
        private PendingRelease pendingRelease;
        private String pendingOperation = "Fill-session unwind";


        private List<LaunchLease> frames ()
        {
            return List.copyOf (this.frames);
        }


        private int size ()
        {
            return this.frames.size ();
        }


        private int indexOf (final LaunchLease lease)
        {
            return this.frames.indexOf (lease);
        }


        private void add (final LaunchLease lease)
        {
            if (this.hasPendingUnwind ())
                throw new IllegalStateException ("A fill frame cannot be acquired during an unwind");
            this.frames.add (Objects.requireNonNull (lease, "lease"));
            this.consecutiveBaseSamples = 0;
        }


        private LaunchLease find (final ControlId owner)
        {
            for (final LaunchLease lease: this.frames)
            {
                if (owner.equals (lease.owner ()))
                    return lease;
            }
            return null;
        }


        private LaunchLease tail ()
        {
            return this.frames.isEmpty () ? null : this.frames.getLast ();
        }


        private LaunchLease pendingRetainedTail ()
        {
            return this.pendingRetainedDepth > 0 && this.pendingRetainedDepth <= this.frames.size () ? this.frames.get (this.pendingRetainedDepth - 1) : null;
        }


        private boolean hasPendingUnwind ()
        {
            return this.pendingRetainedDepth >= 0;
        }


        private boolean requestUnwindTo (final int retainedDepth, final String operation, final long sampleRevision)
        {
            if (retainedDepth < 0 || retainedDepth > this.frames.size ())
                throw new IllegalArgumentException ("retainedDepth is outside the fill-session path");

            this.pendingRetainedDepth = this.hasPendingUnwind () ? Math.min (this.pendingRetainedDepth, retainedDepth) : retainedDepth;
            this.pendingOperation = Objects.requireNonNull (operation, "operation");
            this.consecutiveBaseSamples = 0;
            if (this.frames.size () <= this.pendingRetainedDepth)
            {
                this.finishUnwind ();
                return true;
            }

            if (this.pendingRelease == null)
                this.requestTailRelease (sampleRevision);
            return !this.hasPendingUnwind ();
        }


        private void advance (final long sampleRevision)
        {
            if (!this.hasPendingUnwind ())
                return;

            final PendingRelease release = this.pendingRelease;
            if (release != null)
            {
                if (sampleRevision <= release.issuedAfterSample () || isBusy (release.lease ().target ().playbackState ()))
                    return;

                if (release.lease () != this.tail ())
                    throw new IllegalStateException ("The acknowledged fill release is not the newest session frame");
                this.retireTail ();
                this.pendingRelease = null;
                this.consecutiveBaseSamples = 0;
            }

            this.advanceTowardRetainedDepth (sampleRevision);
        }


        private void advanceTowardRetainedDepth (final long sampleRevision)
        {
            if (this.frames.size () <= this.pendingRetainedDepth)
            {
                if (this.pendingRetainedDepth == 0 || isPlaying (this.tail ()))
                    this.finishUnwind ();
                return;
            }

            final int activeIndex = this.newestPlayingIndex ();
            if (activeIndex >= this.pendingRetainedDepth)
            {
                this.consecutiveBaseSamples = 0;
                for (int index = this.frames.size () - 1; index > activeIndex; index--)
                {
                    if (isBusy (this.frames.get (index).target ().playbackState ()))
                        return;
                }
                while (this.frames.size () - 1 > activeIndex)
                    this.retireTail ();
                this.requestTailRelease (sampleRevision);
                return;
            }

            for (int index = this.pendingRetainedDepth; index < this.frames.size (); index++)
            {
                if (isBusy (this.frames.get (index).target ().playbackState ()))
                {
                    this.consecutiveBaseSamples = 0;
                    return;
                }
            }

            if (this.pendingRetainedDepth == 0)
            {
                // A Return transition may briefly report the released frame stopped before its
                // predecessor starts. Require a second coherent all-idle sample before concluding
                // that Bitwig skipped the retained ancestry and restored the opaque base directly.
                this.consecutiveBaseSamples = Math.min (BASE_RESTORATION_STABLE_SAMPLES, this.consecutiveBaseSamples + 1);
                if (this.consecutiveBaseSamples < BASE_RESTORATION_STABLE_SAMPLES)
                    return;
                while (!this.frames.isEmpty ())
                    this.retireTail ();
                this.finishUnwind ();
            }
        }


        private int newestPlayingIndex ()
        {
            for (int index = this.frames.size () - 1; index >= 0; index--)
            {
                if (isPlaying (this.frames.get (index)))
                    return index;
            }
            return -1;
        }


        private void requestTailRelease (final long sampleRevision)
        {
            final LaunchLease lease = this.tail ();
            if (lease == null || this.frames.size () <= this.pendingRetainedDepth)
            {
                this.finishUnwind ();
                return;
            }

            try
            {
                lease.target ().release ();
                this.pendingRelease = new PendingRelease (lease, sampleRevision);
            }
            catch (final Throwable failure)
            {
                rethrowFatal (failure);
                DrumFillRuntimeEnvironment.this.warn (this.pendingOperation + " failed for " + lease.owner ().value () + ": " + sanitize (failure));
            }
        }


        private void retireTail ()
        {
            final LaunchLease lease = this.tail ();
            if (lease == null)
                return;
            try
            {
                lease.target ().retire ();
            }
            catch (final Throwable failure)
            {
                rethrowFatal (failure);
                DrumFillRuntimeEnvironment.this.warn ("Fill target retirement failed for " + lease.owner ().value () + ": " + sanitize (failure));
            }
            finally
            {
                this.frames.removeLast ();
            }
        }


        private void finishUnwind ()
        {
            this.pendingRetainedDepth = -1;
            this.pendingRelease = null;
            this.pendingOperation = "Fill-session unwind";
            this.consecutiveBaseSamples = 0;
        }


        private FillSessionView view ()
        {
            final Map<ControlId, ClipTargetId> targets = new LinkedHashMap<> ();
            for (final LaunchLease lease: this.frames)
                targets.put (lease.owner (), lease.targetId ());

            final int activeIndex = this.newestPlayingIndex ();
            final Optional<ControlId> activeOwner = activeIndex < 0 ? Optional.empty () : Optional.of (this.frames.get (activeIndex).owner ());
            return new FillSessionView (FillSessionBase.BITWIG_OWNED_CLIP_OR_ARRANGEMENT, targets, activeOwner);
        }


        private static boolean isPlaying (final LaunchLease lease)
        {
            return lease != null && lease.target ().playbackState ().playing ();
        }


        private static boolean isBusy (final DrumFillClipHost.PlaybackState state)
        {
            return state.playing () || state.playbackQueued () || state.stopQueued ();
        }
    }


    private enum FillSessionBase
    {
        BITWIG_OWNED_CLIP_OR_ARRANGEMENT
    }


    private record FillSessionView (FillSessionBase base, Map<ControlId, ClipTargetId> targets, Optional<ControlId> activeOwner)
    {
        private FillSessionView
        {
            base = Objects.requireNonNull (base, "base");
            targets = Map.copyOf (Objects.requireNonNull (targets, "targets"));
            activeOwner = Objects.requireNonNull (activeOwner, "activeOwner");
        }
    }


    private record PendingRelease (LaunchLease lease, long issuedAfterSample)
    {
        private PendingRelease
        {
            lease = Objects.requireNonNull (lease, "lease");
            if (issuedAfterSample < 0)
                throw new IllegalArgumentException ("issuedAfterSample must not be negative");
        }
    }


    private record PreparedResult (Map<ControlId, RgbColor> fillLightColors, long catalogGeneration, Map<ControlId, ClipTargetId> desiredClipBindings, List<PreparedAction> actions) implements PreparedCoreResult
    {
        private PreparedResult
        {
            fillLightColors = Map.copyOf (fillLightColors);
            desiredClipBindings = Map.copyOf (desiredClipBindings);
            actions = List.copyOf (actions);
        }
    }


    private interface PreparedAction
    {
        /**
         * Get the logical effect owner.
         *
         * @return The owner
         */
        ControlId owner ();
    }


    private record PreparedPress (ControlId owner, long catalogGeneration, ClipTargetId targetId, ClipLaunchPolicy launchPolicy, DrumFillClipHost.LaunchTarget target, LaunchLease expectedTail) implements PreparedAction
    {
    }


    private record PreparedReactivate (ControlId owner, LaunchLease lease, LaunchLease expectedTail) implements PreparedAction
    {
    }


    private record PreparedRelease (ControlId owner, LaunchLease lease) implements PreparedAction
    {
    }


    private record LaunchLease (ControlId owner, long catalogGeneration, ClipTargetId targetId, ClipLaunchPolicy launchPolicy, DrumFillClipHost.LaunchTarget target)
    {
    }
}
