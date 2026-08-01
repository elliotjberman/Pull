// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.runtime;

import de.mossgrabers.pull.core.api.ControllerCore;
import de.mossgrabers.pull.core.api.CoreApi;
import de.mossgrabers.pull.core.api.CoreCapabilities;
import de.mossgrabers.pull.core.api.CoreDescriptor;
import de.mossgrabers.pull.core.api.CoreProvider;
import de.mossgrabers.pull.core.api.ShellCapabilities;

import java.util.Map;

/**
 * Production provider for Pull's reloadable controller behavior.
 */
public final class PullCoreProvider implements CoreProvider
{
    /** Checkpoint schema identifier. */
    public static final String STATE_SCHEMA = "pull.controller";

    /** Checkpoint schema version. */
    public static final int STATE_SCHEMA_VERSION = 1;

    private static final ShellCapabilities REQUIRED_CAPABILITIES = new ShellCapabilities (Map.of (
        CoreCapabilities.INPUT_DRUM_FILL, Integer.valueOf (1),
        CoreCapabilities.SNAPSHOT_SELECTED_TRACK_CLIPS, Integer.valueOf (1),
        CoreCapabilities.BINDING_CLIP_TARGET, Integer.valueOf (1),
        CoreCapabilities.SNAPSHOT_CLIP_LAUNCH_SESSION, Integer.valueOf (1),
        CoreCapabilities.EFFECT_CLIP_LAUNCH_HOLD, Integer.valueOf (3),
        CoreCapabilities.OUTPUT_RGB_LIGHT, Integer.valueOf (1)));

    private static final CoreDescriptor DESCRIPTOR = new CoreDescriptor (CoreApi.VERSION, CoreBuildMetadata.load ().buildId (), STATE_SCHEMA, STATE_SCHEMA_VERSION, REQUIRED_CAPABILITIES);


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
        return new PullControllerCore ();
    }
}
