// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.ui.page;

import de.mossgrabers.pull.core.api.DevicePageState;
import de.mossgrabers.pull.core.api.DevicePageState.*;
import de.mossgrabers.pull.core.api.MixerControlKind;
import de.mossgrabers.pull.core.api.MixerControlRole;
import de.mossgrabers.pull.core.api.MixerControlSnapshot;
import de.mossgrabers.pull.core.api.output.DisplayIcon;
import de.mossgrabers.pull.core.api.output.RgbColor;
import de.mossgrabers.pull.core.ui.component.ChoiceCell;
import de.mossgrabers.pull.core.ui.component.TextContent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import static de.mossgrabers.pull.core.ui.PageStyle.*;

/** Chooses labels, parameter roles and menu state from bounded observations of frozen handlers. */
public final class DevicePageProjector
{
    private static final List<String> DEVICE_MENU = List.of ("On", "Parameters", "Expanded", "Chains", "Banks", "Pin Device", "Window", "Up");
    private DevicePageProjector () { }

    public static DevicePagePresentation project (final DevicePageState state)
    {
        final List<ChoiceCell> upper = new ArrayList<> (Collections.nCopies (8, choice ("", false)));
        final List<ChoiceCell> lower = new ArrayList<> (Collections.nCopies (8, choice ("", false)));
        final List<TrackFooterPresentation.Cell> footer = new ArrayList<> (8);
        final List<MixerControlSnapshot> controls = new ArrayList<> (8);
        final List<DevicePagePresentation.ToggleCell> toggles = new ArrayList<> (8);
        String heading = "";
        String message = "";
        final Device device = state.device ();
        final Selection selection = state.selection ();
        if (state.kind () == Kind.TRACK_DETAILS || state.kind () == Kind.LAYER_DETAILS)
        {
            final Channel channel = state.selectedChannel ();
            final boolean track = state.kind () == Kind.TRACK_DETAILS;
            if (!channel.exists ()) message = track ? "Please select a track..." : "Please select a layer...";
            else if (track && (channel.id ().isBlank () || !channel.id ().equals (selection.actionTargetId ()))) message = "Waiting for track target...";
            else
            {
                heading = (track ? trackType (channel.type ()) + " Track: " : "Layer: ") + channel.name ();
                option (lower, toggles, 0, "Active", channel.active ());
                option (lower, toggles, 2, "Mute", channel.muted ());
                option (lower, toggles, 3, "Solo", channel.soloed ());
                if (track)
                {
                    option (lower, toggles, 1, "Rec Arm", channel.armed ());
                    option (lower, toggles, 4, "Monitor", channel.monitor ());
                    option (lower, toggles, 5, "Auto Monitor", channel.autoMonitor ());
                    option (lower, toggles, 6, "Pin Track", selection.trackPinned ());
                    upper.set (5, choice ("MIDI Edit Channel", false));
                    upper.set (7, choice (Integer.toString (selection.midiEditChannel () + 1), false));
                }
                else if (selection.drumPadBank ())
                {
                    upper.set (6, choice ("Clear Mute", false));
                    upper.set (7, choice ("Clear Solo", false));
                }
                if (track || selection.drumPadBank ()) lower.set (7, choice ("Select Color", false));
            }
        }
        else
        {
            switch (state.kind ())
            {
                case PARAMETERS, CHAINS ->
                {
                    if (state.kind () == Kind.PARAMETERS && selection.showDevices ())
                    {
                        for (int index = 0; index < device.siblings ().size (); index++) upper.set (index, choice (device.siblings ().get (index), index == device.selectedSibling ()));
                        footer.addAll (footer (state));
                    }
                    else
                    {
                        final List<Boolean> flags = List.of (device.enabled (), device.parameterSectionVisible (), device.expanded (), state.kind () == Kind.CHAINS,
                            state.kind () != Kind.CHAINS && !selection.showDevices (), device.pinned (), device.windowOpen (), true);
                        for (int index = 0; index < 8; index++) upper.set (index, choice (DEVICE_MENU.get (index), flags.get (index)));
                        final List<String> labels = state.kind () == Kind.CHAINS ? device.chains () : device.parameterPages ();
                        for (int index = 0; index < labels.size (); index++) lower.set (index, choice (labels.get (index), state.kind () == Kind.CHAINS || index == device.selectedPage ()));
                    }
                    if (!device.exists () && state.kind () == Kind.CHAINS) message = "Please select a device or press 'Add Device'...";
                }
                case LAYER, LAYER_VOLUME, LAYER_PAN, LAYER_SEND ->
                {
                    if (!device.exists ()) message = "Please select a device or press 'Add Device'...";
                    else if (!selection.bankAligned ()) message = "Waiting for device bank...";
                    else if (!device.hasLayers ()) message = "This device does not have layers.";
                    else if (!selection.bankHasItems ()) message = device.hasDrumPads () ? "Please create a Drum Pad..." : "Please create a Device Layer...";
                    if (message.isEmpty ())
                    {
                        layerMenu (state, upper);
                        footer.addAll (footer (state));
                    }
                    else upper.set (7, choice ("Up", true));
                }
                case CROSSFADE ->
                {
                    crossfadeMenu (state, upper);
                    footer.addAll (footer (state));
                }
                default -> { }
            }
            if (message.isEmpty () && state.kind () != Kind.CHAINS)
                for (int index = 0; index < state.parameters ().size (); index++)
                {
                    final Parameter parameter = state.parameters ().get (index);
                    if (!parameter.exists ()) continue;
                    final MixerControlKind kind = controlKind (state.kind (), index);
                    final boolean selectedLayer = state.kind () == Kind.LAYER;
                    final boolean perChannel = state.kind () == Kind.LAYER_VOLUME || state.kind () == Kind.LAYER_PAN || state.kind () == Kind.LAYER_SEND || state.kind () == Kind.CROSSFADE;
                    final Channel channel = perChannel && index < state.channels ().size () ? state.channels ().get (index) : state.selectedChannel ();
                    final RgbColor color = channel.exists () ? channel.color () : WHITE;
                    final String label = state.kind () == Kind.CROSSFADE ? "Crossfader" : TextContent.candidate (parameter.name (), 24);
                    controls.add (new MixerControlSnapshot (index, kind, label.isBlank () ? "Parameter" : label,
                        parameter.value (), parameter.modulatedValue (), TextContent.candidate (parameter.displayedValue (), 48),
                        MixerControlRole.HOST_COLORED,
                        parameter.enabled () && (!(selectedLayer || perChannel) || channel.active ()), parameter.touched (),
                        Optional.of (color), channel.vuLeft (), channel.vuRight ()));
                }
        }
        return new DevicePagePresentation (upper, lower, new TrackFooterPresentation (footer), controls, heading, message, toggles);
    }

    private static void layerMenu (final DevicePageState state, final List<ChoiceCell> menu)
    {
        menu.set (0, choice ("Volume", state.kind () == Kind.LAYER_VOLUME));
        menu.set (1, choice ("Pan", state.kind () == Kind.LAYER_PAN));
        final int start = state.sends ().isEmpty () ? 1 : Math.max (0, state.sends ().get (0).position ()) + 1;
        menu.set (3, choice ("Sends " + start + "–" + (start + 3), false));
        for (int index = 0; index < 4 && index < state.sends ().size (); index++)
            menu.set (index + 4, choice (state.sends ().get (index).name (), state.kind () == Kind.LAYER_SEND && state.selection ().sendIndex () % 4 == index));
        if (!state.selection ().shift () && !state.selection ().knobEightTouched ()) menu.set (7, choice ("Up", true));
    }

    private static void crossfadeMenu (final DevicePageState state, final List<ChoiceCell> menu)
    {
        menu.set (0, choice ("Volume", false)); menu.set (1, choice ("Pan", false)); menu.set (7, choice ("Crossfader", true));
        final boolean extra = state.sends ().size () > 5 && state.sends ().get (5).exists ();
        final int offset = extra ? state.selection ().mixSendOffset () : 0;
        if (extra) menu.set (offset == 0 ? 6 : 2, choice (offset == 0 ? ">" : "<", false));
        for (int index = 0; index < (extra ? 4 : 5); index++)
            if (offset + index < state.sends ().size () && state.sends ().get (offset + index).exists ())
                menu.set (index + (offset == 0 ? 2 : 3), choice (state.sends ().get (offset + index).name (), false));
    }

    private static MixerControlKind controlKind (final Kind kind, final int index)
    {
        if (kind == Kind.LAYER_VOLUME || kind == Kind.LAYER && index == 0) return MixerControlKind.VOLUME;
        if (kind == Kind.LAYER_PAN || kind == Kind.LAYER && index == 1) return MixerControlKind.PAN;
        return MixerControlKind.KNOB;
    }

    private static List<TrackFooterPresentation.Cell> footer (final DevicePageState state)
    {
        final List<TrackFooterPresentation.Cell> cells = new ArrayList<> (8);
        for (int index = 0; index < state.channels ().size (); index++)
        {
            final Channel channel = state.channels ().get (index);
            if (channel.exists ()) cells.add (new TrackFooterPresentation.Cell (index, TextContent.candidate (channel.name (), 128),
                channel.selected () && state.selection ().trackPinned () && !state.selection ().drumPadBank () && !isLayer (state.kind ()) ? DisplayIcon.PIN : icon (channel),
                channel.color (), channel.selected (), channel.active (), channel.armed ()));
        }
        return cells;
    }

    private static boolean isLayer (final Kind kind) { return kind == Kind.LAYER || kind == Kind.LAYER_VOLUME || kind == Kind.LAYER_PAN || kind == Kind.LAYER_SEND; }
    private static DisplayIcon icon (final Channel channel)
    {
        return switch (channel.type ())
        {
            case "AUDIO" -> DisplayIcon.AUDIO_TRACK;
            case "INSTRUMENT" -> DisplayIcon.INSTRUMENT_TRACK;
            case "HYBRID" -> DisplayIcon.HYBRID_TRACK;
            case "GROUP" -> channel.groupExpanded () ? DisplayIcon.GROUP_TRACK_OPEN : DisplayIcon.GROUP_TRACK;
            case "EFFECT" -> DisplayIcon.RETURN_TRACK;
            case "MASTER" -> DisplayIcon.MASTER;
            case "LAYER" -> DisplayIcon.MULTI_LAYER;
            default -> null;
        };
    }
    private static String trackType (final String type)
    {
        return switch (type) { case "AUDIO" -> "Audio"; case "INSTRUMENT" -> "Instrument"; case "HYBRID" -> "Hybrid"; case "GROUP" -> "Group"; case "EFFECT" -> "Effect"; case "MASTER" -> "Master"; default -> ""; };
    }
    private static ChoiceCell choice (final String text, final boolean selected) { return new ChoiceCell (TextContent.candidate (text, 24), true, selected); }
    private static void option (final List<ChoiceCell> lower, final List<DevicePagePresentation.ToggleCell> toggles, final int column, final String label, final boolean on)
    {
        lower.set (column, choice (label, on)); toggles.add (new DevicePagePresentation.ToggleCell (column, on));
    }
}
