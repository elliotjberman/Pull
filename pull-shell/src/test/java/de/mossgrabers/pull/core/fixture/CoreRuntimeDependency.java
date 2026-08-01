// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.fixture;

/**
 * Parent-visible core class that a candidate must never use as fallback.
 */
public final class CoreRuntimeDependency
{
    private CoreRuntimeDependency ()
    {
        // Utility class
    }


    /**
     * Return an identifier only if the classloader boundary is broken.
     *
     * @return A parent-fallback build identifier
     */
    public static String buildId ()
    {
        return "forbidden-parent-core";
    }
}
