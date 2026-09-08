// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.controller.ableton.push;

import de.mossgrabers.controller.ableton.push.command.trigger.PushCursorCommand;
import de.mossgrabers.controller.ableton.push.controller.PushColorManager;
import de.mossgrabers.controller.ableton.push.controller.PushControlSurface;
import de.mossgrabers.controller.ableton.push.mode.device.UserMode;
import de.mossgrabers.controller.ableton.push.mode.CorePageMode;
import de.mossgrabers.controller.ableton.push.workspace.SessionBankRegistry;
import de.mossgrabers.framework.command.trigger.mode.ButtonRowModeCommand;
import de.mossgrabers.framework.command.trigger.Direction;
import de.mossgrabers.framework.controller.ButtonID;
import de.mossgrabers.framework.controller.ISetupFactory;
import de.mossgrabers.framework.controller.color.ColorEx;
import de.mossgrabers.framework.controller.display.IGraphicDisplay;
import de.mossgrabers.framework.controller.hardware.IHwButton;
import de.mossgrabers.framework.controller.hardware.IHwLight;
import de.mossgrabers.framework.controller.hardware.IHwSurfaceFactory;
import de.mossgrabers.framework.controller.valuechanger.IValueChanger;
import de.mossgrabers.framework.controller.valuechanger.TwosComplementValueChanger;
import de.mossgrabers.framework.configuration.ISettingsUI;
import de.mossgrabers.framework.daw.IHost;
import de.mossgrabers.framework.daw.IModel;
import de.mossgrabers.framework.daw.IProject;
import de.mossgrabers.framework.daw.data.ICursorTrack;
import de.mossgrabers.framework.daw.data.ITrack;
import de.mossgrabers.framework.daw.data.bank.IParameterBank;
import de.mossgrabers.framework.daw.data.bank.IParameterPageBank;
import de.mossgrabers.framework.daw.data.bank.ISceneBank;
import de.mossgrabers.framework.daw.data.bank.ITrackBank;
import de.mossgrabers.framework.daw.midi.IMidiInput;
import de.mossgrabers.framework.daw.midi.IMidiOutput;
import de.mossgrabers.framework.daw.midi.ISelectedTrackNoteTarget;
import de.mossgrabers.framework.featuregroup.IMode;
import de.mossgrabers.framework.featuregroup.IView;
import de.mossgrabers.framework.mode.Modes;
import de.mossgrabers.framework.parameter.IParameter;
import de.mossgrabers.framework.scale.Scales;
import de.mossgrabers.framework.utils.ButtonEvent;
import de.mossgrabers.framework.view.Views;
import de.mossgrabers.pull.core.api.ControllerViewFacet;
import de.mossgrabers.pull.core.api.DesiredControllerWorkspace;
import de.mossgrabers.pull.core.api.SessionBankShape;
import de.mossgrabers.pull.core.api.output.RgbColor;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static de.mossgrabers.pull.shell.testing.TestProxies.proxy;
import static de.mossgrabers.pull.shell.testing.TestProxies.relaxedProxy;
import static de.mossgrabers.pull.shell.testing.TestProxies.relaxedValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;


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
        assertEquals (colors.getColorIndex (ColorEx.RED), PushControllerSetup.controllerLightColor (colors, new RgbColor (255, 0, 0)));
    }


    @Test
    void drumControlPadsRemainControllerOnlyInTheNoteTranslationTable ()
    {
        final Scales scales = new Scales (new TwosComplementValueChanger (128, 1), 36, 100, 8, 8);
        final int [] drumMatrix = scales.getDrumMatrix ();

        assertEquals (36, drumMatrix[36]);
        assertEquals (51, drumMatrix[36 + 3 * 8 + 3]);
        for (int slot = 0; slot < 4; slot++)
            assertEquals (-1, drumMatrix[36 + 3 * 8 + 4 + slot]);
    }


    @Test
    void fixedDisplayColorsPreserveTheFormerDefaults ()
    {
        final PushConfiguration configuration = new PushConfiguration (relaxedProxy (IHost.class), new TwosComplementValueChanger (128, 1), List.of ());

        assertEquals (ColorEx.fromRGB (83, 83, 83), configuration.getColorBackground ());
        assertEquals (ColorEx.fromRGB (39, 39, 39), configuration.getColorBackgroundDarker ());
        assertEquals (ColorEx.fromRGB (200, 200, 200), configuration.getColorBackgroundLighter ());
        assertEquals (ColorEx.BLACK, configuration.getColorBorder ());
        assertEquals (ColorEx.BLACK, configuration.getColorText ());
        assertEquals (Modes.DEVICE_LAYER, configuration.getCurrentLayerMixMode ());
    }


    @Test
    void selectedTrackMaintenanceDoesNotRecallASecondLegacyNoteLayout ()
    {
        final IValueChanger valueChanger = new TwosComplementValueChanger (128, 1);
        final ICursorTrack cursorTrack = proxy (ICursorTrack.class, (proxy, method, arguments) -> switch (method.getName ())
        {
            case "doesExist" -> Boolean.TRUE;
            case "getPosition" -> Integer.valueOf (0);
            default -> relaxedValue (method.getReturnType ());
        });
        final IModel model = proxy (IModel.class, (proxy, method, arguments) -> "getCursorTrack".equals (method.getName ()) ? cursorTrack : relaxedValue (method.getReturnType ()));
        final PushControlSurface surface = createSurface (valueChanger, relaxedProxy (ISelectedTrackNoteTarget.class), cursorTrack);
        final AtomicInteger noteMappingUpdates = new AtomicInteger ();
        final IView play = proxy (IView.class, (proxy, method, arguments) -> {
            if ("updateNoteMapping".equals (method.getName ()))
                noteMappingUpdates.incrementAndGet ();
            return relaxedValue (method.getReturnType ());
        });
        surface.getViewManager ().register (Views.PLAY, play);
        surface.getViewManager ().register (Views.DRUM_PAD, relaxedProxy (IView.class));
        surface.getViewManager ().setDefaultID (Views.PLAY);
        surface.getViewManager ().setActive (Views.PLAY);
        surface.getViewManager ().setPreferredView (0, Views.DRUM_PAD);
        final IHost host = relaxedProxy (IHost.class);
        final ISetupFactory setupFactory = proxy (ISetupFactory.class, (proxy, method, arguments) -> "getArpeggiatorModes".equals (method.getName ()) ? List.of () : relaxedValue (method.getReturnType ()));
        final ISettingsUI settings = relaxedProxy (ISettingsUI.class);
        final TestPushControllerSetup setup = new TestPushControllerSetup (host, setupFactory, settings);
        setup.install (surface, model);

        setup.updateViewForTest ();

        assertEquals (Views.PLAY, surface.getViewManager ().getActiveID ());
        assertEquals (1, noteMappingUpdates.get ());
    }


    @Test
    void migratedSessionNavigationArrowsAreInertWhileLegacySceneNavigationIsPreserved ()
    {
        final AtomicBoolean shiftPressed = new AtomicBoolean ();
        final IValueChanger valueChanger = new TwosComplementValueChanger (128, 1);
        final ICursorTrack cursorTrack = relaxedProxy (ICursorTrack.class);
        final PushControlSurface surface = createSurface (valueChanger, relaxedProxy (ISelectedTrackNoteTarget.class), cursorTrack, shiftPressed::get);
        surface.createButton (ButtonID.SHIFT, "Shift");

        final List<String> operations = new ArrayList<> ();
        final ISceneBank sceneBank = proxy (ISceneBank.class, (proxy, method, arguments) -> {
            operations.add (method.getName ());
            return relaxedValue (method.getReturnType ());
        });
        final ITrackBank trackBank = proxy (ITrackBank.class, (proxy, method, arguments) -> {
            if ("getSceneBank".equals (method.getName ()))
                return sceneBank;
            operations.add (method.getName ());
            return relaxedValue (method.getReturnType ());
        });
        final IModel model = proxy (IModel.class, (proxy, method, arguments) -> switch (method.getName ())
        {
            case "getCurrentTrackBank", "getTrackBank" -> trackBank;
            default -> relaxedValue (method.getReturnType ());
        });
        final SessionBankShape sessionShape = new SessionBankShape (8, 8);
        surface.setSessionBankRegistry (new SessionBankRegistry (model, Set.of (sessionShape), sessionShape));
        surface.getControllerWorkspaceHost ().apply (new DesiredControllerWorkspace (
            "Session navigation", Set.of (ControllerViewFacet.SESSION_NAVIGATION), SessionBankShape.empty ()));
        operations.clear ();
        for (final Direction direction: Direction.values ())
        {
            final PushCursorCommand command = new PushCursorCommand (direction, model, surface);
            command.execute (ButtonEvent.DOWN, 127);
            shiftPressed.set (true);
            command.execute (ButtonEvent.DOWN, 127);
            assertFalse (command.canScroll ());
            shiftPressed.set (false);
        }
        assertTrue (operations.isEmpty (), "a missing core cannot revive the removed VS navigation recipe");

        surface.getControllerWorkspaceHost ().apply (DesiredControllerWorkspace.empty ());
        operations.clear ();
        final PushCursorCommand legacy = new PushCursorCommand (Direction.UP, model, surface);
        legacy.execute (ButtonEvent.DOWN, 127);
        shiftPressed.set (true);
        legacy.execute (ButtonEvent.DOWN, 127);
        assertEquals (List.of ("scrollBackwards", "selectPreviousPage"), operations);

    }


    @Test
    void coreAutomationColorsPreserveTheInstalledButtonPalette ()
    {
        final PushColorManager colors = new PushColorManager ();
        assertEquals (PushColorManager.PUSH2_COLOR2_GREY_LO, PushColorManager.resolveCoreButtonColor (colors, ButtonID.AUTOMATION, new RgbColor (30, 30, 30)));
        assertEquals (PushColorManager.PUSH2_COLOR2_RED_HI, PushColorManager.resolveCoreButtonColor (colors, ButtonID.AUTOMATION, new RgbColor (255, 0, 0)));
        assertEquals (PushColorManager.PUSH2_COLOR2_AMBER, PushColorManager.resolveCoreButtonColor (colors, ButtonID.AUTOMATION, new RgbColor (89, 29, 0)));
    }


    @Test
    void coreMixAndMasterButtonsPreserveTheInstalledMonochromeIntensities ()
    {
        final PushColorManager colors = new PushColorManager ();
        for (final ButtonID button: List.of (ButtonID.TRACK, ButtonID.MASTERTRACK))
        {
            assertEquals (30, PushColorManager.resolveCoreButtonColor (colors, button, new RgbColor (60, 60, 60)));
            assertEquals (127, PushColorManager.resolveCoreButtonColor (colors, button, new RgbColor (255, 255, 255)));
            assertEquals (0, PushColorManager.resolveCoreButtonColor (colors, button, new RgbColor (0, 0, 0)));
        }
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
                case "getChannelID" -> "track-" + trackIndex;
                case "getType" -> de.mossgrabers.framework.daw.resource.ChannelType.INSTRUMENT;
                case "getColor" -> ColorEx.BLUE;
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
            case "getPageSize" -> 8;
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
        final PushControlSurface surface = createSurface (valueChanger, relaxedProxy (ISelectedTrackNoteTarget.class), cursorTrack);
        for (int index = 1; index <= 8; index++)
            surface.createAbsoluteKnob (de.mossgrabers.framework.controller.ContinuousID.valueOf ("KNOB" + index), "Knob " + index);
        final UserMode mode = new UserMode (surface, model);
        surface.getModeManager ().register (Modes.USER, mode);
        surface.getModeManager ().apply (new de.mossgrabers.pull.core.api.DesiredControllerPageState (1, de.mossgrabers.pull.core.api.ControllerPageRef.legacy ("USER"), de.mossgrabers.pull.core.api.ControllerPageRef.none (), Optional.empty (), 0));
        final var observed = de.mossgrabers.pull.shell.runtime.PushDevicePageObserver.capture (surface, model);
        mode.onFirstRow (3, ButtonEvent.DOWN);
        mode.onFirstRow (3, ButtonEvent.UP);

        assertEquals ("Track 4", observed.channels ().get (3).name ());
        assertEquals ("track-3", observed.channels ().get (3).id ());
        assertEquals (List.of (Integer.valueOf (3)), selectedTrackIndices);
        assertEquals (List.of (), selectedParameterPages);
    }


    @Test
    void stableRowReleaseCannotSelectASecondTrackAfterWorkspaceActivation ()
    {
        final IValueChanger valueChanger = new TwosComplementValueChanger (128, 1);
        final IParameterBank parameters = relaxedProxy (IParameterBank.class);
        final List<Integer> selections = new ArrayList<> ();
        final ITrack track = proxy (ITrack.class, (proxy, method, arguments) -> {
            if ("select".equals (method.getName ()))
                selections.add (Integer.valueOf (1));
            return relaxedValue (method.getReturnType ());
        });
        final ITrackBank tracks = proxy (ITrackBank.class, (proxy, method, arguments) -> "getItem".equals (method.getName ()) ? track : relaxedValue (method.getReturnType ()));
        final IProject project = proxy (IProject.class, (proxy, method, arguments) -> "getParameterBank".equals (method.getName ()) ? parameters : relaxedValue (method.getReturnType ()));
        final IModel model = proxy (IModel.class, (proxy, method, arguments) -> switch (method.getName ())
        {
            case "getCurrentTrackBank" -> tracks;
            case "getProject" -> project;
            default -> relaxedValue (method.getReturnType ());
        });
        final PushControlSurface surface = createSurface (valueChanger, relaxedProxy (ISelectedTrackNoteTarget.class), relaxedProxy (ICursorTrack.class));
        for (int index = 1; index <= 8; index++)
            surface.createAbsoluteKnob (de.mossgrabers.framework.controller.ContinuousID.valueOf ("KNOB" + index), "Knob " + index);
        surface.getModeManager ().register (Modes.USER, relaxedProxy (IMode.class));
        surface.getModeManager ().register (Modes.WORKSPACE, new CorePageMode ("Workspace", surface, model, new de.mossgrabers.pull.shell.runtime.ReloadableControllerRuntime (relaxedProxy (com.bitwig.extension.controller.api.ControllerHost.class))));
        surface.getModeManager ().setActive (Modes.USER);
        final ButtonRowModeCommand<PushControlSurface, PushConfiguration> command = new ButtonRowModeCommand<> (0, 1, model, surface);

        command.execute (ButtonEvent.DOWN, 127);
        surface.getModeManager ().setActive (Modes.WORKSPACE);
        command.execute (ButtonEvent.UP, 0);

        assertEquals (List.of (), selections);
    }


    @Test
    void masterTouchBrowserGuardReadsNativeActivityBeforeAndAfterPageProjection ()
    {
        final var active = new java.util.concurrent.atomic.AtomicBoolean (true);
        final var calls = new ArrayList<String> ();
        final var browser = proxy (de.mossgrabers.framework.daw.IBrowser.class, (proxy, method, arguments) -> "isActive".equals (method.getName ()) ? active.get () : relaxedValue (method.getReturnType ()));
        final var master = proxy (de.mossgrabers.framework.daw.data.IMasterTrack.class, (proxy, method, arguments) -> {
            if ("touchVolume".equals (method.getName ())) calls.add ("touch:" + arguments[0]);
            if ("resetVolume".equals (method.getName ())) calls.add ("reset");
            return relaxedValue (method.getReturnType ());
        });
        final var model = proxy (IModel.class, (proxy, method, arguments) -> switch (method.getName ()) {
            case "getBrowser" -> browser;
            case "getMasterTrack" -> master;
            default -> relaxedValue (method.getReturnType ());
        });
        final var surface = createSurface (new TwosComplementValueChanger (128, 1), relaxedProxy (ISelectedTrackNoteTarget.class), relaxedProxy (ICursorTrack.class));
        final var pages = surface.getModeManager ();
        final var command = new de.mossgrabers.controller.ableton.push.command.continuous.MastertrackTouchCommand (model, surface);
        command.execute (ButtonEvent.DOWN, 127);
        command.execute (ButtonEvent.UP, 0);
        assertTrue (calls.isEmpty (), "native Browser must guard touch/reset even before its controller page is admitted");
        pages.register (Modes.BROWSER, relaxedProxy (IMode.class));
        pages.apply (new de.mossgrabers.pull.core.api.DesiredControllerPageState (1, de.mossgrabers.pull.core.api.ControllerPageRef.legacy ("BROWSER"), de.mossgrabers.pull.core.api.ControllerPageRef.core ("track", "TRACK"), java.util.Optional.empty (), 0, Set.of ()));
        active.set (false);
        command.execute (ButtonEvent.DOWN, 127);
        command.execute (ButtonEvent.UP, 0);
        assertEquals (List.of ("touch:true", "touch:false"), calls, "a stale projected Browser cannot suppress touch cleanup after the native Browser closes");
    }


    @Test
    void optionBrowserObservationUsesLaterHostReadBackAndBoundsTheLegacyVisibleWindow ()
    {
        final AtomicInteger selected = new AtomicInteger (1);
        final AtomicInteger requests = new AtomicInteger ();
        final AtomicBoolean active = new AtomicBoolean (true);
        final var entries = java.util.stream.IntStream.range (0, 64).mapToObj (index -> proxy (de.mossgrabers.framework.daw.data.IBrowserColumnItem.class, (object, method, args) -> switch (method.getName ())
        {
            case "doesExist" -> true;
            case "getName" -> "Long preset name " + index + " with full observed detail";
            case "isSelected" -> index == selected.get ();
            case "getHitCount" -> index + 100;
            default -> relaxedValue (method.getReturnType ());
        })).toArray (de.mossgrabers.framework.daw.data.IBrowserColumnItem[]::new);
        final var browser = proxy (de.mossgrabers.framework.daw.IBrowser.class, (object, method, args) -> switch (method.getName ())
        {
            case "isActive" -> active.get ();
            case "getFilterColumnCount" -> 0;
            case "getResultColumnItems" -> entries;
            case "getInfoText", "getSelectedContentType", "getSelectedResult" -> "Observed";
            case "selectNextResult" -> { requests.incrementAndGet (); yield null; }
            default -> relaxedValue (method.getReturnType ());
        });
        final IModel model = proxy (IModel.class, (object, method, args) -> switch (method.getName ())
        {
            case "getBrowser" -> browser;
            case "getColorManager" -> new PushColorManager ();
            default -> relaxedValue (method.getReturnType ());
        });
        final PushControlSurface surface = createSurface (new TwosComplementValueChanger (128, 1), relaxedProxy (ISelectedTrackNoteTarget.class), relaxedProxy (ICursorTrack.class));
        final var mode = new de.mossgrabers.controller.ableton.push.mode.device.DeviceBrowserMode (surface, model);
        surface.getModeManager ().register (Modes.BROWSER, mode);
        surface.getModeManager ().apply (new de.mossgrabers.pull.core.api.DesiredControllerPageState (1, de.mossgrabers.pull.core.api.ControllerPageRef.legacy ("BROWSER"), de.mossgrabers.pull.core.api.ControllerPageRef.core ("track", "TRACK"), Optional.empty (), 0, Set.of ()));
        mode.onKnobTouch (7, true);
        mode.onFirstRow (7, ButtonEvent.DOWN);
        assertEquals (1, requests.get ());
        final var before = (de.mossgrabers.pull.core.api.OptionPageState.Browser) de.mossgrabers.pull.shell.runtime.PushOptionPageObserver.capture (surface, model);
        assertEquals (48, before.items ().size ());
        assertTrue (before.items ().get (1).selected ());
        assertFalse (before.items ().get (2).selected (), "request submission must not move the observed selection");
        selected.set (2);
        final var after = (de.mossgrabers.pull.core.api.OptionPageState.Browser) de.mossgrabers.pull.shell.runtime.PushOptionPageObserver.capture (surface, model);
        assertTrue (after.items ().get (2).selected ());
        assertTrue (after.items ().get (2).name ().endsWith ("full observed detail"));
        active.set (false);
        assertTrue (de.mossgrabers.pull.shell.runtime.PushOptionPageObserver.capture (surface, model) instanceof de.mossgrabers.pull.core.api.OptionPageState.Empty);
        final PushControlSurface unsupported = createSurface (new TwosComplementValueChanger (128, 1), relaxedProxy (ISelectedTrackNoteTarget.class), relaxedProxy (ICursorTrack.class));
        assertTrue (de.mossgrabers.pull.shell.runtime.PushOptionPageObserver.capture (unsupported, model) instanceof de.mossgrabers.pull.core.api.OptionPageState.Empty);
    }


    private static PushControlSurface createSurface (final IValueChanger valueChanger, final ISelectedTrackNoteTarget selectedTarget, final ITrack drumModelTrack)
    {
        return createSurface (valueChanger, selectedTarget, drumModelTrack, () -> false);
    }


    private static PushControlSurface createSurface (final IValueChanger valueChanger, final ISelectedTrackNoteTarget selectedTarget, final ITrack drumModelTrack, final BooleanSupplier buttonPressed)
    {
        final IHwButton button = proxy (IHwButton.class, (proxy, method, arguments) -> "isPressed".equals (method.getName ()) ? Boolean.valueOf (buttonPressed.getAsBoolean ()) : relaxedValue (method.getReturnType ()));
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


    private static final class TestPushControllerSetup extends PushControllerSetup
    {
        private TestPushControllerSetup (final IHost host, final ISetupFactory factory, final ISettingsUI settings)
        {
            super (host, factory, settings, settings, null);
        }


        private void install (final PushControlSurface surface, final IModel model)
        {
            this.surface = surface;
            this.model = model;
        }


        private void updateViewForTest ()
        {
            this.updateView ();
        }
    }


}
