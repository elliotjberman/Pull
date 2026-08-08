// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2017-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.controller.ableton.push.controller;

import java.util.HashSet;
import java.util.Set;

import de.mossgrabers.framework.controller.ButtonID;
import de.mossgrabers.framework.controller.color.ColorEx;
import de.mossgrabers.framework.controller.color.ColorIndexException;
import de.mossgrabers.framework.controller.color.ColorManager;
import de.mossgrabers.framework.controller.grid.IPadGrid;
import de.mossgrabers.framework.daw.DAWColor;
import de.mossgrabers.framework.featuregroup.AbstractFeatureGroup;
import de.mossgrabers.framework.featuregroup.AbstractMode;
import de.mossgrabers.framework.scale.Scales;
import de.mossgrabers.framework.view.AbstractPlayView;
import de.mossgrabers.framework.view.AbstractSessionView;
import de.mossgrabers.framework.view.sequencer.AbstractDrumView;
import de.mossgrabers.framework.view.sequencer.AbstractSequencerView;
import de.mossgrabers.framework.view.sequencer.ClipLengthView;


/**
 * Different colors to use for the pads and buttons of Push 2.
 *
 * @author Jürgen Moßgraber
 */
@SuppressWarnings("javadoc")
public class PushColorManager extends ColorManager
{
    /** First palette entry reserved for Bitwig colors. */
    public static final int            DAW_COLOR_FIRST                        = 70;
    /** Last palette entry reserved for Bitwig colors. */
    public static final int            DAW_COLOR_LAST                         = 96;

    /** ID for color when button signals a recording state. */
    public static final String         PUSH_BUTTON_STATE_REC_ON               = "PUSH_BUTTON_STATE_REC_ON";
    /** ID for color when button signals an activated recording state. */
    public static final String         PUSH_BUTTON_STATE_REC_HI               = "PUSH_BUTTON_STATE_REC_HI";
    /** ID for color when button signals an overwrite state. */
    public static final String         PUSH_BUTTON_STATE_OVR_ON               = "PUSH_BUTTON_STATE_OVR_ON";
    /** ID for color when button signals an activated overwrite state. */
    public static final String         PUSH_BUTTON_STATE_OVR_HI               = "PUSH_BUTTON_STATE_OVR_HI";
    /** ID for color when button signals a play state. */
    public static final String         PUSH_BUTTON_STATE_PLAY_ON              = "PUSH_BUTTON_STATE_PLAY_ON";
    /** ID for color when button signals an activated play state. */
    public static final String         PUSH_BUTTON_STATE_PLAY_HI              = "PUSH_BUTTON_STATE_PLAY_HI";
    /** ID for color when button signals a mute state. */
    public static final String         PUSH_BUTTON_STATE_MUTE_ON              = "PUSH_BUTTON_STATE_MUTE_ON";
    /** ID for color when button signals an activated mute state. */
    public static final String         PUSH_BUTTON_STATE_MUTE_HI              = "PUSH_BUTTON_STATE_MUTE_HI";
    /** ID for color when button signals a solo state. */
    public static final String         PUSH_BUTTON_STATE_SOLO_ON              = "PUSH_BUTTON_STATE_SOLO_ON";
    /** ID for color when button signals an activated solo state. */
    public static final String         PUSH_BUTTON_STATE_SOLO_HI              = "PUSH_BUTTON_STATE_SOLO_HI";
    /** ID for color when button signals a stop clip state. */
    public static final String         PUSH_BUTTON_STATE_STOP_ON              = "PUSH_BUTTON_STATE_STOP_ON";
    /** ID for color when button signals an activated stop clip state. */
    public static final String         PUSH_BUTTON_STATE_STOP_HI              = "PUSH_BUTTON_STATE_STOP_HI";

    /** ID for the color to use for note repeat resolution. */
    public static final String         NOTE_REPEAT_PERIOD_OFF                 = "NOTE_REPEAT_PERIOD_OFF";
    /** ID for the color to use for note repeat resolution selected. */
    public static final String         NOTE_REPEAT_PERIOD_HI                  = "NOTE_REPEAT_PERIOD_HI";
    /** ID for the color to use for note repeat length. */
    public static final String         NOTE_REPEAT_LENGTH_OFF                 = "NOTE_REPEAT_LENGTH_OFF";
    /** ID for the color to use for note repeat length selected. */
    public static final String         NOTE_REPEAT_LENGTH_HI                  = "NOTE_REPEAT_LENGTH_HI";

    /** ID for color when button signals a master state. */
    public static final String         PUSH_BUTTON_STATE_MASTER_ON            = "PUSH_BUTTON_STATE_MASTER_ON";
    /** ID for color when button signals an activated master state. */
    public static final String         PUSH_BUTTON_STATE_MASTER_HI            = "PUSH_BUTTON_STATE_MASTER_HI";

    /** Selected logical palette. The hardware calibration is applied only when the RGB values are sent. */
    private static final int [] [] TARGET_PALETTE = PushPaletteData.COLORS;

    private static final double [] [] OKLAB_PALETTE = createOKLabPalette ();

    /** Dynamic Bitwig black uses a dim visible neutral; index 0 remains semantic off. */
    private static final int         VISIBLE_BLACK_INDEX                    = 1;

    // Second row & Pad button colors
    public static final int            PUSH2_COLOR2_BLACK                     = 0;
    public static final int            PUSH2_COLOR2_GREY_LO                   = 1;
    public static final int            PUSH2_COLOR2_GREY_MD                   = 103;
    public static final int            PUSH2_COLOR2_GREY_LT                   = 2;
    public static final int            PUSH2_COLOR2_WHITE                     = 3;
    public static final int            PUSH2_COLOR2_ROSE                      = 4;
    public static final int            PUSH2_COLOR2_RED_HI                    = 5;
    public static final int            PUSH2_COLOR2_RED                       = 6;
    public static final int            PUSH2_COLOR2_RED_LO                    = 7;
    public static final int            PUSH2_COLOR2_RED_AMBER                 = 8;
    public static final int            PUSH2_COLOR2_AMBER_HI                  = 9;
    public static final int            PUSH2_COLOR2_AMBER                     = 10;
    public static final int            PUSH2_COLOR2_AMBER_LO                  = 11;
    public static final int            PUSH2_COLOR2_AMBER_YELLOW              = 12;
    public static final int            PUSH2_COLOR2_YELLOW_HI                 = 13;
    public static final int            PUSH2_COLOR2_YELLOW                    = 14;
    public static final int            PUSH2_COLOR2_YELLOW_LO                 = 15;
    public static final int            PUSH2_COLOR2_YELLOW_LIME               = 16;
    public static final int            PUSH2_COLOR2_LIME_HI                   = 17;
    public static final int            PUSH2_COLOR2_LIME                      = 18;
    public static final int            PUSH2_COLOR2_LIME_LO                   = 19;
    public static final int            PUSH2_COLOR2_LIME_GREEN                = 20;
    public static final int            PUSH2_COLOR2_GREEN_HI                  = 21;
    public static final int            PUSH2_COLOR2_GREEN                     = 22;
    public static final int            PUSH2_COLOR2_GREEN_LO                  = 23;
    public static final int            PUSH2_COLOR2_GREEN_SPRING              = 24;
    public static final int            PUSH2_COLOR2_SPRING_HI                 = 25;
    public static final int            PUSH2_COLOR2_SPRING                    = 26;
    public static final int            PUSH2_COLOR2_SPRING_LO                 = 27;

    public static final int            PUSH2_COLOR2_SPRING_TURQUOISE          = 28;
    public static final int            PUSH2_COLOR2_TURQUOISE_LO              = 29;
    public static final int            PUSH2_COLOR2_TURQUOISE                 = 30;
    public static final int            PUSH2_COLOR2_TURQUOISE_HI              = 31;
    public static final int            PUSH2_COLOR2_TURQUOISE_CYAN            = 32;
    public static final int            PUSH2_COLOR2_CYAN_HI                   = 33;
    public static final int            PUSH2_COLOR2_CYAN                      = 34;
    public static final int            PUSH2_COLOR2_CYAN_LO                   = 35;
    public static final int            PUSH2_COLOR2_CYAN_SKY                  = 36;
    public static final int            PUSH2_COLOR2_SKY_HI                    = 37;
    public static final int            PUSH2_COLOR2_SKY                       = 38;
    public static final int            PUSH2_COLOR2_SKY_LO                    = 39;
    public static final int            PUSH2_COLOR2_SKY_OCEAN                 = 40;
    public static final int            PUSH2_COLOR2_OCEAN_HI                  = 41;
    public static final int            PUSH2_COLOR2_OCEAN                     = 42;
    public static final int            PUSH2_COLOR2_OCEAN_LO                  = 43;
    public static final int            PUSH2_COLOR2_OCEAN_BLUE                = 44;
    public static final int            PUSH2_COLOR2_BLUE_HI                   = 45;
    public static final int            PUSH2_COLOR2_BLUE                      = 46;
    public static final int            PUSH2_COLOR2_BLUE_LO                   = 47;
    public static final int            PUSH2_COLOR2_BLUE_ORCHID               = 48;
    public static final int            PUSH2_COLOR2_ORCHID_HI                 = 49;
    public static final int            PUSH2_COLOR2_ORCHID                    = 50;
    public static final int            PUSH2_COLOR2_ORCHID_LO                 = 51;
    public static final int            PUSH2_COLOR2_ORCHID_MAGENTA            = 52;
    public static final int            PUSH2_COLOR2_MAGENTA_HI                = 53;
    public static final int            PUSH2_COLOR2_MAGENTA                   = 54;
    public static final int            PUSH2_COLOR2_MAGENTA_LO                = 55;
    public static final int            PUSH2_COLOR2_MAGENTA_PINK              = 56;
    public static final int            PUSH2_COLOR2_PINK_HI                   = 57;
    public static final int            PUSH2_COLOR2_PINK                      = 58;
    public static final int            PUSH2_COLOR2_PINK_LO                   = 59;
    public static final int            PUSH2_COLOR2_SILVER                    = 118;
    public static final int            PUSH2_COLOR2_ORANGE                    = 65;
    public static final int            PUSH2_COLOR2_ORANGE_LIGHT              = 3;
    public static final int            PUSH2_COLOR2_LIGHT_BROWN               = 69;

    /** Dark red used for an empty record-armed Session slot. */
    public static final int            PUSH2_COLOR2_RECORD_ARMED_DIM          = 10;
    /** Calibrated medium yellow used where the legacy low-yellow slot is too close to off. */
    public static final int            PUSH2_COLOR2_YELLOW_DIM_VISIBLE        = 62;

    // First row colors
    public static final int            PUSH2_COLOR_BLACK                      = 0;
    public static final int            PUSH2_COLOR_RED_LO                     = PUSH2_COLOR2_RED_LO;
    public static final int            PUSH2_COLOR_RED_LO_SBLINK              = 2;
    public static final int            PUSH2_COLOR_RED_LO_FBLINK              = 3;
    public static final int            PUSH2_COLOR_RED_HI                     = PUSH2_COLOR2_RED_HI;
    public static final int            PUSH2_COLOR_RED_HI_SBLINK              = 5;
    public static final int            PUSH2_COLOR_RED_HI_FBLINK              = 6;
    public static final int            PUSH2_COLOR_ORANGE_LO                  = PUSH2_COLOR2_AMBER_LO;
    public static final int            PUSH2_COLOR_ORANGE_LO_SBLINK           = 8;
    public static final int            PUSH2_COLOR_ORANGE_LO_FBLINK           = 9;
    public static final int            PUSH2_COLOR_ORANGE_HI                  = PUSH2_COLOR2_AMBER_HI;
    public static final int            PUSH2_COLOR_ORANGE_HI_SBLINK           = 11;
    public static final int            PUSH2_COLOR_ORANGE_HI_FBLINK           = 12;
    public static final int            PUSH2_COLOR_YELLOW_LO                  = PUSH2_COLOR2_YELLOW_LO;
    public static final int            PUSH2_COLOR_YELLOW_LO_SBLINK           = 14;
    public static final int            PUSH2_COLOR_YELLOW_LO_FBLINK           = 15;
    public static final int            PUSH2_COLOR_YELLOW_MD                  = PUSH2_COLOR2_YELLOW_HI;
    public static final int            PUSH2_COLOR_YELLOW_MD_SBLINK           = 17;
    public static final int            PUSH2_COLOR_YELLOW_MD_FBLINK           = 18;
    public static final int            PUSH2_COLOR_GREEN_LO                   = PUSH2_COLOR2_GREEN_LO;
    public static final int            PUSH2_COLOR_GREEN_LO_SBLINK            = 20;
    public static final int            PUSH2_COLOR_GREEN_LO_FBLINK            = 21;
    public static final int            PUSH2_COLOR_GREEN_HI                   = PUSH2_COLOR2_GREEN_HI;
    public static final int            PUSH2_COLOR_GREEN_HI_SBLINK            = 23;
    public static final int            PUSH2_COLOR_GREEN_HI_FBLINK            = 24;

    // Scene button colors
    public static final int            PUSH2_COLOR_SCENE_RED                  = PUSH2_COLOR2_RED;
    public static final int            PUSH2_COLOR_SCENE_RED_BLINK            = 2;
    public static final int            PUSH2_COLOR_SCENE_RED_BLINK_FAST       = 3;
    public static final int            PUSH2_COLOR_SCENE_RED_HI               = PUSH2_COLOR2_RED_HI;
    public static final int            PUSH2_COLOR_SCENE_RED_HI_BLINK         = 5;
    public static final int            PUSH2_COLOR_SCENE_RED_HI_BLINK_FAST    = 6;
    public static final int            PUSH2_COLOR_SCENE_ORANGE               = PUSH2_COLOR2_AMBER;
    public static final int            PUSH2_COLOR_SCENE_ORANGE_BLINK         = 8;
    public static final int            PUSH2_COLOR_SCENE_ORANGE_BLINK_FAST    = 9;
    public static final int            PUSH2_COLOR_SCENE_ORANGE_HI            = PUSH2_COLOR2_AMBER_HI;
    public static final int            PUSH2_COLOR_SCENE_ORANGE_HI_BLINK      = 11;
    public static final int            PUSH2_COLOR_SCENE_ORANGE_HI_BLINK_FAST = 12;
    public static final int            PUSH2_COLOR_SCENE_YELLOW               = PUSH2_COLOR2_YELLOW;
    public static final int            PUSH2_COLOR_SCENE_YELLOW_BLINK         = 14;
    public static final int            PUSH2_COLOR_SCENE_YELLOW_BLINK_FAST    = 15;
    public static final int            PUSH2_COLOR_SCENE_YELLOW_HI            = PUSH2_COLOR2_YELLOW_HI;
    public static final int            PUSH2_COLOR_SCENE_YELLOW_HI_BLINK      = 17;
    public static final int            PUSH2_COLOR_SCENE_YELLOW_HI_BLINK_FAST = 18;
    public static final int            PUSH2_COLOR_SCENE_GREEN                = PUSH2_COLOR2_GREEN;
    public static final int            PUSH2_COLOR_SCENE_GREEN_BLINK          = 20;
    public static final int            PUSH2_COLOR_SCENE_GREEN_BLINK_FAST     = 21;
    public static final int            PUSH2_COLOR_SCENE_GREEN_HI             = PUSH2_COLOR2_GREEN_HI;
    public static final int            PUSH2_COLOR_SCENE_GREEN_HI_BLINK       = 23;
    public static final int            PUSH2_COLOR_SCENE_GREEN_HI_BLINK_FAST  = 24;
    public static final int            PUSH2_COLOR_SCENE_WHITE                = 60;

    public static final String         PUSH_BLACK                             = "PUSH_BLACK";
    public static final String         PUSH_RED                               = "PUSH_RED";
    public static final String         PUSH_RED_LO                            = "PUSH_RED_LO";
    public static final String         PUSH_RED_HI                            = "PUSH_RED_HI";
    public static final String         PUSH_ORANGE_LO                         = "PUSH_ORANGE_LO";
    public static final String         PUSH_ORANGE_HI                         = "PUSH_ORANGE_HI";
    public static final String         PUSH_YELLOW_LO                         = "PUSH_YELLOW_LO";
    public static final String         PUSH_YELLOW_MD                         = "PUSH_YELLOW_MD";
    public static final String         PUSH_GREEN_LO                          = "PUSH_GREEN_LO";
    public static final String         PUSH_GREEN_HI                          = "PUSH_GREEN_HI";

    public static final String         PUSH_BLACK_2                           = "PUSH_BLACK_2";
    public static final String         PUSH_WHITE_2                           = "PUSH_WHITE_2";
    public static final String         PUSH_GREY_LO_2                         = "PUSH_GREY_LO_2";
    public static final String         PUSH_GREEN_2                           = "PUSH_GREEN_2";

    private static final Set<ButtonID> MONOCHROME_BUTTONS                     = new HashSet<> ();
    static
    {
        MONOCHROME_BUTTONS.add (ButtonID.ACCENT);
        MONOCHROME_BUTTONS.add (ButtonID.ADD_EFFECT);
        MONOCHROME_BUTTONS.add (ButtonID.ADD_TRACK);
        MONOCHROME_BUTTONS.add (ButtonID.ARROW_DOWN);
        MONOCHROME_BUTTONS.add (ButtonID.ARROW_LEFT);
        MONOCHROME_BUTTONS.add (ButtonID.ARROW_RIGHT);
        MONOCHROME_BUTTONS.add (ButtonID.ARROW_UP);
        MONOCHROME_BUTTONS.add (ButtonID.BROWSE);
        MONOCHROME_BUTTONS.add (ButtonID.CLIP);
        MONOCHROME_BUTTONS.add (ButtonID.DELETE);
        MONOCHROME_BUTTONS.add (ButtonID.DEVICE);
        MONOCHROME_BUTTONS.add (ButtonID.DOUBLE);
        MONOCHROME_BUTTONS.add (ButtonID.DUPLICATE);
        MONOCHROME_BUTTONS.add (ButtonID.FIXED_LENGTH);
        MONOCHROME_BUTTONS.add (ButtonID.LAYOUT);
        MONOCHROME_BUTTONS.add (ButtonID.MASTERTRACK);
        MONOCHROME_BUTTONS.add (ButtonID.METRONOME);
        MONOCHROME_BUTTONS.add (ButtonID.NEW);
        MONOCHROME_BUTTONS.add (ButtonID.NOTE);
        MONOCHROME_BUTTONS.add (ButtonID.OCTAVE_DOWN);
        MONOCHROME_BUTTONS.add (ButtonID.OCTAVE_UP);
        MONOCHROME_BUTTONS.add (ButtonID.PAGE_LEFT);
        MONOCHROME_BUTTONS.add (ButtonID.PAGE_RIGHT);
        MONOCHROME_BUTTONS.add (ButtonID.QUANTIZE);
        MONOCHROME_BUTTONS.add (ButtonID.REPEAT);
        MONOCHROME_BUTTONS.add (ButtonID.SCALES);
        MONOCHROME_BUTTONS.add (ButtonID.SELECT);
        MONOCHROME_BUTTONS.add (ButtonID.SESSION);
        MONOCHROME_BUTTONS.add (ButtonID.SETUP);
        MONOCHROME_BUTTONS.add (ButtonID.SHIFT);
        MONOCHROME_BUTTONS.add (ButtonID.TAP_TEMPO);
        MONOCHROME_BUTTONS.add (ButtonID.TRACK);
        MONOCHROME_BUTTONS.add (ButtonID.UNDO);
        MONOCHROME_BUTTONS.add (ButtonID.USER);
    }

    /**
     * Constructor.
     */
    public PushColorManager ()
    {
        this.registerColorIndex (PUSH_BLACK, PUSH2_COLOR_BLACK);
        this.registerColorIndex (PUSH_RED, PUSH2_COLOR_RED_HI);
        this.registerColorIndex (PUSH_RED_LO, PUSH2_COLOR_RED_LO);
        this.registerColorIndex (PUSH_RED_HI, PUSH2_COLOR2_PINK_HI);
        this.registerColorIndex (PUSH_ORANGE_LO, PUSH2_COLOR_ORANGE_LO);
        this.registerColorIndex (PUSH_ORANGE_HI, PUSH2_COLOR_ORANGE_HI);
        this.registerColorIndex (PUSH_YELLOW_LO, PUSH2_COLOR_YELLOW_LO);
        this.registerColorIndex (PUSH_YELLOW_MD, PUSH2_COLOR_YELLOW_MD);
        this.registerColorIndex (PUSH_GREEN_LO, PUSH2_COLOR_GREEN_LO);
        this.registerColorIndex (PUSH_GREEN_HI, PUSH2_COLOR_GREEN_HI);

        this.registerColorIndex (PUSH_BLACK_2, PUSH2_COLOR2_BLACK);
        this.registerColorIndex (PUSH_WHITE_2, PUSH2_COLOR2_WHITE);
        this.registerColorIndex (PUSH_GREY_LO_2, PUSH2_COLOR2_GREY_LO);
        this.registerColorIndex (PUSH_GREEN_2, PUSH2_COLOR2_GREEN);

        this.registerColorIndex (Scales.SCALE_COLOR_OFF, PUSH2_COLOR2_BLACK);
        this.registerColorIndex (Scales.SCALE_COLOR_OCTAVE, PUSH2_COLOR2_OCEAN_HI);
        this.registerColorIndex (Scales.SCALE_COLOR_NOTE, PUSH2_COLOR2_WHITE);
        this.registerColorIndex (Scales.SCALE_COLOR_OUT_OF_SCALE, PUSH2_COLOR_BLACK);

        this.registerColorIndex (AbstractFeatureGroup.BUTTON_COLOR_OFF, PUSH2_COLOR_BLACK);
        this.registerColorIndex (AbstractFeatureGroup.BUTTON_COLOR_ON, PUSH2_COLOR2_GREY_LO);
        this.registerColorIndex (AbstractMode.BUTTON_COLOR_HI, PUSH2_COLOR2_WHITE);
        this.registerColorIndex (AbstractMode.BUTTON_COLOR2_ON, PUSH2_COLOR2_GREY_LO);
        this.registerColorIndex (AbstractMode.BUTTON_COLOR2_HI, PUSH2_COLOR2_WHITE);

        this.registerColorIndex (AbstractSequencerView.COLOR_NO_CONTENT, PUSH2_COLOR2_BLACK);
        this.registerColorIndex (AbstractSequencerView.COLOR_NO_CONTENT_4, PUSH2_COLOR2_BLACK);
        this.registerColorIndex (AbstractSequencerView.COLOR_CONTENT, PUSH2_COLOR2_BLUE_HI);
        this.registerColorIndex (AbstractSequencerView.COLOR_CONTENT_CONT, PUSH2_COLOR2_BLUE_LO);
        this.registerColorIndex (AbstractSequencerView.COLOR_STEP_HILITE_NO_CONTENT, PUSH2_COLOR2_GREEN_LO);
        this.registerColorIndex (AbstractSequencerView.COLOR_STEP_HILITE_CONTENT, PUSH2_COLOR2_GREEN_HI);
        this.registerColorIndex (AbstractSequencerView.COLOR_STEP_MUTED, PUSH2_COLOR2_GREY_MD);
        this.registerColorIndex (AbstractSequencerView.COLOR_STEP_MUTED_CONT, PUSH2_COLOR2_GREY_LO);
        this.registerColorIndex (AbstractSequencerView.COLOR_STEP_SELECTED, PUSH2_COLOR2_YELLOW_HI);
        this.registerColorIndex (AbstractSequencerView.COLOR_PAGE, PUSH2_COLOR2_WHITE);
        this.registerColorIndex (AbstractSequencerView.COLOR_ACTIVE_PAGE, PUSH2_COLOR2_GREEN);
        this.registerColorIndex (AbstractSequencerView.COLOR_SELECTED_PAGE, PUSH2_COLOR2_OCEAN_HI);
        this.registerColorIndex (AbstractSequencerView.COLOR_RESOLUTION, PUSH2_COLOR_SCENE_ORANGE);
        this.registerColorIndex (AbstractSequencerView.COLOR_RESOLUTION_SELECTED, PUSH2_COLOR_SCENE_ORANGE_HI);
        this.registerColorIndex (AbstractSequencerView.COLOR_RESOLUTION_OFF, PUSH2_COLOR2_BLACK);
        this.registerColorIndex (AbstractSequencerView.COLOR_TRANSPOSE, PUSH2_COLOR_SCENE_WHITE);
        this.registerColorIndex (AbstractSequencerView.COLOR_TRANSPOSE_SELECTED, PUSH2_COLOR_SCENE_YELLOW_HI);

        this.registerColorIndex (AbstractDrumView.COLOR_PAD_OFF, PUSH2_COLOR_BLACK);
        this.registerColorIndex (AbstractDrumView.COLOR_PAD_RECORD, PUSH2_COLOR2_PINK_HI);
        this.registerColorIndex (AbstractDrumView.COLOR_PAD_PLAY, PUSH2_COLOR2_GREEN_HI);
        this.registerColorIndex (AbstractDrumView.COLOR_PAD_SELECTED, PUSH2_COLOR2_BLUE_HI);
        this.registerColorIndex (AbstractDrumView.COLOR_PAD_MUTED, PUSH2_COLOR2_AMBER_LO);
        this.registerColorIndex (AbstractDrumView.COLOR_PAD_HAS_CONTENT, PUSH2_COLOR2_YELLOW_HI);
        this.registerColorIndex (AbstractDrumView.COLOR_PAD_NO_CONTENT, PUSH2_COLOR2_YELLOW_LO);

        this.registerColorIndex (AbstractPlayView.COLOR_PLAY, PUSH2_COLOR2_GREEN_HI);
        this.registerColorIndex (AbstractPlayView.COLOR_RECORD, PUSH2_COLOR2_PINK_HI);
        this.registerColorIndex (AbstractPlayView.COLOR_OFF, PUSH2_COLOR2_BLACK);

        this.registerColorIndex (ClipLengthView.COLOR_OUTSIDE, PUSH2_COLOR_BLACK);
        this.registerColorIndex (ClipLengthView.COLOR_PART, PUSH2_COLOR2_OCEAN_HI);

        this.registerColorIndex (AbstractSessionView.COLOR_SCENE, PUSH2_COLOR_SCENE_GREEN);
        this.registerColorIndex (AbstractSessionView.COLOR_SELECTED_SCENE, PUSH2_COLOR_SCENE_GREEN_HI);
        this.registerColorIndex (AbstractSessionView.COLOR_SCENE_OFF, PUSH2_COLOR2_BLACK);


        this.registerColorIndex (IPadGrid.GRID_OFF, PUSH2_COLOR2_BLACK);

        this.registerColorIndex (NOTE_REPEAT_PERIOD_OFF, PUSH2_COLOR_SCENE_YELLOW);
        this.registerColorIndex (NOTE_REPEAT_PERIOD_HI, PUSH2_COLOR_SCENE_YELLOW_HI);
        this.registerColorIndex (NOTE_REPEAT_LENGTH_OFF, PUSH2_COLOR_SCENE_RED);
        this.registerColorIndex (NOTE_REPEAT_LENGTH_HI, PUSH2_COLOR_SCENE_RED_HI);

        // Push 2 DAW colors are set in the color palette from indices 70 to 96
        this.registerColorIndex (DAWColor.COLOR_OFF, PUSH2_COLOR2_BLACK);
        this.registerColorIndex (DAWColor.DAW_COLOR_GRAY_HALF, 70);
        this.registerColorIndex (DAWColor.DAW_COLOR_DARK_GRAY, 71);
        this.registerColorIndex (DAWColor.DAW_COLOR_GRAY, 72);
        this.registerColorIndex (DAWColor.DAW_COLOR_LIGHT_GRAY, 73);
        this.registerColorIndex (DAWColor.DAW_COLOR_SILVER, 74);
        this.registerColorIndex (DAWColor.DAW_COLOR_DARK_BROWN, 75);
        this.registerColorIndex (DAWColor.DAW_COLOR_BROWN, 76);
        this.registerColorIndex (DAWColor.DAW_COLOR_DARK_BLUE, 77);
        this.registerColorIndex (DAWColor.DAW_COLOR_PURPLE_BLUE, 78);
        this.registerColorIndex (DAWColor.DAW_COLOR_PURPLE, 79);
        this.registerColorIndex (DAWColor.DAW_COLOR_PINK, 80);
        this.registerColorIndex (DAWColor.DAW_COLOR_RED, 81);
        this.registerColorIndex (DAWColor.DAW_COLOR_ORANGE, 82);
        this.registerColorIndex (DAWColor.DAW_COLOR_LIGHT_ORANGE, 83);
        this.registerColorIndex (DAWColor.DAW_COLOR_MOSS_GREEN, 84);
        this.registerColorIndex (DAWColor.DAW_COLOR_GREEN, 85);
        this.registerColorIndex (DAWColor.DAW_COLOR_COLD_GREEN, 86);
        this.registerColorIndex (DAWColor.DAW_COLOR_BLUE, 87);
        this.registerColorIndex (DAWColor.DAW_COLOR_LIGHT_PURPLE, 88);
        this.registerColorIndex (DAWColor.DAW_COLOR_LIGHT_PINK, 89);
        this.registerColorIndex (DAWColor.DAW_COLOR_ROSE, 90);
        this.registerColorIndex (DAWColor.DAW_COLOR_REDDISH_BROWN, 91);
        this.registerColorIndex (DAWColor.DAW_COLOR_LIGHT_BROWN, 92);
        this.registerColorIndex (DAWColor.DAW_COLOR_LIGHT_GREEN, 93);
        this.registerColorIndex (DAWColor.DAW_COLOR_BLUISH_GREEN, 94);
        this.registerColorIndex (DAWColor.DAW_COLOR_GREEN_BLUE, 95);
        this.registerColorIndex (DAWColor.DAW_COLOR_LIGHT_BLUE, 96);

        this.registerColorIndex (ColorManager.BUTTON_STATE_OFF, 0);
        this.registerColorIndex (ColorManager.BUTTON_STATE_ON, 30);
        this.registerColorIndex (ColorManager.BUTTON_STATE_HI, 127);
        this.registerColorIndex (PUSH_BUTTON_STATE_REC_ON, PUSH2_COLOR2_GREY_LO);
        this.registerColorIndex (PUSH_BUTTON_STATE_REC_HI, PUSH2_COLOR2_RED_HI);
        this.registerColorIndex (PUSH_BUTTON_STATE_OVR_ON, PUSH2_COLOR2_GREY_LO);
        this.registerColorIndex (PUSH_BUTTON_STATE_OVR_HI, PUSH2_COLOR2_AMBER);
        this.registerColorIndex (PUSH_BUTTON_STATE_PLAY_ON, PUSH2_COLOR2_GREY_LO);
        this.registerColorIndex (PUSH_BUTTON_STATE_PLAY_HI, PUSH2_COLOR2_GREEN_HI);
        this.registerColorIndex (PUSH_BUTTON_STATE_MUTE_ON, PUSH2_COLOR2_GREY_LO);
        this.registerColorIndex (PUSH_BUTTON_STATE_MUTE_HI, PUSH2_COLOR2_AMBER_LO);
        this.registerColorIndex (PUSH_BUTTON_STATE_SOLO_ON, PUSH2_COLOR2_GREY_LO);
        this.registerColorIndex (PUSH_BUTTON_STATE_SOLO_HI, PUSH2_COLOR2_YELLOW);
        this.registerColorIndex (PUSH_BUTTON_STATE_STOP_ON, PUSH2_COLOR2_RED_LO);
        this.registerColorIndex (PUSH_BUTTON_STATE_STOP_HI, PUSH2_COLOR2_RED_HI);

        this.registerColorIndex (PUSH_BUTTON_STATE_MASTER_ON, 30);
        this.registerColorIndex (PUSH_BUTTON_STATE_MASTER_HI, 127);

        for (int i = 0; i < 128; i++)
            this.registerColor (i, getPaletteColor (i));
    }


    /**
     * Get a color entry of the default Push color palette.
     *
     * @param index 0-127
     * @return The palette color as RGB (0-255)
     */
    public static int [] getPaletteColorRGB (final int index)
    {
        if (PushPaletteData.HAS_PROGRAMMED_COLORS)
            return PushPaletteData.PROGRAMMED_COLORS[index].clone ();
        return PushColorCalibration.toLedRGB (getPaletteColor (index).toIntRGB255 ());
    }


    /**
     * Get a color of the default Push color palette.
     *
     * @param index 0-127
     * @return The palette color
     */
    public static ColorEx getPaletteColor (final int index)
    {
        return ColorEx.fromRGB (TARGET_PALETTE[index][0], TARGET_PALETTE[index][1], TARGET_PALETTE[index][2]);
    }


    /**
     * Resolve an RGB color to a Push palette index. Keeping this conversion in the Push color
     * manager lets callers retain the full RGB value until an indexed hardware value is required.
     * Matching uses the logical palette; physical LED calibration stays in
     * {@link #getPaletteColorRGB(int)}.
     *
     * @param color The RGB color
     * @return The closest Push palette index
     */
    @Override
    public int getColorIndex (final ColorEx color)
    {
        if (color.getRed () == 0.0 && color.getGreen () == 0.0 && color.getBlue () == 0.0)
            return VISIBLE_BLACK_INDEX;

        final double [] target = toOKLab (color);
        int closestIndex = 0;
        double closestDistance = Double.MAX_VALUE;
        for (int index = 0; index < OKLAB_PALETTE.length; index++)
        {
            final double [] candidate = OKLAB_PALETTE[index];
            final double deltaL = target[0] - candidate[0];
            final double deltaA = target[1] - candidate[1];
            final double deltaB = target[2] - candidate[2];
            final double distance = deltaL * deltaL + deltaA * deltaA + deltaB * deltaB;
            if (distance < closestDistance)
            {
                closestIndex = index;
                closestDistance = distance;
            }
        }
        return closestIndex;
    }


    private static double [] [] createOKLabPalette ()
    {
        final double [] [] palette = new double [128] [];
        for (int index = 0; index < palette.length; index++)
            palette[index] = toOKLab (getPaletteColor (index));
        return palette;
    }


    private static double [] toOKLab (final ColorEx color)
    {
        final double red = linearize (color.getRed ());
        final double green = linearize (color.getGreen ());
        final double blue = linearize (color.getBlue ());

        final double l = Math.cbrt (0.4122214708 * red + 0.5363325363 * green + 0.0514459929 * blue);
        final double m = Math.cbrt (0.2119034982 * red + 0.6806995451 * green + 0.1073969566 * blue);
        final double s = Math.cbrt (0.0883024619 * red + 0.2817188376 * green + 0.6299787005 * blue);

        return new double []
        {
            0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s,
            1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s,
            0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s
        };
    }


    private static double linearize (final double component)
    {
        final double value = Math.max (0.0, Math.min (1.0, component));
        return value <= 0.04045 ? value / 12.92 : Math.pow ((value + 0.055) / 1.055, 2.4);
    }


    /** {@inheritDoc} */
    @Override
    public ColorEx getColor (final int colorIndex, final ButtonID buttonID)
    {
        if (colorIndex < 0)
            return ColorEx.BLACK;

        if (MONOCHROME_BUTTONS.contains (buttonID))
        {
            if (colorIndex == this.getColorIndex (ColorManager.BUTTON_STATE_OFF))
                return ColorEx.BLACK;
            if (colorIndex == this.getColorIndex (ColorManager.BUTTON_STATE_ON))
                return ColorEx.DARK_GRAY;
            // ColorManager.BUTTON_STATE_HI
            return ColorEx.LIGHT_GRAY;
        }

        final ColorEx color = this.colorByIndex.get (Integer.valueOf (colorIndex));
        if (color == null)
            throw new ColorIndexException ("Color for index " + colorIndex + " is not registered!");
        return color;
    }
}
