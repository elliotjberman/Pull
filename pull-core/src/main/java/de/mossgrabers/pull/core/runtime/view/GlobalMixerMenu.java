// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.api.ControllerSettingsSnapshot;
import de.mossgrabers.pull.core.api.CursorSendBankSnapshot;
import de.mossgrabers.pull.core.api.effect.CoreEffect;
import de.mossgrabers.pull.core.api.effect.SelectControllerModeEffect;
import de.mossgrabers.pull.core.api.effect.SetControllerIntegerSettingEffect;
import de.mossgrabers.pull.core.api.effect.SetControllerModeSettingEffect;

import java.util.ArrayList;
import java.util.List;

/** Global mixer menu policy, shared by pages without depending on stable mode ordinals. */
final class GlobalMixerMenu
{
    private GlobalMixerMenu () { }

    static boolean hasAdditionalSends (final ControllerSettingsSnapshot settings)
    {
        return settings.cursorSends ().sends ().size () > 5 && settings.cursorSends ().sends ().get (5).exists ();
    }

    static List<Entry> entries (final ControllerSettingsSnapshot settings, final String activeMode)
    {
        final ArrayList<Entry> entries = new ArrayList<> (8);
        for (int index = 0; index < 8; index++) entries.add (new Entry ("", false, false));
        if (!settings.available ()) return List.copyOf (entries);
        entries.set (0, new Entry ("Volume", "VOLUME".equals (activeMode), false));
        entries.set (1, new Entry ("Pan", "PAN".equals (activeMode), false));
        entries.set (7, new Entry ("Crossfader", "CROSSFADER".equals (activeMode), false));
        final boolean additional = hasAdditionalSends (settings);
        final int offset = additional ? settings.mixSendOffset () : 0;
        if (!additional)
            for (int index = 0; index < 5; index++) entries.set (index + 2, send (settings, index, activeMode));
        else if (offset == 0)
        {
            for (int index = 0; index < 4; index++) entries.set (index + 2, send (settings, index, activeMode));
            entries.set (6, new Entry (">", false, true));
        }
        else
        {
            entries.set (2, new Entry ("<", false, true));
            for (int index = 0; index < 4; index++) entries.set (index + 3, send (settings, offset + index, activeMode));
        }
        return List.copyOf (entries);
    }

    static List<CoreEffect> select (final int column, final ControllerSettingsSnapshot settings, final long layoutGeneration)
    {
        if (!settings.available () || column < 0 || column >= 8) return List.of ();
        final ArrayList<CoreEffect> effects = new ArrayList<> (3);
        String mode = switch (column) { case 0 -> "VOLUME"; case 1 -> "PAN"; case 7 -> "CROSSFADER"; default -> ""; };
        if (mode.isEmpty ())
        {
            final boolean additional = hasAdditionalSends (settings);
            final int offset = additional ? settings.mixSendOffset () : 0;
            effects.addAll (normalize (settings));
            if (additional && (offset == 0 && column == 6 || offset > 0 && column == 2))
            {
                effects.add (offset (offset == 0 ? 4 : 0));
                return List.copyOf (effects);
            }
            final int send = offset == 0 ? column - 2 : column - 3 + offset;
            if (send < 0 || send >= 8) return List.copyOf (effects);
            mode = "SEND" + (send + 1);
        }
        effects.add (new SetControllerModeSettingEffect (SetControllerModeSettingEffect.Setting.GLOBAL_MIX_MODE, mode));
        effects.add (new SelectControllerModeEffect (layoutGeneration, mode));
        return List.copyOf (effects);
    }

    static List<CoreEffect> normalize (final ControllerSettingsSnapshot settings)
    {
        return settings.available () && !hasAdditionalSends (settings) && settings.mixSendOffset () != 0 ? List.of (offset (0)) : List.of ();
    }

    private static SetControllerIntegerSettingEffect offset (final int value)
    {
        return new SetControllerIntegerSettingEffect (SetControllerIntegerSettingEffect.Setting.MIX_SEND_OFFSET, value);
    }

    private static Entry send (final ControllerSettingsSnapshot settings, final int index, final String activeMode)
    {
        final List<CursorSendBankSnapshot.Send> sends = settings.cursorSends ().sends ();
        final CursorSendBankSnapshot.Send send = index < sends.size () ? sends.get (index) : null;
        final boolean exists = send != null && send.exists ();
        return new Entry (exists ? send.name ().trim () : "", exists && ("SEND" + (index + 1)).equals (activeMode), false);
    }

    record Entry (String text, boolean selected, boolean arrow) { }
}
