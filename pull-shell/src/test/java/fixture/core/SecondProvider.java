// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package fixture.core;

import de.mossgrabers.pull.core.api.ControllerCore;
import de.mossgrabers.pull.core.api.CoreApi;
import de.mossgrabers.pull.core.api.CoreDescriptor;
import de.mossgrabers.pull.core.api.CoreProvider;
import de.mossgrabers.pull.core.api.ShellCapabilities;

/**
 * Second provider used to prove ambiguous candidates are rejected.
 */
public final class SecondProvider implements CoreProvider
{
    /** {@inheritDoc} */
    @Override
    public CoreDescriptor descriptor ()
    {
        return new CoreDescriptor (CoreApi.VERSION, "fixture-second", "fixture", 1, ShellCapabilities.empty ());
    }


    /** {@inheritDoc} */
    @Override
    public ControllerCore create ()
    {
        return new FixtureControllerCore ();
    }
}
