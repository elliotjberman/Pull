// (c) 2017-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.controller.ableton.push.parameterprovider;

import de.mossgrabers.framework.controller.valuechanger.IValueChanger;
import de.mossgrabers.framework.parameter.AbstractParameterWrapper;
import de.mossgrabers.framework.parameter.IParameter;


/**
 * Adds a small center detent to a pan parameter.
 */
public class PushPanParameter extends AbstractParameterWrapper
{
    private static final double CENTER       = 0.5;
    private static final double DETENT_WIDTH = 0.015;

    private final IValueChanger valueChanger;


    /**
     * Constructor.
     *
     * @param parameter The pan parameter
     * @param valueChanger The Push value changer
     */
    public PushPanParameter (final IParameter parameter, final IValueChanger valueChanger)
    {
        super (parameter);

        this.valueChanger = valueChanger;
    }


    /** {@inheritDoc} */
    @Override
    public void changeValue (final int control)
    {
        this.changeValue (this.valueChanger, control);
    }


    /** {@inheritDoc} */
    @Override
    public void changeValue (final IValueChanger changer, final int control)
    {
        final double increment = changer.calcKnobChange (control) * 0.5;
        if (increment == 0)
            return;

        final double currentValue = changer.toNormalizedValue (this.parameter.getValue ());
        final double centerTolerance = 1.0 / (changer.getUpperBound () - 1.0);
        if (Math.abs (currentValue - CENTER) <= centerTolerance)
        {
            this.parameter.inc (increment);
            return;
        }

        final double nextValue = Math.max (0, Math.min (1, currentValue + increment / (changer.getUpperBound () - 1.0)));
        final boolean movingTowardsCenter = increment > 0 && currentValue < CENTER || increment < 0 && currentValue > CENTER;
        final boolean crossesCenter = currentValue < CENTER && nextValue >= CENTER || currentValue > CENTER && nextValue <= CENTER;
        if (movingTowardsCenter && (crossesCenter || Math.abs (nextValue - CENTER) <= DETENT_WIDTH))
        {
            this.parameter.setNormalizedValue (CENTER);
            return;
        }

        this.parameter.inc (increment);
    }
}
