# Semantic Controller-Mapping Identity

## Status

Implemented in Core API 32 for the four Drum Controller control pads. The installed inventory is
intentionally limited to one semantic Drum Controller endpoint per physical pad. Additional views
must add their own permanent semantic endpoint inventory; they must not reuse physical `ControlId`
values as mapping identities.

This document lives beside the parent-loaded API value types because semantic mapping IDs, desired
mapping leases, and authoritative feedback snapshots cross the stable-shell/core class-loader
boundary.

## Problem

Bitwig does not know about Pull views. It knows about permanent `HardwareButton` objects and their
`HardwareAction` binding sources. Before Core API 32, Pull identified a controller mapping using
the same `ControlId` as its physical source, for example `push.pad.29`.

That is sufficient while exactly one view owns controller mapping for that pad, but it conflates
three independent identities:

```text
physical input             semantic mapping endpoint           physical output
push.pad.29                drum-controller.control.1           push.pad.29 LED
```

A physical pad may have different meanings in different views. If more than one of those meanings
becomes controller-mappable, each meaning needs a distinct permanent Bitwig mapping identity even
though the meanings time-share one physical MIDI source and one LED.

## How the First Slice Is Virtualized

The Drum Controller slice has one semantic owner for each mapped pad:

- Core supplies a complete physical-control-to-semantic-endpoint lease.
- The stable shell enables the detached semantic endpoint's Bitwig press matcher only while that
  lease is active.
- Permanent raw input always carries the normalized core gesture for an active semantic mapping;
  this fences core replacement and completes the exact `END` without creating another learned
  action.
- Outside Drum Controller, raw input invokes the original physical button's ordinary Pull dispatch
  without firing any semantic Bitwig mapping action. This is the same raw-only ingress used by all
  64 grid pads; no physical grid button remains a learned identity.
- Authoritative Bitwig Boolean feedback is keyed by semantic endpoint and rendered by core as
  red/on or black/off on the leased physical LED.

Therefore a Drum Controller mapping must not fire in Session or another view. Bitwig stores the
mapping against `drum-controller.control.N`, not against `push.pad.29..32`; changing the core's
physical projection does not change or recreate that learned identity.

The remaining limitation is bounded installed inventory, not coupled identity. API 32 installs
only the four Drum Controller endpoints because no second view currently needs independent
mappings on those pads. A future endpoint requires one shell install and restart to create its
permanent Bitwig identity. Once installed, switching the physical projection between endpoints is
a replayable core result and hot reloads without relearning either mapping.

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

These values are implemented as immutable, fixed-capacity Core API types. The production
`DesiredControllerMappings` and `ControllerMappingFeedbackSnapshot` are bounded to 64 entries at
the API boundary; the installed shell inventory currently contains exactly four endpoints.

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

## Implemented First Migration

Do not begin with universal per-view virtualization. The smallest proving migration is the four
Drum Controller endpoints:

```text
drum-controller.control.1 <-> push.pad.29
drum-controller.control.2 <-> push.pad.30
drum-controller.control.3 <-> push.pad.31
drum-controller.control.4 <-> push.pad.32
```

The migration performs this sequence:

1. `ControllerMappingId`, `ControllerMappingBinding`, `DesiredControllerMappings`, and
   `ControllerMappingFeedbackSnapshot` cross the parent/child boundary.
2. `CONTROLLER_MAPPING_FEEDBACK` replaces the feature-shaped mapped-pad subscription.
3. Four detached permanent semantic Bitwig buttons are created during extension initialization.
4. All 64 original physical pad actions have no MIDI matcher and remain raw-dispatch-only.
5. `DrumControlPadView` leases semantic endpoints and renders feedback by mapping ID.
6. Existing physical exclusive routes and RGB output controls remain physical.
7. Lane transitions reject new input immediately, retain exact held-gesture ownership through
   routed `END`, and activate only the latest replayable desired projection.

Because a new Bitwig action identity does not inherit bindings stored against the previous physical
button action, users must recreate the four controller mappings once after installing API 32.

## Closed-Loop Proof

The current migration's tests and live smoke must prove:

- learning in Drum Controller attaches to `drum-controller.control.1`, not `push.pad.29`;
- the learned action fires exactly once in Drum Controller and never in Session or Note views;
- the generic projection host can switch one physical pad between two installed semantic endpoints
  without changing either endpoint's identity or churning an unchanged projection;
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
- Installing speculative endpoint identities before a second real view requires them.
