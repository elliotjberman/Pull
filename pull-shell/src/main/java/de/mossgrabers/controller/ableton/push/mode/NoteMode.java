// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2017-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.controller.ableton.push.mode;

import java.util.List;

import de.mossgrabers.controller.ableton.push.controller.PushColorManager;
import de.mossgrabers.controller.ableton.push.controller.PushControlSurface;
import de.mossgrabers.framework.controller.ButtonID;
import de.mossgrabers.framework.daw.IModel;
import de.mossgrabers.framework.daw.clip.INoteClip;
import de.mossgrabers.framework.daw.clip.IStepInfo;
import de.mossgrabers.framework.daw.clip.NotePosition;
import de.mossgrabers.framework.daw.data.IItem;
import de.mossgrabers.framework.mode.INoteEditor;
import de.mossgrabers.framework.mode.INoteEditorMode;
import de.mossgrabers.framework.mode.NoteEditor;
import de.mossgrabers.framework.utils.ButtonEvent;


/**
 * Editing of note parameters.
 *
 * @author Jürgen Moßgraber
 */
public class NoteMode extends BaseMode<IItem> implements INoteEditorMode
{
    private static final String [] RECURRENCE_PRESETS =
    {
        "First",
        "Not first",
        " ",
        "Last",
        "Not last",
        " ",
        "Odd",
        "Even",
    };


    private enum Page
    {
        NOTE,
        RECCURRENCE_PATTERN,
        EXPRESSIONS,
        REPEAT
    }


    private Page                                page               = Page.NOTE;
    private final NoteEditor                    noteEditor         = new NoteEditor ();


    /**
     * Constructor.
     *
     * @param surface The control surface
     * @param model The model
     */
    public NoteMode (final PushControlSurface surface, final IModel model)
    {
        super ("Note", surface, model);

        this.setParameterProvider (new de.mossgrabers.framework.parameterprovider.special.EmptyParameterProvider (8));
    }


    /** {@inheritDoc} */
    @Override
    public void onFirstRow (final int index, final ButtonEvent event)
    {
        if (event != ButtonEvent.UP)
            return;

        final INoteClip clip = this.noteEditor.getClip ();
        final List<NotePosition> notes = this.noteEditor.getNotes ();
        for (final NotePosition notePosition: notes)
        {
            final IStepInfo stepInfo = clip.getStep (notePosition);

            switch (this.page)
            {
                case NOTE:
                    switch (index)
                    {
                        case 5:
                            clip.updateStepIsChanceEnabled (notePosition, !stepInfo.isChanceEnabled ());
                            break;

                        case 6:
                            clip.updateStepIsOccurrenceEnabled (notePosition, !stepInfo.isOccurrenceEnabled ());
                            break;

                        case 7:
                            clip.updateStepIsRecurrenceEnabled (notePosition, !stepInfo.isRecurrenceEnabled ());
                            break;

                        default:
                            return;
                    }
                    break;

                case EXPRESSIONS:
                    break;

                case REPEAT:
                    if (index == 3)
                        clip.updateStepIsRepeatEnabled (notePosition, !stepInfo.isRepeatEnabled ());
                    break;

                case RECCURRENCE_PATTERN:
                    if (this.surface.isShiftPressed ())
                    {
                        switch (index)
                        {
                            // First
                            case 0:
                                clip.updateStepRecurrenceMask (notePosition, 1);
                                break;
                            // Not first
                            case 1:
                                clip.updateStepRecurrenceMask (notePosition, 254);
                                break;
                            // Last
                            case 3:
                                final int lastRecurrence = 1 << stepInfo.getRecurrenceLength () - 1;
                                clip.updateStepRecurrenceMask (notePosition, lastRecurrence);
                                break;
                            // Not last
                            case 4:
                                final int notLastRecurrence = (1 << stepInfo.getRecurrenceLength () - 1) - 1;
                                clip.updateStepRecurrenceMask (notePosition, notLastRecurrence);
                                break;
                            // Even
                            case 6:
                                clip.updateStepRecurrenceMask (notePosition, 85);
                                break;
                            // Odd
                            case 7:
                                clip.updateStepRecurrenceMask (notePosition, 170);
                                break;
                            // Not used
                            default:
                                break;
                        }
                    }
                    else
                        clip.updateStepRecurrenceMaskToggleBit (notePosition, index);
                    break;
            }
        }
    }


    /** {@inheritDoc} */
    @Override
    public void onSecondRow (final int index, final ButtonEvent event)
    {
        if (event != ButtonEvent.UP)
            return;

        switch (index)
        {
            case 0:
                this.page = Page.NOTE;
                break;

            case 1:
                this.page = Page.EXPRESSIONS;
                break;

            case 2:
                this.page = Page.REPEAT;
                break;

            case 7:
                this.page = Page.RECCURRENCE_PATTERN;
                break;

            default:
                // Not used:
                break;
        }

    }


    /** {@inheritDoc} */
    @Override
    public void onKnobTouch (final int index, final boolean isTouched)
    {

    }


    /** The legacy editor's current tab; presentation is owned by core. */
    public String getObservedPage () { return this.page.name (); }


    /** {@inheritDoc} */
    @Override
    public int getButtonColor (final ButtonID buttonID)
    {
        final List<NotePosition> notes = this.noteEditor.getNotes ();
        if (notes.isEmpty ())
            return this.colorManager.getColorIndex (PushColorManager.PUSH_BLACK);

        final INoteClip clip = this.noteEditor.getClip ();
        for (final NotePosition notePosition: notes)
        {
            final IStepInfo stepInfo = clip.getStep (notePosition);

            int index = this.isButtonRow (0, buttonID);
            if (index >= 0)
            {
                switch (this.page)
                {
                    case NOTE:
                        if (index == 5)
                            return this.colorManager.getColorIndex (stepInfo.isChanceEnabled () ? PushColorManager.PUSH_ORANGE_HI : PushColorManager.PUSH_ORANGE_LO);
                        if (index == 6)
                            return this.colorManager.getColorIndex (stepInfo.isOccurrenceEnabled () ? PushColorManager.PUSH_ORANGE_HI : PushColorManager.PUSH_ORANGE_LO);
                        if (index == 7)
                            return this.colorManager.getColorIndex (stepInfo.isRecurrenceEnabled () ? PushColorManager.PUSH_ORANGE_HI : PushColorManager.PUSH_ORANGE_LO);
                        break;

                    case EXPRESSIONS:
                        break;

                    case REPEAT:
                        if (index == 3)
                            return this.colorManager.getColorIndex (stepInfo.isRepeatEnabled () ? PushColorManager.PUSH_ORANGE_HI : PushColorManager.PUSH_ORANGE_LO);
                        break;

                    case RECCURRENCE_PATTERN:
                        if (this.surface.isShiftPressed ())
                            return this.colorManager.getColorIndex (RECURRENCE_PRESETS[index].isBlank () ? PushColorManager.PUSH_BLACK : PushColorManager.PUSH_GREEN_LO);

                        final int recurrenceLength = stepInfo.getRecurrenceLength ();
                        final int mask = stepInfo.getRecurrenceMask ();
                        final boolean isOn = (mask & 1 << index) > 0;
                        String color = PushColorManager.PUSH_BLACK;
                        if (index < recurrenceLength)
                            color = isOn ? PushColorManager.PUSH_ORANGE_HI : PushColorManager.PUSH_ORANGE_LO;
                        return this.colorManager.getColorIndex (color);
                }

                return this.colorManager.getColorIndex (PushColorManager.PUSH_BLACK);
            }

            index = this.isButtonRow (1, buttonID);
            if (index >= 0)
            {
                switch (this.page)
                {
                    case NOTE:
                        if (index == 0)
                            return this.colorManager.getColorIndex (PushColorManager.PUSH_GREEN_2);
                        break;

                    case EXPRESSIONS:
                        if (index == 1)
                            return this.colorManager.getColorIndex (PushColorManager.PUSH_GREEN_2);
                        break;

                    case REPEAT:
                        if (index == 2)
                            return this.colorManager.getColorIndex (PushColorManager.PUSH_GREEN_2);
                        break;

                    case RECCURRENCE_PATTERN:
                        if (index == 7)
                            return this.colorManager.getColorIndex (PushColorManager.PUSH_GREEN_2);
                        break;
                }

                if (index == 0 || index == 1)
                    return this.colorManager.getColorIndex (PushColorManager.PUSH_GREY_LO_2);
                if (index == 2 || index == 7)
                    return this.colorManager.getColorIndex (PushColorManager.PUSH_GREY_LO_2);

                return this.colorManager.getColorIndex (PushColorManager.PUSH_BLACK_2);
            }
        }

        return this.colorManager.getColorIndex (PushColorManager.PUSH_BLACK);
    }


    /** {@inheritDoc} */
    @Override
    public INoteEditor getNoteEditor ()
    {
        return this.noteEditor;
    }



}
