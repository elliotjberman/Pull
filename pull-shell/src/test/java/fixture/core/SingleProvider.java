// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package fixture.core;

import de.mossgrabers.pull.core.api.ControllerCore;
import de.mossgrabers.pull.core.api.CoreApi;
import de.mossgrabers.pull.core.api.CoreDescriptor;
import de.mossgrabers.pull.core.api.CoreProvider;
import de.mossgrabers.pull.core.api.ShellCapabilities;

/**
 * Valid fixture provider whose initialization requires its child dependency.
 */
public final class SingleProvider implements CoreProvider
{
    private static final CoreDescriptor DESCRIPTOR = new CoreDescriptor (CoreApi.VERSION, ChildDependency.buildId (), "fixture", 1, ShellCapabilities.empty ());


    /**
     * Require provider construction to run with the candidate loader as the thread context loader.
     */
    public SingleProvider ()
    {
        this.requireCandidateContext ();
    }


    /** {@inheritDoc} */
    @Override
    public CoreDescriptor descriptor ()
    {
        this.requireCandidateContext ();
        return DESCRIPTOR;
    }


    /** {@inheritDoc} */
    @Override
    public ControllerCore create ()
    {
        this.requireCandidateContext ();
        return new FixtureControllerCore ();
    }


    private void requireCandidateContext ()
    {
        if (Thread.currentThread ().getContextClassLoader () != this.getClass ().getClassLoader ())
            throw new IllegalStateException ("Provider invoked without candidate context classloader");
    }
}
