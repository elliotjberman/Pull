// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.shell.runtime;

import de.mossgrabers.pull.core.api.ControllerSnapshot;
import de.mossgrabers.pull.core.api.CoreResult;

/**
 * Stable shell operations used by the reload transaction.
 */
interface CoreRuntimeEnvironment
{
    /**
     * Capture the shell's authoritative state.
     *
     * @return The current snapshot
     */
    ControllerSnapshot snapshot ();


    /**
     * Validate a result without changing shell or hardware state.
     *
     * @param result The candidate result
     */
    void validate (CoreResult result);


    /**
     * Atomically replace the core-owned shell state for a generation.
     * After {@link #validate(CoreResult)} succeeds, this must be a non-throwing in-memory
     * ownership/buffer swap. Implementations publish the generation before scheduling work; all
     * failure-prone validation or effect preparation belongs in {@code validate}.
     *
     * @param generation The active runtime generation
     * @param result The complete result to apply
     */
    void commit (long generation, CoreResult result);


    /**
     * Invalidate callbacks and state owned by older generations.
     *
     * @param generation The new invalidation generation
     */
    void invalidate (long generation);
}
