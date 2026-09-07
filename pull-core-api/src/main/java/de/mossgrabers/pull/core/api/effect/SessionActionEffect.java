// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.api.effect;

import de.mossgrabers.pull.core.api.SessionLocation;
import java.util.Objects;

/** One primitive operation on an exact Session slot or scene. Release is submission, not host acknowledgement. */
public record SessionActionEffect (SessionLocation target, Action action) implements CoreEffect
{
    public enum Action { LAUNCH, LAUNCH_ALT, RELEASE, RELEASE_ALT, SELECT, REMOVE, DUPLICATE, START_RECORDING, BROWSE }
    public SessionActionEffect
    {
        Objects.requireNonNull (target, "target");
        Objects.requireNonNull (action, "action");
        if (target.isScene () && (action == Action.START_RECORDING || action == Action.BROWSE) || !target.isScene () && action == Action.DUPLICATE)
            throw new IllegalArgumentException ("Operation does not apply to this Session location");
    }
    public boolean isRelease () { return this.action == Action.RELEASE || this.action == Action.RELEASE_ALT; }
}
