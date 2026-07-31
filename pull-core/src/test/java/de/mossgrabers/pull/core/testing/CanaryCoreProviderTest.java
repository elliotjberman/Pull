// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.testing;

import de.mossgrabers.pull.core.api.ControlId;
import de.mossgrabers.pull.core.api.ControllerCore;
import de.mossgrabers.pull.core.api.ControllerSnapshot;
import de.mossgrabers.pull.core.api.CoreApi;
import de.mossgrabers.pull.core.api.CoreProvider;
import de.mossgrabers.pull.core.api.CoreResult;
import de.mossgrabers.pull.core.api.ShellCapabilities;
import de.mossgrabers.pull.core.api.StateEnvelope;
import de.mossgrabers.pull.core.api.event.ButtonInputEvent;
import de.mossgrabers.pull.core.runtime.CanaryCoreProvider;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Discovery and lifecycle tests for the production canary.
 */
class CanaryCoreProviderTest
{
    @Test
    void serviceLoaderFindsExactlyOneCanaryProvider ()
    {
        final List<CoreProvider> providers = ServiceLoader.load (CoreProvider.class).stream ().map (ServiceLoader.Provider::get).toList ();

        assertEquals (1, providers.size ());
        assertEquals (CanaryCoreProvider.class, providers.getFirst ().getClass ());
        assertEquals (CoreApi.VERSION, providers.getFirst ().descriptor ().apiVersion ());
        assertEquals ("canary-v1", providers.getFirst ().descriptor ().buildId ());
    }


    @Test
    void createsIndependentCoreInstances ()
    {
        final CanaryCoreProvider provider = new CanaryCoreProvider ();

        assertNotSame (provider.create (), provider.create ());
    }


    @Test
    void enforcesLifecycleAndProducesCompatibleCheckpoint ()
    {
        final CanaryCoreProvider provider = new CanaryCoreProvider ();
        final ControllerCore core = provider.create ();
        final ControllerSnapshot snapshot = snapshot ();
        final ButtonInputEvent event = new ButtonInputEvent (1, 0, new ControlId ("button"), true);

        assertThrows (IllegalStateException.class, core::checkpoint);
        assertThrows (IllegalStateException.class, () -> core.handle (event, snapshot));
        assertEquals (CoreResult.empty (), core.start (snapshot, Optional.empty ()));
        assertThrows (IllegalStateException.class, () -> core.start (snapshot, Optional.empty ()));
        assertEquals (CoreResult.empty (), core.handle (event, snapshot));

        final StateEnvelope checkpoint = core.checkpoint ();
        assertEquals (provider.descriptor ().stateSchema (), checkpoint.schema ());
        assertEquals (provider.descriptor ().stateSchemaVersion (), checkpoint.version ());
        assertEquals (0, checkpoint.payload ().length);

        core.stop ();
        core.stop ();
        assertThrows (IllegalStateException.class, core::checkpoint);
        assertThrows (IllegalStateException.class, () -> core.handle (event, snapshot));
    }


    @Test
    void ignoresIncompatibleStateButRejectsMalformedMatchingState ()
    {
        final CanaryCoreProvider provider = new CanaryCoreProvider ();
        final ControllerSnapshot snapshot = snapshot ();
        final ControllerCore incompatible = provider.create ();
        incompatible.start (snapshot, Optional.of (new StateEnvelope ("other", 99, new byte []
        {
            1
        })));
        assertEquals (0, incompatible.checkpoint ().payload ().length);

        final ControllerCore malformed = provider.create ();
        assertThrows (IllegalArgumentException.class, () -> malformed.start (snapshot, Optional.of (new StateEnvelope (provider.descriptor ().stateSchema (), provider.descriptor ().stateSchemaVersion (), new byte []
        {
            1
        }))));
    }


    private static ControllerSnapshot snapshot ()
    {
        return new ControllerSnapshot (0, 0, ShellCapabilities.empty (), Set.of (), Set.of ());
    }
}
