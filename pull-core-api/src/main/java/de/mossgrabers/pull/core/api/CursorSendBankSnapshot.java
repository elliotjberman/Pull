// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.api;

import java.util.List;
import java.util.Objects;

/** Metadata from the existing pinnable model cursor's bounded send window. No actuator identity is implied. */
public record CursorSendBankSnapshot (long generation, String channelId, int offset, List<Send> sends)
{
    public static final int CAPACITY = 8;

    public CursorSendBankSnapshot
    {
        if (generation < 0 || offset < 0)
            throw new IllegalArgumentException ("cursor send generation and offset must not be negative");
        channelId = Objects.requireNonNull (channelId, "channelId");
        sends = List.copyOf (sends);
        if (sends.size () > CAPACITY || channelId.isEmpty () && !sends.isEmpty ())
            throw new IllegalArgumentException ("cursor sends require an identified cursor and fit the installed window");
    }

    public static CursorSendBankSnapshot empty () { return new CursorSendBankSnapshot (0, "", 0, List.of ()); }

    public record Send (boolean exists, String name)
    {
        public Send { name = Objects.requireNonNull (name, "name"); }
    }
}
