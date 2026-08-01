// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.shell.runtime;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Verified parent-only candidate handed from the watcher to the controller thread.
 *
 * @param requestGeneration Monotonic publication request
 * @param apiVersion Verified API version
 * @param buildId Verified build identifier
 * @param jarPath Verified immutable JAR path
 */
record PreparedCoreCandidate (long requestGeneration, int apiVersion, String buildId, Path jarPath)
{
    PreparedCoreCandidate
    {
        if (requestGeneration <= 0)
            throw new IllegalArgumentException ("requestGeneration must be positive");
        if (apiVersion <= 0)
            throw new IllegalArgumentException ("apiVersion must be positive");
        buildId = Objects.requireNonNull (buildId, "buildId");
        jarPath = Objects.requireNonNull (jarPath, "jarPath").toAbsolutePath ().normalize ();
    }
}
