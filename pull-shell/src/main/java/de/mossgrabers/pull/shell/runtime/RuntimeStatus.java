// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.shell.runtime;

import java.util.Objects;
import java.util.Properties;

/**
 * Atomic acknowledgement consumed by the development command.
 *
 * @param state Protocol state
 * @param requestedBuildId Requested candidate build
 * @param activeBuildId Active build, or an empty string
 * @param message Human-readable detail
 */
record RuntimeStatus (State state, String requestedBuildId, String activeBuildId, String message)
{
    RuntimeStatus
    {
        state = Objects.requireNonNull (state, "state");
        requestedBuildId = Objects.requireNonNull (requestedBuildId, "requestedBuildId");
        activeBuildId = Objects.requireNonNull (activeBuildId, "activeBuildId");
        message = Objects.requireNonNull (message, "message");
    }


    Properties toProperties ()
    {
        final Properties properties = new Properties ();
        properties.setProperty ("formatVersion", "1");
        properties.setProperty ("state", this.state.protocolValue);
        properties.setProperty ("requestedBuildId", this.requestedBuildId);
        properties.setProperty ("activeBuildId", this.activeBuildId);
        properties.setProperty ("message", this.message);
        return properties;
    }


    enum State
    {
        ACTIVE ("active"),
        FAILED ("failed"),
        RESTART_REQUIRED ("restartRequired");

        private final String protocolValue;


        State (final String protocolValue)
        {
            this.protocolValue = protocolValue;
        }
    }
}
