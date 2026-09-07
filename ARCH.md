# Pull architecture

Working source: Core API 47, checkpoint schema 6, Bitwig API 25. Migration is incomplete:
core owns pages and migrated controls; the inventory below names the remaining shell handlers.

Production `bf5bc4d7` passed [scoped live checks](docs/migrations/interaction-lifecycle-live-smoke.md).
Later review reproduced a [lost Session release](docs/migrations/session-launcher-location-design.md#current-release-regression).
That defect remains unresolved; the live checks did not cover it.

## Assembly

```text
Push / Bitwig -> shell callbacks and observed snapshots -> core behavior
                       ^                                      |
                       |       complete desired state         |
                       +------- and ordered effects <---------+
                       |
                       +-> validate targets, execute, transmit -> later read-back
```

| Module | Responsibility |
| --- | --- |
| `pull-core-api` | Parent-loaded immutable events, snapshots, desired state and effects. No Bitwig/framework objects. |
| `pull-core` | Child-loaded controller policy and UI; bounded synchronous handlers, no host callbacks, threads or I/O. |
| `pull-shell` | Initialization-owned proxies, observers, hardware bindings, resources, validation and output transport; also the unmigrated handlers below. |
| `pull-core-bundle` | Embeds the core JAR as a resource without exposing its classes to shell. |
| `pull-core-publisher` | Publishes candidates and waits for exact activation through `tools/reload-core`. |

The shell creates finite proxy topology at startup. Core's complete subscriptions and named-bank
selection gate sampling; unsubscribed domains publish typed empty values. Each `CoreResult`
replaces desired routes, resources and output. Effects request changes; feedback and dependent
operations use later read-back. Mutable targets are checked at preparation and application.

## Pages and input

| Owner | Responsibility |
| --- | --- |
| `PageId` / `Page` | Immutable page identity, fixed views, optional navigation and parameter indications. |
| `PageNavigation` | Current/previous page and one replaceable temporary owner with an exact return token. |
| `ControllerPages` / `ControllerPageCompositions` | Finite Java declarations composed over actual Session/Drum/Note/VS view instances. |
| `ControllerView` / `CompiledWorkspace` | Fixed `SurfaceArea` claims, subscriptions, targets, effects and output; reject ownership conflicts. |
| `InputGestureRouter` / `InteractionLifecycle` | Capture migrated interactions, cancel changed bindings and suppress physical tails until a fresh gesture. |
| `core.ui.page` | Immutable presentations, pure family renderers and shared styles; no navigation, target lookup or host effects. |

Master replaces the parameter page while retaining its active grid and musical views. New core
pages project through one inert `CorePageMode`; they need no shell enum. Remaining legacy bodies
request navigation through a bounded 64-entry inbox. Its aliases and `STABLE_ADAPTER_*` facets
are migration debt, not extension points.

See [views](docs/views-api-design.md) for authoring and [interaction lifecycle](docs/interaction-lifecycle.md)
for target/cleanup contracts. Native `NoteInput`, command arbitration and learned hardware actions
are separate paths. The shared lifecycle does not make unmigrated shell handlers core-owned.

## Current ownership

| Surface | Implementation |
| --- | --- |
| Project Macros, Track, Volume/Pan, eight Sends, Master/Cue | Core controls, touches/reset, menus, display and lights. Normal Track has a current-bank footer; VS has a Session track strip. |
| Transport/global pages | Core Play/Record, Mute/Solo, Tap, Undo/Redo, Track/Mix, Master/Frame, Accent, Metronome/Automation and migrated arrows, including feedback. |
| Drum / selected Note | Core applicability, Note/Layout, playable-pad pressure/lights, rates/roll, fills, octave/native maps and raw strip policy within installed geometry. |
| Session | Frozen grid, scenes and page-button handlers. Core owns VS arrows and Stop policy; Stop remains OBSERVE for the inherited Stop-plus-pad chord. |
| Device/Chains/layers, Browser body, Crossfade, Details/Color, settings and editing | Stable handlers/providers. Core page entry/return does not migrate their controls or rendering. |

Plain Session uses 8×8; Shift+Session selects VS with an upper 8×4 Session bank, Project Macros,
track-selection footer and lower Drum controls. The lower Drum footprint has 4×4 play pads,
four rate pads, eight fills and four native mapping pads; lower scene keys are unclaimed.
`DrumFillView` is directly composed in Drum and VS; `RawPitchBendView` independently owns the strip.

Selected-track Mute/Solo is page-independent. Stop normally targets the private selected track;
its supported chords target the exact Session bank/track and consume the plain release action.
Toggle lanes serialize dependent writes across host read-back.

## Installed bounds

| Capability | Capacity / identity |
| --- | --- |
| Session | 8×8 and 8×4, offsets preserved between shapes; only the active bank owns launcher feedback. |
| Current-track banks | Two main windows and one effect bank, eight tracks each; track identity plus navigation generation. |
| Named parameters | Seventeen banks, at most 131 slots: ACTIVE legacy, project/device remotes, selected mix/sends, current-bank Volume/Pan/eight Sends, Master/Cue and globals. |
| Drum | Canonical 16-pad window and bounded device candidates; a separate 64-pad proxy serves legacy Drum64. |
| Native maps | Complete 128-entry key/velocity tables; enabled notes restricted to claimed physical Push pads 36–99. |
| Output | 960×160 display, claimed regions, explicit temporary overlays, button/grid lights and touch strip. |
| Learned controls | 128 banks of four permanent semantic endpoints, allocated per document to track UUIDs. All 64 physical PAD actions remain ordinary-dispatch-only. |

Parameter references fence domain, owner, page, slot/role and generation. Selected/current/rendered
owners must agree. Old cleanup addressability is separate from new-write eligibility. ACTIVE is
frozen support; device remotes are excluded while production device identity is blank. See
[target limits](docs/findings/parameter-target-proxy-coupling.md) and the
[mapping contract](pull-core-api/src/main/java/de/mossgrabers/pull/core/api/CONTROLLER_MAPPING_IDENTITY.md).

Core behavior inside this installed capability set hot reloads. Parent API, proxies/observers,
capacity, permanent bindings, settings schema and output-transport changes need shell installation
and restart. [Runtime](docs/reloadable-controller-core-design.md) owns activation and host barriers;
[TESTING](TESTING.md) owns verification. Use the [migration audit](docs/reloadable-core-migration-guide.md),
[remaining checklist](docs/reloadable-core-migration-roadmap.md) and [active findings](docs/findings/README.md)
before extending a family. New product policy belongs in core; action and feedback migrate together.
