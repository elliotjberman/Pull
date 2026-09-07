// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.api.BridgeSubscription;
import de.mossgrabers.pull.core.api.ControllerSnapshot;
import de.mossgrabers.pull.core.api.ControllerViewFacet;
import de.mossgrabers.pull.core.api.DesiredControllerLayout;
import de.mossgrabers.pull.core.api.DesiredNotePerformance;
import de.mossgrabers.pull.core.api.DesiredNoteRepeat;
import de.mossgrabers.pull.core.api.output.ControllerDisplayOverlay;
import de.mossgrabers.pull.core.api.output.ControllerDisplayScene;
import de.mossgrabers.pull.core.api.output.ControllerPadGridOverlay;
import de.mossgrabers.pull.core.view.ControllerView;
import de.mossgrabers.pull.core.view.SurfaceArea;
import de.mossgrabers.pull.core.view.SurfaceClaim;
import de.mossgrabers.pull.core.view.ViewOutput;
import de.mossgrabers.pull.core.view.ViewProfile;

import java.util.Map;
import java.util.Set;


/** Musical route and stable lower-grid facet for the composite Drum controller. */
public final class DrumControllerView implements ControllerView
{
    private static final ViewProfile PROFILE = ViewProfile.fixed ("lower",
        Set.of (new SurfaceClaim (SurfaceArea.DRUM_PLAY_PADS, SurfaceClaim.Kind.MUSICAL_INPUT)),
        Set.of (ControllerViewFacet.DRUM_CONTROLLER_LOWER));

    @Override public String id () { return "drum-controller"; }
    @Override public ViewProfile profile () { return PROFILE; }

    @Override
    public Set<BridgeSubscription> bridgeSubscriptions ()
    {
        return Set.of (BridgeSubscription.CONTROLLER_LAYOUT, BridgeSubscription.SELECTED_TRACK, BridgeSubscription.NOTE_VIEW, BridgeSubscription.DRUM_PADS, BridgeSubscription.CONTROLLER_SETTINGS);
    }

    @Override
    public ViewOutput render (final ControllerSnapshot snapshot)
    {
        final ResolvedNoteViewer resolved = ResolvedNoteViewer.resolveCompositeDrum (snapshot);
        return new ViewOutput (
            Map.of (), Map.of (), ControllerDisplayScene.empty (),
            ControllerPadGridOverlay.inactive (), ControllerDisplayOverlay.inactive (),
            new DesiredNotePerformance (DesiredControllerLayout.empty (), resolved.noteInputRoute (), DrumOctaveView.translation (snapshot)),
            DesiredNoteRepeat.unowned ());
    }
}
