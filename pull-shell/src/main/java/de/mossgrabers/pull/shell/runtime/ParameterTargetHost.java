// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.shell.runtime;

import de.mossgrabers.controller.ableton.push.command.continuous.PushMasterVolumeCommand;
import de.mossgrabers.controller.ableton.push.controller.PushControlSurface;
import de.mossgrabers.framework.controller.ContinuousID;
import de.mossgrabers.framework.controller.hardware.IHwContinuousControl;
import de.mossgrabers.framework.daw.IModel;
import de.mossgrabers.framework.daw.ITransport;
import de.mossgrabers.framework.daw.data.ITrack;
import de.mossgrabers.framework.daw.data.bank.IParameterBank;
import de.mossgrabers.framework.daw.data.bank.ITrackBank;
import de.mossgrabers.framework.parameter.IParameter;
import de.mossgrabers.pull.core.api.DesiredParameterBanks;
import de.mossgrabers.pull.core.api.DesiredParameterInteraction;
import de.mossgrabers.pull.core.api.ParameterBankId;
import de.mossgrabers.pull.core.api.ParameterBridgeSnapshot;
import de.mossgrabers.pull.core.api.ParameterSlot;
import de.mossgrabers.pull.core.api.ParameterTargetRef;
import de.mossgrabers.pull.core.api.ParameterTargetKind;
import de.mossgrabers.pull.core.api.ParameterTargetSnapshot;
import de.mossgrabers.pull.core.api.effect.AdjustParameterValueEffect;
import de.mossgrabers.pull.core.api.effect.ResetParameterEffect;
import de.mossgrabers.pull.core.api.effect.SetParameterValueEffect;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.IntFunction;


/**
 * Bounded Bitwig-facing parameter slots and exact generation-fenced actuator leases.
 *
 * <p>Physical controls select slots outside this class. Opaque target identities describe the
 * current Bitwig actuator behind a slot and are never derived from a Push control identifier.</p>
 */
final class ParameterTargetHost
{
    private static final ParameterTargetRef TEMPO_TARGET = new ParameterTargetRef (ParameterTargetKind.FIXED, "tempo", 0);
    private static final ParameterTargetRef MASTER_VOLUME_TARGET = new ParameterTargetRef (ParameterTargetKind.FIXED, "master-volume", 0);
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
    private final ParameterTargetIdentityResolver targetIdentities;
    private final LiveTarget [] activeTargets = new LiveTarget[ParameterSlot.BANK_SIZE];
    private final LiveTarget [] projectTargets = new LiveTarget[ParameterSlot.BANK_SIZE];
    private final LiveTarget [] deviceTargets = new LiveTarget[ParameterSlot.BANK_SIZE];
    private final LiveTarget [] trackVolumeTargets = new LiveTarget[ParameterSlot.BANK_SIZE];
    private final LiveTarget [] trackPanTargets = new LiveTarget[ParameterSlot.BANK_SIZE];
    private final Map<ParameterTargetRef, LiveTarget> currentTargets = new LinkedHashMap<> (ParameterBridgeSnapshot.TARGET_CAPACITY);

    private Map<ParameterTargetRef, RetainedTarget> retainedTargets = Map.of ();
    private ParameterBridgeSnapshot snapshot = ParameterBridgeSnapshot.empty ();
    private DesiredParameterBanks requestedBanks = DesiredParameterBanks.empty ();
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
        this.targetIdentities = new ParameterTargetIdentityResolver (surface, model);
    }


    /**
     * Refresh the current bounded target window.
     *
     * @param banks Complete installed bank selection requested by core
     * @return True when the public snapshot changed
     */
    boolean refresh (final DesiredParameterBanks banks)
    {
        this.requestedBanks = Objects.requireNonNull (banks, "banks");
        this.reconcileTargets (this.requestedBanks);
        this.discardStaleRetainedTargets ();
        final ParameterBridgeSnapshot refreshed = !banks.banks ().isEmpty () || !this.retainedTargets.isEmpty () ? this.captureSnapshot () : ParameterBridgeSnapshot.empty ();
        if (refreshed.equals (this.snapshot))
            return false;
        this.snapshot = refreshed;
        return true;
    }


    /**
     * Resolve one current physical input to its view-independent slot and target.
     *
     * @param control Current hardware binding
     * @return Targeted slot, or {@code null} when the control is not snapback-capable
     */
    ControllerBridge.TargetedParameter resolveMutation (final IHwContinuousControl control)
    {
        final IHwContinuousControl checkedControl = Objects.requireNonNull (control, "control");
        this.reconcileTargets (this.requestedBanks);

        for (final LiveTarget target: this.activeTargets)
        {
            if (target != null && target.control == checkedControl && target.isCurrent ())
                return new ControllerBridge.TargetedParameter (target.snapshot ());
        }

        final LiveTarget tempo = this.currentTargets.get (TEMPO_TARGET);
        if (tempo != null && tempo.control == checkedControl)
            return new ControllerBridge.TargetedParameter (tempo.snapshot ());
        final LiveTarget master = this.currentTargets.get (MASTER_VOLUME_TARGET);
        return master != null && master.control == checkedControl ? new ControllerBridge.TargetedParameter (master.snapshot ()) : null;
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
    Map<ParameterTargetRef, RetainedTarget> prepareLeases (final DesiredParameterInteraction desired, final DesiredParameterBanks banks)
    {
        final DesiredParameterBanks committedBanks = this.requestedBanks;
        try
        {
            this.reconcileTargets (Objects.requireNonNull (banks, "banks"));
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
        finally
        {
            this.reconcileTargets (committedBanks);
        }
    }


    /**
     * Commit a previously prepared complete lease set.
     *
     * @param prepared Prepared leases
     */
    boolean applyLeases (final Map<ParameterTargetRef, RetainedTarget> prepared, final DesiredParameterBanks banks)
    {
        final Map<ParameterTargetRef, RetainedTarget> checkedPrepared = Map.copyOf (Objects.requireNonNull (prepared, "prepared"));
        this.requestedBanks = Objects.requireNonNull (banks, "banks");
        this.reconcileTargets (this.requestedBanks);
        checkedPrepared.forEach ( (reference, retained) -> {
            final LiveTarget current = this.currentTargets.get (reference);
            if (!retained.target.isSameActuator (current) || !retained.target.isCurrent ())
                throw new IllegalStateException ("Prepared parameter lease changed before commit");
        });
        this.retainedTargets = checkedPrepared;
        final ParameterBridgeSnapshot refreshed = !this.requestedBanks.banks ().isEmpty () || !this.retainedTargets.isEmpty () ? this.captureSnapshot () : ParameterBridgeSnapshot.empty ();
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


    /** Prepare a relative mutation against the exact current target. */
    PreparedAdjust prepare (final AdjustParameterValueEffect effect)
    {
        final AdjustParameterValueEffect checkedEffect = Objects.requireNonNull (effect, "effect");
        return new PreparedAdjust (this.requireCurrent (checkedEffect.target ()), checkedEffect.delta ());
    }


    /** Prepare a host-default reset against the exact current target. */
    PreparedReset prepare (final ResetParameterEffect effect)
    {
        return new PreparedReset (this.requireCurrent (Objects.requireNonNull (effect, "effect").target ()));
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


    /** Apply one prepared relative mutation with a live identity recheck. */
    void apply (final PreparedAdjust action)
    {
        final PreparedAdjust checkedAction = Objects.requireNonNull (action, "action");
        final LiveTarget target = this.requireCurrent (checkedAction.target.reference);
        if (target.parameter == null)
            throw new IllegalStateException ("Relative parameter mutation requires a live parameter");
        target.parameter.inc (checkedAction.delta);
    }


    /** Apply one prepared host-default reset with a live identity recheck. */
    void apply (final PreparedReset action)
    {
        final LiveTarget target = this.requireCurrent (Objects.requireNonNull (action, "action").target.reference);
        if (target.parameter == null)
            throw new IllegalStateException ("Parameter reset requires a live parameter");
        target.parameter.resetValue ();
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
        this.requestedBanks = DesiredParameterBanks.empty ();
        this.currentTargets.clear ();
        this.snapshot = ParameterBridgeSnapshot.empty ();
    }


    private void reconcileTargets (final DesiredParameterBanks banks)
    {
        Objects.requireNonNull (banks, "banks");
        this.currentTargets.clear ();
        if (banks.includes (ParameterBankId.ACTIVE))
        {
            for (int index = 0; index < ACTIVE_CONTROLS.length; index++)
                this.reconcileActiveTarget (index, this.surface.getContinuous (ACTIVE_CONTROLS[index]));
        }
        if (banks.includes (ParameterBankId.PROJECT_REMOTE))
        {
            final IParameterBank projectParameters = this.model.getProject ().getParameterBank ();
            for (int index = 0; index < this.projectTargets.length; index++)
                this.reconcileProjectTarget (index, projectParameters);
        }
        if (banks.includes (ParameterBankId.SELECTED_DEVICE_REMOTE))
        {
            final IParameterBank deviceParameters = this.model.getCursorDevice ().getParameterBank ();
            for (int index = 0; index < this.deviceTargets.length; index++)
                this.reconcileDeviceTarget (index, deviceParameters);
        }
        if (banks.includes (ParameterBankId.TRACK_VOLUME))
        {
            final ITrackBank tracks = this.model.getCurrentTrackBank ();
            for (int index = 0; index < this.trackVolumeTargets.length; index++)
                this.reconcileTrackVolumeTarget (index, tracks);
        }
        if (banks.includes (ParameterBankId.TRACK_PAN))
        {
            final ITrackBank tracks = this.model.getCurrentTrackBank ();
            for (int index = 0; index < this.trackPanTargets.length; index++)
                this.reconcileTrackPanTarget (index, tracks);
        }

        if (!banks.includes (ParameterBankId.GLOBAL))
            return;

        final LiveTarget tempo = new LiveTarget (
            TEMPO_TARGET,
            this.surface.getContinuous (ContinuousID.TEMPO),
            null,
            0,
            null,
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
        final ParameterTargetIdentityResolver.TargetIdentity targetIdentity = this.targetIdentities.resolve (index, parameter);
        if (targetIdentity == null)
        {
            this.activeTargets[index] = null;
            return;
        }
        LiveTarget target = this.activeTargets[index];
        if (target == null || target.parameter != parameter || target.bindingGeneration != generation || !target.targetIdentity.equals (targetIdentity))
        {
            target = parameterTarget (
                new ParameterTargetRef (ParameterTargetKind.LIVE, this.nextIdentity (), generation),
                control,
                parameter,
                generation,
                targetIdentity,
                () -> parameter.doesExist () && control.getBindingGeneration () == generation && control.getBoundParameter () == parameter && targetIdentity.equals (this.targetIdentities.resolve (index, parameter)));
            this.activeTargets[index] = target;
        }
        if (target.isCurrent ())
            this.currentTargets.put (target.reference, target);
    }


    private void reconcileProjectTarget (final int index, final IParameterBank bank)
    {
        if (bank == null || index >= bank.getPageSize ())
        {
            this.projectTargets[index] = null;
            return;
        }

        final IParameter parameter = bank.getItem (index);
        final ParameterTargetIdentityResolver.TargetIdentity targetIdentity = this.targetIdentities.remote ("project-remote", this.model.getProject ().getName (), bank, index);
        if (parameter == null || !parameter.doesExist () || targetIdentity == null)
        {
            this.projectTargets[index] = null;
            return;
        }

        LiveTarget target = this.projectTargets[index];
        if (target == null || target.parameter != parameter || !target.targetIdentity.equals (targetIdentity))
        {
            target = parameterTarget (
                new ParameterTargetRef (ParameterTargetKind.LIVE, this.nextIdentity (), bank.getPageBank ().getSelectedItemPosition ()),
                null,
                parameter,
                0,
                targetIdentity,
                () -> parameter.doesExist () && targetIdentity.equals (this.targetIdentities.remote ("project-remote", this.model.getProject ().getName (), bank, index)));
            this.projectTargets[index] = target;
        }
        if (target.isCurrent ())
            this.currentTargets.put (target.reference, target);
    }


    private void reconcileDeviceTarget (final int index, final IParameterBank bank)
    {
        if (bank == null || index >= bank.getPageSize ())
        {
            this.deviceTargets[index] = null;
            return;
        }

        final IParameter parameter = bank.getItem (index);
        final ParameterTargetIdentityResolver.TargetIdentity targetIdentity = this.targetIdentities.remote ("device-remote", this.model.getCursorDevice ().getID (), bank, index);
        if (parameter == null || !parameter.doesExist () || targetIdentity == null)
        {
            this.deviceTargets[index] = null;
            return;
        }

        LiveTarget target = this.deviceTargets[index];
        if (target == null || target.parameter != parameter || !target.targetIdentity.equals (targetIdentity))
        {
            target = parameterTarget (
                new ParameterTargetRef (ParameterTargetKind.LIVE, this.nextIdentity (), bank.getPageBank ().getSelectedItemPosition ()),
                null,
                parameter,
                0,
                targetIdentity,
                () -> parameter.doesExist () && targetIdentity.equals (this.targetIdentities.remote ("device-remote", this.model.getCursorDevice ().getID (), bank, index)));
            this.deviceTargets[index] = target;
        }
        if (target.isCurrent ())
            this.currentTargets.put (target.reference, target);
    }


    private void reconcileTrackVolumeTarget (final int index, final ITrackBank tracks)
    {
        if (tracks == null || index >= tracks.getPageSize ())
        {
            this.trackVolumeTargets[index] = null;
            return;
        }

        final ITrack track = tracks.getItem (index);
        final IParameter parameter = track == null ? null : track.getVolumeParameter ();
        final ParameterTargetIdentityResolver.TargetIdentity targetIdentity = parameter == null ? null : this.targetIdentities.channel (track, parameter);
        if (parameter == null || !parameter.doesExist () || targetIdentity == null)
        {
            this.trackVolumeTargets[index] = null;
            return;
        }

        LiveTarget target = this.trackVolumeTargets[index];
        if (target == null || target.parameter != parameter || !target.targetIdentity.equals (targetIdentity))
        {
            target = parameterTarget (
                new ParameterTargetRef (ParameterTargetKind.LIVE, this.nextIdentity (), index),
                null,
                parameter,
                0,
                targetIdentity,
                () -> {
                    final ITrack currentTrack = tracks.getItem (index);
                    return currentTrack != null && currentTrack.getVolumeParameter () == parameter && parameter.doesExist () && targetIdentity.equals (this.targetIdentities.channel (currentTrack, parameter));
                });
            this.trackVolumeTargets[index] = target;
        }
        if (target.isCurrent ())
            this.currentTargets.put (target.reference, target);
    }


    private void reconcileTrackPanTarget (final int index, final ITrackBank tracks)
    {
        if (tracks == null || index >= tracks.getPageSize ())
        {
            this.trackPanTargets[index] = null;
            return;
        }

        final ITrack track = tracks.getItem (index);
        final IParameter parameter = track == null ? null : track.getPanParameter ();
        final ParameterTargetIdentityResolver.TargetIdentity targetIdentity = parameter == null ? null : this.targetIdentities.channel (track, parameter);
        if (parameter == null || !parameter.doesExist () || targetIdentity == null)
        {
            this.trackPanTargets[index] = null;
            return;
        }

        LiveTarget target = this.trackPanTargets[index];
        if (target == null || target.parameter != parameter || !target.targetIdentity.equals (targetIdentity))
        {
            target = parameterTarget (
                new ParameterTargetRef (ParameterTargetKind.LIVE, this.nextIdentity (), index),
                null,
                parameter,
                0,
                targetIdentity,
                () -> {
                    final ITrack currentTrack = tracks.getItem (index);
                    return currentTrack != null && currentTrack.getPanParameter () == parameter && parameter.doesExist () && targetIdentity.equals (this.targetIdentities.channel (currentTrack, parameter));
                });
            this.trackPanTargets[index] = target;
        }
        if (target.isCurrent ())
            this.currentTargets.put (target.reference, target);
    }


    private ParameterBridgeSnapshot captureSnapshot ()
    {
        final Map<ParameterSlot, ParameterTargetSnapshot> slots = new LinkedHashMap<> (ParameterBridgeSnapshot.TARGET_CAPACITY);
        this.captureBank (slots, ParameterBankId.ACTIVE, this.activeTargets, ParameterSlot::active);
        this.captureBank (slots, ParameterBankId.PROJECT_REMOTE, this.projectTargets, ParameterSlot::projectRemote);
        this.captureBank (slots, ParameterBankId.SELECTED_DEVICE_REMOTE, this.deviceTargets, ParameterSlot::selectedDeviceRemote);
        this.captureBank (slots, ParameterBankId.TRACK_VOLUME, this.trackVolumeTargets, ParameterSlot::trackVolume);
        this.captureBank (slots, ParameterBankId.TRACK_PAN, this.trackPanTargets, ParameterSlot::trackPan);
        final LiveTarget tempo = this.requestedBanks.includes (ParameterBankId.GLOBAL) ? this.currentTargets.get (TEMPO_TARGET) : null;
        if (tempo != null)
            slots.put (ParameterSlot.TEMPO, tempo.snapshot ());
        final LiveTarget master = this.requestedBanks.includes (ParameterBankId.GLOBAL) ? this.currentTargets.get (MASTER_VOLUME_TARGET) : null;
        if (master != null)
            slots.put (ParameterSlot.MASTER_VOLUME, master.snapshot ());

        final Map<ParameterTargetRef, Double> baselines = new LinkedHashMap<> ();
        this.retainedTargets.forEach ( (target, retained) -> baselines.put (target, Double.valueOf (retained.baseline)));
        return new ParameterBridgeSnapshot (slots, baselines);
    }


    private void captureBank (final Map<ParameterSlot, ParameterTargetSnapshot> slots, final ParameterBankId bank, final LiveTarget [] targets, final IntFunction<ParameterSlot> slotAt)
    {
        if (!this.requestedBanks.includes (bank))
            return;
        for (int index = 0; index < targets.length; index++)
        {
            final LiveTarget target = targets[index];
            if (target != null && target.isCurrent ())
                slots.put (slotAt.apply (index), target.snapshot ());
        }
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


    private LiveTarget requireCurrent (final ParameterTargetRef reference)
    {
        final LiveTarget target = this.currentTargets.get (Objects.requireNonNull (reference, "reference"));
        if (target == null || !target.isCurrent ())
            throw new IllegalStateException ("Prepared parameter target changed before effect application");
        return target;
    }


    private static LiveTarget parameterTarget (final ParameterTargetRef reference, final IHwContinuousControl control, final IParameter parameter, final long bindingGeneration, final BooleanSupplier current)
    {
        return parameterTarget (reference, control, parameter, bindingGeneration, null, current);
    }


    private static LiveTarget parameterTarget (final ParameterTargetRef reference, final IHwContinuousControl control, final IParameter parameter, final long bindingGeneration, final ParameterTargetIdentityResolver.TargetIdentity targetIdentity, final BooleanSupplier current)
    {
        return new LiveTarget (
            reference,
            control,
            parameter,
            bindingGeneration,
            targetIdentity,
            parameter::getValue,
            value -> parameter.setValueImmediatly ((int) Math.round (value)),
            current,
            0.5);
    }


    record PreparedSet (LiveTarget target, double value)
    {
    }


    record PreparedAdjust (LiveTarget target, double delta)
    {
    }


    record PreparedReset (LiveTarget target)
    {
    }


    record RetainedTarget (LiveTarget target, double baseline) implements ControllerBridge.ParameterLease
    {
    }


    private static final class LiveTarget
    {
        private final ParameterTargetRef reference;
        private final IHwContinuousControl control;
        private final IParameter parameter;
        private final long bindingGeneration;
        private final ParameterTargetIdentityResolver.TargetIdentity targetIdentity;
        private final DoubleSupplier reader;
        private final DoubleConsumer restorer;
        private final BooleanSupplier current;
        private final double tolerance;


        private LiveTarget (final ParameterTargetRef reference, final IHwContinuousControl control, final IParameter parameter, final long bindingGeneration, final ParameterTargetIdentityResolver.TargetIdentity targetIdentity, final DoubleSupplier reader, final DoubleConsumer restorer, final BooleanSupplier current, final double tolerance)
        {
            this.reference = Objects.requireNonNull (reference, "reference");
            this.control = control;
            this.parameter = parameter;
            this.bindingGeneration = bindingGeneration;
            this.targetIdentity = targetIdentity;
            this.reader = Objects.requireNonNull (reader, "reader");
            this.restorer = Objects.requireNonNull (restorer, "restorer");
            this.current = Objects.requireNonNull (current, "current");
            this.tolerance = tolerance;
        }


        private ParameterTargetSnapshot snapshot ()
        {
            final double value = this.reader.getAsDouble ();
            if (this.parameter == null)
                return new ParameterTargetSnapshot (this.reference, "Tempo", value, value, Double.toString (value), -1, this.tolerance);
            return new ParameterTargetSnapshot (
                this.reference,
                this.parameter.getName (),
                value,
                this.parameter.getModulatedValue (),
                this.parameter.getDisplayedValue (),
                this.parameter.getNumberOfSteps (),
                this.tolerance);
        }


        private boolean isCurrent ()
        {
            return this.current.getAsBoolean ();
        }


        private boolean isSameActuator (final LiveTarget other)
        {
            return this == other || other != null && this.reference.kind () == ParameterTargetKind.FIXED && this.reference.equals (other.reference);
        }


        private void restore (final double value)
        {
            this.restorer.accept (value);
        }
    }
}
