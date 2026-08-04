// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.shell.runtime;

import de.mossgrabers.pull.core.api.CoreProvider;

import java.io.IOException;
import java.util.Objects;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.function.Supplier;

/**
 * Owns a provider and the isolated classloader that defined it.
 */
public final class LoadedCoreProvider implements CoreProviderSource
{
    private ServiceLoader.Provider<CoreProvider> providerFactory;
    private CoreProvider provider;
    private IsolatedCoreClassLoader classLoader;


    LoadedCoreProvider (final ServiceLoader.Provider<CoreProvider> providerFactory, final IsolatedCoreClassLoader classLoader)
    {
        this.providerFactory = Objects.requireNonNull (providerFactory, "providerFactory");
        this.classLoader = Objects.requireNonNull (classLoader, "classLoader");
    }


    /**
     * Instantiate the provider on the calling thread. Subsequent calls return the same instance.
     *
     * @return The child-loaded provider
     * @throws CoreLoadException Provider construction failed
     */
    public synchronized CoreProvider instantiateProvider () throws CoreLoadException
    {
        if (this.classLoader == null)
            throw new IllegalStateException ("The core provider is closed");

        if (this.provider != null)
            return this.provider;

        try
        {
            this.provider = this.invokeRaw (this.providerFactory::get);
            if (this.provider.getClass ().getClassLoader () != this.classLoader)
                throw new CoreLoadException ("CoreProvider must be defined by the candidate JAR");
        }
        catch (final ServiceConfigurationError | LinkageError | RuntimeException failure)
        {
            throw new CoreLoadException ("CoreProvider construction failed: " + failure.getClass ().getSimpleName () + CoreJarLoader.messageSuffix (failure));
        }
        return this.provider;
    }


    /** {@inheritDoc} */
    @Override
    public synchronized <T> T invokeWithContext (final Supplier<T> operation)
    {
        Objects.requireNonNull (operation, "operation");
        if (this.classLoader == null)
            throw new IllegalStateException ("The core provider is closed");

        try
        {
            return this.invokeRaw (operation);
        }
        catch (final VirtualMachineError fatal)
        {
            throw fatal;
        }
        catch (final RuntimeException | Error failure)
        {
            throw new CoreInvocationException ("Core invocation failed: " + failure.getClass ().getSimpleName () + CoreJarLoader.messageSuffix (failure));
        }
    }


    private <T> T invokeRaw (final Supplier<T> operation)
    {
        Objects.requireNonNull (operation, "operation");
        if (this.classLoader == null)
            throw new IllegalStateException ("The core provider is closed");

        final Thread thread = Thread.currentThread ();
        final ClassLoader previousContextClassLoader = thread.getContextClassLoader ();
        boolean contextChanged = false;
        try
        {
            thread.setContextClassLoader (this.classLoader);
            contextChanged = true;
            return operation.get ();
        }
        finally
        {
            if (contextChanged)
                thread.setContextClassLoader (previousContextClassLoader);
        }
    }


    /**
     * Release the provider and close its classloader. Closing twice is safe.
     *
     * @throws IOException Could not close the underlying JAR
     */
    @Override
    public synchronized void close () throws IOException
    {
        if (this.classLoader == null)
            return;

        final IsolatedCoreClassLoader loader = this.classLoader;
        this.providerFactory = null;
        this.provider = null;
        this.classLoader = null;
        loader.close ();
    }
}
