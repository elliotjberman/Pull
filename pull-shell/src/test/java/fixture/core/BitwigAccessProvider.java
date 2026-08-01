// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package fixture.core;

import com.bitwig.fixture.ForbiddenBitwigDependency;

import de.mossgrabers.pull.core.api.ControllerCore;
import de.mossgrabers.pull.core.api.CoreApi;
import de.mossgrabers.pull.core.api.CoreDescriptor;
import de.mossgrabers.pull.core.api.CoreProvider;
import de.mossgrabers.pull.core.api.ShellCapabilities;

/**
 * Broken provider that tries to link against a Bitwig-owned class.
 */
public final class BitwigAccessProvider implements CoreProvider
{
    private static final CoreDescriptor DESCRIPTOR = new CoreDescriptor (CoreApi.VERSION, ForbiddenBitwigDependency.buildId (), "fixture", 1, ShellCapabilities.empty ());


    /** {@inheritDoc} */
    @Override
    public CoreDescriptor descriptor ()
    {
        return DESCRIPTOR;
    }


    /** {@inheritDoc} */
    @Override
    public ControllerCore create ()
    {
        return new FixtureControllerCore ();
    }
}
