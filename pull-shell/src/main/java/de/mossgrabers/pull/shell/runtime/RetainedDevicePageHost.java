// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.shell.runtime;

import de.mossgrabers.framework.parameter.IParameter;
import de.mossgrabers.pull.shell.SelectionDebug;
import de.mossgrabers.pull.shell.runtime.RetainedCursorPool.Handle;
import de.mossgrabers.pull.shell.runtime.RetainedCursorPool.Profile;
import de.mossgrabers.pull.shell.runtime.RetainedCursorPool.Request;
import de.mossgrabers.pull.shell.runtime.RetainedCursorPool.Status;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Child acquisition over the shared pool; source navigation and exact cleanup are separate fences. */
final class RetainedDevicePageHost implements RetainedDeviceParameters
{
    private static final String NAMESPACE = "device-parameters";
    private static final int ACQUISITION_TICKS = 16;

    record Source (String trackId, int page, boolean available) { }

    /** Revisions record observed invalidations, so disappearance followed by undo cannot revive a lease. */
    record Observation (boolean exists, boolean pinned, int page, long ownRevision, long sourceRevision,
                        boolean sourceDeviceEqual, boolean sourceMappingEditor, boolean mappingEditor)
    {
        boolean sourceAligned (final int expectedPage)
        {
            return this.exists && this.pinned && this.page == expectedPage && this.sourceDeviceEqual && !this.sourceMappingEditor && !this.mappingEditor;
        }
    }

    interface Access
    {
        Source source ();
        void acquireDevice (int poolSlot);
        void selectPage (int poolSlot, int page);
        boolean propertiesCoherent (int poolSlot);
        Observation observe (int poolSlot);
        List<IParameter> parameters (int poolSlot);
        void recordDiagnostics (int poolSlot);
    }

    private final RetainedCursorPool pool;
    private final Access access;
    private final List<Lease> leases = new ArrayList<> ();
    private Lease selected;
    private long nextOwner;
    private long sample;

    RetainedDevicePageHost (final RetainedCursorPool pool, final Access access)
    {
        this.pool = Objects.requireNonNull (pool, "pool");
        this.access = Objects.requireNonNull (access, "access");
    }

    @Override
    public void requestDevicePage (final boolean active, final Set<String> cleanupOwners)
    {
        final Source source = this.access.source ();
        if (this.selected != null && (!active || !this.matchesSource (this.selected, source) ||
            this.selected.ready != null && !this.current (this.selected)))
            this.selected = null;
        if (active && source.available () && this.selected == null)
        {
            final long generation = Math.incrementExact (this.nextOwner);
            this.nextOwner = generation;
            this.selected = new Lease ("retained-device-page:" + generation, generation, source);
            this.leases.add (this.selected);
        }
        this.leases.removeIf (lease -> lease != this.selected && (!cleanupOwners.contains (lease.owner) || !this.addressable (lease)));
        final List<Request> requests = new ArrayList<> (this.leases.size ());
        for (final Lease lease: this.leases)
            requests.add (new Request (lease.owner, lease.source.trackId (), Profile.DEVICE_PAGE));
        this.pool.reconcile (NAMESPACE, requests);
    }

    /** Exactly one call per host tick. Acquisition commands cannot acknowledge their own result. */
    void tick ()
    {
        this.sample = Math.incrementExact (this.sample);
        final Lease lease = this.selected;
        if (lease == null || lease.ready != null)
            return;
        final Source source = this.access.source ();
        if (!this.matchesSource (lease, source))
        {
            this.selected = null;
            return;
        }
        final var acquired = this.pool.lookup (NAMESPACE, lease.owner);
        if (SelectionDebug.recording ())
            this.recordDiagnostics (lease, source, acquired);
        if (acquired.status () != Status.READY)
            return;
        if (lease.handle == null)
        {
            lease.handle = acquired.handle ();
            lease.submittedAt = this.sample;
            this.access.acquireDevice (lease.handle.slot ());
            return;
        }
        if (!lease.handle.equals (acquired.handle ()) || !this.pool.valid (lease.handle) || this.sample - lease.submittedAt > ACQUISITION_TICKS)
        {
            this.selected = null;
            return;
        }
        final Observation observed = this.access.observe (lease.handle.slot ());
        if (!lease.pageSubmitted)
        {
            if (observed.exists () && observed.pinned () && observed.sourceDeviceEqual () && !observed.mappingEditor ())
            {
                this.access.selectPage (lease.handle.slot (), lease.source.page ());
                lease.pageSubmitted = true;
            }
            return;
        }
        if (!observed.sourceAligned (lease.source.page ()) || !this.access.propertiesCoherent (lease.handle.slot ()))
        {
            lease.confirmations = 0;
            lease.last = null;
            return;
        }
        if (lease.last != null && (lease.last.ownRevision () != observed.ownRevision () || lease.last.sourceRevision () != observed.sourceRevision ()))
            lease.confirmations = 0;
        lease.last = observed;
        if (++lease.confirmations < 2)
            return;
        lease.ready = new DevicePage (lease.owner, lease.generation, lease.source.page (), this.access.parameters (lease.handle.slot ()),
            () -> this.current (lease), () -> this.addressable (lease));
    }

    @Override
    public DevicePage devicePage ()
    {
        return this.selected != null && this.selected.ready != null && this.current (this.selected) ? this.selected.ready : null;
    }

    /** Uses the existing bounded diagnostic lane and interested values only while explicitly armed. */
    private void recordDiagnostics (final Lease lease, final Source source, final RetainedCursorPool.Result acquired)
    {
        final String phase = lease.handle == null ? "track" : lease.pageSubmitted ? "page" : "device";
        final Observation observed = lease.handle == null ? null : this.access.observe (lease.handle.slot ());
        final boolean coherent = lease.handle != null && this.access.propertiesCoherent (lease.handle.slot ());
        SelectionDebug.record ("DEVICE_PAGE", "owner=" + lease.owner + " phase=" + phase + " age=" + (lease.handle == null ? 0 : this.sample - lease.submittedAt) +
            " requested=" + lease.source + " source=" + source + " pool=" + acquired + " handle=" + lease.handle +
            " confirmations=" + lease.confirmations + " observed=" + observed + " coherent=" + coherent);
        if (lease.handle != null)
            this.access.recordDiagnostics (lease.handle.slot ());
    }

    private boolean matchesSource (final Lease lease, final Source source)
    {
        return source.available () && lease.source.trackId ().equals (source.trackId ()) && lease.source.page () == source.page ();
    }

    private boolean current (final Lease lease)
    {
        if (this.selected != lease || !this.addressable (lease) || !this.matchesSource (lease, this.access.source ()))
            return false;
        final Observation observed = this.access.observe (lease.handle.slot ());
        return observed.sourceRevision () == lease.last.sourceRevision () && observed.sourceAligned (lease.source.page ());
    }

    private boolean addressable (final Lease lease)
    {
        if (lease.ready == null || lease.handle == null || !this.pool.valid (lease.handle))
            return false;
        final Observation observed = this.access.observe (lease.handle.slot ());
        return observed.exists () && observed.pinned () && observed.page () == lease.source.page () &&
            observed.ownRevision () == lease.last.ownRevision () && !observed.mappingEditor ();
    }

    private static final class Lease
    {
        private final String owner;
        private final long generation;
        private final Source source;
        private Handle handle;
        private long submittedAt;
        private boolean pageSubmitted;
        private int confirmations;
        private Observation last;
        private DevicePage ready;

        private Lease (final String owner, final long generation, final Source source)
        {
            this.owner = owner;
            this.generation = generation;
            this.source = source;
        }
    }
}
