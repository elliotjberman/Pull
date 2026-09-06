// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.ui.page;

import de.mossgrabers.pull.core.api.AutomationWriteMode;
import de.mossgrabers.pull.core.api.PreRoll;
import java.util.Objects;
import java.util.Optional;

/** The two supported settings pages have separate observed state, sharing only their geometry. */
public sealed interface SettingsPagePresentation
{
    boolean available ();

    record Automation (boolean available, boolean writingEnabled, AutomationWriteMode mode) implements SettingsPagePresentation
    {
        public Automation { mode = Objects.requireNonNull (mode, "mode"); }
    }

    record Metronome (boolean available, PreRoll preRoll, boolean duringPreRoll, Optional<Volume> volume) implements SettingsPagePresentation
    {
        public Metronome
        {
            preRoll = Objects.requireNonNull (preRoll, "preRoll");
            volume = Objects.requireNonNull (volume, "volume");
        }
    }

    /** Already normalized observed values; no parameter target or mutation capability. */
    record Volume (double value, double modulatedValue, String displayedValue)
    {
        public Volume { displayedValue = Objects.requireNonNull (displayedValue, "displayedValue"); }
    }
}
