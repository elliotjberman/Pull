// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.api.ControllerPageRef;
import de.mossgrabers.pull.core.view.PageId;
import java.util.LinkedHashMap;
import java.util.Map;

/** Frozen mode names translated at the edge of core navigation; page definitions do not use them. */
public final class LegacyPageAliases
{
    private static final Map<String, PageId> CORE_PAGES = aliases ();

    private LegacyPageAliases () { }

    public static ControllerPageRef resolve (final String alias)
    {
        if (alias.isEmpty ())
            return reference (PageId.TRACK);
        final PageId page = CORE_PAGES.get (alias);
        return page == null ? ControllerPageRef.legacy (alias) : ControllerPageRef.core (page.value (), alias);
    }

    public static ControllerPageRef reference (final PageId page)
    {
        final String alias = CORE_PAGES.entrySet ().stream ().filter (entry -> entry.getValue ().equals (page)).map (Map.Entry::getKey).findFirst ().orElse ("");
        return ControllerPageRef.core (page.value (), alias);
    }

    private static Map<String, PageId> aliases ()
    {
        final Map<String, PageId> result = new LinkedHashMap<> ();
        result.put ("TRACK", PageId.TRACK);
        result.put ("WORKSPACE", PageId.PROJECT_MACROS);
        result.put ("MASTER", PageId.MASTER);
        result.put ("MASTER_TEMP", PageId.MASTER);
        result.put ("VOLUME", PageId.VOLUME);
        result.put ("PAN", PageId.PAN);
        result.put ("TRANSPORT", PageId.METRONOME);
        result.put ("AUTOMATION", PageId.AUTOMATION);
        result.put ("ACCENT", PageId.ACCENT);
        result.put ("FRAME", PageId.FRAME);
        result.put ("INFO", PageId.INFO);
        for (int index = 0; index < 8; index++)
            result.put ("SEND" + (index + 1), PageId.send (index));
        return java.util.Collections.unmodifiableMap (result);
    }
}
