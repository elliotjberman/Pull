// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2017-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.framework.controller.hardware;

import de.mossgrabers.framework.utils.ButtonEvent;


/**
 * Arbitrates one physical button event before it reaches the established controller behavior.
 * The supplied legacy dispatch contains the complete established behavior for the event,
 * including button state, command and event-handler updates. An arbitrator can run it exactly
 * once to preserve that behavior or omit it to claim the event exclusively.
 */
@FunctionalInterface
public interface ButtonEventArbitrator
{
    /**
     * Arbitrate one physical button event.
     *
     * @param event Physical event
     * @param velocity MIDI velocity in the range of [0..127]
     * @param legacyDispatch Complete established button dispatch
     */
    void arbitrate (ButtonEvent event, int velocity, Runnable legacyDispatch);
}
