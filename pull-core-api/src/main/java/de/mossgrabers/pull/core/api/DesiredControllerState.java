// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api;

import java.util.Objects;


/**
 * Complete replayable controller state composed from every active view.
 *
 * @param workspace Fixed stable-controller facets contributed by the active views
 * @param notePerformance Selected-track musical route and any full-grid Note layout
 */
public record DesiredControllerState (DesiredControllerWorkspace workspace, DesiredNotePerformance notePerformance)
{
    private static final DesiredControllerState EMPTY = new DesiredControllerState (DesiredControllerWorkspace.empty (), DesiredNotePerformance.inactive ());


    /** Validate that the composed physical layout and musical route agree. */
    public DesiredControllerState
    {
        workspace = Objects.requireNonNull (workspace, "workspace");
        notePerformance = Objects.requireNonNull (notePerformance, "notePerformance");

        final ControllerNoteView noteView = notePerformance.layout ().noteView ();
        final boolean fullGridLayout = noteView.isPresent ();
        final boolean routedPerformance = fullGridLayout && noteView != ControllerNoteView.CLIP_LENGTH;
        final boolean compositeDrumPerformance = workspace.facets ().contains (ControllerViewFacet.DRUM_CONTROLLER_LOWER);
        if (notePerformance.inputRoute ().active () && !routedPerformance && !compositeDrumPerformance)
            throw new IllegalArgumentException ("Selected-track note routing requires a musical controller facet");
        if (routedPerformance && !notePerformance.inputRoute ().active ())
            throw new IllegalArgumentException ("A full-grid musical Note layout requires its selected-track note route");
        if (fullGridLayout && compositeDrumPerformance)
            throw new IllegalArgumentException ("A full-grid controller layout overlaps the lower Drum controller");
        if (fullGridLayout && workspace.sessionBankShape ().isPresent ())
            throw new IllegalArgumentException ("A full-grid Note layout cannot overlap a Session grid workspace");
        if (notePerformance.layout ().neutralizing ())
            throw new IllegalArgumentException ("Layout neutralization is a stable lifecycle state, not core-owned view output");
    }


    /** @return No core-owned workspace, Note layout, or selected-track route. */
    public static DesiredControllerState empty ()
    {
        return EMPTY;
    }
}
