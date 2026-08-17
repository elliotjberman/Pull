// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.shell.runtime;

import de.mossgrabers.pull.core.api.ControllerSnapshot;
import de.mossgrabers.pull.core.api.CoreResult;
import de.mossgrabers.pull.core.api.event.CoreEvent;


/** Optional controller-thread observation of reload transactions for the opt-in debugger. */
interface RuntimeTraceSink
{
    /** Record one child invocation and the exact authoritative input supplied to it. */
    void transaction (long generation, CoreEvent event, ControllerSnapshot snapshot, CoreResult result);


    /** Record a validated candidate startup result before stable commit. */
    void startup (long generation, String buildID, ControllerSnapshot snapshot, CoreResult result);


    /** Record successful stable application for the preceding retained transaction. */
    void applied (long generation);


    /** Record a sparse stable lifecycle transition. */
    void lifecycle (long generation, String state, String detail);


    /** Record a failed boundary without allowing diagnostics to change runtime behavior. */
    void failure (long generation, String stage, String detail);
}
