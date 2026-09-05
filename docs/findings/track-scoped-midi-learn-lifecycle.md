---
status: active
created: 2026-09-05
scope: track-scoped-native-controller-mapping
remove_when: bounded per-track native mapping allocation and document lifecycle are proven and their contract is recorded in permanent architecture documentation
---

# Track-Scoped MIDI Learn Lifecycle Needs Host Proof

## Observation

A bounded pool of permanent native Bitwig mapping endpoints could give each track its own four
Drum Controller toggles. The existing four shared endpoints remain unchanged. This investigation
supports further host probes; it does not yet establish a safe complete per-track lifecycle.

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

## Contract Gaps And Open Questions

- API 25 exposes channel identity without a documented lifetime guarantee. The observations above
  are useful evidence, not a promise covering every project operation.
- API 25 does not expose learned-binding enumeration, serialization, copying, or target-owner
  validation. Whether track duplication copies a learned binding, and what that means for an
  inactive source endpoint, still needs a direct native-mapping probe.
- Hidden string storage through `DocumentState` offers no documented atomic-write or undo contract,
  nor document-addressed compare-and-set. Registry behavior under undo/redo and project switching
  needs an experiment before it can protect permanent endpoint ownership.
- `ProjectImpl` currently combines root-channel UUID and project name. Its uniqueness across
  same-name project copies is unproven; it is not yet a durable registry ownership guarantee.
- A native learned action executes in Bitwig's matcher path, outside Pull's effect executor. There
  is no installed apply-time selection check on that path. A design may acknowledge context
  propagation latency, but must not promise a strict instantaneous selection fence.

These are contract gaps and untested behaviors. No duplicate-mapping, undo-registry, or
cross-project ownership failure was observed in this probe.

## Proposed Bounded Model

Investigate an eagerly created endpoint pool with an append-only, document-persisted allocation
from track UUID to one four-endpoint bank. Core owns allocation policy and the active bank lease;
stable owns bounded resources, owner storage, authoritative observations, and matcher handoff.
Deleted tracks retain tombstones so undo can recover their original allocation. A newly duplicated
track receives a fresh bank. Never recycle a tombstoned bank onto another track while its native
learned bindings cannot be inspected or cleared safely. Capacity therefore counts historically
allocated tracks, not merely current tracks; exhaustion must fail closed with a clear diagnostic.

This is a proposal, not implemented persistence. Prove how duplicate bindings interact with the
fresh bank and how registry writes survive undo, reopening, and document changes before adopting it.

## Capability Audit And Next Probes

Existing PAD29–32/PAD input, all modifier/release variants, RGB ownership, raw target feedback,
and matcher lifecycle remain the baseline. No new pad gesture or target-write effect is needed.
Missing capabilities are a bounded per-track endpoint inventory, durable document-scoped owner
read-back/storage, and validated bank activation. Allocation state must survive reload and reopen;
core retains policy while the parent owns the persistence and endpoint mechanisms.

This is Class C / not ready until the lifecycle probes settle those contracts. A subsequent bounded
canopy expansion would require an API/shell install and Bitwig restart. Keep PR #37's tested shared
endpoint behavior while investigating actual mapping duplication; registry undo/redo and delayed
writes across project tabs/copies; capacity exhaustion; and reorder/group movement.

## Removal Criteria

Delete this finding when native mapping duplication and document/registry lifecycle tests pass,
permanent endpoint ownership cannot silently transfer to another track or document within the
supported contract, and capacity, tombstones, context latency, and unsupported cases are documented
in the permanent mapping design. Add deterministic regressions for representable invariants and
retain the native-host probes for behavior fakes cannot establish.
