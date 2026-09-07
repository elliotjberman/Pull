---
status: active
created: 2026-08-19
scope: controller-view-authoring
remove_when: a core view can declare and validate a bounded non-built-in physical-pad-to-note map, pressure policy, and selected-target route without stable semantic policy
---

# Custom Musical Surface Geometry Is Not Installed

## Observation

The current migration installs complete, replayable 128-entry native key and velocity translation
under the selected-track `NoteInput` lifecycle. Only physical Push pad notes 36–99 may be enabled.
The compiler requires a same-view `MUSICAL_INPUT` claim, rejects overlapping musical footprints,
and prevents enabled notes from borrowing another view's controller-owned pads. Musical ownership
is independent of controller callback routes and RGB output claims. The standalone and composite
Drum views use this capability for their existing lower-left 4x4 geometry.

This removes the fixed native key-table obstacle for core maps within declared physical areas.
It does not yet establish a general musical-surface contract: pressure and feedback still use the
built-in Drum geometry, channels are not per-pad, and no non-built-in geometry has passed the
required integrated live test. A rotated 4x4, 8x2, sparse, or second layout is not yet a demonstrated
end-to-end authoring capability.

## Consequences

- Source contributors can rearrange and combine the installed Drum slices without duplicating
  their target fencing, pressure, feedback, repeat, fill, or mapping policy.
- A friend can add a Java `ControllerView` that uses installed areas, snapshots, effects, and
  output lanes, then hot reload policy inside the current canopy.
- A new geometry must audit its footprint, pressure, feedback, and target route independently;
  gaps outside the installed bounded capability still require a shell change and restart.
  Neither RGB ownership nor `EXCLUSIVE` controller routing grants native musical translation.
- Views are currently registered in core Java code. A configuration loader may construct the same
  validated definitions; it still needs the installed musical input, pressure and feedback capabilities.

## Required Design

Introduce one bounded desired musical-surface value which couples:

- a claimed physical pad footprint;
- an immutable physical-pad-to-note/channel map;
- strike and pressure policy;
- the selected-target-fenced `NoteInput` route;
- generation and idle-transition rules; and
- parent-owned MIDI neutralization on replacement, target change, failure, and shutdown.

The stable shell should install only generic translation/transmission and lifecycle safety. The
reloadable view must own the musical meaning. Capacity, overlap, target disagreement, and unsupported
MIDI state must fail closed.

## Removal Criteria

Delete this finding when an offline integration test and live Push smoke test prove that a
non-built-in geometry can be declared by a core view, can play and render through one target-fenced
route, survives composition/reload correctly, and requires no feature-shaped branch in stable code.
Move the durable musical-surface contract into `ARCH.md` and `docs/views-api-design.md` first.
