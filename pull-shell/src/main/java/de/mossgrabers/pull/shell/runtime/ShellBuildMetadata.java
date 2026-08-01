// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.shell.runtime;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.Properties;

/**
 * Exact parent-loaded shell/API source identity embedded by the extension build.
 *
 * @param fingerprint Source fingerprint
 */
record ShellBuildMetadata (String fingerprint)
{
    static final String RESOURCE = "/META-INF/pull-shell.properties";


    ShellBuildMetadata
    {
        Objects.requireNonNull (fingerprint, "fingerprint");
        if (!fingerprint.matches ("[0-9a-f]{40}|[0-9a-f]{64}"))
            throw new IllegalArgumentException ("Invalid shell fingerprint");
    }


    static ShellBuildMetadata load ()
    {
        try (InputStream input = ShellBuildMetadata.class.getResourceAsStream (RESOURCE))
        {
            if (input == null)
                throw new IllegalStateException ("Reloadable shell is missing " + RESOURCE);

            final Properties properties = new Properties ();
            properties.load (input);
            CoreArtifactMetadata.requireExact (properties, "formatVersion", "1");
            return new ShellBuildMetadata (CoreArtifactMetadata.require (properties, "fingerprint"));
        }
        catch (final IOException failure)
        {
            throw new IllegalStateException ("Could not read reloadable shell metadata", failure);
        }
    }
}
