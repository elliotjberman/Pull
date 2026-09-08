// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2017-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.controller.ableton.push.mode.device;

import de.mossgrabers.controller.ableton.push.controller.PushColorManager;
import de.mossgrabers.controller.ableton.push.controller.PushControlSurface;
import de.mossgrabers.controller.ableton.push.mode.BaseMode;
import de.mossgrabers.framework.controller.ButtonID;
import de.mossgrabers.framework.daw.IBrowser;
import de.mossgrabers.framework.daw.IModel;
import de.mossgrabers.framework.daw.data.IBrowserColumn;
import de.mossgrabers.framework.daw.data.IItem;
import de.mossgrabers.framework.featuregroup.AbstractFeatureGroup;
import de.mossgrabers.framework.featuregroup.AbstractMode;
import de.mossgrabers.framework.utils.ButtonEvent;

import java.util.Optional;


/**
 * Mode for navigating the browser.
 *
 * @author Jürgen Moßgraber
 */
public class DeviceBrowserMode extends BaseMode<IItem>
{
    private static final int SELECTION_OFF    = 0;
    private static final int SELECTION_PRESET = 1;
    private static final int SELECTION_FILTER = 2;

    private int              selectionMode;
    private int              filterColumn;


    /**
     * Constructor.
     *
     * @param surface The control surface
     * @param model The model
     */
    public DeviceBrowserMode (final PushControlSurface surface, final IModel model)
    {
        super ("Browser", surface, model);

        this.selectionMode = SELECTION_OFF;
        this.filterColumn = -1;
    }


    /** {@inheritDoc} */
    @Override
    public void onDeactivate ()
    {
        super.onDeactivate ();

        this.model.getBrowser ().stopBrowsing (true);
        this.selectionMode = SELECTION_OFF;
        this.filterColumn = -1;
    }


    /**
     * Change the value of the last selected column.
     *
     * @param value The change value
     */
    public void changeSelectedColumnValue (final int value)
    {
        final int index = this.filterColumn == -1 ? 7 : this.filterColumn;
        this.changeValue (index, value);
    }


    /**
     * Set the last selected column to the selection column.
     */
    public void resetFilterColumn ()
    {
        this.filterColumn = -1;
    }


    /** {@inheritDoc} */
    @Override
    public void onKnobValue (final int index, final int value)
    {
        if (!this.isKnobTouched (index))
            return;

        if (this.increaseKnobMovement ())
            this.changeValue (index, value);
    }


    /** {@inheritDoc} */
    @Override
    public void onKnobTouch (final int index, final boolean isTouched)
    {
        // Make sure that only 1 knob gets changed in browse mode to prevent weird behavior
        if (this.isAnyKnobTouched () && !this.isKnobTouched (index))
            return;
        this.setTouchedKnob (index, isTouched);

        Optional<IBrowserColumn> fc;
        if (isTouched)
        {
            if (this.surface.isDeletePressed ())
            {
                this.surface.setTriggerConsumed (ButtonID.DELETE);
                fc = this.getFilterColumn (index);
                if (fc.isPresent () && fc.get ().doesExist ())
                    this.model.getBrowser ().resetFilterColumn (fc.get ().getIndex ());
                return;
            }
        }
        else
        {
            this.selectionMode = DeviceBrowserMode.SELECTION_OFF;
            return;
        }

        if (index == 7)
        {
            this.selectionMode = DeviceBrowserMode.SELECTION_PRESET;
            this.filterColumn = -1;
        }
        else
        {
            fc = this.getFilterColumn (index);
            if (fc.isPresent () && fc.get ().doesExist ())
            {
                this.selectionMode = DeviceBrowserMode.SELECTION_FILTER;
                this.filterColumn = fc.get ().getIndex ();
            }
        }
    }


    /** {@inheritDoc} */
    @Override
    public void onFirstRow (final int index, final ButtonEvent event)
    {
        if (event != ButtonEvent.DOWN)
            return;

        if (this.surface.isShiftPressed () && index == 7)
        {
            this.model.getBrowser ().togglePreviewEnabled ();
            return;
        }

        this.selectNext (index, 1);
    }


    /** {@inheritDoc} */
    @Override
    public void onSecondRow (final int index, final ButtonEvent event)
    {
        if (event != ButtonEvent.DOWN)
            return;
        this.selectPrevious (index, 1);
    }


    /** {@inheritDoc} */
    @Override
    public String getButtonColorID (final ButtonID buttonID)
    {
        int index = this.isButtonRow (0, buttonID);
        if (index >= 0)
        {
            if (index == 7)
            {
                if (this.surface.isShiftPressed ())
                    return this.model.getBrowser ().isPreviewEnabled () ? PushColorManager.PUSH_ORANGE_HI : PushColorManager.PUSH_ORANGE_LO;
                return AbstractFeatureGroup.BUTTON_COLOR_ON;
            }
            final Optional<IBrowserColumn> col = this.getFilterColumn (index);
            return col.isPresent () && col.get ().doesExist () ? AbstractFeatureGroup.BUTTON_COLOR_ON : AbstractFeatureGroup.BUTTON_COLOR_OFF;
        }

        index = this.isButtonRow (1, buttonID);
        if (index >= 0)
        {
            if (index == 7)
                return AbstractMode.BUTTON_COLOR2_ON;
            final Optional<IBrowserColumn> col = this.getFilterColumn (index);
            return col.isPresent () && col.get ().doesExist () ? AbstractMode.BUTTON_COLOR2_ON : AbstractFeatureGroup.BUTTON_COLOR_OFF;
        }

        return AbstractFeatureGroup.BUTTON_COLOR_OFF;
    }


    /** {@inheritDoc} */
    @Override
    public void selectPreviousItemPage ()
    {
        this.resetFilterColumn ();
        this.model.getBrowser ().previousContentType ();
    }


    /** {@inheritDoc} */
    @Override
    public void selectNextItemPage ()
    {
        this.resetFilterColumn ();
        this.model.getBrowser ().nextContentType ();
    }


    /** {@inheritDoc} */
    @Override
    public boolean hasPreviousItem ()
    {
        return this.model.getBrowser ().hasPreviousContentType ();
    }


    /** {@inheritDoc} */
    @Override
    public boolean hasNextItem ()
    {
        return this.model.getBrowser ().hasNextContentType ();
    }


    /** {@inheritDoc} */
    @Override
    public boolean hasPreviousItemPage ()
    {
        return this.hasPreviousItem ();
    }


    /** {@inheritDoc} */
    @Override
    public boolean hasNextItemPage ()
    {
        return this.hasNextItem ();
    }


    private Optional<IBrowserColumn> getFilterColumn (final int index)
    {
        final IBrowser browser = this.model.getBrowser ();
        int column = -1;
        for (int i = 0; i < browser.getFilterColumnCount (); i++)
        {
            column++;
            if (column == index)
                return Optional.of (browser.getFilterColumn (i));
        }
        return Optional.empty ();
    }


    private void selectNext (final int index, final int count)
    {
        final IBrowser browser = this.model.getBrowser ();
        if (index < 7)
        {
            final Optional<IBrowserColumn> fc = this.getFilterColumn (index);
            if (fc.isPresent () && fc.get ().doesExist ())
            {
                final int fi = fc.get ().getIndex ();
                if (fi < 0)
                    return;
                this.filterColumn = fi;
                for (int i = 0; i < count; i++)
                    browser.selectNextFilterItem (this.filterColumn);
            }
        }
        else
        {
            for (int i = 0; i < count; i++)
                browser.selectNextResult ();
        }
    }


    private void selectPrevious (final int index, final int count)
    {
        final IBrowser browser = this.model.getBrowser ();
        if (index < 7)
        {
            final Optional<IBrowserColumn> fc = this.getFilterColumn (index);
            if (fc.isPresent () && fc.get ().doesExist ())
            {
                final int fi = fc.get ().getIndex ();
                if (fi < 0)
                    return;
                this.filterColumn = fi;
                for (int j = 0; j < count; j++)
                    browser.selectPreviousFilterItem (this.filterColumn);
            }
        }
        else
        {
            for (int j = 0; j < count; j++)
                browser.selectPreviousResult ();
        }
    }


    private void changeValue (final int index, final int value)
    {
        int speed = this.model.getValueChanger ().calcSteppedKnobChange (value);
        final boolean direction = speed > 0;
        if (this.surface.isShiftPressed ())
            speed = speed * 4;

        speed = Math.abs (speed);
        if (direction)
            this.selectNext (index, speed);
        else
            this.selectPrevious (index, speed);
    }


    /** Existing local browse selection, exposed only for subscribed read-back. */
    public int getObservedSelectionMode () { return this.selectionMode; }
    public int getObservedFilterColumn () { return this.filterColumn; }

}
