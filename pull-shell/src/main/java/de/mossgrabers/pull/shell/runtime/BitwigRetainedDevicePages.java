// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.shell.runtime;

import com.bitwig.extension.controller.api.BooleanValue;
import com.bitwig.extension.controller.api.CursorDeviceFollowMode;
import com.bitwig.extension.controller.api.CursorRemoteControlsPage;
import com.bitwig.extension.controller.api.CursorTrack;
import com.bitwig.extension.controller.api.PinnableCursorDevice;
import com.bitwig.extension.controller.api.RemoteControl;
import de.mossgrabers.bitwig.framework.daw.data.ParameterImpl;
import de.mossgrabers.framework.controller.valuechanger.IValueChanger;
import de.mossgrabers.framework.parameter.IParameter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

/** Eager native child proxies and equality observers; no device name or position is an identity. */
final class BitwigRetainedDevicePages implements RetainedDevicePageHost.Access
{
    private final PinnableCursorDevice source;
    private final CursorRemoteControlsPage sourcePage;
    private final Map<Integer, Child> children;

    BitwigRetainedDevicePages (final PinnableCursorDevice source, final CursorRemoteControlsPage sourcePage, final Map<Integer, CursorTrack> tracks, final IValueChanger changer)
    {
        this.source = source;
        this.sourcePage = sourcePage;
        source.exists ().markInterested ();
        source.channel ().channelId ().markInterested ();
        this.sourcePage.selectedPageIndex ().markInterested ();
        final Map<Integer, Child> children = new LinkedHashMap<> ();
        tracks.forEach ((slot, track) -> children.put (slot, new Child (slot.intValue (), track, changer)));
        this.children = Map.copyOf (children);
    }

    @Override
    public RetainedDevicePageHost.Source source ()
    {
        // channel() is the device cursor's creation channel, including when the UI device is pinned
        // elsewhere. It provisions the retained parent; only createEqualsValue proves the device target.
        final String track = this.source.channel ().channelId ().get ();
        final int page = this.sourcePage.selectedPageIndex ().get ();
        return new RetainedDevicePageHost.Source (track == null ? "" : track, page,
            track != null && !track.isBlank () && page >= 0 && this.source.exists ().get ());
    }

    @Override
    public void acquireDevice (final int poolSlot)
    {
        final Child child = this.children.get (Integer.valueOf (poolSlot));
        child.device.isPinned ().set (false);
        child.device.selectDevice (this.source);
        child.device.isPinned ().set (true);
    }

    @Override
    public void selectPage (final int poolSlot, final int page)
    {
        this.children.get (Integer.valueOf (poolSlot)).page.selectedPageIndex ().set (page);
    }

    @Override
    public RetainedDevicePageHost.Observation observe (final int poolSlot)
    {
        final Child child = this.children.get (Integer.valueOf (poolSlot));
        boolean aligned = true;
        boolean mapping = false;
        for (int index = 0; index < 8; index++)
        {
            final RemoteControl retained = child.page.getParameter (index);
            final RemoteControl selected = this.sourcePage.getParameter (index);
            mapping |= retained.isBeingMapped ().get ();
            aligned &= !retained.exists ().get () && !selected.exists ().get () || child.equalParameters.get (index).get ();
        }
        return new RetainedDevicePageHost.Observation (child.device.exists ().get (), child.device.isPinned ().get (), child.page.selectedPageIndex ().get (),
            child.ownRevision, child.sourceRevision, child.equalDevice.get (), aligned, mapping);
    }

    @Override
    public boolean propertiesCoherent (final int poolSlot)
    {
        final Child child = this.children.get (Integer.valueOf (poolSlot));
        for (int index = 0; index < 8; index++)
        {
            final RemoteControl retained = child.page.getParameter (index);
            final RemoteControl selected = this.sourcePage.getParameter (index);
            if ((retained.exists ().get () || selected.exists ().get ()) && !RetainedCursorHost.sameParameter (retained, selected))
                return false;
        }
        return true;
    }

    @Override public List<IParameter> parameters (final int poolSlot) { return this.children.get (Integer.valueOf (poolSlot)).parameters; }

    private final class Child
    {
        private final PinnableCursorDevice device;
        private final CursorRemoteControlsPage page;
        private final BooleanValue equalDevice;
        private final List<BooleanValue> equalParameters;
        private final List<IParameter> parameters;
        private long ownRevision;
        private long sourceRevision;

        private Child (final int index, final CursorTrack track, final IValueChanger changer)
        {
            this.device = track.createCursorDevice ("PULL_RETAINED_DEVICE_" + index, "Pull Retained Device " + index, 0, CursorDeviceFollowMode.FOLLOW_SELECTION);
            this.page = this.device.createCursorRemoteControlsPage ("PULL_RETAINED_PAGE_" + index, 8, "");
            this.equalDevice = this.device.createEqualsValue (BitwigRetainedDevicePages.this.source);
            this.equalDevice.markInterested ();
            this.equalDevice.addValueObserver (ignored -> this.sourceRevision++);
            this.device.exists ().markInterested ();
            this.device.isPinned ().markInterested ();
            this.device.exists ().addValueObserver (exists -> { if (!exists) this.ownRevision++; });
            this.device.isPinned ().addValueObserver (pinned -> { if (!pinned) this.ownRevision++; });
            this.page.selectedPageIndex ().markInterested ();
            this.page.selectedPageIndex ().addValueObserver (ignored -> this.ownRevision++);
            this.page.pageNames ().addValueObserver (ignored -> this.ownRevision++);
            final List<BooleanValue> equals = new ArrayList<> (8);
            final List<IParameter> parameters = new ArrayList<> (8);
            for (int slot = 0; slot < 8; slot++)
            {
                final RemoteControl remote = this.page.getParameter (slot);
                final RemoteControl sourceRemote = BitwigRetainedDevicePages.this.sourcePage.getParameter (slot);
                RetainedCursorHost.markParameter (sourceRemote);
                remote.isBeingMapped ().markInterested ();
                remote.isBeingMapped ().addValueObserver (mapped -> { if (mapped) this.ownRevision++; });
                remote.exists ().addValueObserver (ignored -> this.ownRevision++);
                remote.name ().addValueObserver (ignored -> this.ownRevision++);
                final BooleanValue equal = remote.createEqualsValue (sourceRemote);
                equal.markInterested ();
                equal.addValueObserver (ignored -> this.sourceRevision++);
                equals.add (equal);
                parameters.add (new ParameterImpl (changer, remote, slot, true));
            }
            this.equalParameters = List.copyOf (equals);
            this.parameters = List.copyOf (parameters);
        }
    }
}
