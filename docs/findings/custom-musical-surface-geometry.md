---
status: active
created: 2026-08-19
scope: controller-view-authoring
remove_when: a core view can declare and validate a bounded non-built-in physical-pad-to-note map, pressure policy, and selected-target route without stable semantic policy
---

# Custom Musical Surface Geometry Is Not Installed

## Observation

The view compiler can compose fixed pad regions, route controller gestures, render RGB feedback,
and select the installed selected-track `NoteInput` route. It cannot yet let a core-authored view
define an arbitrary musical pad map. Note edges still use initialization-owned Push note
translation for the closed built-in layouts, and the special lower Drum Controller route is
admitted only for its installed geometry.

`DrumPlayPadView` is therefore reusable across the standalone and VS Live 4x4 Drum Controller
composition, but it is not a general drum-controller SDK. A rotated 4x4 layout, 8x2 layout, sparse
pad set, or second semantic layout cannot define its physical-pad-to-note translation core-only.

## Consequences

- Source contributors can rearrange and combine the installed Drum slices without duplicating
  their target fencing, pressure, feedback, repeat, fill, or mapping policy.
- A friend can add a Java `ControllerView` that uses installed areas, snapshots, effects, and
  output lanes, then hot reload policy inside the current canopy.
- A genuinely new playable geometry still needs a parent-loaded API/shell expansion and Bitwig
  restart. Pretending that RGB ownership or an `EXCLUSIVE` controller route also owns Bitwig's
  musical note translation would create a second, unsafe input path.
- There is no dynamic class/config registration contract; authored views are currently source
  changes compiled into `pull-core`.

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
