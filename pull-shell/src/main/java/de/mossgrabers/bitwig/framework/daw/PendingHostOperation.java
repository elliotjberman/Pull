// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.bitwig.framework.daw;

import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;

/** One replaceable host continuation. A poll is only a chance to read state, never an acknowledgement. */
public final class PendingHostOperation implements AutoCloseable
{
    private final BiConsumer<Runnable, Long> scheduler;
    private Request pending;
    private boolean scheduled;
    private boolean closed;

    public PendingHostOperation (final BiConsumer<Runnable, Long> scheduler)
    {
        this.scheduler = scheduler;
    }

    /** Replace the pending continuation; a later matching host observation must authorize completion. */
    public void await (final BooleanSupplier valid, final BooleanSupplier advance, final Runnable complete, final Runnable cancelled)
    {
        this.await (valid, advance, complete, cancelled, () -> { });
    }

    /** Advance may submit a dependent step and return false; timeout cancels before reporting failure. */
    public void await (final BooleanSupplier valid, final BooleanSupplier advance, final Runnable complete, final Runnable cancelled, final Runnable timedOut)
    {
        if (this.closed)
            return;
        this.cancel ();
        this.pending = new Request (valid, advance, complete, cancelled, timedOut);
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
            this.scheduler.accept (this::poll, 20L);
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
        else if (request.advance.getAsBoolean ())
        {
            this.pending = null;
            request.complete.run ();
        }
        else if (--request.remaining == 0)
        {
            this.cancel ();
            request.timedOut.run ();
        }
        else
            this.schedule ();
    }

    private static final class Request
    {
        private final BooleanSupplier valid;
        private final BooleanSupplier advance;
        private final Runnable complete;
        private final Runnable cancelled;
        private final Runnable timedOut;
        private int remaining = 150;

        private Request (final BooleanSupplier valid, final BooleanSupplier advance, final Runnable complete, final Runnable cancelled, final Runnable timedOut)
        {
            this.valid = valid;
            this.advance = advance;
            this.complete = complete;
            this.cancelled = cancelled;
            this.timedOut = timedOut;
        }
    }
}
