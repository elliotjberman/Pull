// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.testing;

import de.mossgrabers.pull.core.api.ClipCatalogSnapshot;
import de.mossgrabers.pull.core.api.ControlId;
import de.mossgrabers.pull.core.api.ControllerCore;
import de.mossgrabers.pull.core.api.ControllerSnapshot;
import de.mossgrabers.pull.core.api.CoreApi;
import de.mossgrabers.pull.core.api.CoreCapabilities;
import de.mossgrabers.pull.core.api.CoreProvider;
import de.mossgrabers.pull.core.api.ShellCapabilities;
import de.mossgrabers.pull.core.api.StateEnvelope;
import de.mossgrabers.pull.core.api.event.ButtonInputEvent;
import de.mossgrabers.pull.core.runtime.PullCoreProvider;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.ServiceLoader;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Discovery, compatibility, and lifecycle tests for the production core provider.
 */
class PullCoreProviderTest
{
    @Test
    void serviceLoaderFindsExactlyOneProductionProvider () throws IOException
    {
        final List<CoreProvider> providers = ServiceLoader.load (CoreProvider.class).stream ().map (ServiceLoader.Provider::get).toList ();

        assertEquals (1, providers.size ());
        assertEquals (PullCoreProvider.class, providers.getFirst ().getClass ());
        assertEquals (CoreApi.VERSION, providers.getFirst ().descriptor ().apiVersion ());
        assertEquals (embeddedBuildId (), providers.getFirst ().descriptor ().buildId ());
    }


    @Test
    void declaresEveryShellCapabilityUsedByTheCore ()
    {
        final Map<String, Integer> required = new PullCoreProvider ().descriptor ().requiredCapabilities ().versions ();

        assertEquals (Map.ofEntries (
            Map.entry (CoreCapabilities.INPUT_DRUM_FILL, Integer.valueOf (1)),
            Map.entry (CoreCapabilities.SNAPSHOT_SELECTED_TRACK_CLIPS, Integer.valueOf (1)),
            Map.entry (CoreCapabilities.BINDING_CLIP_TARGET, Integer.valueOf (1)),
            Map.entry (CoreCapabilities.SNAPSHOT_CLIP_LAUNCH_SESSION, Integer.valueOf (1)),
            Map.entry (CoreCapabilities.EFFECT_CLIP_LAUNCH_HOLD, Integer.valueOf (4)),
            Map.entry (CoreCapabilities.OUTPUT_RGB_LIGHT, Integer.valueOf (1)),
            Map.entry (CoreCapabilities.INPUT_CONTROLLER, Integer.valueOf (1)),
            Map.entry (CoreCapabilities.ROUTING_CONTROLLER_INPUT, Integer.valueOf (1)),
            Map.entry (CoreCapabilities.SNAPSHOT_CONTROLLER_BRIDGE, Integer.valueOf (1)),
            Map.entry (CoreCapabilities.SUBSCRIPTION_CONTROLLER_BRIDGE, Integer.valueOf (1)),
            Map.entry (CoreCapabilities.EFFECT_TRANSPORT, Integer.valueOf (1)),
            Map.entry (CoreCapabilities.EFFECT_SELECTED_TRACK, Integer.valueOf (2)),
            Map.entry (CoreCapabilities.EFFECT_DRUM_PAD, Integer.valueOf (1)),
            Map.entry (CoreCapabilities.EFFECT_NOTE_INPUT_MIDI, Integer.valueOf (1))), required);
    }


    @Test
    void createsIndependentCoreInstances ()
    {
        final PullCoreProvider provider = new PullCoreProvider ();

        assertNotSame (provider.create (), provider.create ());
    }


    @Test
    void enforcesStartLifecycleAndKeepsCheckpointFreeOfTargetLeases ()
    {
        final PullCoreProvider provider = new PullCoreProvider ();
        final ControllerCore core = provider.create ();
        final ControllerSnapshot snapshot = snapshot ();
        final ButtonInputEvent event = new ButtonInputEvent (1, 0, new ControlId ("other"), true);

        assertThrows (IllegalStateException.class, core::checkpoint);
        assertThrows (IllegalStateException.class, () -> core.handle (event, snapshot));
        core.start (snapshot, Optional.of (new StateEnvelope (provider.descriptor ().stateSchema (), provider.descriptor ().stateSchemaVersion (), new byte []
        {
            1,
            2,
            3
        })));
        assertThrows (IllegalStateException.class, () -> core.start (snapshot, Optional.empty ()));
        core.handle (event, snapshot);

        final StateEnvelope checkpoint = core.checkpoint ();
        assertEquals (provider.descriptor ().stateSchema (), checkpoint.schema ());
        assertEquals (provider.descriptor ().stateSchemaVersion (), checkpoint.version ());
        assertEquals (0, checkpoint.payload ().length);

    }


    private static ControllerSnapshot snapshot ()
    {
        return new ControllerSnapshot (0, 0, ShellCapabilities.empty (), ClipCatalogSnapshot.empty (), Map.of (), Set.of (), Set.of ());
    }


    private static String embeddedBuildId () throws IOException
    {
        final Properties properties = new Properties ();
        try (InputStream input = PullCoreProviderTest.class.getResourceAsStream ("/META-INF/pull-core.properties"))
        {
            properties.load (input);
        }
        return properties.getProperty ("buildId");
    }
}
