// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.api.*;
import de.mossgrabers.pull.core.api.effect.CoreEffect;
import de.mossgrabers.pull.core.api.event.CoreEvent;
import de.mossgrabers.pull.core.view.*;
import java.util.*;

/** First direct instrument child; drum discovery never advances to a sibling. */
public final class GroupDrumTargetView implements ControllerView
{
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
            return;
        }
        // TODO(group-drum-target): revisit positional preference with explicit/routing-aware ownership.
        this.desired = scan.children ().stream ().filter (GroupDrumScanSnapshot.Child::canHoldNotes)
            .findFirst ().map (child -> new DesiredDrumTarget (scan.selectionGeneration (), scan.selectionId (), scan.revision (), child.channelId ()))
            .orElse (DesiredDrumTarget.inactive ());
    }
}
