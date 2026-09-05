// (c) 2017-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.controller.ableton.push.mode.track;

import de.mossgrabers.controller.ableton.push.controller.PushControlSurface;
import de.mossgrabers.controller.ableton.push.mode.CorePageMode;
import de.mossgrabers.framework.daw.IModel;
import de.mossgrabers.pull.shell.runtime.ReloadableControllerRuntime;

/** Registered name for the inert Track page adapter. */
public final class TrackMode extends CorePageMode
{
    public TrackMode (final PushControlSurface surface, final IModel model, final ReloadableControllerRuntime runtime)
    {
        super ("Track", surface, model, runtime);
    }
}
