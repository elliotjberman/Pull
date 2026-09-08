// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2017-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.controller.ableton.push.mode.track;

import de.mossgrabers.controller.ableton.push.PushConfiguration;
import de.mossgrabers.controller.ableton.push.controller.PushColorManager;
import de.mossgrabers.controller.ableton.push.controller.PushControlSurface;
import de.mossgrabers.controller.ableton.push.mode.BaseMode;
import de.mossgrabers.framework.command.trigger.BrowserCommand;
import de.mossgrabers.framework.controller.ButtonID;
import de.mossgrabers.framework.controller.color.ColorEx;
import de.mossgrabers.framework.daw.IModel;
import de.mossgrabers.framework.daw.data.IDeviceMetadata;
import de.mossgrabers.framework.daw.data.IItem;
import de.mossgrabers.framework.daw.resource.ChannelType;
import de.mossgrabers.framework.featuregroup.AbstractFeatureGroup;
import de.mossgrabers.framework.utils.ButtonEvent;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;


/**
 * Menu for adding tracks.
 *
 * @author Jürgen Moßgraber
 */
public class AddTrackMode extends BaseMode<IItem>
{
    private static final AddMode []                                     TOP_MENU             =
    {
        AddMode.INSTRUMENT,
        AddMode.AUDIO,
        AddMode.EFFECT,
        null,
        AddMode.DEVICE,
        null,
        null,
        null
    };

    private final Map<ColorEx, Integer>                                 buttonColorsHiFirst  = new HashMap<> ();
    private final Map<ColorEx, Integer>                                 buttonColorsLoFirst  = new HashMap<> ();
    private final Map<ColorEx, Integer>                                 buttonColorsHiSecond = new HashMap<> ();
    private final Map<ColorEx, Integer>                                 buttonColorsLoSecond = new HashMap<> ();

    private AddMode                                                     addMode              = AddMode.INSTRUMENT;
    private final BrowserCommand<PushControlSurface, PushConfiguration> browserCommand;


    /**
     * Constructor.
     *
     * @param surface The control surface
     * @param model The model
     */
    public AddTrackMode (final PushControlSurface surface, final IModel model)
    {
        super ("Add Track", surface, model);

        this.browserCommand = new BrowserCommand<> (model, surface);

        this.buttonColorsHiFirst.put (ColorEx.YELLOW, Integer.valueOf (PushColorManager.PUSH2_COLOR2_YELLOW_HI));
        this.buttonColorsHiFirst.put (ColorEx.GREEN, Integer.valueOf (PushColorManager.PUSH2_COLOR2_GREEN_HI));
        this.buttonColorsHiFirst.put (ColorEx.BLUE, Integer.valueOf (PushColorManager.PUSH2_COLOR2_BLUE_HI));
        this.buttonColorsHiFirst.put (ColorEx.ORANGE, Integer.valueOf (PushColorManager.PUSH2_COLOR2_AMBER_HI));
        this.buttonColorsHiFirst.put (ColorEx.DARK_ORANGE, Integer.valueOf (PushColorManager.PUSH2_COLOR2_AMBER_HI));

        this.buttonColorsLoFirst.put (ColorEx.YELLOW, Integer.valueOf (PushColorManager.PUSH2_COLOR2_YELLOW_LO));
        this.buttonColorsLoFirst.put (ColorEx.GREEN, Integer.valueOf (PushColorManager.PUSH2_COLOR2_GREEN_LO));
        this.buttonColorsLoFirst.put (ColorEx.BLUE, Integer.valueOf (PushColorManager.PUSH2_COLOR2_BLUE_LO));
        this.buttonColorsLoFirst.put (ColorEx.ORANGE, Integer.valueOf (PushColorManager.PUSH2_COLOR2_AMBER_LO));
        this.buttonColorsLoFirst.put (ColorEx.DARK_ORANGE, Integer.valueOf (PushColorManager.PUSH2_COLOR2_AMBER_LO));

        this.buttonColorsHiSecond.put (ColorEx.YELLOW, Integer.valueOf (PushColorManager.PUSH2_COLOR2_YELLOW_HI));
        this.buttonColorsHiSecond.put (ColorEx.GREEN, Integer.valueOf (PushColorManager.PUSH2_COLOR2_GREEN_HI));
        this.buttonColorsHiSecond.put (ColorEx.BLUE, Integer.valueOf (PushColorManager.PUSH2_COLOR2_BLUE_HI));
        this.buttonColorsHiSecond.put (ColorEx.ORANGE, Integer.valueOf (PushColorManager.PUSH2_COLOR2_AMBER_HI));
        this.buttonColorsHiSecond.put (ColorEx.DARK_ORANGE, Integer.valueOf (PushColorManager.PUSH2_COLOR2_AMBER_HI));

        this.buttonColorsLoSecond.put (ColorEx.YELLOW, Integer.valueOf (PushColorManager.PUSH2_COLOR2_YELLOW_LO));
        this.buttonColorsLoSecond.put (ColorEx.GREEN, Integer.valueOf (PushColorManager.PUSH2_COLOR2_GREEN_LO));
        this.buttonColorsLoSecond.put (ColorEx.BLUE, Integer.valueOf (PushColorManager.PUSH2_COLOR2_BLUE_LO));
        this.buttonColorsLoSecond.put (ColorEx.ORANGE, Integer.valueOf (PushColorManager.PUSH2_COLOR2_AMBER_LO));
        this.buttonColorsLoSecond.put (ColorEx.DARK_ORANGE, Integer.valueOf (PushColorManager.PUSH2_COLOR2_AMBER_LO));
    }


    /**
     * Set the add mode.
     *
     * @param addMode The mode to activate
     */
    public void setAddMode (final AddMode addMode)
    {
        this.addMode = addMode;
    }


    /** {@inheritDoc} */
    @Override
    public void onFirstRow (final int index, final ButtonEvent event)
    {
        if (event != ButtonEvent.UP)
            return;

        this.surface.getModeManager ().restore ();

        if (index == 0 && this.addMode == AddMode.DEVICE)
        {
            this.browserCommand.startBrowser (true, false);
            return;
        }

        final ChannelType channelType = this.addMode.getChannelType ();
        final Optional<IDeviceMetadata> shortcut = index == 0 ? Optional.empty () : this.getShortcut (index - 1);
        String channelName = null;
        IDeviceMetadata deviceMetadata = null;
        if (shortcut.isPresent ())
        {
            deviceMetadata = shortcut.get ();
            channelName = deviceMetadata.name ();
        }

        if (channelType == ChannelType.UNKNOWN)
        {
            if (deviceMetadata != null)
                this.model.getCursorTrack ().addDevice (deviceMetadata);
        }
        else
            this.model.getTrackBank ().addChannel (channelType, channelName, deviceMetadata == null ? Collections.emptyList () : Collections.singletonList (deviceMetadata));
    }


    /** {@inheritDoc} */
    @Override
    public void onSecondRow (final int index, final ButtonEvent event)
    {
        if (event == ButtonEvent.UP && TOP_MENU[index] != null)
            this.addMode = TOP_MENU[index];
    }


    /** {@inheritDoc} */
    @Override
    public int getButtonColor (final ButtonID buttonID)
    {
        int index = this.isButtonRow (0, buttonID);
        if (index >= 0)
        {
            final ColorEx color = this.addMode.getColor ();
            return index == 0 ? this.buttonColorsHiFirst.get (color).intValue () : this.buttonColorsLoFirst.get (color).intValue ();
        }

        index = this.isButtonRow (1, buttonID);
        if (index >= 0)
        {
            if (TOP_MENU[index] == null)
                return this.colorManager.getColorIndex (AbstractFeatureGroup.BUTTON_COLOR_OFF);
            final ColorEx color = TOP_MENU[index].getColor ();
            return TOP_MENU[index] == this.addMode ? this.buttonColorsHiSecond.get (color).intValue () : this.buttonColorsLoSecond.get (color).intValue ();
        }

        return this.colorManager.getColorIndex (AbstractFeatureGroup.BUTTON_COLOR_OFF);
    }


    /**
     * Get the selected shortcut depending on the current add mode.
     *
     * @param index The shortcut index
     * @return The metadata at that index, if the host exposes one
     */
    private Optional<IDeviceMetadata> getShortcut (final int index)
    {
        final PushConfiguration conf = this.surface.getConfiguration ();
        return switch (this.addMode)
        {
            case INSTRUMENT -> conf.getInstrumentShortcut (index);
            case AUDIO, EFFECT -> conf.getAudioEffectShortcut (index);
            case DEVICE -> conf.getDeviceShortcut (index);
        };
    }
    /** Existing local chooser state and shortcut metadata, without rendering policy. */
    public AddMode getObservedAddMode () { return this.addMode; }
    public Optional<IDeviceMetadata> getObservedShortcut (final int index) { return this.getShortcut (index); }

}
