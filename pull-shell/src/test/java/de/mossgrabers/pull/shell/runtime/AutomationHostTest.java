// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.shell.runtime;

import de.mossgrabers.pull.core.api.AutomationSnapshot;
import de.mossgrabers.pull.core.api.effect.SetAutomationWriteEffect;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutomationHostTest
{
    @Test
    void rawModeAndOverrideResetAreIndependentNativeRequestsWithProjectFencing ()
    {
        final AtomicReference<String> project = new AtomicReference<> ("project-a");
        final AtomicReference<de.mossgrabers.pull.core.api.AutomationWriteMode> mode = new AtomicReference<> (de.mossgrabers.pull.core.api.AutomationWriteMode.LATCH);
        final List<Object> writes = new ArrayList<> ();
        final AutomationHost host = new AutomationHost (project::get, () -> false, writes::add, () -> false, mode::get, writes::add, () -> writes.add ("reset"));
        final var modeRequest = host.prepare (new de.mossgrabers.pull.core.api.effect.SetAutomationModeEffect ("project-a", de.mossgrabers.pull.core.api.AutomationWriteMode.TOUCH));
        host.apply (modeRequest);
        assertEquals (List.of (de.mossgrabers.pull.core.api.AutomationWriteMode.TOUCH), writes);
        assertEquals (de.mossgrabers.pull.core.api.AutomationWriteMode.LATCH, host.snapshot ().mode ());
        assertEquals (false, host.snapshot ().writingEnabled ());
        mode.set (de.mossgrabers.pull.core.api.AutomationWriteMode.TOUCH);
        assertEquals (mode.get (), host.snapshot ().mode ());
        final var reset = host.prepare (new de.mossgrabers.pull.core.api.effect.ResetAutomationOverridesEffect ("project-a"));
        host.apply (reset);
        project.set ("project-b");
        assertThrows (IllegalStateException.class, () -> host.apply (modeRequest));
        assertThrows (IllegalStateException.class, () -> host.apply (reset));
        assertEquals (List.of (de.mossgrabers.pull.core.api.AutomationWriteMode.TOUCH, "reset"), writes);
    }

    @Test
    void submissionDoesNotPretendTheHostHasAcknowledgedWritingChange ()
    {
        final AtomicBoolean writing = new AtomicBoolean (true);
        final AtomicBoolean stop = new AtomicBoolean (true);
        final List<Boolean> submitted = new ArrayList<> ();
        final AutomationHost host = new AutomationHost (() -> "project-a", writing::get, submitted::add, stop::get);
        final AutomationHost.PreparedWrite prepared = host.prepare (new SetAutomationWriteEffect ("project-a", false));
        assertTrue (submitted.isEmpty ());
        host.apply (prepared);
        assertEquals (List.of (false), submitted);
        assertEquals (new AutomationSnapshot ("project-a", true, true), host.snapshot ());
        writing.set (false);
        stop.set (false);
        assertEquals (new AutomationSnapshot ("project-a", false, false), host.snapshot ());
    }

    @Test
    void projectIsRecheckedBetweenPreparationAndApplication ()
    {
        final AtomicReference<String> project = new AtomicReference<> ("project-a");
        final List<Boolean> submitted = new ArrayList<> ();
        final AutomationHost host = new AutomationHost (project::get, () -> true, submitted::add, () -> true);
        assertThrows (IllegalStateException.class, () -> host.prepare (new SetAutomationWriteEffect ("project-b", false)));
        final AutomationHost.PreparedWrite prepared = host.prepare (new SetAutomationWriteEffect ("project-a", false));
        project.set ("project-b");
        assertThrows (IllegalStateException.class, () -> host.apply (prepared));
        assertTrue (submitted.isEmpty ());
        project.set (null);
        assertEquals (AutomationSnapshot.empty (), host.snapshot ());
    }
}
