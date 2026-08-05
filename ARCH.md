# Pull View Architecture

Status: current through Core API 12 and the first `VS Live` composite workspace.

Read this file before changing controller views, modes, workspaces, input routing, or Session bank
topology. The detailed design contract is in
[`docs/views-api-design.md`](docs/views-api-design.md). The shell/core boundary is specified in
[`docs/reloadable-controller-core-design.md`](docs/reloadable-controller-core-design.md).

## Intent

A Pull view is behavior with a fixed, inspectable Push 2 footprint. A workspace composes views; it
does not remap their behavior onto arbitrary controls.

The central rule is:

> A view decides where its behavior lives. A workspace may include or omit declared behavior, but
> it cannot wire callbacks to raw hardware.

This keeps workspace configuration declarative and makes conflicts fail during validation instead
of becoming order-dependent controller behavior.

## Runtime Boundary

```text
Push 2 + Bitwig
      |
      v
stable shell: permanent MIDI/USB callbacks, Bitwig proxies, bounded banks
      |
      | normalized events + authoritative snapshots
      v
reloadable core: active workspace, view policy, routing, desired state
      |
      | complete replayable result + one-shot effects
      v
stable shell: validate, commit, execute, render
```

The stable shell owns resources that Bitwig only allows during extension initialization. The
reloadable core owns behavior whenever the installed shell canopy already exposes the required
input, state, effect, and output capability.

A core-only policy change hot reloads. A new physical input, Bitwig proxy, bank shape, API contract,
or output transport requires a shell build and Bitwig restart.

## Data Model

### Surface areas

`SurfaceArea` names a stable physical region and privately expands it to atomic controls. Workspace
configuration never receives arbitrary coordinates. Overlap is therefore inspectable and can be
validated before activation.

A grid claim includes pad edges, strike velocity, and per-pad pressure. Pressure follows the same
pad owner; it is not enabled by a parallel list of concrete view classes. Aggregate channel pressure
is a separate surface-wide input because it has no pad identity.

The intended area vocabulary includes encoders and touches, parameter display cells, the bottom
track strip, upper and lower soft keys, upper and lower grid halves, matching scene keys, navigation
groups, the touch strip, transport controls, and global modifiers. The current `SurfaceArea` enum is
only the migrated subset; see **Migration status** below.

### Claims

A `SurfaceClaim` declares one view's use of an area:

- `OBSERVE_INPUT`: core observes input while established stable behavior may also receive it.
- `EXCLUSIVE_INPUT`: core is the only behavior owner for the routed input.
- `DIRECT_INPUT`: core owns a permanent feature-specific route that predates general arbitration.
- `OUTPUT`: the view owns replayable hardware output for the area.

Multiple observers may coexist. Two direct/exclusive input owners conflict. Two output owners
conflict. A compiled result must be independent of view declaration order.

### Views

`ControllerView` is reloadable behavior with fixed claims. It may:

- request authoritative bridge subscriptions;
- initialize and reconcile state from snapshots;
- handle only events covered by its claims;
- emit ordered effects; and
- render complete replayable output that it owns.

Input is a request to change state. Displays and lights render later authoritative Bitwig/shell
read-back, never the value most recently submitted merely for immediacy.

### Workspaces

`CompiledWorkspace` validates and deterministically composes core views. It merges input routes,
bridge subscriptions, outputs, clip bindings, and effects into one complete `CoreResult`.

The intended workspace shape is boring configuration:

```yaml
name: VS Live
session_bank: { tracks: 8, scenes: 4 }
views:
  - project-macro-controls
  - track-selection-strip
  - session-navigation
  - session-clip-grid-upper: { facets: [scene-launch] }
  - drum-controller: { facets: [pitch-bend] }
```

The Java-defined `ControllerWorkspaceView.VS_LIVE` value is the current equivalent. YAML/JSON is
not implemented and must not become a raw control-mapping language.

### Stable facets

`ControllerViewFacet` and `DesiredControllerWorkspace` are a migration bridge. They let core select
fixed mechanics that still depend on the inherited stable-shell object graph. They are not the
desired final substitute for core `ControllerView` composition.

The shell must interpret facet IDs, not workspace names. There must be no stable-shell conditional
for `"VS Live"`.

## VS Live Today

Shift + Session selects the hardcoded VS Live composition. Plain Session and Note return through
their ordinary destinations.

VS Live contains:

- project macros on the eight top encoders and parameter display;
- track names and selection on the bottom display strip and lower soft keys;
- Session arrow/page navigation;
- an 8-track by 4-scene Session grid on the upper four pad rows;
- the four matching upper scene keys;
- Drum Controller on the lower four pad rows; and
- drum pitch bend on the touch strip.

The lower Drum Controller includes its 4x4 playable block, rate pads, and twelve fill pads. It does
not own the lower scene keys. Per-pad pressure has a musical destination only on the playable 4x4
block and is implemented by reloadable `DrumPressureView`.

The normal Session view declares an 8x8 bank. VS Live declares 8x4. `SessionBankRegistry` eagerly
holds exactly those installed shapes, preserves track/scene offsets when switching, and enables
Bitwig clip-launcher feedback on only the active bank. An undeclared shape is rejected.

## Current Implementation Map

Reloadable core:

- `CompiledWorkspace`: claim validation, routing, deterministic composition.
- `DefaultWorkspace`: currently migrated core behaviors.
- `DrumFillView`: fill selection, launch lifecycle, bindings, and twelve RGB lights.
- `RecordControlView`: Record modifier policy.
- `ControllerWorkspaceView`: VS Live selection state and desired stable facets.
- `DrumPressureView`: VS Live playable-pad pressure policy.

Stable shell:

- `ControllerWorkspaceHost`: validates and transactionally realizes desired facets.
- `WorkspaceMode`: project macro and track-strip adapter.
- `WorkspaceView`: upper Session grid plus reusable lower Drum Controller adapter.
- `SessionBankRegistry`: bounded 8x8/8x4 Bitwig bank canopy.
- `PushControlSurface`: remaining stable pitch-bend and navigation integration.

## Migration Status

Implemented:

- Fixed-footprint claims and deterministic conflict detection for migrated core views.
- Behavior-preserving core views for drum fills and Record controls.
- Core-owned Shift + Session workspace selection and reload checkpoint state.
- Functional VS Live composition with the specified physical layout.
- Correct 8x4 Session navigation and Bitwig feedback via a declared bank.
- Core-owned composite drum pressure using the permanent NoteInput MIDI effect.
- Removal of the unused legacy aftertouch commands and ClipLauncherNavigator topology.
- Transactional shell preparation before a candidate result is committed.

Partial or transitional:

- `SurfaceArea` describes only drum pads, aggregate pressure, and a few buttons. Encoders, displays,
  soft keys, upper Session pads, scene keys, navigation, octave controls, touch strip, and general
  transport areas are not yet compiler-visible.
- VS Live's macro, track-strip, Session, navigation, Drum Controller, and pitch-bend facets are a
  fixed core-selected set, but most mechanics still run in stable `WorkspaceMode`/`WorkspaceView`.
- VS Live is not itself a `CompiledWorkspace` of independent core views. One core view emits a
  `DesiredControllerWorkspace` that activates stable facets inside the default compiled workspace.
- Optional facets are represented by the closed `ControllerViewFacet` enum. There are no core
  `ViewProfile`, `FacetId`, or `ViewFacet` types yet.
- Capability and Session-shape validation happens during stable result preparation, not entirely in
  `CompiledWorkspace`.
- General display and light output ownership has not crossed the core API. Only the twelve drum-fill
  RGB lights currently use core-owned output arbitration.

Deferred by design:

- YAML/JSON loading and schema versioning.
- Capability-driven optional-facet negotiation.
- Explicit named overlay/replacement rules.
- User-authored workspace configurations.
- Migration of every inherited DrivenByMoss mode/view family.
- Rich per-view navigation state across reloads.

## Rules For New Work

1. Decide the fixed physical footprint first. Extend `SurfaceArea` only with a reusable named area,
   never a workspace-specific coordinate fragment.
2. Put policy in a core `ControllerView` when the stable canopy already has the required capability.
3. Keep a stable adapter mechanical and generic. It may branch on fixed facet IDs, never workspace
   names or feature-specific policy that could reload in core.
4. Add all semantic variants of an exclusively owned gesture to core before making its stable
   binding inert.
5. Render authoritative host state. Submitted effects do not prove that Bitwig applied them.
6. Declare every required Session bank shape at shell initialization. Use the matching bank for
   rendering, navigation, paging, and Bitwig feedback.
7. Test claim conflicts, declaration-order independence, route ownership, rejected capabilities,
   authoritative read-back, and workspace exit/re-entry.
8. State explicitly whether the change is core-only/hot-reloadable or changes the shell canopy and
   requires a Bitwig restart.

The next architectural step is not more entries in `ControllerViewFacet`. It is expanding the
surface/output canopy and moving each stable facet behind a real fixed-footprint core view until VS
Live can be compiled from those views directly.
