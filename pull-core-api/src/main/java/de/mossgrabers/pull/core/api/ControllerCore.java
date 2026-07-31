// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api;

import de.mossgrabers.pull.core.api.event.CoreEvent;

import java.util.Optional;

/**
 * Reloadable controller behavior.
 */
public interface ControllerCore
{
    /**
     * Start the core from the shell's authoritative snapshot.
     *
     * @param snapshot The current shell snapshot
     * @param previousState A compatible checkpoint, when one exists
     * @return Effects and desired output produced during startup
     */
    CoreResult start (ControllerSnapshot snapshot, Optional<StateEnvelope> previousState);


    /**
     * Handle one normalized shell event.
     *
     * @param event The event
     * @param snapshot The authoritative snapshot after applying the event
     * @return Effects and desired output produced by the event
     */
    CoreResult handle (CoreEvent event, ControllerSnapshot snapshot);


    /**
     * Capture reloadable state without shell or child-loaded objects.
     *
     * @return The checkpoint
     */
    StateEnvelope checkpoint ();


    /**
     * Stop this instance. Implementations must make this idempotent and non-blocking.
     */
    void stop ();
}
