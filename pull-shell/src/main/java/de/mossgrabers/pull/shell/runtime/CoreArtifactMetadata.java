// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.shell.runtime;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Properties;
import java.util.function.Predicate;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Identity embedded in every reloadable core artifact.
 *
 * @param apiVersion The exact parent API version
 * @param buildId The immutable build identifier
 */
record CoreArtifactMetadata (int apiVersion, String buildId)
{
    static final String RESOURCE = "META-INF/pull-core.properties";
    static final Predicate<String> VALID_BUILD_ID = value -> value != null && value.matches ("[A-Za-z0-9][A-Za-z0-9._-]{0,127}");


    CoreArtifactMetadata
    {
        if (apiVersion <= 0)
            throw new IllegalArgumentException ("apiVersion must be positive");
        if (!VALID_BUILD_ID.test (buildId))
            throw new IllegalArgumentException ("Invalid core buildId");
    }


    static CoreArtifactMetadata read (final Path jarPath) throws IOException
    {
        try (JarFile jar = new JarFile (Objects.requireNonNull (jarPath, "jarPath").toFile (), false))
        {
            final JarEntry entry = jar.getJarEntry (RESOURCE);
            if (entry == null)
                throw new IOException ("Core JAR is missing " + RESOURCE);

            final Properties properties = new Properties ();
            try (InputStream input = jar.getInputStream (entry))
            {
                properties.load (input);
            }

            requireExact (properties, "formatVersion", "1");
            final int apiVersion = parsePositiveInteger (properties, "apiVersion");
            final String buildId = require (properties, "buildId");
            return new CoreArtifactMetadata (apiVersion, buildId);
        }
    }


    static String require (final Properties properties, final String key)
    {
        final String value = properties.getProperty (key);
        if (value == null || value.isBlank ())
            throw new IllegalArgumentException ("Missing property " + key);
        return value;
    }


    static int parsePositiveInteger (final Properties properties, final String key)
    {
        final String value = require (properties, key);
        try
        {
            final int parsed = Integer.parseInt (value);
            if (parsed <= 0)
                throw new IllegalArgumentException (key + " must be positive");
            return parsed;
        }
        catch (final NumberFormatException failure)
        {
            throw new IllegalArgumentException (key + " must be a positive integer");
        }
    }


    static void requireExact (final Properties properties, final String key, final String expected)
    {
        if (!expected.equals (properties.getProperty (key)))
            throw new IllegalArgumentException (key + " must be " + expected);
    }
}
