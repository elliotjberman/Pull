// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.shell.runtime;

import de.mossgrabers.controller.ableton.push.controller.PushColorManager;
import de.mossgrabers.controller.ableton.push.controller.PushControlSurface;
import de.mossgrabers.controller.ableton.push.mode.BaseMode;
import de.mossgrabers.controller.ableton.push.mode.device.DeviceLayerMode;
import de.mossgrabers.controller.ableton.push.mode.device.DeviceParamsMode;
import de.mossgrabers.controller.ableton.push.mode.track.TrackDetailsMode;
import de.mossgrabers.framework.controller.ContinuousID;
import de.mossgrabers.framework.controller.color.ColorEx;
import de.mossgrabers.framework.controller.display.IGraphicDisplay;
import de.mossgrabers.framework.controller.valuechanger.TwosComplementValueChanger;
import de.mossgrabers.framework.daw.IModel;
import de.mossgrabers.framework.daw.ITransport;
import de.mossgrabers.framework.daw.data.*;
import de.mossgrabers.framework.daw.data.bank.*;
import de.mossgrabers.framework.daw.resource.ChannelType;
import de.mossgrabers.framework.mode.Modes;
import de.mossgrabers.framework.parameter.IParameter;
import de.mossgrabers.framework.utils.ButtonEvent;
import de.mossgrabers.pull.core.api.*;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import static de.mossgrabers.pull.shell.runtime.CurrentTrackParameterBankHostTest.proxy;
import static org.junit.jupiter.api.Assertions.*;

class PushDevicePageObserverTest
{
    @Test
    void trackDetailsKeepCursorReadbackAndFrozenBankOrMasterActionIdentitySeparate ()
    {
        final Fixture fixture = new Fixture ();
        final TrackDetailsMode mode = new TrackDetailsMode (fixture.surface, fixture.model);
        fixture.activate (Modes.TRACK_DETAILS, mode);
        assertEquals ("pinned-cursor", fixture.capture ().selectedChannel ().id ());
        assertEquals ("visible-0", fixture.capture ().selection ().actionTargetId ());
        mode.onFirstRow (2, ButtonEvent.UP);
        assertEquals (List.of ("visible-0:mute"), fixture.requests);
        assertFalse (fixture.capture ().selectedChannel ().muted (), "void toggle submission is not observed state");
        assertFalse (fixture.capture ().channels ().get (0).muted ());
        fixture.selectedMuted = true;
        assertFalse (fixture.capture ().selectedChannel ().muted (), "the pinned cursor did not become the selected action target");
        assertTrue (fixture.capture ().channels ().get (0).muted ());
        fixture.cursorId = "visible-0";
        assertTrue (fixture.capture ().selectedChannel ().muted ());
        assertEquals (fixture.capture ().selectedChannel ().id (), fixture.capture ().selection ().actionTargetId ());
        fixture.masterSelected = true;
        assertEquals ("master", fixture.capture ().selection ().actionTargetId ());
        mode.onFirstRow (2, ButtonEvent.UP);
        assertEquals ("master:mute", fixture.requests.getLast ());
    }

    @Test
    void deviceNamesAndBankOptionsAreRawBoundedObservations ()
    {
        final Fixture fixture = new Fixture ();
        final DeviceParamsMode mode = new DeviceParamsMode (fixture.surface, fixture.model);
        fixture.activate (Modes.DEVICE_PARAMS, mode);
        assertEquals (8, fixture.capture ().device ().siblings ().size ());
        assertEquals (8, fixture.capture ().device ().chains ().size (), "host may report more chains than eight physical choices");
        assertTrue (fixture.capture ().selection ().showDevices ());
        mode.setShowDevices (false);
        assertFalse (fixture.capture ().selection ().showDevices ());
        assertEquals ("Host page 7", fixture.capture ().device ().parameterPages ().get (7));
        assertTrue (fixture.requests.isEmpty ());
    }

    @Test
    void aDrumBankObserverGapCannotPairNewProviderValuesWithOldLayerLabels ()
    {
        final Fixture fixture = new Fixture ();
        final DeviceLayerMode mode = new DeviceLayerMode (fixture.surface, fixture.model);
        fixture.activate (Modes.DEVICE_LAYER, mode);
        assertTrue (fixture.capture ().selection ().bankAligned ());
        assertFalse (fixture.capture ().parameters ().isEmpty ());
        fixture.hasDrumPads = true; // cursor read-back changes before the mode's installed observer switches banks
        assertFalse (fixture.capture ().selection ().bankAligned ());
        assertTrue (fixture.capture ().parameters ().isEmpty ());
        assertTrue (fixture.requests.isEmpty ());
    }

    private static final class Fixture
    {
        private boolean cursorMuted;
        private boolean selectedMuted;
        private boolean masterSelected;
        private String cursorId = "pinned-cursor";
        private boolean hasDrumPads;
        private final String longName = "A complete host parameter name that exceeds the old sixteen character abbreviation";
        private final List<String> requests = new ArrayList<> ();
        private final TwosComplementValueChanger changer = new TwosComplementValueChanger (128, 1);
        private final PushControlSurface surface = ParameterTargetHostTest.emptySurface (this.changer);
        private final IParameter trackParameter = this.parameter ();
        private final IParameterPageBank pages = proxy (IParameterPageBank.class, (method, args) -> switch (method) { case "getPageSize" -> 8; case "getItem" -> "Host page " + args[0]; case "getSelectedItemIndex" -> 2; default -> null; });
        private final IParameterBank trackParameters = this.parameters (this.trackParameter);
        private final ISendBank sends = proxy (ISendBank.class, (method, args) -> switch (method) { case "getPageSize" -> 8; case "getItem" -> this.send ((int) args[0]); default -> null; });
        private final ICursorTrack cursor = proxy (ICursorTrack.class, (method, args) -> this.channel (this.cursorId, false, method, args));
        private final ITrack[] tracks = new ITrack[8];
        private final ILayer[] layers = new ILayer[16];
        private final ITrackBank trackBank = proxy (ITrackBank.class, (method, args) -> switch (method) { case "getPageSize" -> 8; case "getItem" -> this.tracks[(int) args[0]]; case "getSelectedItem" -> Optional.of (this.tracks[0]); case "hasExistingItems" -> true; default -> null; });
        private final ILayerBank layerBank = proxy (ILayerBank.class, (method, args) -> "getPageSize".equals (method) ? 8 : this.layerBank (method, args));
        private final IDrumPadBank drumBank = proxy (IDrumPadBank.class, (method, args) -> this.layerBank (method, args));
        private final IDeviceBank devices = proxy (IDeviceBank.class, (method, args) -> switch (method) { case "getPageSize" -> 8; case "getItem" -> proxy (IDevice.class, (name, values) -> switch (name) { case "doesExist" -> true; case "getName" -> "Host device " + args[0]; default -> null; }); default -> null; });
        private final ICursorDevice device = proxy (ICursorDevice.class, (method, args) -> switch (method) {
            case "doesExist", "hasLayers", "isEnabled" -> true; case "hasDrumPads" -> this.hasDrumPads;
            case "getLayerBank" -> this.layerBank; case "getDrumPadBank" -> this.drumBank; case "getParameterBank" -> this.trackParameters;
            case "getDeviceBank" -> this.devices; case "getName" -> "Observed device"; case "getSlotChains" -> java.util.stream.IntStream.range (0, 20).mapToObj (index -> "Chain " + index).toArray (String[]::new);
            default -> null;
        });
        private final IModel model = proxy (IModel.class, (method, args) -> switch (method) {
            case "getValueChanger" -> this.changer; case "getColorManager" -> new PushColorManager (); case "getCurrentTrackBank", "getTrackBank" -> this.trackBank;
            case "getCursorTrack" -> this.cursor; case "getCursorDevice" -> this.device;
            case "getMasterTrack" -> proxy (IMasterTrack.class, (name, values) -> "isSelected".equals (name) ? this.masterSelected : this.channel ("master", false, name, values));
            case "getTransport" -> proxy (ITransport.class, (name, values) -> null); default -> null;
        });

        private Fixture ()
        {
            this.surface.addGraphicsDisplay (proxy (IGraphicDisplay.class, (method, args) -> null));
            for (int index = 0; index < 8; index++)
            {
                final String id = "visible-" + index;
                this.tracks[index] = proxy (ITrack.class, (method, args) -> this.channel (id, false, method, args));
                this.surface.createRelativeKnob (ContinuousID.valueOf ("KNOB" + (index + 1)), "Knob " + index);
            }
            for (int index = 0; index < 16; index++)
            {
                final String id = "layer-" + index;
                final int slot = index;
                this.layers[index] = proxy (ILayer.class, (method, args) -> "getIndex".equals (method) ? slot : this.channel (id, true, method, args));
            }
        }

        private void activate (final Modes id, final BaseMode<?> mode)
        {
            this.surface.getModeManager ().register (id, mode);
            this.surface.getModeManager ().activateConsumer (1);
            this.surface.getModeManager ().apply (new DesiredControllerPageState (1, ControllerPageRef.legacy (id.name ()), ControllerPageRef.none (), Optional.empty (), 0));
        }
        private DevicePageState capture () { return PushDevicePageObserver.capture (this.surface, this.model); }
        private IParameterBank parameters (final IParameter parameter)
        { return proxy (IParameterBank.class, (method, args) -> switch (method) { case "getPageSize" -> 8; case "getItem" -> parameter; case "getPageBank" -> this.pages; default -> null; }); }
        private Object layerBank (final String method, final Object[] args)
        { return switch (method) { case "getPageSize" -> 16; case "getItem" -> this.layers[(int) args[0]]; case "getSelectedItem" -> Optional.of (this.layers[0]); case "hasExistingItems" -> true; case "getEditSendName" -> "Send " + args[0]; default -> null; }; }
        private Object channel (final String id, final boolean layer, final String method, final Object[] args)
        {
            return switch (method) {
                case "doesExist", "isActivated" -> true; case "getChannelID", "getName" -> id; case "getType" -> layer ? ChannelType.LAYER : ChannelType.INSTRUMENT;
                case "getColor" -> ColorEx.BLUE; case "isPinned" -> id.equals ("pinned-cursor"); case "isSelected" -> id.endsWith ("0");
                case "getParameterBank" -> this.trackParameters; case "getSendBank" -> this.sends;
                case "getVolumeParameter", "getPanParameter" -> this.trackParameter;
                case "isMute" -> id.equals ("pinned-cursor") ? this.cursorMuted : id.equals ("visible-0") && this.selectedMuted;
                case "toggleMute" -> { this.requests.add (id + ":mute"); yield null; }
                default -> null;
            };
        }
        private IParameter parameter ()
        {
            return proxy (IParameter.class, (method, args) -> switch (method) {
                case "doesExist" -> true; case "getName" -> { assertTrue (args == null || args.length == 0, "observer must not request display truncation"); yield this.longName; }
                case "getValue", "getModulatedValue" -> 96;
                case "getDisplayedValue" -> "96 dB";
                case "inc" -> { this.requests.add ("track:" + args[0]); yield null; }
                case "touchValue" -> { this.requests.add ("track:" + "touch:" + args[0]); yield null; }
                default -> null;
            });
        }
        private ISend send (final int index)
        { return proxy (ISend.class, (method, args) -> switch (method) { case "doesExist", "isEnabled" -> true; case "getName" -> "Send " + index; case "getPosition" -> index; case "getDisplayedValue" -> "50 %"; case "getValue", "getModulatedValue" -> 64; default -> null; }); }
    }
}
