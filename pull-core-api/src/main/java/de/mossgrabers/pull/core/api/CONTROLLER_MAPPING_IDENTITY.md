# Semantic Controller-Mapping Identity

## Status And Scope

Core API 45 retains bounded track-scoped Drum Controller mappings: 128 permanent banks of four
absolute endpoints, projected onto physical PAD29–32 only while the owning core view supplies an
acknowledged, fenced lease. The four former shared endpoints remain constructed but inert. All 64
original physical PAD buttons remain ordinary-dispatch-only; none is a learned mapping identity.

This is the user-accepted bounded V1. General native binding ownership, copying, clearing/recycling,
stronger document identity, and host persistence lifecycle remain explicit TODOs in
[`track-scoped-midi-learn-lifecycle.md`](../../../../../../../../../docs/findings/track-scoped-midi-learn-lifecycle.md).
The API 45 naming addition requires a shell rebuild and Bitwig restart. A core-only policy change
inside this installed inventory can subsequently hot reload.

## Three Independent Identities

Bitwig learns a permanent hardware-control source, not a Pull view or selected-track intention:

```text
physical input       selected track's allocated semantic endpoint     physical output
push.pad.29          drum-controller.track.1.control.1                push.pad.29 LED
```

Core selects the semantic bank from the observed document and selected-track UUID. Moving its
physical projection does not recreate that permanent Bitwig identity. Endpoint names encode bank
and control slot, not a mutable track name or position. Banks are numbered 1–128 and controls 1–4;
`CoreControllerMappings.trackBank()` accepts a zero-based bank index. The corresponding hardware
ID is `CONTROLLER_MAPPING_TRACK_<bank>_CONTROL_VALUE_<slot>`, initially displayed as
`Bank <bank> Drum Controller Toggle <slot>`. Once the track is observed, core supplies
`<track name> — Drum Controller Toggle <slot>` through separate naming metadata. Neither the
name nor the track-list position changes the permanent hardware ID.

Only the leased endpoint receives a positive-velocity Note On matcher. Core supplies the literal
minimum or maximum for that matcher; Note Off and zero-velocity Note On do not write a learned
value. Later target-presence/value read-back determines the opposite next endpoint and red/off
output. The shell applies no midpoint, color, modifier, or allocation policy. An observed raw
gesture still fences matcher handoff through release, but next-value policy does not depend on
Bitwig also forwarding the learned MIDI packet to Pull's ordinary callback.

## Bounded Registry And Acknowledgement

The core-authored registry is an opaque hidden `DocumentState` string to stable code. Its V1 format
is `v1`, the observed document master UUID, then allocated track UUIDs, separated by newlines with
no trailing newline. At most 128 distinct track UUIDs are allowed. The list position permanently
selects the track's bank within that document. A blank observed store starts a new registry for the
observed document; malformed, duplicate-owner, or foreign-document contents fail closed.

Allocation is append-only. A track first needing a mapping bank receives the next unused slot.
Rename and reorder do not intentionally alter allocation because names and positions are not keys.
Deleted owners remain in the registry as tombstones; undo can reuse that owner's slot if its UUID
returns. A newly duplicated track with a new UUID gets a fresh bank. This does not copy or validate
Bitwig's learned bindings. Capacity counts historical allocations, including deleted tracks, and
exhaustion does not permit recycling a slot onto a new owner.

A storage write is a request, not acknowledgement. Core emits an opaque storage effect and leaves
mapping inactive until a later authoritative snapshot returns the expected value. The registry is
then reparsed from that read-back; it is not replaced by an optimistic local copy or a checkpoint.
Corrupt or exhausted registry state renders all four control pads amber with no mapping leases.
Unavailable context, pending storage acknowledgement, and unready target feedback remain inert.

V1 embeds the observed master-channel UUID as its document key. Its stronger lifetime and
uniqueness across copied projects are not established by the API contract. Hidden DocumentState
storage has no documented atomicity/undo or document-addressed compare-and-set guarantee. Stable
validation and later read-back bound V1 operation; they do not establish those stronger promises.

## Parent-Loaded Values And Bounds

- `ControllerMappingId` identifies one permanent endpoint. The installed inventory contains 512
  track endpoints plus four inert legacy shared endpoints.
- `ControllerMappingBinding` contains physical control, semantic endpoint, requested literal value,
  and `ControllerMappingContext`. Complete desired bindings remain bounded to 64 physical controls.
- `ControllerMappingContext` carries selected-target generation and channel UUID, storage revision,
  and document UUID. It fences the selected bank against the authoritative context used to choose it.
- `ControllerMappingTarget` preserves observed target presence and a finite normalized value in
  `[0,1]`. Absent-target presence does not erase the independently observed value.
- `ControllerMappingFeedbackSnapshot` carries available endpoint targets and storage context. Its
  bound is 516 entries, independent of the 64 simultaneous physical-binding limit. Omitted endpoint
  feedback means unsupported/unready inventory, not an observed unmapped target.
- `ControllerMappingStorageSnapshot` carries availability, observed revision, document UUID, and
  opaque value. Its string bound is 8192 characters. Stable observes/stores the value; core parses it.

API 45 advertises controller-mapping output version 5, mapping-feedback version 4, and
`effect.controller-mapping-storage` version 1. Adding more banks, a new parent-loaded contract, or a
new Bitwig observer still requires a shell install and restart.

## Ownership And Fencing

Reloadable core owns registry format and validation, append-only allocation, active bank selection,
read-back acknowledgement policy, and the complete physical-to-semantic lease. It also owns every
modifier/gesture variant, target midpoint (`hasTarget && value >= 0.5`), next endpoint, red/off
feedback, and amber failure indication. No toggle phase needs to survive a core handoff.

Stable owns eager absolute-control creation, permanent IDs/default labels, raw target/storage observation,
opaque storage execution, physical matcher translation, lifecycle fencing, and RGB transmission.
It validates selected-track UUID/generation, document UUID, and observed storage revision when
accepting/applying a lease. It does not parse the registry or choose which bank means a track.

Required invariants:

1. Each physical pad admits at most one semantic absolute matcher at a time.
2. No legacy shared endpoint or unleased bank accepts learned input.
3. Active leases require owned exclusive PAD input, owned RGB output, and authoritative feedback.
4. New or changed ownership cannot activate on storage-write submission alone.
5. Selection/document/storage disagreement invalidates the old lease; held-input cleanup remains
   with the established lifecycle owner rather than being delivered to a replacement core.
6. Unchanged complete output does not churn matchers or resubmit registry writes.
7. Feedback comes from later host read-back, never inferred from a press or emitted request.
8. Fault, missing context, invalid registry, and exhausted capacity do not revive shared mappings.

Native learned actions execute in Bitwig's matcher path, outside Pull's effect executor. Once a
selection change is observed, stable fencing can reject the stale context. It cannot perform an
instantaneous apply-time selection check inside a previously installed native matcher. V1 therefore
acknowledges context propagation latency; it does not claim a strict instantaneous selection fence.

## Mapping Browser Names

`ControllerMappingNames` is complete, document/storage-revision-fenced presentation metadata,
separate from the `ControllerMappingBinding` values that determine native matcher lifetimes.
`DesiredControllerMappings` carries both; changing only names never retires or rebinds a matcher.
The metadata accepts at most 512 named endpoints, each bounded to 256 UTF-16 code units.

A retained controller-level core view subscribes to selected-track and mapping-feedback state in
all workspaces. It caches the last observed name by track UUID within the current acknowledged
registry, then derives bank labels from that registry. Selected-track renames update from later host
observation. Unselected names retain their last observed value and refresh on selection. Name
observation does not allocate a bank or grant input ownership. Names are not saved in the registry:
after core reload/restart, unvisited banks show their generic bank labels until reselected.

The shell receives the whole name map, validates its installed endpoint inventory, and applies
only changed names through API 25 `HardwareControl.setName(String)`. Omitted entries restore the
initialization label using the API's empty-name fallback. The shell rechecks live document and
storage revision before applying and during refresh, clearing stale names without waiting for a
new core result. Empty output, invalid state, core failure, and shutdown release name ownership.
The core owns formatting and retention; the shell owns only metadata application and lifecycle
fencing. The existing 516 endpoint feedback snapshot is cached and sampled only by subscription;
name tracking adds no track bank, observer, scanner, target-write path, or matcher.

### API 45 naming capability audit

- Feature: meaningful native mapping-browser names for allocated track banks.
- Physical inputs and variants: unchanged PAD29–32 press/release/modifier handling; metadata alone
  grants no physical input or light ownership.
- Authoritative state: existing selected-track name/UUID and acknowledged document registry.
- Effects: none; complete replayable naming metadata is a separate output component.
- Output: a bounded names map applied through verified, non-deprecated API 25 `setName(String)`.
- Reloadable state: a per-document UUID/name cache; deliberately not persisted across core reload.
- Existing canopy: selected-name observation, registry read-back, and permanent native controls.
- Missing canopy: one reusable document-fenced name output mechanism. This is Class B: Core API 45
  and mapping-output capability 5 require an extension install and Bitwig restart.
- Out of scope: continuously observing unselected track names; persisting presentation names;
  allocation/reclamation changes; learned-target copying or migration.

## Migration Compatibility

API 32 separated the original `1_PAD29`–`1_PAD32` identities from semantic Drum Controller controls.
API 42 replaced `CONTROLLER_MAPPING_DRUM_CONTROL_1`–`4` (Drum Controller Control 1–4) with absolute
`CONTROLLER_MAPPING_DRUM_CONTROL_VALUE_1`–`4` (Drum Controller Toggle 1–4). API 43 retained those exact
absolute IDs and labels while moving raw target interpretation into core.

API 44 retains construction of those four shared absolute identities for compatibility, but never
activates them. Their old learned mappings are inert. Delete the old shared mappings and relearn
the four controls for each desired track through that track's allocated bank. Native API 25 does
not expose learned-binding enumeration, target-owner validation, or copying, so Pull cannot safely
migrate the shared assignments automatically. New per-track bank IDs remain permanent.

## API 44 Capability Audit

- Feature: independent native Drum Controller mappings for the selected track, with bounded V1
  persistence and explicit general lifecycle TODOs.
- Inputs/variants: existing PAD29–32/PAD, positive press endpoint requests, ignored learned release
  and zero-velocity Note On, and unchanged modifier variants; no additional MIDI callback.
- State: private selected-track UUID/generation, observed document master UUID, opaque storage value
  and revision, and raw endpoint target presence/value with observer readiness.
- Effects: one bounded opaque document-storage write. Native absolute mappings remain the target
  actuators; core does not introduce parameter-target writes for these pads.
- Output: existing exclusive routes/RGB transport plus context-fenced semantic mapping leases.
- Retained state: document-backed registry allocation; no retained toggle phase. Matching waits for
  later registry acknowledgement and is recomputed after reload.
- Existing canopy: physical routes, generic RGB, absolute-control matcher lifecycle, and raw target
  feedback. Expansion: fixed 128×4 endpoint pool, document/storage observation and effect, and lease
  context fences. This is Class B and requires API/shell install and Bitwig restart.
- Out of scope/TODO: arbitrary endpoint creation; learned-target ownership/enumeration; native copy
  semantics; clearing/recycling tombstones; stronger document identity and atomic persistence; and
  an instantaneous native selection fence.

## Verification Contract

Offline regressions must distinguish requests, later host/storage acknowledgement, leased matcher
activation, and rendered output. Cover raw fractional values, every release/modifier variant,
unavailable state, rejected context, corrupt/foreign registry, duplicate owners, full capacity,
tombstones, unchanged replay, reload/fault cleanup, and inert legacy identities.

The exact API 45 shell/core build still requires a physical learning smoke test. Learn separate
bindings on two tracks, verify later off → on → off read-back/output, then exercise rename/reorder,
delete/undo, save/reopen, native duplication, project boundaries, and held-input reload. A debug
PAD_OUTPUT request proves the routed extension/output path but cannot inject a native learned MIDI
action. Follow `TESTING.md`; record actual results separately from these acceptance requirements.
