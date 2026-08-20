// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2017-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.controller.ableton.push.mode.track;

import de.mossgrabers.controller.ableton.push.controller.PushControlSurface;
import de.mossgrabers.controller.ableton.push.mode.BaseMode;
import de.mossgrabers.framework.controller.ButtonID;
import de.mossgrabers.framework.controller.color.ColorEx;
import de.mossgrabers.framework.controller.display.IGraphicDisplay;
import de.mossgrabers.framework.daw.IModel;
import de.mossgrabers.framework.daw.IProject;
import de.mossgrabers.framework.daw.data.IMasterTrack;
import de.mossgrabers.framework.daw.data.ITrack;
import de.mossgrabers.framework.daw.data.empty.EmptyParameter;
import de.mossgrabers.framework.graphics.canvas.component.IComponent;
import de.mossgrabers.framework.parameterprovider.special.FixedParameterProvider;
import de.mossgrabers.framework.utils.ButtonEvent;
import de.mossgrabers.pull.core.api.PushControlIds;
import de.mossgrabers.pull.core.api.output.RgbColor;
import de.mossgrabers.pull.shell.runtime.ReloadableControllerRuntime;


/**
 * Mode for editing the parameters of the master track.
 *
 * @author Jürgen Moßgraber
 */
public class MasterMode extends BaseMode<ITrack>
{
    private static final IComponent BLANK_DISPLAY = info -> {
        final var bounds = info.getBounds ();
        info.getContext ().fillRectangle (bounds.left (), bounds.top (), bounds.width (), bounds.height (), ColorEx.BLACK);
    };
    private final IMasterTrack      masterTrack;
    private final IProject          project;
    private final ReloadableControllerRuntime reloadableRuntime;


    /**
     * Constructor.
     *
     * @param surface The control surface
     * @param model The model
     * @param isTemporary If true treat this mode only as temporary
     */
    public MasterMode (final PushControlSurface surface, final IModel model, final boolean isTemporary, final ReloadableControllerRuntime reloadableRuntime)
    {
        super ("Master", surface, model);

        this.masterTrack = this.model.getMasterTrack ();
        this.project = this.model.getProject ();
        this.reloadableRuntime = reloadableRuntime;
        this.setParameterProvider (new FixedParameterProvider (EmptyParameter.INSTANCE, EmptyParameter.INSTANCE, EmptyParameter.INSTANCE, EmptyParameter.INSTANCE, EmptyParameter.INSTANCE, EmptyParameter.INSTANCE, EmptyParameter.INSTANCE, EmptyParameter.INSTANCE));
    }


    /** {@inheritDoc} */
    @Override
    public void onActivate ()
    {
        super.onActivate ();

        this.setActive (true);
    }


    /** {@inheritDoc} */
    @Override
    public void onDeactivate ()
    {
        super.onDeactivate ();

        this.setActive (false);
    }


    /** {@inheritDoc} */
    @Override
    public void onKnobTouch (final int index, final boolean isTouched)
    {
        this.setTouchedKnob (index, isTouched);

        if (isTouched && this.surface.isDeletePressed ())
        {
            this.surface.setTriggerConsumed (ButtonID.DELETE);

            switch (index)
            {
                case 0:
                    this.masterTrack.resetVolume ();
                    break;
                case 1:
                    this.masterTrack.resetPan ();
                    break;
                case 2:
                    this.project.resetCueVolume ();
                    break;
                case 3:
                    this.project.resetCueMix ();
                    break;
                default:
                    // Not used
                    break;
            }
        }

        switch (index)
        {
            case 0:
                this.masterTrack.touchVolume (isTouched);
                break;
            case 1:
                this.masterTrack.touchPan (isTouched);
                break;
            case 2:
                this.project.touchCueVolume (isTouched);
                break;
            case 3:
                this.project.touchCueMix (isTouched);
                break;
            default:
                // Not used
                break;
        }

        this.checkStopAutomationOnKnobRelease (isTouched);
    }


    /** {@inheritDoc} */
    @Override
    public void updateDisplay2 (final IGraphicDisplay display)
    {
        // The generic display base plane projects the core scene. Missing core output stays blank.
        display.addElement (BLANK_DISPLAY);
    }


    /** {@inheritDoc} */
    @Override
    public void onFirstRow (final int index, final ButtonEvent event)
    {
        // All Master-row actions are reloadable-core-owned. Missing or faulted cores are inert.
    }


    /** {@inheritDoc} */
    @Override
    public int getButtonColor (final ButtonID buttonID)
    {
        if (this.isButtonRow (0, buttonID) >= 0 || this.isButtonRow (1, buttonID) >= 0)
            return this.closestPaletteColor (this.reloadableRuntime.lightColor (PushControlIds.button (buttonID.name ())));

        return super.getButtonColor (buttonID);
    }

    private int closestPaletteColor (final RgbColor color)
    {
        return this.model.getColorManager ().getColorIndex (ColorEx.fromRGB (color.red (), color.green (), color.blue ()));
    }

    private void setActive (final boolean enable)
    {
        this.masterTrack.setVolumeIndication (enable);
        this.masterTrack.setPanIndication (enable);
    }
}
