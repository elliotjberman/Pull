// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.fixture;

import de.mossgrabers.pull.core.api.ControllerCore;
import de.mossgrabers.pull.core.api.CoreApi;
import de.mossgrabers.pull.core.api.CoreDescriptor;
import de.mossgrabers.pull.core.api.CoreProvider;
import de.mossgrabers.pull.core.api.ShellCapabilities;

import fixture.core.FixtureControllerCore;

/**
 * Core fixture whose required dependency is intentionally omitted from one candidate JAR.
 */
public final class CoreRuntimeProvider implements CoreProvider
{
    private static final CoreDescriptor DESCRIPTOR = new CoreDescriptor (CoreApi.VERSION, CoreRuntimeDependency.buildId (), "fixture", 1, ShellCapabilities.empty ());


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
