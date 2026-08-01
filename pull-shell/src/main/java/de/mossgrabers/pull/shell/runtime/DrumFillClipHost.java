// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.shell.runtime;

import de.mossgrabers.pull.core.api.ClipCatalogSnapshot;
import de.mossgrabers.pull.core.api.ClipTargetId;
import de.mossgrabers.pull.core.api.ControlId;
import de.mossgrabers.pull.core.api.effect.ClipLaunchPolicy;

import java.util.Map;


/**
 * Parent-owned selected-track clip catalog and its asynchronously parked launch targets.
 */
interface DrumFillClipHost
{
    /**
     * Advance the selected-track scanner and all idle launch actuators.
     *
     * @return True when the public catalog or armed-target map changed
     */
    boolean refresh ();


    /**
     * Get the last complete immutable selected-track catalog.
     *
     * @return The clip catalog
     */
    ClipCatalogSnapshot clipCatalog ();


    /**
     * Replace the complete desired control-to-target binding state. Actuators move
     * asynchronously; a binding is not launchable until it appears in
     * {@link #armedClipTargets()}.
     *
     * @param catalogGeneration Required catalog generation
     * @param bindings Complete desired bindings
     */
    void setDesiredBindings (long catalogGeneration, Map<ControlId, ClipTargetId> bindings);


    /**
     * Get bindings whose private actuator has validated the selected track, scene, name, and clip
     * content for two consecutive samples.
     *
     * @return Complete immutable armed binding state
     */
    Map<ControlId, ClipTargetId> armedClipTargets ();


    /**
     * Resolve one target without launching it.
     *
     * @param owner Logical control backed by the private actuator
     * @param catalogGeneration Required catalog generation
     * @param targetId Opaque target ID from that catalog
     * @return A parent-owned launch handle
     */
    LaunchTarget prepare (ControlId owner, long catalogGeneration, ClipTargetId targetId);


    /**
     * A resolved parent-owned target. Press uses a fully validated parked actuator; release keeps
     * that actuator frozen and addresses the exact same private slot proxy.
     */
    interface LaunchTarget
    {
        /**
         * Get the target ID.
         *
         * @return The target ID
         */
        ClipTargetId targetId ();


        /**
         * Press the target with a policy that remains frozen until release.
         *
         * @param launchPolicy Host-independent launch and release policy
         */
        void press (ClipLaunchPolicy launchPolicy);


        /**
         * Release the exact target previously pressed.
         */
        void release ();
    }
}
