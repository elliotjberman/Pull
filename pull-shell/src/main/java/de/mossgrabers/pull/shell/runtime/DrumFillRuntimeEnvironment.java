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
import de.mossgrabers.pull.core.api.effect.CoreEffect;
import de.mossgrabers.pull.core.api.effect.PressClipTargetEffect;
import de.mossgrabers.pull.core.api.effect.ReleaseClipTargetsEffect;
import de.mossgrabers.pull.core.api.event.ButtonInputEvent;
import de.mossgrabers.pull.core.api.event.SnapshotChangedEvent;
import de.mossgrabers.pull.core.api.output.RgbColor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
        CoreCapabilities.EFFECT_CLIP_LAUNCH_HOLD, Integer.valueOf (1),
        CoreCapabilities.OUTPUT_RGB_LIGHT, Integer.valueOf (1)));

    private final DrumFillClipHost clipHost;
    private final RuntimeLog log;
    private final LongSupplier clock;
    private final long timeOrigin;
    private final Set<ControlId> pressedControls = new LinkedHashSet<> ();
    private final Map<ControlId, LaunchLease> activeLeases = new HashMap<> ();
    private final Map<ControlId, DrumFillClipHost.LaunchTarget> pendingReleases = new HashMap<> ();

    private ClipCatalogSnapshot clipCatalog;
    private Map<ControlId, ClipTargetId> armedClipTargets;
    private Map<ControlId, RgbColor> fillLightColors = offLights ();
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
        for (final ControlId owner: List.copyOf (this.pendingReleases.keySet ()))
            this.retryPendingRelease (owner, "Pending fill release retry");

        this.clipHost.refresh ();
        final ClipCatalogSnapshot refreshedCatalog = Objects.requireNonNull (this.clipHost.clipCatalog (), "refreshed clip catalog");
        final Map<ControlId, ClipTargetId> refreshedArmedTargets = copyHostBindings (this.clipHost.armedClipTargets ());
        if (refreshedCatalog.equals (this.clipCatalog) && refreshedArmedTargets.equals (this.armedClipTargets))
            return false;

        this.clipCatalog = refreshedCatalog;
        this.armedClipTargets = refreshedArmedTargets;
        this.advanceSnapshotRevision ();
        return true;
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
     * Release only the resources owned by the supplied control. This operation is idempotent.
     *
     * @param owner Logical owner to release
     */
    void safetyRelease (final ControlId owner)
    {
        Objects.requireNonNull (owner, "owner");
        final LaunchLease lease = this.activeLeases.remove (owner);
        this.releaseOwnedTarget (owner, lease == null ? null : lease.target (), "Safety release");

        if (this.pressedControls.remove (owner))
            this.advanceSnapshotRevision ();
    }


    /**
     * Clear stale core output for one pad after an authoritative release was rejected.
     *
     * @param owner Logical fill-pad owner
     */
    void failSafeFillOutputOff (final ControlId owner)
    {
        final ControlId fillOwner = requireFillOwner (owner);
        if (OFF.equals (this.fillLightColors.get (fillOwner)))
            return;

        final Map<ControlId, RgbColor> colors = new LinkedHashMap<> (this.fillLightColors);
        colors.put (fillOwner, OFF);
        this.fillLightColors = Map.copyOf (colors);
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
            else if (action instanceof final PreparedRelease release)
                this.applyRelease (release);
        }
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

        final Map<ControlId, LaunchLease> leases = Map.copyOf (this.activeLeases);
        final Set<ControlId> owners = new HashSet<> (this.pendingReleases.keySet ());
        owners.addAll (leases.keySet ());
        this.activeLeases.clear ();
        for (final ControlId owner: owners)
        {
            final LaunchLease lease = leases.get (owner);
            this.releaseOwnedTarget (owner, lease == null ? null : lease.target (), "Runtime invalidation");
        }

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


    private static Map<ControlId, ClipTargetId> prepareBindings (final Map<ControlId, ClipTargetId> bindings, final ClipCatalogSnapshot clipCatalog)
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
        if (!targetId.equals (desiredBindings.get (owner)))
            throw new IllegalArgumentException ("Clip press must match this result's desired binding");
        final LaunchLease existingLease = this.activeLeases.get (owner);
        if (existingLease != null)
        {
            if (!targetId.equals (existingLease.targetId ()))
                throw new IllegalStateException ("A held fill control cannot be redirected");
            return new PreparedPress (owner, effect.catalogGeneration (), targetId, existingLease.target (), existingLease);
        }

        if (!targetId.equals (this.armedClipTargets.get (owner)))
            throw new IllegalArgumentException ("Clip target is not armed for this fill control");

        final DrumFillClipHost.LaunchTarget target = Objects.requireNonNull (this.clipHost.prepare (owner, effect.catalogGeneration (), targetId), "prepared launch target");
        if (!targetId.equals (target.targetId ()))
            throw new IllegalStateException ("Clip host resolved a different target");
        return new PreparedPress (owner, effect.catalogGeneration (), targetId, target, null);
    }


    private PreparedRelease prepareRelease (final ReleaseClipTargetsEffect effect)
    {
        final ControlId owner = requireFillOwner (effect.owner ());
        return new PreparedRelease (owner, this.activeLeases.get (owner));
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
        final LaunchLease currentLease = this.activeLeases.get (press.owner ());
        if (press.existingLease () != null)
        {
            if (currentLease == press.existingLease ())
                return true;
            this.warn ("Held fill lease changed before its reload acquisition was applied");
            return false;
        }

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
        if (!this.retryPendingRelease (press.owner (), "Pending fill release retry"))
        {
            this.warn ("Fill press was discarded because an earlier release still requires cleanup");
            return false;
        }

        try
        {
            // A host call may apply externally and then throw. The prepared target is retained
            // first so compensation always includes that indeterminate call.
            press.target ().press ();
        }
        catch (final Throwable failure)
        {
            rethrowFatal (failure);
            this.warn ("Fill target press failed: " + sanitize (failure));
            this.releaseOwnedTarget (press.owner (), press.target (), "Partial fill acquisition rollback");
            return false;
        }
        this.activeLeases.put (press.owner (), new LaunchLease (press.catalogGeneration (), press.targetId (), press.target ()));
        return true;
    }


    private void applyRelease (final PreparedRelease release)
    {
        final LaunchLease expectedLease = release.lease ();
        if (expectedLease == null)
        {
            this.retryPendingRelease (release.owner (), "Fill target release retry");
            return;
        }
        if (this.activeLeases.get (release.owner ()) != expectedLease)
            return;

        this.activeLeases.remove (release.owner ());
        this.releaseOwnedTarget (release.owner (), expectedLease.target (), "Fill target release");
    }


    private boolean retryPendingRelease (final ControlId owner, final String operation)
    {
        this.releaseOwnedTarget (owner, null, operation);
        return !this.pendingReleases.containsKey (owner);
    }


    private void releaseOwnedTarget (final ControlId owner, final DrumFillClipHost.LaunchTarget target, final String operation)
    {
        final DrumFillClipHost.LaunchTarget pending = this.pendingReleases.get (owner);
        final DrumFillClipHost.LaunchTarget targetToRelease = pending == null ? target : pending;
        if (targetToRelease == null)
            return;
        if (pending != null && target != null && pending != target)
        {
            this.warn (operation + " found more than one retained target for a fill owner");
            return;
        }

        // Retain ownership before the failure-prone call so apply-then-throw remains retryable.
        this.pendingReleases.put (owner, targetToRelease);
        try
        {
            targetToRelease.release ();
            this.pendingReleases.remove (owner, targetToRelease);
        }
        catch (final Throwable failure)
        {
            rethrowFatal (failure);
            this.warn (operation + " failed: " + sanitize (failure));
        }
    }


    private void advanceSnapshotRevision ()
    {
        this.revision = Math.incrementExact (this.revision);
    }


    private ControllerSnapshot createSnapshot (final long monotonicTimeNanos)
    {
        return new ControllerSnapshot (this.revision, monotonicTimeNanos, CAPABILITIES, this.clipCatalog, this.armedClipTargets, this.pressedControls, Set.of ());
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


    private record PreparedPress (ControlId owner, long catalogGeneration, ClipTargetId targetId, DrumFillClipHost.LaunchTarget target, LaunchLease existingLease) implements PreparedAction
    {
    }


    private record PreparedRelease (ControlId owner, LaunchLease lease) implements PreparedAction
    {
    }


    private record LaunchLease (long catalogGeneration, ClipTargetId targetId, DrumFillClipHost.LaunchTarget target)
    {
    }
}
