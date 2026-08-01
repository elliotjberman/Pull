// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package fixture.core;

import de.mossgrabers.pull.core.api.ControllerCore;
import de.mossgrabers.pull.core.api.ControllerSnapshot;
import de.mossgrabers.pull.core.api.CoreResult;
import de.mossgrabers.pull.core.api.StateEnvelope;
import de.mossgrabers.pull.core.api.event.CoreEvent;

import java.util.Optional;

/**
 * Minimal controller core used only by classloader fixtures.
 */
public final class FixtureControllerCore implements ControllerCore
{
    /** {@inheritDoc} */
    @Override
    public CoreResult start (final ControllerSnapshot snapshot, final Optional<StateEnvelope> previousState)
    {
        return CoreResult.empty ();
    }


    /** {@inheritDoc} */
    @Override
    public CoreResult handle (final CoreEvent event, final ControllerSnapshot snapshot)
    {
        return CoreResult.empty ();
    }


    /** {@inheritDoc} */
    @Override
    public StateEnvelope checkpoint ()
    {
        return new StateEnvelope ("fixture", 1, new byte [0]);
    }


    /** {@inheritDoc} */
    @Override
    public void stop ()
    {
        // No fixture resources
    }
}
