// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.shell.runtime;

import de.mossgrabers.framework.controller.IControllerSetup;

import java.util.Objects;

/**
 * Adds the reload supervisor around the existing controller setup without changing its graph.
 */
public final class ReloadableControllerSetup implements IControllerSetup
{
    private final IControllerSetup delegate;
    private final ReloadableControllerRuntime runtime;


    /**
     * Constructor.
     *
     * @param delegate The unchanged legacy setup
     * @param runtime The stable reloadable-core runtime
     */
    public ReloadableControllerSetup (final IControllerSetup delegate, final ReloadableControllerRuntime runtime)
    {
        this.delegate = Objects.requireNonNull (delegate, "delegate");
        this.runtime = Objects.requireNonNull (runtime, "runtime");
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
        this.runtime.start ();
    }


    /** {@inheritDoc} */
    @Override
    public void exit ()
    {
        try
        {
            this.runtime.close ();
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
            this.runtime.tick ();
        }
        finally
        {
            this.delegate.flush ();
        }
    }
}
