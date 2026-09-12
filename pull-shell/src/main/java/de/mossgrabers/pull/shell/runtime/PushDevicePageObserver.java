// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.shell.runtime;

import de.mossgrabers.controller.ableton.push.controller.PushControlSurface;
import de.mossgrabers.controller.ableton.push.mode.BaseMode;
import de.mossgrabers.controller.ableton.push.mode.device.*;
import de.mossgrabers.controller.ableton.push.mode.track.CrossfadeMode;
import de.mossgrabers.controller.ableton.push.mode.track.TrackDetailsMode;
import de.mossgrabers.framework.daw.IModel;
import de.mossgrabers.framework.daw.data.IChannel;
import de.mossgrabers.framework.daw.data.ICursorDevice;
import de.mossgrabers.framework.daw.data.ITrack;
import de.mossgrabers.framework.daw.data.ISend;
import de.mossgrabers.framework.daw.data.bank.IBank;
import de.mossgrabers.framework.daw.data.bank.IDrumPadBank;
import de.mossgrabers.framework.daw.data.bank.ILayerBank;
import de.mossgrabers.framework.daw.data.bank.ISendBank;
import de.mossgrabers.framework.parameter.IParameter;
import de.mossgrabers.pull.core.api.DevicePageState;
import de.mossgrabers.pull.core.api.DevicePageState.*;
import de.mossgrabers.pull.core.api.output.RgbColor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Copies existing cached model values on the controller thread. No rendering, setters, or new proxies. */
public final class PushDevicePageObserver
{
    private PushDevicePageObserver () { }

    public static DevicePageState capture (final PushControlSurface surface, final IModel model)
    {
        if (!(surface.getModeManager ().getActive () instanceof final BaseMode<?> mode)) return DevicePageState.empty ();
        final Kind kind = kind (mode);
        if (kind == Kind.NONE) return DevicePageState.empty ();
        final boolean layer = mode instanceof DeviceLayerMode || mode instanceof DeviceLayerDetailsMode;
        final IBank<?> bank = layer ? mode.getBank () : model.getCurrentTrackBank ();
        final ICursorDevice cursor = model.getCursorDevice ();
        final Object selected = bank.getSelectedItem ().orElse (null);
        final IChannel selectedChannel = layer ? selected instanceof IChannel channel ? channel : null : model.getCursorTrack ();
        final boolean drum = layer && bank instanceof IDrumPadBank;
        final int offset = mode instanceof DeviceLayerMode && drum && selectedChannel != null && selectedChannel.getIndex () > 7 ? 8 : 0;
        // Providers and mode touch/row callbacks must still refer to the same bank during observer propagation.
        final boolean aligned = !(mode instanceof DeviceLayerMode) || bank == (cursor.hasDrumPads () ? cursor.getDrumPadBank () : cursor.getLayerBank ());
        final List<Channel> channels = new ArrayList<> (8);
        for (int index = 0; index < 8 && offset + index < bank.getPageSize (); index++)
            channels.add (channel (bank.getItem (offset + index) instanceof IChannel value ? value : null, model, surface));
        final List<Parameter> parameters = new ArrayList<> (8);
        if (kind != Kind.PARAMETERS && kind != Kind.CHAINS && kind != Kind.TRACK_DETAILS && kind != Kind.LAYER_DETAILS && aligned)
            for (int index = 0; index < 8; index++)
                parameters.add (parameter (mode.getParameterProvider ().get (index), mode.isKnobTouched (index), model));
        final boolean deviceFamily = mode instanceof DeviceParamsMode || mode instanceof DeviceLayerMode;
        final IChannel actionTarget = kind == Kind.TRACK_DETAILS ? model.getMasterTrack ().isSelected () ? model.getMasterTrack () : selected instanceof IChannel channel ? channel : null : null;
        final Selection selection = new Selection (mode instanceof DeviceParamsMode params && params.isShowDevices (),
            drum, bank.hasExistingItems (), aligned, offset,
            mode instanceof DeviceLayerSendMode send ? send.getSendIndex () : 0,
            surface.getConfiguration ().getMixSendOffset (), surface.isShiftPressed (), mode.isKnobTouched (7),
            model.getCursorTrack ().isPinned (), surface.getConfiguration ().getMidiEditChannel (),
            actionTarget == null || !actionTarget.doesExist () ? "" : text (actionTarget.getChannelID ()));
        return new DevicePageState (kind, deviceFamily ? device (cursor) : Device.empty (), channels,
            channel (selectedChannel, model, surface), parameters, sends (layer ? bank : null, selectedChannel), selection);
    }

    private static Kind kind (final BaseMode<?> mode)
    {
        if (mode instanceof DeviceChainsMode) return Kind.CHAINS;
        if (mode instanceof DeviceParamsMode) return Kind.PARAMETERS;
        if (mode instanceof DeviceLayerVolumeMode) return Kind.LAYER_VOLUME;
        if (mode instanceof DeviceLayerPanMode) return Kind.LAYER_PAN;
        if (mode instanceof DeviceLayerSendMode) return Kind.LAYER_SEND;
        if (mode instanceof DeviceLayerMode) return Kind.LAYER;
        if (mode instanceof DeviceLayerDetailsMode) return Kind.LAYER_DETAILS;
        if (mode instanceof TrackDetailsMode) return Kind.TRACK_DETAILS;
        if (mode instanceof CrossfadeMode) return Kind.CROSSFADE;
        return Kind.NONE;
    }

    private static Device device (final ICursorDevice cursor)
    {
        final List<String> siblings = new ArrayList<> (8);
        final List<String> pages = new ArrayList<> (8);
        final var deviceBank = cursor.getDeviceBank ();
        final var pageBank = cursor.getParameterBank ().getPageBank ();
        for (int index = 0; index < 8; index++)
        {
            if (index < deviceBank.getPageSize ()) siblings.add (text (deviceBank.getItem (index).doesExist () ? deviceBank.getItem (index).getName () : ""));
            if (index < pageBank.getPageSize ()) pages.add (text (pageBank.getItem (index)));
        }
        return new Device (cursor.doesExist (), text (cursor.getName ()), cursor.isEnabled (), cursor.isExpanded (),
            cursor.isParameterPageSectionVisible (), cursor.isPinned (), cursor.isWindowOpen (), cursor.hasLayers (),
            cursor.hasDrumPads (), cursor.getIndex (), siblings, pages, pageBank.getSelectedItemIndex (),
            Arrays.stream (cursor.getSlotChains ()).limit (8).map (PushDevicePageObserver::text).toList ());
    }

    private static Channel channel (final IChannel channel, final IModel model, final PushControlSurface surface)
    {
        if (channel == null || !channel.doesExist ()) return Channel.empty ();
        final ITrack track = channel instanceof ITrack value ? value : null;
        final int[] color = channel.getColor ().toIntRGB255 ();
        final boolean meters = surface.getConfiguration ().isEnableVUMeters ();
        return new Channel (true, text (channel.getChannelID ()), text (channel.getName ()), channel.getType ().name (),
            new RgbColor (color[0], color[1], color[2]), channel.isSelected (), channel.isActivated (),
            channel.isMute (), channel.isSolo (), track != null && track.isRecArm (), track != null && track.isMonitor (),
            track != null && track.isAutoMonitor (), track != null && track.isGroupExpanded (),
            meters ? normalized (channel.getVuLeft (), model) : 0, meters ? normalized (channel.getVuRight (), model) : 0);
    }

    private static Parameter parameter (final IParameter parameter, final boolean touched, final IModel model)
    {
        if (parameter == null || !parameter.doesExist ()) return Parameter.empty ();
        return new Parameter (true, text (parameter.getName ()), normalized (parameter.getValue (), model),
            parameter.getModulatedValue () < 0 ? -1 : normalized (parameter.getModulatedValue (), model),
            text (parameter.getDisplayedValue ()), !(parameter instanceof ISend send) || send.isEnabled (), touched);
    }

    private static List<Send> sends (final IBank<?> layerBank, final IChannel selected)
    {
        final IChannel source = layerBank != null && layerBank.getPageSize () > 0 && layerBank.getItem (0) instanceof IChannel first ? first : selected;
        if (source == null) return List.of ();
        final ISendBank bank = source.getSendBank ();
        final List<Send> result = new ArrayList<> (8);
        for (int index = 0; index < 8 && index < bank.getPageSize (); index++)
        {
            final ISend send = bank.getItem (index);
            final String name = layerBank instanceof ILayerBank layers ? layers.getEditSendName (index) : send.getName ();
            result.add (new Send (send.doesExist (), text (name), send.getPosition (), send.isEnabled ()));
        }
        return result;
    }

    private static double normalized (final int value, final IModel model) { return Math.max (0, Math.min (1, model.getValueChanger ().toNormalizedValue (value))); }
    private static String text (final String value) { return value == null ? "" : value.substring (0, Math.min (1024, value.length ())); }
}
