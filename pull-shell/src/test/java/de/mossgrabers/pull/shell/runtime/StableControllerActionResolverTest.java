// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.shell.runtime;

import de.mossgrabers.controller.ableton.push.PushConfiguration;
import de.mossgrabers.controller.ableton.push.command.trigger.PushCursorCommand;
import de.mossgrabers.controller.ableton.push.controller.PushColorManager;
import de.mossgrabers.controller.ableton.push.controller.PushControlSurface;
import de.mossgrabers.controller.ableton.push.workspace.SessionBankRegistry;
import de.mossgrabers.framework.command.trigger.Direction;
import de.mossgrabers.framework.command.trigger.mode.ButtonRowModeCommand;
import de.mossgrabers.framework.controller.hardware.IHwButton;
import de.mossgrabers.framework.controller.hardware.IHwLight;
import de.mossgrabers.framework.controller.hardware.IHwSurfaceFactory;
import de.mossgrabers.framework.controller.valuechanger.IValueChanger;
import de.mossgrabers.framework.controller.valuechanger.TwosComplementValueChanger;
import de.mossgrabers.framework.controller.display.IGraphicDisplay;
import de.mossgrabers.framework.daw.IHost;
import de.mossgrabers.framework.daw.IModel;
import de.mossgrabers.framework.daw.data.ITrack;
import de.mossgrabers.framework.daw.midi.IMidiInput;
import de.mossgrabers.framework.daw.midi.IMidiOutput;
import de.mossgrabers.framework.daw.midi.ISelectedTrackNoteTarget;
import de.mossgrabers.framework.featuregroup.IMode;
import de.mossgrabers.framework.mode.Modes;
import de.mossgrabers.framework.utils.ButtonEvent;
import de.mossgrabers.pull.core.api.ControllerActionId;
import de.mossgrabers.pull.core.api.ControllerActionIntent;
import de.mossgrabers.pull.core.api.ControllerViewFacet;
import de.mossgrabers.pull.core.api.DesiredControllerWorkspace;
import de.mossgrabers.pull.core.api.SessionBankShape;
import de.mossgrabers.pull.shell.input.InputKind;
import de.mossgrabers.pull.shell.input.InputPhase;
import de.mossgrabers.pull.shell.input.InputRoute;
import de.mossgrabers.pull.shell.input.PhysicalControlRegistry;
import de.mossgrabers.pull.shell.input.PhysicalInputRouter;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;


/**
 * Tests the transitional command-to-semantic-action adapter against live stable context.
 */
class StableControllerActionResolverTest
{
    private static final String BUTTON = "cursor-right";


    @Test
    void cursorIntentFollowsTheCommandModePath ()
    {
        final Fixture fixture = new Fixture ();
        final PushCursorCommand cursor = new PushCursorCommand (Direction.RIGHT, fixture.model, fixture.surface);

        assertEquals (ControllerActionId.SELECT_PARAMETER_PAGE, fixture.resolver.resolve (cursor, ButtonEvent.DOWN).action ());
        assertNull (fixture.resolver.resolve (cursor, ButtonEvent.UP));

        fixture.enableSessionNavigation ();
        assertEquals (ControllerActionId.NAVIGATE_SELECTED_TARGET, fixture.resolver.resolve (cursor, ButtonEvent.DOWN).action ());
    }


    @Test
    void buttonRowIntentRequiresTheActualActiveMode ()
    {
        final Fixture fixture = new Fixture ();
        final ButtonRowModeCommand<PushControlSurface, PushConfiguration> row = new ButtonRowModeCommand<> (0, 0, fixture.model, fixture.surface);

        assertNull (fixture.resolver.resolve (row, ButtonEvent.DOWN));
        fixture.surface.getModeManager ().register (Modes.USER, relaxedProxy (IMode.class));
        fixture.surface.getModeManager ().setActive (Modes.USER);

        assertEquals (ControllerActionId.SELECT_PARAMETER_CONTEXT, fixture.resolver.resolve (row, ButtonEvent.DOWN).action ());
    }


    @Test
    void deferredDispatchRetainsTheBeginTimeCommandIntent ()
    {
        final Fixture fixture = new Fixture ();
        final PushCursorCommand cursor = new PushCursorCommand (Direction.RIGHT, fixture.model, fixture.surface);
        final ControllerActionIntent beginIntent = fixture.resolver.resolve (cursor, ButtonEvent.DOWN);
        final AtomicBoolean blocked = new AtomicBoolean (true);
        final List<ControllerActionIntent> barrierChecks = new ArrayList<> ();
        final PhysicalInputRouter<String> router = new PhysicalInputRouter<> (
            PhysicalControlRegistry.<String>builder (1).register (BUTTON, InputKind.BUTTON).build (),
            (ignoredControl, ignoredKind) -> InputRoute.NONE,
            ignoredEvent -> {
                // The semantic event is asserted through the barrier's retained intent.
            },
            (ignoredControl, ignoredKind, action) -> {
                barrierChecks.add (action);
                return blocked.get ();
            },
            System::nanoTime,
            () -> 1);

        router.route (BUTTON, InputKind.BUTTON, InputPhase.BEGIN, 127, beginIntent, () -> {
            // Deferred stable dispatch.
        });
        fixture.enableSessionNavigation ();
        assertEquals (ControllerActionId.NAVIGATE_SELECTED_TARGET, fixture.resolver.resolve (cursor, ButtonEvent.DOWN).action ());
        router.route (BUTTON, InputKind.BUTTON, InputPhase.END, 0, null, () -> {
            // Deferred stable dispatch.
        });

        blocked.set (false);
        router.releaseDeferredStableDispatches ();
        assertEquals (beginIntent, barrierChecks.getLast ());
    }


    private static final class Fixture
    {
        private final IValueChanger valueChanger = new TwosComplementValueChanger (128, 1);
        private final IModel model = relaxedProxy (IModel.class);
        private final PushControlSurface surface = createSurface (this.valueChanger);
        private final StableControllerActionResolver resolver = new StableControllerActionResolver (this.surface);


        private Fixture ()
        {
            final SessionBankShape shape = new SessionBankShape (8, 8);
            this.surface.setSessionBankRegistry (new SessionBankRegistry (this.model, Set.of (shape), shape));
        }


        private void enableSessionNavigation ()
        {
            this.surface.getControllerWorkspaceHost ().apply (new DesiredControllerWorkspace (
                "session-navigation-test",
                Set.of (ControllerViewFacet.SESSION_NAVIGATION),
                SessionBankShape.empty ()));
        }
    }


    private static PushControlSurface createSurface (final IValueChanger valueChanger)
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
            relaxedProxy (ISelectedTrackNoteTarget.class),
            relaxedProxy (ITrack.class),
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
