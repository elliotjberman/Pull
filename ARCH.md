# Pull View Architecture

Status: current through Core API 14 and the post-demo `VS Live` view composition.

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

### Parameter banks, effects, and snapback

Core API 14 exposes named, view-independent banks for the inherited active encoder window, project
remotes, the selected-device remote page, visible-track volume and pan, and fixed globals. A bank
declaration is latent configuration; stable samples and publishes only the declared banks while
core requests the `PARAMETERS` subscription. Each slot publishes opaque target identity/generation,
name, raw and modulated values, authoritative displayed value, step count, and read-back tolerance.

Stable owns the live Bitwig proxies and actuators, target resolution, authoritative read-back,
exact leases, relative/reset/absolute effect application, and prepare/apply identity fences.
Physical Push control IDs are not host target identities. Core owns control-to-bank-slot mapping,
eligibility, relative mutation intent, and interaction policy. The ten-target snapback bound is an
interaction bound for eight top encoders plus tempo and master volume, not the size of the installed
parameter canopy.

| Bank | Capacity and scope | Identity fence |
| --- | --- | --- |
| `ACTIVE` | 8 slots bound by the current inherited stable mode | Stable binding generation plus resolved live domain/owner/page/role; compatibility only. |
| `PROJECT_REMOTE` | 8 project remote controls on the current page | Project owner, remote page, slot, and parameter name. |
| `SELECTED_DEVICE_REMOTE` | 8 controls on the current selected-device page | Device ID, remote page, slot, and parameter name. |
| `TRACK_VOLUME` / `TRACK_PAN` | 8 visible tracks per bank | Current bank slot, stable channel ID, and parameter role. |
| `GLOBAL` | Tempo and master volume | Fixed extension-lifetime target; master is available only in master-volume mode. |

Track sends are not installed as parameter-only rows. They belong with the future authoritative
visible-track bank so the rendered track window and send actuators share one generation fence.

`ProjectMacroControlsView` is the first complete parameter-input migration: its eight encoder turns
are exclusive core inputs mapped to `PROJECT_REMOTE`, and core emits typed relative effects against
the authoritative slot target. `WorkspaceMode` no longer binds or mutates those encoders; it remains
a stable touch/delete and display adapter. Input and output ownership are intentionally independent,
so project display rendering can migrate later without moving encoder policy back into stable.

For movable Bitwig parameters, stable re-resolves the exact identity from the live parameter
domain, selected owner, selected page, and slot or channel role. The same Java `IParameter` wrapper
may therefore produce a new opaque target generation after Bitwig rebinds it. Unclassified Bitwig
parameter wrappers are not leaseable; wrapper identity alone never authorizes a restore.

Shift snapback policy lives in the reloadable core. The first eligible mutation publishes its
authoritative baseline before stable submits the write. Core retains that exact target, waits for
motion to settle, requests an absolute restore, and waits for later authoritative acknowledgement.
A core-owned view resolves a complete semantic action payload at gesture `BEGIN`. A stable-owned
command remains stable-owned, but its compatibility adapter publishes semantic intent derived from
the actual command and current mode path before the dispatch waits behind the same restoration
barrier. Touch edges do not define the session lifetime. Stable restores retained targets
best-effort if the core faults.

Core replacement waits until the physical input router has no core-relevant active gesture, queued
motion, or deferred stable callback. A stable-only `NONE` gesture with no semantic action never
entered core and does not fence replacement. This keeps each core-observed, core-owned, semantic, or
deferred gesture in one policy generation rather than transferring partial gesture state through a
core checkpoint.

The named bank slots still follow bounded movable proxies; they are not durable project-wide
parameter identities. API 14 deliberately restores before navigation and has no pinned actuator
pool. The intended endpoint keeps physical controls, semantic `ParameterTargetRef` values, movable
Bitwig proxies, and bounded pinned leases independent. See
[`docs/findings/parameter-target-proxy-coupling.md`](docs/findings/parameter-target-proxy-coupling.md).

## Data Model

### Surface areas

`SurfaceArea` names a stable physical region and privately expands it to atomic controls. Workspace
configuration never receives arbitrary coordinates. Overlap is therefore inspectable and can be
validated before activation.

A grid claim includes pad edges, strike velocity, and per-pad pressure. Pressure follows the same
pad owner; it is not enabled by a parallel list of concrete view classes. Aggregate channel pressure
is a separate surface-wide input because it has no pad identity.

The current vocabulary covers every region used by the installed compiled workspaces: encoders and
touches, parameter display cells, the bottom track strip, both soft-key rows, upper and lower grid
halves and drum subregions, matching scene-key groups, navigation groups, the touch strip, and the
currently migrated transport/modifier controls. Add other named transport areas only when a view
actually needs them.

### Claims

A `SurfaceClaim` declares one view's use of an area:

- `OBSERVE_INPUT`: core observes input while established stable behavior may also receive it.
- `EXCLUSIVE_INPUT`: core is the only behavior owner for the routed input.
- `DIRECT_INPUT`: core owns a permanent feature-specific route that predates general arbitration.
- `STABLE_ADAPTER_INPUT`: the selected view owns input, but its stable adapter still implements it.
- `OUTPUT`: core owns and directly renders replayable hardware output for the area.
- `STABLE_ADAPTER_OUTPUT`: the selected view owns output, but its stable adapter still renders it.

Multiple observers may coexist. Two owning input claims conflict. Two output claims conflict,
regardless of whether core or a stable adapter realizes them. Adapter-backed claims are invalid
without the matching declared `ControllerViewFacet`. A compiled result must be independent of view
declaration order.

### Views

`ControllerView` is reloadable behavior with fixed claims. It may:

- request authoritative bridge subscriptions;
- map physical controls to bounded parameter slots and edge inputs to semantic action intents;
- initialize and reconcile state from snapshots;
- handle only events covered by its claims;
- emit ordered effects; and
- render complete replayable output that it owns.

Input is a request to change state. Displays and lights render later authoritative Bitwig/shell
read-back, never the value most recently submitted merely for immediacy.

### Workspaces

`CompiledWorkspace` validates and deterministically composes core views. It snapshots each selected
`ViewProfile` once, expands its named `ViewFacet` values, builds immutable input-owner tables, and
merges routes, bridge subscriptions, outputs, clip bindings, effects, and the stable-adapter
manifest into one complete `CoreResult`.

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

`VsLiveWorkspace.create(...)` constructs this configuration directly in Java. YAML/JSON is not
implemented and must not become a raw control-mapping language.

### Stable facets

`ControllerViewFacet` and `DesiredControllerWorkspace` are a migration bridge. Each real core view
selects only the fixed mechanical adapters required by its profile; `CompiledWorkspace` derives the
single complete adapter manifest. Facets are not standalone views and may not carry composition
policy.

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

The lower Drum Controller includes its 4x4 playable block, rate pads, twelve fill pads, octave
navigation, aggregate grid pressure, and its optional pitch-bend facet. It does not own the lower
scene keys. Per-pad pressure has a musical destination only on the playable 4x4 block and is part of
the same reloadable `DrumControllerView`, so pressure cannot be accidentally omitted from a
composition that owns those pads.

The normal Session view declares an 8x8 bank. VS Live declares 8x4. `SessionBankRegistry` eagerly
holds exactly those installed shapes, preserves track/scene offsets when switching, and enables
Bitwig clip-launcher feedback on only the active bank. An undeclared shape is rejected.

## Current Implementation Map

Reloadable core:

- `CompiledWorkspace`: claim validation, routing, deterministic composition.
- `DefaultWorkspace`: ordinary migrated behavior plus shared workspace selection.
- `VsLiveWorkspace`: Java-defined composition and declared 8x4 Session bank.
- `ProjectMacroControlsView`: core-owned relative encoder behavior plus adapter-backed touch and
  parameter-display ownership.
- `TrackSelectionStripView`: lower display strip and lower soft-key ownership.
- `SessionNavigationView`: arrow and page navigation ownership.
- `SessionClipGridView`: upper Session grid plus optional upper scene keys.
- `DrumControllerView`: complete lower Drum Controller, pressure policy, fills, and optional pitch
  bend.
- `DrumFillView`: fill selection, launch lifecycle, bindings, and twelve RGB lights.
- `RecordControlView`: Record modifier policy.
- `WorkspaceSelectionView`: shared Shift + Session entry and Session/Note exit policy.

Stable shell:

- `ControllerWorkspaceHost`: validates and transactionally realizes desired facets.
- `StableControllerActionResolver`: derives semantic intent from remaining stable commands at their
  dispatch boundary.
- `ControllerRuntimeEnvironment`: owns bounded leases, action barriers, and committed bridge state.
- `WorkspaceMode`: project macro touch/delete and display plus track-strip adapter.
- `WorkspaceView`: upper Session grid plus reusable lower Drum Controller adapter.
- `SessionBankRegistry`: bounded 8x8/8x4 Bitwig bank canopy.
- `PushControlSurface`: remaining stable pitch-bend and navigation integration.

## Migration Status

Implemented:

- Fixed-footprint areas, profiles, named optional facets, and deterministic conflict detection.
- Independent core views for every VS Live behavior and a real compiled VS Live workspace.
- Deterministic event-owner tables, active-workspace routes/subscriptions, and exit/re-entry
  reconciliation.
- Explicit core versus stable-adapter input/output claims.
- Behavior-preserving core views for drum fills and Record controls.
- Core-owned Shift + Session workspace selection and reload checkpoint state.
- Correct 8x4 Session navigation and Bitwig feedback via a declared bank.
- Drum pressure owned by the same fixed Drum Controller view as its playable pads.
- Removal of the unused legacy aftertouch commands and ClipLauncherNavigator topology.
- Transactional shell preparation before a candidate result is committed.

Partial or transitional:

- Project macro relative encoder behavior runs in core; its touch/delete and display remain in
  stable `WorkspaceMode`. Track-strip, Session, navigation, Drum Controller, and pitch-bend
  adapter-backed mechanics still run in stable `WorkspaceMode`/`WorkspaceView`.
- `ControllerViewFacet` remains a closed cross-boundary adapter ID. New adapter mechanics still
  require a Core API/shell change and Bitwig restart.
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

The next architectural step is not another workspace-shaped facet. Expand the typed state, effect,
and complete-output canopy, then migrate one existing adapter-backed claim at a time from
`STABLE_ADAPTER_*` to core input/output without changing the workspace configuration.
