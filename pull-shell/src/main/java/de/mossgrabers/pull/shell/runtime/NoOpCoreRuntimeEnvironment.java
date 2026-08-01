// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.shell.runtime;

import de.mossgrabers.pull.core.api.ControllerSnapshot;
import de.mossgrabers.pull.core.api.CoreResult;
import de.mossgrabers.pull.core.api.ShellCapabilities;

import java.util.Objects;
import java.util.Set;

/**
 * Behavior-neutral environment used until the first stable hardware proxy is migrated.
 */
final class NoOpCoreRuntimeEnvironment implements CoreRuntimeEnvironment
{
    private final long timeOrigin = System.nanoTime ();
    private long revision;
    private long generation;


    /** {@inheritDoc} */
    @Override
    public ControllerSnapshot snapshot ()
    {
        final long elapsed = Math.max (0, System.nanoTime () - this.timeOrigin);
        return new ControllerSnapshot (this.revision++, elapsed, ShellCapabilities.empty (), Set.of (), Set.of ());
    }


    /** {@inheritDoc} */
    @Override
    public void validate (final CoreResult result)
    {
        Objects.requireNonNull (result, "result");
        if (!result.effects ().isEmpty () || !result.desiredOutput ().lights ().isEmpty ())
            throw new IllegalStateException ("The stable shell does not own any core effects or hardware output yet");
    }


    /** {@inheritDoc} */
    @Override
    public void commit (final long generation, final CoreResult result)
    {
        this.generation = generation;
    }


    /** {@inheritDoc} */
    @Override
    public void invalidate (final long generation)
    {
        this.generation = generation;
    }


    long generation ()
    {
        return this.generation;
    }
}
