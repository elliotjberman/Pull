// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.shell.runtime;

import com.bitwig.extension.controller.api.ControllerHost;
import com.bitwig.extension.controller.api.CursorRemoteControlsPage;
import com.bitwig.extension.controller.api.CursorTrack;
import com.bitwig.extension.controller.api.RemoteControl;

import de.mossgrabers.pull.core.api.ParameterTargetId;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;


/**
 * Owns one API-21 selected-track cursor and its filtered Pull remote-control page.
 *
 * <p>Structural observations are generation-fenced. A slot becomes coherent only after two
 * consecutive complete samples agree on track, page, and all slot identities. Value writes are
 * commands only: state changes solely when a later subscribed read reports the new value.</p>
 */
final class SelectedTrackRemoteParameterHost implements SelectedTrackParameterHost
{
    static final String PAGE_NAME = "Pull";
    static final String FILTER_EXPRESSION = "pull";
    static final int SLOT_COUNT = 8;

    private final Adapter adapter;

    private State state = State.empty ();
    private StructuralIdentity observedIdentity;
    private int coherentSampleCount;
    private long generation;


    /**
     * Create the dedicated selected-track cursor and remote-control proxies during extension
     * initialization.
     *
     * @param host Bitwig controller host
     */
    SelectedTrackRemoteParameterHost (final ControllerHost host)
    {
        this (new LiveAdapter (Objects.requireNonNull (host, "host")));
    }


    /**
     * Deterministic model-free test seam.
     *
     * @param adapter Subscribed remote-control adapter
     */
    SelectedTrackRemoteParameterHost (final Adapter adapter)
    {
        this.adapter = Objects.requireNonNull (adapter, "adapter");
    }


    /** {@inheritDoc} */
    @Override
    public boolean refresh ()
    {
        final State previous = this.state;
        this.accept (Objects.requireNonNull (this.adapter.sample (), "remote-control sample"));
        return !previous.equals (this.state);
    }


    /** {@inheritDoc} */
    @Override
    public State state ()
    {
        return this.state;
    }


    /** {@inheritDoc} */
    @Override
    public void setImmediately (final long catalogGeneration, final ParameterTargetId targetId, final double normalizedValue)
    {
        Objects.requireNonNull (targetId, "targetId");
        requireNormalized (normalizedValue);
        if (catalogGeneration != this.generation)
            throw new IllegalArgumentException ("Parameter-catalog generation is stale");

        final int slotIndex = slotIndex (targetId);
        final Slot publishedSlot = this.findPublishedSlot (slotIndex);
        if (!publishedSlot.exists () || !publishedSlot.coherent ())
            throw new IllegalArgumentException ("Parameter target is not coherent and available");

        final StructuralIdentity expectedIdentity = this.observedIdentity;
        final RemoteSample freshSample = Objects.requireNonNull (this.adapter.sample (), "remote-control sample");
        final StructuralIdentity freshIdentity = StructuralIdentity.from (freshSample);
        if (expectedIdentity == null || !expectedIdentity.equals (freshIdentity) || !isCoherent (freshSample))
        {
            this.accept (freshSample);
            throw new IllegalStateException ("Parameter target identity changed before write");
        }

        final SlotSample freshSlot = freshSample.slots ().get (slotIndex);
        if (!freshSlot.exists () || !publishedSlot.pageName ().equals (freshSample.pageName ()) || !publishedSlot.name ().equals (freshSlot.name ()))
        {
            this.accept (freshSample);
            throw new IllegalStateException ("Parameter slot changed before write");
        }

        this.adapter.setImmediately (slotIndex, normalizedValue);
    }


    private void accept (final RemoteSample sample)
    {
        final StructuralIdentity identity = StructuralIdentity.from (sample);
        final boolean coherent = isCoherent (sample);
        if (!identity.equals (this.observedIdentity))
        {
            this.observedIdentity = identity;
            this.generation = Math.incrementExact (this.generation);
            this.coherentSampleCount = coherent ? 1 : 0;
        }
        else if (coherent)
            this.coherentSampleCount = Math.min (2, this.coherentSampleCount + 1);
        else
            this.coherentSampleCount = 0;

        final boolean confirmed = coherent && this.coherentSampleCount >= 2;
        final List<Slot> slots = new ArrayList<> (sample.slots ().size ());
        for (int index = 0; index < sample.slots ().size (); index++)
        {
            final SlotSample slot = sample.slots ().get (index);
            slots.add (new Slot (new ParameterTargetId (index), sample.pageName (), slot.name (), slot.exists (), slot.normalizedValue (), confirmed));
        }
        this.state = new State (this.generation, sample.trackExists () ? sample.trackId () : "", sample.pageName (), slots);

        if (sample.pinned ())
            this.adapter.followSelection ();

        final int pullPage = uniquePullPage (sample.pageNames ());
        if (pullPage >= 0 && sample.selectedPageIndex () != pullPage)
            this.adapter.selectPage (pullPage);
    }


    private Slot findPublishedSlot (final int slotIndex)
    {
        if (slotIndex >= this.state.slots ().size ())
            throw new IllegalArgumentException ("Parameter target is unavailable");
        final Slot slot = this.state.slots ().get (slotIndex);
        if (slot.targetId ().value () != slotIndex)
            throw new IllegalStateException ("Parameter slot identity is inconsistent");
        return slot;
    }


    private static int slotIndex (final ParameterTargetId targetId)
    {
        final long value = targetId.value ();
        if (value < 0 || value >= SLOT_COUNT)
            throw new IllegalArgumentException ("Parameter target is outside the Pull page");
        return (int) value;
    }


    private static boolean isCoherent (final RemoteSample sample)
    {
        if (!sample.trackExists () || sample.trackId ().isEmpty () || sample.pinned () || sample.pageNames ().size () != sample.pageCount () || sample.slots ().size () != SLOT_COUNT)
            return false;

        final int pullPage = uniquePullPage (sample.pageNames ());
        return pullPage >= 0 && pullPage < sample.pageCount () && sample.selectedPageIndex () == pullPage && PAGE_NAME.equals (sample.pageName ());
    }


    private static int uniquePullPage (final List<String> pageNames)
    {
        int result = -1;
        for (int index = 0; index < pageNames.size (); index++)
        {
            if (!PAGE_NAME.equals (pageNames.get (index)))
                continue;
            if (result >= 0)
                return -1;
            result = index;
        }
        return result;
    }


    private static double requireNormalized (final double normalizedValue)
    {
        if (!Double.isFinite (normalizedValue) || normalizedValue < 0 || normalizedValue > 1)
            throw new IllegalArgumentException ("normalizedValue must be finite and in [0, 1]");
        return normalizedValue;
    }


    private static String safe (final String value)
    {
        return value == null ? "" : value;
    }


    /** Model-free adapter for subscribed host state and exact slot writes. */
    interface Adapter
    {
        RemoteSample sample ();


        void followSelection ();


        void selectPage (int pageIndex);


        void setImmediately (int slotIndex, double normalizedValue);
    }


    /** One complete subscribed selected-track/page sample. */
    record RemoteSample (boolean trackExists, String trackId, boolean pinned, int pageCount, List<String> pageNames, int selectedPageIndex, String pageName, List<SlotSample> slots)
    {
        RemoteSample
        {
            trackId = safe (trackId);
            if (pageCount < 0)
                throw new IllegalArgumentException ("pageCount must not be negative");
            pageNames = List.copyOf (Objects.requireNonNull (pageNames, "pageNames"));
            pageName = safe (pageName);
            slots = List.copyOf (Objects.requireNonNull (slots, "slots"));
        }
    }


    /** One subscribed remote-control slot sample. */
    record SlotSample (String name, boolean exists, double normalizedValue)
    {
        SlotSample
        {
            name = safe (name);
            requireNormalized (normalizedValue);
        }
    }


    private record SlotIdentity (int index, String name, boolean exists)
    {
    }


    private record StructuralIdentity (boolean trackExists, String trackId, boolean pinned, int pageCount, List<String> pageNames, int selectedPageIndex, String pageName, List<SlotIdentity> slots)
    {
        private StructuralIdentity
        {
            trackId = Objects.requireNonNull (trackId, "trackId");
            pageNames = List.copyOf (Objects.requireNonNull (pageNames, "pageNames"));
            pageName = Objects.requireNonNull (pageName, "pageName");
            slots = List.copyOf (Objects.requireNonNull (slots, "slots"));
        }


        private static StructuralIdentity from (final RemoteSample sample)
        {
            final List<SlotIdentity> slots = new ArrayList<> (sample.slots ().size ());
            for (int index = 0; index < sample.slots ().size (); index++)
            {
                final SlotSample slot = sample.slots ().get (index);
                slots.add (new SlotIdentity (index, slot.name (), slot.exists ()));
            }
            return new StructuralIdentity (
                sample.trackExists (),
                sample.trackId (),
                sample.pinned (),
                sample.pageCount (),
                sample.pageNames (),
                sample.selectedPageIndex (),
                sample.pageName (),
                slots);
        }
    }


    /** Live API-21 adapter. All proxies are created eagerly in its constructor. */
    private static final class LiveAdapter implements Adapter
    {
        private final CursorTrack selectedTrack;
        private final CursorRemoteControlsPage remoteControls;
        private final List<RemoteControl> slots;


        private LiveAdapter (final ControllerHost host)
        {
            this.selectedTrack = host.createCursorTrack ("PULL_SELECTED_TRACK_PARAMETERS", "Pull Selected Track Parameters", 0, 0, true);
            this.remoteControls = this.selectedTrack.createCursorRemoteControlsPage (PAGE_NAME, SLOT_COUNT, FILTER_EXPRESSION);

            this.selectedTrack.exists ().markInterested ();
            this.selectedTrack.channelId ().markInterested ();
            this.selectedTrack.isPinned ().markInterested ();
            this.remoteControls.pageCount ().markInterested ();
            this.remoteControls.pageNames ().markInterested ();
            this.remoteControls.selectedPageIndex ().markInterested ();
            this.remoteControls.getName ().markInterested ();

            final List<RemoteControl> subscribedSlots = new ArrayList<> (SLOT_COUNT);
            for (int index = 0; index < SLOT_COUNT; index++)
            {
                final RemoteControl slot = this.remoteControls.getParameter (index);
                slot.exists ().markInterested ();
                slot.name ().markInterested ();
                slot.value ().markInterested ();
                subscribedSlots.add (slot);
            }
            this.slots = List.copyOf (subscribedSlots);
        }


        /** {@inheritDoc} */
        @Override
        public RemoteSample sample ()
        {
            final List<SlotSample> samples = new ArrayList<> (SLOT_COUNT);
            for (final RemoteControl slot: this.slots)
                samples.add (new SlotSample (slot.name ().get (), slot.exists ().get (), slot.value ().get ()));

            final String [] pageNames = this.remoteControls.pageNames ().get ();
            return new RemoteSample (
                this.selectedTrack.exists ().get (),
                this.selectedTrack.channelId ().get (),
                this.selectedTrack.isPinned ().get (),
                Math.max (0, this.remoteControls.pageCount ().get ()),
                pageNames == null ? List.of () : Arrays.asList (pageNames),
                this.remoteControls.selectedPageIndex ().get (),
                this.remoteControls.getName ().get (),
                samples);
        }


        /** {@inheritDoc} */
        @Override
        public void followSelection ()
        {
            this.selectedTrack.isPinned ().set (false);
        }


        /** {@inheritDoc} */
        @Override
        public void selectPage (final int pageIndex)
        {
            this.remoteControls.selectedPageIndex ().set (pageIndex);
        }


        /** {@inheritDoc} */
        @Override
        public void setImmediately (final int slotIndex, final double normalizedValue)
        {
            this.slots.get (slotIndex).value ().setImmediately (normalizedValue);
        }
    }
}
