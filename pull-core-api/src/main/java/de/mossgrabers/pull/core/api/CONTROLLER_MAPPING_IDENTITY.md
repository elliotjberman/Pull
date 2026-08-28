# Semantic Controller-Mapping Identity

## Status

Implemented in Core API 32 for the four Drum Controller control pads and changed to alternating
absolute endpoints in Core API 42. The installed inventory is
intentionally limited to one semantic Drum Controller endpoint per physical pad. Additional views
must add their own permanent semantic endpoint inventory; they must not reuse physical `ControlId`
values as mapping identities.

This document lives beside the parent-loaded API value types because semantic mapping IDs, desired
mapping leases, and authoritative feedback snapshots cross the stable-shell/core class-loader
boundary.

## Problem

Bitwig does not know about Pull views. It knows about permanent hardware-control binding sources.
Before Core API 32, Pull identified a controller mapping using
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
- The stable shell enables the detached absolute endpoint's positive-velocity Note On matcher only
  while that lease is active. The matcher emits the core-selected literal maximum or minimum;
  Note Off is never part of the learned mapping.
- Permanent raw input carries the normalized core gesture when Bitwig also publishes the matched
  MIDI packet to Pull. Toggle phase does not depend on that parallel callback: later authoritative
  mapped-target feedback selects the opposite literal value for the next press.
- Outside Drum Controller, raw input invokes the original physical button's ordinary Pull dispatch
  without firing any semantic Bitwig mapping action. This is the same raw-only ingress used by all
  64 grid pads; no physical grid button remains a learned identity.
- Authoritative Bitwig mapped-target feedback is keyed by semantic endpoint and rendered by core as
  red above the target midpoint or black below it on the leased physical LED.

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
    ControllerMappingId mappingId,
    ControllerMappingValue value
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
The number 64 is a Pull-defined bound, not a Bitwig limit. It is the principled maximum for
simultaneously active physical-pad leases—one per control in the 64-pad grid—but the shared
constant also currently caps the complete installed semantic-endpoint feedback inventory. That
second use is convenient rather than fundamental and may need its own larger bound if future views
install multiple permanent semantic identities for the same physical pad.

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
- the policy that derives the next absolute value from the opposite of authoritative mapped-target
  feedback;
- proof that the declaring view itself owns the physical control's exclusive PAD input and output;
- conflict detection when two views claim the same physical input or mapping endpoint;
- the declaring view's subscription to authoritative controller-mapping feedback;
- interpretation of authoritative mapped-target feedback, including red/on and black/off policy;
- all view, modifier, gesture, and mapping meaning.

Stable shell owns only:

- eager creation of the bounded permanent Bitwig `AbsoluteHardwareControl` inventory;
- stable host-facing IDs and labels for semantic mapping endpoints;
- exact physical MIDI matcher installation and translation;
- literal-value matcher handoff, held-input fencing, reload safety, and shutdown cleanup;
- observation of each absolute control's mapped target value;
- immutable snapshot publication and hardware RGB transmission.

The shell must never select a mapping endpoint from the active view itself. It realizes only the
complete lease returned by core.

## Bitwig API 25 Constraint

API 25 exposes permanent `AbsoluteHardwareControl` objects, custom absolute MIDI value matchers,
and authoritative mapped-target values. It does not expose a native view-sensitive mapping
context, mapping page, or virtual-controller bank.

Virtualization must therefore be simulated with a bounded set of permanent semantic
absolute-control identities whose physical matchers are activated one at a time. Arbitrary mapping
endpoints cannot be created by a hot-reloaded core. Adding an endpoint outside the installed
inventory requires a shell install and Bitwig restart.

Renaming one physical hardware control as views change is not virtualization: its stored Bitwig
bindings remain attached to the same permanent action identity.

## Required Invariants

1. A semantic `ControllerMappingId` has one permanent Bitwig absolute-control identity.
2. A physical control admits at most one semantic absolute matcher at a time.
3. An endpoint not leased by core cannot learn or fire a new controller mapping.
4. A lane change immediately rejects new presses from the old endpoint.
5. Later authoritative target feedback selects the opposite value for the next press; a parallel
   raw lifecycle, when present, still fences matcher replacement until `END`.
6. Raw ordinary dispatch must not fire any Bitwig controller-mapping action.
7. Feedback is authoritative Bitwig read-back, never inferred from a press or submitted action.
8. Missing, unavailable, mismatched, or faulted state fails closed.
9. Stable endpoint IDs never change meaning across releases.
10. The installed endpoint inventory and every matcher/proxy pool remain explicitly bounded.
11. A view may emit a mapping only for a physical PAD input and output it owns itself.
12. A mapping may activate only while authoritative controller-mapping feedback is subscribed.

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
3. Four detached permanent semantic Bitwig absolute controls are created during extension initialization.
4. All 64 original physical pad actions have no MIDI matcher and remain raw-dispatch-only.
5. `DrumControlPadView` leases semantic endpoints and renders feedback by mapping ID.
6. Existing physical exclusive routes and RGB output controls remain physical.
7. Each later mapped-target feedback update selects only that lane's opposite next value; matcher
   replacement waits for any observed raw gesture to become idle.

Because a new Bitwig action identity does not inherit bindings stored against the previous physical
button action, users must recreate the four controller mappings once after installing API 32. Old
`1_PAD29` through `1_PAD32` mappings are intentionally not migrated and are inert after their
physical matchers are removed. Bitwig may continue warning about those persisted entries until the
user deletes them; remove them before learning the four new `Drum Controller Toggle` endpoints.

## Closed-Loop Proof

The current migration's tests and live smoke must prove:

- learning in Drum Controller attaches to `drum-controller.control.1`, not `push.pad.29`;
- the learned action fires exactly once in Drum Controller and never in Session or Note views;
- the generic projection host can switch one physical pad between two installed semantic endpoints
  without changing either endpoint's identity or churning an unchanged projection;
- a learned continuous target observes maximum on the first press and minimum on the second press;
- physical release produces no learned-mapping value;
- a learned Boolean target and continuous target both alternate on successive presses;
- a physical learned action alternates even when Bitwig does not also publish its MIDI packet to
  Pull's raw callback;
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
