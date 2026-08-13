---
status: active
created: 2026-08-05
scope: controller-target-model
remove_when: views bind controls to semantic targets and mutations use generation-fenced target leases
---

# Parameter Targets Are Coupled to Proxy Slots

## Observation

Bitwig does not know or depend on the Push layout. Controller extensions submit semantic operations
through Bitwig API proxy objects. The inherited Moss architecture nevertheless often treats three
different concepts as one:

```text
Physical control: Push knob 1
Proxy position:  Current remote-control slot 1
Semantic target: Device A / Cutoff
```

This is convenient while a fixed controller view and a Bitwig cursor remain aligned, but it makes
the physical layout appear to define host identity. It also makes interactions such as snapback,
undo-like gestures, and cross-view composition difficult to retain after navigation.

## Bitwig API Constraint

Bitwig controller API 21 is proxy-oriented. Extensions create bounded `TrackBank`, `CursorTrack`,
`CursorDevice`, and `CursorRemoteControlsPage` objects. Banks scroll and cursors follow selection,
so one proxy slot can address different host objects over time.

The API exposes identities such as `Channel.channelId()` for observation and comparison, but it
does not expose a general `getTrackById()` or global `setParameterById()` actuator. A semantic
identity is therefore not sufficient by itself; mutation also requires a currently valid Bitwig
proxy that addresses that identity.

This bounded topology is a genuine host constraint. Treating a proxy slot as the semantic target
is inherited design debt that Pull can remove.

## Consequences

- Scrolling a bank or changing cursor selection can rebind an existing actuator.
- State retained by physical slot can later refer to the wrong track, device, or parameter.
- A view disappearing can accidentally make an interaction's target unreachable.
- Comparing identity only when preparing an effect is insufficient if the proxy can rebind before
  the effect is applied.
- Supporting arbitrary project-wide retention is impossible without an explicitly bounded proxy
  or actuator canopy created during extension initialization.

## Proposed Model

Formalize these independent concepts:

```java
record ViewBinding (ControlId control, ParameterTargetRef target)
{
}

record ParameterTargetRef (ParameterDomain domain, TargetIdentity identity, long generation)
{
}

record TargetLease (ParameterTargetRef target, BitwigActuator actuator)
{
}
```

The intended flow is:

```text
Push input
  -> view binding
  -> semantic target reference
  -> validated target lease
  -> requested Bitwig mutation
  -> authoritative read-back
  -> rendered feedback
```

A view owns the mapping from a physical control to a semantic target. Removing or replacing that
view should remove the binding, not redefine the target retained by an independent interaction.

## Current Mitigation

Core API 24 separates physical controls from a named bounded parameter canopy. Stable can publish
the inherited active encoder window, project remotes, the selected-device remote page, visible-track
volume and pan, the project-scoped Master/Cue page, and fixed globals. A slot contains an opaque target identity/generation, name, raw
and modulated values, authoritative display text, step count, and tolerance; it never uses a Push
control ID as target identity. Core selects which installed banks are sampled, owns view-specific
control-to-slot mapping, and owns snapback policy.

Master volume/pan and cue volume/mix are fenced to the subscribed project identity. Even when
Bitwig retains the same Java parameter wrapper while switching project tabs, stable invalidates the
old live target and publishes a new generation before accepting another mutation.

`ProjectMacroControlsView` proves the intended mutation direction: it owns the eight relative
encoder routes in core and emits typed relative effects against `PROJECT_REMOTE` targets. Stable's
`WorkspaceMode` no longer mutates or binds those encoders; it remains a display and touch/delete
adapter. The inherited `ACTIVE` bank remains explicit compatibility scaffolding for unmigrated
stable parameter modes, not the model new views should copy.

Stable re-resolves every movable parameter wrapper to a bounded `domain + owner + page + slot/role`
identity during refresh and again before effect application. A cursor remote-control wrapper that
survives a selected-device or remote-page change therefore receives a new opaque target generation.
An unclassified Bitwig `ParameterImpl` is excluded from the lease window instead of being treated as
exact merely because its Java wrapper is unchanged.

Selected-track Mix is additionally fail-closed against the exact selection-following cursor. Its
volume, pan, and send encoders cannot fall back to a matching parameter elsewhere in the visible
track bank; while Bitwig reconciles a project or selection change, an unresolved turn is suppressed
instead of reaching the stale stable binding.

Core may retain a complete target-to-baseline lease set. Stable resolves each request to the exact
current live actuator, rechecks that actuator at apply time, executes absolute restores only through
leases present in the same result, and restores retained targets best-effort if the core faults.
The public retained-baseline snapshot lets a replacement core hydrate in-flight work without
receiving a stable object.

Before a semantic action that may invalidate active parameters can run, pending motion is flushed
and core restoration waits for authoritative read-back. Core-owned views resolve immutable action
payloads at `BEGIN`; frozen legacy compatibility commands publish a separate semantic event derived
from the actual command and current mode path. Snapback compares action invalidation scopes with
retained parameter dependencies, so downstream policy contains no rebinding-button list.
Frozen legacy compatibility dispatch waits behind the same bounded action barrier used by core-owned
actions.
Stable dispatch runs before the released core workspace applies, and a replacement core cannot
activate while either half remains pending or the input router still owns an edge, queued motion, or
deferred callback. An unexpected owner/page/slot or generation change fails closed and never
restores through the replacement proxy.

This implements physical/action separation, named bank selection, and generation-fenced exact
leases, but not the final semantic target model. Core maps controls to bounded slots and then uses
the opaque exact target behind the slot; views do not yet bind directly to durable semantic target
references. The slots still follow rebindable bounded proxies, two slots cannot yet
deduplicate a shared semantic parameter, and there is no bounded pinned actuator pool that keeps an
old target addressable across navigation. The removal criteria therefore remain unsatisfied.

## Target Categories

The design must distinguish:

- Fixed targets, such as tempo and master volume.
- Bank-window targets, such as visible tracks, sends, and scenes.
- Selection-following targets, such as the selected track and selected device.
- Rebindable remote-control slots for project and device parameter pages.
- Pinned or retained targets whose exact actuator is leased temporarily across navigation.

Each bounded bank or lease pool must document its capacity, identity and generation rules,
selection scope, and behavior when capacity is exhausted.

API 24 deliberately omits visible-track sends. They should arrive with the authoritative visible
track bank so send targets and rendered track identities share one generation fence, rather than as
64 parameter-only slots with an independent alignment model.

## Stable Shell And Reloadable Core

The stable shell should own:

- Eagerly created Bitwig proxy topology.
- Exact actuators and bounded target leases.
- Authoritative values and identity generations.
- Prepare-time and apply-time identity validation.

The reloadable core should own:

- Controller view bindings.
- Interaction and persistence policy.
- Parameter eligibility and mutation intent.
- Selection of targets already available in the installed stable capability canopy.

Effects should identify semantic targets and expected generations. They should not rely on the
physical control or visible slot as proof of target identity.

## Investigation Deliverables

Before implementing a general target/lease architecture:

1. Inventory controller-originated mutation paths and classify their target type.
2. Identify where physical indices or proxy positions are currently treated as identities.
3. Build a capability table for fixed, banked, cursor-following, directly addressable, and pinnable
   targets in Bitwig API 21.
4. Define target identity for selected tracks, device instances, parameter pages, and remote-control
   slots using only API-supported observations.
5. Define bounded lease capacities, acquisition, acknowledgement, release, and exhaustion behavior.
6. Define how navigation is serialized when an interaction still retains a target that would be
   rebound.
7. Produce a migration sequence that does not require rewriting every view at once.

## Removal Criteria

Delete this finding when views bind physical controls to semantic target references, controller
mutations execute through generation-fenced target leases, and the supported retention boundaries
are documented in permanent architecture material. Preserve the distinction between Bitwig's
bounded proxy constraint and the removed Moss coupling in that permanent documentation.
