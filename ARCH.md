# Pull View Architecture

Status: current through Core API 33, semantic controller-mapping identities, the shared mixer-control renderer, the Master-control
migration, the post-demo `VS Live` composition, and core-owned note-view and drum-rate policy.

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
stable shell: validate effects/output, execute effects, encode and transmit hardware output
```

The stable shell owns resources that Bitwig only allows during extension initialization. The
reloadable core owns all new or changed controller behavior. The core decides mappings, gesture
meaning, navigation policy, view/workspace selection, colors, layout, animation, and desired
hardware state. The shell may observe authoritative state and mechanically realize a core result;
it must not decide what that result means.

A core-only policy change hot reloads. A new physical input, Bitwig proxy, bank shape, API contract,
or output transport requires a shell build and Bitwig restart.

### Zero-new-stable-policy rule

Missing canopy coverage does not make stable behavior acceptable. If a requested behavior needs a
missing input route, snapshot, effect, or output lane, add the smallest reusable bounded capability
to the parent-loaded API/shell and implement the complete semantic policy in core. That is a
Class-B migration and may require one bootstrap restart. If the capability expansion is outside the
task's safe scope, stop and report the feature as not ready rather than putting a temporary version
in stable code.

Existing stable modes, views, commands, suppliers, and adapters are frozen migration debt. They may
preserve their existing behavior unchanged until migrated, but they may not acquire new product
semantics. Stable changes are limited to resource creation, observation, immutable snapshot
publication, validation and fencing, effect execution, lifecycle safety, generic output
interpretation, hardware encoding/transmission, and removal of legacy behavior.

Input and output transports are mechanically independent, but a controller surface's action and
feedback are one semantic slice. Migrating or changing a control that has feedback requires moving
both its action and feedback policy to core; do not retain its stable supplier for a later restart.
A request that changes only a light/display meaning still migrates that output policy. Stable
performs rendering in the graphics/hardware sense—rasterization, palette translation, clipping,
encoding, and writes. Core performs rendering in the product sense—content, colors, geometry,
states, timing, and composition.

Controller-level Play is fully core-owned. The core remembers the last authoritative project that
owned the audio engine. On another tab it renders purple while that owner is playing and white
while paused; pressing Play runs a bounded identity-fenced navigation, transport-toggle, and exact
return transaction. Bitwig exposes transport only for the visible document, so the core never
targets an offscreen project optimistically.

Controller-level note-view selection is also core-owned. Stable publishes the active layout plus a
selected-target-fenced preference and drum applicability. `ResolvedNoteViewer` resolves one
melodic, drum-controller, or audio viewer and the capabilities attached to that viewer;
`NoteViewControllerView` requests its layout while `DrumRateView` owns automatic roll only when it
is attached to the resolved Drum Controller viewer. The request remains active until later layout
read-back agrees. The stable shell validates the bounded view ID and mechanically activates it. It
does not infer a view from the current cursor, selected device, or previous layout. Track-selection
callbacks publish state but never recall an inherited preferred view. The Layout button is an
exclusive input of `NoteViewControllerView`; its normal and shifted cycles persist through the
same target-fenced preference effect and composed Note lifecycle. Layout activation changes only
the grid view—the independently selected controller page owns its mode.

`DrumRateView` exclusively owns the four rate-pad edges and RGB lights whenever Drum Controller is
authoritatively engaged. It requests a complete note-repeat state through a stable lease. The shell
captures the user's manual Repeat state before the first owned request, serializes toggle-only API
operations across later read-back, and restores the manual parameters while authoritatively
retiring Repeat when the core releases ownership.
The Bitwig **Automatic arp / roll** setting is published as state; core alone decides whether the
drum workspace owns repeat or leaves it untouched.

### Parameter banks, effects, and snapback

Core API 24 exposes named, view-independent banks for the inherited active encoder window, project
remotes, the selected-device remote page, visible-track volume and pan, project-scoped Master/Cue
controls, and fixed globals. A bank
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
| `MASTER` | Master volume/pan and project cue volume/mix | Current project identity plus exact current parameter proxy; a project-tab change creates a new target generation. |
| `GLOBAL` | Tempo and master volume | Fixed extension-lifetime target; master is available only in master-volume mode. |

Track sends are not installed as parameter-only rows. They belong with the future authoritative
visible-track bank so the rendered track window and send actuators share one generation fence.

`ProjectMacroControlsView` is the first complete parameter-input migration: its eight encoder turns
are exclusive core inputs mapped to `PROJECT_REMOTE`, and core emits typed relative effects against
the authoritative slot target. `WorkspaceMode` no longer binds or mutates those encoders; it remains
a frozen stable touch/delete and display adapter pending complete migration.

For movable Bitwig parameters, stable re-resolves the exact identity from the live parameter
domain, selected owner, selected page, and slot or channel role. The same Java `IParameter` wrapper
may therefore produce a new opaque target generation after Bitwig rebinds it. Unclassified Bitwig
parameter wrappers are not leaseable; wrapper identity alone never authorizes a restore.

Shift snapback policy lives in the reloadable core. The first eligible mutation publishes its
authoritative baseline before stable submits the write. Core retains that exact target, waits for
motion to settle, requests an absolute restore, and waits for later authoritative acknowledgement.
A core-owned view resolves a complete semantic action payload at gesture `BEGIN`. A frozen legacy
stable command remains unchanged until migrated, but its compatibility adapter publishes semantic
intent derived from the actual command and current mode path before the dispatch waits behind the
same restoration barrier. Touch edges do not define the session lifetime. Stable restores retained
targets best-effort if the core faults.

Core replacement waits until the physical input router has no core-relevant active gesture, queued
motion, or deferred stable callback. A stable-only `NONE` gesture with no semantic action never
entered core and does not fence replacement. This keeps each core-observed, core-owned, semantic, or
deferred gesture in one policy generation rather than transferring partial gesture state through a
core checkpoint.

The named bank slots still follow bounded movable proxies; they are not durable project-wide
parameter identities. API 24 deliberately restores before navigation and has no pinned actuator
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
- `STABLE_ADAPTER_INPUT`: frozen legacy input policy pending migration.
- `OUTPUT`: core owns and directly renders replayable hardware output for the area.
- `STABLE_ADAPTER_OUTPUT`: frozen legacy output policy pending migration.

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
single complete adapter manifest. Facets are not standalone views and may carry neither composition
nor product policy.

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

The lower Drum Controller includes its 4x4 playable block, four core-owned rate pads, eight fill
pads, four Bitwig-manually-mappable control pads, octave navigation, aggregate grid pressure, and its optional pitch-bend facet. It does not own
the lower scene keys. Per-pad pressure has a musical destination only on the playable 4x4 block and
is part of the same reloadable `DrumControllerView`, so pressure cannot be accidentally omitted
from a composition that owns those pads.

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
- `NoteViewControllerView`: authoritative per-selected-track note-layout policy.
- `DrumControllerView`: playable lower-grid mapping, pressure policy, and optional pitch bend.
- `DrumRateView`: four exclusive rate-pad gestures, RGB output, and desired note-repeat state.
- `DrumFillView`: fill selection, launch lifecycle, bindings, and eight RGB lights.
- `DrumControlPadView`: four exclusive physical control-pad routes, a complete
  physical-to-semantic controller-mapping lease, and authoritative semantic-endpoint red/off
  feedback; Bitwig's hardware mapping remains the actuator.
- `TransportControlView`: persistent authoritative Play/Record lights and Record modifier policy.
- `MasterControlView`: Master/Cue encoder policy, project/audio actions, both row-light banks, and
  a complete declarative graphics scene.
- `WorkspaceSelectionView`: shared Shift + Session entry and Session/Note exit policy.

Stable shell:

- `ControllerWorkspaceHost`: validates and transactionally realizes desired facets and note views.
- `StableControllerActionResolver`: derives semantic intent from remaining stable commands at their
  dispatch boundary.
- `ControllerRuntimeEnvironment`: owns bounded leases, action barriers, and committed bridge state.
- `WorkspaceMode`: project macro touch/delete and display plus track-strip adapter.
- `WorkspaceView`: upper Session grid plus reusable lower Drum Controller adapter.
- `SessionBankRegistry`: bounded 8x8/8x4 Bitwig bank canopy.
- `PushControlSurface`: remaining stable pitch-bend and navigation integration.
- `ControllerMappingHost`: eagerly creates the four permanent semantic Bitwig button identities,
  attaches their no-output Boolean feedback, and removes MIDI matchers from all 64 original grid
  buttons so physical pads remain ordinary-dispatch-only objects rather than learned identities.
- `HardwareMappingActivationHost`: mechanically projects the complete core lease onto those
  semantic buttons. A lane change immediately revokes new mapped presses, retains the exact routed
  gesture through `END`, and admits only the latest desired projection after the lifecycle is idle.
  Permanent raw MIDI supplies the normalized core gesture while a semantic matcher is active; when
  no mapping is active it triggers the established original-button dispatch through the same raw
  ingress for every grid pad. No duplicate learned action or second MIDI callback exists.

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
- Core-owned selected-track note-view policy with private-target identity fencing and delayed drum
  applicability reconciliation.
- Core-owned drum-rate input/output policy with authoritative note-repeat feedback and a
  stable-owned restoration lease for the user's manual Repeat state.
- Complete Master-page input and output arbitration. The core owns its copy, typography, geometry,
  colors, clipping, shapes, and composition; the stable shell only interprets bounded generic
  display primitives. Missing or execution-faulted core behavior is blank and inert. A stable
  preparation rejection preserves the active generation's last committed output rather than
  converting one invalid result into a controller-wide fault.

Partial or transitional:

- Project macro relative encoder behavior runs in core; its touch/delete and display remain in
  stable `WorkspaceMode`. Track-strip, Session, navigation, Drum Controller play pads and octave
  controls, and pitch-bend adapter-backed mechanics still run in stable
  `WorkspaceMode`/`WorkspaceView`.
- `ControllerViewFacet` remains a closed cross-boundary adapter ID.
- Capability and Session-shape validation happens during stable result preparation, not entirely in
  `CompiledWorkspace`.
- General display and light output ownership is still partial. The eight drum-fill, four drum-rate,
  and four mappable-control RGB lights, authoritative semantic Bitwig Boolean feedback, and replayable physical-to-semantic mapping leases, global Play/Record lights, the Master page's two button rows and graphics
  display, a temporary sparse 8x8 grid overlay, and a complete temporary 960x160 display overlay
  use core-owned output arbitration. The detailed design's API 33 installed-output inventory is
  canonical.

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
2. Put every new or changed product policy in a core `ControllerView`. If the stable canopy is
   missing a prerequisite, add the smallest reusable capability or stop; never implement the
   behavior in stable as an interim shortcut.
3. Keep stable adapters frozen, mechanical, and generic. They may preserve existing facet behavior
   but may not add a branch for a new feature, workspace, color, layout, gesture, or navigation
   meaning.
4. Add all semantic variants of an exclusively owned gesture to core before making its stable
   binding inert.
5. Render authoritative host state. Submitted effects do not prove that Bitwig applied them.
6. Declare every required Session bank shape at shell initialization. Use the matching bank for
   rendering, navigation, paging, and Bitwig feedback.
7. Test claim conflicts, declaration-order independence, route ownership, rejected capabilities,
   authoritative read-back, and workspace exit/re-entry.
8. State explicitly whether the change is core-only/hot-reloadable or changes the shell canopy and
   requires a Bitwig restart.
9. Account for every changed `pull-shell` line as resource creation, observation, validation,
   effect execution, lifecycle safety, generic hardware/output translation, or legacy-policy
   deletion. A shell diff that makes a product decision fails the architecture review.

For migrations and smoke-test fixes, begin with a behavior-characterization test that preserves
the intended Moss contract from physical input and modifiers through effect scale/identity and
authoritative output; where practical, verify that it fails on the buggy or pre-migration revision.
Fake hosts must keep command submission separate from explicit host advancement, and every
software invariant learned during live smoke testing should become a deterministic regression at
the lowest boundary that can represent it. Physical feel, firmware behavior, and host semantics
that cannot yet be represented remain a documented live smoke test rather than a cooperative mock.

The next architectural step is not another workspace-shaped facet. Expand the typed state, effect,
and complete-output canopy, then migrate one existing adapter-backed claim at a time from
`STABLE_ADAPTER_*` to core input/output without changing the workspace configuration.
