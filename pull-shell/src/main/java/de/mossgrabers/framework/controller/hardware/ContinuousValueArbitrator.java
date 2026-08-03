// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2017-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.framework.controller.hardware;


/**
 * Arbitrates one decoded physical value before it reaches the currently bound command or
 * parameter. The supplied legacy mutation contains the complete established behavior and may be
 * invoked exactly once to observe the value or omitted to claim it exclusively.
 *
 * <p>Relative controls supply the integer payload expected by their legacy continuous command.
 * Absolute controls supply a normalized 14-bit value in the range {@code 0..16383}.</p>
 */
@FunctionalInterface
public interface ContinuousValueArbitrator
{
    /**
     * Arbitrate one physical value.
     *
     * @param value Decoded control value
     * @param legacyMutation Complete established command or parameter mutation
     */
    void arbitrate (int value, Runnable legacyMutation);
}
