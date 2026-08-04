// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2017-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.controller.ableton.push.mode.track;

import java.util.List;

import de.mossgrabers.controller.ableton.push.PushConfiguration;
import de.mossgrabers.controller.ableton.push.controller.PushColorManager;
import de.mossgrabers.controller.ableton.push.controller.PushControlSurface;
import de.mossgrabers.controller.ableton.push.mode.BaseMode;
import de.mossgrabers.framework.controller.ButtonID;
import de.mossgrabers.framework.controller.color.ColorEx;
import de.mossgrabers.framework.controller.color.ColorManager;
import de.mossgrabers.framework.controller.display.IGraphicDisplay;
import de.mossgrabers.framework.controller.valuechanger.IValueChanger;
import de.mossgrabers.framework.daw.IModel;
import de.mossgrabers.framework.daw.IProject;
import de.mossgrabers.framework.daw.data.ICursorTrack;
import de.mossgrabers.framework.daw.data.IMasterTrack;
import de.mossgrabers.framework.daw.data.ITrack;
import de.mossgrabers.framework.daw.data.empty.EmptyParameter;
import de.mossgrabers.framework.daw.resource.ChannelType;
import de.mossgrabers.framework.featuregroup.AbstractFeatureGroup;
import de.mossgrabers.framework.graphics.canvas.component.TrackMixerComponent;
import de.mossgrabers.framework.graphics.canvas.component.TrackMixerComponent.MenuData;
import de.mossgrabers.framework.graphics.canvas.component.TrackMixerComponent.ParameterData;
import de.mossgrabers.framework.graphics.canvas.component.TrackMixerComponent.TrackData;
import de.mossgrabers.framework.parameterprovider.special.FixedParameterProvider;
import de.mossgrabers.framework.utils.ButtonEvent;


/**
 * Mode for editing the parameters of the master track.
 *
 * @author Jürgen Moßgraber
 */
public class MasterMode extends BaseMode<ITrack>
{
    private static final String     TAG_VOLUME = "Volume";
    private final IMasterTrack      masterTrack;
    private final IProject          project;
    private final PushConfiguration configuration;


    /**
     * Constructor.
     *
     * @param surface The control surface
     * @param model The model
     * @param isTemporary If true treat this mode only as temporary
     */
    public MasterMode (final PushControlSurface surface, final IModel model, final boolean isTemporary)
    {
        super ("Master", surface, model);

        this.configuration = this.surface.getConfiguration ();
        this.masterTrack = this.model.getMasterTrack ();
        this.project = this.model.getProject ();
        this.setParameterProvider (new FixedParameterProvider (this.masterTrack.getVolumeParameter (), this.masterTrack.getPanParameter (), this.project.getCueVolumeParameter (), this.project.getCueMixParameter (), EmptyParameter.INSTANCE, EmptyParameter.INSTANCE, EmptyParameter.INSTANCE, EmptyParameter.INSTANCE));
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
        final IValueChanger valueChanger = this.model.getValueChanger ();
        final boolean enableVUMeters = this.configuration.isEnableVUMeters ();
        final int vuR = valueChanger.toDisplayValue (enableVUMeters ? this.masterTrack.getVuRight () : 0);
        final int vuL = valueChanger.toDisplayValue (enableVUMeters ? this.masterTrack.getVuLeft () : 0);
        final ICursorTrack cursorTrack = this.model.getCursorTrack ();
        final boolean isActive = this.masterTrack.isActivated ();

        final List<MenuData> menus = List.of (
            new MenuData (TAG_VOLUME, false),
            new MenuData ("Pan", false),
            new MenuData ("Cue Volume", false),
            new MenuData ("Cue Mix", false),
            new MenuData ("Audio Engine", this.model.getApplication ().isEngineActive ()),
            new MenuData ("", false),
            new MenuData ("Previous", false),
            new MenuData ("Next", false));

        final List<ParameterData> parameters = List.of (
            new ParameterData ("Master Volume", valueChanger.toDisplayValue (this.masterTrack.getVolume ()), valueChanger.toDisplayValue (this.masterTrack.getModulatedVolume ()), this.masterTrack.getVolumeStr (8), isActive),
            new ParameterData ("Pan", valueChanger.toDisplayValue (this.masterTrack.getPan ()), valueChanger.toDisplayValue (this.masterTrack.getModulatedPan ()), this.formatPanValue (this.masterTrack.getPan ()), isActive),
            new ParameterData ("Cue Level", valueChanger.toDisplayValue (this.project.getCueVolume ()), -1, this.project.getCueVolumeStr (8), true),
            new ParameterData ("Cue Mix", valueChanger.toDisplayValue (this.project.getCueMix ()), -1, this.project.getCueMixStr (8), true),
            new ParameterData ("", -1, -1, "", false),
            new ParameterData ("", -1, -1, "", false),
            new ParameterData ("", -1, -1, "", false),
            new ParameterData ("", -1, -1, "", false));

        final List<TrackData> tracks = List.of (
            new TrackData (this.masterTrack.getName (), ChannelType.MASTER, this.masterTrack.getColor (), this.masterTrack.isSelected (), isActive, this.masterTrack.isSelected () && cursorTrack.isPinned ()),
            new TrackData ("", null, this.masterTrack.getColor (), false, isActive, false),
            new TrackData ("Cue", ChannelType.CUE, ColorEx.GRAY, false, true, false),
            new TrackData ("", null, ColorEx.GRAY, false, true, false),
            new TrackData ("", null, ColorEx.WHITE, false, true, false),
            new TrackData ("", null, ColorEx.WHITE, false, true, false),
            new TrackData ("Load", null, ColorEx.WHITE, false, true, false),
            new TrackData ("Save", null, ColorEx.WHITE, false, true, false));

        display.addElement (new TrackMixerComponent (menus, parameters, tracks, vuL, vuR, this.masterTrack.getColor ()));
    }


    private String formatPanValue (final int value)
    {
        final double bipolarValue = 2.0 * this.model.getValueChanger ().toNormalizedValue (value) - 1.0;
        final int amount = (int) Math.round (100.0 * Math.abs (bipolarValue));
        if (amount == 0)
            return "C";
        return (bipolarValue < 0 ? "L " : "R ") + amount;
    }


    /** {@inheritDoc} */
    @Override
    public void onFirstRow (final int index, final ButtonEvent event)
    {
        if (event != ButtonEvent.UP)
            return;

        if (this.surface.isPressed (ButtonID.RECORD))
        {
            this.surface.setTriggerConsumed (ButtonID.RECORD);
            this.masterTrack.toggleRecArm ();
            return;
        }

        switch (index)
        {
            case 0:
                this.surface.getButton (ButtonID.DEVICE).trigger (ButtonEvent.DOWN);
                break;

            case 4:
                this.model.getApplication ().toggleEngineActive ();
                break;

            case 6:
                this.project.previous ();
                break;

            case 7:
                this.project.next ();
                break;

            default:
                // Not used
                break;
        }
    }


    /** {@inheritDoc} */
    @Override
    public int getButtonColor (final ButtonID buttonID)
    {
        int index = this.isButtonRow (0, buttonID);
        if (index >= 0)
        {
            final ColorManager colorManager = this.model.getColorManager ();

            if (index == 0)
                return this.getTrackButtonColor ();
            if (index < 4 || index == 5)
                return colorManager.getColorIndex (AbstractFeatureGroup.BUTTON_COLOR_OFF);
            if (index > 5)
                return colorManager.getColorIndex (AbstractFeatureGroup.BUTTON_COLOR_ON);

            final int red = PushColorManager.PUSH2_COLOR_RED_HI;
            return this.model.getApplication ().isEngineActive () ? colorManager.getColorIndex (AbstractFeatureGroup.BUTTON_COLOR_ON) : red;
        }

        index = this.isButtonRow (1, buttonID);
        if (index >= 0)
        {
            final int off = PushColorManager.PUSH2_COLOR_BLACK;

            switch (index)
            {
                case 0:
                    break;

                case 6:
                    return PushColorManager.PUSH2_COLOR_GREEN_HI;

                case 7:
                    if (this.project.isDirty ())
                        return PushColorManager.PUSH2_COLOR_ORANGE_HI;
                    return PushColorManager.PUSH2_COLOR_GREEN_LO;
            }

            return off;
        }

        return super.getButtonColor (buttonID);
    }


    /** {@inheritDoc} */
    @Override
    public void onSecondRow (final int index, final ButtonEvent event)
    {
        if (event != ButtonEvent.DOWN)
            return;

        switch (index)
        {
            case 0:
                break;

            case 6:
                this.project.load ();
                break;

            case 7:
                this.project.save ();
                break;

            default:
                // Not used
                break;
        }
    }


    private int getTrackButtonColor ()
    {
        if (!this.masterTrack.isActivated ())
            return PushColorManager.PUSH2_COLOR_BLACK;
        if (this.masterTrack.isRecArm ())
            return PushColorManager.PUSH2_COLOR_RED_HI;
        return PushColorManager.PUSH2_COLOR_ORANGE_HI;
    }


    private void setActive (final boolean enable)
    {
        this.masterTrack.setVolumeIndication (enable);
        this.masterTrack.setPanIndication (enable);
    }
}
