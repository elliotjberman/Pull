// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2017-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.controller.ableton.push;

import java.util.List;
import java.util.Set;

import de.mossgrabers.framework.configuration.AbstractConfiguration;
import de.mossgrabers.framework.configuration.IEnumSetting;
import de.mossgrabers.framework.configuration.IIntegerSetting;
import de.mossgrabers.framework.configuration.ISettingsUI;
import de.mossgrabers.framework.controller.color.ColorEx;
import de.mossgrabers.framework.controller.valuechanger.IValueChanger;
import de.mossgrabers.framework.daw.IHost;
import de.mossgrabers.framework.daw.midi.ArpeggiatorMode;
import de.mossgrabers.framework.graphics.IGraphicsConfiguration;
import de.mossgrabers.framework.mode.Modes;
import de.mossgrabers.framework.view.Views;


/**
 * The configuration settings for Push.
 *
 * @author Jürgen Moßgraber
 */
public class PushConfiguration extends AbstractConfiguration implements IGraphicsConfiguration
{
    /** Setting for the ribbon mode. */
    public static final Integer     RIBBON_MODE                                = Integer.valueOf (NEXT_SETTING_ID);
    /** Setting for the ribbon mode MIDI CC. */
    public static final Integer     RIBBON_MODE_CC_VAL                         = Integer.valueOf (NEXT_SETTING_ID + 1);
    /** Setting for the ribbon mode note repeat. */
    public static final Integer     RIBBON_MODE_NOTE_REPEAT                    = Integer.valueOf (NEXT_SETTING_ID + 2);
    /** Enable the automatic arpeggiator used by the drum-controller roll pads. */
    public static final Integer     DRUM_CONTROLLER_ROLL                       = Integer.valueOf (NEXT_SETTING_ID + 3);

    /** Setting for the display brightness. */
    public static final Integer     DISPLAY_BRIGHTNESS                         = Integer.valueOf (NEXT_SETTING_ID + 5);
    /** Setting for the pad LED brightness. */
    public static final Integer     LED_BRIGHTNESS                             = Integer.valueOf (NEXT_SETTING_ID + 6);
    /** Setting for the Push 2 pad sensitivity. */
    public static final Integer     PAD_PUSH2_SENSITIVITY                      = Integer.valueOf (NEXT_SETTING_ID + 7);
    /** Setting for the Push 2 pad gain. */
    public static final Integer     PAD_PUSH2_GAIN                             = Integer.valueOf (NEXT_SETTING_ID + 8);
    /** Setting for the Push 2 pad dynamics. */
    public static final Integer     PAD_PUSH2_DYNAMICS                         = Integer.valueOf (NEXT_SETTING_ID + 9);
    /** Setting for stopping automation recording on knob release. */
    public static final Integer     STOP_AUTOMATION_ON_KNOB_RELEASE            = Integer.valueOf (NEXT_SETTING_ID + 15);
    /** Use ribbon for pitch bend. */
    public static final int         RIBBON_MODE_PITCH                          = 0;
    /** Use ribbon for MIDI CC. */
    public static final int         RIBBON_MODE_CC                             = 1;
    /** Use ribbon for MIDI CC and pitch bend. */
    public static final int         RIBBON_MODE_CC_PB                          = 2;
    /** Use ribbon for pitch bend and MIDI CC. */
    public static final int         RIBBON_MODE_PB_CC                          = 3;
    /** Use ribbon as volume fader. */
    public static final int         RIBBON_MODE_FADER                          = 4;
    /** Use ribbon to change the last touched parameter. */
    public static final int         RIBBON_MODE_LAST_TOUCHED                   = 5;

    /** Use ribbon not for note repeat settings. */
    public static final int         NOTE_REPEAT_OFF                            = 0;
    /** Use ribbon for changing the note repeat period. */
    public static final int         NOTE_REPEAT_PERIOD                         = 1;
    /** Use ribbon for changing the note repeat length. */
    public static final int         NOTE_REPEAT_LENGTH                         = 2;

    private static final String     CATEGORY_RIBBON                            = "Ribbon";
    private static final String     CATEGORY_DRUM_CONTROLLER                   = "Drum Controller";

    private static final String []  RIBBON_MODE_VALUES                         =
    {
        "Pitch",
        "CC",
        "CC/Pitch",
        "Pitch/CC",
        "Fader",
        "Last Touched"
    };

    private static final String []  RIBBON_NOTE_REPEAT_VALUES                  =
    {
        "Off",
        "Period",
        "Length"
    };

    private static final Views []   PREFERRED_NOTE_VIEWS                       =
    {
        Views.PLAY,
        Views.CHORDS,
        Views.PIANO,
        Views.DRUM64,
        Views.DRUM,
        Views.DRUM4,
        Views.DRUM8,
        Views.DRUM_XOX,
        Views.SEQUENCER,
        Views.RAINDROPS,
        Views.POLY_SEQUENCER
    };

    /** What does the ribbon send? **/
    private int                   ribbonMode                   = RIBBON_MODE_PITCH;
    private int                   ribbonModeCCVal              = 1;
    private int                   ribbonModeNoteRepeat         = NOTE_REPEAT_PERIOD;
    private boolean               drumControllerRoll          = true;

    private boolean               stopAutomationOnKnobRelease  = false;
    private Modes                 globalMixMode                = Modes.VOLUME;
    private int                   mixSendOffset;
    private int                   trackMixSendOffset;
    private Modes                 layerMode                    = null;

    private int                   displayBrightness            = 255;
    private int                   ledBrightness                = 127;
    private int                   padSensitivityPush2          = 5;
    private int                   padGainPush2                 = 5;
    private int                   padDynamicsPush2             = 5;
    private IIntegerSetting       displayBrightnessSetting;
    private IIntegerSetting       ledBrightnessSetting;
    private IEnumSetting          ribbonModeSetting;
    private IIntegerSetting       ribbonModeCCSetting;
    private IEnumSetting          ribbonModeNoteRepeatSetting;

    private IIntegerSetting       padSensitivitySetting;
    private IIntegerSetting       padGainSetting;
    private IIntegerSetting       padDynamicsSetting;

    /**
     * Constructor.
     *
     * @param host The DAW host
     * @param valueChanger The value changer
     * @param arpeggiatorModes The available arpeggiator modes
     */
    public PushConfiguration (final IHost host, final IValueChanger valueChanger, final List<ArpeggiatorMode> arpeggiatorModes)
    {
        super (host, valueChanger, arpeggiatorModes);

        this.preferredAudioView = Views.CLIP_LENGTH;

        // Each pad callback uploads the complete pad configuration. Let PAD_PUSH2_SENSITIVITY
        // perform the one initial upload; later changes still notify normally.
        this.dontNotifyAll.addAll (Set.of (PAD_PUSH2_GAIN, PAD_PUSH2_DYNAMICS));
    }


    /** {@inheritDoc} */
    @Override
    public void init (final ISettingsUI globalSettings, final ISettingsUI documentSettings)
    {
        ///////////////////////////
        // Scale

        this.activateScaleSetting (documentSettings);
        this.activateScaleBaseSetting (documentSettings);
        this.activateScaleInScaleSetting (documentSettings);
        this.activateScaleLayoutSetting (documentSettings);

        ///////////////////////////
        // Note Repeat

        this.activateNoteRepeatSetting (documentSettings);

        ///////////////////////////
        // Session

        this.activateSelectClipOnLaunchSetting (globalSettings);
        this.activateDrawRecordStripeSetting (globalSettings);
        this.activateActionForRecArmedPad (globalSettings);

        ///////////////////////////
        // Play and Sequence

        this.activateAccentActiveSetting (globalSettings);
        this.activateAccentValueSetting (globalSettings);
        this.activateQuantizeAmountSetting (globalSettings);
        this.activateStartupViewSetting (globalSettings, PREFERRED_NOTE_VIEWS);
        this.activateMidiEditChannelSetting (documentSettings);
        this.activateTurnOffScalePadsSetting (globalSettings);
        this.activateShowPlayedChordsSetting (globalSettings);

        ///////////////////////////
        // Drum Sequencer

        this.activateAutoSelectDrumSetting (globalSettings);
        this.activateTurnOffEmptyDrumPadsSetting (globalSettings);
        this.activateDrumControllerRollSetting (globalSettings);

        ///////////////////////////
        // Workflow

        this.activateExcludeDeactivatedItemsSetting (globalSettings);
        this.activateEnableVUMetersSetting (globalSettings);
        this.activateFootswitchSetting (globalSettings, 0, "Footswitch 2");
        this.activateStopAutomationOnKnobReleaseSetting (globalSettings);
        this.activateNewClipLengthSetting (globalSettings);
        this.activateKnobSpeedSetting (globalSettings);

        ///////////////////////////
        // Add Track - Device Shortcuts

        this.initializeDeviceShortcuts ();

        ///////////////////////////
        // Ribbon

        this.activateRibbonSettings (globalSettings);

        ///////////////////////////
        // Hardware configuration

        this.activatePush2PadSettings (globalSettings);
        this.activateConvertAftertouchSetting (globalSettings);

        ///////////////////////////
        // Push 2 Hardware

        this.activatePush2HardwareSettings (globalSettings);
    }


    /**
     * Set the ribbon mode.
     *
     * @param mode The functionality for the ribbon
     */
    public void setRibbonMode (final int mode)
    {
        this.ribbonModeSetting.set (RIBBON_MODE_VALUES[mode]);
    }


    /**
     * Get the ribbon mode.
     *
     * @return The functionality for the ribbon
     */
    public int getRibbonMode ()
    {
        return this.ribbonMode;
    }


    /**
     * Set the MIDI CC to use for the CC functionality of the ribbon.
     *
     * @param value The MIDI CC value
     */
    public void setRibbonModeCC (final int value)
    {
        this.ribbonModeCCSetting.set (value);
    }


    /**
     * Get the MIDI CC to use for the CC functionality of the ribbon.
     *
     * @return The MIDI CC value
     */
    public int getRibbonModeCCVal ()
    {
        return this.ribbonModeCCVal;
    }


    /**
     * Set the ribbon mode note repeat.
     *
     * @param mode The functionality for the ribbon in note repeat mode
     */
    public void setRibbonNoteRepeat (final int mode)
    {
        this.ribbonModeNoteRepeatSetting.set (RIBBON_NOTE_REPEAT_VALUES[mode]);
    }


    /**
     * Get the ribbon mode note repeat.
     *
     * @return The functionality for the ribbon in note repeat mode
     */
    public int getRibbonNoteRepeat ()
    {
        return this.ribbonModeNoteRepeat;
    }


    /**
     * Change the display brightness.
     *
     * @param control The control value
     */
    public void changeDisplayBrightness (final int control)
    {
        this.displayBrightnessSetting.set (this.valueChanger.changeValue (control, this.displayBrightness, -100, 101));
    }


    /**
     * Change the LED brightness.
     *
     * @param control The control value
     */
    public void changeLEDBrightness (final int control)
    {
        this.ledBrightnessSetting.set (this.valueChanger.changeValue (control, this.ledBrightness, -100, 101));
    }


    /**
     * Change the pad sensitivity.
     *
     * @param control The control value
     */
    public void changePadSensitivity (final int control)
    {
        this.padSensitivitySetting.set (this.valueChanger.changeValue (control, this.padSensitivityPush2, -100, 11));
    }


    /**
     * Change the pad gain.
     *
     * @param control The control value
     */
    public void changePadGain (final int control)
    {
        this.padGainSetting.set (this.valueChanger.changeValue (control, this.padGainPush2, -100, 11));
    }


    /**
     * Change the pad dynamics.
     *
     * @param control The control value
     */
    public void changePadDynamics (final int control)
    {
        this.padDynamicsSetting.set (this.valueChanger.changeValue (control, this.padDynamicsPush2, -100, 11));
    }


    /**
     * Get the display brightness.
     *
     * @return The display brightness.
     */
    public int getDisplayBrightness ()
    {
        return this.displayBrightness;
    }


    /**
     * Set the display brightness.
     *
     * @param displayBrightness The display brightness.
     */
    public void setDisplayBrightness (final int displayBrightness)
    {
        this.displayBrightnessSetting.set (displayBrightness);
    }


    /**
     * Get the LED brightness.
     *
     * @return The LED brightness
     */
    public int getLedBrightness ()
    {
        return this.ledBrightness;
    }


    /**
     * Set the LED brightness.
     *
     * @param ledBrightness The LED brightness
     */
    public void setLEDBrightness (final int ledBrightness)
    {
        this.ledBrightnessSetting.set (ledBrightness);
    }


    /**
     * Stop automation recording on knob release?
     *
     * @return True if should be stopped
     */
    public boolean isStopAutomationOnKnobRelease ()
    {
        return this.stopAutomationOnKnobRelease;
    }


    /**
     * Get the pad sensitivity for Push 2.
     *
     * @return The pad sensitivity
     */
    public int getPadSensitivityPush2 ()
    {
        return this.padSensitivityPush2;
    }


    /**
     * Set the pad sensitivity for Push 2.
     *
     * @param padSensitivity The pad sensitivity
     */
    public void setPadSensitivityPush2 (final int padSensitivity)
    {
        this.padSensitivitySetting.set (padSensitivity);
    }


    /**
     * Get the pad gain for Push 2.
     *
     * @return The pad gain
     */
    public int getPadGainPush2 ()
    {
        return this.padGainPush2;
    }


    /**
     * Set the pad gain for Push 2.
     *
     * @param padGain The pad gain
     */
    public void setPadGainPush2 (final int padGain)
    {
        this.padGainSetting.set (padGain);
    }


    /**
     * Get the pad dynamics for Push 2.
     *
     * @return The pad dynamics.
     */
    public int getPadDynamicsPush2 ()
    {
        return this.padDynamicsPush2;
    }


    /**
     * Set the pad dynamics for Push 2.
     *
     * @param padDynamics The pad dynamics.
     */
    public void setPadDynamicsPush2 (final int padDynamics)
    {
        this.padDynamicsSetting.set (padDynamics);
    }


    /**
     * Get the parameter mode used by Global Mix.
     *
     * @return The Global Mix mode
     */
    public Modes getGlobalMixMode ()
    {
        return this.globalMixMode;
    }


    /**
     * Set the parameter mode used by Global Mix.
     *
     * @param mode The Global Mix mode
     */
    public void setGlobalMixMode (final Modes mode)
    {
        this.globalMixMode = mode;
    }


    /**
     * Get the first send shown in the mixer.
     *
     * @return The zero-based send offset
     */
    public int getMixSendOffset ()
    {
        return this.mixSendOffset;
    }


    /**
     * Set the first send shown in the mixer.
     *
     * @param offset The zero-based send offset
     */
    public void setMixSendOffset (final int offset)
    {
        this.mixSendOffset = Math.max (0, Math.min (4, offset));
    }


    /**
     * Get the first send shown for the selected track.
     *
     * @return The zero-based send offset
     */
    public int getTrackMixSendOffset ()
    {
        return this.trackMixSendOffset;
    }


    /**
     * Set the first send shown for the selected track.
     *
     * @param offset The zero-based send offset
     */
    public void setTrackMixSendOffset (final int offset)
    {
        this.trackMixSendOffset = Math.max (0, Math.min (4, offset));
    }


    /**
     * Set the current mode which is selected for layer mixing.
     *
     * @param layerMode The ID of a layer mode
     */
    public void setLayerMixMode (final Modes layerMode)
    {
        this.layerMode = layerMode;
    }


    /**
     * Get the current mode which is selected for layer mixing.
     *
     * @return The ID of the current mode which is selected for layer mixing.
     */
    public Modes getCurrentLayerMixMode ()
    {
        if (this.layerMode == null)
            this.layerMode = Modes.DEVICE_LAYER;
        return this.layerMode;
    }


    /** {@inheritDoc} */
    @Override
    public ColorEx getColorBackground ()
    {
        return DEFAULT_COLOR_BACKGROUND;
    }


    /** {@inheritDoc} */
    @Override
    public ColorEx getColorBackgroundDarker ()
    {
        return DEFAULT_COLOR_BACKGROUND_DARKER;
    }


    /** {@inheritDoc} */
    @Override
    public ColorEx getColorBackgroundLighter ()
    {
        return DEFAULT_COLOR_BACKGROUND_LIGHTER;
    }


    /** {@inheritDoc} */
    @Override
    public ColorEx getColorBorder ()
    {
        return DEFAULT_COLOR_BORDER;
    }


    /** {@inheritDoc} */
    @Override
    public ColorEx getColorText ()
    {
        return DEFAULT_COLOR_TEXT;
    }


    /** {@inheritDoc} */
    @Override
    public boolean isAntialiasEnabled ()
    {
        return true;
    }


    /**
     * Activate the Push 2 hardware settings.
     *
     * @param settingsUI The settings
     */
    private void activatePush2HardwareSettings (final ISettingsUI settingsUI)
    {
        this.displayBrightnessSetting = settingsUI.getRangeSetting ("Display Brightness", CATEGORY_HARDWARE_SETUP, 0, 100, 1, "%", 100);
        this.displayBrightnessSetting.addValueObserver (value -> {
            this.displayBrightness = value.intValue ();
            this.notifyObservers (DISPLAY_BRIGHTNESS);
        });

        this.ledBrightnessSetting = settingsUI.getRangeSetting ("LED Brightness", CATEGORY_HARDWARE_SETUP, 0, 100, 1, "%", 100);
        this.ledBrightnessSetting.addValueObserver (value -> {
            this.ledBrightness = value.intValue ();
            this.notifyObservers (LED_BRIGHTNESS);
        });
    }


    /**
     * Activate the ribbon settings.
     *
     * @param settingsUI The settings
     */
    private void activateRibbonSettings (final ISettingsUI settingsUI)
    {
        this.ribbonModeSetting = settingsUI.getEnumSetting ("Mode", CATEGORY_RIBBON, RIBBON_MODE_VALUES, RIBBON_MODE_VALUES[0]);
        this.ribbonModeSetting.addValueObserver (value -> {
            this.ribbonMode = lookupIndex (RIBBON_MODE_VALUES, value);
            this.notifyObservers (RIBBON_MODE);
        });

        this.ribbonModeCCSetting = settingsUI.getRangeSetting ("CC", CATEGORY_RIBBON, 0, 127, 1, "", 1);
        this.ribbonModeCCSetting.addValueObserver (value -> {
            this.ribbonModeCCVal = value.intValue ();
            this.notifyObservers (RIBBON_MODE_CC_VAL);
        });

        this.ribbonModeNoteRepeatSetting = settingsUI.getEnumSetting ("Function if Note Repeat is active", CATEGORY_RIBBON, RIBBON_NOTE_REPEAT_VALUES, RIBBON_NOTE_REPEAT_VALUES[1]);
        this.ribbonModeNoteRepeatSetting.addValueObserver (value -> {
            this.ribbonModeNoteRepeat = lookupIndex (RIBBON_NOTE_REPEAT_VALUES, value);
            this.notifyObservers (RIBBON_MODE_NOTE_REPEAT);
        });
    }


    /**
     * Test whether the drum controller should automatically enable its roll arpeggiator.
     *
     * @return True if automatic rolls are enabled
     */
    public boolean isDrumControllerRollEnabled ()
    {
        return this.drumControllerRoll;
    }


    /**
     * Activate the drum-controller settings.
     *
     * @param settingsUI The settings
     */
    private void activateDrumControllerRollSetting (final ISettingsUI settingsUI)
    {
        settingsUI.getEnumSetting ("Automatic arp / roll", CATEGORY_DRUM_CONTROLLER, ON_OFF_OPTIONS, ON_OFF_OPTIONS[1]).addValueObserver (value -> {
            this.drumControllerRoll = ON_OFF_OPTIONS[1].equals (value);
            this.notifyObservers (DRUM_CONTROLLER_ROLL);
        });
    }


    /**
     * Activate the stop automation on knob release setting.
     *
     * @param settingsUI The settings
     */
    private void activateStopAutomationOnKnobReleaseSetting (final ISettingsUI settingsUI)
    {
        settingsUI.getEnumSetting ("Stop automation recording on knob release", CATEGORY_WORKFLOW, ON_OFF_OPTIONS, ON_OFF_OPTIONS[0]).addValueObserver (value -> {
            this.stopAutomationOnKnobRelease = "On".equals (value);
            this.notifyObservers (STOP_AUTOMATION_ON_KNOB_RELEASE);
        });
    }


    /**
     * Activate the Push 2 pad settings.
     *
     * @param settingsUI The settings
     */
    private void activatePush2PadSettings (final ISettingsUI settingsUI)
    {
        this.padSensitivitySetting = settingsUI.getRangeSetting ("Sensitivity", CATEGORY_PADS, 0, 10, 1, "", 5);
        this.padSensitivitySetting.addValueObserver (value -> {
            this.padSensitivityPush2 = value.intValue ();
            this.notifyObservers (PAD_PUSH2_SENSITIVITY);
        });

        this.padGainSetting = settingsUI.getRangeSetting ("Gain", CATEGORY_PADS, 0, 10, 1, "", 5);
        this.padGainSetting.addValueObserver (value -> {
            this.padGainPush2 = value.intValue ();
            this.notifyObservers (PAD_PUSH2_GAIN);
        });

        this.padDynamicsSetting = settingsUI.getRangeSetting ("Dynamics", CATEGORY_PADS, 0, 10, 1, "", 5);
        this.padDynamicsSetting.addValueObserver (value -> {
            this.padDynamicsPush2 = value.intValue ();
            this.notifyObservers (PAD_PUSH2_DYNAMICS);
        });
    }


}
