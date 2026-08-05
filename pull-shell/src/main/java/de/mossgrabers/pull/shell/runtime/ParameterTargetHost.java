// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.shell.runtime;

import de.mossgrabers.controller.ableton.push.command.continuous.PushMasterVolumeCommand;
import de.mossgrabers.controller.ableton.push.controller.PushControlSurface;
import de.mossgrabers.framework.controller.ContinuousID;
import de.mossgrabers.framework.controller.hardware.IHwContinuousControl;
import de.mossgrabers.framework.daw.IModel;
import de.mossgrabers.framework.daw.ITransport;
import de.mossgrabers.framework.parameter.IParameter;
import de.mossgrabers.pull.core.api.DesiredParameterLeases;
import de.mossgrabers.pull.core.api.ParameterBridgeSnapshot;
import de.mossgrabers.pull.core.api.ParameterSlot;
import de.mossgrabers.pull.core.api.ParameterTargetRef;
import de.mossgrabers.pull.core.api.ParameterTargetSnapshot;
import de.mossgrabers.pull.core.api.effect.SetParameterValueEffect;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;


/**
 * Bounded Bitwig-facing parameter slots and exact generation-fenced actuator leases.
 *
 * <p>Physical controls select slots outside this class. Opaque target identities describe the
 * current Bitwig actuator behind a slot and are never derived from a Push control identifier.</p>
 */
final class ParameterTargetHost
{
    private static final String FIXED_DOMAIN = "fixed";
    private static final String LEASE_DOMAIN = "live-parameter";
    private static final ParameterTargetRef TEMPO_TARGET = new ParameterTargetRef (FIXED_DOMAIN, "tempo", 0);
    private static final ParameterTargetRef MASTER_VOLUME_TARGET = new ParameterTargetRef (FIXED_DOMAIN, "master-volume", 0);
    private static final ContinuousID [] ACTIVE_CONTROLS =
    {
        ContinuousID.KNOB1,
        ContinuousID.KNOB2,
        ContinuousID.KNOB3,
        ContinuousID.KNOB4,
        ContinuousID.KNOB5,
        ContinuousID.KNOB6,
        ContinuousID.KNOB7,
        ContinuousID.KNOB8
    };

    private final PushControlSurface surface;
    private final IModel model;
    private final ITransport transport;
    private final RuntimeLog log;
    private final LiveTarget [] activeTargets = new LiveTarget[ParameterSlot.ACTIVE_SLOT_COUNT];
    private final Map<ParameterTargetRef, LiveTarget> currentTargets = new LinkedHashMap<> (ParameterBridgeSnapshot.TARGET_CAPACITY);

    private Map<ParameterTargetRef, RetainedTarget> retainedTargets = Map.of ();
    private ParameterBridgeSnapshot snapshot = ParameterBridgeSnapshot.empty ();
    private long nextIdentity = 1;


    /**
     * Constructor.
     *
     * @param surface Stable Push surface
     * @param model Stable framework model
     * @param log Runtime diagnostics
     */
    ParameterTargetHost (final PushControlSurface surface, final IModel model, final RuntimeLog log)
    {
        this.surface = Objects.requireNonNull (surface, "surface");
        this.model = Objects.requireNonNull (model, "model");
        this.transport = Objects.requireNonNull (model.getTransport (), "transport");
        this.log = Objects.requireNonNull (log, "log");
    }


    /**
     * Refresh the current bounded target window.
     *
     * @param requested True when core requests parameter snapshots
     * @return True when the public snapshot changed
     */
    boolean refresh (final boolean requested)
    {
        this.reconcileTargets ();
        this.discardStaleRetainedTargets ();
        final ParameterBridgeSnapshot refreshed = requested || !this.retainedTargets.isEmpty () ? this.captureSnapshot () : ParameterBridgeSnapshot.empty ();
        if (refreshed.equals (this.snapshot))
            return false;
        this.snapshot = refreshed;
        return true;
    }


    /**
     * Resolve one current physical input to its view-independent slot and target.
     *
     * @param controlID Stable framework control
     * @param control Current hardware binding
     * @return Targeted slot, or {@code null} when the control is not snapback-capable
     */
    TargetedParameter resolveMutation (final ContinuousID controlID, final IHwContinuousControl control)
    {
        final ContinuousID checkedControlID = Objects.requireNonNull (controlID, "controlID");
        final IHwContinuousControl checkedControl = Objects.requireNonNull (control, "control");
        this.reconcileTargets ();

        final int activeIndex = activeIndex (checkedControlID);
        if (activeIndex >= 0)
        {
            final LiveTarget target = this.activeTargets[activeIndex];
            return target != null && target.control == checkedControl ? new TargetedParameter (ParameterSlot.active (activeIndex), target.snapshot ()) : null;
        }

        if (checkedControlID == ContinuousID.TEMPO)
        {
            final LiveTarget target = this.currentTargets.get (TEMPO_TARGET);
            return target == null ? null : new TargetedParameter (ParameterSlot.TEMPO, target.snapshot ());
        }

        if (checkedControlID != ContinuousID.MASTER_KNOB)
            return null;
        final LiveTarget target = this.currentTargets.get (MASTER_VOLUME_TARGET);
        return target != null && target.control == checkedControl ? new TargetedParameter (ParameterSlot.MASTER_VOLUME, target.snapshot ()) : null;
    }


    /**
     * Get the current immutable public snapshot.
     *
     * @return Parameter state
     */
    ParameterBridgeSnapshot snapshot ()
    {
        return this.snapshot;
    }


    /**
     * Validate desired exact leases without mutating retained state.
     *
     * @param desired Complete desired leases
     * @return Prepared exact targets
     */
    Map<ParameterTargetRef, RetainedTarget> prepareLeases (final DesiredParameterLeases desired)
    {
        this.reconcileTargets ();
        final Map<ParameterTargetRef, RetainedTarget> prepared = new LinkedHashMap<> ();
        for (final Map.Entry<ParameterTargetRef, Double> requested: Objects.requireNonNull (desired, "desired").baselines ().entrySet ())
        {
            final LiveTarget target = this.currentTargets.get (requested.getKey ());
            if (target == null || !target.isCurrent ())
                throw new IllegalArgumentException ("Requested parameter lease is stale or outside the installed window");
            prepared.put (requested.getKey (), new RetainedTarget (target, requested.getValue ().doubleValue ()));
        }
        return Map.copyOf (prepared);
    }


    /**
     * Commit a previously prepared complete lease set.
     *
     * @param prepared Prepared leases
     */
    boolean applyLeases (final Map<ParameterTargetRef, RetainedTarget> prepared, final boolean requested)
    {
        this.retainedTargets = Map.copyOf (Objects.requireNonNull (prepared, "prepared"));
        final ParameterBridgeSnapshot refreshed = requested || !this.retainedTargets.isEmpty () ? this.captureSnapshot () : ParameterBridgeSnapshot.empty ();
        if (refreshed.equals (this.snapshot))
            return false;
        this.snapshot = refreshed;
        return true;
    }


    boolean retains (final ParameterTargetRef target)
    {
        return this.retainedTargets.containsKey (Objects.requireNonNull (target, "target"));
    }


    /**
     * Prepare one absolute parameter effect against this result's retained targets.
     *
     * @param effect Effect
     * @param preparedLeases This result's prepared leases
     * @return Prepared action
     */
    PreparedSet prepare (final SetParameterValueEffect effect, final Map<ParameterTargetRef, RetainedTarget> preparedLeases)
    {
        final SetParameterValueEffect checkedEffect = Objects.requireNonNull (effect, "effect");
        final RetainedTarget retained = Objects.requireNonNull (preparedLeases, "preparedLeases").get (checkedEffect.target ());
        if (retained == null)
            throw new IllegalArgumentException ("Parameter effects require an exact lease in the same core result");
        return new PreparedSet (retained.target, checkedEffect.value ());
    }


    /**
     * Apply one prepared absolute parameter effect with a live generation recheck.
     *
     * @param action Prepared action
     */
    void apply (final PreparedSet action)
    {
        final PreparedSet checkedAction = Objects.requireNonNull (action, "action");
        if (!checkedAction.target.isCurrent ())
            throw new IllegalStateException ("Prepared parameter target changed before effect application");
        checkedAction.target.restore (checkedAction.value);
    }


    /**
     * Best-effort restoration of core-retained targets during core invalidation.
     */
    void invalidate ()
    {
        for (final RetainedTarget retained: this.retainedTargets.values ())
        {
            if (!retained.target.isCurrent ())
                continue;
            try
            {
                retained.target.restore (retained.baseline);
            }
            catch (final RuntimeException failure)
            {
                this.log.warn ("Terminal parameter restoration failed for " + retained.target.reference + ": " + failure.getMessage ());
            }
        }
        this.retainedTargets = Map.of ();
        this.snapshot = ParameterBridgeSnapshot.empty ();
    }


    private void reconcileTargets ()
    {
        this.currentTargets.clear ();
        for (int index = 0; index < ACTIVE_CONTROLS.length; index++)
            this.reconcileActiveTarget (index, this.surface.getContinuous (ACTIVE_CONTROLS[index]));

        final LiveTarget tempo = new LiveTarget (
            TEMPO_TARGET,
            null,
            null,
            0,
            this.transport::getTempo,
            this.transport::setTempo,
            () -> true,
            0.001);
        this.currentTargets.put (tempo.reference, tempo);

        final IHwContinuousControl masterControl = this.surface.getContinuous (ContinuousID.MASTER_KNOB);
        if (masterControl != null && masterControl.getCommand () instanceof final PushMasterVolumeCommand command && command.isMasterVolumeMode () && !this.surface.isSelectPressed ())
        {
            final IParameter parameter = this.model.getMasterTrack ().getVolumeParameter ();
            if (parameter.doesExist ())
            {
                final LiveTarget master = parameterTarget (
                    MASTER_VOLUME_TARGET,
                    masterControl,
                    parameter,
                    masterControl.getBindingGeneration (),
                    () -> parameter.doesExist () && masterControl.getCommand () == command && command.isMasterVolumeMode () && !this.surface.isSelectPressed ());
                this.currentTargets.put (master.reference, master);
            }
        }
    }


    private void reconcileActiveTarget (final int index, final IHwContinuousControl control)
    {
        if (control == null || control.getBoundParameter () == null)
        {
            this.activeTargets[index] = null;
            return;
        }

        final IParameter parameter = control.getBoundParameter ();
        final long generation = control.getBindingGeneration ();
        LiveTarget target = this.activeTargets[index];
        if (target == null || target.parameter != parameter || target.bindingGeneration != generation)
        {
            target = parameterTarget (
                new ParameterTargetRef (LEASE_DOMAIN, this.nextIdentity (), generation),
                control,
                parameter,
                generation,
                () -> parameter.doesExist () && control.getBindingGeneration () == generation && control.getBoundParameter () == parameter);
            this.activeTargets[index] = target;
        }
        if (target.isCurrent ())
            this.currentTargets.put (target.reference, target);
    }


    private ParameterBridgeSnapshot captureSnapshot ()
    {
        final Map<ParameterSlot, ParameterTargetSnapshot> slots = new LinkedHashMap<> (ParameterBridgeSnapshot.TARGET_CAPACITY);
        for (int index = 0; index < this.activeTargets.length; index++)
        {
            final LiveTarget target = this.activeTargets[index];
            if (target != null && target.isCurrent ())
                slots.put (ParameterSlot.active (index), target.snapshot ());
        }
        final LiveTarget tempo = this.currentTargets.get (TEMPO_TARGET);
        if (tempo != null)
            slots.put (ParameterSlot.TEMPO, tempo.snapshot ());
        final LiveTarget master = this.currentTargets.get (MASTER_VOLUME_TARGET);
        if (master != null)
            slots.put (ParameterSlot.MASTER_VOLUME, master.snapshot ());

        final Map<ParameterTargetRef, Double> baselines = new LinkedHashMap<> ();
        this.retainedTargets.forEach ( (target, retained) -> baselines.put (target, Double.valueOf (retained.baseline)));
        return new ParameterBridgeSnapshot (slots, baselines);
    }


    private void discardStaleRetainedTargets ()
    {
        if (this.retainedTargets.isEmpty ())
            return;
        final Map<ParameterTargetRef, RetainedTarget> current = new LinkedHashMap<> ();
        this.retainedTargets.forEach ( (reference, retained) -> {
            if (retained.target.isCurrent ())
                current.put (reference, retained);
            else
                this.log.warn ("Abandoned stale retained parameter target " + reference);
        });
        this.retainedTargets = Map.copyOf (current);
    }


    private String nextIdentity ()
    {
        if (this.nextIdentity == Long.MAX_VALUE)
            throw new IllegalStateException ("Parameter target identity sequence exhausted");
        return Long.toUnsignedString (this.nextIdentity++);
    }


    private static LiveTarget parameterTarget (final ParameterTargetRef reference, final IHwContinuousControl control, final IParameter parameter, final long bindingGeneration, final BooleanSupplier current)
    {
        return new LiveTarget (
            reference,
            control,
            parameter,
            bindingGeneration,
            parameter::getValue,
            value -> parameter.setValueImmediatly ((int) Math.round (value)),
            current,
            0.5);
    }


    private static int activeIndex (final ContinuousID controlID)
    {
        for (int index = 0; index < ACTIVE_CONTROLS.length; index++)
        {
            if (ACTIVE_CONTROLS[index] == controlID)
                return index;
        }
        return -1;
    }


    record TargetedParameter (ParameterSlot slot, ParameterTargetSnapshot target)
    {
        TargetedParameter
        {
            Objects.requireNonNull (slot, "slot");
            Objects.requireNonNull (target, "target");
        }
    }


    record PreparedSet (LiveTarget target, double value)
    {
    }


    record RetainedTarget (LiveTarget target, double baseline)
    {
    }


    private static final class LiveTarget
    {
        private final ParameterTargetRef reference;
        private final IHwContinuousControl control;
        private final IParameter parameter;
        private final long bindingGeneration;
        private final DoubleSupplier reader;
        private final DoubleConsumer restorer;
        private final BooleanSupplier current;
        private final double tolerance;


        private LiveTarget (final ParameterTargetRef reference, final IHwContinuousControl control, final IParameter parameter, final long bindingGeneration, final DoubleSupplier reader, final DoubleConsumer restorer, final BooleanSupplier current, final double tolerance)
        {
            this.reference = Objects.requireNonNull (reference, "reference");
            this.control = control;
            this.parameter = parameter;
            this.bindingGeneration = bindingGeneration;
            this.reader = Objects.requireNonNull (reader, "reader");
            this.restorer = Objects.requireNonNull (restorer, "restorer");
            this.current = Objects.requireNonNull (current, "current");
            this.tolerance = tolerance;
        }


        private ParameterTargetSnapshot snapshot ()
        {
            return new ParameterTargetSnapshot (this.reference, this.reader.getAsDouble (), this.tolerance);
        }


        private boolean isCurrent ()
        {
            return this.current.getAsBoolean ();
        }


        private void restore (final double value)
        {
            this.restorer.accept (value);
        }
    }
}
