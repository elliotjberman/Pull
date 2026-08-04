// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.controller.ableton.push.workspace;


/**
 * Stable inherited mode/view adapter which can reconcile a replaced fixed-facet set.
 */
public interface WorkspaceFacetAdapter
{
    /**
     * Reconcile mechanics against the host's latest complete desired facets.
     */
    void reconcileWorkspaceFacets ();
}
