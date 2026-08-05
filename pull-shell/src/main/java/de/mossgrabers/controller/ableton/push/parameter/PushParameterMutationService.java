// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.controller.ableton.push.parameter;

import de.mossgrabers.controller.ableton.push.command.continuous.PushMasterVolumeCommand;
import de.mossgrabers.controller.ableton.push.controller.PushControlSurface;
import de.mossgrabers.framework.controller.ButtonID;
import de.mossgrabers.framework.controller.ContinuousID;
import de.mossgrabers.framework.controller.hardware.IHwContinuousControl;
import de.mossgrabers.framework.daw.IModel;
import de.mossgrabers.framework.daw.ITransport;
import de.mossgrabers.framework.parameter.IParameter;
import de.mossgrabers.framework.utils.ButtonEvent;

import java.util.Objects;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;


/**
 * Resolves Push controls to parameter targets and owns the Shift snapback lifecycle.
 */
public final class PushParameterMutationService
{
    private static final String FIXED_DOMAIN = "fixed";
    private static final String PROXY_SLOT_DOMAIN = "proxy-slot";
    private static final Set<ButtonID> POTENTIAL_TARGET_REBINDS = Set.of (
        ButtonID.ROW1_1,
        ButtonID.ROW1_2,
        ButtonID.ROW1_3,
        ButtonID.ROW1_4,
        ButtonID.ROW1_5,
        ButtonID.ROW1_6,
        ButtonID.ROW1_7,
        ButtonID.ROW1_8,
        ButtonID.ROW2_1,
        ButtonID.ROW2_2,
        ButtonID.ROW2_3,
        ButtonID.ROW2_4,
        ButtonID.ROW2_5,
        ButtonID.ROW2_6,
        ButtonID.ROW2_7,
        ButtonID.ROW2_8,
        ButtonID.PAGE_LEFT,
        ButtonID.PAGE_RIGHT,
        ButtonID.ARROW_LEFT,
        ButtonID.ARROW_RIGHT,
        ButtonID.ARROW_UP,
        ButtonID.ARROW_DOWN,
        ButtonID.DEVICE_LEFT,
        ButtonID.DEVICE_RIGHT,
        ButtonID.DEVICE,
        ButtonID.TRACK,
        ButtonID.CLIP,
        ButtonID.USER,
        ButtonID.SESSION,
        ButtonID.NOTE,
        ButtonID.MASTERTRACK,
        ButtonID.ADD_EFFECT,
        ButtonID.ADD_TRACK,
        ButtonID.BROWSE);

    private final ParameterTargetResolver targetResolver;
    private final SnapbackInterceptor snapback;


    /**
     * Create the production Push target resolver and snapback session.
     *
     * @param surface Push surface
     * @param model DAW model
     */
    public PushParameterMutationService (final PushControlSurface surface, final IModel model)
    {
        this (new PushTargetResolver (surface, model), message -> model.getHost ().println ("Snapback: " + message));
    }


    PushParameterMutationService (final ParameterTargetResolver targetResolver)
    {
        this (targetResolver, ignored -> {
            // No-op test warning sink.
        });
    }


    private PushParameterMutationService (final ParameterTargetResolver targetResolver, final java.util.function.Consumer<String> warningSink)
    {
        this.targetResolver = Objects.requireNonNull (targetResolver, "targetResolver");
        this.snapback = new SnapbackInterceptor (request -> request.mutation ().run (), warningSink);
    }


    /**
     * Route one controller-originated numeric mutation through the snapback interceptor.
     *
     * @param controlID Physical continuous control
     * @param control Current hardware binding
     * @param mutation Established mutation callback
     */
    public void mutate (final ContinuousID controlID, final IHwContinuousControl control, final Runnable mutation)
    {
        final ParameterMutationTarget target = this.targetResolver.resolve (
            Objects.requireNonNull (controlID, "controlID"),
            Objects.requireNonNull (control, "control"));
        this.snapback.mutate (target == null ? ParameterMutationRequest.persistent (mutation) : ParameterMutationRequest.snapback (target, mutation));
    }


    /**
     * Preserve the complete Shift lifecycle while using release as the snapback barrier.
     *
     * @param event Shift event
     * @param flushPendingMotion Flush callback
     * @param stableDispatch Established Shift dispatch
     */
    public void routeShift (final ButtonEvent event, final Runnable flushPendingMotion, final Runnable stableDispatch)
    {
        Objects.requireNonNull (event, "event");
        Objects.requireNonNull (flushPendingMotion, "flushPendingMotion");
        Objects.requireNonNull (stableDispatch, "stableDispatch");
        switch (event)
        {
            case DOWN -> {
                stableDispatch.run ();
                this.snapback.triggerPressed ();
            }
            case LONG -> stableDispatch.run ();
            case UP -> {
                flushPendingMotion.run ();
                stableDispatch.run ();
                this.snapback.triggerReleased ();
            }
        }
    }


    /**
     * Serialize a controller action which may rebind a retained parameter proxy.
     *
     * @param button Physical button
     * @param flushPendingMotion Flush callback
     * @param stableDispatch Established button dispatch
     */
    public void routeButton (final ButtonID button, final Runnable flushPendingMotion, final Runnable stableDispatch)
    {
        Objects.requireNonNull (button, "button");
        Objects.requireNonNull (flushPendingMotion, "flushPendingMotion");
        final Runnable checkedDispatch = Objects.requireNonNull (stableDispatch, "stableDispatch");
        if (!POTENTIAL_TARGET_REBINDS.contains (button))
        {
            checkedDispatch.run ();
            return;
        }

        flushPendingMotion.run ();
        this.snapback.beforePotentialTargetRebind (checkedDispatch);
    }


    /**
     * Keep the normalized core half of a target-rebinding button edge behind the same restoration
     * barrier as its stable half.
     *
     * @param button Physical button
     * @param coreDispatch Normalized core event delivery
     */
    public void routeCoreButton (final ButtonID button, final Runnable coreDispatch)
    {
        Objects.requireNonNull (button, "button");
        final Runnable checkedDispatch = Objects.requireNonNull (coreDispatch, "coreDispatch");
        if (POTENTIAL_TARGET_REBINDS.contains (button))
            this.snapback.afterPotentialTargetRebind (checkedDispatch);
        else
            checkedDispatch.run ();
    }


    /**
     * Reconcile restoration requests with host read-back.
     */
    public void tick ()
    {
        this.snapback.tick ();
    }


    /**
     * Best-effort restore retained targets before extension shutdown.
     */
    public void shutdown ()
    {
        this.snapback.shutdown ();
    }


    @FunctionalInterface
    interface ParameterTargetResolver
    {
        ParameterMutationTarget resolve (ContinuousID controlID, IHwContinuousControl control);
    }


    private static final class PushTargetResolver implements ParameterTargetResolver
    {
        private final PushControlSurface surface;
        private final IModel model;


        private PushTargetResolver (final PushControlSurface surface, final IModel model)
        {
            this.surface = Objects.requireNonNull (surface, "surface");
            this.model = Objects.requireNonNull (model, "model");
        }


        @Override
        public ParameterMutationTarget resolve (final ContinuousID controlID, final IHwContinuousControl control)
        {
            final IParameter parameter = control.getBoundParameter ();
            if (parameter != null)
            {
                final long generation = control.getBindingGeneration ();
                return integerTarget (
                    new ParameterTargetRef (PROXY_SLOT_DOMAIN, controlID.name (), generation),
                    parameter,
                    () -> parameter.doesExist () && control.getBindingGeneration () == generation && control.getBoundParameter () == parameter);
            }

            if (controlID == ContinuousID.TEMPO)
            {
                final ITransport transport = this.model.getTransport ();
                return new FunctionalTarget (
                    new ParameterTargetRef (FIXED_DOMAIN, "tempo", 0),
                    transport::getTempo,
                    transport::setTempo,
                    () -> true,
                    0.001);
            }

            if (controlID != ContinuousID.MASTER_KNOB || !(control.getCommand () instanceof final PushMasterVolumeCommand command) || !command.isMasterVolumeMode () || this.surface.isSelectPressed ())
                return null;

            final IParameter masterVolume = this.model.getMasterTrack ().getVolumeParameter ();
            return integerTarget (
                new ParameterTargetRef (FIXED_DOMAIN, "master-volume", 0),
                masterVolume,
                () -> masterVolume.doesExist () && control.getCommand () == command && command.isMasterVolumeMode ());
        }


        private static ParameterMutationTarget integerTarget (final ParameterTargetRef reference, final IParameter parameter, final BooleanSupplier current)
        {
            return new FunctionalTarget (
                reference,
                parameter::getValue,
                value -> parameter.setValueImmediatly ((int) Math.round (value)),
                current,
                0.5);
        }
    }


    private record FunctionalTarget (ParameterTargetRef reference, DoubleSupplier reader, DoubleConsumer restorer, BooleanSupplier current, double tolerance) implements ParameterMutationTarget
    {
        private FunctionalTarget
        {
            Objects.requireNonNull (reference, "reference");
            Objects.requireNonNull (reader, "reader");
            Objects.requireNonNull (restorer, "restorer");
            Objects.requireNonNull (current, "current");
            if (!Double.isFinite (tolerance) || tolerance < 0)
                throw new IllegalArgumentException ("tolerance must be finite and non-negative");
        }


        @Override
        public double readAuthoritativeValue ()
        {
            return this.reader.getAsDouble ();
        }


        @Override
        public void restore (final double value)
        {
            this.restorer.accept (value);
        }


        @Override
        public boolean isCurrent ()
        {
            return this.current.getAsBoolean ();
        }


        @Override
        public boolean isAt (final double expected)
        {
            return Math.abs (this.readAuthoritativeValue () - expected) <= this.tolerance;
        }
    }
}
