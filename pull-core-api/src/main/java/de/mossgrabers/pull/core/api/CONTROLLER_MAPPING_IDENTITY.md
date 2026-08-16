# Semantic Controller-Mapping Identity

## Status

Deferred architecture for a future follow-up. The current Drum Controller implementation remains
intentionally limited to one view-scoped controller mapping per physical pad. Do not extend that
model to additional views by reusing physical `ControlId` values as mapping identities.

This document lives beside the future parent-loaded API value types because semantic mapping IDs,
desired mapping leases, and authoritative feedback snapshots must cross the stable-shell/core
class-loader boundary.

## Problem

Bitwig does not know about Pull views. It knows about permanent `HardwareButton` objects and their
`HardwareAction` binding sources. Pull currently identifies a controller mapping using the same
`ControlId` as its physical source, for example `push.pad.29`.

That is sufficient while exactly one view owns controller mapping for that pad, but it conflates
three independent identities:

```text
physical input             semantic mapping endpoint           physical output
push.pad.29                drum-controller.control.1           push.pad.29 LED
```

A physical pad may have different meanings in different views. If more than one of those meanings
becomes controller-mappable, each meaning needs a distinct permanent Bitwig mapping identity even
though the meanings time-share one physical MIDI source and one LED.

## Why the Current Slice Is Not a Functional Bug

The current Drum Controller slice has one semantic owner for each mapped pad:

- Core supplies a complete view-scoped active-mapping lease.
- The stable shell enables the original pad's Bitwig press matcher only while that lease is active.
- Outside Drum Controller, permanent raw input invokes ordinary Pull dispatch without firing the
  Bitwig `HardwareAction` that owns the controller mapping.
- Authoritative Bitwig Boolean feedback is rendered by core as red/on or black/off.

Therefore a Drum Controller mapping must not fire in Session or another view. The limitation is
identity and extensibility: Bitwig still stores the mapping against a physical-pad-shaped identity,
so a second independently mappable meaning cannot coexist on that pad.

## Target Model

Introduce a semantic identity distinct from physical `ControlId`:

```java
public record ControllerMappingId(String value) {}

public record ControllerMappingBinding(
    ControlId physicalControl,
    ControllerMappingId mappingId
) {}

public record DesiredControllerMappings(
    Set<ControllerMappingBinding> bindings
) {}

public record ControllerMappingFeedbackSnapshot(
    boolean available,
    Map<ControllerMappingId, Boolean> states
) {}
```

Names are illustrative; preserve the repository's immutable-value validation and fixed-capacity
conventions in the implementation.

The composed view model should resolve mappings like this:

```text
push.pad.29 -- active Drum Controller --> drum-controller.control.1
push.pad.29 -- another installed view --> another-view.control.1
```

Feedback is keyed by `ControllerMappingId`. Input routing and RGB transmission remain keyed by the
physical `ControlId` because input and LED ownership are physical surfaces.

## Ownership Boundary

Reloadable core owns:

- which semantic mapping endpoint a view declares;
- the complete physical-control-to-mapping-endpoint lease for the active workspace;
- conflict detection when two views claim the same physical input or mapping endpoint;
- subscriptions to authoritative controller-mapping feedback;
- interpretation of Boolean feedback, including red/on and black/off policy;
- all view, modifier, gesture, and mapping meaning.

Stable shell owns only:

- eager creation of the bounded permanent Bitwig `HardwareButton` inventory;
- stable host-facing IDs and labels for semantic mapping endpoints;
- exact physical MIDI matcher installation and translation;
- matcher handoff, held-input fencing, release completion, reload safety, and shutdown cleanup;
- creation and observation of no-output `OnOffHardwareLight` feedback;
- immutable snapshot publication and hardware RGB transmission.

The shell must never select a mapping endpoint from the active view itself. It realizes only the
complete lease returned by core.

## Bitwig API 21 Constraint

API 21 exposes permanent `HardwareButton` objects with `pressedAction()` and `releasedAction()`
binding sources. It does not expose a native view-sensitive mapping context, mapping page, or
virtual-controller bank.

Virtualization must therefore be simulated with a bounded set of permanent semantic
`HardwareButton` identities whose physical matchers are activated one at a time. Arbitrary mapping
endpoints cannot be created by a hot-reloaded core. Adding an endpoint outside the installed
inventory requires a shell install and Bitwig restart.

Renaming one physical `HardwareButton` as views change is not virtualization: its stored Bitwig
bindings remain attached to the same permanent action identity.

## Required Invariants

1. A semantic `ControllerMappingId` has one permanent Bitwig `HardwareAction` identity.
2. A physical control admits at most one semantic mapping press matcher at a time.
3. An endpoint not leased by core cannot learn or fire a new controller mapping.
4. A lane change immediately rejects new presses from the old endpoint.
5. The exact accepted gesture completes through `END` before the latest desired endpoint activates.
6. Raw ordinary dispatch must not fire any Bitwig controller-mapping action.
7. Feedback is authoritative Bitwig read-back, never inferred from a press or submitted action.
8. Missing, unavailable, mismatched, or faulted state fails closed.
9. Stable endpoint IDs never change meaning across releases.
10. The installed endpoint inventory and every matcher/proxy pool remain explicitly bounded.

## Bounded First Migration

Do not begin with universal per-view virtualization. The smallest proving migration is the four
Drum Controller endpoints:

```text
drum-controller.control.1 <-> push.pad.29
drum-controller.control.2 <-> push.pad.30
drum-controller.control.3 <-> push.pad.31
drum-controller.control.4 <-> push.pad.32
```

Suggested sequence:

1. Add `ControllerMappingId`, generic desired bindings, and generic feedback snapshot values.
2. Replace `MappedPadLightsSnapshot` and `MAPPED_PAD_LIGHTS` with the generic bounded API.
3. Create the four permanent semantic Bitwig button identities during extension initialization.
4. Make the original physical pad actions raw-dispatch-only so they are not competing learnable
   identities.
5. Have `DrumControlPadView` lease semantic endpoints and render feedback by mapping ID.
6. Preserve the existing physical exclusive routes and RGB output controls.
7. Remove every physical-ID-as-mapping-ID compatibility path after the migration proves live.

Because a new Bitwig action identity does not inherit bindings stored against the previous one,
this migration may require users to recreate the four controller mappings once. Do not silently
claim mapping preservation without a live project test.

## Closed-Loop Proof

Tests and live smoke must prove:

- learning in Drum Controller attaches to `drum-controller.control.1`, not `push.pad.29`;
- the learned action fires exactly once in Drum Controller and never in Session or Note views;
- a second installed view can retain a different mapping on the same physical pad;
- both release-callback orders and an immediate re-press preserve exact gesture ownership;
- view changes while held activate only the latest desired semantic endpoint after `END`;
- true and false Bitwig feedback address the semantic endpoint and render on the physical LED;
- unmapped/off remains distinct from unavailable or unsupported inventory;
- core reload, rejected candidate, fault, shutdown, and restart never leave multiple matchers live;
- the Bitwig mapping browser exposes no ordinary-dispatch or duplicate physical-pad mapping source.

## Non-Goals

- Arbitrary endpoint creation by reloadable code.
- Treating raw MIDI notes as user-facing mapping identities.
- Moving matcher or Bitwig proxy ownership into core.
- Optimistic feedback derived from the latest press.
- Building a universal virtual controller before a second real view requires independent mappings.
