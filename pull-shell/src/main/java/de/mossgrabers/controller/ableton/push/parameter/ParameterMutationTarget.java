// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.controller.ableton.push.parameter;


/**
 * Exact actuator and authoritative read-back for one parameter target.
 */
public interface ParameterMutationTarget
{
    /**
     * Get the target identity and generation.
     *
     * @return Target reference
     */
    ParameterTargetRef reference ();


    /**
     * Read the latest authoritative host value.
     *
     * @return Current value
     */
    double readAuthoritativeValue ();


    /**
     * Request restoration to a prior authoritative value.
     *
     * @param value Value to restore
     */
    void restore (double value);


    /**
     * Test whether the retained actuator still addresses the referenced target generation.
     *
     * @return True while safe to read or mutate
     */
    boolean isCurrent ();


    /**
     * Test whether host read-back acknowledges a requested value.
     *
     * @param expected Expected value
     * @return True when acknowledged
     */
    boolean isAt (double expected);
}
