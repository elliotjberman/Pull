// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.testing;

import de.mossgrabers.pull.core.api.DevicePageState;
import de.mossgrabers.pull.core.api.DevicePageState.*;
import de.mossgrabers.pull.core.api.output.ControllerDisplayScene;
import de.mossgrabers.pull.core.api.output.RgbColor;
import de.mossgrabers.pull.core.ui.page.DevicePageRenderer;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

/** Offline values for every remaining device/channel image, replayed by the production renderer. */
public final class DevicePageGallery
{
    private DevicePageGallery () { }
    public record Example (String id, String title, String description, DevicePageState state)
    {
        public ControllerDisplayScene display () { return DevicePageRenderer.render (this.state); }
    }

    public static List<Example> examples ()
    {
        final List<Example> result = new ArrayList<> ();
        for (final Kind kind: Kind.values ())
            if (kind != Kind.NONE)
                result.add (new Example ("device-" + kind.name ().toLowerCase (java.util.Locale.ROOT).replace ('_', '-'), title (kind),
                    "Observed values through the core component library. Input handlers and physical row lights remain frozen legacy behavior.", sample (kind)));
        final DevicePageState parameters = sample (Kind.PARAMETERS);
        result.add (new Example ("device-parameter-banks", "Device · parameter banks", "Page names and cursor-device options retain their physical row positions.",
            selection (parameters, new Selection (false, false, false, true, true, 0, 0, 0, false, false, true, 0, ""))));
        final DevicePageState user = sample (Kind.USER);
        result.add (new Example ("user-pinned-track", "User · pinned track controls", "The parameter owner names the pinned model cursor; the current track-bank footer remains an independent selection row.",
            selection (user, new Selection (false, false, false, true, true, 0, 0, 0, false, false, true, 15, ""))));
        final DevicePageState layer = sample (Kind.LAYER_SEND);
        result.add (new Example ("device-layer-shift", "Layer · fourth send with Shift", "Shift or touched knob eight reveals the fourth send in place of Up.",
            selection (layer, new Selection (false, false, true, true, true, 8, 3, 0, true, false, false, 0, ""))));
        result.add (new Example ("device-bank-unavailable", "Device · bank propagation", "A bank mismatch renders a waiting state instead of pairing new parameters with old channel labels.",
            selection (layer, new Selection (false, false, true, true, false, 8, 3, 0, false, false, false, 0, ""))));
        final DevicePageState details = sample (Kind.TRACK_DETAILS);
        result.add (new Example ("track-details-target-mismatch", "Track · target mismatch", "Frozen buttons target the selected bank track or master. Core hides cursor details while those observed identities disagree; physical lights remain frozen migration debt.",
            selection (details, new Selection (false, false, false, true, true, 0, 0, 0, false, false, true, 0, "other-selected-track"))));
        result.add (new Example ("device-absent", "Device · absent", "No device state invents parameters or chain names; the existing Up affordance remains visible.",
            new DevicePageState (Kind.LAYER, Device.empty (), List.of (), Channel.empty (), List.of (), List.of (), Selection.empty ())));
        final DevicePageState longNames = sample (Kind.PARAMETERS);
        result.add (new Example ("device-long-names", "Device · long names and values", "Transport-bounded strings reach core intact; shared text fitting bounds the visible content.",
            new DevicePageState (longNames.kind (), longNames.device (), longNames.channels (), longNames.selectedChannel (),
                IntStream.range (0, 8).mapToObj (index -> new Parameter (true, "Unusually descriptive modulation parameter with a long name ".repeat (8), index / 7.0, -1,
                    "A very long host-formatted value and unit ".repeat (8), true, index == 0)).toList (), longNames.sends (), longNames.selection ())));
        return List.copyOf (result);
    }

    public static DevicePageState sample (final Kind kind)
    {
        final boolean layer = kind == Kind.LAYER || kind == Kind.LAYER_VOLUME || kind == Kind.LAYER_PAN || kind == Kind.LAYER_SEND || kind == Kind.LAYER_DETAILS;
        final List<Channel> channels = IntStream.range (0, 8).mapToObj (index -> new Channel (index != 6, "channel-" + index,
            (layer ? "Layer " : "Track ") + (index + 1), layer ? "LAYER" : index == 0 ? "GROUP" : "INSTRUMENT",
            new RgbColor (40 + index * 20, 160, 220 - index * 15), index == 1, index != 3, index == 3, index == 1, index == 1,
            false, true, index == 0, index / 8.0, (7 - index) / 8.0)).toList ();
        final List<Parameter> parameters = IntStream.range (0, 8).mapToObj (index -> new Parameter (index != 6 && !(kind == Kind.LAYER && (index == 2 || index == 3)),
            "Parameter " + (index + 1), index / 7.0, index == 2 ? 0.8 : -1, kind == Kind.CROSSFADE ? List.of ("A", "AB", "B").get (index % 3) : index * 12 + " %", index != 3, index == 2)).toList ();
        final List<Send> sends = IntStream.range (0, 8).mapToObj (index -> new Send (true, List.of ("Reverb", "Delay", "Chorus", "Echo", "Hall", "Space", "Slap", "Room").get (index), index, index != 2)).toList ();
        final Device device = new Device (true, "Polymer", true, false, true, true, false, true, layer, 1,
            List.of ("Note Grid", "Polymer", "EQ+", "Delay+", "Tool", "", "", ""),
            List.of ("Oscillator", "Filter", "Envelope", "Modulation", "FX", "Macros", "", ""), 2, List.of ("Note FX", "Audio FX", "Feedback"));
        return new DevicePageState (kind, device, channels, channels.get (1), parameters, sends,
            new Selection (true, true, layer, true, true, layer ? 8 : 0, 2, 4, false, false, !layer, 9, kind == Kind.TRACK_DETAILS ? channels.get (1).id () : ""));
    }

    private static DevicePageState selection (final DevicePageState state, final Selection selection)
    { return new DevicePageState (state.kind (), state.device (), state.channels (), state.selectedChannel (), state.parameters (), state.sends (), selection); }
    private static String title (final Kind kind)
    {
        return switch (kind) { case PARAMETERS -> "Device · parameters"; case CHAINS -> "Device · slot chains"; case USER -> "User · project controls";
            case LAYER -> "Layer · selected channel"; case LAYER_VOLUME -> "Layers · volume"; case LAYER_PAN -> "Layers · pan"; case LAYER_SEND -> "Layers · send";
            case TRACK_DETAILS -> "Track · details"; case LAYER_DETAILS -> "Layer · details"; case CROSSFADE -> "Tracks · crossfade"; default -> "Device"; };
    }
}
