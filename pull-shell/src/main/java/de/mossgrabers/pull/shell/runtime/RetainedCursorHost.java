// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.shell.runtime;

import com.bitwig.extension.controller.api.ClipLauncherSlot;
import com.bitwig.extension.controller.api.ClipLauncherSlotBank;
import com.bitwig.extension.controller.api.ControllerHost;
import com.bitwig.extension.controller.api.CursorTrack;
import com.bitwig.extension.controller.api.Parameter;
import com.bitwig.extension.controller.api.Track;
import com.bitwig.extension.controller.api.TrackBank;

import de.mossgrabers.bitwig.framework.daw.data.ParameterImpl;
import de.mossgrabers.framework.controller.valuechanger.IValueChanger;
import de.mossgrabers.pull.shell.SelectionDebug;
import de.mossgrabers.pull.shell.runtime.RetainedCursorPool.Catalog;
import de.mossgrabers.pull.shell.runtime.RetainedCursorPool.Coverage;
import de.mossgrabers.pull.shell.runtime.RetainedCursorPool.Handle;
import de.mossgrabers.pull.shell.runtime.RetainedCursorPool.Observation;
import de.mossgrabers.pull.shell.runtime.RetainedCursorPool.Profile;
import de.mossgrabers.pull.shell.runtime.RetainedCursorPool.Request;
import de.mossgrabers.pull.shell.runtime.RetainedCursorPool.Status;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Initialization-owned Bitwig resources for the shared pool. The private flat discovery window
 * never follows a UI cursor. Only tick() advances observation time; reads and submitted effects
 * cannot acknowledge an assignment. Consumer namespaces come from complete core desired state.
 */
final class RetainedCursorHost implements RetainedCursorPool.Host, RetainedTrackParameters
{
    static final int CAPACITY = 64;
    private static final String MIX_NAMESPACE = "parameters";

    private final TrackBank discovery;
    private final List<Track> discoveredTracks;
    private final List<Resource> resources;
    private final Supplier<String> projectIdentity;
    private final RuntimeLog log;
    private final RetainedCursorPool pool;
    private final Map<Handle, TrackMix> mixes = new LinkedHashMap<> ();
    private Map<String, Track> catalogTracks = Map.of ();
    private Catalog catalog;
    private String project = "";
    private long projectGeneration;
    private long sequence;
    private Coverage reportedCoverage;

    RetainedCursorHost (final ControllerHost host, final IValueChanger valueChanger, final Supplier<String> projectIdentity, final RuntimeLog log)
    {
        final long startedAt = System.nanoTime ();
        this.projectIdentity = Objects.requireNonNull (projectIdentity, "projectIdentity");
        this.log = Objects.requireNonNull (log, "log");
        this.discovery = host.createTrackBank (CAPACITY, 0, 0, true);
        this.discovery.itemCount ().markInterested ();
        this.discovery.scrollPosition ().markInterested ();
        this.discovery.scrollPosition ().set (0);
        final List<Track> tracks = new ArrayList<> (CAPACITY);
        for (int index = 0; index < CAPACITY; index++)
        {
            final Track track = this.discovery.getItemAt (index);
            track.exists ().markInterested ();
            track.channelId ().markInterested ();
            markParameter (track.volume ());
            markParameter (track.pan ());
            tracks.add (track);
        }
        this.discoveredTracks = List.copyOf (tracks);

        final List<Profile> profiles = new ArrayList<> (CAPACITY);
        profiles.add (Profile.CLIP_SCAN);
        for (int index = 0; index < 8; index++)
            profiles.add (Profile.CLIP_ACTUATOR);
        profiles.add (Profile.DEVICE_PAGE);
        profiles.add (Profile.DEVICE_PAGE);
        while (profiles.size () < CAPACITY)
            profiles.add (Profile.MIX);
        final List<Resource> slots = new ArrayList<> (CAPACITY);
        for (int index = 0; index < CAPACITY; index++)
        {
            final Profile profile = profiles.get (index);
            final int scenes = profile == Profile.CLIP_SCAN ? 8 : profile == Profile.CLIP_ACTUATOR ? 1 : 0;
            final CursorTrack cursor = host.createCursorTrack ("PULL_RETAINED_" + index, "Pull Retained " + (index + 1), 0, scenes, false);
            cursor.exists ().markInterested ();
            cursor.channelId ().markInterested ();
            cursor.isPinned ().markInterested ();
            final Resource resource = new Resource (profile, cursor, scenes, valueChanger);
            slots.add (resource);
        }
        this.resources = List.copyOf (slots);
        this.catalog = this.pendingCatalog ();
        this.pool = new RetainedCursorPool (profiles, this);
        this.log.info ("Retained cursor pool: " + CAPACITY + " slots provisioned in " + (System.nanoTime () - startedAt) / 1_000_000 + " ms");
    }

    RetainedCursorPool pool ()
    {
        return this.pool;
    }

    /** Native child topology is created once during initialization, never at lookup or effect time. */
    Map<Integer, CursorTrack> deviceTracks ()
    {
        final Map<Integer, CursorTrack> tracks = new LinkedHashMap<> ();
        for (int index = 0; index < this.resources.size (); index++)
            if (this.resources.get (index).profile == Profile.DEVICE_PAGE)
                tracks.put (Integer.valueOf (index), this.resources.get (index).track);
        return Map.copyOf (tracks);
    }

    /** Called once by the controller tick, never by input or effect preparation. */
    void tick ()
    {
        this.sequence = Math.incrementExact (this.sequence);
        this.observeProject ();
        if (this.sequence > 1 && !this.pool.hasDemand ())
        {
            this.pool.refresh ();
            return;
        }
        this.captureCatalog ();
        for (final Resource resource: this.resources)
            this.confirmProperties (resource);
        this.pool.refresh ();
        this.mixes.keySet ().removeIf (handle -> !this.pool.valid (handle));
        if (SelectionDebug.recording ())
            SelectionDebug.record ("CURSOR_POOL", "project=" + this.projectGeneration + " coverage=" + this.catalog.coverage () + " count=" + this.catalog.totalCount () + " allocated=" + this.resources.stream ().filter (resource -> resource.handle != null).count ());
    }

    @Override
    public Catalog catalog ()
    {
        // A project change between preparation and application invalidates immediately, even
        // before the next regular sample can publish its new discovery window.
        this.observeProject ();
        return this.catalog;
    }

    @Override
    public boolean assign (final Handle handle)
    {
        if (handle.projectGeneration () != this.catalog ().projectGeneration ())
            return false;
        final Track target = this.catalogTracks.get (handle.trackId ());
        if (target == null || !target.exists ().get () || !handle.trackId ().equals (target.channelId ().get ()))
            return false;
        final Resource resource = this.resources.get (handle.slot ());
        resource.handle = handle;
        resource.assignedAfter = this.sequence;
        resource.matchingSamples = 0;
        resource.propertiesGeneration = 0;
        // The pool suppresses unchanged requests. Reused cursors still undergo an explicit
        // selection transaction, even when Bitwig happens to report the same UUID already.
        resource.track.isPinned ().set (false);
        resource.track.selectChannel (target);
        resource.track.isPinned ().set (true);
        if (SelectionDebug.recording ())
            SelectionDebug.record ("CURSOR_REQUEST", "slot=" + handle.slot () + " target=" + handle.trackId () + " generation=" + handle.assignmentGeneration ());
        return true;
    }

    @Override
    public Observation observe (final Handle handle)
    {
        final Resource resource = this.resources.get (handle.slot ());
        return new Observation (this.sequence, resource.track.exists ().get (), safe (resource.track.channelId ().get ()), resource.track.isPinned ().get (),
            handle.equals (resource.handle) ? resource.propertiesGeneration : 0);
    }

    @Override
    public void release (final Handle handle)
    {
        final Resource resource = this.resources.get (handle.slot ());
        if (!handle.equals (resource.handle))
            return;
        resource.handle = null;
        resource.propertiesGeneration = 0;
        this.mixes.remove (handle);
        // Keep the now-idle physical cursor pinned. Unpinning would follow unrelated selection;
        // only a later explicit assignment may navigate this resource again.
    }

    ClipLauncherSlotBank clipSlots (final Handle handle)
    {
        this.requireValid (handle);
        final ClipLauncherSlotBank slots = this.resources.get (handle.slot ()).clips;
        if (slots == null)
            throw new IllegalArgumentException ("Cursor profile has no launcher slots");
        return slots;
    }

    @Override
    public void requestTracks (final Set<String> trackIds)
    {
        final List<Request> requests = trackIds.stream ().sorted ().map (id -> new Request (id, id, Profile.MIX)).toList ();
        this.pool.reconcile (MIX_NAMESPACE, requests);
    }

    @Override
    public TrackMix lookup (final String trackId)
    {
        final var result = this.pool.lookup (MIX_NAMESPACE, trackId);
        if (result.status () != Status.READY)
            return null;
        final Handle handle = result.handle ();
        return this.mixes.computeIfAbsent (handle, ignored -> {
            final Resource resource = this.resources.get (handle.slot ());
            return new TrackMix (handle.trackId (), handle.assignmentGeneration (), resource.volume, resource.pan, () -> this.pool.valid (handle));
        });
    }

    private void requireValid (final Handle handle)
    {
        if (!this.pool.valid (handle))
            throw new IllegalStateException ("Retained cursor is pending, retired, or no longer addresses its exact target");
    }

    private void observeProject ()
    {
        final String observed = safe (this.projectIdentity.get ());
        if (observed.equals (this.project))
            return;
        this.project = observed;
        this.projectGeneration = Math.incrementExact (this.projectGeneration);
        this.catalogTracks = Map.of ();
        this.catalog = this.pendingCatalog ();
        this.reportedCoverage = null;
    }

    private Catalog pendingCatalog ()
    {
        return new Catalog (this.projectGeneration, Coverage.PENDING, -1, 0, CAPACITY, List.of ());
    }

    private void captureCatalog ()
    {
        final int offset = this.discovery.scrollPosition ().get ();
        final int total = Math.max (0, this.discovery.itemCount ().get ());
        if (this.project.replace ("\u001F", "").isBlank () || offset != 0)
        {
            this.catalogTracks = Map.of ();
            this.catalog = this.pendingCatalog ();
            if (offset != 0)
                this.discovery.scrollPosition ().set (0);
            return;
        }
        final Map<String, Track> tracks = new LinkedHashMap<> ();
        boolean coherent = true;
        for (int index = 0; index < Math.min (CAPACITY, total); index++)
        {
            final Track track = this.discoveredTracks.get (index);
            final String id = safe (track.channelId ().get ());
            if (!track.exists ().get () || id.isBlank () || tracks.putIfAbsent (id, track) != null)
                coherent = false;
        }
        final Coverage coverage = !coherent ? Coverage.PENDING : total > CAPACITY ? Coverage.PARTIAL : Coverage.FULL;
        this.catalogTracks = Map.copyOf (tracks);
        this.catalog = new Catalog (this.projectGeneration, coverage, total, offset, CAPACITY, List.copyOf (tracks.keySet ()));
        if (coverage != this.reportedCoverage)
        {
            this.reportedCoverage = coverage;
            if (coverage == Coverage.PARTIAL)
                this.log.warn ("Retained cursor catalog covers " + CAPACITY + " of " + total + " tracks; undiscovered targets are unavailable");
        }
    }

    private void confirmProperties (final Resource resource)
    {
        final Handle handle = resource.handle;
        if (handle == null)
            return;
        if (handle.projectGeneration () != this.projectGeneration || this.sequence <= resource.assignedAfter ||
            !resource.track.exists ().get () || !resource.track.isPinned ().get () || !handle.trackId ().equals (resource.track.channelId ().get ()))
        {
            resource.matchingSamples = 0;
            resource.propertiesGeneration = 0;
            return;
        }
        if (resource.propertiesGeneration == handle.assignmentGeneration ())
            return;
        if (resource.profile == Profile.MIX)
        {
            final Track discovered = this.catalogTracks.get (handle.trackId ());
            // A changed UUID alone cannot make old parameter values current. Confirm the complete
            // mix values against the independently observed discovery target before admitting it.
            if (discovered == null || !discovered.exists ().get () || !handle.trackId ().equals (discovered.channelId ().get ()) ||
                !sameParameter (resource.track.volume (), discovered.volume ()) || !sameParameter (resource.track.pan (), discovered.pan ()) ||
                !handle.trackId ().equals (discovered.channelId ().get ()))
            {
                resource.matchingSamples = 0;
                return;
            }
        }
        resource.matchingSamples++;
        if (resource.matchingSamples >= 2)
            resource.propertiesGeneration = handle.assignmentGeneration ();
    }

    static boolean sameParameter (final Parameter left, final Parameter right)
    {
        return left.exists ().get () == right.exists ().get () && Objects.equals (left.name ().get (), right.name ().get ()) &&
            Double.compare (left.value ().get (), right.value ().get ()) == 0 &&
            Double.compare (left.modulatedValue ().get (), right.modulatedValue ().get ()) == 0 &&
            Objects.equals (left.displayedValue ().get (), right.displayedValue ().get ()) &&
            left.discreteValueCount ().get () == right.discreteValueCount ().get ();
    }

    static void markParameter (final Parameter parameter)
    {
        parameter.exists ().markInterested ();
        parameter.name ().markInterested ();
        parameter.value ().markInterested ();
        parameter.modulatedValue ().markInterested ();
        parameter.displayedValue ().markInterested ();
        parameter.discreteValueCount ().markInterested ();
    }

    private static String safe (final String value)
    {
        return value == null ? "" : value;
    }

    private static final class Resource
    {
        private final Profile profile;
        private final CursorTrack track;
        private final ClipLauncherSlotBank clips;
        private final ParameterImpl volume;
        private final ParameterImpl pan;
        private Handle handle;
        private long assignedAfter;
        private int matchingSamples;
        private long propertiesGeneration;

        private Resource (final Profile profile, final CursorTrack track, final int sceneCapacity, final IValueChanger valueChanger)
        {
            this.profile = profile;
            this.track = track;
            this.volume = profile == Profile.MIX ? new ParameterImpl (valueChanger, track.volume ()) : null;
            this.pan = profile == Profile.MIX ? new ParameterImpl (valueChanger, track.pan ()) : null;
            this.clips = sceneCapacity == 0 ? null : track.clipLauncherSlotBank ();
            if (this.clips == null)
                return;
            this.clips.scrollPosition ().markInterested ();
            this.clips.itemCount ().markInterested ();
            for (int index = 0; index < sceneCapacity; index++)
            {
                final ClipLauncherSlot slot = this.clips.getItemAt (index);
                slot.exists ().markInterested ();
                slot.sceneIndex ().markInterested ();
                slot.name ().markInterested ();
                slot.hasContent ().markInterested ();
                slot.isPlaying ().markInterested ();
                slot.isPlaybackQueued ().markInterested ();
                slot.isStopQueued ().markInterested ();
            }
        }
    }
}
