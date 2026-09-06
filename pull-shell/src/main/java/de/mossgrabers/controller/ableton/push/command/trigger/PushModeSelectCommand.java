// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.controller.ableton.push.command.trigger;

import de.mossgrabers.controller.ableton.push.PushConfiguration;
import de.mossgrabers.controller.ableton.push.controller.PushControlSurface;
import de.mossgrabers.framework.command.trigger.mode.ModeSelectCommand;
import de.mossgrabers.framework.daw.IModel;
import de.mossgrabers.framework.mode.Modes;

/** Keep the inherited notification after the core acknowledges its queued selection. */
public final class PushModeSelectCommand extends ModeSelectCommand<PushControlSurface, PushConfiguration>
{
    public PushModeSelectCommand (final IModel model, final PushControlSurface surface, final Modes mode)
    {
        super (model, surface, mode);
    }
    @Override protected void displayMode () { this.surface.getModeManager ().afterPageRequest (super::displayMode); }
}
