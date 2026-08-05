// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.view;

import de.mossgrabers.pull.core.api.ControllerViewFacet;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;


/**
 * One selected fixed profile of a controller view.
 *
 * @param id Stable profile identifier within its view
 * @param requiredClaims Claims which are always active
 * @param requiredControllerFacets Stable mechanical adapters which are always active
 * @param optionalFacets Named optional fixed-footprint facets
 * @param enabledFacets Selected optional facet IDs
 */
public record ViewProfile (String id, Set<SurfaceClaim> requiredClaims, Set<ControllerViewFacet> requiredControllerFacets, Map<String, ViewFacet> optionalFacets, Set<String> enabledFacets)
{
    /**
     * Validate and copy a profile.
     */
    public ViewProfile
    {
        id = Objects.requireNonNull (id, "id").strip ();
        if (id.isEmpty ())
            throw new IllegalArgumentException ("profile id must not be blank");
        requiredClaims = Set.copyOf (Objects.requireNonNull (requiredClaims, "requiredClaims"));
        requiredControllerFacets = Set.copyOf (Objects.requireNonNull (requiredControllerFacets, "requiredControllerFacets"));
        optionalFacets = Map.copyOf (Objects.requireNonNull (optionalFacets, "optionalFacets"));
        enabledFacets = Set.copyOf (Objects.requireNonNull (enabledFacets, "enabledFacets"));

        if (requiredClaims.stream ().anyMatch (claim -> claim.kind ().requiresStableAdapter ()) && requiredControllerFacets.isEmpty ())
            throw new IllegalArgumentException ("stable-adapter profile claims require a controller facet: " + id);

        optionalFacets.forEach ( (facetId, facet) -> {
            if (!Objects.requireNonNull (facetId, "facet id").equals (Objects.requireNonNull (facet, "facet").id ()))
                throw new IllegalArgumentException ("optional facet key must match its id: " + facetId);
        });
        for (final String facetId: enabledFacets)
        {
            if (!optionalFacets.containsKey (facetId))
                throw new IllegalArgumentException ("unknown optional facet: " + facetId);
        }
    }


    /**
     * Create a profile without optional facets.
     *
     * @param id Profile identifier
     * @param claims Required claims
     * @param controllerFacets Required stable adapters
     * @return Fixed profile
     */
    public static ViewProfile fixed (final String id, final Set<SurfaceClaim> claims, final Set<ControllerViewFacet> controllerFacets)
    {
        return new ViewProfile (id, claims, controllerFacets, Map.of (), Set.of ());
    }


    /**
     * Get all effective claims.
     *
     * @return Required and enabled-facet claims
     */
    public Set<SurfaceClaim> claims ()
    {
        final Set<SurfaceClaim> claims = new LinkedHashSet<> (this.requiredClaims);
        this.enabledFacets.forEach (facetId -> claims.addAll (this.optionalFacets.get (facetId).claims ()));
        return Set.copyOf (claims);
    }


    /**
     * Get all stable mechanical adapters required by this profile.
     *
     * @return Required and enabled-facet adapters
     */
    public Set<ControllerViewFacet> controllerFacets ()
    {
        final Set<ControllerViewFacet> facets = new LinkedHashSet<> (this.requiredControllerFacets);
        this.enabledFacets.forEach (facetId -> facets.addAll (this.optionalFacets.get (facetId).controllerFacets ()));
        return Set.copyOf (facets);
    }
}
