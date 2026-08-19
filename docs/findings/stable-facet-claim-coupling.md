---
status: active
created: 2026-08-19
scope: view-compiler-migration-bridge
remove_when: every stable controller facet has an exact bidirectional fixed-claim contract enforced before workspace activation, or the stable facet bridge is deleted
---

# Stable Facets Are Not Yet Bidirectionally Coupled To Claims

## Observation

`CompiledWorkspace` verifies physical overlap and requires a stable-adapter claim to name at least
one `ControllerViewFacet`. It also checks several cross-facet relationships. The inverse is not yet
complete: each facet does not carry one compiler-enforced manifest of the exact
`STABLE_ADAPTER_INPUT` and `STABLE_ADAPTER_OUTPUT` claims its shell adapter activates.

The shell realizes adapter behavior from facet IDs, while the core compiler reasons primarily from
claims. A malformed new profile could therefore declare a facet whose actual stable adapter uses
more surface than the profile admits. The current built-in profiles are reviewed and tested, but
the type system does not prove that correspondence for future authors.

Parameter bindings are now separately checked against the declaring view's relative-input claim;
that closes one concrete ownership gap but not the general facet contract.

## Safe Current Boundary

- Treat all `ControllerViewFacet` values as closed migration scaffolding, not author-facing
  extension points.
- New views may reuse only an existing, reviewed profile/facet combination exactly.
- Do not add a facet or change its stable adapter footprint without adding one canonical contract,
  bidirectional compiler validation, and negative tests in the same change.
- Prefer deleting a facet by migrating its complete semantic slice to core.

## Removal Criteria

Delete this finding when facet-to-claim correspondence is enforced bidirectionally before
activation, including negative tests for omitted and parasitic claims, or when no stable adapter
facets remain. Preserve the rule that parameter/action bindings must be covered by same-view input
claims in the permanent architecture documentation.
