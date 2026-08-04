// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.shell.runtime;

import de.mossgrabers.pull.core.api.CoreProvider;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Enumeration;

/**
 * Shares JDK and core-API classes while loading every core implementation and dependency only
 * from the candidate JAR.
 */
final class IsolatedCoreClassLoader extends URLClassLoader
{
    private static final String CORE_API_PACKAGE = "de.mossgrabers.pull.core.api.";
    private static final String CORE_API_RESOURCE_PACKAGE = "de/mossgrabers/pull/core/api/";
    private static final String PROVIDER_RESOURCE = "META-INF/services/" + CoreProvider.class.getName ();
    private static final ClassLoader PLATFORM_CLASS_LOADER = ClassLoader.getPlatformClassLoader ();

    private static final String [] DENIED_PACKAGES =
    {
        "com.bitwig.",
        "de.mossgrabers.bitwig.",
        "de.mossgrabers.controller.",
        "de.mossgrabers.framework.",
        "de.mossgrabers.pull.shell."
    };


    IsolatedCoreClassLoader (final URL coreJar, final ClassLoader parent)
    {
        super (new URL []
        {
            coreJar
        }, parent);
    }


    @Override
    protected Class<?> loadClass (final String name, final boolean resolve) throws ClassNotFoundException
    {
        synchronized (this.getClassLoadingLock (name))
        {
            Class<?> loadedClass = this.findLoadedClass (name);
            if (loadedClass == null)
            {
                if (isDenied (name))
                    throw new ClassNotFoundException ("Core JARs may not access shell class " + name);

                if (name.startsWith (CORE_API_PACKAGE))
                    loadedClass = this.getParent ().loadClass (name);
                else
                    loadedClass = this.loadPlatformOrCandidate (name);
            }

            if (resolve)
                this.resolveClass (loadedClass);
            return loadedClass;
        }
    }


    /**
     * Service discovery must never see a provider accidentally present on the parent classpath.
     */
    @Override
    public URL getResource (final String name)
    {
        if (PROVIDER_RESOURCE.equals (name))
            return this.findResource (name);
        if (name.startsWith (CORE_API_RESOURCE_PACKAGE))
            return this.getParent ().getResource (name);
        return this.findResource (name);
    }


    /**
     * Service discovery must never see providers accidentally present on the parent classpath.
     */
    @Override
    public Enumeration<URL> getResources (final String name) throws IOException
    {
        if (PROVIDER_RESOURCE.equals (name))
            return this.findResources (name);
        if (name.startsWith (CORE_API_RESOURCE_PACKAGE))
            return this.getParent ().getResources (name);
        return this.findResources (name);
    }


    private Class<?> loadPlatformOrCandidate (final String className) throws ClassNotFoundException
    {
        try
        {
            return PLATFORM_CLASS_LOADER.loadClass (className);
        }
        catch (final ClassNotFoundException notInPlatform)
        {
            return this.findClass (className);
        }
    }


    private static boolean isDenied (final String className)
    {
        for (final String deniedPackage: DENIED_PACKAGES)
        {
            if (className.startsWith (deniedPackage))
                return true;
        }
        return false;
    }
}
