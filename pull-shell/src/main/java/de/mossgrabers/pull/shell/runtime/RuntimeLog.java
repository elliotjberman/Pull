// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.shell.runtime;

/**
 * Parent-owned logging sink that never retains child exceptions.
 */
interface RuntimeLog
{
    /**
     * Report normal runtime progress.
     *
     * @param message The message
     */
    void info (String message);


    /**
     * Report a recoverable runtime problem.
     *
     * @param message The sanitized message
     */
    void warn (String message);
}
