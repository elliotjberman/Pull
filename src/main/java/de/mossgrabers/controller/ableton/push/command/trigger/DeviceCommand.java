// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2017-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.controller.ableton.push.command.trigger;

import de.mossgrabers.controller.ableton.push.PushConfiguration;
import de.mossgrabers.controller.ableton.push.controller.PushControlSurface;
import de.mossgrabers.controller.ableton.push.mode.device.DeviceParamsMode;
import de.mossgrabers.framework.command.core.AbstractTriggerCommand;
import de.mossgrabers.framework.daw.IModel;
import de.mossgrabers.framework.featuregroup.ModeManager;
import de.mossgrabers.framework.mode.Modes;
import de.mossgrabers.framework.utils.ButtonEvent;


/**
 * Command to edit device parameters.
 *
 * @author Jürgen Moßgraber
 */
public class DeviceCommand extends AbstractTriggerCommand<PushControlSurface, PushConfiguration>
{
    private Modes   previousMode;
    private boolean switchedMode;
    private boolean restoreOnRelease;


    /**
     * Constructor.
     *
     * @param model The model
     * @param surface The surface
     */
    public DeviceCommand (final IModel model, final PushControlSurface surface)
    {
        super (model, surface);
    }


    /** {@inheritDoc} */
    @Override
    public void execute (final ButtonEvent event, final int velocity)
    {
        final ModeManager modeManager = this.surface.getModeManager ();
        if (event == ButtonEvent.LONG)
        {
            if (this.switchedMode)
                this.restoreOnRelease = true;
            return;
        }

        if (event == ButtonEvent.UP)
        {
            if (this.switchedMode && this.restoreOnRelease && this.previousMode != null)
                modeManager.setActive (this.previousMode);
            this.previousMode = null;
            this.switchedMode = false;
            this.restoreOnRelease = false;
            return;
        }

        if (event != ButtonEvent.DOWN)
            return;

        this.previousMode = null;
        this.switchedMode = false;
        this.restoreOnRelease = false;

        final Modes currentMode = modeManager.getActiveID ();
        if (currentMode == Modes.DEVICE_PARAMS)
        {
            ((DeviceParamsMode) modeManager.get (Modes.DEVICE_PARAMS)).setShowDevices (true);
            return;
        }

        if (Modes.isDeviceMode (currentMode) || currentMode == Modes.DEVICE_CHAINS)
        {
            modeManager.setActive (Modes.DEVICE_PARAMS);
            return;
        }

        this.previousMode = modeManager.getActiveIDIgnoreTemporary ();
        modeManager.setActive (Modes.DEVICE_PARAMS);
        this.switchedMode = true;
    }
}
