---
status: active
created: 2026-08-05
scope: parameter-mutations
remove_when: snapback sessions can safely retain and restore semantic targets across proxy rebinding
---

# Snapback Parameters V1

## Goal

Add Elektron-style momentary parameter changes without scattering modifier checks through modes.
Holding a configurable trigger, initially Shift, opens one session. Every eligible parameter first
changed during that session captures its authoritative Bitwig value. Releasing the trigger restores
all captured parameters, regardless of intervening knob touches or releases.

A single session should support at least:

- Project macros on the current page.
- Tempo.
- Master volume.
- Parameters on the current selected-device remote-control page.

For example, Shift down, changing two project macros, then changing tempo must restore all three
values together when Shift is released.

## Mutation Seam

Controller-originated numeric writes pass through the permanent `PushControllerInputBridge` once.
While core requests the `PARAMETERS` subscription, the first eligible write to an unretained target
publishes a synchronous `ParameterMutationEvent` before the established mutation runs. Core's
`SnapbackSession` commits the baseline lease before control returns to the stable callback.

The session keeps one bounded entry per changed target, not an unbounded history of encoder deltas.
Each entry records:

- The exact target identity and generation.
- The original authoritative value, captured once before the first submitted mutation.

Restoration remains pending until the retained target reports the baseline through authoritative
host read-back. Core does not treat the submitted restore effect as acknowledgement.

Trigger assignment belongs in one policy object. Parameter modes must not add local
`isShiftPressed()` branches. Target-specific sensitivity and acceleration remain separate from the
persistence policy even when Shift affects both.

## Integration Surface

The inherited paths are not currently uniform:

- Generic project, device, and mode parameters generally use `IParameter` bindings.
- Tempo writes through `ITransport` and `TempoCommand`.
- The dedicated master encoder uses `PushMasterVolumeCommand` and can resolve to master volume, cue
  volume, or zoom depending on controller state.

The stable `ParameterTargetHost` adapts these paths into one bounded slot window. It does not know
the Shift policy. Core maps physical Push controls to slots and applies the interaction policy to
the opaque target identity currently published for that slot.

## V1 Context Boundary

V1 does not need to retain a selection-following parameter after its Bitwig proxy is rebound to a
different track, device, or remote-control page.

When navigation would rebind a retained target while the trigger is held:

1. Flush pending parameter motion.
2. Request restoration of the active session.
3. Wait for authoritative host acknowledgement.
4. Apply the pending navigation.
5. Begin a fresh session if the trigger remains physically held.

Never restore through a proxy after its target generation changes. Reject the stale operation
instead of mutating the replacement target.

Shift release is also an ordering barrier. The input router must deliver pending coalesced encoder
motion before delivering the trigger's end event, or the final delta can escape the session and
become persistent.

## Current V1 Implementation

`PushControllerInputBridge` wraps the permanent continuous-control callback once. Every established
stable mutation reaches the runtime's pre-mutation seam. No mode contains a snapback-specific Shift
branch, and no second MIDI or hardware callback is installed.

The installed target set is deliberately narrow:

- Any parameter directly bound to one of the eight top encoders, including project macros and the
  current selected-device remote-control page, is published as an opaque generation-fenced target
  in one active slot.
- Tempo and master volume use fixed stable-shell actuators.
- Cue volume, zoom, the touch strip, and unrelated command-bound encoders remain persistent.

The capture set is bounded to the installed 10 targets and the deferred navigation queue to 64
edges. A full capture set rejects the new temporary mutation because applying an untracked
temporary value would make restoration impossible.

Shift remains `OBSERVE` during migration. On release, the bridge flushes coalesced motion before the
normalized release reaches core. Potentially rebinding button edges reach core immediately but
their stable commands wait behind a replayable route barrier until later host read-back confirms
every retained baseline. Stable dispatch then runs before the newly requested workspace applies.
If Shift remains held, core opens a fresh session after that barrier.

Policy now lives in the reloadable core. Stable owns only the bounded live actuator window, exact
leases, absolute effect execution, generation rechecks, and best-effort invalidation restoration.
An in-flight lease is included immediately in the public snapshot so a same-cycle core reload can
hydrate and finish it. The remaining limitation is the absence of a pinned proxy pool: restoration
still completes before navigation rather than spanning a proxy rebind.

## V1 Decisions

- Restoration unconditionally requests the captured baseline. External automation or another
  controller may therefore be overwritten on release; this is an explicit initial limitation.
- Physical knob touch continues to control automation touch. Holding Shift must not synthesize a
  long automation touch.
- Effect submission is not restoration completion. Do not permit a new session for a restoring
  target until later host read-back acknowledges the baseline.
- Restoration through an `IParameter` uses its immediate setter so Bitwig's configured takeover
  mode cannot reject the return to the retained baseline.
- Restoration requires two consecutive authoritative baseline samples. If a delayed relative
  mutation moves the target after the first sample, restoration is requested again. Waiting is
  bounded to 16 controller ticks; a timeout abandons retained targets and releases deferred
  navigation so one failed acknowledgement cannot capture controller input indefinitely.
- The stable shell should retain exact restoration targets and restore them best-effort if the
  active core generation is invalidated.

## Acceptance Tests

- One parameter restores to its first authoritative baseline.
- Multiple macros and tempo restore together.
- Repeated changes to one target capture only one baseline.
- Knob touch and release do not close the session.
- Pending motion is ordered before trigger release.
- Context-changing navigation restores before proxy rebinding.
- A stale target generation never mutates the newly selected target.
- Rapid trigger release and repress cannot capture an unrestored temporary value as a new baseline.
- Reload or core failure does not silently leave an active retained target temporary.
- Missing restoration read-back times out and releases parameter and navigation input.
- A delayed mutation after the first baseline sample is observed and restored again.

## Removal Criteria

Delete this finding when semantic parameter targets and bounded retained target leases allow an
active snapback session to span supported track, device, and parameter-page changes safely. Move
any durable snapback lifecycle rules into the permanent controller architecture documentation
before deletion.
