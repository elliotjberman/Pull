// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.shell.runtime;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Stable development publication paths.
 *
 * @param root The reload directory
 */
record RuntimePaths (Path root)
{
    static final String DIRECTORY_PROPERTY = "pull.core.reload.dir";
    static final String DIRECTORY_ENVIRONMENT = "PULL_CORE_RELOAD_DIR";
    static final String CANDIDATE_FILE = "candidate.properties";
    static final String STATUS_FILE = "status.properties";


    RuntimePaths
    {
        root = Objects.requireNonNull (root, "root").toAbsolutePath ().normalize ();
    }


    static RuntimePaths fromSystem ()
    {
        String configured = System.getProperty (DIRECTORY_PROPERTY);
        if (configured == null || configured.isBlank ())
            configured = System.getenv (DIRECTORY_ENVIRONMENT);
        if (configured != null && !configured.isBlank ())
        {
            final Path configuredPath = Path.of (configured);
            if (configuredPath.isAbsolute ())
                return new RuntimePaths (configuredPath);
        }

        final String userHome = Objects.requireNonNull (System.getProperty ("user.home"), "user.home");
        return new RuntimePaths (Path.of (userHome, ".drivenbymoss", "pull", "reload"));
    }


    Path candidate ()
    {
        return this.root.resolve (CANDIDATE_FILE);
    }


    Path status ()
    {
        return this.root.resolve (STATUS_FILE);
    }
}
