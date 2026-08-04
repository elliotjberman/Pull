// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.shell.runtime;

/**
 * Parent-loaded, cause-free report of a failed invocation into a candidate core.
 */
public final class CoreInvocationException extends RuntimeException
{
    private static final long serialVersionUID = 1L;


    CoreInvocationException (final String message)
    {
        super (message);
    }
}
