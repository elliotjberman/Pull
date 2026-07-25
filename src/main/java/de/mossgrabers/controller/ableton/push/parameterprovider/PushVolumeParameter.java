// (c) 2017-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.controller.ableton.push.parameterprovider;

import de.mossgrabers.framework.controller.valuechanger.IValueChanger;
import de.mossgrabers.framework.parameter.AbstractParameterWrapper;
import de.mossgrabers.framework.parameter.IParameter;


/**
 * Adds a gentle value-dependent response curve to a volume parameter.
 */
public class PushVolumeParameter extends AbstractParameterWrapper
{
    private static final double MAX_ACCELERATION = 0.2;

    private final IValueChanger valueChanger;


    /**
     * Constructor.
     *
     * @param parameter The volume parameter
     * @param valueChanger The Push value changer
     */
    public PushVolumeParameter (final IParameter parameter, final IValueChanger valueChanger)
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
        final double normalizedValue = changer.toNormalizedValue (this.parameter.getValue ());
        final double acceleration = 1.0 + MAX_ACCELERATION * normalizedValue;
        this.parameter.inc (changer.calcKnobChange (control) * acceleration);
    }
}
