// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.shell.runtime;

import de.mossgrabers.pull.core.api.PreRoll;
import de.mossgrabers.pull.core.api.TransportSettingsSnapshot;
import de.mossgrabers.pull.core.api.effect.SetPreRollEffect;
import de.mossgrabers.pull.core.api.effect.SetTransportSettingEffect;
import de.mossgrabers.pull.core.api.effect.TransportSetting;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class TransportSettingsHostTest
{
    @Test
    void independentSettingSubmissionsAwaitHostReadbackAndRecheckProject ()
    {
        final AtomicReference<String> project = new AtomicReference<> ("project-a");
        final AtomicBoolean ticks = new AtomicBoolean ();
        final AtomicBoolean metronome = new AtomicBoolean ();
        final AtomicReference<PreRoll> preRoll = new AtomicReference<> (PreRoll.NONE);
        final List<Object> writes = new ArrayList<> ();
        final TransportSettingsHost host = new TransportSettingsHost (project::get, ticks::get, writes::add, preRoll::get, writes::add, metronome::get, writes::add);
        host.apply (host.prepare (new SetTransportSettingEffect ("project-a", TransportSetting.TICK_PLAYBACK, true)));
        host.apply (host.prepare (new SetPreRollEffect ("project-a", PreRoll.TWO_BARS)));
        assertEquals (List.of (true, PreRoll.TWO_BARS), writes);
        assertEquals (new TransportSettingsSnapshot ("project-a", false, PreRoll.NONE, false), host.snapshot ());
        ticks.set (true);
        preRoll.set (PreRoll.TWO_BARS);
        assertEquals (new TransportSettingsSnapshot ("project-a", true, PreRoll.TWO_BARS, false), host.snapshot ());
        final var pending = host.prepare (new SetTransportSettingEffect ("project-a", TransportSetting.METRONOME_DURING_PRE_ROLL, true));
        project.set ("project-b");
        assertThrows (IllegalStateException.class, () -> host.apply (pending));
        assertThrows (IllegalStateException.class, () -> host.prepare (new SetPreRollEffect ("project-a", PreRoll.FOUR_BARS)));
        assertEquals (2, writes.size ());
        project.set ("");
        assertEquals (TransportSettingsSnapshot.empty (), host.snapshot ());
    }
}
