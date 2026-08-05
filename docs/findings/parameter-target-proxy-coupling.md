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

Shift snapback V1 introduces a centralized parameter-mutation seam but does not claim to implement
this model. For a directly bound parameter it retains the physical control ID, that control's
monotonic binding generation, the exact current proxy object, and the first authoritative value.
Every command, pitch-bend, or parameter rebind advances the generation, including rebinding the
same remote-control proxy object to a new page.

Before a known context-changing button can run, pending motion is flushed and restoration waits
for authoritative read-back. Both stable and core-observed halves of that physical edge are then
released in order. An unexpected generation change fails closed and never restores through the
replacement proxy, although the old target may remain temporary because V1 has no retained lease.

This mitigation is bounded and safe for the supported no-navigation session. It still identifies
rebindable parameters by physical proxy slot, cannot deduplicate the same semantic parameter bound
to two controls, and cannot keep the old actuator alive across navigation. The removal criteria
therefore remain unsatisfied.

## Target Categories

The design must distinguish:

- Fixed targets, such as tempo and master volume.
- Bank-window targets, such as visible tracks, sends, and scenes.
- Selection-following targets, such as the selected track and selected device.
- Rebindable remote-control slots for project and device parameter pages.
- Pinned or retained targets whose exact actuator is leased temporarily across navigation.

Each bounded bank or lease pool must document its capacity, identity and generation rules,
selection scope, and behavior when capacity is exhausted.

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
