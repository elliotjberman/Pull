// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api.output;


/** Tempo-clocked hardware blink rates available to reloadable light policy. */
public enum LightBlinkRate
{
    /** Steady light with no alternate color. */
    NONE,
    /** Slow tempo-clocked alternation, used by playing Session clips. */
    SLOW,
    /** Fast tempo-clocked alternation, used by queued Session actions. */
    FAST
}
