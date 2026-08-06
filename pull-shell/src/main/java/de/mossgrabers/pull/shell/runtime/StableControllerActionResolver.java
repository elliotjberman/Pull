// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.shell.runtime;

import de.mossgrabers.controller.ableton.push.command.trigger.ClipCommand;
import de.mossgrabers.controller.ableton.push.command.trigger.DeviceCommand;
import de.mossgrabers.controller.ableton.push.command.trigger.MastertrackCommand;
import de.mossgrabers.controller.ableton.push.command.trigger.PageLeftCommand;
import de.mossgrabers.controller.ableton.push.command.trigger.PageRightCommand;
import de.mossgrabers.controller.ableton.push.command.trigger.PushAddEffectCommand;
import de.mossgrabers.controller.ableton.push.command.trigger.PushCursorCommand;
import de.mossgrabers.controller.ableton.push.command.trigger.TrackCommand;
import de.mossgrabers.controller.ableton.push.controller.PushControlSurface;
import de.mossgrabers.framework.command.core.TriggerCommand;
import de.mossgrabers.framework.command.trigger.BrowserCommand;
import de.mossgrabers.framework.command.trigger.Direction;
import de.mossgrabers.framework.command.trigger.mode.ButtonRowModeCommand;
import de.mossgrabers.framework.command.trigger.mode.ModeSelectCommand;
import de.mossgrabers.framework.utils.ButtonEvent;
import de.mossgrabers.pull.core.api.ControllerActionId;
import de.mossgrabers.pull.core.api.ControllerActionIntent;
import de.mossgrabers.pull.core.api.ControllerStateScope;

import java.util.Objects;
import java.util.Set;


/**
 * Compatibility adapter from the stable command selected by Push to semantic controller intent.
 * This is deliberately command- and mode-driven; core views do not guess stable behavior from a
 * physical button name.
 */
final class StableControllerActionResolver
{
    private static final Set<ControllerStateScope> ACTIVE_PARAMETERS = Set.of (ControllerStateScope.ACTIVE_PARAMETERS);

    private final PushControlSurface surface;


    StableControllerActionResolver (final PushControlSurface surface)
    {
        this.surface = Objects.requireNonNull (surface, "surface");
    }


    /** Resolve the stable behavior selected for one gesture begin, or {@code null}. */
    ControllerActionIntent resolve (final TriggerCommand command, final ButtonEvent event)
    {
        if (event != ButtonEvent.DOWN || command == null)
            return null;

        if (command instanceof final PushCursorCommand cursor)
            return this.resolveCursor (cursor.getDirection ());
        if (command instanceof PageLeftCommand || command instanceof PageRightCommand)
            return this.surface.isSessionLayoutActive () ? intent (ControllerActionId.NAVIGATE_SELECTED_TARGET) : null;
        if (command instanceof ButtonRowModeCommand<?, ?>)
            return this.surface.getModeManager ().getActive () == null ? null : intent (ControllerActionId.SELECT_PARAMETER_CONTEXT);
        if (command instanceof TrackCommand)
            return this.surface.isShiftPressed () ? null : intent (ControllerActionId.SWITCH_PARAMETER_CONTEXT);
        if (command instanceof DeviceCommand || command instanceof ClipCommand || command instanceof MastertrackCommand || command instanceof PushAddEffectCommand || command instanceof ModeSelectCommand<?, ?> || command instanceof BrowserCommand<?, ?>)
            return intent (ControllerActionId.SWITCH_PARAMETER_CONTEXT);
        return null;
    }


    private ControllerActionIntent resolveCursor (final Direction direction)
    {
        if (direction == Direction.UP || direction == Direction.DOWN)
            return null;
        return intent (this.surface.isSessionNavigationActive () ? ControllerActionId.NAVIGATE_SELECTED_TARGET : ControllerActionId.SELECT_PARAMETER_PAGE);
    }


    private static ControllerActionIntent intent (final ControllerActionId action)
    {
        return new ControllerActionIntent (action, ACTIVE_PARAMETERS);
    }
}
