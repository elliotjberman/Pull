// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.shell.runtime;

import de.mossgrabers.framework.controller.hardware.IHwContinuousControl;
import de.mossgrabers.pull.core.api.ControllerBridgeSnapshot;
import de.mossgrabers.pull.core.api.CoreExecutionRequirements;
import de.mossgrabers.pull.core.api.DesiredBridgeSubscriptions;
import de.mossgrabers.pull.core.api.DesiredControllerWorkspace;
import de.mossgrabers.pull.core.api.DesiredParameterInteraction;
import de.mossgrabers.pull.core.api.DesiredParameterBanks;
import de.mossgrabers.pull.core.api.ParameterTargetRef;
import de.mossgrabers.pull.core.api.ParameterTargetSnapshot;
import de.mossgrabers.pull.core.api.effect.CoreEffect;

import java.util.Map;
import java.util.Objects;


/** Stable bounded canopy used by the transactional runtime. */
interface ControllerBridge
{
    boolean refresh (long monotonicTimeNanos, DesiredBridgeSubscriptions subscriptions, DesiredParameterBanks parameterBanks);

    void activateCoreGeneration (long generation);

    void invalidate ();

    default CoreExecutionRequirements prepareExecutionRequirements (final CoreExecutionRequirements requirements)
    {
        return Objects.requireNonNull (requirements, "requirements");
    }

    default void applyExecutionRequirements (final CoreExecutionRequirements requirements)
    {
        Objects.requireNonNull (requirements, "requirements");
    }

    default boolean canReplaceActiveCore ()
    {
        return true;
    }

    default void abandonActiveCore ()
    {
        // No parent-owned transaction to unwind.
    }

    TargetedParameter resolveParameterMutation (IHwContinuousControl control);

    default boolean requiresResolvedParameterMutation (final IHwContinuousControl control)
    {
        return false;
    }

    Map<ParameterTargetRef, ParameterLease> prepareParameterLeases (DesiredParameterInteraction desired, DesiredParameterBanks parameterBanks);

    boolean applyParameterLeases (Map<ParameterTargetRef, ParameterLease> prepared, DesiredParameterBanks parameterBanks);

    boolean retainsParameterTarget (ParameterTargetRef target);

    DesiredControllerWorkspace prepareWorkspace (DesiredControllerWorkspace workspace);

    void applyWorkspace (DesiredControllerWorkspace workspace);

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
