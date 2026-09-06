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
import de.mossgrabers.framework.controller.display.IDisplay;
import de.mossgrabers.framework.controller.valuechanger.TwosComplementValueChanger;
import de.mossgrabers.framework.daw.IHost;
import de.mossgrabers.framework.daw.IModel;
import de.mossgrabers.framework.daw.data.ITrack;
import de.mossgrabers.framework.daw.data.bank.ISceneBank;
import de.mossgrabers.framework.daw.data.bank.ITrackBank;
import de.mossgrabers.framework.daw.midi.IMidiInput;
import de.mossgrabers.framework.daw.midi.IMidiOutput;
import de.mossgrabers.framework.daw.midi.ISelectedTrackNoteTarget;
import de.mossgrabers.framework.featuregroup.IMode;
import de.mossgrabers.framework.featuregroup.IView;
import de.mossgrabers.framework.mode.Modes;
import de.mossgrabers.framework.scale.Scales;
import de.mossgrabers.framework.utils.ButtonEvent;
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


/** Changed Session navigation ownership; unchanged clip-grid migration is outside this suite. */
class SessionBehaviorCharacterizationTest
{
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


    private static final class Fixture
    {
        private final List<String> requests = new ArrayList<> ();
        private final Surface surface = new Surface ();
        private final IModel model;
        private final SessionView view;
        private boolean canScrollScenes;
        private boolean modeCanPreviousPage;
        private boolean modeCanPreviousItem;

        private Fixture (final int rows)
        {
            final ISceneBank sceneBank = proxy (ISceneBank.class, (ignored, method, args) -> switch (method.getName ())
            {
                case "getPageSize" -> Integer.valueOf (rows);
                case "canScrollBackwards", "canScrollForwards", "canScrollPageBackwards", "canScrollPageForwards" -> Boolean.valueOf (this.canScrollScenes);
                case "selectPreviousPage", "selectNextPage", "scrollBackwards", "scrollForwards" -> this.request ("scenes:" + method.getName ());
                default -> defaultValue (method.getReturnType ());
            });
            final ITrackBank trackBank = proxy (ITrackBank.class, (ignored, method, args) -> switch (method.getName ())
            {
                case "getSceneBank" -> sceneBank;
                case "getPageSize" -> Integer.valueOf (8);
                case "selectPreviousPage", "selectNextPage" -> this.request ("tracks:" + method.getName ());
                default -> defaultValue (method.getReturnType ());
            });
            final Scales scales = new Scales (new TwosComplementValueChanger (128, 1), 36, 100, 8, 8);
            this.model = proxy (IModel.class, (ignored, method, args) -> switch (method.getName ())
            {
                case "getScales" -> scales;
                case "getColorManager" -> new PushColorManager ();
                case "getCurrentTrackBank", "getTrackBank" -> trackBank;
                case "getSceneBank" -> sceneBank;
                default -> defaultValue (method.getReturnType ());
            });
            final IMode mode = proxy (IMode.class, (ignored, method, args) -> switch (method.getName ())
            {
                case "selectPreviousItemPage", "selectNextItemPage" -> this.request ("mode:" + method.getName ());
                case "hasPreviousItemPage" -> Boolean.valueOf (this.modeCanPreviousPage);
                case "hasPreviousItem" -> Boolean.valueOf (this.modeCanPreviousItem);
                default -> defaultValue (method.getReturnType ());
            });
            this.surface.getModeManager ().register (Modes.DEVICE_PARAMS, mode);
            this.surface.getModeManager ().apply (new de.mossgrabers.pull.core.api.DesiredControllerPageState (1, de.mossgrabers.pull.core.api.ControllerPageRef.legacy ("DEVICE_PARAMS"), de.mossgrabers.pull.core.api.ControllerPageRef.none (), java.util.Optional.empty (), 0));
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

        private Object request (final String request)
        {
            this.requests.add (request);
            return null;
        }
    }


    private static final class Surface extends PushControlSurface
    {
        private final Set<ButtonID> pressed = EnumSet.noneOf (ButtonID.class);

        private Surface ()
        {
            super (emptyProxy (IHost.class), new PushColorManager (),
                new PushConfiguration (emptyProxy (IHost.class), new TwosComplementValueChanger (128, 1), List.of ()),
                emptyProxy (IMidiOutput.class), emptyProxy (IMidiInput.class), emptyProxy (ISelectedTrackNoteTarget.class), emptyProxy (ITrack.class), () -> false, null);
        }

        @Override
        protected void createPads () { }

        @Override
        public boolean isPressed (final ButtonID button) { return this.pressed.contains (button); }

        @Override
        public IDisplay getDisplay () { return emptyProxy (IDisplay.class); }
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
