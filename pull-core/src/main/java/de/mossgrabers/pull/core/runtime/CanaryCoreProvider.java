// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.runtime;

import de.mossgrabers.pull.core.api.ControllerCore;
import de.mossgrabers.pull.core.api.CoreApi;
import de.mossgrabers.pull.core.api.CoreDescriptor;
import de.mossgrabers.pull.core.api.CoreProvider;
import de.mossgrabers.pull.core.api.ShellCapabilities;

/**
 * Minimal provider used to validate core discovery and lifecycle before behavior is migrated.
 */
public final class CanaryCoreProvider implements CoreProvider
{
    /** Checkpoint schema identifier. */
    public static final String STATE_SCHEMA = "pull.canary";

    /** Checkpoint schema version. */
    public static final int STATE_SCHEMA_VERSION = 1;

    private static final CoreDescriptor DESCRIPTOR = new CoreDescriptor (CoreApi.VERSION, "canary-v1", STATE_SCHEMA, STATE_SCHEMA_VERSION, ShellCapabilities.empty ());


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
        return new NoOpControllerCore ();
    }
}
