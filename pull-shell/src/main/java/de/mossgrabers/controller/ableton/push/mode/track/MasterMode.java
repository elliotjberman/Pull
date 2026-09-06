// (c) 2017-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.controller.ableton.push.mode.track;

import de.mossgrabers.controller.ableton.push.controller.PushControlSurface;
import de.mossgrabers.controller.ableton.push.mode.CorePageMode;
import de.mossgrabers.framework.daw.IModel;
import de.mossgrabers.framework.daw.data.IMasterTrack;
import de.mossgrabers.pull.shell.runtime.ReloadableControllerRuntime;

/** Inert Master page adapter retaining host parameter-indication lifecycle. */
public final class MasterMode extends CorePageMode
{
    private final IMasterTrack masterTrack;

    public MasterMode (final PushControlSurface surface, final IModel model, final ReloadableControllerRuntime runtime)
    {
        super ("Master", surface, model, runtime);
        this.masterTrack = model.getMasterTrack ();
    }

    @Override
    public void onActivate ()
    {
        super.onActivate ();
        this.setIndication (true);
    }

    @Override
    public void onDeactivate ()
    {
        super.onDeactivate ();
        this.setIndication (false);
    }

    private void setIndication (final boolean enabled)
    {
        this.masterTrack.setVolumeIndication (enabled);
        this.masterTrack.setPanIndication (enabled);
    }
}
