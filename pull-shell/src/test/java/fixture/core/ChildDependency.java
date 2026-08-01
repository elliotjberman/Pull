// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package fixture.core;

/**
 * Pure-Java dependency packaged beside a fixture provider.
 */
public final class ChildDependency
{
    private ChildDependency ()
    {
        // Utility class
    }


    /**
     * Get the fixture build identifier.
     *
     * @return The build identifier
     */
    public static String buildId ()
    {
        return "fixture-child";
    }
}
