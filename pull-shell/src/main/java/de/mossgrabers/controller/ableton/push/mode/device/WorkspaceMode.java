// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.controller.ableton.push.mode.device;

import de.mossgrabers.controller.ableton.push.controller.PushColorManager;
import de.mossgrabers.controller.ableton.push.controller.PushControlSurface;
import de.mossgrabers.controller.ableton.push.mode.BaseMode;
import de.mossgrabers.controller.ableton.push.workspace.WorkspaceFacetAdapter;
import de.mossgrabers.framework.controller.ButtonID;
import de.mossgrabers.framework.controller.display.IGraphicDisplay;
import de.mossgrabers.framework.controller.valuechanger.IValueChanger;
import de.mossgrabers.framework.daw.DAWColor;
import de.mossgrabers.framework.daw.IModel;
import de.mossgrabers.framework.daw.data.ITrack;
import de.mossgrabers.framework.daw.data.bank.ITrackBank;
import de.mossgrabers.framework.graphics.canvas.component.ParameterComponent;
import de.mossgrabers.framework.parameter.IParameter;
import de.mossgrabers.framework.parameterprovider.device.BankParameterProvider;
import de.mossgrabers.framework.parameterprovider.special.EmptyParameterProvider;
import de.mossgrabers.framework.utils.ButtonEvent;
import de.mossgrabers.pull.core.api.ControllerViewFacet;


/**
 * Stable display/encoder adapter for fixed workspace facets.
 */
public final class WorkspaceMode extends BaseMode<IParameter> implements WorkspaceFacetAdapter
{
    private final BankParameterProvider projectParameterProvider;
    private final EmptyParameterProvider emptyParameterProvider = new EmptyParameterProvider (8);


    /**
     * Constructor.
     *
     * @param surface The surface
     * @param model The model
     */
    public WorkspaceMode (final PushControlSurface surface, final IModel model)
    {
        super ("Workspace", surface, model, model.getProject ().getParameterBank ());
        this.projectParameterProvider = new BankParameterProvider (model.getProject ().getParameterBank ());
        this.setParameterProvider (this.projectParameterProvider);
    }


    /** {@inheritDoc} */
    @Override
    public void onActivate ()
    {
        this.reconcileWorkspaceFacets ();
        super.onActivate ();
    }


    /** {@inheritDoc} */
    @Override
    public void reconcileWorkspaceFacets ()
    {
        this.setParameterProvider (this.hasFacet (ControllerViewFacet.PROJECT_MACRO_CONTROLS) ? this.projectParameterProvider : this.emptyParameterProvider);
        this.bindControls ();
    }


    /** {@inheritDoc} */
    @Override
    public void onKnobTouch (final int index, final boolean isTouched)
    {
        if (!this.hasFacet (ControllerViewFacet.PROJECT_MACRO_CONTROLS))
            return;

        this.setTouchedKnob (index, isTouched);
        final IParameter parameter = this.bank.getItem (index);
        if (isTouched && this.surface.isDeletePressed ())
        {
            this.surface.setTriggerConsumed (ButtonID.DELETE);
            parameter.resetValue ();
        }
        parameter.touchValue (isTouched);
        this.checkStopAutomationOnKnobRelease (isTouched);
    }


    /** {@inheritDoc} */
    @Override
    public void onFirstRow (final int index, final ButtonEvent event)
    {
        if (this.hasFacet (ControllerViewFacet.TRACK_SELECTION_STRIP) && event == ButtonEvent.UP)
            this.model.getCurrentTrackBank ().getItem (index).select ();
    }


    /** {@inheritDoc} */
    @Override
    public int getButtonColor (final ButtonID buttonID)
    {
        int index = this.isButtonRow (0, buttonID);
        if (index >= 0 && this.hasFacet (ControllerViewFacet.TRACK_SELECTION_STRIP))
        {
            final ITrack track = this.model.getCurrentTrackBank ().getItem (index);
            if (!track.doesExist () || !track.isActivated ())
                return PushColorManager.PUSH2_COLOR_BLACK;
            if (track.isRecArm ())
                return PushColorManager.PUSH2_COLOR_RED_HI;
            return this.colorManager.getColorIndex (DAWColor.getColorID (track.getColor ()));
        }

        index = this.isButtonRow (1, buttonID);
        if (index == 0 && this.hasFacet (ControllerViewFacet.PROJECT_MACRO_CONTROLS))
            return PushColorManager.PUSH2_COLOR2_WHITE;
        return PushColorManager.PUSH2_COLOR_BLACK;
    }


    /** {@inheritDoc} */
    @Override
    public void updateDisplay2 (final IGraphicDisplay display)
    {
        final boolean showParameters = this.hasFacet (ControllerViewFacet.PROJECT_MACRO_CONTROLS);
        final boolean showTracks = this.hasFacet (ControllerViewFacet.TRACK_SELECTION_STRIP);
        final IValueChanger valueChanger = this.model.getValueChanger ();
        final ITrackBank trackBank = this.model.getCurrentTrackBank ();

        for (int index = 0; index < this.bank.getPageSize (); index++)
        {
            final IParameter parameter = this.bank.getItem (index);
            final boolean parameterExists = showParameters && parameter.doesExist ();
            final ITrack track = trackBank.getItem (index);
            final boolean trackExists = showTracks && track.doesExist ();
            display.addElement (new ParameterComponent (
                showParameters && index == 0 ? "Project" : "",
                showParameters && index == 0,
                trackExists ? track.getName (16) : "",
                track.getType (),
                track.getColor (),
                trackExists && track.isSelected (),
                parameterExists ? parameter.getName (16) : "",
                valueChanger.toDisplayValue (parameterExists ? parameter.getValue () : 0),
                valueChanger.toDisplayValue (parameterExists ? parameter.getModulatedValue () : -1),
                parameterExists ? parameter.getDisplayedValue (8) : "",
                parameterExists && this.isKnobTouched (index),
                ProjectMacroColors.at (index)));
        }
    }


    private boolean hasFacet (final ControllerViewFacet facet)
    {
        return this.surface.getControllerWorkspaceHost ().hasFacet (facet);
    }
}
