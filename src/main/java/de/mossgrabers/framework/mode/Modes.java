// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2017-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.framework.mode;

import java.util.EnumSet;
import java.util.Set;


/**
 * Static mode IDs and some helper functions.
 *
 * @author Jürgen Moßgraber
 */
public enum Modes
{
    /** Single Track editing mode. */
    TRACK,
    /** Edit details of one track. */
    TRACK_DETAILS,
    /** Edit volume of all tracks. */
    VOLUME,
    /** Edit panning of all tracks. */
    PAN,
    /** Edit cross-fader settings of all tracks. */
    CROSSFADER,
    /** Mode to arm tracks for recording. */
    REC_ARM,

    /** Edit Send 1 of all tracks. */
    SEND1,
    /** Edit Send 2 of all tracks. */
    SEND2,
    /** Edit Send 3 of all tracks. */
    SEND3,
    /** Edit Send 4 of all tracks. */
    SEND4,
    /** Edit Send 5 of all tracks. */
    SEND5,
    /** Edit Send 6 of all tracks. */
    SEND6,
    /** Edit Send 7 of all tracks. */
    SEND7,
    /** Edit Send 8 of all tracks. */
    SEND8,
    /** Edit master track. */
    MASTER,
    /** Edit master track (temporary). */
    MASTER_TEMP,

    /** Edit device parameters. */
    DEVICE_PARAMS,
    /** Edit device slot chains. */
    DEVICE_CHAINS,

    /** Edit layer parameters. */
    DEVICE_LAYER,
    /** Edit volume of all layers. */
    DEVICE_LAYER_VOLUME,
    /** Edit panning of all layers. */
    DEVICE_LAYER_PAN,
    /** Edit Send 1 of all layers. */
    DEVICE_LAYER_SEND1,
    /** Edit Send 2 of all layers. */
    DEVICE_LAYER_SEND2,
    /** Edit Send 3 of all layers. */
    DEVICE_LAYER_SEND3,
    /** Edit Send 4 of all layers. */
    DEVICE_LAYER_SEND4,
    /** Edit Send 5 of all layers. */
    DEVICE_LAYER_SEND5,
    /** Edit Send 6 of all layers. */
    DEVICE_LAYER_SEND6,
    /** Edit Send 7 of all layers. */
    DEVICE_LAYER_SEND7,
    /** Edit Send 8 of all layers. */
    DEVICE_LAYER_SEND8,
    /** Edit layer details. */
    DEVICE_LAYER_DETAILS,
    /** Browser mode. */
    BROWSER,

    /** Edit clip parameters. */
    CLIP,
    /** Edit note parameters. */
    NOTE,

    /** Show/hide different frames. */
    FRAME,
    /** Groove edit mode. */
    GROOVE,
    /** Edit accent parameters. */
    ACCENT,
    /** Scale configuration. */
    SCALES,
    /** Scale layout mode. */
    SCALE_LAYOUT,
    /** Pick length of new clips. */
    FIXED,
    /** Edit ribbon parameters. */
    RIBBON,
    /** Edit automation parameters. */
    AUTOMATION,
    /** Transport mode. */
    TRANSPORT,
    /** Setup mode. */
    SETUP,
    /** Info mode. */
    INFO,
    /** Repeat note length mode. */
    REPEAT_NOTE,
    /** A user mode. */
    USER,
    /** A mode to select options for adding a track. */
    ADD_TRACK;


    /** The name of the Volume mode. */
    public static final String      NAME_VOLUME        = "Volume";
    /** The name of the Cross-fade mode. */
    public static final String      NAME_CROSSFADE     = "Crossfade";
    /** The name of the Layer mode. */
    public static final String      NAME_LAYER         = "Layer";
    /** The name of the Layer Volume mode. */
    public static final String      NAME_LAYER_VOLUME  = "Layer Volume";
    /** The name of the Layer Panning mode. */
    public static final String      NAME_LAYER_PANNING = "Layer Panning";
    /** The name of the Layer Sends mode. */
    public static final String      NAME_LAYER_SENDS   = "Layer Sends";

    private static final Set<Modes> TRACK_MODES        = EnumSet.of (TRACK, TRACK_DETAILS, VOLUME, PAN, CROSSFADER, REC_ARM, SEND1, SEND2, SEND3, SEND4, SEND5, SEND6, SEND7, SEND8);
    private static final Set<Modes> LAYER_MODES        = EnumSet.of (DEVICE_LAYER, DEVICE_LAYER_VOLUME, DEVICE_LAYER_PAN, DEVICE_LAYER_SEND1, DEVICE_LAYER_SEND2, DEVICE_LAYER_SEND3, DEVICE_LAYER_SEND4, DEVICE_LAYER_SEND5, DEVICE_LAYER_SEND6, DEVICE_LAYER_SEND7, DEVICE_LAYER_SEND8, DEVICE_LAYER_DETAILS);
    private static final Set<Modes> SEND_MODES         = EnumSet.range (SEND1, SEND8);
    private static final Set<Modes> LAYER_SEND_MODES   = EnumSet.range (DEVICE_LAYER_SEND1, DEVICE_LAYER_SEND8);
    private static final Set<Modes> MIX_MODES          = EnumSet.copyOf (TRACK_MODES);
    private static final Set<Modes> MASTER_MODES       = EnumSet.of (MASTER, MASTER_TEMP, FRAME);


    /**
     * Private due to utility class.
     */
    private Modes ()
    {
        // Intentionally empty
    }


    /**
     * Returns true if the given mode ID is one of the send modes.
     *
     * @param modeId The mode ID to test
     * @return True if it is a send mode
     */
    public static boolean isSendMode (final Modes modeId)
    {
        return SEND_MODES.contains (modeId);
    }


    /**
     * Returns true if the given mode ID is one of the track modes.
     *
     * @param modeId The mode ID to test
     * @return True if it is a track mode
     */
    public static boolean isTrackMode (final Modes modeId)
    {
        return TRACK_MODES.contains (modeId);
    }


    /**
     * Returns true if the given mode ID is one of the mix modes.
     *
     * @param modeId The mode ID to test
     * @return True if it is a mix mode
     */
    public static boolean isMixMode (final Modes modeId)
    {
        return MIX_MODES.contains (modeId);
    }


    /**
     * Returns true if the given mode ID is one of the device layer modes.
     *
     * @param modeId The mode ID to test
     * @return True if it is a device layer mode
     */
    public static boolean isLayerMode (final Modes modeId)
    {
        return LAYER_MODES.contains (modeId);
    }


    /**
     * Returns true if the given mode ID is one of the layer send modes.
     *
     * @param modeId The mode ID to test
     * @return True if it is a layer send mode
     */
    public static boolean isLayerSendMode (final Modes modeId)
    {
        return LAYER_SEND_MODES.contains (modeId);
    }


    /**
     * Returns true if the given mode ID is one of the master modes.
     *
     * @param modeId The mode ID to test
     * @return True if it is a master mode
     */
    public static boolean isMasterMode (final Modes modeId)
    {
        return MASTER_MODES.contains (modeId);
    }


    /**
     * Returns true if the given mode ID is one of the device modes.
     *
     * @param modeId The mode ID to test
     * @return True if it is a device mode
     */
    public static boolean isDeviceMode (final Modes modeId)
    {
        return LAYER_MODES.contains (modeId) || DEVICE_PARAMS == modeId;
    }
    /**
     * Get an offset mode.
     *
     * @param mode The base mode
     * @param offset The offset
     * @return The offset mode
     */
    public static Modes get (final Modes mode, final int offset)
    {
        return Modes.values ()[mode.ordinal () + offset];
    }
}
