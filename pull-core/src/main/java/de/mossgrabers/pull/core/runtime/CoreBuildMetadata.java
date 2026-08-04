// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.runtime;

import de.mossgrabers.pull.core.api.CoreApi;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.Properties;

/**
 * Build identity embedded in each core artifact.
 *
 * @param buildId Exact immutable build identifier
 */
record CoreBuildMetadata (String buildId)
{
    private static final String RESOURCE = "/META-INF/pull-core.properties";


    /**
     * Load and validate the identity of the artifact containing this class.
     *
     * @return The embedded build identity
     */
    static CoreBuildMetadata load ()
    {
        final Properties properties = new Properties ();
        try (InputStream input = CoreBuildMetadata.class.getResourceAsStream (RESOURCE))
        {
            if (input == null)
                throw new IllegalStateException ("Core artifact is missing " + RESOURCE);
            properties.load (input);
        }
        catch (final IOException ex)
        {
            throw new IllegalStateException ("Could not read core build metadata", ex);
        }

        final int formatVersion = parsePositiveInteger (properties, "formatVersion");
        if (formatVersion != 1)
            throw new IllegalStateException ("Unsupported core metadata formatVersion: " + formatVersion);

        final int apiVersion = parsePositiveInteger (properties, "apiVersion");
        if (apiVersion != CoreApi.VERSION)
            throw new IllegalStateException ("Core metadata apiVersion " + apiVersion + " does not match descriptor API " + CoreApi.VERSION);

        final String buildId = Objects.requireNonNull (properties.getProperty ("buildId"), "Core metadata is missing buildId").trim ();
        if (buildId.isEmpty ())
            throw new IllegalStateException ("Core metadata buildId must not be blank");
        return new CoreBuildMetadata (buildId);
    }


    private static int parsePositiveInteger (final Properties properties, final String key)
    {
        final String value = properties.getProperty (key);
        try
        {
            final int parsed = Integer.parseInt (Objects.requireNonNull (value, "Core metadata is missing " + key));
            if (parsed <= 0)
                throw new IllegalStateException ("Core metadata " + key + " must be positive");
            return parsed;
        }
        catch (final NumberFormatException ex)
        {
            throw new IllegalStateException ("Core metadata " + key + " is not an integer: " + value, ex);
        }
    }
}
