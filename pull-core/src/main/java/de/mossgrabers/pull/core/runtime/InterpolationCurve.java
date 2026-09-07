// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.runtime;

/** Pure easing over progress in [0, 1]; callers own timing and target lifetimes. */
enum InterpolationCurve implements de.mossgrabers.pull.core.runtime.curve.ReturnCurve
{
    LINEAR,
    EASE_OUT;

    @Override
    public double apply (final double progress)
    {
        return this == EASE_OUT ? 1 - Math.pow (1 - progress, 3) : progress;
    }

    static InterpolationCurve fromSetting (final String value)
    {
        return "Ease-out".equals (value) ? EASE_OUT : LINEAR;
    }

}
