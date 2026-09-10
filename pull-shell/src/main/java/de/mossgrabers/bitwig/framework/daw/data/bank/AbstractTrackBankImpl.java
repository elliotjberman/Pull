// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2017-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.bitwig.framework.daw.data.bank;

import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import com.bitwig.extension.controller.api.CursorTrack;
import com.bitwig.extension.controller.api.Track;
import com.bitwig.extension.controller.api.TrackBank;

import de.mossgrabers.bitwig.framework.daw.ApplicationImpl;
import de.mossgrabers.bitwig.framework.daw.PendingHostOperation;
import de.mossgrabers.bitwig.framework.daw.GroupNavigationHost;
import de.mossgrabers.bitwig.framework.daw.data.CursorTrackImpl;
import de.mossgrabers.bitwig.framework.daw.data.TrackImpl;
import de.mossgrabers.framework.controller.color.ColorEx;
import de.mossgrabers.framework.controller.valuechanger.IValueChanger;
import de.mossgrabers.framework.daw.DAWColor;
import de.mossgrabers.framework.daw.IApplication;
import de.mossgrabers.framework.daw.IHost;
import de.mossgrabers.framework.daw.data.AbstractItemImpl;
import de.mossgrabers.framework.daw.data.IDeviceMetadata;
import de.mossgrabers.framework.daw.data.ITrack;
import de.mossgrabers.framework.daw.data.bank.ISlotBank;
import de.mossgrabers.framework.daw.data.bank.ITrackBank;
import de.mossgrabers.framework.daw.resource.ChannelType;
import de.mossgrabers.framework.observer.IIndexedValueObserver;


/**
 * An abstract track bank.
 *
 * @author Jürgen Moßgraber
 */
public abstract class AbstractTrackBankImpl extends AbstractChannelBankImpl<TrackBank, ITrack> implements ITrackBank
{
    protected IApplication          application;
    protected final CursorTrackImpl cursorTrack;
    protected final Track           rootGroup;
    private int                     currentPage = -1;
    private final PendingHostOperation pendingSelection;
    private Track parentTrack;
    private Supplier<String> projectIdentity;
    private boolean flatTrackList;
    private PageSelection pageSelection;
    private int submittedPageOffset;
    private boolean submittingPageScroll;


    /**
     * Constructor.
     *
     * @param host The DAW host
     * @param valueChanger The value changer
     * @param bank The bank to encapsulate
     * @param cursorTrack The cursor track assigned to this track bank
     * @param rootGroup The root track
     * @param application The application
     * @param numTracks The number of tracks of a bank page
     * @param numScenes The number of scenes of a bank page
     * @param numSends The number of sends of a bank page
     */
    protected AbstractTrackBankImpl (final IHost host, final IValueChanger valueChanger, final TrackBank bank, final CursorTrackImpl cursorTrack, final Track rootGroup, final ApplicationImpl application, final int numTracks, final int numScenes, final int numSends)
    {
        super (host, valueChanger, bank, numTracks, numScenes, numSends);

        this.application = application;
        this.cursorTrack = cursorTrack;
        this.rootGroup = rootGroup;
        this.pendingSelection = new PendingHostOperation (host);

        if (this.bank.isEmpty ())
            return;

        final TrackBank trackBank = this.bank.get ();
        if (numTracks > 0)
        {
            this.parentTrack = trackBank.getItemAt (0).createParentTrack (0, 0);
            this.parentTrack.exists ().markInterested ();
            this.parentTrack.channelId ().markInterested ();
        }

        this.sceneBank = new SceneBankImpl (host, valueChanger, this.numScenes == 0 ? null : trackBank.sceneBank (), this.numScenes, cursorTrack);

        for (int i = 0; i < this.getPageSize (); i++)
            this.items.add (new TrackImpl (host, valueChanger, application, (CursorTrack) cursorTrack.getTrack (), numScenes > 0 ? bank.sceneBank () : null, rootGroup, trackBank.getItemAt (i), i, this.numSends, this.numScenes));

        trackBank.cursorIndex ().addValueObserver (this::handleTrackSelection);
        trackBank.scrollPosition ().addValueObserver (this::handlePageSelection);
    }


    /** {@inheritDoc} */
    @Override
    protected void beforeWindowMutation ()
    {
        if (!this.submittingPageScroll)
        {
            this.pendingSelection.cancel ();
            this.pageSelection = null;
        }
        this.host.beforeProjectStructureMutation ();
    }


    /** {@inheritDoc} */
    @Override
    public void enableObservers (final boolean enable)
    {
        if (!enable)
        {
            this.pendingSelection.cancel ();
            this.pageSelection = null;
        }
        super.enableObservers (enable);

        if (this.sceneBank != null)
            this.sceneBank.enableObservers (enable);
    }


    /** Install the model project fence and bank topology during initialization. */
    public void configurePendingOperations (final Supplier<String> identity, final boolean flat, final GroupNavigationHost groupEntry)
    {
        this.projectIdentity = identity;
        this.flatTrackList = flat;
        for (final ITrack item: this.items)
            ((TrackImpl) item).configurePendingOperations (groupEntry);
    }


    /** Cancel deferred bank selection at model shutdown. */
    public void closePendingOperations ()
    {
        this.pendingSelection.close ();
        this.pageSelection = null;
    }


    @Override
    public void selectNextItem ()
    {
        this.pendingSelection.cancel ();
        this.pageSelection = null;
        super.selectNextItem ();
    }


    @Override
    public void selectPreviousItem ()
    {
        this.pendingSelection.cancel ();
        this.pageSelection = null;
        super.selectPreviousItem ();
    }


    @Override
    public void selectNextPage ()
    {
        if (this.flatTrackList || this.pageSelection == null || !this.selectionValid (this.pageSelection))
            super.selectNextPage ();
        else if (this.pageSelection.offset < this.pageSelection.count - 1)
            this.selectAfterScroll (this.getScrollPosition () + this.getPageSize (), 0, false, this::scrollPageForwards);
    }


    @Override
    public void selectPreviousPage ()
    {
        if (this.flatTrackList || this.pageSelection == null || !this.selectionValid (this.pageSelection))
            super.selectPreviousPage ();
        else if (this.pageSelection.offset > 0)
            // Do not clamp this relative request against the stale observed offset.
            this.selectAfterScroll (this.getScrollPosition () - this.getPageSize (), 0, false, this::scrollPageBackwards);
    }


    @Override
    protected void selectAfterScroll (final int offset, final int index, final boolean notifyPage, final Runnable scroll)
    {
        // Track.position is local to its immediate parent. A flattened/filtered list has no
        // proven offset-to-position mapping across groups; preserve its existing continuation.
        if (this.flatTrackList)
        {
            super.selectAfterScroll (offset, index, notifyPage, scroll);
            return;
        }
        if (this.projectIdentity == null || this.items.isEmpty () || !this.getItem (0).doesExist ())
            return;
        final String project = this.projectIdentity.get ();
        final String owner = this.navigationOwner ();
        final String cursor = this.cursorTrack.getChannelID ();
        final int count = this.getItemCount ();
        final int oldOffset = this.getScrollPosition ();
        if (this.pageSelection != null && !this.selectionValid (this.pageSelection))
            this.pendingSelection.cancel ();
        final PageSelection previous = this.pageSelection;
        final int positionBase = previous != null ? previous.positionBase : this.getItem (0).getPosition () - oldOffset;
        final int expectedOffset = Math.max (0, Math.min (count - 1,
            !notifyPage && previous != null ? previous.offset + offset - oldOffset : offset));
        if (owner.isBlank () || project.isBlank () || count <= 0 || index < 0 || index >= this.items.size ())
            return;
        this.pageSelection = new PageSelection (project, owner, cursor, count, positionBase, expectedOffset, index, notifyPage);
        if (previous != null)
            return;
        this.submitPageScroll (expectedOffset);
        // Replacements update only the latest intent; the first request owns the entire deadline.
        this.pendingSelection.await (
            () -> this.pageSelection != null && this.selectionValid (this.pageSelection),
            this::advancePageSelection,
            () -> {
                final PageSelection completed = this.pageSelection;
                this.pageSelection = null;
                this.getItem (completed.index).select ();
                if (completed.notifyPage)
                    this.firePageObserver ();
            }, () -> this.pageSelection = null);
    }


    private void submitPageScroll (final int offset)
    {
        this.submittedPageOffset = offset;
        this.submittingPageScroll = true;
        try
        {
            this.scrollTo (offset);
        }
        finally
        {
            this.submittingPageScroll = false;
        }
    }


    private boolean advancePageSelection ()
    {
        final PageSelection latest = this.pageSelection;
        if (this.getScrollPosition () != this.submittedPageOffset || !this.windowAligned (latest.positionBase + this.submittedPageOffset, 0))
            return false;
        if (this.submittedPageOffset != latest.offset)
        {
            this.submitPageScroll (latest.offset);
            return false;
        }
        return this.getItem (latest.index).doesExist ();
    }

    private boolean selectionValid (final PageSelection request)
    {
        return request.project.equals (this.projectIdentity.get ()) && request.owner.equals (this.navigationOwner ()) &&
            request.count == this.getItemCount () && request.cursor.equals (this.cursorTrack.getChannelID ());
    }


    private record PageSelection (String project, String owner, String cursor, int count, int positionBase, int offset, int index, boolean notifyPage) { }


    private String navigationOwner ()
    {
        return this.parentTrack == null || !this.parentTrack.exists ().get () ? "" : this.parentTrack.channelId ().get ();
    }


    private boolean windowAligned (final int firstPosition, final int selectedIndex)
    {
        for (int index = 0; index < this.items.size (); index++)
        {
            final ITrack track = this.getItem (index);
            if (track.doesExist () && (track.getChannelID ().isBlank () || track.getPosition () != firstPosition + index))
                return false;
        }
        return this.getItem (selectedIndex).doesExist ();
    }


    /** {@inheritDoc} */
    @Override
    public void stop (final boolean isAlternative)
    {
        if (this.sceneBank != null)
            this.sceneBank.stop (isAlternative);
    }


    /** {@inheritDoc} */
    @Override
    public void setIndication (final boolean enable)
    {
        if (this.bank.isEmpty ())
            return;

        final TrackBank trackBank = this.bank.get ();
        trackBank.setShouldShowClipLauncherFeedback (enable);
    }


    /** {@inheritDoc} */
    @Override
    public void addNameObserver (final IIndexedValueObserver<String> observer)
    {
        for (int index = 0; index < this.getPageSize (); index++)
        {
            final int i = index;
            this.getItem (index).addNameObserver (name -> observer.update (i, name));
        }
    }


    /** {@inheritDoc} */
    @Override
    public boolean isClipRecording ()
    {
        for (int t = 0; t < this.getPageSize (); t++)
        {
            final ISlotBank slotBank = this.items.get (t).getSlotBank ();
            for (int s = 0; s < this.numScenes; s++)
            {
                if (slotBank.getItem (s).isRecording ())
                    return true;
            }
        }
        return false;
    }


    /**
     * Handles bank selection changes. Notifies all registered observers.
     *
     * @param index The index of the newly de-/selected item
     */
    private void handleTrackSelection (final int index)
    {
        for (int i = 0; i < this.getPageSize (); i++)
        {
            final boolean isSelected = index == i;
            final ITrack item = this.getItem (i);
            if (item instanceof final AbstractItemImpl itemImpl && itemImpl.getRawSelectionState () != isSelected)
            {
                item.setSelected (isSelected);
                this.notifySelectionObservers (i, isSelected);
            }
        }
    }


    private void handlePageSelection (final int position)
    {
        final int page = position / this.pageSize;
        if (page != this.currentPage)
        {
            this.currentPage = page;
            this.firePageObserver ();
        }
    }


    /** {@inheritDoc} */
    @Override
    public void addChannel (final ChannelType type)
    {
        this.addChannel (type, null);
    }


    /** {@inheritDoc} */
    @Override
    public void addChannel (final ChannelType type, final String name)
    {
        this.addChannel (type, name, DAWColor.getNextColor ().getColor ());
    }


    /** {@inheritDoc} */
    @Override
    public void addChannel (final ChannelType type, final String name, final ColorEx color)
    {
        this.addChannel (type, name, color, Collections.emptyList ());
    }


    /** {@inheritDoc} */
    @Override
    public void addChannel (final ChannelType type, final String name, final List<IDeviceMetadata> devices)
    {
        this.addChannel (type, name, DAWColor.getNextColor ().getColor (), devices);
    }


    private void addChannel (final ChannelType type, final String name, final ColorEx color, final List<IDeviceMetadata> devices)
    {
        this.addTrack (type);

        if (name == null && color == null)
            return;

        this.host.scheduleTask ( () -> {

            if (!this.cursorTrack.doesExist ())
                return;
            if (name != null)
                this.cursorTrack.setName (name);
            if (color != null)
                this.cursorTrack.setColor (color);

            this.beforeWindowMutation ();
            this.bank.get ().scrollIntoView (this.cursorTrack.getPosition ());

            for (final IDeviceMetadata device: devices)
                this.cursorTrack.addDevice (device);

        }, 300);
    }


    /**
     * Adds a new track to this track bank.
     *
     * @param type The type of the track to add
     */
    protected void addTrack (final ChannelType type)
    {
        switch (type)
        {
            case HYBRID, INSTRUMENT:
                this.application.addInstrumentTrack ();
                break;

            case EFFECT:
                this.application.addEffectTrack ();
                break;

            default:
            case AUDIO:
                this.application.addAudioTrack ();
                break;
        }
    }


    /** {@inheritDoc} */
    @Override
    public boolean canEditSend (final int sendIndex)
    {
        return this.getItem (0).getSendBank ().getItem (sendIndex).doesExist ();
    }


    /** {@inheritDoc} */
    @Override
    public String getEditSendName (final int sendIndex)
    {
        return this.getItem (0).getSendBank ().getItem (sendIndex).getName ();
    }


    /** {@inheritDoc} */
    @Override
    public void toggleRecArm ()
    {
        if (this.items.isEmpty ())
            return;

        final boolean state = !this.items.get (0).isRecArm ();
        for (final ITrack track: this.items)
            track.setRecArm (state);
    }
}