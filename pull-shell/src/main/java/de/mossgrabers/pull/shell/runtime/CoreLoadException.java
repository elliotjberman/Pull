// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.shell.runtime;

/**
 * Reports that an isolated core JAR could not supply exactly one usable provider.
 */
public final class CoreLoadException extends Exception
{
    private static final long serialVersionUID = 1L;


    /**
     * Create a load failure with a safe parent-loaded message.
     *
     * @param message The failure description
     */
    public CoreLoadException (final String message)
    {
        super (message);
    }
}
