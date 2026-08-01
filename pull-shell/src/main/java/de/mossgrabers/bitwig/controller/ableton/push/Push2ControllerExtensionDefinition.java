// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2017-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.bitwig.controller.ableton.push;

import de.mossgrabers.bitwig.framework.BitwigSetupFactory;
import de.mossgrabers.bitwig.framework.configuration.SettingsUIImpl;
import de.mossgrabers.bitwig.framework.daw.HostImpl;
import de.mossgrabers.bitwig.framework.extension.AbstractControllerExtensionDefinition;
import de.mossgrabers.controller.ableton.push.Push2ControllerDefinition;
import de.mossgrabers.controller.ableton.push.PushControllerSetup;
import de.mossgrabers.framework.controller.IControllerSetup;
import de.mossgrabers.pull.shell.runtime.ReloadableControllerSetup;
import de.mossgrabers.pull.shell.runtime.ReloadableControllerRuntime;

import com.bitwig.extension.controller.api.ControllerHost;


/**
 * Definition class for the Push 2 controller extension.
 *
 * @author Jürgen Moßgraber
 */
public class Push2ControllerExtensionDefinition extends AbstractControllerExtensionDefinition
{
    /**
     * Constructor.
     */
    public Push2ControllerExtensionDefinition ()
    {
        super (new Push2ControllerDefinition ());
    }


    /** {@inheritDoc} */
    @Override
    protected IControllerSetup getControllerSetup (final ControllerHost host)
    {
        final ReloadableControllerRuntime runtime = new ReloadableControllerRuntime (host);
        final IControllerSetup setup = new PushControllerSetup (new HostImpl (host), new BitwigSetupFactory (host), new SettingsUIImpl (host, host.getPreferences ()), new SettingsUIImpl (host, host.getDocumentState ()), runtime);
        return new ReloadableControllerSetup (setup, runtime);
    }


    /** {@inheritDoc} */
    @Override
    public boolean isUsingBetaAPI ()
    {
        return true;
    }
}
