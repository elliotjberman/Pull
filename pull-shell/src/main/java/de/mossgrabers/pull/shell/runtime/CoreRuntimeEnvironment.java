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
     * Test whether replacing the active core would preserve every admitted semantic transaction.
     *
     * @return True when a candidate may activate now
     */
    default boolean canReplaceActiveCore ()
    {
        return true;
    }


    /**
     * Validate and resolve a result without changing shell or hardware state.
     *
     * @param result The candidate result
     * @return The parent-owned prepared result
     */
    PreparedCoreResult prepare (CoreResult result);


    /**
     * Atomically replace the core-owned shell state for a generation.
     * After {@link #prepare(CoreResult)} succeeds, this must be a non-throwing in-memory
     * ownership/buffer swap. All failure-prone validation and effect resolution belongs in
     * {@code prepare}.
     *
     * @param generation The active runtime generation
     * @param result The prepared result to commit
     */
    void commit (long generation, PreparedCoreResult result);


    /**
     * Apply external effects from the committed result. The runtime invokes this only after the
     * active core and generation have been published. Implementations must generation-fence any
     * asynchronous work they create.
     *
     * @param generation The committed runtime generation
     */
    void apply (long generation);


    /**
     * Invalidate callbacks and state owned by older generations.
     *
     * @param generation The new invalidation generation
     */
    void invalidate (long generation);


    /** Preserve the last committed output while abandoning a rejected child transition. */
    default void quarantine (final long generation)
    {
        // Environments without retained output fall back to ordinary invalidation.
        this.invalidate (generation);
    }
}
