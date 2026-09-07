# Pull architecture

Working source: Core API 51, checkpoint schema 6, Bitwig API 25. Migration is incomplete:
core owns pages and migrated controls; the inventory below names the remaining shell handlers.

Production `2fa63736` / Core API 50 is installed and passed scoped Setup/Info and plain/Shift track-arrow checks.
The prior API 50 source passed 1,018 offline package tests. The [smoke record](docs/migrations/session-core-live-smoke.md)
identifies exact builds and limits; Session and reload coverage remains the earlier API 49 evidence.

API 51 removes the unused logical timer contract. It requires a matching shell install and
restart; this cleanup has not been installed or live tested.

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
| `pull-core` | Child-loaded controller policy, UI and configuration; bounded controller callbacks. |
| `pull-shell` | Initialization-owned proxies, observers, hardware bindings, resources, validation and output transport; also the unmigrated handlers below. |
| `pull-core-bundle` | Embeds the core JAR as a resource without exposing its classes to shell. |
| `pull-core-publisher` | Publishes candidates and waits for exact activation through `tools/reload-core`. |

The shell creates finite proxy topology at startup. Core's complete subscriptions and named-bank
selection gate sampling; unsubscribed domains publish typed empty values. Each `CoreResult`
replaces desired routes, resources and output. Effects request changes; feedback and dependent
operations use later read-back. Mutable targets are checked at preparation and application. A shared host guard releases Session
holds and revokes captured locations at actual track/scene/window mutation methods, covering core and frozen callers alike.

Core may parse configuration, including YAML, and read bundled or external data. Keep potentially
blocking external I/O off the controller callback path. Streams, watchers and background tasks need
explicit ownership and cleanup; results from a retired core generation must not alter controller
state. Adding background work includes implementing its reload and failure lifecycle.

## Pages and input

| Owner | Responsibility |
| --- | --- |
| `PageId` / `Page` | Immutable page identity, fixed views, optional navigation and parameter indications. |
| `PageNavigation` | Current/previous page and one replaceable temporary owner with an exact return token. |
| `ControllerPages` / `ControllerPageCompositions` | Validated page declarations composed over actual Session/Drum/Note/VS view instances. |
| `ControllerView` / `CompiledWorkspace` | Fixed `SurfaceArea` claims, subscriptions, targets, effects and output; reject ownership conflicts. |
| `InputGestureRouter` / `InteractionLifecycle` | Capture migrated interactions, cancel changed bindings and suppress physical tails until a fresh gesture. |
| `core.ui` | Shared components/styles and pure page renderers over immutable presentations; no navigation, target lookup or host effects. |

Master replaces the parameter page while retaining its active grid and musical views. New core
pages project through one inert `CorePageMode`; they need no shell enum. Remaining legacy bodies
request navigation through a bounded 64-entry inbox. Its aliases and `STABLE_ADAPTER_*` facets
are migration debt, not extension points.

The [UI component library](docs/ui-component-library.md) supplies shared choice, toggle, ring and
parameter visuals plus an offline catalog. Info owns hardware identity presentation, Info/Setup tabs,
current-bank row selection and row/display output. Setup owns its physical button, five preferences,
Delete-touch defaults and calibration graph; Ribbon owns its settings page and return controls.
Their settings writes wait for later read-back and page departure retires unsent intent. Physical
Ribbon behavior and Shift-strip entry remain frozen legacy policy.

`CONTROLLER_SETTINGS` adds five hardware integers (brightness 0–100, pad controls 0–10), exactly 128
velocity samples (1–127), and Ribbon function/CC/repeat ranges 0–5/0–127/0–2. These are observed global
preferences, not project targets. The shell caches the curve until settings change and rasterizes
bounded core line primitives inside compiler-owned clips. `CONTROLLER_HARDWARE`
samples the existing firmware/board/signed-serial tuple only when requested. Its generation counts
observed tuple changes, survives core replacement/subscription gaps and resets with the extension.
It is neither a connection guarantee nor an actuator identity; unsampled changes are unobservable.

See [views](docs/views-api-design.md) for authoring and [interaction lifecycle](docs/interaction-lifecycle.md)
for target/cleanup contracts. Native `NoteInput`, command arbitration and learned hardware actions
are separate paths. The shared lifecycle does not make unmigrated shell handlers core-owned.

## Current ownership

| Surface | Implementation |
| --- | --- |
| Project Macros, Track, Volume/Pan, eight Sends, Master/Cue | Core controls, touches/reset, menus, display and lights. Normal Track has a current-bank footer; VS has a Session track strip. |
| Transport/global pages | Core Play/Record, Mute/Solo, Tap, Undo/Redo, Track/Mix, Master/Frame, Accent/Info/Setup, Ribbon settings, Metronome/Automation and migrated arrows, including feedback. |
| Drum / selected Note | Core applicability, Note/Layout, playable-pad pressure/lights, rates/roll, fills, octave/native maps and raw strip policy within installed geometry. |
| Session | Core grid, scene keys, bank/page/octave navigation, Stop chords, modifiers, create/record/copy/browse and observed blinking lights. Legacy parameter pages retain only horizontal parameter navigation. |
| Device/Chains/layers, Browser body, Crossfade, Details/Color, remaining settings and editing | Stable handlers/providers. Core page entry/return does not migrate their controls or rendering. |

Plain Session uses 8×8; Shift+Session selects VS with an upper 8×4 Session bank, Project Macros,
track-selection footer and lower Drum controls. The lower Drum footprint has 4×4 play pads,
four rate pads, eight fills and four native mapping pads; lower scene keys are unclaimed.
`DrumFillView` is directly composed in Drum and VS; `RawPitchBendView` independently owns the strip.

Selected-track Mute/Solo is page-independent. Stop normally targets the private selected track;
its supported chords target the exact Session bank/track and consume the plain release action.
Toggle lanes serialize dependent writes across host read-back. Migrated track-navigation arrows step one track;
Shift pages eight. Light refresh is a single end-of-flush pass, so observer bursts cannot multiply it.

## Installed bounds

| Capability | Capacity / identity |
| --- | --- |
| Session | 8×8 and 8×4; exact project/channel/scene locations, at most 72 acquired launch presses. Cleanup precedes controller bank rebind; external loss fails closed. |
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

Core behavior and configuration schemas using installed capabilities can change with a core reload.
Changes to parent-loaded code/API, startup-created Bitwig preferences or proxies, proxy capacity,
permanent bindings or output transport need shell installation and restart.
[Runtime](docs/reloadable-controller-core-design.md) owns activation and host barriers;
[TESTING](TESTING.md) owns verification. Use the [migration audit](docs/reloadable-core-migration-guide.md),
[remaining checklist](docs/reloadable-core-migration-roadmap.md) and [active findings](docs/findings/README.md)
before extending a family. New product policy belongs in core; action and feedback migrate together.
