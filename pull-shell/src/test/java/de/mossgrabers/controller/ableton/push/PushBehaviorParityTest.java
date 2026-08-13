// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.controller.ableton.push;

import de.mossgrabers.controller.ableton.push.command.trigger.MastertrackCommand;
import de.mossgrabers.controller.ableton.push.command.trigger.SelectPlayViewCommand;
import de.mossgrabers.controller.ableton.push.controller.PushColorManager;
import de.mossgrabers.controller.ableton.push.controller.PushControlSurface;
import de.mossgrabers.controller.ableton.push.mode.device.UserMode;
import de.mossgrabers.controller.ableton.push.workspace.SessionBankRegistry;
import de.mossgrabers.framework.controller.color.ColorEx;
import de.mossgrabers.framework.controller.display.IGraphicDisplay;
import de.mossgrabers.framework.controller.hardware.IHwButton;
import de.mossgrabers.framework.controller.hardware.IHwLight;
import de.mossgrabers.framework.controller.hardware.IHwSurfaceFactory;
import de.mossgrabers.framework.controller.valuechanger.IValueChanger;
import de.mossgrabers.framework.controller.valuechanger.TwosComplementValueChanger;
import de.mossgrabers.framework.daw.IHost;
import de.mossgrabers.framework.daw.IModel;
import de.mossgrabers.framework.daw.IProject;
import de.mossgrabers.framework.daw.data.ICursorTrack;
import de.mossgrabers.framework.daw.data.IMasterTrack;
import de.mossgrabers.framework.daw.data.ITrack;
import de.mossgrabers.framework.daw.data.bank.IParameterBank;
import de.mossgrabers.framework.daw.data.bank.IParameterPageBank;
import de.mossgrabers.framework.daw.data.bank.ITrackBank;
import de.mossgrabers.framework.daw.midi.IMidiInput;
import de.mossgrabers.framework.daw.midi.IMidiOutput;
import de.mossgrabers.framework.daw.midi.ISelectedTrackNoteTarget;
import de.mossgrabers.framework.featuregroup.IMode;
import de.mossgrabers.framework.featuregroup.IView;
import de.mossgrabers.framework.mode.Modes;
import de.mossgrabers.framework.parameter.IParameter;
import de.mossgrabers.framework.utils.ButtonEvent;
import de.mossgrabers.framework.view.Views;
import de.mossgrabers.pull.core.api.ControllerViewFacet;
import de.mossgrabers.pull.core.api.DesiredControllerWorkspace;
import de.mossgrabers.pull.core.api.SessionBankShape;
import de.mossgrabers.pull.core.api.output.RgbColor;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;


/**
 * Characterization tests for ordinary Push behavior preserved across Pull migrations.
 */
class PushBehaviorParityTest
{
    @Test
    void coreLightAdapterPreservesOffAndMapsRgbToThePushPalette ()
    {
        final PushColorManager colors = new PushColorManager ();
        assertEquals (PushColorManager.PUSH2_COLOR2_BLACK, PushControllerSetup.controllerLightColor (colors, new RgbColor (0, 0, 0)));
        assertEquals (colors.getColorIndex (ColorEx.WHITE), PushControllerSetup.controllerLightColor (colors, new RgbColor (255, 255, 255)));
        assertEquals (colors.getColorIndex (ColorEx.GREEN), PushControllerSetup.controllerLightColor (colors, new RgbColor (0, 255, 0)));
    }


    @Test
    void masterButtonFreezesPageOverlayPolicyAtPress ()
    {
        final IValueChanger valueChanger = new TwosComplementValueChanger (128, 1);
        final ICursorTrack cursorTrack = proxy (ICursorTrack.class, (proxy, method, arguments) -> switch (method.getName ())
        {
            case "doesExist" -> Boolean.TRUE;
            case "getIndex" -> Integer.valueOf (3);
            default -> relaxedValue (method.getReturnType ());
        });
        final List<String> selections = new ArrayList<> ();
        final IMasterTrack masterTrack = proxy (IMasterTrack.class, (proxy, method, arguments) -> {
            if ("select".equals (method.getName ()))
                selections.add ("master");
            return relaxedValue (method.getReturnType ());
        });
        final IModel model = proxy (IModel.class, (proxy, method, arguments) -> switch (method.getName ())
        {
            case "getCursorTrack" -> cursorTrack;
            case "getMasterTrack" -> masterTrack;
            default -> relaxedValue (method.getReturnType ());
        });
        final PushControlSurface surface = createSurface (valueChanger, relaxedProxy (ISelectedTrackNoteTarget.class), cursorTrack);
        surface.getModeManager ().register (Modes.TRACK, relaxedProxy (IMode.class));
        surface.getModeManager ().register (Modes.MASTER, relaxedProxy (IMode.class));
        surface.getModeManager ().setDefaultID (Modes.TRACK);
        final SessionBankShape sessionShape = new SessionBankShape (8, 8);
        surface.setSessionBankRegistry (new SessionBankRegistry (model, Set.of (sessionShape), sessionShape));
        surface.getControllerWorkspaceHost ().apply (new DesiredControllerWorkspace (
            "Master", Set.of (ControllerViewFacet.MASTER_CONTROLS), SessionBankShape.empty ()));
        surface.getModeManager ().setActive (Modes.TRACK);
        final MastertrackCommand command = new MastertrackCommand (model, surface);

        command.execute (ButtonEvent.DOWN, 127);
        surface.getControllerWorkspaceHost ().invalidate ();
        command.execute (ButtonEvent.UP, 0);

        assertEquals (Modes.MASTER, surface.getModeManager ().getActiveID ());
        assertEquals (List.of (), selections);

        command.execute (ButtonEvent.DOWN, 127);
        command.execute (ButtonEvent.UP, 0);

        assertEquals (Modes.TRACK, surface.getModeManager ().getActiveID ());
        assertEquals (List.of (), selections);
    }


    @Test
    void userModeBottomMenuAndButtonAddressTheSameTrack ()
    {
        final IValueChanger valueChanger = new TwosComplementValueChanger (128, 1);
        final IParameter parameter = relaxedProxy (IParameter.class);
        final List<Integer> selectedParameterPages = new ArrayList<> ();
        final IParameterPageBank parameterPageBank = proxy (IParameterPageBank.class, (proxy, method, arguments) -> switch (method.getName ())
        {
            case "getItem" -> "Page " + (((Integer) arguments[0]).intValue () + 1);
            case "selectPage" -> {
                selectedParameterPages.add ((Integer) arguments[0]);
                yield null;
            }
            default -> relaxedValue (method.getReturnType ());
        });
        final IParameterBank parameterBank = proxy (IParameterBank.class, (proxy, method, arguments) -> switch (method.getName ())
        {
            case "getPageSize" -> Integer.valueOf (8);
            case "getItem" -> parameter;
            case "getPageBank" -> parameterPageBank;
            default -> relaxedValue (method.getReturnType ());
        });
        final List<Integer> selectedTrackIndices = new ArrayList<> ();
        final ITrack [] tracks = new ITrack [8];
        for (int index = 0; index < tracks.length; index++)
        {
            final int trackIndex = index;
            tracks[index] = proxy (ITrack.class, (proxy, method, arguments) -> switch (method.getName ())
            {
                case "doesExist" -> Boolean.TRUE;
                case "getName" -> "Track " + (trackIndex + 1);
                case "select" -> {
                    selectedTrackIndices.add (Integer.valueOf (trackIndex));
                    yield null;
                }
                default -> relaxedValue (method.getReturnType ());
            });
        }
        final ICursorTrack cursorTrack = proxy (ICursorTrack.class, (proxy, method, arguments) -> "getParameterBank".equals (method.getName ()) ? parameterBank : relaxedValue (method.getReturnType ()));
        final ITrackBank trackBank = proxy (ITrackBank.class, (proxy, method, arguments) -> switch (method.getName ())
        {
            case "getItem" -> tracks[((Integer) arguments[0]).intValue ()];
            case "getSelectedItem" -> Optional.empty ();
            default -> relaxedValue (method.getReturnType ());
        });
        final IProject project = proxy (IProject.class, (proxy, method, arguments) -> "getParameterBank".equals (method.getName ()) ? parameterBank : relaxedValue (method.getReturnType ()));
        final PushColorManager colorManager = new PushColorManager ();
        final IModel model = proxy (IModel.class, (proxy, method, arguments) -> switch (method.getName ())
        {
            case "getColorManager" -> colorManager;
            case "getCursorTrack" -> cursorTrack;
            case "getCurrentTrackBank" -> trackBank;
            case "getProject" -> project;
            case "getValueChanger" -> valueChanger;
            default -> relaxedValue (method.getReturnType ());
        });
        final UserMode mode = new UserMode (createSurface (valueChanger, relaxedProxy (ISelectedTrackNoteTarget.class), cursorTrack), model);
        final List<String> bottomMenus = new ArrayList<> ();
        final IGraphicDisplay display = proxy (IGraphicDisplay.class, (proxy, method, arguments) -> {
            if ("addParameterElement".equals (method.getName ()) && arguments.length == 11)
                bottomMenus.add ((String) arguments[2]);
            return relaxedValue (method.getReturnType ());
        });

        mode.updateDisplay2 (display);
        mode.onFirstRow (3, ButtonEvent.DOWN);
        mode.onFirstRow (3, ButtonEvent.UP);

        assertEquals ("Track 4", bottomMenus.get (3));
        assertEquals (List.of (Integer.valueOf (3)), selectedTrackIndices);
        assertEquals (List.of (), selectedParameterPages);
    }


    @Test
    void automaticDrumLayoutBecomesTheTracksPreferredNoteView ()
    {
        final IValueChanger valueChanger = new TwosComplementValueChanger (128, 1);
        final ICursorTrack cursorTrack = proxy (ICursorTrack.class, (proxy, method, arguments) -> switch (method.getName ())
        {
            case "doesExist" -> Boolean.TRUE;
            case "getChannelID" -> "track-a";
            case "getPosition" -> Integer.valueOf (4);
            default -> relaxedValue (method.getReturnType ());
        });
        final ISelectedTrackNoteTarget selectedTarget = proxy (ISelectedTrackNoteTarget.class, (proxy, method, arguments) -> switch (method.getName ())
        {
            case "doesExist", "canHoldNotes", "hasDrumDevice" -> Boolean.TRUE;
            case "getChannelID" -> "track-a";
            default -> relaxedValue (method.getReturnType ());
        });
        final IModel model = proxy (IModel.class, (proxy, method, arguments) -> "getCursorTrack".equals (method.getName ()) ? cursorTrack : relaxedValue (method.getReturnType ()));
        final PushControlSurface surface = createSurface (valueChanger, selectedTarget, cursorTrack);
        surface.getViewManager ().register (Views.SESSION, relaxedProxy (IView.class));
        surface.getViewManager ().register (Views.DRUM_PAD, relaxedProxy (IView.class));
        surface.getViewManager ().setActive (Views.SESSION);

        new SelectPlayViewCommand (model, surface).execute (ButtonEvent.DOWN, 127);

        assertEquals (Views.DRUM_PAD, surface.getViewManager ().getActiveID ());
        assertEquals (Views.DRUM_PAD, surface.getViewManager ().getPreferredView (4));
    }


    @Test
    void automaticDrumLayoutWaitsForSelectedTrackModelAlignment ()
    {
        final IValueChanger valueChanger = new TwosComplementValueChanger (128, 1);
        final ICursorTrack cursorTrack = proxy (ICursorTrack.class, (proxy, method, arguments) -> switch (method.getName ())
        {
            case "doesExist" -> Boolean.TRUE;
            case "getChannelID" -> "track-b";
            case "getPosition" -> Integer.valueOf (4);
            default -> relaxedValue (method.getReturnType ());
        });
        final ISelectedTrackNoteTarget selectedTarget = proxy (ISelectedTrackNoteTarget.class, (proxy, method, arguments) -> switch (method.getName ())
        {
            case "doesExist", "canHoldNotes", "hasDrumDevice" -> Boolean.TRUE;
            case "getChannelID" -> "track-a";
            default -> relaxedValue (method.getReturnType ());
        });
        final IModel model = proxy (IModel.class, (proxy, method, arguments) -> "getCursorTrack".equals (method.getName ()) ? cursorTrack : relaxedValue (method.getReturnType ()));
        final PushControlSurface surface = createSurface (valueChanger, selectedTarget, cursorTrack);
        surface.getViewManager ().register (Views.SESSION, relaxedProxy (IView.class));
        surface.getViewManager ().register (Views.DRUM_PAD, relaxedProxy (IView.class));
        surface.getViewManager ().setActive (Views.SESSION);

        new SelectPlayViewCommand (model, surface).execute (ButtonEvent.DOWN, 127);

        assertEquals (Views.SESSION, surface.getViewManager ().getActiveID ());
        assertNull (surface.getViewManager ().getPreferredView (4));
    }


    private static PushControlSurface createSurface (final IValueChanger valueChanger, final ISelectedTrackNoteTarget selectedTarget, final ITrack drumModelTrack)
    {
        final IHwButton button = relaxedProxy (IHwButton.class);
        final IHwLight light = relaxedProxy (IHwLight.class);
        final IHwSurfaceFactory factory = proxy (IHwSurfaceFactory.class, (proxy, method, arguments) -> switch (method.getName ())
        {
            case "createButton" -> button;
            case "createLight" -> light;
            default -> relaxedValue (method.getReturnType ());
        });
        final IHost host = proxy (IHost.class, (proxy, method, arguments) -> "createSurfaceFactory".equals (method.getName ()) ? factory : relaxedValue (method.getReturnType ()));
        final PushControlSurface surface = new PushControlSurface (
            host,
            new PushColorManager (),
            new PushConfiguration (host, valueChanger, List.of ()),
            relaxedProxy (IMidiOutput.class),
            relaxedProxy (IMidiInput.class),
            selectedTarget,
            drumModelTrack,
            () -> false,
            null);
        surface.addGraphicsDisplay (relaxedProxy (IGraphicDisplay.class));
        return surface;
    }


    private static <T> T proxy (final Class<T> type, final java.lang.reflect.InvocationHandler handler)
    {
        return type.cast (Proxy.newProxyInstance (type.getClassLoader (), new Class<?> []
        {
            type
        }, handler));
    }


    private static <T> T relaxedProxy (final Class<T> type)
    {
        return proxy (type, (proxy, method, arguments) -> relaxedValue (method.getReturnType ()));
    }


    private static Object relaxedValue (final Class<?> type)
    {
        if (type.isInterface ())
            return relaxedProxy (type);
        if (!type.isPrimitive () || void.class.equals (type))
            return null;
        if (boolean.class.equals (type))
            return Boolean.FALSE;
        if (char.class.equals (type))
            return Character.valueOf ('\0');
        if (byte.class.equals (type))
            return Byte.valueOf ((byte) 0);
        if (short.class.equals (type))
            return Short.valueOf ((short) 0);
        if (int.class.equals (type))
            return Integer.valueOf (0);
        if (long.class.equals (type))
            return Long.valueOf (0L);
        if (float.class.equals (type))
            return Float.valueOf (0.0F);
        return Double.valueOf (0.0);
    }
}
