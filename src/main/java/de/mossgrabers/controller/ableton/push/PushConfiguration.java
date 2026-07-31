// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2017-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.controller.ableton.push;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import de.mossgrabers.framework.configuration.AbstractConfiguration;
import de.mossgrabers.framework.configuration.IColorSetting;
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
    /** A lock state for the mode buttons. */
    public enum LockState
    {
        /** No lock state. */
        OFF,
        /** Locked to mute. */
        MUTE,
        /** Locked to solo. */
        SOLO,
        /** Locked to clip stop. */
        CLIP_STOP,
    }


    /** Setting for the ribbon mode. */
    public static final Integer     RIBBON_MODE                                = Integer.valueOf (NEXT_SETTING_ID);
    /** Setting for the ribbon mode MIDI CC. */
    public static final Integer     RIBBON_MODE_CC_VAL                         = Integer.valueOf (NEXT_SETTING_ID + 1);
    /** Setting for the ribbon mode note repeat. */
    public static final Integer     RIBBON_MODE_NOTE_REPEAT                    = Integer.valueOf (NEXT_SETTING_ID + 2);

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
    /** Mode debug. */
    public static final Integer     DEBUG_MODE                                 = Integer.valueOf (NEXT_SETTING_ID + 16);
    /** Push 2 display debug window. */
    public static final Integer     DEBUG_WINDOW                               = Integer.valueOf (NEXT_SETTING_ID + 17);
    /** Background color of an element. */
    public static final Integer     COLOR_BACKGROUND                           = Integer.valueOf (NEXT_SETTING_ID + 20);
    /** Border color of an element. */
    public static final Integer     COLOR_BORDER                               = Integer.valueOf (NEXT_SETTING_ID + 21);
    /** Text color of an element. */
    public static final Integer     COLOR_TEXT                                 = Integer.valueOf (NEXT_SETTING_ID + 22);
    /** Fader color of an element. */
    public static final Integer     COLOR_FADER                                = Integer.valueOf (NEXT_SETTING_ID + 23);
    /** VU color of an element. */
    public static final Integer     COLOR_VU                                   = Integer.valueOf (NEXT_SETTING_ID + 24);
    /** Edit color of an element. */
    public static final Integer     COLOR_EDIT                                 = Integer.valueOf (NEXT_SETTING_ID + 25);
    /** Record color of an element. */
    public static final Integer     COLOR_RECORD                               = Integer.valueOf (NEXT_SETTING_ID + 26);
    /** Solo color of an element. */
    public static final Integer     COLOR_SOLO                                 = Integer.valueOf (NEXT_SETTING_ID + 27);
    /** Mute color of an element. */
    public static final Integer     COLOR_MUTE                                 = Integer.valueOf (NEXT_SETTING_ID + 28);
    /** Background color darker of an element. */
    public static final Integer     COLOR_BACKGROUND_DARKER                    = Integer.valueOf (NEXT_SETTING_ID + 29);
    /** Background color lighter of an element. */
    public static final Integer     COLOR_BACKGROUND_LIGHTER                   = Integer.valueOf (NEXT_SETTING_ID + 30);

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
    private static final String     CATEGORY_COLORS                            = "Display Colors";

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

    /** ID for moving the track bank by a page. */
    public static final int         CURSOR_KEYS_TRACK_OPTION_MOVE_BANK_BY_PAGE = 0;
    /** ID for moving the track bank by 1. */
    public static final int         CURSOR_KEYS_TRACK_OPTION_MOVE_BANK_BY_1    = 1;
    /** ID for swapping the track with the previous/next track. */
    public static final int         CURSOR_KEYS_TRACK_OPTION_SWAP              = 2;

    private static final String []  CURSOR_KEYS_TRACK_OPTIONS                  =
    {
        "Scroll track bank by page",
        "Scroll track bank by 1",
        "Swap tracks"
    };

    /** ID for moving the scene bank by a page. */
    public static final int         CURSOR_KEYS_SCENE_OPTION_MOVE_BANK_BY_PAGE = 0;
    /** ID for moving the scene bank by 1. */
    public static final int         CURSOR_KEYS_SCENE_OPTION_MOVE_BANK_BY_1    = 1;

    private static final String []  CURSOR_KEYS_SCENE_OPTIONS                  =
    {
        "Scroll scene bank by page",
        "Scroll scene bank by 1",
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

    /** Debug modes. */
    private static final Set<Modes> DEBUG_MODES                                = EnumSet.noneOf (Modes.class);

    static
    {
        DEBUG_MODES.add (Modes.TRACK);
        DEBUG_MODES.add (Modes.TRACK_DETAILS);
        DEBUG_MODES.add (Modes.VOLUME);
        DEBUG_MODES.add (Modes.CROSSFADER);
        DEBUG_MODES.add (Modes.PAN);
        DEBUG_MODES.add (Modes.SEND1);
        DEBUG_MODES.add (Modes.SEND2);
        DEBUG_MODES.add (Modes.SEND3);
        DEBUG_MODES.add (Modes.SEND4);
        DEBUG_MODES.add (Modes.SEND5);
        DEBUG_MODES.add (Modes.SEND6);
        DEBUG_MODES.add (Modes.SEND7);
        DEBUG_MODES.add (Modes.SEND8);
        DEBUG_MODES.add (Modes.MASTER);
        DEBUG_MODES.add (Modes.MASTER_TEMP);
        DEBUG_MODES.add (Modes.DEVICE_PARAMS);
        DEBUG_MODES.add (Modes.DEVICE_CHAINS);
        DEBUG_MODES.add (Modes.DEVICE_LAYER);
        DEBUG_MODES.add (Modes.DEVICE_LAYER_VOLUME);
        DEBUG_MODES.add (Modes.DEVICE_LAYER_PAN);
        DEBUG_MODES.add (Modes.DEVICE_LAYER_SEND1);
        DEBUG_MODES.add (Modes.DEVICE_LAYER_SEND2);
        DEBUG_MODES.add (Modes.DEVICE_LAYER_SEND3);
        DEBUG_MODES.add (Modes.DEVICE_LAYER_SEND4);
        DEBUG_MODES.add (Modes.DEVICE_LAYER_SEND5);
        DEBUG_MODES.add (Modes.DEVICE_LAYER_SEND6);
        DEBUG_MODES.add (Modes.DEVICE_LAYER_SEND7);
        DEBUG_MODES.add (Modes.DEVICE_LAYER_SEND8);
        DEBUG_MODES.add (Modes.DEVICE_LAYER_DETAILS);
        DEBUG_MODES.add (Modes.BROWSER);
        DEBUG_MODES.add (Modes.CLIP);
        DEBUG_MODES.add (Modes.NOTE);
        DEBUG_MODES.add (Modes.FRAME);
        DEBUG_MODES.add (Modes.GROOVE);
        DEBUG_MODES.add (Modes.REC_ARM);
        DEBUG_MODES.add (Modes.ACCENT);
        DEBUG_MODES.add (Modes.SCALES);
        DEBUG_MODES.add (Modes.SCALE_LAYOUT);
        DEBUG_MODES.add (Modes.FIXED);
        DEBUG_MODES.add (Modes.RIBBON);
        DEBUG_MODES.add (Modes.AUTOMATION);
        DEBUG_MODES.add (Modes.TRANSPORT);
        DEBUG_MODES.add (Modes.USER);
        DEBUG_MODES.add (Modes.SETUP);
        DEBUG_MODES.add (Modes.INFO);
        DEBUG_MODES.add (Modes.REPEAT_NOTE);
    }

    private LockState             lockState                    = LockState.OFF;
    private int                   cursorKeysTrackOption        = 0;
    private int                   cursorKeysTrackShiftedOption = 2;
    private int                   cursorKeysSceneOption        = 1;
    private int                   cursorKeysSceneShiftedOption = 0;
    /** What does the ribbon send? **/
    private int                   ribbonMode                   = RIBBON_MODE_PITCH;
    private int                   ribbonModeCCVal              = 1;
    private int                   ribbonModeNoteRepeat         = NOTE_REPEAT_PERIOD;

    private boolean               stopAutomationOnKnobRelease  = false;
    private Modes                 debugMode                    = Modes.TRACK;
    private Modes                 globalMixMode                = Modes.VOLUME;
    private int                   mixSendOffset;
    private int                   trackMixSendOffset;
    private Modes                 layerMode                    = null;

    private int                   displayBrightness            = 255;
    private int                   ledBrightness                = 127;
    private int                   padSensitivityPush2          = 5;
    private int                   padGainPush2                 = 5;
    private int                   padDynamicsPush2             = 5;
    private ColorEx               colorBackground              = DEFAULT_COLOR_BACKGROUND;
    private ColorEx               colorBorder                  = DEFAULT_COLOR_BORDER;
    private ColorEx               colorText                    = DEFAULT_COLOR_TEXT;
    private ColorEx               colorFader                   = DEFAULT_COLOR_FADER;
    private ColorEx               colorVU                      = DEFAULT_COLOR_VU;
    private ColorEx               colorEdit                    = DEFAULT_COLOR_EDIT;
    private ColorEx               colorRecord                  = DEFAULT_COLOR_RECORD;
    private ColorEx               colorSolo                    = DEFAULT_COLOR_SOLO;
    private ColorEx               colorMute                    = DEFAULT_COLOR_MUTE;
    private ColorEx               colorBackgroundDarker        = DEFAULT_COLOR_BACKGROUND_DARKER;
    private ColorEx               colorBackgroundLighter       = DEFAULT_COLOR_BACKGROUND_LIGHTER;

    private IIntegerSetting       displayBrightnessSetting;
    private IIntegerSetting       ledBrightnessSetting;
    private IEnumSetting          ribbonModeSetting;
    private IIntegerSetting       ribbonModeCCSetting;
    private IEnumSetting          ribbonModeNoteRepeatSetting;

    private IIntegerSetting       padSensitivitySetting;
    private IIntegerSetting       padGainSetting;
    private IIntegerSetting       padDynamicsSetting;

    private IEnumSetting          debugModeSetting;
    private IColorSetting         colorBackgroundSetting;
    private IColorSetting         colorBackgroundDarkerSetting;
    private IColorSetting         colorBackgroundLighterSetting;
    private IColorSetting         colorBorderSetting;
    private IColorSetting         colorTextSetting;
    private IColorSetting         colorFaderSetting;
    private IColorSetting         colorVUSetting;
    private IColorSetting         colorEditSetting;
    private IColorSetting         colorRecordSetting;
    private IColorSetting         colorSoloSetting;
    private IColorSetting         colorMuteSetting;

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

        // DEBUG_WINDOW is a signal rather than a setting value.
        this.dontNotifyAll.add (DEBUG_WINDOW);

        // Each pad callback uploads the complete pad configuration. Let PAD_PUSH2_SENSITIVITY
        // perform the one initial upload; later changes still notify normally.
        this.dontNotifyAll.addAll (Set.of (PAD_PUSH2_GAIN, PAD_PUSH2_DYNAMICS));

        // Each color callback redraws the complete display. The first scheduled surface flush
        // performs one deterministic redraw after startup; later changes still notify normally.
        this.dontNotifyAll.addAll (Set.of (COLOR_BACKGROUND, COLOR_BORDER, COLOR_TEXT, COLOR_FADER,
            COLOR_VU, COLOR_EDIT, COLOR_RECORD, COLOR_SOLO, COLOR_MUTE, COLOR_BACKGROUND_DARKER,
            COLOR_BACKGROUND_LIGHTER));
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
        // Transport

        this.activateFlipRecordSetting (globalSettings);

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

        ///////////////////////////
        // Workflow

        this.activateTrackNavigationSetting (globalSettings, CATEGORY_WORKFLOW, false);
        this.activateCursorKeysSettings (globalSettings);
        this.activateIncludeMasterSetting (globalSettings);
        this.activateExcludeDeactivatedItemsSetting (globalSettings);
        this.activateEnableVUMetersSetting (globalSettings);
        this.activateFootswitchSetting (globalSettings, 0, "Footswitch 2");
        this.activateStopAutomationOnKnobReleaseSetting (globalSettings);
        this.activateNewClipLengthSetting (globalSettings);
        this.activateKnobSpeedSetting (globalSettings);

        ///////////////////////////
        // Add Track - Device Favorites

        this.activateDeviceFavorites (globalSettings, 7, 7, 7, 7);

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
        this.activatePush2DisplayColorsSettings (globalSettings);

        ///////////////////////////
        // Debugging

        this.activateDebugSettings (globalSettings);
    }


    /**
     * Get the track function to execute for left/right cursor keys.
     *
     * @return The index of the option
     */
    public int getCursorKeysTrackOption ()
    {
        return this.cursorKeysTrackOption;
    }


    /**
     * Get the track function to execute for shifted left/right cursor keys.
     *
     * @return The index of the option
     */
    public int getCursorKeysTrackShiftedOption ()
    {
        return this.cursorKeysTrackShiftedOption;
    }


    /**
     * Get the scene function to execute for up/down cursor keys.
     *
     * @return The index of the option
     */
    public int getCursorKeysSceneOption ()
    {
        return this.cursorKeysSceneOption;
    }


    /**
     * Get the scene function to execute for shifted up/down cursor keys.
     *
     * @return The index of the option
     */
    public int getCursorKeysSceneShiftedOption ()
    {
        return this.cursorKeysSceneShiftedOption;
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
     * Returns true if the solo button is long pressed or solo mode is locked.
     *
     * @param isSoloLongPressed True if solo is long pressed
     * @return As explained above
     */
    public boolean isSoloState (final boolean isSoloLongPressed)
    {
        return isSoloLongPressed || this.lockState == LockState.SOLO;
    }


    /**
     * Returns true if the mute button is long pressed or mute mode is locked.
     *
     * @param isMuteLongPressed True if mute is long pressed
     * @return As explained above
     */
    public boolean isMuteState (final boolean isMuteLongPressed)
    {
        return isMuteLongPressed || this.lockState == LockState.MUTE;
    }


    /**
     * Returns true if the clip stop button is long pressed or clip stop mode is locked.
     *
     * @param isClipStopLongPressed True if clip stop is long pressed
     * @return As explained above
     */
    public boolean isClipStopState (final boolean isClipStopLongPressed)
    {
        return isClipStopLongPressed || this.lockState == LockState.CLIP_STOP;
    }


    /**
     * Is mute, solo or clip state locked (all mode buttons are used for solo or mute)?
     *
     * @return The state
     */
    public LockState getLockState ()
    {
        return this.lockState;
    }


    /**
     * Set if mute, solo or clip stop is locked (all mode buttons are used for solo or mute).
     *
     * @param lockState The new lock state
     */
    public void setLockState (final LockState lockState)
    {
        this.lockState = lockState;
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
     * Get the current mode which is selected for mixing.
     *
     * @return The ID of the current mode which is selected for mixing.
     */
    public Modes getCurrentMixMode ()
    {
        return Modes.isTrackMode (this.debugMode) ? this.debugMode : null;
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
        if (this.layerMode != null)
            return this.layerMode;

        final Modes currentMixMode = this.getCurrentMixMode ();
        if (currentMixMode == null)
            this.layerMode = Modes.DEVICE_LAYER;
        else
        {
            switch (currentMixMode)
            {
                case VOLUME:
                    this.layerMode = Modes.DEVICE_LAYER_VOLUME;
                    break;

                case PAN:
                    this.layerMode = Modes.DEVICE_LAYER_PAN;
                    break;

                case SEND1:
                    this.layerMode = Modes.DEVICE_LAYER_SEND1;
                    break;
                case SEND2:
                    this.layerMode = Modes.DEVICE_LAYER_SEND2;
                    break;
                case SEND3:
                    this.layerMode = Modes.DEVICE_LAYER_SEND3;
                    break;
                case SEND4:
                    this.layerMode = Modes.DEVICE_LAYER_SEND4;
                    break;
                case SEND5:
                    this.layerMode = Modes.DEVICE_LAYER_SEND5;
                    break;
                case SEND6:
                    this.layerMode = Modes.DEVICE_LAYER_SEND6;
                    break;
                case SEND7:
                    this.layerMode = Modes.DEVICE_LAYER_SEND7;
                    break;
                case SEND8:
                    this.layerMode = Modes.DEVICE_LAYER_SEND8;
                    break;

                case TRACK:
                default:
                    this.layerMode = Modes.DEVICE_LAYER;
                    break;
            }
        }
        return this.layerMode;
    }


    /** {@inheritDoc} */
    @Override
    public ColorEx getColorBackground ()
    {
        return this.colorBackground;
    }


    /** {@inheritDoc} */
    @Override
    public ColorEx getColorBackgroundDarker ()
    {
        return this.colorBackgroundDarker;
    }


    /** {@inheritDoc} */
    @Override
    public ColorEx getColorBackgroundLighter ()
    {
        return this.colorBackgroundLighter;
    }


    /** {@inheritDoc} */
    @Override
    public ColorEx getColorBorder ()
    {
        return this.colorBorder;
    }


    /** {@inheritDoc} */
    @Override
    public ColorEx getColorText ()
    {
        return this.colorText;
    }


    /** {@inheritDoc} */
    @Override
    public ColorEx getColorEdit ()
    {
        return this.colorEdit;
    }


    /** {@inheritDoc} */
    @Override
    public ColorEx getColorFader ()
    {
        return this.colorFader;
    }


    /** {@inheritDoc} */
    @Override
    public ColorEx getColorVu ()
    {
        return this.colorVU;
    }


    /** {@inheritDoc} */
    @Override
    public ColorEx getColorRecord ()
    {
        return this.colorRecord;
    }


    /** {@inheritDoc} */
    @Override
    public ColorEx getColorSolo ()
    {
        return this.colorSolo;
    }


    /** {@inheritDoc} */
    @Override
    public ColorEx getColorMute ()
    {
        return this.colorMute;
    }


    /** {@inheritDoc} */
    @Override
    public boolean isAntialiasEnabled ()
    {
        return true;
    }


    /**
     * Get the selected display mode for debugging.
     *
     * @return The ID of a mode
     */
    public Modes getMixerMode ()
    {
        return this.debugMode;
    }
    /**
     * Activate the cursor keys track option.
     *
     * @param settingsUI The settings
     */
    private void activateCursorKeysSettings (final ISettingsUI settingsUI)
    {
        final IEnumSetting cursorKeysTrackSetting = settingsUI.getEnumSetting ("Cursor Keys Track Option", CATEGORY_WORKFLOW, CURSOR_KEYS_TRACK_OPTIONS, CURSOR_KEYS_TRACK_OPTIONS[0]);
        cursorKeysTrackSetting.addValueObserver (value -> this.cursorKeysTrackOption = lookupIndex (CURSOR_KEYS_TRACK_OPTIONS, value));
        final IEnumSetting cursorKeysTrackShiftedSetting = settingsUI.getEnumSetting ("Shifted Cursor Keys Track Option", CATEGORY_WORKFLOW, CURSOR_KEYS_TRACK_OPTIONS, CURSOR_KEYS_TRACK_OPTIONS[2]);
        cursorKeysTrackShiftedSetting.addValueObserver (value -> this.cursorKeysTrackShiftedOption = lookupIndex (CURSOR_KEYS_TRACK_OPTIONS, value));

        final IEnumSetting cursorKeysSceneSetting = settingsUI.getEnumSetting ("Cursor Keys Scene Option", CATEGORY_WORKFLOW, CURSOR_KEYS_SCENE_OPTIONS, CURSOR_KEYS_SCENE_OPTIONS[1]);
        cursorKeysSceneSetting.addValueObserver (value -> this.cursorKeysSceneOption = lookupIndex (CURSOR_KEYS_SCENE_OPTIONS, value));
        final IEnumSetting cursorKeysSceneShiftedSetting = settingsUI.getEnumSetting ("Shifted Cursor Keys Scene Option", CATEGORY_WORKFLOW, CURSOR_KEYS_SCENE_OPTIONS, CURSOR_KEYS_SCENE_OPTIONS[0]);
        cursorKeysSceneShiftedSetting.addValueObserver (value -> this.cursorKeysSceneShiftedOption = lookupIndex (CURSOR_KEYS_SCENE_OPTIONS, value));
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


    /**
     * Activate the color settings for the Push 2 display.
     *
     * @param settingsUI The settings
     */
    private void activatePush2DisplayColorsSettings (final ISettingsUI settingsUI)
    {
        settingsUI.getSignalSetting ("Reset colors to default", CATEGORY_COLORS, "Reset").addSignalObserver (value -> {
            this.colorBackgroundSetting.set (DEFAULT_COLOR_BACKGROUND);
            this.colorBackgroundDarkerSetting.set (DEFAULT_COLOR_BACKGROUND_DARKER);
            this.colorBackgroundLighterSetting.set (DEFAULT_COLOR_BACKGROUND_LIGHTER);
            this.colorBorderSetting.set (DEFAULT_COLOR_BORDER);
            this.colorTextSetting.set (DEFAULT_COLOR_TEXT);
            this.colorFaderSetting.set (DEFAULT_COLOR_FADER);
            this.colorVUSetting.set (DEFAULT_COLOR_VU);
            this.colorEditSetting.set (DEFAULT_COLOR_EDIT);
            this.colorRecordSetting.set (DEFAULT_COLOR_RECORD);
            this.colorSoloSetting.set (DEFAULT_COLOR_SOLO);
            this.colorMuteSetting.set (DEFAULT_COLOR_MUTE);
        });

        this.colorBackgroundSetting = settingsUI.getColorSetting ("Background", CATEGORY_COLORS, DEFAULT_COLOR_BACKGROUND);
        this.colorBackgroundSetting.addValueObserver (color -> {
            this.colorBackground = color;
            this.notifyObservers (COLOR_BACKGROUND);
        });

        this.colorBackgroundDarkerSetting = settingsUI.getColorSetting ("Background Darker", CATEGORY_COLORS, DEFAULT_COLOR_BACKGROUND_DARKER);
        this.colorBackgroundDarkerSetting.addValueObserver (color -> {
            this.colorBackgroundDarker = color;
            this.notifyObservers (COLOR_BACKGROUND_DARKER);
        });

        this.colorBackgroundLighterSetting = settingsUI.getColorSetting ("Background Selected", CATEGORY_COLORS, DEFAULT_COLOR_BACKGROUND_LIGHTER);
        this.colorBackgroundLighterSetting.addValueObserver (color -> {
            this.colorBackgroundLighter = color;
            this.notifyObservers (COLOR_BACKGROUND_LIGHTER);
        });

        this.colorBorderSetting = settingsUI.getColorSetting ("Border", CATEGORY_COLORS, DEFAULT_COLOR_BORDER);
        this.colorBorderSetting.addValueObserver (color -> {
            this.colorBorder = color;
            this.notifyObservers (COLOR_BORDER);
        });

        this.colorTextSetting = settingsUI.getColorSetting ("Text", CATEGORY_COLORS, DEFAULT_COLOR_TEXT);
        this.colorTextSetting.addValueObserver (color -> {
            this.colorText = color;
            this.notifyObservers (COLOR_TEXT);
        });

        this.colorFaderSetting = settingsUI.getColorSetting ("Fader", CATEGORY_COLORS, DEFAULT_COLOR_FADER);
        this.colorFaderSetting.addValueObserver (color -> {
            this.colorFader = color;
            this.notifyObservers (COLOR_FADER);
        });

        this.colorVUSetting = settingsUI.getColorSetting ("VU", CATEGORY_COLORS, DEFAULT_COLOR_VU);
        this.colorVUSetting.addValueObserver (color -> {
            this.colorVU = color;
            this.notifyObservers (COLOR_VU);
        });

        this.colorEditSetting = settingsUI.getColorSetting ("Edit", CATEGORY_COLORS, DEFAULT_COLOR_EDIT);
        this.colorEditSetting.addValueObserver (color -> {
            this.colorEdit = color;
            this.notifyObservers (COLOR_EDIT);
        });

        this.colorRecordSetting = settingsUI.getColorSetting ("Record", CATEGORY_COLORS, DEFAULT_COLOR_RECORD);
        this.colorRecordSetting.addValueObserver (color -> {
            this.colorRecord = color;
            this.notifyObservers (COLOR_RECORD);
        });

        this.colorSoloSetting = settingsUI.getColorSetting ("Solo", CATEGORY_COLORS, DEFAULT_COLOR_SOLO);
        this.colorSoloSetting.addValueObserver (color -> {
            this.colorSolo = color;
            this.notifyObservers (COLOR_SOLO);
        });

        this.colorMuteSetting = settingsUI.getColorSetting ("Mute", CATEGORY_COLORS, DEFAULT_COLOR_MUTE);
        this.colorMuteSetting.addValueObserver (color -> {
            this.colorMute = color;
            this.notifyObservers (COLOR_MUTE);
        });
    }


    /**
     * Activate the debug settings.
     *
     * @param settingsUI The settings
     */
    private void activateDebugSettings (final ISettingsUI settingsUI)
    {
        final String [] modes = new String [DEBUG_MODES.size ()];
        int i = 0;
        for (final Modes mode: DEBUG_MODES)
        {
            modes[i] = mode.toString ();
            i++;
        }

        this.debugModeSetting = settingsUI.getEnumSetting ("Display Mode", CATEGORY_DEBUG, modes, Modes.TRACK.toString ());
        this.debugModeSetting.addValueObserver (value -> {
            try
            {
                this.debugMode = Modes.valueOf (value);
            }
            catch (final IllegalArgumentException ex)
            {
                this.debugMode = Modes.TRACK;
            }
            this.notifyObservers (DEBUG_MODE);
        });

        settingsUI.getSignalSetting (" ", CATEGORY_DEBUG, "Display window").addSignalObserver (value -> this.notifyObservers (DEBUG_WINDOW));
    }
}
