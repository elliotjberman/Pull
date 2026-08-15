// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.shell.runtime;

import de.mossgrabers.framework.controller.hardware.IHwContinuousControl;
import de.mossgrabers.pull.core.api.ControllerBridgeSnapshot;
import de.mossgrabers.pull.core.api.DesiredBridgeSubscriptions;
import de.mossgrabers.pull.core.api.DesiredControllerState;
import de.mossgrabers.pull.core.api.DesiredNoteRepeat;
import de.mossgrabers.pull.core.api.DesiredParameterInteraction;
import de.mossgrabers.pull.core.api.DesiredParameterBanks;
import de.mossgrabers.pull.core.api.ParameterTargetRef;
import de.mossgrabers.pull.core.api.ParameterTargetSnapshot;
import de.mossgrabers.pull.core.api.effect.CoreEffect;

import java.util.Map;
import java.util.Objects;
import java.util.function.BooleanSupplier;


/** Stable bounded canopy used by the transactional runtime. */
interface ControllerBridge
{
    boolean refresh (long monotonicTimeNanos, DesiredBridgeSubscriptions subscriptions, DesiredParameterBanks parameterBanks);

    void activateCoreGeneration (long generation);

    void invalidate ();

    default boolean canReplaceActiveCore ()
    {
        return true;
    }

    default void abandonActiveCore ()
    {
        // No core-owned parent transaction to unwind.
    }

    TargetedParameter resolveParameterMutation (IHwContinuousControl control);

    default boolean requiresResolvedParameterMutation (final IHwContinuousControl control)
    {
        return false;
    }

    Map<ParameterTargetRef, ParameterLease> prepareParameterLeases (DesiredParameterInteraction desired, DesiredParameterBanks parameterBanks);

    boolean applyParameterLeases (Map<ParameterTargetRef, ParameterLease> prepared, DesiredParameterBanks parameterBanks);

    boolean retainsParameterTarget (ParameterTargetRef target);

    default void setNoteInputLifecycleIdle (final BooleanSupplier idle)
    {
        Objects.requireNonNull (idle, "idle");
    }

    default DesiredControllerState prepareControllerState (final DesiredControllerState state)
    {
        final DesiredControllerState requested = Objects.requireNonNull (state, "state");
        if (!requested.equals (DesiredControllerState.empty ()))
            throw new IllegalArgumentException ("Controller bridge does not support composed controller-state ownership");
        return requested;
    }

    default void applyControllerState (final DesiredControllerState state)
    {
        if (!Objects.requireNonNull (state, "state").equals (DesiredControllerState.empty ()))
            throw new IllegalArgumentException ("Controller bridge does not support composed controller-state ownership");
    }

    default DesiredNoteRepeat prepareNoteRepeat (final DesiredNoteRepeat noteRepeat)
    {
        final DesiredNoteRepeat requested = Objects.requireNonNull (noteRepeat, "noteRepeat");
        if (requested.owned ())
            throw new IllegalArgumentException ("Controller bridge does not support note-repeat ownership");
        return requested;
    }

    default void applyNoteRepeat (final DesiredNoteRepeat noteRepeat)
    {
        if (Objects.requireNonNull (noteRepeat, "noteRepeat").owned ())
            throw new IllegalArgumentException ("Controller bridge does not support note-repeat ownership");
    }

    ControllerBridgeSnapshot snapshot ();

    PreparedAction prepare (CoreEffect effect, Map<ParameterTargetRef, ParameterLease> parameterLeases);

    void apply (PreparedAction action);


    /** Opaque stable action prepared before transactional commit. */
    interface PreparedAction
    {
        // Marker type.
    }


    /** Opaque exact parameter actuator retained across asynchronous host readback. */
    interface ParameterLease
    {
        // Marker type.
    }


    /** Exact target observed immediately before an established mutation. */
    record TargetedParameter (ParameterTargetSnapshot target)
    {
        public TargetedParameter
        {
            Objects.requireNonNull (target, "target");
        }
    }
}
