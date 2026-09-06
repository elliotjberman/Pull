// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.api.BridgeSubscription;
import de.mossgrabers.pull.core.api.ControllerSnapshot;
import de.mossgrabers.pull.core.api.ControllerViewFacet;
import de.mossgrabers.pull.core.api.DesiredControllerLayout;
import de.mossgrabers.pull.core.api.DesiredNotePerformance;
import de.mossgrabers.pull.core.api.effect.CoreEffect;
import de.mossgrabers.pull.core.api.event.CoreEvent;
import de.mossgrabers.pull.core.view.ControllerView;
import de.mossgrabers.pull.core.view.SurfaceArea;
import de.mossgrabers.pull.core.view.SurfaceClaim;
import de.mossgrabers.pull.core.view.ViewOutput;
import de.mossgrabers.pull.core.view.ViewProfile;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;


/**
 * Remaining composite Drum Controller lifecycle; the shared touch-strip view owns pitch bend.
 */
public final class DrumControllerView implements ControllerView
{
    private final DrumFillView fillView = new DrumFillView ();
    private final ViewProfile profile;


    /** Construct the remaining fixed Drum lifecycle adapter. */
    public DrumControllerView ()
    {
        final Set<SurfaceClaim> claims = new LinkedHashSet<> (this.fillView.claims ());
        claims.add (new SurfaceClaim (SurfaceArea.DRUM_PLAY_PADS, SurfaceClaim.Kind.MUSICAL_INPUT));
        this.profile = ViewProfile.fixed ("lower", claims, requiredControllerFacets (this.fillView));
    }


    /** {@inheritDoc} */
    @Override
    public String id ()
    {
        return "drum-controller";
    }


    /** {@inheritDoc} */
    @Override
    public ViewProfile profile ()
    {
        return this.profile;
    }


    /** {@inheritDoc} */
    @Override
    public Set<BridgeSubscription> bridgeSubscriptions ()
    {
        final Set<BridgeSubscription> subscriptions = new LinkedHashSet<> (this.fillView.bridgeSubscriptions ());
        subscriptions.addAll (Set.of (BridgeSubscription.CONTROLLER_LAYOUT, BridgeSubscription.SELECTED_TRACK, BridgeSubscription.NOTE_VIEW, BridgeSubscription.DRUM_PADS, BridgeSubscription.CONTROLLER_SETTINGS));
        return Set.copyOf (subscriptions);
    }


    /** {@inheritDoc} */
    @Override
    public void start (final ControllerSnapshot snapshot)
    {
        this.fillView.start (snapshot);
    }


    /** {@inheritDoc} */
    @Override
    public void reconcile (final ControllerSnapshot snapshot)
    {
        this.fillView.reconcile (snapshot);
    }


    /** {@inheritDoc} */
    @Override
    public List<CoreEffect> handle (final CoreEvent event, final ControllerSnapshot snapshot)
    {
        return this.fillView.handle (event, snapshot);
    }


    /** {@inheritDoc} */
    @Override
    public ViewOutput render (final ControllerSnapshot snapshot)
    {
        final ViewOutput fill = this.fillView.render (snapshot);
        final ResolvedNoteViewer resolved = ResolvedNoteViewer.resolveCompositeDrum (snapshot);
        return new ViewOutput (
            fill.lights (),
            fill.clipBindings (),
            fill.display (),
            fill.padGridOverlay (),
            fill.displayOverlay (),
            new DesiredNotePerformance (DesiredControllerLayout.empty (), resolved.noteInputRoute (), DrumOctaveView.translation (snapshot)),
            fill.noteRepeat ());
    }


    private static Set<ControllerViewFacet> requiredControllerFacets (final DrumFillView fillView)
    {
        final Set<ControllerViewFacet> facets = new LinkedHashSet<> (fillView.profile ().controllerFacets ());
        facets.add (ControllerViewFacet.DRUM_CONTROLLER_LOWER);
        return Set.copyOf (facets);
    }
}
