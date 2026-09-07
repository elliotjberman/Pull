// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.runtime.curve;

/** Pure return progress; zero is released value, one is baseline. Overshoot is permitted. */
@FunctionalInterface
public interface ReturnCurve
{
    double apply (double time);
}
