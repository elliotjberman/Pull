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
    private final List<LaunchLease> launchChain = new ArrayList<> ();

    private ClipCatalogSnapshot clipCatalog;
    private Map<ControlId, ClipTargetId> armedClipTargets;
    private Map<ControlId, RgbColor> fillLightColors = offLights ();
    private int pendingRetainedDepth = -1;
    private long pendingSnapshotRevision = -1;
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
        final Optional<ControlId> activeOwnerBefore = this.activeClipLaunchOwner ();
        this.retryPendingUnwind ("Pending fill-session unwind retry");
        this.recordActiveOwnerChange (activeOwnerBefore);

        this.clipHost.refresh ();
        final ClipCatalogSnapshot refreshedCatalog = Objects.requireNonNull (this.clipHost.clipCatalog (), "refreshed clip catalog");
        final Map<ControlId, ClipTargetId> refreshedArmedTargets = copyHostBindings (this.clipHost.armedClipTargets ());
        if (!refreshedCatalog.equals (this.clipCatalog) || !refreshedArmedTargets.equals (this.armedClipTargets))
        {
            this.clipCatalog = refreshedCatalog;
            this.armedClipTargets = refreshedArmedTargets;
            this.recordSnapshotChange ();
        }

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
        final Optional<ControlId> activeOwnerBefore = this.activeClipLaunchOwner ();
        final LaunchLease pendingRetainedOwner = this.pendingRetainedDepth > 0 ? this.launchChain.get (this.pendingRetainedDepth - 1) : null;
        if (pendingRetainedOwner != null && fillOwner.equals (pendingRetainedOwner.owner ()))
            this.unwindTo (0, "Safety release during fill-session reactivation");
        else
            this.retryPendingUnwind ("Pending fill-session unwind retry");
        if (!this.hasPendingUnwind ())
        {
            final LaunchLease lease = this.findLease (fillOwner);
            if (lease != null && lease == this.tailLease ())
                this.unwindTo (0, "Safety release");
        }
        this.recordActiveOwnerChange (activeOwnerBefore);

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

        final Optional<ControlId> activeOwnerBefore = this.activeClipLaunchOwner ();
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
        this.recordActiveOwnerChange (activeOwnerBefore);
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

        final Optional<ControlId> activeOwnerBefore = this.activeClipLaunchOwner ();
        this.unwindTo (0, "Runtime invalidation");
        this.recordActiveOwnerChange (activeOwnerBefore);

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

        for (final LaunchLease lease: this.launchChain)
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
        final LaunchLease existingLease = this.findLease (owner);
        if (existingLease != null)
            throw new IllegalStateException ("A retained fill control must be reactivated without another press");
        for (final LaunchLease lease: this.launchChain)
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
        return new PreparedPress (owner, effect.catalogGeneration (), targetId, effect.launchPolicy (), target, this.tailLease ());
    }


    private PreparedReactivate prepareReactivate (final ReactivateClipTargetEffect effect)
    {
        final ControlId owner = requireFillOwner (effect.owner ());
        if (!this.pressedControls.contains (owner))
            throw new IllegalStateException ("The fill control is not physically held");

        final LaunchLease lease = this.findLease (owner);
        if (lease == null)
            throw new IllegalStateException ("The fill control is not retained in the clip-launch session");
        return new PreparedReactivate (owner, lease, this.tailLease ());
    }


    private PreparedRelease prepareRelease (final ReleaseClipTargetsEffect effect)
    {
        final ControlId owner = requireFillOwner (effect.owner ());
        return new PreparedRelease (owner, this.findLease (owner));
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
        if (!this.retryPendingUnwind ("Pending fill-session unwind retry"))
        {
            this.warn ("Fill press was discarded because an earlier unwind still requires cleanup; release and press again");
            return false;
        }
        if (this.tailLease () != press.expectedTail ())
        {
            this.warn ("Fill session changed before its prepared press was applied");
            return false;
        }

        final LaunchLease currentLease = this.findLease (press.owner ());
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

        final int retainedDepth = this.launchChain.size ();
        final LaunchLease lease = new LaunchLease (press.owner (), press.catalogGeneration (), press.targetId (), press.launchPolicy (), press.target ());
        // Retain the new top frame before the failure-prone host call. If the call applies and then
        // throws, ordered compensation can still pop it back to the prior active fill.
        this.launchChain.add (lease);
        try
        {
            press.target ().press (press.launchPolicy ());
        }
        catch (final Throwable failure)
        {
            rethrowFatal (failure);
            this.warn ("Fill target press failed: " + sanitize (failure));
            this.unwindTo (retainedDepth, "Partial fill acquisition rollback");
            return false;
        }
        return true;
    }


    private boolean applyReactivate (final PreparedReactivate reactivate)
    {
        if (!this.retryPendingUnwind ("Pending fill-session unwind retry"))
        {
            this.warn ("Fill reactivation was discarded because an earlier unwind still requires cleanup; release and press again");
            return false;
        }
        if (this.tailLease () != reactivate.expectedTail ())
        {
            this.warn ("Fill session changed before its prepared reactivation was applied");
            return false;
        }

        final LaunchLease currentLease = this.findLease (reactivate.owner ());
        if (currentLease != reactivate.lease ())
        {
            this.warn ("Retained fill lease changed before its reactivation was applied");
            return false;
        }

        final int retainedDepth = this.launchChain.indexOf (currentLease) + 1;
        return retainedDepth == this.launchChain.size () || this.unwindTo (retainedDepth, "Fill-session ancestor reactivation");
    }


    private void applyRelease (final PreparedRelease release)
    {
        this.retryPendingUnwind ("Pending fill-session unwind retry");
        if (this.hasPendingUnwind ())
            return;

        final LaunchLease expectedLease = release.lease ();
        if (expectedLease == null || this.findLease (release.owner ()) != expectedLease)
            return;
        if (expectedLease != this.tailLease ())
            return;

        this.unwindTo (0, "Fill-session release");
    }


    private boolean retryPendingUnwind (final String operation)
    {
        return !this.hasPendingUnwind () || this.unwindTo (this.pendingRetainedDepth, operation);
    }


    private boolean unwindTo (final int retainedDepth, final String operation)
    {
        if (retainedDepth < 0 || retainedDepth > this.launchChain.size ())
            throw new IllegalArgumentException ("retainedDepth is outside the fill-session chain");

        this.pendingRetainedDepth = this.hasPendingUnwind () ? Math.min (this.pendingRetainedDepth, retainedDepth) : retainedDepth;
        while (this.launchChain.size () > this.pendingRetainedDepth)
        {
            final LaunchLease lease = this.tailLease ();
            try
            {
                lease.target ().release ();
                this.launchChain.removeLast ();
            }
            catch (final Throwable failure)
            {
                rethrowFatal (failure);
                this.warn (operation + " failed for " + lease.owner ().value () + ": " + sanitize (failure));
                return false;
            }
        }

        this.pendingRetainedDepth = -1;
        return true;
    }


    private void advanceSnapshotRevision ()
    {
        this.revision = Math.incrementExact (this.revision);
    }


    private void recordActiveOwnerChange (final Optional<ControlId> previousOwner)
    {
        if (previousOwner.equals (this.activeClipLaunchOwner ()))
            return;

        this.recordSnapshotChange ();
    }


    private void recordSnapshotChange ()
    {
        this.advanceSnapshotRevision ();
        this.pendingSnapshotRevision = this.revision;
    }


    private Optional<ControlId> activeClipLaunchOwner ()
    {
        // Keep reporting the newest frame until its release succeeds. A failed release request is
        // not proof that Bitwig stopped playing that fill; successful pops update the tail in
        // newest-to-oldest order as the native Return chain advances.
        final LaunchLease activeLease = this.tailLease ();
        return activeLease == null ? Optional.empty () : Optional.of (activeLease.owner ());
    }


    private boolean hasPendingSnapshotChange ()
    {
        return this.pendingSnapshotRevision >= 0;
    }


    private boolean hasPendingUnwind ()
    {
        return this.pendingRetainedDepth >= 0;
    }


    private LaunchLease findLease (final ControlId owner)
    {
        for (final LaunchLease lease: this.launchChain)
        {
            if (owner.equals (lease.owner ()))
                return lease;
        }
        return null;
    }


    private LaunchLease tailLease ()
    {
        return this.launchChain.isEmpty () ? null : this.launchChain.getLast ();
    }


    private Map<ControlId, ClipTargetId> clipLaunchSessionTargets ()
    {
        final Map<ControlId, ClipTargetId> targets = new LinkedHashMap<> ();
        for (final LaunchLease lease: this.launchChain)
            targets.put (lease.owner (), lease.targetId ());
        return Map.copyOf (targets);
    }


    private ControllerSnapshot createSnapshot (final long monotonicTimeNanos)
    {
        return new ControllerSnapshot (this.revision, monotonicTimeNanos, CAPABILITIES, this.clipCatalog, this.armedClipTargets, this.clipLaunchSessionTargets (), this.activeClipLaunchOwner (), this.pressedControls, Set.of ());
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
