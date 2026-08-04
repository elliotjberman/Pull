// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.shell.runtime;

import de.mossgrabers.pull.core.api.CoreProvider;

import java.io.IOException;
import java.util.function.Supplier;

/**
 * Supplies one candidate provider and owns the resources used to load it.
 */
interface CoreProviderSource extends AutoCloseable
{
    /**
     * Instantiate the provider on the calling thread.
     *
     * @return The candidate provider
     * @throws CoreLoadException Provider construction failed
     */
    CoreProvider instantiateProvider () throws CoreLoadException;


    /**
     * Invoke child code with this source's classloader as the thread context classloader.
     *
     * @param operation The synchronous child invocation
     * @param <T> The result type
     * @return The invocation result
     * @throws CoreInvocationException The child invocation failed
     */
    <T> T invokeWithContext (Supplier<T> operation);


    /**
     * Release all candidate-loading resources. Closing twice must be safe.
     *
     * @throws IOException Resource release failed
     */
    @Override
    void close () throws IOException;
}
