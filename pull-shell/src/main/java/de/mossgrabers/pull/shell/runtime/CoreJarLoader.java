// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.shell.runtime;

import de.mossgrabers.pull.core.api.CoreProvider;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;

/**
 * Opens a core JAR in an isolated classloader and discovers its single provider.
 */
public final class CoreJarLoader
{
    private final ClassLoader parentClassLoader;


    /**
     * Create a loader that shares only the shell's parent-loaded core API.
     */
    public CoreJarLoader ()
    {
        this (CoreProvider.class.getClassLoader ());
    }


    CoreJarLoader (final ClassLoader parentClassLoader)
    {
        this.parentClassLoader = Objects.requireNonNull (parentClassLoader, "parentClassLoader");
    }


    /**
     * Load exactly one provider from an immutable core JAR.
     *
     * @param coreJar Path to the candidate JAR
     * @return A closeable provider handle
     * @throws IOException The JAR cannot be read or its loader cannot be closed after failure
     * @throws CoreLoadException Provider discovery failed or did not find exactly one provider
     */
    public LoadedCoreProvider load (final Path coreJar) throws IOException, CoreLoadException
    {
        final Path candidate = Objects.requireNonNull (coreJar, "coreJar").toAbsolutePath ().normalize ();
        if (!Files.isRegularFile (candidate) || !Files.isReadable (candidate))
            throw new IOException ("Core JAR is not a readable regular file: " + candidate);

        final IsolatedCoreClassLoader classLoader = new IsolatedCoreClassLoader (candidate.toUri ().toURL (), this.parentClassLoader);
        try
        {
            return new LoadedCoreProvider (discoverProvider (classLoader), classLoader);
        }
        catch (final CoreLoadException | RuntimeException | Error failure)
        {
            try
            {
                classLoader.close ();
            }
            catch (final IOException closeFailure)
            {
                failure.addSuppressed (closeFailure);
            }
            throw failure;
        }
    }


    private static ServiceLoader.Provider<CoreProvider> discoverProvider (final IsolatedCoreClassLoader classLoader) throws CoreLoadException
    {
        try
        {
            final List<ServiceLoader.Provider<CoreProvider>> providers = ServiceLoader.load (CoreProvider.class, classLoader).stream ().toList ();
            if (providers.size () != 1)
                throw new CoreLoadException ("Core JAR must declare exactly one CoreProvider; found " + providers.size ());

            final ServiceLoader.Provider<CoreProvider> provider = providers.getFirst ();
            if (provider.type ().getClassLoader () != classLoader)
                throw new CoreLoadException ("CoreProvider must be defined by the candidate JAR");
            return provider;
        }
        catch (final ServiceConfigurationError | LinkageError | RuntimeException failure)
        {
            throw new CoreLoadException ("CoreProvider discovery failed: " + failure.getClass ().getSimpleName () + messageSuffix (failure));
        }
    }


    static String messageSuffix (final Throwable failure)
    {
        final String message = failure.getMessage ();
        return message == null || message.isBlank () ? "" : ": " + message;
    }
}
