// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2017-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.controller.ableton.push.controller;

import java.util.Objects;
import java.util.function.BooleanSupplier;

import de.mossgrabers.controller.ableton.push.PushConfiguration;
import de.mossgrabers.controller.ableton.push.workspace.ControllerWorkspaceHost;
import de.mossgrabers.controller.ableton.push.workspace.SessionBankRegistry;
import de.mossgrabers.framework.controller.AbstractControlSurface;
import de.mossgrabers.framework.controller.ButtonID;
import de.mossgrabers.framework.controller.color.ColorManager;
import de.mossgrabers.framework.controller.hardware.BindType;
import de.mossgrabers.framework.controller.grid.PadColor;
import de.mossgrabers.framework.daw.IHost;
import de.mossgrabers.framework.daw.data.ITrack;
import de.mossgrabers.framework.daw.midi.DeviceInquiry;
import de.mossgrabers.framework.daw.midi.IMidiInput;
import de.mossgrabers.framework.daw.midi.IMidiOutput;
import de.mossgrabers.framework.daw.midi.ISelectedTrackNoteTarget;
import de.mossgrabers.framework.daw.midi.SelectedTrackNoteTargetSnapshot;
import de.mossgrabers.framework.utils.ButtonEvent;
import de.mossgrabers.framework.utils.StringUtils;
import de.mossgrabers.framework.view.Views;
import de.mossgrabers.pull.shell.runtime.ReloadableControllerRuntime;
import de.mossgrabers.pull.core.api.ControlId;
import de.mossgrabers.pull.core.api.ControllerViewFacet;
import de.mossgrabers.pull.core.api.PushControlIds;
import de.mossgrabers.pull.core.api.event.InputKind;
import de.mossgrabers.pull.core.api.event.InputPhase;
import de.mossgrabers.pull.core.api.output.RgbColor;


/**
 * The Push 2 control surface.
 *
 * @author Jürgen Moßgraber
 */
public class PushControlSurface extends AbstractControlSurface<PushConfiguration> implements ColorPalette.Host
{
    /** The tap button. */
    public static final int          PUSH_BUTTON_TAP                      = 3;
    /** The metronome button. */
    public static final int          PUSH_BUTTON_METRONOME                = 9;
    /** The small knob 1 turned. */
    public static final int          PUSH_SMALL_KNOB1                     = 14;
    /** The small knob 2 turned. */
    public static final int          PUSH_SMALL_KNOB2                     = 15;
    /** The button 1 in row 1. */
    public static final int          PUSH_BUTTON_ROW1_1                   = 20;
    /** The button 2 in row 1. */
    public static final int          PUSH_BUTTON_ROW1_2                   = 21;
    /** The button 3 in row 1. */
    public static final int          PUSH_BUTTON_ROW1_3                   = 22;
    /** The button 4 in row 1. */
    public static final int          PUSH_BUTTON_ROW1_4                   = 23;
    /** The button 5 in row 1. */
    public static final int          PUSH_BUTTON_ROW1_5                   = 24;
    /** The button 6 in row 1. */
    public static final int          PUSH_BUTTON_ROW1_6                   = 25;
    /** The button 7 in row 1. */
    public static final int          PUSH_BUTTON_ROW1_7                   = 26;
    /** The button 8 in row 1. */
    public static final int          PUSH_BUTTON_ROW1_8                   = 27;
    /** The master button. */
    public static final int          PUSH_BUTTON_MASTER                   = 28;
    /** The clip stop button. */
    public static final int          PUSH_BUTTON_STOP_CLIP                = 29;
    /** The setup button. */
    public static final int          PUSH_BUTTON_SETUP                    = 30;
    /** The layout button. */
    public static final int          PUSH_BUTTON_LAYOUT                   = 31;
    /** The convert button. */
    public static final int          PUSH_BUTTON_CONVERT                  = 35;
    /** The scene 1 button. */
    public static final int          PUSH_BUTTON_SCENE1                   = 36;                                                                                                                                                                                                                                                                       // 1/4
    /** The scene 2 button. */
    public static final int          PUSH_BUTTON_SCENE2                   = 37;
    /** The scene 3 button. */
    public static final int          PUSH_BUTTON_SCENE3                   = 38;
    /** The scene 4 button. */
    public static final int          PUSH_BUTTON_SCENE4                   = 39;
    /** The scene 5 button. */
    public static final int          PUSH_BUTTON_SCENE5                   = 40;                                                                                                                                                                                                                                                                       // ...
    /** The scene 6 button. */
    public static final int          PUSH_BUTTON_SCENE6                   = 41;
    /** The scene 7 button. */
    public static final int          PUSH_BUTTON_SCENE7                   = 42;
    /** The scene 8 button. */
    public static final int          PUSH_BUTTON_SCENE8                   = 43;                                                                                                                                                                                                                                                                       // 1/32T
    /** The cursor left button. */
    public static final int          PUSH_BUTTON_LEFT                     = 44;
    /** The cursor right button. */
    public static final int          PUSH_BUTTON_RIGHT                    = 45;
    /** The cursor up button. */
    public static final int          PUSH_BUTTON_UP                       = 46;
    /** The cursor down button. */
    public static final int          PUSH_BUTTON_DOWN                     = 47;
    /** The select button. */
    public static final int          PUSH_BUTTON_SELECT                   = 48;
    /** The shift button. */
    public static final int          PUSH_BUTTON_SHIFT                    = 49;
    /** The note button. */
    public static final int          PUSH_BUTTON_NOTE                     = 50;
    /** The session button. */
    public static final int          PUSH_BUTTON_SESSION                  = 51;
    /** The add effect button. */
    public static final int          PUSH_BUTTON_ADD_EFFECT               = 52;
    /** The add track button. */
    public static final int          PUSH_BUTTON_ADD_TRACK                = 53;
    /** The octave down button. */
    public static final int          PUSH_BUTTON_OCTAVE_DOWN              = 54;
    /** The octave up button. */
    public static final int          PUSH_BUTTON_OCTAVE_UP                = 55;
    /** The repeat button. */
    public static final int          PUSH_BUTTON_REPEAT                   = 56;
    /** The accent button. */
    public static final int          PUSH_BUTTON_ACCENT                   = 57;
    /** The scales button. */
    public static final int          PUSH_BUTTON_SCALES                   = 58;
    /** The user mode button. */
    public static final int          PUSH_BUTTON_USER_MODE                = 59;
    /** The mute button. */
    public static final int          PUSH_BUTTON_MUTE                     = 60;
    /** The solo button. */
    public static final int          PUSH_BUTTON_SOLO                     = 61;
    /** The device left button. */
    public static final int          PUSH_BUTTON_DEVICE_LEFT              = 62;
    /** The device right button. */
    public static final int          PUSH_BUTTON_DEVICE_RIGHT             = 63;
    /** The footswitch 1. */
    public static final int          PUSH_FOOTSWITCH1                     = 64;
    /** The footswitch 2. */
    public static final int          PUSH_FOOTSWITCH2                     = 69;
    /** The knob 1. */
    public static final int          PUSH_KNOB1                           = 71;
    /** The knob 2. */
    public static final int          PUSH_KNOB2                           = 72;
    /** The knob 3. */
    public static final int          PUSH_KNOB3                           = 73;
    /** The knob 4. */
    public static final int          PUSH_KNOB4                           = 74;
    /** The knob 5. */
    public static final int          PUSH_KNOB5                           = 75;
    /** The knob 6. */
    public static final int          PUSH_KNOB6                           = 76;
    /** The knob 7. */
    public static final int          PUSH_KNOB7                           = 77;
    /** The knob 8. */
    public static final int          PUSH_KNOB8                           = 78;
    /** The knob 9 - master knob. */
    public static final int          PUSH_KNOB9                           = 79;
    /** The play button. */
    public static final int          PUSH_BUTTON_PLAY                     = 85;
    /** The record button. */
    public static final int          PUSH_BUTTON_RECORD                   = 86;
    /** The new button. */
    public static final int          PUSH_BUTTON_NEW                      = 87;
    /** The duplicate button. */
    public static final int          PUSH_BUTTON_DUPLICATE                = 88;
    /** The automation button. */
    public static final int          PUSH_BUTTON_AUTOMATION               = 89;
    /** The fixed length button. */
    public static final int          PUSH_BUTTON_FIXED_LENGTH             = 90;
    /** The second row button 1. */
    public static final int          PUSH_BUTTON_ROW2_1                   = 102;
    /** The second row button 2. */
    public static final int          PUSH_BUTTON_ROW2_2                   = 103;
    /** The second row button 3. */
    public static final int          PUSH_BUTTON_ROW2_3                   = 104;
    /** The second row button 4. */
    public static final int          PUSH_BUTTON_ROW2_4                   = 105;
    /** The second row button 5. */
    public static final int          PUSH_BUTTON_ROW2_5                   = 106;
    /** The second row button 6. */
    public static final int          PUSH_BUTTON_ROW2_6                   = 107;
    /** The second row button 7. */
    public static final int          PUSH_BUTTON_ROW2_7                   = 108;
    /** The second row button 8. */
    public static final int          PUSH_BUTTON_ROW2_8                   = 109;
    /** The device button. */
    public static final int          PUSH_BUTTON_DEVICE                   = 110;
    /** The browse button. */
    public static final int          PUSH_BUTTON_BROWSE                   = 111;
    /** The track / mix button. */
    public static final int          PUSH_BUTTON_TRACK                    = 112;
    /** The clip button. */
    public static final int          PUSH_BUTTON_CLIP                     = 113;
    /** The quantize button. */
    public static final int          PUSH_BUTTON_QUANTIZE                 = 116;
    /** The double button. */
    public static final int          PUSH_BUTTON_DOUBLE                   = 117;
    /** The delete button. */
    public static final int          PUSH_BUTTON_DELETE                   = 118;
    /** The undo button. */
    public static final int          PUSH_BUTTON_UNDO                     = 119;

    /** The note sent when touching knob 1. */
    public static final int          PUSH_KNOB1_TOUCH                     = 0;
    /** The note sent when touching knob 2. */
    public static final int          PUSH_KNOB2_TOUCH                     = 1;
    /** The note sent when touching knob 3. */
    public static final int          PUSH_KNOB3_TOUCH                     = 2;
    /** The note sent when touching knob 4. */
    public static final int          PUSH_KNOB4_TOUCH                     = 3;
    /** The note sent when touching knob 5. */
    public static final int          PUSH_KNOB5_TOUCH                     = 4;
    /** The note sent when touching knob 6. */
    public static final int          PUSH_KNOB6_TOUCH                     = 5;
    /** The note sent when touching knob 7. */
    public static final int          PUSH_KNOB7_TOUCH                     = 6;
    /** The note sent when touching knob 8. */
    public static final int          PUSH_KNOB8_TOUCH                     = 7;
    /** The note sent when touching the master knob. */
    public static final int          PUSH_KNOB9_TOUCH                     = 8;
    /** The note sent when touching the small knob 1. */
    public static final int          PUSH_SMALL_KNOB1_TOUCH               = 10;
    /** The note sent when touching the small knob 2. */
    public static final int          PUSH_SMALL_KNOB2_TOUCH               = 9;

    /** The MIDI note which is sent when touching the ribbon. */
    public static final int          PUSH_RIBBON_TOUCH                    = 12;

    /** Configure Ribbon as pitch-bend. */
    public static final int          PUSH_RIBBON_PITCHBEND                = 0;
    /** Configure Ribbon as volume slider. */
    public static final int          PUSH_RIBBON_VOLUME                   = 1;
    /** Configure Ribbon as panning. */
    public static final int          PUSH_RIBBON_PAN                      = 2;
    /** Configure Ribbon discrete values. */
    public static final int          PUSH_RIBBON_DISCRETE                 = 3;

    private static final int []      MAXW                                 =
    {
        1700,
        1660,
        1590,
        1510,
        1420,
        1300,
        1170,
        1030,
        860,
        640,
        400
    };
    private static final int []      PUSH2_CPMIN                          =
    {
        1650,
        1580,
        1500,
        1410,
        1320,
        1220,
        1110,
        1000,
        900,
        800,
        700
    };
    private static final int []      PUSH2_CPMAX                          =
    {
        2050,
        1950,
        1850,
        1750,
        1650,
        1570,
        1490,
        1400,
        1320,
        1240,
        1180
    };
    private static final double []   GAMMA                                =
    {
        0.7,
        0.64,
        0.58,
        0.54,
        0.5,
        0.46,
        0.43,
        0.4,
        0.36,
        0.32,
        0.25
    };
    private static final int []      MINV                                 =
    {
        1,
        1,
        1,
        1,
        1,
        1,
        3,
        6,
        12,
        24,
        36
    };
    private static final int []      MAXV                                 =
    {
        96,
        102,
        116,
        121,
        124,
        127,
        127,
        127,
        127,
        127,
        127
    };
    private static final int []      ALPHA                                =
    {
        90,
        70,
        54,
        40,
        28,
        20,
        10,
        -5,
        -25,
        -55,
        -90
    };

    private static final String      SYSEX_HEADER_TEXT                    = "F0 00 21 1D 01 01 ";
    private static final int []      SYSEX_HEADER_BYTES                   =
    {
        0xF0,
        0x00,
        0x21,
        0x1D,
        0x01,
        0x01
    };

    private static final int         PAD_VELOCITY_CURVE_CHUNK_SIZE        = 16;
    private static final int         NUM_VELOCITY_CURVE_ENTRIES           = 128;

    private final ColorPalette                colorPalette;
    private final PushPadGrid                 pushPadGrid;
    private final ReloadableControllerRuntime reloadableRuntime;
    private final ControllerWorkspaceHost      controllerWorkspaceHost;
    private final PushDebugSurfaceHost         debugSurfaceHost;
    private SessionBankRegistry                 sessionBankRegistry;
    private final ISelectedTrackNoteTarget    selectedTrackNoteTarget;
    private final ITrack                      drumModelTrack;
    private final BooleanSupplier             drumModelDeviceReady;
    private BooleanSupplier                   drumPadLayoutActive       = () -> false;
    private BooleanSupplier                   drumControllerEngaged     = () -> false;
    private boolean                           rawPitchbendGestureActive;

    private int                      ribbonMode                           = -1;
    private int                      ribbonValue                          = -1;

    private int                      majorVersion                         = -1;
    private int                      minorVersion                         = -1;
    private int                      buildNumber                          = -1;
    private int                      serialNumber                         = -1;
    private int                      boardRevision                        = -1;

    private int                      currentPadSensitivityPush2           = -1;
    private int                      currentPadGainPush2                  = -1;
    private int                      currentPadDynamicsPush2              = -1;
    private int []                   currentCurve                         = null;


    /**
     * Constructor.
     *
     * @param host The host
     * @param colorManager The color manager
     * @param configuration The configuration
     * @param output The MIDI output
     * @param input The MIDI input
     * @param selectedTrackNoteTarget State for the private selected-track note target
     * @param drumModelTrack Track represented by the framework drum-device model
     * @param drumModelDeviceReady True when the framework drum-device model rendered by the
     *            layout is ready
     * @param reloadableRuntime The stable reloadable-core runtime
     */
    public PushControlSurface (final IHost host, final ColorManager colorManager, final PushConfiguration configuration, final IMidiOutput output, final IMidiInput input, final ISelectedTrackNoteTarget selectedTrackNoteTarget, final ITrack drumModelTrack, final BooleanSupplier drumModelDeviceReady, final ReloadableControllerRuntime reloadableRuntime)
    {
        super (host, configuration, colorManager, output, input, new PushPadGrid (colorManager, output), 200.0, 156.0);

        this.selectedTrackNoteTarget = Objects.requireNonNull (selectedTrackNoteTarget, "selectedTrackNoteTarget");
        this.drumModelTrack = Objects.requireNonNull (drumModelTrack, "drumModelTrack");
        this.drumModelDeviceReady = Objects.requireNonNull (drumModelDeviceReady, "drumModelDeviceReady");
        this.reloadableRuntime = reloadableRuntime;
        this.controllerWorkspaceHost = new ControllerWorkspaceHost (this);
        this.notifyViewChange = false;
        this.pushPadGrid = (PushPadGrid) this.padGrid;
        if (this.reloadableRuntime != null)
            this.pushPadGrid.setOverlaySupplier (this.reloadableRuntime::padGridOverlay);
        this.colorPalette = new ColorPalette (this);
        this.debugSurfaceHost = PushDebugSurfaceHost.createIfEnabled ();
        if (this.debugSurfaceHost != null)
        {
            this.pushPadGrid.setDebugSurfaceObserver ( (oneBasedPad, color, blinkColor, fast) -> {
                final ButtonID button = ButtonID.get (ButtonID.PAD1, oneBasedPad - 1);
                this.debugSurfaceHost.observePad (
                    oneBasedPad,
                    color,
                    this.colorManager.getColor (color, button),
                    blinkColor,
                    this.colorManager.getColor (blinkColor, button),
                    fast);
            });
        }

        this.input.setSysexCallback (this::handleSysEx);
    }


    /** Mirror successful button-light transmissions into the opt-in local debugger. */
    @Override
    public void setTrigger (final BindType bindType, final int channel, final int cc, final int value)
    {
        super.setTrigger (bindType, channel, cc, value);
        if (this.debugSurfaceHost == null)
            return;
        final ButtonID button = debugButtonForMidiControl (cc);
        if (button != null)
            this.debugSurfaceHost.observeButton (button, value, this.colorManager.getColor (value, button));
    }


    /** Sample physical pressed state after the established view, grid, and output flush. */
    @Override
    protected void internalFlushHandler ()
    {
        super.internalFlushHandler ();
        if (this.debugSurfaceHost != null)
            this.debugSurfaceHost.observePressed (this.getButtons ());
    }


    /** Record one successfully submitted debugger edge for the local virtual surface. */
    public void observeDebugInput (final ButtonID button, final ButtonEvent event)
    {
        final int padIndex = button.ordinal () - ButtonID.PAD1.ordinal () + 1;
        final ControlId control = padIndex >= 1 && padIndex <= 64 ? PushControlIds.pad (padIndex) : PushControlIds.button (button.name ());
        final InputKind kind = padIndex >= 1 && padIndex <= 64 ? InputKind.PAD : InputKind.BUTTON;
        final InputPhase phase = switch (event)
        {
            case DOWN -> InputPhase.BEGIN;
            case LONG -> InputPhase.LONG;
            case UP -> InputPhase.END;
        };
        this.observeDebugInput (control, kind, phase, event == ButtonEvent.UP ? 0 : 127);
    }


    /** Record one successfully submitted generic debugger input for the local virtual surface. */
    public void observeDebugInput (final ControlId control, final InputKind kind, final InputPhase phase, final long value)
    {
        if (this.debugSurfaceHost != null)
            this.debugSurfaceHost.observeDebugInput (control, kind, phase, value);
    }


    /** Inject one bounded poly-pressure sample through the same raw MIDI path as Push hardware. */
    public void triggerDebugPadPressure (final int oneBasedPad, final int pressure)
    {
        if (this.debugSurfaceHost == null)
            throw new IllegalStateException ("Push debugging is not enabled");
        if (oneBasedPad < 1 || oneBasedPad > 64 || pressure < 0 || pressure > 127)
            throw new IllegalArgumentException ("Pad pressure requires pad 1..64 and value 0..127");
        this.handleMidi (0xA0, this.pushPadGrid.getStartNote () + oneBasedPad - 1, pressure);
    }


    static ButtonID debugButtonForMidiControl (final int control)
    {
        if (control >= PUSH_BUTTON_ROW1_1 && control <= PUSH_BUTTON_ROW1_8)
            return ButtonID.get (ButtonID.ROW1_1, control - PUSH_BUTTON_ROW1_1);
        if (control >= PUSH_BUTTON_SCENE1 && control <= PUSH_BUTTON_SCENE8)
            return ButtonID.get (ButtonID.SCENE1, PUSH_BUTTON_SCENE8 - control);
        if (control >= PUSH_BUTTON_ROW2_1 && control <= PUSH_BUTTON_ROW2_8)
            return ButtonID.get (ButtonID.ROW2_1, control - PUSH_BUTTON_ROW2_1);

        return switch (control)
        {
            case PUSH_BUTTON_TAP -> ButtonID.TAP_TEMPO;
            case PUSH_BUTTON_METRONOME -> ButtonID.METRONOME;
            case PUSH_BUTTON_MASTER -> ButtonID.MASTERTRACK;
            case PUSH_BUTTON_STOP_CLIP -> ButtonID.STOP_CLIP;
            case PUSH_BUTTON_SETUP -> ButtonID.SETUP;
            case PUSH_BUTTON_LAYOUT -> ButtonID.LAYOUT;
            case PUSH_BUTTON_CONVERT -> ButtonID.CONVERT;
            case PUSH_BUTTON_LEFT -> ButtonID.ARROW_LEFT;
            case PUSH_BUTTON_RIGHT -> ButtonID.ARROW_RIGHT;
            case PUSH_BUTTON_UP -> ButtonID.ARROW_UP;
            case PUSH_BUTTON_DOWN -> ButtonID.ARROW_DOWN;
            case PUSH_BUTTON_SELECT -> ButtonID.SELECT;
            case PUSH_BUTTON_SHIFT -> ButtonID.SHIFT;
            case PUSH_BUTTON_NOTE -> ButtonID.NOTE;
            case PUSH_BUTTON_SESSION -> ButtonID.SESSION;
            case PUSH_BUTTON_ADD_EFFECT -> ButtonID.ADD_EFFECT;
            case PUSH_BUTTON_ADD_TRACK -> ButtonID.ADD_TRACK;
            case PUSH_BUTTON_OCTAVE_DOWN -> ButtonID.OCTAVE_DOWN;
            case PUSH_BUTTON_OCTAVE_UP -> ButtonID.OCTAVE_UP;
            case PUSH_BUTTON_REPEAT -> ButtonID.REPEAT;
            case PUSH_BUTTON_ACCENT -> ButtonID.ACCENT;
            case PUSH_BUTTON_SCALES -> ButtonID.SCALES;
            case PUSH_BUTTON_USER_MODE -> ButtonID.USER;
            case PUSH_BUTTON_MUTE -> ButtonID.MUTE;
            case PUSH_BUTTON_SOLO -> ButtonID.SOLO;
            case PUSH_BUTTON_DEVICE_LEFT -> ButtonID.PAGE_LEFT;
            case PUSH_BUTTON_DEVICE_RIGHT -> ButtonID.PAGE_RIGHT;
            case PUSH_BUTTON_PLAY -> ButtonID.PLAY;
            case PUSH_BUTTON_RECORD -> ButtonID.RECORD;
            case PUSH_BUTTON_NEW -> ButtonID.NEW;
            case PUSH_BUTTON_DUPLICATE -> ButtonID.DUPLICATE;
            case PUSH_BUTTON_AUTOMATION -> ButtonID.AUTOMATION;
            case PUSH_BUTTON_FIXED_LENGTH -> ButtonID.FIXED_LENGTH;
            case PUSH_BUTTON_DEVICE -> ButtonID.DEVICE;
            case PUSH_BUTTON_BROWSE -> ButtonID.BROWSE;
            case PUSH_BUTTON_TRACK -> ButtonID.TRACK;
            case PUSH_BUTTON_CLIP -> ButtonID.CLIP;
            case PUSH_BUTTON_QUANTIZE -> ButtonID.QUANTIZE;
            case PUSH_BUTTON_DOUBLE -> ButtonID.DOUBLE;
            case PUSH_BUTTON_DELETE -> ButtonID.DELETE;
            case PUSH_BUTTON_UNDO -> ButtonID.UNDO;
            default -> null;
        };
    }


    /** {@inheritDoc} */
    @Override
    protected void handleGridNote (final ButtonEvent event, final int note, final int velocity)
    {
        if (this.reloadableRuntime.routeGridEvent (this.isDrumControllerActive (), event, note))
            return;

        super.handleGridNote (event, note, velocity);
    }


    /**
     * Supply authoritative ownership of the composite drum-pad layout. Fill interception follows
     * layout ownership rather than inferring it from a generic view identifier.
     *
     * @param drumPadLayoutActive True while the drum-pad layout owns its physical controls
     */
    public void setDrumPadLayoutActive (final BooleanSupplier drumPadLayoutActive)
    {
        this.drumPadLayoutActive = Objects.requireNonNull (drumPadLayoutActive, "drumPadLayoutActive");
    }


    /**
     * Test whether the composite drum-pad layout currently owns its physical controls.
     *
     * @return True while the drum-pad layout is active
     */
    public boolean isDrumPadLayoutActive ()
    {
        return this.drumPadLayoutActive.getAsBoolean ();
    }


    /**
     * Get the stable adapter for core-owned fixed-facet workspaces.
     *
     * @return The workspace host
     */
    public ControllerWorkspaceHost getControllerWorkspaceHost ()
    {
        return this.controllerWorkspaceHost;
    }


    /**
     * Supply the initialized bounded Session-bank canopy.
     *
     * @param sessionBankRegistry Session-bank registry
     */
    public void setSessionBankRegistry (final SessionBankRegistry sessionBankRegistry)
    {
        if (this.sessionBankRegistry != null)
            throw new IllegalStateException ("Session-bank registry is already installed");
        this.sessionBankRegistry = Objects.requireNonNull (sessionBankRegistry, "sessionBankRegistry");
    }


    /**
     * Get the bounded Session-bank canopy.
     *
     * @return Session-bank registry
     */
    public SessionBankRegistry getSessionBankRegistry ()
    {
        if (this.sessionBankRegistry == null)
            throw new IllegalStateException ("Session-bank registry has not been installed");
        return this.sessionBankRegistry;
    }


    /**
     * Test whether the active layout exposes Session clip navigation.
     *
     * @return True for ordinary Session or a workspace Session grid
     */
    public boolean isSessionLayoutActive ()
    {
        return this.viewManager.isActive (Views.SESSION) || this.controllerWorkspaceHost.hasFacet (ControllerViewFacet.SESSION_CLIP_GRID_UPPER);
    }


    /**
     * Test whether arrows should navigate the Session track and scene banks.
     *
     * @return True when the Session navigation facet is active
     */
    public boolean isSessionNavigationActive ()
    {
        return this.controllerWorkspaceHost.hasFacet (ControllerViewFacet.SESSION_NAVIGATION);
    }


    /**
     * Supply the reconciled drum-controller state. Unlike live capability read-back, this state
     * changes only after the drum controls have completed their engage or disengage transition.
     *
     * @param drumControllerEngaged True after the controller transition has completed
     */
    public void setDrumControllerEngaged (final BooleanSupplier drumControllerEngaged)
    {
        this.drumControllerEngaged = Objects.requireNonNull (drumControllerEngaged, "drumControllerEngaged");
    }


    /**
     * Test whether the authoritative selected-track observer identifies a compatible Drum Machine
     * target. This does not prove that the legacy model cursor represents the same track.
     *
     * @return True if the selected track should use the Drum controller layout
     */
    public boolean isDrumControllerTarget ()
    {
        return isDrumTargetCapable (
            this.selectedTrackNoteTarget.doesExist (),
            this.selectedTrackNoteTarget.canHoldNotes (),
            this.selectedTrackNoteTarget.hasDrumDevice ());
    }


    /**
     * Test whether the selected target is compatible with the drum layout and the framework drum
     * model represents that same track. Layout preference must not be written through a stale or
     * pinned model cursor.
     *
     * @return True if the selected target is drum-capable and aligned with the model cursor
     */
    public boolean isDrumControllerTargetAligned ()
    {
        return this.isDrumControllerTarget () && this.isDrumModelAligned ();
    }


    /**
     * Capture the authoritative private selection-following target.
     *
     * @return The coherent selected-target snapshot
     */
    public SelectedTrackNoteTargetSnapshot getAuthoritativeSelectedTrackSnapshot ()
    {
        return this.selectedTrackNoteTarget.snapshot ();
    }


    /**
     * Test whether the private selected-track observer supports the Pull drum controller and the
     * framework drum model represents that same track. The identity check prevents a pinned model
     * cursor from rendering or mutating a different track than the selected-track observer.
     *
     * @return True if the selected track is a note-capable native Drum Machine track
     */
    public boolean isDrumControllerApplicable ()
    {
        final boolean modelDeviceReady = this.drumModelDeviceReady.getAsBoolean ();
        return isDrumControllerApplicable (this.isDrumControllerTarget (), this.isDrumModelAligned (), modelDeviceReady);
    }


    private boolean isDrumModelAligned ()
    {
        return isDrumModelAligned (
            this.drumModelTrack.doesExist (),
            this.selectedTrackNoteTarget.getChannelID (),
            this.drumModelTrack.getChannelID ());
    }


    /**
     * Test whether the drum layout both owns its controls and has a compatible target.
     *
     * @return True when new drum-controller gestures may be acquired
     */
    public boolean isDrumControllerActive ()
    {
        return isDrumControllerActive (this.isDrumPadLayoutActive (), this.drumControllerEngaged.getAsBoolean ());
    }


    /**
     * Test whether the ribbon currently has direct raw pitch-bend semantics. Session keeps its
     * existing policy; the drum path follows layout ownership rather than a view identifier.
     *
     * @return True when raw pitch bend should be routed and rendered directly
     */
    public boolean isRawPitchbendRoutingActive ()
    {
        final boolean workspacePitchbend = this.controllerWorkspaceHost.hasFacet (ControllerViewFacet.DRUM_PITCH_BEND);
        final boolean standaloneDrumController = !this.controllerWorkspaceHost.isActive () && this.isDrumControllerActive ();
        return isRawPitchbendRoutingActive (this.viewManager.isActive (Views.SESSION), workspacePitchbend || standaloneDrumController);
    }


    /**
     * Acquire raw pitch-bend routing for the current ribbon touch. Once acquired, the lease stays
     * active until release even if selection or view state changes in the meantime.
     *
     * @return True if raw routing was acquired
     */
    public boolean beginRawPitchbendGesture ()
    {
        if (!this.isRawPitchbendRoutingActive ())
            return false;

        this.rawPitchbendGestureActive = true;
        return true;
    }


    /**
     * Release the current raw pitch-bend routing lease.
     *
     * @return True if a raw gesture had been active
     */
    public boolean endRawPitchbendGesture ()
    {
        final boolean wasActive = this.rawPitchbendGestureActive;
        this.rawPitchbendGestureActive = false;
        return wasActive;
    }


    /**
     * Test whether pitch-bend data should be routed raw, including an in-flight gesture lease.
     *
     * @return True when raw pitch-bend data should be routed
     */
    public boolean shouldRouteRawPitchbend ()
    {
        return shouldRouteRawPitchbend (this.isRawPitchbendRoutingActive (), this.rawPitchbendGestureActive);
    }


    static boolean isDrumControllerActive (final boolean layoutActive, final boolean controllerEngaged)
    {
        return layoutActive && controllerEngaged;
    }


    static boolean isDrumTargetCapable (final boolean targetExists, final boolean targetCanHoldNotes, final boolean targetHasDrumDevice)
    {
        return targetExists && targetCanHoldNotes && targetHasDrumDevice;
    }


    static boolean isDrumModelAligned (final boolean modelTrackExists, final String selectedTargetID, final String modelTrackID)
    {
        return modelTrackExists && selectedTargetID != null && !selectedTargetID.isEmpty () && selectedTargetID.equals (modelTrackID);
    }


    static boolean isDrumControllerApplicable (final boolean targetCapable, final boolean modelAligned, final boolean modelDeviceReady)
    {
        return targetCapable && modelAligned && modelDeviceReady;
    }


    static boolean isRawPitchbendRoutingActive (final boolean sessionActive, final boolean drumControllerActive)
    {
        return sessionActive || drumControllerActive;
    }


    static boolean shouldRouteRawPitchbend (final boolean currentPolicyActive, final boolean gestureLeaseActive)
    {
        return currentPolicyActive || gestureLeaseActive;
    }


    /**
     * Fade a pad to the expected target color using the Push 2 firmware transition.
     *
     * @param note The physical Push pad note
     * @param targetColor The unresolved expected target color
     */
    public void requestPadFade (final int note, final PadColor targetColor)
    {
        this.pushPadGrid.requestFade (note, targetColor);
    }


    /**
     * Cancel a pending pad fade.
     *
     * @param note The physical Push pad note
     */
    public void cancelPadFade (final int note)
    {
        this.pushPadGrid.cancelFade (note);
    }


    /** Begin observing successful transmissions for one physical Push pad. */
    public void beginDebugPadObservation (final int oneBasedPad)
    {
        this.pushPadGrid.beginDebugObservation (this.debugPadNote (oneBasedPad));
    }


    /** Snapshot one observed pad's resolved light and successful outbound MIDI state. */
    public DebugPadOutput debugPadOutput (final int oneBasedPad)
    {
        final int note = this.debugPadNote (oneBasedPad);
        final PushPadGrid.DebugObservation observed = this.pushPadGrid.debugObservation (note);
        return new DebugPadOutput (
            oneBasedPad, note, observed.color (), observed.blinkColor (), observed.fast (),
            toDebugTransmission (observed.base ()), toDebugTransmission (observed.blink ()));
    }


    /** Resolve hardware-independent RGB through the same Push palette used by pad rendering. */
    public int resolveDebugPadColor (final RgbColor color)
    {
        return PushColorManager.resolveCoreColor (this.colorManager, Objects.requireNonNull (color, "color"));
    }


    /** End the single bounded pad-transmission observation lane. */
    public void endDebugPadObservation (final int oneBasedPad)
    {
        this.pushPadGrid.endDebugObservation (this.debugPadNote (oneBasedPad));
    }


    private int debugPadNote (final int oneBasedPad)
    {
        if (oneBasedPad < 1 || oneBasedPad > 64)
            throw new IllegalArgumentException ("Push pad must be between 1 and 64.");
        return this.pushPadGrid.getStartNote () + oneBasedPad - 1;
    }


    private static DebugPadTransmission toDebugTransmission (final PushPadGrid.Transmission transmission)
    {
        return new DebugPadTransmission (transmission.revision (), transmission.channel (), transmission.note (), transmission.color ());
    }


    /** Immutable debug-only view of one resolved pad output and its matching transmissions. */
    public record DebugPadOutput (int oneBasedPad, int midiNote, int color, int blinkColor, boolean fast, DebugPadTransmission base, DebugPadTransmission blink)
    {
    }


    /** One successful outbound pad MIDI transmission. */
    public record DebugPadTransmission (long revision, int channel, int note, int color)
    {
    }


    /** {@inheritDoc} */
    @Override
    protected void internalShutdown ()
    {
        this.setRibbonMode (PUSH_RIBBON_PITCHBEND);
        this.setRibbonValue (0);

        super.internalShutdown ();
        if (this.debugSurfaceHost != null)
            this.debugSurfaceHost.close ();
    }


    /** {@inheritDoc} */
    @Override
    protected void handleMidi (final int status, final int data1, final int data2)
    {
        // Observe the physical release below the command layer: consumed pad commands suppress
        // their normal UP callback, but an acquired fill lease must still be released.
        this.reloadableRuntime.routePhysicalMidiRelease (this.isDrumControllerActive (), status, data1, data2);

        if (this.reloadableRuntime.routeControllerMidi (status, data1, data2, () -> PushControlSurface.super.handleMidi (status, data1, data2)))
            return;

        // Ignore active sensing, which seems to be sent from some Push devices
        if (status == 254)
            return;

        super.handleMidi (status, data1, data2);
    }


    /**
     * Set the ribbon mode on the Push controller.
     *
     * @param mode The mode to set
     */
    public void setRibbonMode (final int mode)
    {
        if (this.ribbonMode == mode)
            return;
        this.ribbonMode = mode;
        // See section 2.10.1 in Push 2 programmer manual for status codes
        int status = 0;
        switch (mode)
        {
            case PUSH_RIBBON_PITCHBEND:
                status = 104;
                break;
            case PUSH_RIBBON_VOLUME:
                status = 1;
                break;
            case PUSH_RIBBON_PAN:
                status = 17;
                break;
            case PUSH_RIBBON_DISCRETE:
                status = 9;
                break;
            default:
                break;
        }
        this.sendSysex (new int []
        {
            23,
            status
        });
    }


    /**
     * Set the display value of the ribbon on the Push controller.
     *
     * @param value The value to set
     */
    public void setRibbonValue (final int value)
    {
        if (this.ribbonValue == value)
            return;
        this.ribbonValue = value;
        this.output.sendPitchbend (0, value);
    }


    /**
     * Set the pad sensitivity of Push 2.
     */
    public void sendPadSensitivityPush2 ()
    {
        this.sendPadVelocityCurvePush2 ();
        this.sendPadThresholdPush2 ();
    }


    /**
     * Sets the Push 2 pads aftertouch either to poly or channel pressure.
     *
     * @param isPolyPressure Set poly pressure if true otherwise channel pressure
     */
    public void sendPressureMode (final boolean isPolyPressure)
    {
        this.sendSysex ("1E 0" + (isPolyPressure ? "1" : "0"));
    }


    /**
     * Send the pad threshold.
     */
    private void sendPadThresholdPush2 ()
    {
        final int [] args = new int [9];
        args[0] = 27;
        add7L5M (args, 1, 33); // threshold0
        add7L5M (args, 3, 31); // threshold1
        final int padSensitivity = this.configuration.getPadSensitivityPush2 ();
        add7L5M (args, 5, PUSH2_CPMIN[padSensitivity]); // cpmin
        add7L5M (args, 7, PUSH2_CPMAX[padSensitivity]); // cpmax
        this.sendSysex (args);
    }


    /**
     * Set the pad velocity of Push 2.
     */
    private void sendPadVelocityCurvePush2 ()
    {
        final int [] velocities = this.createPadSensitivityCurvePush2 ();
        for (int index = 0; index < velocities.length; index += PAD_VELOCITY_CURVE_CHUNK_SIZE)
        {
            final int [] args = new int [2 + PAD_VELOCITY_CURVE_CHUNK_SIZE];
            args[0] = 32;
            args[1] = index;
            for (int i = 0; i < PAD_VELOCITY_CURVE_CHUNK_SIZE; i++)
                args[i + 2] = velocities[index + i];
            this.sendSysex (args);
        }
    }


    /**
     * Send the display brightness.
     */
    public void sendDisplayBrightness ()
    {
        final int brightness = this.configuration.getDisplayBrightness () * 255 / 100;
        this.sendSysex (new int []
        {
            8,
            brightness & 127,
            brightness >> 7 & 1
        });
    }


    /**
     * Send the LED brightness.
     */
    public void sendLEDBrightness ()
    {
        final int brightness = this.configuration.getLedBrightness () * 127 / 100;
        this.sendSysex (new int []
        {
            6,
            brightness
        });
    }


    /**
     * Send SysEx to the Push 2.
     *
     * @param parameters The parameters to send
     */
    public void sendSysex (final int [] parameters)
    {
        this.output.sendSysex (SYSEX_HEADER_TEXT + StringUtils.toHexStr (parameters) + "F7");
    }


    /**
     * Send SysEx to the Push 2.
     *
     * @param parameters The parameters to send
     */
    public void sendSysex (final String parameters)
    {
        this.output.sendSysex (SYSEX_HEADER_TEXT + parameters + " F7");
    }


    /**
     * Get the pad sensitivity curve for the Push 2.
     *
     * @return The curve with 128 entries
     */
    public int [] createPadSensitivityCurvePush2 ()
    {
        final int sensitivity = this.configuration.getPadSensitivityPush2 ();
        final int gain = this.configuration.getPadGainPush2 ();
        final int dynamics = this.configuration.getPadDynamicsPush2 ();
        if (this.currentPadSensitivityPush2 == sensitivity && this.currentPadGainPush2 == gain && this.currentPadDynamicsPush2 == dynamics)
            return this.currentCurve;
        this.currentPadSensitivityPush2 = sensitivity;
        this.currentPadGainPush2 = gain;
        this.currentPadDynamicsPush2 = dynamics;

        final int minw = 160;
        final int maxw = MAXW[sensitivity];
        final int minv = MINV[gain];
        final int maxv = MAXV[gain];
        final double [] result = calculatePointsPush2 (ALPHA[dynamics]);
        final double p1x = result[0];
        final double p1y = result[1];
        final double p2x = result[2];
        final double p2y = result[3];
        final int [] curve = new int [NUM_VELOCITY_CURVE_ENTRIES];
        final int minwIndex = minw / 32;
        final int maxwIndex = maxw / 32;
        double t = 0.0;

        double w;
        for (int index = 0; index < NUM_VELOCITY_CURVE_ENTRIES; index++)
        {
            w = index * 32.0;
            double velocity;

            if (w <= minw)
                velocity = 1.0 + (minv - 1.0) * index / minwIndex;
            else if (w >= maxw)
                velocity = maxv + (127.0 - maxv) * (index - maxwIndex) / (128 - maxwIndex);
            else
            {
                final double wnorm = (w - minw) / (maxw - minw);
                final double [] bez = bezierPush2 (wnorm, t, p1x, p1y, p2x, p2y);
                final double b = bez[0];
                t = bez[1];
                final double velonorm = gammaFunc (b, GAMMA[gain]);
                velocity = minv + velonorm * (maxv - minv);
            }
            curve[index] = Math.clamp (Math.round (velocity), 1, 127);
        }

        this.currentCurve = curve;
        return curve;
    }


    private static double [] bezierPush2 (final double x, final double t, final double p1x, final double p1y, final double p2x, final double p2y)
    {
        final double p0x = 0.0;
        final double p0y = 0.0;
        final double p3x = 1.0;
        final double p3y = 1.0;
        double s;
        double t2;
        double t3;
        double s2;
        double s3;
        double xt;
        double tl = t;
        while (tl <= 1.0)
        {
            s = 1 - tl;
            t2 = tl * tl;
            t3 = t2 * tl;
            s2 = s * s;
            s3 = s2 * s;
            xt = s3 * p0x + 3 * tl * s2 * p1x + 3 * t2 * s * p2x + t3 * p3x;
            if (xt >= x)
                return new double []
                {
                    s3 * p0y + 3 * tl * s2 * p1y + 3 * t2 * s * p2y + t3 * p3y,
                    tl
                };
            tl += 0.0001;
        }
        return new double []
        {
            1.0,
            tl
        };
    }


    private static double [] calculatePointsPush2 (final double alpha)
    {
        final double a1 = (225.0 - alpha) * Math.PI / 180.0;
        final double a2 = (45.0 - alpha) * Math.PI / 180.0;
        final double r = 0.4;
        return new double []
        {
            0.5 + r * Math.cos (a1),
            0.5 + r * Math.sin (a1),
            0.5 + r * Math.cos (a2),
            0.5 + r * Math.sin (a2)
        };
    }


    private static double gammaFunc (final double x, final double gamma)
    {
        return Math.pow (x, Math.exp (-4.0 + 8.0 * gamma));
    }


    private static void add7L5M (final int [] array, final int index, final int value)
    {
        array[index] = value & 127;
        array[index + 1] = value >> 7 & 31;
    }


    /**
     * Handle incoming system exclusive data.
     *
     * @param data The data
     */
    private void handleSysEx (final String data)
    {
        final int [] byteData = StringUtils.fromHexStr (data);
        final DeviceInquiry deviceInquiry = new DeviceInquiry (byteData);
        if (deviceInquiry.isValid ())
        {
            this.handleDeviceInquiryResponse (deviceInquiry);
            return;
        }

        if (isPush2Data (byteData))
            this.colorPalette.handleColorPaletteMessage (byteData);
    }


    private static boolean isPush2Data (final int [] data)
    {
        if (data.length + 1 < SYSEX_HEADER_BYTES.length)
            return false;

        for (int i = 0; i < SYSEX_HEADER_BYTES.length; i++)
        {
            if (SYSEX_HEADER_BYTES[i] != data[i])
                return false;
        }

        return data[data.length - 1] == 0xF7;
    }


    /**
     * Handle the response of a device inquiry.
     *
     * @param deviceInquiry The parsed response
     */
    private void handleDeviceInquiryResponse (final DeviceInquiry deviceInquiry)
    {
        final int [] unspecifiedData = deviceInquiry.getUnspecifiedData ();
        if (unspecifiedData.length < 10)
            return;

        this.majorVersion = unspecifiedData[0];
        this.minorVersion = unspecifiedData[1];
        this.buildNumber = unspecifiedData[2] + (unspecifiedData[3] << 7);
        this.serialNumber = unspecifiedData[4] + (unspecifiedData[5] << 7) + (unspecifiedData[6] << 14) + (unspecifiedData[7] << 21) + (unspecifiedData[8] << 28);
        this.boardRevision = unspecifiedData[9];
    }


    /**
     * Get the major hardware version.
     *
     * @return The major hardware version.
     */
    public int getMajorVersion ()
    {
        return this.majorVersion;
    }


    /**
     * Set the major hardware version.
     *
     * @param majorVersion The major hardware version.
     */
    public void setMajorVersion (final int majorVersion)
    {
        this.majorVersion = majorVersion;
    }


    /**
     * Get the minor hardware version.
     *
     * @return The minor hardware version.
     */
    public int getMinorVersion ()
    {
        return this.minorVersion;
    }


    /**
     * Set the minor hardware version.
     *
     * @param minorVersion The major hardware version.
     */
    public void setMinorVersion (final int minorVersion)
    {
        this.minorVersion = minorVersion;
    }


    /**
     * Get the firmware build number.
     *
     * @return The build number
     */
    public int getBuildNumber ()
    {
        return this.buildNumber;
    }


    /**
     * Get the hardware board revision number.
     *
     * @return The number
     */
    public int getBoardRevision ()
    {
        return this.boardRevision;
    }


    /**
     * Get the controller serial number.
     *
     * @return The number
     */
    public int getSerialNumber ()
    {
        return this.serialNumber;
    }


    /**
     * Request the full color palette.
     */
    public void updateColorPalette ()
    {
        this.colorPalette.updatePalette ();
    }


    /** {@inheritDoc} */
    @Override
    public void notifyPaletteStatus (final String message)
    {
        this.getDisplay ().notify (message);
    }


}
