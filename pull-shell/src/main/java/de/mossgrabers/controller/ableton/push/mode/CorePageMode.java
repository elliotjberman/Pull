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
        // These physical IDs never change. Do not rebuild/normalize them on every result validation.
        return control != null && switch (control.value ())
        {
            case "push.continuous.knob1", "push.continuous.knob2", "push.continuous.knob3", "push.continuous.knob4",
                 "push.continuous.knob5", "push.continuous.knob6", "push.continuous.knob7", "push.continuous.knob8" -> kind == InputKind.RELATIVE || kind == InputKind.TOUCH;
            case "push.button.row1-1", "push.button.row1-2", "push.button.row1-3", "push.button.row1-4", "push.button.row1-5", "push.button.row1-6", "push.button.row1-7", "push.button.row1-8",
                 "push.button.row2-1", "push.button.row2-2", "push.button.row2-3", "push.button.row2-4", "push.button.row2-5", "push.button.row2-6", "push.button.row2-7", "push.button.row2-8" -> kind == InputKind.BUTTON;
            default -> kind == InputKind.BUTTON && containsNavigationInput (control);
        };
    }

    /** Four arrow controls routed below their permanent inert navigation adapter. */
    public static boolean containsNavigationInput (final ControlId control)
    {
        return control != null && ("push.button.arrow-left".equals (control.value ()) || "push.button.arrow-right".equals (control.value ()) ||
            "push.button.arrow-up".equals (control.value ()) || "push.button.arrow-down".equals (control.value ()));
    }

    /** Session navigation that is inert independently of the selected parameter page. */
    public static boolean containsSessionNavigationInput (final ControlId control, final boolean fullSession, final boolean sessionNavigation)
    {
        return sessionNavigation && containsNavigationInput (control) || fullSession && control != null &&
            ("push.button.arrow-up".equals (control.value ()) || "push.button.arrow-down".equals (control.value ()));
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
