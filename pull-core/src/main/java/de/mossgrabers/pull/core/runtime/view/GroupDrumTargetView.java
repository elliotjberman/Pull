// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.api.*;
import de.mossgrabers.pull.core.api.effect.CoreEffect;
import de.mossgrabers.pull.core.api.event.CoreEvent;
import de.mossgrabers.pull.core.view.*;
import java.util.*;

/** First drum-bearing direct child, using one shared scanner in track order. */
public final class GroupDrumTargetView implements ControllerView
{
    private long generation;
    private long revision;
    private String group = "";
    private final Set<String> emptyTracks = new HashSet<> ();
    private DesiredDrumTarget desired = DesiredDrumTarget.inactive ();

    @Override public String id () { return "group-drum-target"; }
    @Override public ViewProfile profile () { return ViewProfile.fixed ("group-target", Set.of (), Set.of ()); }
    @Override public Set<BridgeSubscription> bridgeSubscriptions () { return Set.of (BridgeSubscription.SELECTED_TRACK, BridgeSubscription.NOTE_VIEW); }
    @Override public DesiredDrumTarget desiredDrumTarget () { return this.desired; }
    @Override public void start (ControllerSnapshot snapshot) { this.resolve (snapshot); }
    @Override public List<CoreEffect> handle (CoreEvent event, ControllerSnapshot snapshot) { this.resolve (snapshot); return List.of (); }

    private void resolve (ControllerSnapshot snapshot)
    {
        final var selected = snapshot.bridge ().selectedTrack ();
        final var scan = snapshot.bridge ().noteView ().groupScan ();
        if (!selected.exists () || !selected.group () || selected.generation () != scan.selectionGeneration () || !selected.channelId ().equals (scan.selectionId ()))
        {
            this.desired = DesiredDrumTarget.inactive ();
            this.group = "";
            this.emptyTracks.clear ();
            return;
        }
        if (!this.group.equals (scan.selectionId ()) || this.generation != scan.selectionGeneration () || this.revision != scan.revision ())
        {
            this.emptyTracks.clear ();
            this.group = scan.selectionId ();
            this.generation = scan.selectionGeneration ();
            this.revision = scan.revision ();
        }
        if (scan.complete () && !scan.hasDrums ()) this.emptyTracks.add (scan.scannedTrackId ());
        // TODO(group-drum-target): replace positional preference with explicit/routing-aware ownership.
        this.desired = scan.children ().stream ().filter (child -> child.canHoldNotes () && !this.emptyTracks.contains (child.channelId ()))
            .findFirst ().map (child -> new DesiredDrumTarget (this.generation, this.group, this.revision, child.channelId ()))
            .orElse (DesiredDrumTarget.inactive ());
    }
}
