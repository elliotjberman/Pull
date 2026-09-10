// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.bitwig.framework.daw;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import com.bitwig.extension.controller.api.CursorTrack;
import de.mossgrabers.framework.daw.IHost;


/** One native selection in flight per cursor, with one replaceable latest group-entry intent. */
public final class GroupNavigationHost implements AutoCloseable
{
    private final CursorTrack cursor;
    private final Supplier<String> projectIdentity;
    private final PendingHostOperation operation;
    private Request pending;
    private String selectionOrigin;
    private String submittedTarget;
    private boolean closed;

    public GroupNavigationHost (final IHost host, final CursorTrack cursor, final Supplier<String> projectIdentity)
    {
        this.cursor = cursor;
        this.projectIdentity = projectIdentity;
        this.operation = new PendingHostOperation (host);
    }

    /** Replace the intent without submitting another selection before the current one is observed. */
    public boolean enter (final String target, final BooleanSupplier targetValid, final Runnable select, final Runnable enter)
    {
        if (this.closed || target.isBlank () || !targetValid.getAsBoolean ())
            return false;
        if (this.pending != null && !this.valid ())
            this.cancel ();
        final boolean start = this.pending == null;
        this.pending = new Request (this.projectIdentity.get (), target, targetValid, select, enter);
        if (start)
        {
            this.selectionOrigin = this.cursor.channelId ().get ();
            if (target.equals (this.selectionOrigin))
                this.complete ();
            else
            {
                this.submitSelection ();
                this.operation.await (this::valid, this::advance, this::complete, this::retire);
            }
        }
        return true;
    }

    public void cancel ()
    {
        this.operation.cancel ();
        this.retire ();
    }

    @Override
    public void close ()
    {
        this.closed = true;
        this.operation.close ();
        this.retire ();
    }

    private boolean valid ()
    {
        final String selected = this.cursor.channelId ().get ();
        return this.pending != null && this.pending.project.equals (this.projectIdentity.get ()) && this.pending.targetValid.getAsBoolean () &&
            (this.selectionOrigin.equals (selected) || this.submittedTarget.equals (selected));
    }

    private boolean advance ()
    {
        if (!this.submittedTarget.equals (this.cursor.channelId ().get ()))
            return false;
        if (this.submittedTarget.equals (this.pending.target))
            return true;
        this.selectionOrigin = this.submittedTarget;
        this.submitSelection ();
        return false;
    }

    private void submitSelection ()
    {
        this.submittedTarget = this.pending.target;
        this.pending.select.run ();
    }

    private void complete ()
    {
        final Request request = this.pending;
        this.retire ();
        request.enter.run ();
    }

    private void retire ()
    {
        this.pending = null;
        this.submittedTarget = null;
        this.selectionOrigin = null;
    }

    private record Request (String project, String target, BooleanSupplier targetValid, Runnable select, Runnable enter) { }
}
