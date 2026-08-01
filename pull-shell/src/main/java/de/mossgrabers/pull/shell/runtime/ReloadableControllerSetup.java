// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.shell.runtime;

import de.mossgrabers.framework.controller.IControllerSetup;

import com.bitwig.extension.controller.api.ControllerHost;

import java.util.Objects;

/**
 * Adds the reload supervisor around the existing controller setup without changing its graph.
 */
public final class ReloadableControllerSetup implements IControllerSetup
{
    private final IControllerSetup delegate;
    private final CoreReloadSupervisor reloadSupervisor;


    /**
     * Constructor.
     *
     * @param delegate The unchanged legacy setup
     * @param host The Bitwig host
     */
    public ReloadableControllerSetup (final IControllerSetup delegate, final ControllerHost host)
    {
        this.delegate = Objects.requireNonNull (delegate, "delegate");
        this.reloadSupervisor = new CoreReloadSupervisor (host);
    }


    /** {@inheritDoc} */
    @Override
    public void init ()
    {
        this.delegate.init ();
    }


    /** {@inheritDoc} */
    @Override
    public void startup ()
    {
        this.delegate.startup ();
        this.reloadSupervisor.start ();
    }


    /** {@inheritDoc} */
    @Override
    public void exit ()
    {
        try
        {
            this.reloadSupervisor.close ();
        }
        finally
        {
            this.delegate.exit ();
        }
    }


    /** {@inheritDoc} */
    @Override
    public void flush ()
    {
        try
        {
            this.reloadSupervisor.tick ();
        }
        finally
        {
            this.delegate.flush ();
        }
    }
}
