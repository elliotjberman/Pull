// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.api;

import java.util.Objects;

/** Observed controller preferences and existing model-cursor send metadata, sampled only on request. */
public record ControllerSettingsSnapshot (boolean available, boolean vuMetersEnabled, String globalMixMode, int mixSendOffset, CursorSendBankSnapshot cursorSends, boolean accentEnabled, int accentVelocity, SessionSettingsSnapshot session)
{
    public ControllerSettingsSnapshot
    {
        session = Objects.requireNonNull (session, "session");
        globalMixMode = Objects.requireNonNull (globalMixMode, "globalMixMode");
        cursorSends = Objects.requireNonNull (cursorSends, "cursorSends");
        if (mixSendOffset < 0 || mixSendOffset > 4 || available && (globalMixMode.isBlank () || accentVelocity < 1 || accentVelocity > 127) || !available && (accentEnabled || accentVelocity != 0))
            throw new IllegalArgumentException ("invalid controller settings");
    }

    public ControllerSettingsSnapshot (final boolean available, final boolean vuMetersEnabled, final String globalMixMode, final int mixSendOffset, final CursorSendBankSnapshot cursorSends, final boolean accentEnabled, final int accentVelocity)
    {
        this (available, vuMetersEnabled, globalMixMode, mixSendOffset, cursorSends, accentEnabled, accentVelocity, SessionSettingsSnapshot.empty ());
    }

    /** Compatibility constructor for observations without Accent settings. */
    public ControllerSettingsSnapshot (final boolean available, final boolean vuMetersEnabled, final String globalMixMode, final int mixSendOffset, final CursorSendBankSnapshot cursorSends)
    {
        this (available, vuMetersEnabled, globalMixMode, mixSendOffset, cursorSends, false, available ? 127 : 0);
    }

    public static ControllerSettingsSnapshot empty () { return new ControllerSettingsSnapshot (false, false, "", 0, CursorSendBankSnapshot.empty ()); }
}
