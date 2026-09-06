// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.api.ControllerLayoutSnapshot;
import de.mossgrabers.pull.core.api.ControllerSnapshot;
import de.mossgrabers.pull.core.api.effect.CoreEffect;
import de.mossgrabers.pull.core.api.effect.SelectControllerModeEffect;
import java.util.List;

/** One core-owned transition and return debt for the native manager's single temporary slot. */
final class ControllerPageTransitions
{
    private static final long ACKNOWLEDGEMENT_TIMEOUT_NANOS = 5_000_000_000L;
    private Request current;

    Request temporary (final ControllerLayoutSnapshot origin, final String page)
    {
        return this.replace (new Request (origin, new SelectControllerModeEffect (origin.generation (), page, SelectControllerModeEffect.Operation.TEMPORARY)));
    }

    Request select (final ControllerLayoutSnapshot origin, final String page)
    {
        return this.replace (new Request (origin, new SelectControllerModeEffect (origin.generation (), page)));
    }

    Request restore (final ControllerLayoutSnapshot origin)
    {
        return this.replace (new Request (origin, SelectControllerModeEffect.restore (origin.generation ())));
    }

    /** Transfer an inherited return flag only while its original slot transaction is still current. */
    Request adopt (final Request previous)
    {
        if (!this.current (previous) || !previous.temporary ()) return null;
        final Request next = new Request (previous.origin, previous.effect);
        next.submitted = previous.submitted;
        next.submittedRevision = previous.submittedRevision;
        next.submittedAt = previous.submittedAt;
        next.acknowledged = previous.acknowledged;
        return this.replace (next);
    }

    private Request replace (final Request next)
    {
        if (this.current != null) this.current.closed = true;
        this.current = next;
        return next;
    }

    void observe (final ControllerSnapshot snapshot)
    {
        final Request request = this.current;
        if (request == null || !request.submitted || request.closed) return;
        final ControllerLayoutSnapshot layout = snapshot.bridge ().layout ();
        final boolean matchingSlot = layout.temporaryMode () && request.effect.modeId ().equals (layout.modeId ()) &&
            request.origin.viewId ().equals (layout.viewId ()) && request.origin.activeModeId ().equals (layout.activeModeId ()) &&
            request.origin.previousModeId ().equals (layout.previousModeId ());
        if (request.acknowledged)
        {
            if (!matchingSlot) this.cancel (request);
            return;
        }
        if (snapshot.revision () > request.submittedRevision && matchingSlot)
            request.acknowledged = true;
        else if (snapshot.monotonicTimeNanos () - request.submittedAt >= ACKNOWLEDGEMENT_TIMEOUT_NANOS)
            this.cancel (request);
    }

    /** Called only after the owning gesture's semantic BEGIN has been admitted. */
    List<CoreEffect> advance (final Request request, final ControllerSnapshot snapshot)
    {
        this.observe (snapshot);
        if (!this.current (request)) return List.of ();
        if (!request.submitted)
        {
            if (request.origin.generation () == 0 || !request.origin.equals (snapshot.bridge ().layout ()))
            {
                this.cancel (request);
                return List.of ();
            }
            request.submitted = true;
            request.submittedRevision = snapshot.revision ();
            request.submittedAt = snapshot.monotonicTimeNanos ();
            if (!request.temporary ()) this.cancel (request);
            // A dependent return must use a later snapshot, never the entry's result.
            return List.of (request.effect);
        }
        if (request.released && request.restoreOnRelease && request.acknowledged)
        {
            this.cancel (request);
            return List.of (SelectControllerModeEffect.restore (snapshot.bridge ().layout ().generation ()));
        }
        return List.of ();
    }

    void release (final Request request, final boolean restore)
    {
        if (!this.current (request)) return;
        request.released = true;
        request.restoreOnRelease = restore;
    }

    boolean complete (final Request request)
    {
        return !this.current (request) || request.submitted && request.released && !request.restoreOnRelease;
    }

    boolean pending ()
    {
        return this.current != null && this.current.submitted && !this.current.acknowledged;
    }

    void cancel (final Request request)
    {
        if (request == null) return;
        request.closed = true;
        if (this.current == request) this.current = null;
    }

    private boolean current (final Request request) { return request != null && request == this.current && !request.closed; }

    static final class Request
    {
        private final ControllerLayoutSnapshot origin;
        private final SelectControllerModeEffect effect;
        private boolean submitted;
        private long submittedRevision;
        private long submittedAt;
        private boolean acknowledged;
        private boolean released;
        private boolean restoreOnRelease;
        private boolean closed;
        private Request (final ControllerLayoutSnapshot origin, final SelectControllerModeEffect effect) { this.origin = origin; this.effect = effect; }
        private boolean temporary () { return this.effect.operation () == SelectControllerModeEffect.Operation.TEMPORARY; }
    }
}
