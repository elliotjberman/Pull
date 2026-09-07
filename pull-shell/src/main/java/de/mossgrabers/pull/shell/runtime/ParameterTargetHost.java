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
import de.mossgrabers.framework.daw.data.ISend;
import de.mossgrabers.framework.daw.midi.ISelectedTrackNoteTarget;
import de.mossgrabers.framework.daw.data.bank.IParameterBank;
import de.mossgrabers.framework.daw.data.bank.ITrackBank;
import de.mossgrabers.framework.parameter.IParameter;
import de.mossgrabers.pull.core.api.DesiredParameterBanks;
import de.mossgrabers.pull.core.api.ControlId;
import de.mossgrabers.pull.core.api.DesiredParameterTouches;
import de.mossgrabers.pull.core.api.DesiredParameterInteraction;
import de.mossgrabers.pull.core.api.ParameterBankId;
import de.mossgrabers.pull.core.api.ParameterBridgeSnapshot;
import de.mossgrabers.pull.core.api.ParameterSlot;
import de.mossgrabers.pull.core.api.ParameterTargetRef;
import de.mossgrabers.pull.core.api.ParameterTargetKind;
import de.mossgrabers.pull.core.api.ParameterTargetSnapshot;
import de.mossgrabers.pull.core.api.effect.AdjustParameterValueEffect;
import de.mossgrabers.pull.core.api.effect.AcquireParameterTouchEffect;
import de.mossgrabers.pull.core.api.effect.SetParameterEnabledEffect;
import de.mossgrabers.pull.core.api.effect.SetParameterNormalizedValueEffect;
import de.mossgrabers.pull.core.api.effect.ResetParameterEffect;
import de.mossgrabers.pull.core.api.effect.SetParameterValueEffect;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.IntFunction;
import java.util.function.Supplier;


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
    private final ISelectedTrackNoteTarget selectedTarget;
    private final ITransport transport;
    private final RuntimeLog log;
    private final ParameterTargetIdentityResolver targetIdentities;
    private final LiveTarget [] selectedTrackTargets = new LiveTarget[2];
    private final LiveTarget [] selectedSendTargets = new LiveTarget[ParameterSlot.BANK_SIZE];
    private Map<ParameterTargetRef, LiveTarget> indicatedTargets = Map.of ();
    private java.util.Set<ParameterSlot> requestedIndications = java.util.Set.of ();

    private final LiveTarget [] activeTargets = new LiveTarget[ParameterSlot.BANK_SIZE];
    private final LiveTarget [] projectTargets = new LiveTarget[ParameterSlot.BANK_SIZE];
    private final LiveTarget [] deviceTargets = new LiveTarget[ParameterSlot.BANK_SIZE];
    private final LiveTarget [] trackVolumeTargets = new LiveTarget[ParameterSlot.BANK_SIZE];
    private final LiveTarget [] trackPanTargets = new LiveTarget[ParameterSlot.BANK_SIZE];
    private final LiveTarget [][] trackSendTargets = new LiveTarget[ParameterSlot.BANK_SIZE][ParameterSlot.BANK_SIZE];
    private final Map<ParameterTargetRef, LiveTarget> currentTargets = new LinkedHashMap<> (ParameterBridgeSnapshot.TARGET_CAPACITY);

    private Map<ParameterTargetRef, RetainedTarget> retainedTargets = Map.of ();
    private final Map<ParameterTargetRef, LiveTarget> touchedTargets = new LinkedHashMap<> ();
    private LiveTarget masterMixVolumeTarget;
    private LiveTarget metronomeVolumeTarget;
    private LiveTarget masterMixPanTarget;
    private LiveTarget cueVolumeTarget;
    private LiveTarget cueMixTarget;
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
        this (surface, model, null, log);
    }


    ParameterTargetHost (final PushControlSurface surface, final IModel model, final ISelectedTrackNoteTarget selectedTarget, final RuntimeLog log)
    {
        this.selectedTarget = selectedTarget;
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
        final var touches = this.touchedTargets.entrySet ().iterator ();
        while (touches.hasNext ())
        {
            final LiveTarget touched = touches.next ().getValue ();
            if (!touched.isCurrent ())
            {
                touches.remove ();
                this.releaseTouch (touched);
            }
        }
        final ParameterBridgeSnapshot refreshed = !banks.banks ().isEmpty () || !this.retainedTargets.isEmpty () || !this.touchedTargets.isEmpty () ? this.captureSnapshot () : ParameterBridgeSnapshot.empty ();
        if (refreshed.equals (this.snapshot))
        {
            this.reconcileIndications ();
            return false;
        }
        this.snapshot = refreshed;
        this.reconcileIndications ();
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
     * Reject a stale physical binding under the generic core page adapter.
     * Core pages use named banks and the permanent page binding is empty; it must never
     * regain legacy mutation through an unclassified physical parameter wrapper.
     */
    boolean requiresResolvedMutation (final IHwContinuousControl control)
    {
        if (!this.requestedBanks.includes (ParameterBankId.ACTIVE) || this.surface.getModeManager ().pageState ().effectivePage ().kind () != de.mossgrabers.pull.core.api.ControllerPageRef.Kind.CORE)
            return false;
        final IHwContinuousControl checkedControl = Objects.requireNonNull (control, "control");
        for (final ContinuousID id: ACTIVE_CONTROLS)
        {
            if (this.surface.getContinuous (id) == checkedControl)
                return true;
        }
        return false;
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
        final ParameterBridgeSnapshot refreshed = !this.requestedBanks.banks ().isEmpty () || !this.retainedTargets.isEmpty () || !this.touchedTargets.isEmpty () ? this.captureSnapshot () : ParameterBridgeSnapshot.empty ();
        if (refreshed.equals (this.snapshot))
            return false;
        this.snapshot = refreshed;
        return true;
    }


    Map<ControlId, ControllerBridge.ParameterTouchLease> prepareTouches (final DesiredParameterTouches desired, final DesiredParameterBanks banks)
    {
        final DesiredParameterBanks committedBanks = this.requestedBanks;
        try
        {
            this.reconcileTargets (banks);
            final Map<ControlId, ControllerBridge.ParameterTouchLease> prepared = new LinkedHashMap<> ();
            desired.targets ().forEach ( (control, reference) -> {
                final LiveTarget target = this.requireCurrent (reference);
                if (target.parameter == null)
                    throw new IllegalArgumentException ("Parameter touch requires a touch-capable parameter actuator");
                prepared.put (control, new TouchTarget (target));
            });
            return Map.copyOf (prepared);
        }
        finally
        {
            this.reconcileTargets (committedBanks);
        }
    }


    void releaseTouchesExcept (final Map<ControlId, ControllerBridge.ParameterTouchLease> prepared)
    {
        final var current = this.touchedTargets.entrySet ().iterator ();
        while (current.hasNext ())
        {
            final LiveTarget target = current.next ().getValue ();
            final boolean retained = prepared.values ().stream ().anyMatch (lease -> touchTarget (lease).isSameActuator (target));
            if (retained)
                continue;
            current.remove ();
            this.releaseTouch (target);
        }
    }


    void acquireTouches (final Map<ControlId, ControllerBridge.ParameterTouchLease> prepared)
    {
        for (final ControllerBridge.ParameterTouchLease lease: prepared.values ())
        {
            final LiveTarget target = touchTarget (lease);
            final LiveTarget current = this.requireCurrent (target.reference);
            if (!target.isSameActuator (current) || !target.isCurrent ())
                throw new IllegalStateException ("Parameter touch target changed before acquisition");
            if (this.touchedTargets.containsKey (target.reference))
                continue;
            this.touchedTargets.put (target.reference, target);
            target.parameter.touchValue (true);
        }
    }


    void releaseTouches ()
    {
        this.releaseTouchesExcept (Map.of ());
    }


    private void releaseTouch (final LiveTarget target)
    {
        if (!target.addressable.getAsBoolean ())
        {
            this.log.warn ("Cannot release a parameter touch after its exact target changed " + target.reference);
            return;
        }
        try
        {
            target.parameter.touchValue (false);
        }
        catch (final RuntimeException failure)
        {
            this.log.warn ("Parameter touch cleanup failed for " + target.reference + ": " + failure.getMessage ());
        }
    }


    private static LiveTarget touchTarget (final ControllerBridge.ParameterTouchLease lease)
    {
        if (!(lease instanceof final TouchTarget touch))
            throw new IllegalArgumentException ("Parameter touch lease belongs to another bridge");
        return touch.target ();
    }


    private record TouchTarget (LiveTarget target) implements ControllerBridge.ParameterTouchLease
    {
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


    PreparedNormalized prepare (final SetParameterNormalizedValueEffect effect)
    {
        final LiveTarget target = this.requireCurrent (effect.target ());
        if (target.parameter == null)
            throw new IllegalArgumentException ("Parameter does not expose normalized mutation");
        return new PreparedNormalized (target, effect.value ());
    }


    void apply (final PreparedNormalized action)
    {
        this.requireCurrent (action.target ().reference).parameter.setNormalizedValue (action.value ());
    }


    PreparedEnabled prepare (final SetParameterEnabledEffect effect)
    {
        final LiveTarget target = this.requireCurrent (effect.target ());
        if (!(target.parameter instanceof ISend))
            throw new IllegalArgumentException ("Parameter does not expose enabled state");
        return new PreparedEnabled (target, effect.enabled ());
    }


    PreparedTouch prepare (final AcquireParameterTouchEffect effect)
    {
        final LiveTarget target = this.requireCurrent (effect.target ());
        if (target.parameter == null)
            throw new IllegalArgumentException ("Parameter does not expose touch state");
        return new PreparedTouch (effect.owner (), target);
    }


    void apply (final PreparedEnabled action)
    {
        final LiveTarget target = this.requireCurrent (action.target ().reference);
        if (!(target.parameter instanceof final ISend send))
            throw new IllegalStateException ("Parameter enabled capability changed");
        send.setEnabled (action.enabled ());
    }


    void apply (final PreparedTouch action)
    {
        this.acquireTouches (Map.of (action.owner (), new TouchTarget (action.target ())));
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
        this.releaseTouches ();
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
        this.applyIndications (java.util.Set.of ());
        this.retainedTargets = Map.of ();
        this.requestedBanks = DesiredParameterBanks.empty ();
        this.currentTargets.clear ();
        this.snapshot = ParameterBridgeSnapshot.empty ();
    }


    /** Apply core-selected host indication through the same named, fenced target canopy. */
    void applyIndications (final java.util.Set<ParameterSlot> slots)
    {
        this.requestedIndications = java.util.Set.copyOf (Objects.requireNonNull (slots, "slots"));
        this.reconcileIndications ();
    }

    /** Retire outgoing ownership before a legacy page's foreign activation callback. */
    void releaseIndicationsExcept (final java.util.Set<ParameterSlot> slots)
    {
        this.requestedIndications = java.util.Set.copyOf (Objects.requireNonNull (slots, "slots"));
        final java.util.Set<ParameterTargetRef> retained = new java.util.HashSet<> ();
        for (final ParameterSlot slot: slots)
        {
            final ParameterTargetSnapshot target = this.snapshot.slots ().get (slot);
            if (target != null) retained.add (target.target ());
        }
        final Map<ParameterTargetRef, LiveTarget> previous = this.indicatedTargets;
        final Map<ParameterTargetRef, LiveTarget> next = new LinkedHashMap<> (previous);
        next.keySet ().retainAll (retained);
        this.indicatedTargets = Map.copyOf (next);
        for (final var entry: previous.entrySet ())
            if (!next.containsKey (entry.getKey ()) && entry.getValue ().addressable.getAsBoolean ()) entry.getValue ().parameter.setIndication (false);
    }


    private void reconcileIndications ()
    {
        final Map<ParameterTargetRef, LiveTarget> next = new LinkedHashMap<> ();
        for (final ParameterSlot slot: this.requestedIndications)
        {
            final ParameterTargetSnapshot value = this.snapshot.slots ().get (slot);
            final LiveTarget target = value == null ? null : this.currentTargets.get (value.target ());
            if (target != null && target.parameter != null && target.isCurrent ()) next.put (target.reference, target);
        }
        final Map<ParameterTargetRef, LiveTarget> previous = this.indicatedTargets;
        this.indicatedTargets = Map.copyOf (next);
        for (final var entry: previous.entrySet ())
            if (!next.containsKey (entry.getKey ()) && entry.getValue ().addressable.getAsBoolean ()) entry.getValue ().parameter.setIndication (false);
        for (final var entry: next.entrySet ())
            if (!previous.containsKey (entry.getKey ())) entry.getValue ().parameter.setIndication (true);
    }


    private void reconcileTargets (final DesiredParameterBanks banks)
    {
        Objects.requireNonNull (banks, "banks");
        this.currentTargets.clear ();
        if (banks.includes (ParameterBankId.SELECTED_TRACK))
        {
            for (int index = 0; index < this.selectedTrackTargets.length; index++)
                this.selectedTrackTargets[index] = this.reconcileSelectedTarget (this.selectedTrackTargets[index], index, false);
        }
        if (banks.includes (ParameterBankId.SELECTED_TRACK_SENDS))
        {
            for (int index = 0; index < this.selectedSendTargets.length; index++)
                this.selectedSendTargets[index] = this.reconcileSelectedTarget (this.selectedSendTargets[index], index, true);
        }
        if (banks.includes (ParameterBankId.ACTIVE))
        {
            for (int index = 0; index < ACTIVE_CONTROLS.length; index++)
                this.reconcileActiveTarget (index, this.surface.getContinuous (ACTIVE_CONTROLS[index]));
        }
        if (banks.includes (ParameterBankId.PROJECT_REMOTE))
        {
            final IParameterBank projectParameters = this.model.getProject ().getParameterBank ();
            for (int index = 0; index < this.projectTargets.length; index++)
                this.projectTargets[index] = this.reconcileRemoteTarget (this.projectTargets[index], index, projectParameters, "project-remote", () -> this.model.getProject ().getIdentity ());
        }
        if (banks.includes (ParameterBankId.SELECTED_DEVICE_REMOTE))
        {
            final IParameterBank deviceParameters = this.model.getCursorDevice ().getParameterBank ();
            for (int index = 0; index < this.deviceTargets.length; index++)
                this.deviceTargets[index] = this.reconcileRemoteTarget (this.deviceTargets[index], index, deviceParameters, "device-remote", () -> this.model.getCursorDevice ().getID ());
        }
        if (banks.includes (ParameterBankId.TRACK_VOLUME))
        {
            final ITrackBank tracks = this.model.getCurrentTrackBank ();
            for (int index = 0; index < this.trackVolumeTargets.length; index++)
                this.trackVolumeTargets[index] = this.reconcileCurrentTrackTarget (this.trackVolumeTargets[index], index, tracks, ITrack::getVolumeParameter);
        }
        if (banks.includes (ParameterBankId.TRACK_PAN))
        {
            final ITrackBank tracks = this.model.getCurrentTrackBank ();
            for (int index = 0; index < this.trackPanTargets.length; index++)
                this.trackPanTargets[index] = this.reconcileCurrentTrackTarget (this.trackPanTargets[index], index, tracks, ITrack::getPanParameter);
        }
        for (int sendIndex = 0; sendIndex < this.trackSendTargets.length; sendIndex++)
        {
            if (!banks.includes (ParameterBankId.trackSend (sendIndex)))
                continue;
            final int column = sendIndex;
            final ITrackBank tracks = this.model.getCurrentTrackBank ();
            final LiveTarget [] targets = this.trackSendTargets[column];
            for (int trackIndex = 0; trackIndex < targets.length; trackIndex++)
                targets[trackIndex] = this.reconcileCurrentTrackTarget (targets[trackIndex], trackIndex, tracks, track -> selectedParameter (track, column, true));
        }

        if (banks.includes (ParameterBankId.MASTER))
        {
            this.masterMixVolumeTarget = this.reconcileProjectScopedTarget (this.masterMixVolumeTarget, "project-master", 0, () -> this.model.getMasterTrack ().getVolumeParameter ());
            this.masterMixPanTarget = this.reconcileProjectScopedTarget (this.masterMixPanTarget, "project-master", 1, () -> this.model.getMasterTrack ().getPanParameter ());
            this.cueVolumeTarget = this.reconcileProjectScopedTarget (this.cueVolumeTarget, "project-master", 2, () -> this.model.getProject ().getCueVolumeParameter ());
            this.cueMixTarget = this.reconcileProjectScopedTarget (this.cueMixTarget, "project-master", 3, () -> this.model.getProject ().getCueMixParameter ());
        }

        if (!banks.includes (ParameterBankId.GLOBAL))
            return;

        this.metronomeVolumeTarget = this.reconcileProjectScopedTarget (this.metronomeVolumeTarget, "project-global", 0, this.transport::getMetronomeVolumeParameter);

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
        if (masterControl != null && masterControl.getCommand () instanceof final PushMasterVolumeCommand command && !this.surface.isSelectPressed ())
        {
            final IParameter parameter = this.model.getMasterTrack ().getVolumeParameter ();
            if (parameter.doesExist ())
            {
                final LiveTarget master = parameterTarget (
                    MASTER_VOLUME_TARGET,
                    masterControl,
                    parameter,
                    masterControl.getBindingGeneration (),
                    () -> parameter.doesExist () && masterControl.getCommand () == command && !this.surface.isSelectPressed ());
                this.currentTargets.put (master.reference, master);
            }
        }
    }


    private LiveTarget reconcileSelectedTarget (final LiveTarget existing, final int index, final boolean send)
    {
        if (this.selectedTarget == null || !this.selectedTarget.doesExist ())
            return null;
        final ITrackBank bank = this.model.getCurrentTrackBank ();
        final ITrack track = bank == null ? null : bank.getSelectedItem ().orElse (null);
        if (track == null || !track.doesExist () || !Objects.equals (this.selectedTarget.getChannelID (), track.getChannelID ()))
            return null;
        final IParameter parameter = selectedParameter (track, index, send);
        if (parameter == null || !parameter.doesExist ())
            return null;
        final ParameterTargetIdentityResolver.TargetIdentity identity = this.targetIdentities.channel (track, parameter);
        if (identity == null)
            return null;
        LiveTarget target = existing;
        if (target == null || target.parameter != parameter || !target.isCurrent () || !identity.equals (target.targetIdentity))
        {
            final long selectedGeneration = this.selectedTarget.getGeneration ();
            final String project = this.model.getProject ().getIdentity ();
            final String channel = track.getChannelID ();
            final BooleanSupplier addressable = () -> parameter.doesExist () && track.doesExist () && channel.equals (track.getChannelID ()) &&
                Objects.equals (project, this.model.getProject ().getIdentity ()) && selectedParameter (track, index, send) == parameter && identity.equals (this.targetIdentities.channel (track, parameter));
            final BooleanSupplier current = () -> addressable.getAsBoolean () && this.selectedTarget.doesExist () && selectedGeneration == this.selectedTarget.getGeneration () &&
                channel.equals (this.selectedTarget.getChannelID ()) && this.model.getCurrentTrackBank () == bank &&
                bank.getSelectedItem ().filter (candidate -> candidate == track && channel.equals (candidate.getChannelID ())).isPresent ();
            target = new LiveTarget (new ParameterTargetRef (ParameterTargetKind.LIVE, this.nextIdentity (), selectedGeneration), null, parameter, 0, identity,
                parameter::getValue, value -> parameter.setValueImmediatly ((int) Math.round (value)), current, addressable, 0.5);
        }
        if (target.isCurrent ())
            this.currentTargets.put (target.reference, target);
        return target;
    }


    private static IParameter selectedParameter (final ITrack track, final int index, final boolean send)
    {
        if (!send)
            return index == 0 ? track.getVolumeParameter () : track.getPanParameter ();
        return track.getSendBank () != null && index < track.getSendBank ().getPageSize () ? track.getSendBank ().getItem (index) : null;
    }


    private LiveTarget reconcileProjectScopedTarget (final LiveTarget existing, final String domain, final int role, final Supplier<IParameter> currentParameter)
    {
        final String projectIdentity = this.model.getProject ().getIdentity ();
        if (projectIdentity == null || projectIdentity.isBlank ())
            return null;
        final IParameter parameter = currentParameter.get ();
        if (parameter == null || !parameter.doesExist ())
            return null;

        LiveTarget target = existing;
        if (target == null || target.parameter != parameter || !target.isCurrent ())
        {
            target = parameterTarget (
                new ParameterTargetRef (ParameterTargetKind.LIVE, this.nextIdentity (), 0),
                null,
                parameter,
                0,
                new ParameterTargetIdentityResolver.TargetIdentity (domain, projectIdentity, 0, role, parameter.getName ()),
                () -> parameter.doesExist () && Objects.equals (projectIdentity, this.model.getProject ().getIdentity ()) && currentParameter.get () == parameter);
        }
        if (target.isCurrent ())
            this.currentTargets.put (target.reference, target);
        return target;
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


    private LiveTarget reconcileRemoteTarget (final LiveTarget existing, final int index, final IParameterBank bank, final String domain, final Supplier<String> owner)
    {
        if (bank == null || index >= bank.getPageSize ())
            return null;

        final IParameter parameter = bank.getItem (index);
        final ParameterTargetIdentityResolver.TargetIdentity targetIdentity = this.targetIdentities.remote (domain, owner.get (), bank, index);
        if (parameter == null || !parameter.doesExist () || targetIdentity == null)
            return null;

        LiveTarget target = existing;
        if (target == null || target.parameter != parameter || !target.targetIdentity.equals (targetIdentity))
        {
            target = parameterTarget (
                new ParameterTargetRef (ParameterTargetKind.LIVE, this.nextIdentity (), bank.getPageBank ().getSelectedItemPosition ()),
                null,
                parameter,
                0,
                targetIdentity,
                () -> parameter.doesExist () && targetIdentity.equals (this.targetIdentities.remote (domain, owner.get (), bank, index)));
        }
        if (target.isCurrent ())
            this.currentTargets.put (target.reference, target);
        return target;
    }


    private LiveTarget reconcileCurrentTrackTarget (final LiveTarget existing, final int index, final ITrackBank tracks, final java.util.function.Function<ITrack, IParameter> role)
    {
        if (tracks == null || index >= tracks.getPageSize ())
            return null;

        final ITrack track = tracks.getItem (index);
        final IParameter parameter = track == null ? null : role.apply (track);
        final ParameterTargetIdentityResolver.TargetIdentity targetIdentity = parameter == null ? null : this.targetIdentities.channel (track, parameter);
        if (track == null || !track.doesExist () || parameter == null || !parameter.doesExist () || targetIdentity == null)
            return null;

        LiveTarget target = existing;
        if (target == null || target.parameter != parameter || !target.isCurrent () || !target.targetIdentity.equals (targetIdentity))
        {
            final String project = this.model.getProject ().getIdentity ();
            final String channel = track.getChannelID ();
            final BooleanSupplier addressable = () -> parameter.doesExist () && track.doesExist () && channel.equals (track.getChannelID ()) &&
                Objects.equals (project, this.model.getProject ().getIdentity ()) && tracks.getItem (index) == track && role.apply (track) == parameter &&
                targetIdentity.equals (this.targetIdentities.channel (track, parameter));
            final BooleanSupplier current = () -> this.model.getCurrentTrackBank () == tracks && addressable.getAsBoolean ();
            target = new LiveTarget (new ParameterTargetRef (ParameterTargetKind.LIVE, this.nextIdentity (), index), null, parameter, 0, targetIdentity,
                parameter::getValue, value -> parameter.setValueImmediatly ((int) Math.round (value)), current, addressable, 0.5);
        }
        if (target.isCurrent ())
            this.currentTargets.put (target.reference, target);
        return target;
    }


    private ParameterBridgeSnapshot captureSnapshot ()
    {
        final Map<ParameterSlot, ParameterTargetSnapshot> slots = new LinkedHashMap<> (ParameterBridgeSnapshot.TARGET_CAPACITY);
        this.captureBank (slots, ParameterBankId.SELECTED_TRACK, this.selectedTrackTargets, index -> new ParameterSlot (ParameterBankId.SELECTED_TRACK, index));
        this.captureBank (slots, ParameterBankId.SELECTED_TRACK_SENDS, this.selectedSendTargets, ParameterSlot::selectedTrackSend);
        this.captureBank (slots, ParameterBankId.ACTIVE, this.activeTargets, ParameterSlot::active);
        this.captureBank (slots, ParameterBankId.PROJECT_REMOTE, this.projectTargets, ParameterSlot::projectRemote);
        this.captureBank (slots, ParameterBankId.SELECTED_DEVICE_REMOTE, this.deviceTargets, ParameterSlot::selectedDeviceRemote);
        this.captureBank (slots, ParameterBankId.TRACK_VOLUME, this.trackVolumeTargets, ParameterSlot::trackVolume);
        this.captureBank (slots, ParameterBankId.TRACK_PAN, this.trackPanTargets, ParameterSlot::trackPan);
        for (int sendIndex = 0; sendIndex < this.trackSendTargets.length; sendIndex++)
        {
            final int column = sendIndex;
            this.captureBank (slots, ParameterBankId.trackSend (column), this.trackSendTargets[column], trackIndex -> ParameterSlot.trackSend (column, trackIndex));
        }
        final LiveTarget tempo = this.requestedBanks.includes (ParameterBankId.GLOBAL) ? this.currentTargets.get (TEMPO_TARGET) : null;
        if (tempo != null)
            slots.put (ParameterSlot.TEMPO, tempo.snapshot ());
        final LiveTarget master = this.requestedBanks.includes (ParameterBankId.GLOBAL) ? this.currentTargets.get (MASTER_VOLUME_TARGET) : null;
        if (master != null)
            slots.put (ParameterSlot.MASTER_VOLUME, master.snapshot ());
        this.captureTargetSlot (slots, ParameterBankId.GLOBAL, ParameterSlot.METRONOME_VOLUME, this.metronomeVolumeTarget);
        this.captureTargetSlot (slots, ParameterBankId.MASTER, ParameterSlot.MASTER_MIX_VOLUME, this.masterMixVolumeTarget);
        this.captureTargetSlot (slots, ParameterBankId.MASTER, ParameterSlot.MASTER_MIX_PAN, this.masterMixPanTarget);
        this.captureTargetSlot (slots, ParameterBankId.MASTER, ParameterSlot.CUE_VOLUME, this.cueVolumeTarget);
        this.captureTargetSlot (slots, ParameterBankId.MASTER, ParameterSlot.CUE_MIX, this.cueMixTarget);

        final Map<ParameterTargetRef, Double> baselines = new LinkedHashMap<> ();
        this.retainedTargets.forEach ( (target, retained) -> baselines.put (target, Double.valueOf (retained.baseline)));
        return new ParameterBridgeSnapshot (slots, baselines, this.touchedTargets.keySet ());
    }


    private void captureTargetSlot (final Map<ParameterSlot, ParameterTargetSnapshot> slots, final ParameterBankId bank, final ParameterSlot slot, final LiveTarget target)
    {
        if (!this.requestedBanks.includes (bank) || target == null)
            return;
        final LiveTarget liveTarget = this.currentTargets.get (target.reference);
        if (liveTarget != null && liveTarget.isCurrent ())
            slots.put (slot, liveTarget.snapshot ());
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


    record PreparedNormalized (LiveTarget target, double value)
    {
    }


    record PreparedEnabled (LiveTarget target, boolean enabled)
    {
    }


    record PreparedTouch (ControlId owner, LiveTarget target)
    {
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
        private final BooleanSupplier addressable;
        private final double tolerance;


        private LiveTarget (final ParameterTargetRef reference, final IHwContinuousControl control, final IParameter parameter, final long bindingGeneration, final ParameterTargetIdentityResolver.TargetIdentity targetIdentity, final DoubleSupplier reader, final DoubleConsumer restorer, final BooleanSupplier current, final double tolerance)
        {
            this (reference, control, parameter, bindingGeneration, targetIdentity, reader, restorer, current, current, tolerance);
        }


        private LiveTarget (final ParameterTargetRef reference, final IHwContinuousControl control, final IParameter parameter, final long bindingGeneration, final ParameterTargetIdentityResolver.TargetIdentity targetIdentity, final DoubleSupplier reader, final DoubleConsumer restorer, final BooleanSupplier current, final BooleanSupplier addressable, final double tolerance)
        {
            this.reference = Objects.requireNonNull (reference, "reference");
            this.control = control;
            this.parameter = parameter;
            this.bindingGeneration = bindingGeneration;
            this.targetIdentity = targetIdentity;
            this.reader = Objects.requireNonNull (reader, "reader");
            this.restorer = Objects.requireNonNull (restorer, "restorer");
            this.current = Objects.requireNonNull (current, "current");
            this.addressable = Objects.requireNonNull (addressable, "addressable");
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
                this.tolerance,
                this.parameter instanceof final ISend send ? Optional.of (Boolean.valueOf (send.isEnabled ())) : Optional.empty (),
                this.targetIdentity == null || this.targetIdentity.page () < 0 || this.targetIdentity.index () < 0 ? de.mossgrabers.pull.core.api.ParameterTargetIdentitySnapshot.empty () : new de.mossgrabers.pull.core.api.ParameterTargetIdentitySnapshot (this.targetIdentity.domain (), this.targetIdentity.ownerId (), this.targetIdentity.page (), this.targetIdentity.index ()));
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
