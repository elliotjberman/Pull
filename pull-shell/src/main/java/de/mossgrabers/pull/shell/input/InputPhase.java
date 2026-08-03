// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.shell.input;

/**
 * Phase of a physical controller input.
 */
public enum InputPhase
{
    /** The first edge of a gesture. */
    BEGIN,
    /** A continuous-value change. */
    CHANGE,
    /** A long-press callback within an edge gesture. */
    LONG,
    /** The final edge of a gesture. */
    END
}
