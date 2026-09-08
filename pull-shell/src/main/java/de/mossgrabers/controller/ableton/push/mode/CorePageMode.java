// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.controller.ableton.push.mode;

import de.mossgrabers.controller.ableton.push.controller.PushControlSurface;
import de.mossgrabers.framework.controller.ButtonID;
import de.mossgrabers.framework.controller.ContinuousID;
import de.mossgrabers.framework.controller.color.ColorEx;
import de.mossgrabers.framework.daw.IModel;
import de.mossgrabers.framework.daw.data.empty.EmptyParameter;
import de.mossgrabers.framework.parameter.IParameter;
import de.mossgrabers.framework.parameterprovider.IParameterProvider;
import de.mossgrabers.framework.parameterprovider.special.EmptyParameterProvider;
import de.mossgrabers.framework.utils.ButtonEvent;
import de.mossgrabers.pull.core.api.ControlId;
import de.mossgrabers.pull.core.api.PushControlIds;
import de.mossgrabers.pull.core.api.event.InputKind;
import de.mossgrabers.pull.core.api.output.RgbColor;
import de.mossgrabers.pull.shell.runtime.ReloadableControllerRuntime;

import java.util.Objects;

/**
 * Installed mechanical adapter for a completely migrated parameter page. Final inert callbacks
 * and empty physical bindings declare its reusable encoder/row/navigation/display footprint.
 */
public class CorePageMode extends BaseMode<IParameter>
{
    private static final IParameterProvider EMPTY_PARAMETERS = new EmptyParameterProvider (8);
    private final ReloadableControllerRuntime runtime;

    public CorePageMode (final String name, final PushControlSurface surface, final IModel model, final ReloadableControllerRuntime runtime)
    {
        super (name, surface, model);
        this.runtime = Objects.requireNonNull (runtime, "runtime");
        this.setParameterProvider (EMPTY_PARAMETERS);
    }

    /** Whether this input lies in the permanently inert page footprint. */
    public static boolean containsInput (final ControlId control, final InputKind kind)
    {
        for (int index = 1; index <= 8; index++)
        {
            if ((kind == InputKind.RELATIVE || kind == InputKind.TOUCH) && PushControlIds.continuous ("KNOB" + index).equals (control))
                return true;
            if (kind == InputKind.BUTTON && (PushControlIds.button ("ROW1_" + index).equals (control) || PushControlIds.button ("ROW2_" + index).equals (control)))
                return true;
        }
        return kind == InputKind.BUTTON && containsNavigationInput (control);
    }

    /** Four arrow controls routed below their permanent inert navigation adapter. */
    public static boolean containsNavigationInput (final ControlId control)
    {
        return PushControlIds.button ("ARROW_LEFT").equals (control) || PushControlIds.button ("ARROW_RIGHT").equals (control) ||
            PushControlIds.button ("ARROW_UP").equals (control) || PushControlIds.button ("ARROW_DOWN").equals (control);
    }

    /** Session navigation that is inert independently of the selected parameter page. */
    public static boolean containsSessionNavigationInput (final ControlId control, final boolean fullSession, final boolean sessionNavigation)
    {
        return sessionNavigation && containsNavigationInput (control) || fullSession &&
            (PushControlIds.button ("ARROW_UP").equals (control) || PushControlIds.button ("ARROW_DOWN").equals (control));
    }

    /** Whether this light belongs to a page soft-key row or navigation arrow. */
    public static boolean containsLight (final ControlId control)
    {
        return containsInput (control, InputKind.BUTTON);
    }

    @Override
    protected final void bindControls ()
    {
        if (!this.isActive)
            return;
        for (final ContinuousID control: DEFAULT_KNOB_IDS)
            this.surface.getContinuous (control).bind (EmptyParameter.INSTANCE);
    }

    @Override
    public final IParameterProvider getParameterProvider () { return EMPTY_PARAMETERS; }

    @Override
    public final void onKnobValue (final int index, final int value) { }

    @Override
    public final void onKnobTouch (final int index, final boolean touched) { }

    @Override
    public final void onFirstRow (final int index, final ButtonEvent event) { }

    @Override
    public final void onSecondRow (final int index, final ButtonEvent event) { }

    @Override
    public final int getButtonColor (final ButtonID button)
    {
        if (this.isButtonRow (0, button) >= 0 || this.isButtonRow (1, button) >= 0)
        {
            final RgbColor color = this.runtime.lightColor (PushControlIds.button (button.name ()));
            return this.model.getColorManager ().getColorIndex (ColorEx.fromRGB (color.red (), color.green (), color.blue ()));
        }
        return super.getButtonColor (button);
    }

}
