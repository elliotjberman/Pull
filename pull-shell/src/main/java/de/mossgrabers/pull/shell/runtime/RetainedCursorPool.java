// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.shell.runtime;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;


/**
 * Fixed, initialization-owned cursor resources. Requests are complete per namespace; a consumer
 * retains an exact handle while asynchronous work or cleanup still needs that resource. No host
 * object, selection policy, or product behavior belongs in this allocation state machine.
 */
final class RetainedCursorPool
{
    enum Profile { MIX, CLIP_SCAN, CLIP_ACTUATOR, DEVICE_PAGE }
    enum Status { READY, PENDING, MISSING, UNAVAILABLE }
    enum Coverage { FULL, PARTIAL, PENDING }

    record Request (String owner, String trackId, Profile profile)
    {
        Request
        {
            requireIdentity (owner, "owner");
            requireIdentity (trackId, "trackId");
            Objects.requireNonNull (profile, "profile");
        }
    }

    record Handle (long projectGeneration, int slot, long assignmentGeneration, String trackId) { }
    record Result (Status status, Handle handle, String reason) { }

    /** FULL is permitted only for an explicitly complete root window. Unknown counts use -1. */
    record Catalog (long projectGeneration, Coverage coverage, int totalCount, int windowOffset,
                    int windowCapacity, List<String> trackIds)
    {
        Catalog
        {
            Objects.requireNonNull (coverage, "coverage");
            trackIds = List.copyOf (trackIds);
            if (windowCapacity < 1 || windowOffset < 0 || totalCount < -1 || trackIds.size () > windowCapacity)
                throw new IllegalArgumentException ("Invalid discovery window");
            if (new HashSet<> (trackIds).size () != trackIds.size () || trackIds.stream ().anyMatch (String::isBlank))
                throw new IllegalArgumentException ("Catalog identities must be distinct and nonempty");
            if (coverage == Coverage.FULL && (windowOffset != 0 || totalCount != trackIds.size ()))
                throw new IllegalArgumentException ("Full coverage requires the complete root catalog");
        }
    }

    /**
     * Sequence advances with host observation, never with a getter or command submission.
     * propertiesAssignmentGeneration acknowledges that every required profile property belongs
     * to this assignment. An aligned UUID alone must not acknowledge stale parameter values.
     */
    record Observation (long sequence, boolean exists, String trackId, boolean pinned,
                        long propertiesAssignmentGeneration) { }

    interface Host
    {
        Catalog catalog ();

        /** Recheck the discovery proxy UUID; false rejects the request without submitting it. */
        boolean assign (Handle handle);

        /** Available by slot even immediately before its first assignment. */
        Observation observe (Handle handle);

        /** Consumer holds have all ended, or the owning project has gone away. */
        void release (Handle handle);
    }

    private final Host host;
    private final List<Slot> slots;
    private final Map<Owner, Request> desired = new LinkedHashMap<> ();
    private final Map<Owner, Slot> assignments = new LinkedHashMap<> ();
    private final Set<Owner> rejected = new HashSet<> ();
    private Catalog catalog;
    private long nextAssignmentGeneration;


    RetainedCursorPool (final List<Profile> profiles, final Host host)
    {
        this.host = Objects.requireNonNull (host, "host");
        if (profiles.isEmpty () || profiles.size () > 64)
            throw new IllegalArgumentException ("Cursor capacity must be between 1 and 64");
        final List<Slot> resources = new ArrayList<> (profiles.size ());
        for (int index = 0; index < profiles.size (); index++)
            resources.add (new Slot (index, Objects.requireNonNull (profiles.get (index), "profile")));
        this.slots = List.copyOf (resources);
        this.catalog = Objects.requireNonNull (host.catalog (), "catalog");
    }


    /** Replace one consumer's complete desired assignments without disturbing other consumers. */
    void reconcile (final String namespace, final List<Request> requests)
    {
        requireIdentity (namespace, "namespace");
        final Map<Owner, Request> next = new LinkedHashMap<> ();
        for (final Request request: List.copyOf (requests))
        {
            if (next.put (new Owner (namespace, request.owner ()), request) != null)
                throw new IllegalArgumentException ("Duplicate cursor owner");
        }
        this.refreshCatalog ();
        this.desired.keySet ().removeIf (owner -> owner.namespace ().equals (namespace));
        this.rejected.removeIf (owner -> owner.namespace ().equals (namespace));
        this.desired.putAll (next);
        this.reconcileAssignments ();
    }


    /** Observe occupied resources and then retry pending discovery/capacity requests. */
    void refresh ()
    {
        this.refreshCatalog ();
        for (final Slot slot: this.slots)
        {
            if (slot.handle != null && !slot.revoked)
                this.observe (slot);
        }
        this.reconcileAssignments ();
    }


    Catalog catalog ()
    {
        return this.catalog;
    }


    /** Pending requests and retained cleanup keep host observation active. */
    boolean hasDemand ()
    {
        return !this.desired.isEmpty () || this.slots.stream ().anyMatch (slot -> slot.handle != null);
    }


    /** Read only: looking up or preparing an effect never allocates or selects a cursor. */
    Result lookup (final String namespace, final String owner)
    {
        final Owner key = new Owner (namespace, owner);
        final Request request = this.desired.get (key);
        if (request == null)
            return new Result (Status.UNAVAILABLE, null, "not-requested");
        final Slot slot = this.assignments.get (key);
        if (slot != null && !slot.revoked && (!slot.ready || this.valid (slot.handle)))
            return new Result (slot.ready ? Status.READY : Status.PENDING, slot.handle,
                slot.ready ? "" : "awaiting-host-observation");
        if (this.rejected.contains (key) && this.catalog.trackIds ().contains (request.trackId ()))
            return new Result (Status.PENDING, null, "assignment-rejected");
        return this.unallocated (request);
    }


    /** Resource ownership only; pending resources may be sampled through this fence. */
    boolean assigned (final Handle handle)
    {
        return this.find (handle) != null && this.host.catalog ().projectGeneration () == handle.projectGeneration ();
    }


    /** Exact addressability for effects and retained cleanup, including a retiring assignment. */
    boolean valid (final Handle handle)
    {
        final Slot slot = this.find (handle);
        return slot != null && this.assigned (handle) && !slot.revoked && slot.ready && this.observe (slot);
    }


    /** New work additionally requires this assignment to remain in its owner's desired state. */
    boolean writable (final Handle handle)
    {
        final Slot slot = this.find (handle);
        return slot != null && slot.owner != null && this.valid (handle);
    }


    boolean retain (final Handle handle)
    {
        if (!this.writable (handle))
            return false;
        final Slot slot = this.find (handle);
        slot.holds = Math.incrementExact (slot.holds);
        return true;
    }


    /** Call only after later host acknowledgement of cleanup, or explicit abandonment on loss. */
    void release (final Handle handle)
    {
        final Slot slot = this.find (handle);
        if (slot == null || slot.holds == 0)
            return;
        slot.holds--;
        if (slot.holds == 0 && slot.owner == null)
            this.free (slot);
    }


    private void refreshCatalog ()
    {
        final Catalog observed = Objects.requireNonNull (this.host.catalog (), "catalog");
        if (observed.projectGeneration () != this.catalog.projectGeneration ())
        {
            // Replaying requests for a new project is explicit; old work cannot adopt a same UUID.
            this.desired.clear ();
            this.assignments.clear ();
            this.rejected.clear ();
            this.catalog = observed;
            RuntimeException failure = null;
            for (final Slot slot: this.slots)
            {
                if (slot.handle == null)
                    continue;
                try
                {
                    this.host.release (slot.handle);
                }
                catch (final RuntimeException ex)
                {
                    if (failure == null)
                        failure = ex;
                    else
                        failure.addSuppressed (ex);
                }
                finally
                {
                    slot.clear ();
                }
            }
            if (failure != null)
                throw failure;
        }
        this.catalog = observed;
    }


    private void reconcileAssignments ()
    {
        for (final Slot slot: this.slots)
        {
            if (slot.owner == null)
                continue;
            final Request request = this.desired.get (slot.owner);
            final boolean vanished = !slot.ready && this.catalog.coverage () == Coverage.FULL && !this.catalog.trackIds ().contains (slot.handle.trackId ());
            if (slot.revoked || vanished || request == null || request.profile () != slot.profile || !request.trackId ().equals (slot.handle.trackId ()))
            {
                this.assignments.remove (slot.owner);
                slot.owner = null;
                if (slot.holds == 0)
                    this.free (slot);
            }
        }
        for (final Map.Entry<Owner, Request> entry: this.desired.entrySet ())
        {
            if (this.assignments.containsKey (entry.getKey ()) || !this.catalog.trackIds ().contains (entry.getValue ().trackId ()))
                continue;
            for (final Slot slot: this.slots)
            {
                if (slot.handle == null && slot.profile == entry.getValue ().profile ())
                {
                    this.nextAssignmentGeneration = Math.incrementExact (this.nextAssignmentGeneration);
                    final Handle handle = new Handle (this.catalog.projectGeneration (), slot.index, this.nextAssignmentGeneration, entry.getValue ().trackId ());
                    slot.submittedSequence = this.host.observe (handle).sequence ();
                    slot.handle = handle;
                    slot.owner = entry.getKey ();
                    this.assignments.put (slot.owner, slot);
                    try
                    {
                        if (this.host.assign (handle))
                            this.rejected.remove (entry.getKey ());
                        else
                        {
                            this.assignments.remove (slot.owner);
                            this.rejected.add (slot.owner);
                            slot.clear ();
                        }
                    }
                    catch (final RuntimeException ex)
                    {
                        slot.revoked = true;
                        throw ex;
                    }
                    break;
                }
            }
        }
    }


    private Result unallocated (final Request request)
    {
        if (this.catalog.coverage () == Coverage.PENDING)
            return new Result (Status.PENDING, null, "catalog-pending");
        if (!this.catalog.trackIds ().contains (request.trackId ()))
            return this.catalog.coverage () == Coverage.FULL ? new Result (Status.MISSING, null, "track-missing") :
                new Result (Status.UNAVAILABLE, null, "outside-discovery-coverage");
        if (this.slots.stream ().noneMatch (slot -> slot.profile == request.profile ()))
            return new Result (Status.UNAVAILABLE, null, "profile-unavailable");
        return new Result (Status.UNAVAILABLE, null, "capacity-exhausted");
    }


    private boolean observe (final Slot slot)
    {
        final Observation observed = Objects.requireNonNull (this.host.observe (slot.handle), "observation");
        final boolean aligned = observed.exists () && observed.pinned () && slot.handle.trackId ().equals (observed.trackId ()) &&
            observed.propertiesAssignmentGeneration () == slot.handle.assignmentGeneration ();
        if (slot.ready && (!aligned || observed.sequence () < slot.readySequence))
        {
            slot.revoked = true;
            return false;
        }
        if (aligned && observed.sequence () > slot.submittedSequence)
        {
            slot.ready = true;
            slot.readySequence = observed.sequence ();
        }
        return slot.ready;
    }


    private Slot find (final Handle handle)
    {
        if (handle == null || handle.projectGeneration () != this.catalog.projectGeneration () || handle.slot () < 0 || handle.slot () >= this.slots.size ())
            return null;
        final Slot slot = this.slots.get (handle.slot ());
        return handle.equals (slot.handle) ? slot : null;
    }


    private void free (final Slot slot)
    {
        // If the host refuses retirement, quarantine the slot instead of reassigning its proxy.
        slot.revoked = true;
        this.host.release (slot.handle);
        slot.clear ();
    }


    private static void requireIdentity (final String value, final String name)
    {
        if (value == null || value.isBlank ())
            throw new IllegalArgumentException (name + " must be nonempty");
    }


    private record Owner (String namespace, String name) { }

    private static final class Slot
    {
        private final int index;
        private final Profile profile;
        private Handle handle;
        private Owner owner;
        private long submittedSequence;
        private long readySequence;
        private int holds;
        private boolean ready;
        private boolean revoked;

        private Slot (final int index, final Profile profile)
        {
            this.index = index;
            this.profile = profile;
        }

        private void clear ()
        {
            this.handle = null;
            this.owner = null;
            this.holds = 0;
            this.ready = false;
            this.revoked = false;
        }
    }
}
