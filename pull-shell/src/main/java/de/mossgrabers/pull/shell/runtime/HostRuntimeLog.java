// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.shell.runtime;

import com.bitwig.extension.controller.api.ControllerHost;

import java.util.Objects;

/**
 * Runtime log backed by Bitwig's stable controller host.
 */
final class HostRuntimeLog implements RuntimeLog
{
    private static final String PREFIX = "[Pull reload] ";

    private final ControllerHost host;


    HostRuntimeLog (final ControllerHost host)
    {
        this.host = Objects.requireNonNull (host, "host");
    }


    /** {@inheritDoc} */
    @Override
    public void info (final String message)
    {
        this.host.println (PREFIX + message);
    }


    /** {@inheritDoc} */
    @Override
    public void warn (final String message)
    {
        this.host.errorln (PREFIX + message);
    }
}
