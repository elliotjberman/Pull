// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.api;

import de.mossgrabers.pull.core.api.output.RgbColor;
import java.util.List;
import java.util.Objects;

/** Bounded observations for frozen Device/channel handlers. No layout or actuator authority. */
public record DevicePageState (Kind kind, Device device, List<Channel> channels, Channel selectedChannel,
                               List<Parameter> parameters, List<Send> sends, Selection selection, String parameterOwnerId)
    implements ControllerPageDisplayState
{
    public enum Kind { NONE, PARAMETERS, CHAINS, LAYER, LAYER_VOLUME, LAYER_PAN, LAYER_SEND, TRACK_DETAILS, LAYER_DETAILS, CROSSFADE }

    public DevicePageState
    {
        Objects.requireNonNull (kind, "kind");
        Objects.requireNonNull (device, "device");
        channels = window (channels);
        Objects.requireNonNull (selectedChannel, "selectedChannel");
        parameters = window (parameters);
        sends = window (sends);
        Objects.requireNonNull (selection, "selection");
        parameterOwnerId = text (parameterOwnerId);
    }

    public DevicePageState (final Kind kind, final Device device, final List<Channel> channels, final Channel selectedChannel,
                            final List<Parameter> parameters, final List<Send> sends, final Selection selection)
    {
        this (kind, device, channels, selectedChannel, parameters, sends, selection, "");
    }

    /** Attach only the currently source-aligned opaque parameter lease; this is not a device ID. */
    public DevicePageState withParameterOwner (final String owner)
    {
        return new DevicePageState (this.kind, this.device, this.channels, this.selectedChannel, this.parameters, this.sends, this.selection, owner);
    }

    /** Legacy local selection and physical observations, not successful host writes. */
    public record Selection (boolean showDevices, boolean drumPadBank,
                             boolean bankHasItems, boolean bankAligned, int bankOffset, int sendIndex,
                             int mixSendOffset, boolean shift, boolean knobEightTouched,
                             boolean trackPinned, int midiEditChannel, String actionTargetId)
    {
        public Selection
        {
            actionTargetId = text (actionTargetId);
            if (bankOffset < 0 || bankOffset > 56 || sendIndex < 0 || sendIndex > 7 ||
                mixSendOffset < 0 || mixSendOffset > 4 || midiEditChannel < 0 || midiEditChannel > 15)
                throw new IllegalArgumentException ("Invalid observed selection");
        }
        public static Selection empty () { return new Selection (false, false, false, true, 0, 0, 0, false, false, false, 0, ""); }
    }

    public record Device (boolean exists, String name, boolean enabled, boolean expanded,
                          boolean parameterSectionVisible, boolean pinned, boolean windowOpen,
                          boolean hasLayers, boolean hasDrumPads, int selectedSibling,
                          List<String> siblings, List<String> parameterPages, int selectedPage, List<String> chains)
    {
        public Device
        {
            name = text (name);
            siblings = names (siblings);
            parameterPages = names (parameterPages);
            chains = names (chains);
        }
        public static Device empty () { return new Device (false, "", false, false, false, false, false, false, false, -1, List.of (), List.of (), -1, List.of ()); }
    }

    public record Channel (boolean exists, String id, String name, String type, RgbColor color,
                           boolean selected, boolean active, boolean muted, boolean soloed,
                           boolean armed, boolean monitor, boolean autoMonitor, boolean groupExpanded,
                           double vuLeft, double vuRight)
    {
        public Channel
        {
            id = text (id); name = text (name); type = text (type);
            Objects.requireNonNull (color, "color");
            normalized (vuLeft); normalized (vuRight);
        }
        public static Channel empty () { return new Channel (false, "", "", "UNKNOWN", new RgbColor (0, 0, 0), false, false, false, false, false, false, false, false, 0, 0); }
    }

    /** Native host parameter value text is retained whole, with only a transport capacity bound. */
    public record Parameter (boolean exists, String name, double value, double modulatedValue,
                             String displayedValue, boolean enabled, boolean touched)
    {
        public Parameter
        {
            name = text (name); displayedValue = text (displayedValue);
            normalized (value);
            if (modulatedValue != -1) normalized (modulatedValue);
        }
        public static Parameter empty () { return new Parameter (false, "", 0, -1, "", false, false); }
    }

    public record Send (boolean exists, String name, int position, boolean enabled)
    {
        public Send { name = text (name); }
    }

    public static DevicePageState empty () { return new DevicePageState (Kind.NONE, Device.empty (), List.of (), Channel.empty (), List.of (), List.of (), Selection.empty ()); }

    private static <T> List<T> window (final List<T> values)
    {
        final List<T> result = List.copyOf (values);
        if (result.size () > 8) throw new IllegalArgumentException ("Observed window exceeds eight slots");
        return result;
    }

    private static List<String> names (final List<String> values) { return window (values).stream ().map (DevicePageState::text).toList (); }
    private static String text (final String value)
    {
        Objects.requireNonNull (value, "text");
        if (value.length () > 1024) throw new IllegalArgumentException ("Observed text exceeds transport capacity");
        return value;
    }
    private static void normalized (final double value)
    {
        if (!Double.isFinite (value) || value < 0 || value > 1) throw new IllegalArgumentException ("Expected normalized observation");
    }
}
