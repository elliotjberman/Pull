// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.api.AutomationSnapshot;
import de.mossgrabers.pull.core.api.AutomationWriteMode;
import de.mossgrabers.pull.core.api.ControllerSnapshot;
import de.mossgrabers.pull.core.api.effect.CoreEffect;
import de.mossgrabers.pull.core.api.effect.SetAutomationModeEffect;
import de.mossgrabers.pull.core.api.effect.SetAutomationWriteEffect;
import java.util.List;

/** One shared lane for raw mode and write-enable requests, each acknowledged by later read-back. */
final class AutomationControlState
{
    private String project = "";
    private AutomationWriteMode desiredMode;
    private Boolean desiredWrite;
    private AutomationWriteMode submittedMode;
    private Boolean submittedWrite;
    private long submittedAt;

    void toggle (final ControllerSnapshot snapshot)
    {
        this.align (snapshot.bridge ().automation ());
        this.desiredWrite = Boolean.valueOf (!(this.desiredWrite == null ? snapshot.bridge ().automation ().writingEnabled () : this.desiredWrite.booleanValue ()));
    }

    void select (final ControllerSnapshot snapshot, final AutomationWriteMode mode)
    {
        this.align (snapshot.bridge ().automation ());
        this.desiredMode = mode;
        this.desiredWrite = Boolean.valueOf (mode != null);
    }

    List<CoreEffect> advance (final ControllerSnapshot snapshot)
    {
        final AutomationSnapshot observed = snapshot.bridge ().automation ();
        this.align (observed);
        if (!observed.available ()) return List.of ();
        if (this.submittedMode != null && observed.mode () == this.submittedMode) this.submittedMode = null;
        if (this.submittedWrite != null && observed.writingEnabled () == this.submittedWrite.booleanValue ()) this.submittedWrite = null;
        if (this.submittedMode != null || this.submittedWrite != null)
        {
            if (snapshot.monotonicTimeNanos () - this.submittedAt >= 5_000_000_000L) this.clear ();
            return List.of ();
        }
        if (this.desiredMode != null && observed.mode () != this.desiredMode)
        {
            this.submittedMode = this.desiredMode;
            this.submittedAt = snapshot.monotonicTimeNanos ();
            return List.of (new SetAutomationModeEffect (this.project, this.desiredMode));
        }
        this.desiredMode = null;
        if (this.desiredWrite != null && observed.writingEnabled () != this.desiredWrite.booleanValue ())
        {
            this.submittedWrite = this.desiredWrite;
            this.submittedAt = snapshot.monotonicTimeNanos ();
            return List.of (new SetAutomationWriteEffect (this.project, this.desiredWrite.booleanValue ()));
        }
        this.desiredWrite = null;
        return List.of ();
    }

    boolean pending () { return this.desiredWrite != null || this.submittedMode != null || this.submittedWrite != null; }

    private void align (final AutomationSnapshot snapshot)
    {
        if (!snapshot.available () || !this.project.equals (snapshot.projectIdentity ())) this.clear ();
        this.project = snapshot.projectIdentity ();
    }

    private void clear ()
    {
        this.desiredMode = null;
        this.desiredWrite = null;
        this.submittedMode = null;
        this.submittedWrite = null;
    }
}
