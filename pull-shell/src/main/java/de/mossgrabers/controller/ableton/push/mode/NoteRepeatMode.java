// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2017-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.controller.ableton.push.mode;

import de.mossgrabers.controller.ableton.push.PushConfiguration;
import de.mossgrabers.controller.ableton.push.controller.PushControlSurface;
import de.mossgrabers.framework.configuration.Configuration;
import de.mossgrabers.framework.controller.ButtonID;
import de.mossgrabers.framework.controller.color.ColorManager;
import de.mossgrabers.framework.controller.display.IGraphicDisplay;
import de.mossgrabers.framework.controller.valuechanger.IValueChanger;
import de.mossgrabers.framework.daw.GrooveParameterID;
import de.mossgrabers.framework.daw.IGroove;
import de.mossgrabers.framework.daw.IModel;
import de.mossgrabers.framework.daw.constants.Resolution;
import de.mossgrabers.framework.daw.data.IItem;
import de.mossgrabers.framework.daw.midi.ArpeggiatorMode;
import de.mossgrabers.framework.daw.midi.INoteInput;
import de.mossgrabers.framework.daw.midi.INoteRepeat;
import de.mossgrabers.framework.featuregroup.AbstractFeatureGroup;
import de.mossgrabers.framework.featuregroup.AbstractMode;
import de.mossgrabers.framework.parameter.IParameter;
import de.mossgrabers.framework.utils.ButtonEvent;
import de.mossgrabers.framework.utils.StringUtils;

import java.util.List;


/**
 * Editing the length of note repeat notes.
 *
 * @author Jürgen Moßgraber
 */
public class NoteRepeatMode extends BaseMode<IItem>
{
    private final INoteRepeat noteRepeat;


    /**
     * Constructor.
     *
     * @param surface The control surface
     * @param model The model
     */
    public NoteRepeatMode (final PushControlSurface surface, final IModel model)
    {
        super ("Note Repeat", surface, model);

        final INoteInput defaultNoteInput = surface.getMidiInput ().getDefaultNoteInput ();
        this.noteRepeat = defaultNoteInput == null ? null : defaultNoteInput.getNoteRepeat ();
    }


    /** {@inheritDoc} */
    @Override
    public void onActivate ()
    {
        super.onActivate ();

        this.model.getGroove ().enableObservers (true);
    }


    /** {@inheritDoc} */
    @Override
    public void onDeactivate ()
    {
        super.onDeactivate ();

        this.model.getGroove ().enableObservers (false);
    }


    /** {@inheritDoc} */
    @Override
    public void onKnobValue (final int index, final int value)
    {
        if (index != 7 && !this.increaseKnobMovement ())
            return;

        final PushConfiguration configuration = this.surface.getConfiguration ();
        final IValueChanger valueChanger = this.model.getValueChanger ();
        switch (index)
        {
            case 0, 1:
                final int sel = Resolution.change (Resolution.getMatch (configuration.getNoteRepeatPeriod ().getValue ()), valueChanger.isIncrease (value));
                configuration.setNoteRepeatPeriod (Resolution.values ()[sel]);
                break;

            case 2, 3:
                final int sel2 = Resolution.change (Resolution.getMatch (configuration.getNoteRepeatLength ().getValue ()), valueChanger.calcKnobChange (value) > 0);
                configuration.setNoteRepeatLength (Resolution.values ()[sel2]);
                break;

            case 5:
                configuration.setPrevNextNoteRepeatMode (valueChanger.isIncrease (value));
                break;

            case 6:
                configuration.setNoteRepeatOctave (configuration.getNoteRepeatOctave () + (valueChanger.calcKnobChange (value) > 0 ? 1 : -1));
                break;

            case 7:
                this.model.getGroove ().getParameter (GrooveParameterID.SHUFFLE_AMOUNT).changeValue (value);
                break;

            default:
                // Not used
                break;
        }
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
                case 5:
                    this.noteRepeat.setMode (ArpeggiatorMode.UP);
                    break;

                case 6:
                    this.surface.getConfiguration ().setNoteRepeatOctave (1);
                    break;

                case 7:
                    this.model.getGroove ().getParameter (GrooveParameterID.SHUFFLE_AMOUNT).resetValue ();
                    break;

                default:
                    // Unused
                    break;
            }
        }
    }


    /** {@inheritDoc} */
    @Override
    public void onFirstRow (final int index, final ButtonEvent event)
    {
        if (event != ButtonEvent.UP || this.noteRepeat == null)
            return;

        final PushConfiguration configuration = this.surface.getConfiguration ();

        switch (index)
        {
            case 0, 1:
                final int sel = Resolution.change (Resolution.getMatch (this.noteRepeat.getPeriod ()), index == 1);
                configuration.setNoteRepeatPeriod (Resolution.values ()[sel]);
                break;

            case 2, 3:
                final int sel2 = Resolution.change (Resolution.getMatch (this.noteRepeat.getNoteLength ()), index == 3);
                configuration.setNoteRepeatLength (Resolution.values ()[sel2]);
                break;

            case 4:
                this.noteRepeat.toggleLatchActive ();
                break;

            case 5:
                this.noteRepeat.toggleUsePressure ();
                break;

            case 6:
                this.noteRepeat.toggleIsFreeRunning ();
                break;

            case 7:
                this.noteRepeat.toggleShuffle ();
                break;

            default:
                // Unused
                break;
        }
    }


    /** {@inheritDoc} */
    @Override
    public void onSecondRow (final int index, final ButtonEvent event)
    {
        if (event != ButtonEvent.UP || this.noteRepeat == null)
            return;

        if (index == 7)
        {
            final IParameter grooveEnabled = this.model.getGroove ().getParameter (GrooveParameterID.ENABLED);
            grooveEnabled.setValue (grooveEnabled.getValue () == 0 ? this.model.getValueChanger ().getUpperBound () : 0);
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
            final int onColor = colorManager.getColorIndex (AbstractFeatureGroup.BUTTON_COLOR_ON);
            final int hiColor = colorManager.getColorIndex (AbstractMode.BUTTON_COLOR_HI);

            switch (index)
            {
                default:
                case 0, 1:
                    return onColor;
                case 2, 3:
                    return onColor;

                case 4:
                    return this.noteRepeat.isLatchActive () ? hiColor : onColor;

                case 5:
                    return this.noteRepeat.usePressure () ? hiColor : onColor;

                case 6:
                    return !this.noteRepeat.isFreeRunning () ? hiColor : onColor;

                case 7:
                    return this.noteRepeat.isShuffle () ? hiColor : onColor;
            }
        }

        index = this.isButtonRow (1, buttonID);
        if (index >= 0)
        {
            final ColorManager colorManager = this.model.getColorManager ();
            if (index < 7)
                return colorManager.getColorIndex (AbstractFeatureGroup.BUTTON_COLOR_OFF);
            return this.model.getGroove ().getParameter (GrooveParameterID.ENABLED).getValue () > 0 ? colorManager.getColorIndex (AbstractMode.BUTTON_COLOR_HI) : colorManager.getColorIndex (AbstractFeatureGroup.BUTTON_COLOR_ON);
        }

        return super.getButtonColor (buttonID);
    }


    /** {@inheritDoc} */
    @Override
    public void updateDisplay2 (final IGraphicDisplay display)
    {
        if (this.noteRepeat == null)
            return;

        display.addOptionElement ("Period", "", false, "", "", false, false);
        final int selPeriodIndex = this.getSelectedPeriodIndex ();
        display.addListElement (6, Resolution.getNames (), selPeriodIndex);

        display.addOptionElement ("  Length", "", false, "", "", false, false);
        final int selLengthIndex = this.getSelectedNoteLengthIndex ();
        display.addListElement (6, Resolution.getNames (), selLengthIndex);

        display.addOptionElement ("", "", false, "", "Latch", this.noteRepeat.isLatchActive (), false);

        final int upperBound = this.model.getValueChanger ().getUpperBound ();
        final boolean usePressure = this.noteRepeat.usePressure ();
        final ArpeggiatorMode mode = this.noteRepeat.getMode ();
        final Configuration configuration = this.surface.getConfiguration ();
        final List<ArpeggiatorMode> arpeggiatorModes = configuration.getArpeggiatorModes ();
        final int modeIndex = configuration.lookupArpeggiatorModeIndex (mode);
        final int modeValue = modeIndex * upperBound / (arpeggiatorModes.size () - 1);
        display.addParameterElementWithPlainMenu ("", false, "Use Pressure", null, usePressure, "Mode", modeValue, StringUtils.optimizeName (mode.getName (), 8), this.isKnobTouched (5), -1);

        final boolean isSynced = !this.noteRepeat.isFreeRunning ();
        final int octaves = this.noteRepeat.getOctaves ();
        final int octaveValue = octaves * upperBound / 8;
        display.addParameterElementWithPlainMenu ("", false, "Sync", null, isSynced, "Octaves", octaveValue, Integer.toString (octaves), this.isKnobTouched (6), -1);

        final IGroove groove = this.model.getGroove ();
        final IParameter shuffleParam = groove.getParameter (GrooveParameterID.SHUFFLE_AMOUNT);
        final IParameter enabledParam = groove.getParameter (GrooveParameterID.ENABLED);
        final int grooveValue = enabledParam.getValue ();
        display.addParameterElementWithPlainMenu ("Groove " + enabledParam.getDisplayedValue (8), grooveValue != 0, "Shuffle", null, this.noteRepeat.isShuffle (), shuffleParam.getName (10), shuffleParam.getValue (), shuffleParam.getDisplayedValue (8), this.isKnobTouched (7), -1);
    }


    /**
     * Get the index of the selected period.
     *
     * @return The selected period index
     */
    private int getSelectedPeriodIndex ()
    {
        return this.noteRepeat == null ? -1 : Resolution.getMatch (this.noteRepeat.getPeriod ());
    }


    /**
     * Get the index of the selected length.
     *
     * @return The selected length index
     */
    private int getSelectedNoteLengthIndex ()
    {
        return this.noteRepeat == null ? -1 : Resolution.getMatch (this.noteRepeat.getNoteLength ());
    }
}
