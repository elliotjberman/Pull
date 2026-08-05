// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.view;

import de.mossgrabers.pull.core.api.ControllerViewFacet;

import java.util.Objects;
import java.util.Set;


/**
 * One named optional part of a fixed controller view.
 *
 * @param id Stable facet identifier within its view
 * @param claims Additional fixed surface claims
 * @param controllerFacets Stable mechanical adapters required by this facet
 */
public record ViewFacet (String id, Set<SurfaceClaim> claims, Set<ControllerViewFacet> controllerFacets)
{
    /**
     * Validate and copy a facet.
     */
    public ViewFacet
    {
        id = Objects.requireNonNull (id, "id").strip ();
        if (id.isEmpty ())
            throw new IllegalArgumentException ("facet id must not be blank");
        claims = Set.copyOf (Objects.requireNonNull (claims, "claims"));
        controllerFacets = Set.copyOf (Objects.requireNonNull (controllerFacets, "controllerFacets"));
        if (claims.stream ().anyMatch (claim -> claim.kind ().requiresStableAdapter ()) && controllerFacets.isEmpty ())
            throw new IllegalArgumentException ("stable-adapter facet claims require a controller facet: " + id);
    }
}
