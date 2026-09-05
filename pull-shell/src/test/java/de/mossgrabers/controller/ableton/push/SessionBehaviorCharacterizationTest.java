// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.controller.ableton.push;

import de.mossgrabers.controller.ableton.push.command.trigger.PageLeftCommand;
import de.mossgrabers.controller.ableton.push.command.trigger.PageRightCommand;
import de.mossgrabers.controller.ableton.push.command.trigger.PushCursorCommand;
import de.mossgrabers.controller.ableton.push.controller.PushColorManager;
import de.mossgrabers.controller.ableton.push.controller.PushControlSurface;
import de.mossgrabers.controller.ableton.push.view.SessionView;
import de.mossgrabers.controller.ableton.push.view.WorkspaceView;
import de.mossgrabers.controller.ableton.push.workspace.SessionBankRegistry;
import de.mossgrabers.controller.ableton.push.workspace.WorkspaceFacetAdapter;
import de.mossgrabers.framework.command.trigger.Direction;
import de.mossgrabers.framework.controller.ButtonID;
import de.mossgrabers.framework.controller.color.ColorEx;
import de.mossgrabers.framework.controller.display.IDisplay;
import de.mossgrabers.framework.controller.grid.PadColor;
import de.mossgrabers.framework.controller.grid.PadLight;
import de.mossgrabers.framework.controller.valuechanger.TwosComplementValueChanger;
import de.mossgrabers.framework.daw.IBrowser;
import de.mossgrabers.framework.daw.IHost;
import de.mossgrabers.framework.daw.IModel;
import de.mossgrabers.framework.daw.ITransport;
import de.mossgrabers.framework.daw.data.IScene;
import de.mossgrabers.framework.daw.data.ISlot;
import de.mossgrabers.framework.daw.data.ITrack;
import de.mossgrabers.framework.daw.data.bank.ISceneBank;
import de.mossgrabers.framework.daw.data.bank.ISlotBank;
import de.mossgrabers.framework.daw.data.bank.ITrackBank;
import de.mossgrabers.framework.daw.midi.IMidiInput;
import de.mossgrabers.framework.daw.midi.IMidiOutput;
import de.mossgrabers.framework.daw.midi.ISelectedTrackNoteTarget;
import de.mossgrabers.framework.featuregroup.IMode;
import de.mossgrabers.framework.featuregroup.IView;
import de.mossgrabers.framework.mode.Modes;
import de.mossgrabers.framework.scale.Scales;
import de.mossgrabers.framework.utils.ButtonEvent;
import de.mossgrabers.framework.view.AbstractSessionView;
import de.mossgrabers.framework.view.Views;
import de.mossgrabers.pull.core.api.ControllerViewFacet;
import de.mossgrabers.pull.core.api.DesiredControllerWorkspace;
import de.mossgrabers.pull.core.api.SessionBankShape;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * Characterizes the existing Session policy boundary, not Bitwig or physical-router integration.
 * Host commands append requests; only advanceHost applies their fake read-back. These tests keep
 * surprising legacy release behavior visible so a migration cannot change it accidentally.
 */
class SessionBehaviorCharacterizationTest
{
    @ParameterizedTest
    @ValueSource(ints = { 4, 8 })
    void fullAndUpperLayoutsAddressTheirOwnVisibleRows (final int rows)
    {
        final Fixture fixture = new Fixture (rows);
        fixture.view.onGridNote (note (0, 0), 100);
        fixture.view.onGridNote (note (0, 0), 0);
        fixture.view.onGridNote (note (7, rows - 1), 100);
        fixture.view.onGridNote (note (7, rows - 1), 0);

        assertEquals (List.of ("slot:0:0:select", "slot:0:0:launch:true:false", "slot:0:0:launch:false:false",
            "slot:7:" + (rows - 1) + ":select", "slot:7:" + (rows - 1) + ":launch:true:false", "slot:7:" + (rows - 1) + ":launch:false:false"), fixture.requests);
        assertFalse (fixture.slots[0][0].playing);
        fixture.advanceHost ();
        assertTrue (fixture.slots[0][0].playing);
        if (rows == 4)
        {
            fixture.requests.clear ();
            fixture.view.onGridNote (note (0, 4), 100);
            fixture.view.onGridNote (note (0, 4), 0);
            assertTrue (fixture.requests.isEmpty (), "lower four rows belong to another view");
        }
    }


    @ParameterizedTest
    @ValueSource(ints = { 4, 8 })
    void launchSelectionAndAlternateReleaseUseCurrentLegacyConfiguration (final int rows)
    {
        final Fixture fixture = new Fixture (rows);
        fixture.configuration.selectOnLaunch = false;
        fixture.surface.pressed.add (ButtonID.SHIFT);
        fixture.view.onGridNote (note (2, 1), 100);
        fixture.view.onGridNote (note (2, 1), 0);
        assertEquals (List.of ("slot:2:1:launch:true:true", "slot:2:1:launch:false:true"), fixture.requests);

        fixture.requests.clear ();
        fixture.view.onGridNote (note (2, 1), 100);
        fixture.surface.pressed.clear ();
        fixture.view.onGridNote (note (2, 1), 0);
        assertEquals (List.of ("slot:2:1:launch:true:true", "slot:2:1:launch:false:false"), fixture.requests,
            "legacy release re-reads Shift; target/lane freezing needs an explicit migration decision");
    }


    @ParameterizedTest
    @ValueSource(ints = { 4, 8 })
    void gridModifierPrecedenceAndConsumptionRemainVisible (final int rows)
    {
        final Fixture fixture = new Fixture (rows);
        fixture.surface.pressed.addAll (Set.of (ButtonID.SELECT, ButtonID.DELETE, ButtonID.DUPLICATE, ButtonID.STOP_CLIP, ButtonID.BROWSE));
        fixture.view.onGridNote (note (0, 0), 100);
        assertEquals (List.of ("slot:0:0:select"), fixture.requests);
        assertEquals (Set.of (ButtonID.SELECT), fixture.surface.consumed);
        assertEquals (1, fixture.surface.padConsumptions);

        fixture.requests.clear ();
        fixture.surface.pressed.remove (ButtonID.SELECT);
        fixture.view.onGridNote (note (0, 0), 100);
        assertEquals (List.of ("slot:0:0:remove"), fixture.requests);
        assertTrue (fixture.surface.consumed.contains (ButtonID.DELETE));
        assertTrue (fixture.slots[0][0].content, "delete submission does not change read-back");
        fixture.advanceHost ();
        assertFalse (fixture.slots[0][0].content);

        fixture.requests.clear ();
        fixture.surface.pressed.removeAll (Set.of (ButtonID.DELETE, ButtonID.DUPLICATE));
        fixture.surface.pressed.add (ButtonID.SHIFT);
        fixture.view.onGridNote (note (1, 0), 100);
        assertEquals (List.of ("track:1:stop:true"), fixture.requests);
        assertTrue (fixture.surface.consumed.contains (ButtonID.STOP_CLIP));

        fixture.requests.clear ();
        fixture.surface.pressed.remove (ButtonID.STOP_CLIP);
        fixture.view.onGridNote (note (1, 0), 100);
        assertEquals (List.of ("browser:slot:1:0"), fixture.requests);
        assertTrue (fixture.surface.consumed.contains (ButtonID.BROWSE));
    }


    @ParameterizedTest
    @ValueSource(ints = { 4, 8 })
    void duplicateCapturesContentThenPastesIntoAnEmptySlot (final int rows)
    {
        final Fixture fixture = new Fixture (rows);
        fixture.slots[1][0].content = false;
        fixture.surface.pressed.addAll (Set.of (ButtonID.DUPLICATE, ButtonID.STOP_CLIP, ButtonID.BROWSE));
        fixture.view.onGridNote (note (0, 0), 100);
        assertTrue (fixture.requests.isEmpty ());
        fixture.view.onGridNote (note (1, 0), 100);
        assertEquals (List.of ("slot:1:0:paste:slot:0:0"), fixture.requests);
        assertFalse (fixture.slots[1][0].content);
        fixture.advanceHost ();
        assertTrue (fixture.slots[1][0].content);
        assertTrue (fixture.surface.consumed.contains (ButtonID.DUPLICATE));
    }


    @ParameterizedTest
    @ValueSource(ints = { 4, 8 })
    void armedEmptyPadsPreserveRecordCreateAndNoActionBranches (final int rows)
    {
        final Fixture fixture = new Fixture (rows);
        fixture.armed[0] = true;
        fixture.slots[0][0].content = false;
        fixture.configuration.armedPadAction = 0;
        fixture.view.onGridNote (note (0, 0), 100);
        fixture.view.onGridNote (note (0, 0), 0);
        assertEquals (List.of ("slot:0:0:select", "model:record:slot:0:0", "model:record:slot:0:0"), fixture.requests,
            "legacy recording policy runs on both edges when host content has not advanced");
        assertFalse (fixture.slots[0][0].recording);
        fixture.advanceHost ();
        assertTrue (fixture.slots[0][0].recording);

        fixture.requests.clear ();
        fixture.slots[0][0].content = false;
        fixture.configuration.armedPadAction = 1;
        fixture.view.onGridNote (note (0, 0), 100);
        assertEquals (List.of ("slot:0:0:select", "model:create:slot:0:0:16:true"), fixture.requests);

        fixture.requests.clear ();
        fixture.configuration.armedPadAction = 2;
        fixture.view.onGridNote (note (0, 0), 100);
        fixture.view.onGridNote (note (0, 0), 0);
        assertEquals (List.of ("slot:0:0:select"), fixture.requests);
    }


    @ParameterizedTest
    @ValueSource(ints = { 4, 8 })
    void scenePressSelectsAndNotifiesButLightsWaitForHostReadback (final int rows)
    {
        final Fixture fixture = new Fixture (rows);
        assertEquals (AbstractSessionView.COLOR_SCENE, fixture.view.getButtonColorID (ButtonID.SCENE1));
        fixture.scene (ButtonID.SCENE1, ButtonEvent.DOWN);
        fixture.scene (ButtonID.SCENE1, ButtonEvent.LONG);
        fixture.scene (ButtonID.SCENE1, ButtonEvent.UP);
        assertEquals (List.of ("scene:0:select", "notify:Scene 0", "scene:0:launch:true:false", "scene:0:launch:false:false"), fixture.requests);
        assertEquals (AbstractSessionView.COLOR_SCENE, fixture.view.getButtonColorID (ButtonID.SCENE1));
        assertFalse (fixture.scenes[0].playing);
        fixture.advanceHost ();
        assertEquals (AbstractSessionView.COLOR_SELECTED_SCENE, fixture.view.getButtonColorID (ButtonID.SCENE1));
        assertTrue (fixture.scenes[0].playing);
        fixture.scenes[0].exists = false;
        assertEquals (AbstractSessionView.COLOR_SCENE_OFF, fixture.view.getButtonColorID (ButtonID.SCENE1));
        fixture.requests.clear ();
        fixture.scene (ButtonID.SCENE1, ButtonEvent.DOWN);
        fixture.scene (ButtonID.SCENE1, ButtonEvent.UP);
        assertTrue (fixture.requests.isEmpty ());
        if (rows == 4)
        {
            fixture.scene (ButtonID.SCENE5, ButtonEvent.DOWN);
            fixture.scene (ButtonID.SCENE5, ButtonEvent.UP);
            assertTrue (fixture.requests.isEmpty (), "upper facet does not own lower scene keys");
        }
    }


    @ParameterizedTest
    @ValueSource(ints = { 4, 8 })
    void sceneCombinationsKeepTheirLegacyReleaseBehavior (final int rows)
    {
        final Fixture fixture = new Fixture (rows);
        fixture.surface.pressed.addAll (Set.of (ButtonID.SELECT, ButtonID.SHIFT));
        fixture.scene (ButtonID.SCENE1, ButtonEvent.DOWN);
        fixture.scene (ButtonID.SCENE1, ButtonEvent.UP);
        assertEquals (List.of ("scene:0:select", "notify:Scene 0", "scene:0:launch:false:true"), fixture.requests,
            "Select suppresses DOWN launch but the existing UP still sends alternate release");

        fixture.requests.clear ();
        fixture.surface.pressed.addAll (Set.of (ButtonID.DELETE, ButtonID.DUPLICATE));
        fixture.scene (ButtonID.SCENE1, ButtonEvent.DOWN);
        fixture.scene (ButtonID.SCENE1, ButtonEvent.UP);
        assertEquals (List.of ("scene:0:remove", "scene:0:launch:false:true"), fixture.requests);
        assertTrue (fixture.surface.consumed.contains (ButtonID.DELETE));

        fixture.requests.clear ();
        fixture.surface.pressed.remove (ButtonID.DELETE);
        fixture.scene (ButtonID.SCENE1, ButtonEvent.DOWN);
        fixture.scene (ButtonID.SCENE1, ButtonEvent.UP);
        assertEquals (List.of ("scene:0:duplicate", "scene:0:launch:false:true"), fixture.requests);
        assertTrue (fixture.surface.consumed.contains (ButtonID.DUPLICATE));
    }


    @ParameterizedTest
    @ValueSource(ints = { 4, 8 })
    void birdsEyeConsumesOnlyPressAndUsesEachLayoutsPageSize (final int rows)
    {
        final Fixture fixture = new Fixture (rows);
        fixture.surface.pressed.addAll (Set.of (ButtonID.SHIFT, ButtonID.SELECT, ButtonID.DELETE));
        fixture.view.onGridNote (note (2, 1), 100);
        fixture.view.onGridNote (note (2, 1), 0);
        assertEquals (List.of ("tracks:scrollTo:16", "scenes:scrollTo:" + rows), fixture.requests);
        assertEquals (0, fixture.trackOffset);
        assertEquals (0, fixture.sceneOffset);
        fixture.advanceHost ();
        assertEquals (16, fixture.trackOffset);
        assertEquals (rows, fixture.sceneOffset);
        assertTrue (fixture.surface.consumed.isEmpty (), "birds-eye bypasses normal modifier combinations");
    }


    @Test
    void gridLightPriorityUsesObservedSlotState ()
    {
        final Fixture fixture = new Fixture (8);
        final Slot slot = fixture.slots[0][0];
        final PadColor rgb = PadColor.rgb (Slot.COLOR);
        final PadColor green = PadColor.indexed (PushColorManager.PUSH2_COLOR2_GREEN);
        final PadColor rose = PadColor.indexed (PushColorManager.PUSH2_COLOR2_ROSE);
        assertEquals (new PadLight (rgb), fixture.view.getPadColor (slot.proxy, false));
        fixture.view.onGridNote (note (0, 0), 100);
        assertEquals (new PadLight (rgb), fixture.view.getPadColor (slot.proxy, false));
        fixture.advanceHost ();
        assertEquals (new PadLight (rgb, green, false), fixture.view.getPadColor (slot.proxy, false));

        slot.stopQueued = true;
        assertEquals (new PadLight (rgb, green, true), fixture.view.getPadColor (slot.proxy, false));
        slot.playingQueued = true;
        slot.recording = true;
        assertEquals (new PadLight (rgb, rose, false), fixture.view.getPadColor (slot.proxy, false));
        slot.recordingQueued = true;
        assertEquals (new PadLight (rose, PadColor.indexed (PushColorManager.PUSH2_COLOR2_BLACK), true), fixture.view.getPadColor (slot.proxy, false));

        slot.playing = false;
        slot.stopQueued = false;
        slot.playingQueued = false;
        slot.recording = false;
        slot.recordingQueued = false;
        slot.muted = true;
        assertEquals (new PadLight (PadColor.indexed (PushColorManager.PUSH2_COLOR2_GREY_LO)), fixture.view.getPadColor (slot.proxy, false));
        slot.muted = false;
        assertEquals (new PadLight (rgb, PadColor.indexed (PushColorManager.PUSH2_COLOR2_WHITE), false), fixture.view.getPadColor (slot.proxy, false));
        slot.content = false;
        assertEquals (new PadLight (PadColor.indexed (PushColorManager.PUSH2_COLOR2_RECORD_ARMED_DIM)), fixture.view.getPadColor (slot.proxy, true));
        fixture.configuration.drawRecordStripe = false;
        assertEquals (new PadLight (PadColor.indexed (PushColorManager.PUSH2_COLOR2_BLACK)), fixture.view.getPadColor (slot.proxy, true));
    }


    @ParameterizedTest
    @ValueSource(ints = { 4, 8 })
    void legacyFullSessionArrowsAndFrozenPageActionsRemainWhileCompositeArrowsAreCoreOwned (final int rows)
    {
        final Fixture fixture = new Fixture (rows);
        final PushCursorCommand left = new PushCursorCommand (Direction.LEFT, fixture.model, fixture.surface);
        final PushCursorCommand right = new PushCursorCommand (Direction.RIGHT, fixture.model, fixture.surface);
        final PushCursorCommand up = new PushCursorCommand (Direction.UP, fixture.model, fixture.surface);
        final PushCursorCommand down = new PushCursorCommand (Direction.DOWN, fixture.model, fixture.surface);
        left.execute (ButtonEvent.DOWN, 127);
        right.execute (ButtonEvent.DOWN, 127);
        up.execute (ButtonEvent.DOWN, 127);
        down.execute (ButtonEvent.DOWN, 127);
        left.execute (ButtonEvent.LONG, 127);
        left.execute (ButtonEvent.UP, 0);
        assertEquals (rows == 4 ? List.of () : List.of ("mode:selectPreviousItemPage", "mode:selectNextItemPage", "scenes:scrollBackwards", "scenes:scrollForwards"), fixture.requests,
            "VS arrow policy and feedback are now exercised through core NavigationView tests");

        fixture.requests.clear ();
        fixture.surface.pressed.add (ButtonID.SHIFT);
        left.execute (ButtonEvent.DOWN, 127);
        right.execute (ButtonEvent.DOWN, 127);
        up.execute (ButtonEvent.DOWN, 127);
        down.execute (ButtonEvent.DOWN, 127);
        new PageLeftCommand (fixture.model, fixture.surface).execute (ButtonEvent.DOWN, 127);
        new PageRightCommand (fixture.model, fixture.surface).execute (ButtonEvent.DOWN, 127);
        assertEquals (rows == 4 ? List.of ("tracks:selectPreviousPage", "tracks:selectNextPage") :
            List.of ("mode:selectPreviousItemPage", "mode:selectNextItemPage", "scenes:selectPreviousPage", "scenes:selectNextPage", "tracks:selectPreviousPage", "tracks:selectNextPage"), fixture.requests);
    }


    @Test
    void navigationFeedbackAndSceneOctavePagingRemainAuthoritative ()
    {
        final Fixture fixture = new Fixture (8);
        final PushCursorCommand down = new PushCursorCommand (Direction.DOWN, fixture.model, fixture.surface);
        assertFalse (down.canScroll ());
        down.execute (ButtonEvent.DOWN, 127);
        assertFalse (down.canScroll (), "a submitted scroll does not change availability");
        fixture.canScrollScenes = true;
        assertTrue (down.canScroll ());
        fixture.requests.clear ();
        fixture.view.onOctaveDown (ButtonEvent.DOWN);
        fixture.view.onOctaveUp (ButtonEvent.DOWN);
        fixture.view.onOctaveUp (ButtonEvent.UP);
        assertEquals (List.of ("scenes:selectNextPage", "scenes:selectPreviousPage"), fixture.requests);
        assertTrue (fixture.view.isOctaveUpButtonOn ());
        assertTrue (fixture.view.isOctaveDownButtonOn ());
    }


    @Test
    void fullSessionShiftChangesArrowAvailabilityWithoutChangingPageAction ()
    {
        final Fixture fixture = new Fixture (8);
        final PushCursorCommand left = new PushCursorCommand (Direction.LEFT, fixture.model, fixture.surface);
        fixture.modeCanPreviousPage = true;
        assertTrue (left.canScroll ());
        fixture.surface.pressed.add (ButtonID.SHIFT);
        assertFalse (left.canScroll ());
        left.execute (ButtonEvent.DOWN, 127);
        assertEquals (List.of ("mode:selectPreviousItemPage"), fixture.requests,
            "legacy full Session checks item availability with Shift but still submits a page action");
        fixture.modeCanPreviousItem = true;
        assertTrue (left.canScroll ());
    }


    private static int note (final int track, final int scene)
    {
        return 36 + (7 - scene) * 8 + track;
    }


    private static final class Fixture
    {
        private final List<String> requests = new ArrayList<> ();
        private final List<Runnable> pendingHost = new ArrayList<> ();
        private final Slot [][] slots = new Slot [8][8];
        private final Scene [] scenes = new Scene [8];
        private final boolean [] armed = new boolean [8];
        private final Configuration configuration = new Configuration ();
        private final Surface surface = new Surface (this.configuration, this.requests);
        private final IModel model;
        private final SessionView view;
        private int trackOffset;
        private int sceneOffset;
        private boolean canScrollScenes;
        private boolean modeCanPreviousPage;
        private boolean modeCanPreviousItem;


        private Fixture (final int rows)
        {
            final ITrack [] tracks = new ITrack [8];
            for (int index = 0; index < 8; index++)
            {
                final int column = index;
                this.scenes[index] = new Scene (this, index);
                for (int row = 0; row < 8; row++)
                    this.slots[index][row] = new Slot (this, index, row);
                final ISlotBank slotBank = proxy (ISlotBank.class, (ignored, method, args) -> "getItem".equals (method.getName ()) ? this.slots[column][(Integer) args[0]].proxy : defaultValue (method.getReturnType ()));
                tracks[index] = proxy (ITrack.class, (ignored, method, args) -> switch (method.getName ())
                {
                    case "doesExist" -> Boolean.TRUE;
                    case "getSlotBank" -> slotBank;
                    case "isRecArm" -> Boolean.valueOf (this.armed[column]);
                    case "getPosition" -> Integer.valueOf (this.trackOffset + column);
                    case "stop" -> this.request ("track:" + column + ":stop:" + args[0]);
                    default -> defaultValue (method.getReturnType ());
                });
            }
            final ISceneBank sceneBank = proxy (ISceneBank.class, (ignored, method, args) -> switch (method.getName ())
            {
                case "getItem" -> this.scenes[(Integer) args[0]].proxy;
                case "getPageSize" -> Integer.valueOf (rows);
                case "getItemCount" -> Integer.valueOf (64);
                case "getScrollPosition" -> Integer.valueOf (this.sceneOffset);
                case "canScrollBackwards", "canScrollForwards", "canScrollPageBackwards", "canScrollPageForwards" -> Boolean.valueOf (this.canScrollScenes);
                case "scrollTo" -> this.submit ("scenes:scrollTo:" + args[0], () -> this.sceneOffset = (Integer) args[0]);
                case "selectPreviousPage", "selectNextPage", "scrollBackwards", "scrollForwards" -> this.request ("scenes:" + method.getName ());
                default -> defaultValue (method.getReturnType ());
            });
            final ITrackBank trackBank = proxy (ITrackBank.class, (ignored, method, args) -> switch (method.getName ())
            {
                case "getItem" -> tracks[(Integer) args[0]];
                case "getSceneBank" -> sceneBank;
                case "getPageSize" -> Integer.valueOf (8);
                case "getItemCount" -> Integer.valueOf (64);
                case "getScrollPosition" -> Integer.valueOf (this.trackOffset);
                case "scrollTo" -> this.submit ("tracks:scrollTo:" + args[0], () -> this.trackOffset = (Integer) args[0]);
                case "selectPreviousPage", "selectNextPage", "scrollBackwards", "scrollForwards" -> this.request ("tracks:" + method.getName ());
                default -> defaultValue (method.getReturnType ());
            });
            final IBrowser browser = proxy (IBrowser.class, (ignored, method, args) -> "replace".equals (method.getName ()) ? this.request ("browser:" + args[0]) : defaultValue (method.getReturnType ()));
            final ITransport transport = proxy (ITransport.class, (ignored, method, args) -> "getQuartersPerMeasure".equals (method.getName ()) ? Integer.valueOf (4) : defaultValue (method.getReturnType ()));
            final Scales scales = new Scales (new TwosComplementValueChanger (128, 1), 36, 100, 8, 8);
            this.model = proxy (IModel.class, (ignored, method, args) -> switch (method.getName ())
            {
                case "getScales" -> scales;
                case "getColorManager" -> new PushColorManager ();
                case "getCurrentTrackBank", "getTrackBank" -> trackBank;
                case "getSceneBank" -> sceneBank;
                case "getTransport" -> transport;
                case "getBrowser" -> browser;
                case "recordNoteClip" -> this.submit ("model:record:" + args[1], () -> this.slot ((ISlot) args[1]).recording = true);
                case "createNoteClip" -> this.submit ("model:create:" + args[1] + ":" + args[2] + ":" + args[3], () -> this.slot ((ISlot) args[1]).content = true);
                default -> defaultValue (method.getReturnType ());
            });
            final IMode mode = proxy (IMode.class, (ignored, method, args) -> switch (method.getName ())
            {
                case "selectPreviousItemPage", "selectNextItemPage" -> this.request ("mode:" + method.getName ());
                case "hasPreviousItemPage" -> Boolean.valueOf (this.modeCanPreviousPage);
                case "hasPreviousItem" -> Boolean.valueOf (this.modeCanPreviousItem);
                default -> defaultValue (method.getReturnType ());
            });
            this.surface.getModeManager ().register (Modes.TRACK, mode);
            this.surface.getModeManager ().setActive (Modes.TRACK);
            this.surface.getViewManager ().register (Views.SESSION, emptyProxy (IView.class));
            this.surface.getViewManager ().register (Views.WORKSPACE, (IView) Proxy.newProxyInstance (
                IView.class.getClassLoader (), new Class<?> [] { IView.class, WorkspaceFacetAdapter.class },
                (ignored, method, args) -> defaultValue (method.getReturnType ())));
            final SessionBankShape shape = new SessionBankShape (8, rows);
            this.surface.setSessionBankRegistry (new SessionBankRegistry (this.model, Set.of (shape), shape));
            this.surface.getControllerWorkspaceHost ().apply (new DesiredControllerWorkspace ("characterization",
                rows == 8 ? Set.of (ControllerViewFacet.SESSION_GRID_FULL) : Set.of (ControllerViewFacet.SESSION_CLIP_GRID_UPPER,
                    ControllerViewFacet.SESSION_SCENE_KEYS_UPPER, ControllerViewFacet.SESSION_NAVIGATION), shape));
            this.view = rows == 8 ? new SessionView (this.surface, this.model) : new WorkspaceView (this.surface, this.model, null);
            this.requests.clear ();
        }


        private void scene (final ButtonID button, final ButtonEvent event)
        {
            this.view.onButton (button, event, event == ButtonEvent.UP ? 0 : 127);
        }


        private Slot slot (final ISlot target)
        {
            for (final Slot [] column: this.slots)
                for (final Slot slot: column)
                    if (slot.proxy == target)
                        return slot;
            throw new AssertionError ("Unknown slot");
        }


        private Object request (final String request)
        {
            this.requests.add (request);
            return null;
        }


        private Object submit (final String request, final Runnable hostUpdate)
        {
            this.pendingHost.add (hostUpdate);
            return this.request (request);
        }


        private void advanceHost ()
        {
            final List<Runnable> updates = List.copyOf (this.pendingHost);
            this.pendingHost.clear ();
            updates.forEach (Runnable::run);
        }
    }


    private static final class Slot
    {
        private static final ColorEx COLOR = ColorEx.fromRGB (75, 80, 190);
        private final ISlot proxy;
        private boolean content = true;
        private boolean selected;
        private boolean playing;
        private boolean recording;
        private boolean recordingQueued;
        private boolean playingQueued;
        private boolean stopQueued;
        private boolean muted;


        private Slot (final Fixture fixture, final int track, final int scene)
        {
            final String id = "slot:" + track + ":" + scene;
            this.proxy = proxy (ISlot.class, (ignored, method, args) -> switch (method.getName ())
            {
                case "toString" -> id;
                case "doesExist" -> Boolean.TRUE;
                case "hasContent" -> Boolean.valueOf (this.content);
                case "isSelected" -> Boolean.valueOf (this.selected);
                case "isPlaying" -> Boolean.valueOf (this.playing);
                case "isRecording" -> Boolean.valueOf (this.recording);
                case "isRecordingQueued" -> Boolean.valueOf (this.recordingQueued);
                case "isPlayingQueued" -> Boolean.valueOf (this.playingQueued);
                case "isStopQueued" -> Boolean.valueOf (this.stopQueued);
                case "isMuted" -> Boolean.valueOf (this.muted);
                case "getColor" -> COLOR;
                case "select" -> fixture.submit (id + ":select", () -> this.selected = true);
                case "launch" -> fixture.submit (id + ":launch:" + args[0] + ":" + args[1], () -> {
                    if ((Boolean) args[0])
                        this.playing = true;
                });
                case "remove" -> fixture.submit (id + ":remove", () -> this.content = false);
                case "paste" -> fixture.submit (id + ":paste:" + args[0], () -> this.content = true);
                default -> defaultValue (method.getReturnType ());
            });
        }
    }


    private static final class Scene
    {
        private final IScene proxy;
        private boolean exists = true;
        private boolean selected;
        private boolean playing;


        private Scene (final Fixture fixture, final int index)
        {
            this.proxy = proxy (IScene.class, (ignored, method, args) -> switch (method.getName ())
            {
                case "doesExist" -> Boolean.valueOf (this.exists);
                case "isSelected" -> Boolean.valueOf (this.selected);
                case "getName" -> "Scene " + index;
                case "select" -> fixture.submit ("scene:" + index + ":select", () -> this.selected = true);
                case "launch" -> fixture.submit ("scene:" + index + ":launch:" + args[0] + ":" + args[1], () -> {
                    if ((Boolean) args[0])
                        this.playing = true;
                });
                case "remove" -> fixture.submit ("scene:" + index + ":remove", () -> this.exists = false);
                case "duplicate" -> fixture.request ("scene:" + index + ":duplicate");
                default -> defaultValue (method.getReturnType ());
            });
        }
    }


    private static final class Surface extends PushControlSurface
    {
        private final Set<ButtonID> pressed = EnumSet.noneOf (ButtonID.class);
        private final Set<ButtonID> consumed = EnumSet.noneOf (ButtonID.class);
        private final IDisplay display;
        private int padConsumptions;


        private Surface (final Configuration configuration, final List<String> requests)
        {
            super (emptyProxy (IHost.class), new PushColorManager (), configuration,
                emptyProxy (IMidiOutput.class), emptyProxy (IMidiInput.class), emptyProxy (ISelectedTrackNoteTarget.class), emptyProxy (ITrack.class), () -> false, null);
            this.display = proxy (IDisplay.class, (ignored, method, args) -> {
                if ("notify".equals (method.getName ()))
                    requests.add ("notify:" + args[0]);
                return defaultValue (method.getReturnType ());
            });
        }


        @Override
        protected void createPads ()
        {
            // These tests invoke the policy boundary; router and permanent hardware tests are separate.
        }


        @Override
        public boolean isPressed (final ButtonID button)
        {
            return this.pressed.contains (button);
        }


        @Override
        public void setTriggerConsumed (final ButtonID button)
        {
            this.consumed.add (button);
        }


        @Override
        public void consumePads ()
        {
            this.padConsumptions++;
        }


        @Override
        public IDisplay getDisplay ()
        {
            return this.display;
        }
    }


    private static final class Configuration extends PushConfiguration
    {
        private boolean selectOnLaunch = true;
        private boolean drawRecordStripe = true;
        private int armedPadAction;


        private Configuration ()
        {
            super (emptyProxy (IHost.class), new TwosComplementValueChanger (128, 1), List.of ());
        }


        @Override
        public boolean isSelectClipOnLaunch ()
        {
            return this.selectOnLaunch;
        }


        @Override
        public boolean isDrawRecordStripe ()
        {
            return this.drawRecordStripe;
        }


        @Override
        public int getActionForRecArmedPad ()
        {
            return this.armedPadAction;
        }


        @Override
        public int getNewClipLenghthInBeats (final int quartersPerMeasure)
        {
            return 4 * quartersPerMeasure;
        }
    }


    private static <T> T proxy (final Class<T> type, final InvocationHandler handler)
    {
        return type.cast (Proxy.newProxyInstance (type.getClassLoader (), new Class<?> [] { type }, handler));
    }


    private static <T> T emptyProxy (final Class<T> type)
    {
        return proxy (type, (ignored, method, args) -> defaultValue (method.getReturnType ()));
    }


    private static Object defaultValue (final Class<?> type)
    {
        if (type == boolean.class)
            return Boolean.FALSE;
        if (type == int.class)
            return Integer.valueOf (0);
        if (type == long.class)
            return Long.valueOf (0);
        if (type == double.class)
            return Double.valueOf (0);
        return null;
    }
}
