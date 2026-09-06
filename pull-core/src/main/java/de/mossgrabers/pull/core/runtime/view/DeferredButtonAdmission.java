// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.api.ControllerActionIntent;
import de.mossgrabers.pull.core.api.DesiredParameterInteraction;
import de.mossgrabers.pull.core.api.effect.CoreEffect;
import de.mossgrabers.pull.core.view.ResolvedControllerAction;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/** Bounded admission lifetime for a physical gesture whose semantic BEGIN may be deferred. */
final class DeferredButtonAdmission
{
    private final Set<Ticket> live = new HashSet<> ();

    Ticket begin ()
    {
        if (this.live.size () >= DesiredParameterInteraction.PENDING_ACTION_CAPACITY + 1)
            throw new IllegalStateException ("deferred button continuation capacity exhausted");
        final Ticket ticket = new Ticket ();
        this.live.add (ticket);
        return ticket;
    }

    ResolvedControllerAction action (final Ticket ticket, final ControllerActionIntent intent, final Supplier<List<CoreEffect>> onAdmission)
    {
        return ResolvedControllerAction.of (intent, () -> {
            if (!this.live.contains (ticket) || ticket.admitted) return List.of ();
            ticket.admitted = true;
            return onAdmission.get ();
        });
    }

    void finish (final Ticket ticket)
    {
        this.live.remove (ticket);
        ticket.closed = true;
    }

    void clear ()
    {
        this.live.forEach (ticket -> ticket.closed = true);
        this.live.clear ();
    }

    static final class Ticket
    {
        private boolean admitted;
        private boolean closed;
        private Ticket () { }
        boolean admitted () { return this.admitted && !this.closed; }
    }
}
