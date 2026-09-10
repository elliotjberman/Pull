// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.bitwig.framework.daw;

import java.util.function.BooleanSupplier;

import de.mossgrabers.framework.daw.IHost;


/** One replaceable host continuation. A poll is only a chance to read state, never an acknowledgement. */
public final class PendingHostOperation implements AutoCloseable
{
    private final IHost host;
    private Request pending;
    private boolean scheduled;
    private boolean closed;

    public PendingHostOperation (final IHost host)
    {
        this.host = host;
    }

    /** Replace the pending continuation; a later matching host observation must authorize completion. */
    public void await (final BooleanSupplier valid, final BooleanSupplier ready, final Runnable complete)
    {
        this.await (valid, ready, complete, () -> { });
    }

    public void await (final BooleanSupplier valid, final BooleanSupplier ready, final Runnable complete, final Runnable cancelled)
    {
        if (this.closed)
            return;
        this.cancel ();
        this.pending = new Request (valid, ready, complete, cancelled);
        this.schedule ();
    }

    public void cancel ()
    {
        final Request request = this.pending;
        this.pending = null;
        if (request != null)
            request.cancelled.run ();
    }

    @Override
    public void close ()
    {
        this.closed = true;
        this.cancel ();
    }

    private void schedule ()
    {
        if (!this.scheduled)
        {
            this.scheduled = true;
            this.host.scheduleTask (this::poll, 20);
        }
    }

    private void poll ()
    {
        this.scheduled = false;
        final Request request = this.pending;
        if (request == null || this.closed)
            return;
        if (!request.valid.getAsBoolean ())
            this.cancel ();
        else if (request.ready.getAsBoolean ())
        {
            this.pending = null;
            request.complete.run ();
        }
        else if (--request.remaining == 0)
            this.cancel ();
        else
            this.schedule ();
    }

    private static final class Request
    {
        private final BooleanSupplier valid;
        private final BooleanSupplier ready;
        private final Runnable complete;
        private final Runnable cancelled;
        private int remaining = 150;

        private Request (final BooleanSupplier valid, final BooleanSupplier ready, final Runnable complete, final Runnable cancelled)
        {
            this.valid = valid;
            this.ready = ready;
            this.complete = complete;
            this.cancelled = cancelled;
        }
    }
}
