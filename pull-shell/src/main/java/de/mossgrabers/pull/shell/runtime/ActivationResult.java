// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.shell.runtime;

import java.util.Objects;

/**
 * Parent-owned result of one candidate activation attempt.
 *
 * @param state The activation state
 * @param requestedBuildId The requested candidate build
 * @param activeBuildId The active build after the attempt, or an empty string
 * @param message Human-readable status
 */
record ActivationResult (State state, String requestedBuildId, String activeBuildId, String message)
{
    ActivationResult
    {
        state = Objects.requireNonNull (state, "state");
        requestedBuildId = Objects.requireNonNull (requestedBuildId, "requestedBuildId");
        activeBuildId = Objects.requireNonNull (activeBuildId, "activeBuildId");
        message = Objects.requireNonNull (message, "message");
    }


    enum State
    {
        ACTIVE,
        FAILED,
        SUPERSEDED
    }
}
