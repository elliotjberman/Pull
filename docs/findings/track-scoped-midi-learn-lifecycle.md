---
status: active
created: 2026-09-05
scope: track-scoped-native-controller-mapping
remove_when: bounded per-track native mapping allocation and document lifecycle are proven and their contract is recorded in permanent architecture documentation
---

# Track-Scoped MIDI Learn V1 Lifecycle TODOs

## Observation

The user explicitly accepted a bounded per-track V1 with the general lifecycle work left as TODOs.
API 44 implements that scope with 128 banks of four permanent native endpoints; the original four
shared endpoints remain constructed but inert. This acceptance does not establish the unverified
host contracts below or remove the need for an exact-build physical learning smoke test.

On 2026-09-05, the scratch identity probe held live lease `pr37-track-identity-probe` against PR #37
commit `748c0df241791d93a0500df81007fa2f22bcd723`, with recorded active core build
`20260904T214803Z-089985e92a1271f46260e6385804bc61`. Later selected-track read-back showed:

| Operation | Observed channel UUID |
| --- | --- |
| Create instrument track Inst 1 | `8153103e-aa55-41aa-a7b9-7694b6429635` |
| Duplicate to Inst 2 | `5e10dc9a-b8c9-4771-bd30-c0a536b6ba2b` |
| Rename duplicate to PR37 Identity B | Same duplicate UUID |
| Delete duplicate | Selection moved to audio track `051c8e60-b918-4aff-b85f-38e60219ce78` |
| Undo deletion | Duplicate UUID returned |
| Save, close, and reopen | Duplicate UUID persisted |

The scratch project was then closed, the original project restored, and the lease released. These
observations cover that scratch project only. Reorder/group movement, duplication of actual native
learned mappings, and a persistent allocation registry were not verified.

API 45 adds core-owned mapping-browser names using the existing selected-track name observation.
Names are separate from matcher identity, retained only within the active core/document, and reset
to generic bank labels when unavailable. Inactive tracks refresh on selection; this adds no global
track scanner and does not resolve the lifecycle questions below. The user confirmed the API 44
physical two-track mapping smoke; final API 45 rename/browser persistence testing is separate.

## Contract Gaps And Open Questions

- API 25 exposes channel identity without a documented lifetime guarantee. The observations above
  are useful evidence, not a promise covering every project operation.
- API 25 does not expose learned-binding enumeration, serialization, copying, or target-owner
  validation. Whether track duplication copies a learned binding, and what that means for an
  inactive source endpoint, still needs a direct native-mapping probe.
- Hidden string storage through `DocumentState` offers no documented atomic-write or undo contract,
  nor document-addressed compare-and-set. Registry behavior under undo/redo and project switching
  still needs host experiments. V1 gates matching on later storage read-back rather than treating
  submission as persistence.
- `ProjectImpl` combines root-channel UUID and project name. V1 instead embeds the observed
  document master UUID in its registry, but uniqueness across copies remains unproven. A stronger
  document ownership contract is still a TODO.
- A native learned action executes in Bitwig's matcher path, outside Pull's effect executor. There
  is no installed apply-time selection check on that path. A design may acknowledge context
  propagation latency, but must not promise a strict instantaneous selection fence.

These are contract gaps and untested behaviors. No duplicate-mapping, undo-registry, or
cross-project ownership failure was observed in this probe.

## Accepted Bounded V1

Use an eagerly created endpoint pool with an append-only, document-persisted allocation
from track UUID to one four-endpoint bank, up to 128 historical allocations per document. Core owns
allocation policy and the active bank lease; stable owns bounded resources, owner storage,
authoritative observations, and matcher handoff.
Deleted tracks retain tombstones so undo can recover their original allocation. A newly duplicated
track receives a fresh bank. Never recycle a tombstoned bank onto another track while its native
learned bindings cannot be inspected or cleared safely. Capacity therefore counts historically
allocated tracks, not merely current tracks; exhaustion must fail closed with a clear diagnostic.

Core parses the raw hidden DocumentState payload containing document master UUID and track UUIDs.
Matching waits for observed storage acknowledgement; leases fence the document, selected-track
UUID/generation, and storage revision. Corrupt storage or exhausted capacity leaves the four pads
amber and inert. This mechanism does not enumerate or validate Bitwig's native learned targets.
TODO: prove duplicate native-binding behavior, registry undo/project boundaries, safe clearing and
recycling, and stronger document identity before claiming a general lifecycle solution.

## Capability Audit And Next Probes

Existing PAD29–32/PAD input, all modifier/release variants, RGB ownership, raw target feedback,
and matcher lifecycle remain the baseline. No new pad gesture or target-write effect is needed.
The V1 expansion adds a bounded per-track endpoint inventory, raw document owner/storage
read-back and writes, and fenced bank activation. Allocation state is document-persisted; core
retains policy while the parent owns the persistence and endpoint mechanisms.

This accepted V1 is a Class B bounded canopy expansion requiring API/shell installation and a
Bitwig restart. It supersedes the earlier recommendation to defer all per-track behavior. Full
native lifecycle support remains pending the probes above. Offline coverage and live verification
for the final API 44 build must be reported separately; the earlier identity probe is not proof of
the new registry or native mappings. See `../../TESTING.md` for the required smoke sequence.

## Removal Criteria

Delete this finding when native mapping duplication and document/registry lifecycle tests pass,
permanent endpoint ownership cannot silently transfer to another track or document within the
supported contract, and capacity, tombstones, context latency, and unsupported cases are documented
in the permanent mapping design. Add deterministic regressions for representable invariants and
retain the native-host probes for behavior fakes cannot establish.
