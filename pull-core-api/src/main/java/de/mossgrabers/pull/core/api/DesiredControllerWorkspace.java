// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api;

import java.util.Objects;
import java.util.Set;


/**
 * Complete replayable selection of fixed controller-view facets.
 *
 * @param name Core-owned workspace name, blank only for no workspace override
 * @param facets Fixed facets to activate
 * @param sessionBankShape Session bank required by the selected facets
 */
public record DesiredControllerWorkspace (String name, Set<ControllerViewFacet> facets, SessionBankShape sessionBankShape)
{
    private static final DesiredControllerWorkspace EMPTY = new DesiredControllerWorkspace ("", Set.of (), SessionBankShape.empty ());


    /**
     * Validate and copy the workspace.
     */
    public DesiredControllerWorkspace
    {
        name = Objects.requireNonNull (name, "name").strip ();
        facets = Set.copyOf (Objects.requireNonNull (facets, "facets"));
        sessionBankShape = Objects.requireNonNull (sessionBankShape, "sessionBankShape");
        if (name.isEmpty () != facets.isEmpty ())
            throw new IllegalArgumentException ("workspace name and facets must either both be empty or both be present");
        final boolean hasSessionGrid = facets.contains (ControllerViewFacet.SESSION_CLIP_GRID_UPPER);
        if (hasSessionGrid != sessionBankShape.isPresent ())
            throw new IllegalArgumentException ("Session grid facets and Session bank shape must either both be present or both be absent");
    }


    /**
     * Create a workspace which does not use a Session grid.
     *
     * @param name Core-owned workspace name
     * @param facets Fixed facets to activate
     */
    public DesiredControllerWorkspace (final String name, final Set<ControllerViewFacet> facets)
    {
        this (name, facets, SessionBankShape.empty ());
    }


    /**
     * Get the empty workspace, which leaves the legacy controller layout active.
     *
     * @return Empty workspace
     */
    public static DesiredControllerWorkspace empty ()
    {
        return EMPTY;
    }


    /**
     * Test whether the core requests a composed workspace.
     *
     * @return True when at least one facet is selected
     */
    public boolean isActive ()
    {
        return !this.facets.isEmpty ();
    }
}
