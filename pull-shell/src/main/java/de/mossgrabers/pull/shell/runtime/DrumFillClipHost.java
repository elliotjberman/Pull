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
     * Authoritative playback state observed for one parked launch target.
     *
     * @param playing True when the target is currently playing
     * @param playbackQueued True when the target is queued for playback
     * @param stopQueued True when the target is playing and its track is queued to stop
     */
    record PlaybackState (boolean playing, boolean playbackQueued, boolean stopQueued)
    {
        // Immutable value.
    }


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
         * Request release of the exact target previously pressed. At most one host release is
         * sent, and the actuator remains frozen so {@link #playbackState()} can acknowledge the
         * resulting host transition. A thrown call is not considered submitted and may be retried.
         */
        void release ();


        /**
         * Get the latest authoritative playback state for the still-frozen target.
         *
         * @return Current host playback state
         */
        default PlaybackState playbackState ()
        {
            return new PlaybackState (false, false, false);
        }


        /**
         * Finalize the target after its host transition has been acknowledged and unlock its
         * actuator. This operation is idempotent.
         */
        default void retire ()
        {
            // Hosts without a retained actuator have nothing to unlock.
        }
    }
}
