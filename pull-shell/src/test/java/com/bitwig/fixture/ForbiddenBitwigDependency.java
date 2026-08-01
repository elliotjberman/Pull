// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package com.bitwig.fixture;

/**
 * A Bitwig-owned type that a candidate must never load.
 */
public final class ForbiddenBitwigDependency
{
    private ForbiddenBitwigDependency ()
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
        return "forbidden-bitwig";
    }
}
