// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.shell.fixture;

/**
 * A shell-owned type that a candidate must never load.
 */
public final class ForbiddenShellDependency
{
    private ForbiddenShellDependency ()
    {
        // Utility class
    }


    /**
     * Return an identifier only if the classloader boundary is broken.
     *
     * @return A forbidden build identifier
     */
    public static String buildId ()
    {
        return "forbidden-shell";
    }
}
