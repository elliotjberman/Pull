# Pull architecture

Working source: Core API 55, checkpoint schema 6, Bitwig API 25. Migration is incomplete:
core owns ordinary Push display pages and migrated controls; the inventory below names the
remaining shell handlers. The optional Clip piano roll is deferred unchanged.

API 55 removes the legacy User handler and its raw page state. User selects the same Project
Macros view as Shift+Session while retaining the current grid. The button action and light are
core-owned; Shift+User remains inert. A matching shell install/restart and first live smoke are
pending. The last recorded installation below is API 54.

API 54 adds bounded raw page observations and moves remaining ordinary page drawing into core
components. Its matching shell was installed and restarted; the [display cutover validation](docs/migrations/ui-library-completion.md#validation)
records exact build identities, passed page checks and remaining live coverage. The
[Session smoke record](docs/migrations/session-core-live-smoke.md) and [Control Return](docs/control-return.md)
retain their separate scoped evidence. Physical Color drawing and selection are deferred together and
retain their existing implementation; see the [handoff TODO](docs/migrations/ui-and-editing-handoff.md).

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

The [UI component library](docs/ui-component-library.md) supplies shared choices, lists, toggles,
meters, sliders, parameter values and pure page renderers with one offline component/view catalog.
Core owns Info, Setup and Ribbon settings-page interactions and feedback. Settings writes wait for
read-back; page departure retires unsent intent. Physical
Ribbon behavior and Shift-strip entry remain partly in frozen handlers.

`CONTROLLER_PAGE_DISPLAY` samples only the active page's bounded raw Device, Option or Editing
state. `ControllerPageDisplaySnapshot` fences that state by observed mode; core owns formatting
and full-page assembly. Missing or mismatched observations clear the owned display. Browser
selection and note values come from host read-back; note observation is separate from the legacy
editor's optimistic working copy. See the
[display cutover audit](docs/migrations/ui-library-completion.md) for bounds and the piano-roll deferral.

Track Details observes the selected bank track or Master's `actionTargetId` separately from the
cursor used for display. Missing or unequal identities show “Waiting for track target...” instead
of details for a different target. Its frozen physical lights still read the cursor, including
when pinned; reconciling those lights belongs to the remaining control migration.

See [views](docs/views-api-design.md) for authoring and [interaction lifecycle](docs/interaction-lifecycle.md)
for target/cleanup contracts. Native `NoteInput`, command arbitration and learned hardware actions
are separate paths. The shared lifecycle does not make unmigrated shell handlers core-owned.

## Current ownership

| Surface | Implementation |
| --- | --- |
| Project Macros (User), Track, Volume/Pan, eight Sends, Master/Cue | Core page controls, touches/reset, menus, display and lights. The physical Master encoder is separately listed below. |
| Transport/global pages | Core Play/Record, Mute/Solo, Tap, Undo/Redo, Track/Mix, Master/Frame, Accent/Info/Setup, Ribbon settings, Metronome/Automation and migrated arrows, including feedback. |
| Drum / selected Note | Core applicability, Note/Layout, playable-pad pressure/lights, rates/roll, fills, octave/native maps and raw strip policy within installed geometry. |
| Session | Core grid, scene keys, bank/page/octave navigation, Stop chords, modifiers, create/record/copy/browse and observed blinking lights. Within the Session navigation slice, legacy parameter pages retain horizontal parameter navigation. |
| Device/Chains/layers, Browser, Scales/Layout, Repeat, Fixed Length, Add Track, Crossfade, Track/Layer Details, Clip/Note/Quantize/Groove | Core components render ordinary displays from raw observations. Actions, parameter providers, modifiers and hardware lights remain frozen stable behavior. |
| Color chooser | Physical pad drawing, target selection and click/return remain unchanged in the stable implementation; migration is explicitly deferred. |
| Optional Clip piano roll | Specialized rendering is deferred unchanged. |
| Clip/note editing gestures, clip length, Chords/Piano/Program Change, sequencers, Raindrops and alternate drum layouts | Remaining stable musical/editing controls and non-page feedback; core Note/Layout selection does not migrate the selected implementation. |
| Global knobs and standalone commands | Stable Tempo/Master/play-position variants and touch notifications; New, Duplicate, Delete, Double, Quantize, Convert and footswitch commands. Core handling of a modifier chord does not migrate its standalone command. |

Plain Session uses an 8×8 grid; VS composes upper Session with Project Macros, a track footer
and lower Drum controls. Views declare their fixed footprints and musical/output ownership.
Global Tempo/Master `DIRECT_INPUT` parameter bindings support parameter interaction; they do not
establish exclusive ownership of the complete knob gesture. Trace permanent commands and feedback
as well as core claims when auditing a migration.

Selected-track Mute/Solo is page-independent. Stop normally targets the private selected track;
its supported chords target the exact Session bank/track and consume the plain release action.
Toggle lanes serialize dependent writes across host read-back. Migrated track-navigation arrows step one track;
Shift pages eight. Light refresh is a single end-of-flush pass, so observer bursts cannot multiply it.

## Stable capability bounds

| Capability | Capacity / identity |
| --- | --- |
| Session | 8×8 and 8×4; exact project/channel/scene locations, at most 72 acquired launch presses. Cleanup precedes controller bank rebind; external loss fails closed. |
| Current-track banks | Two main windows and one effect bank, eight tracks each; track identity plus navigation generation. |
| Named parameters | Seventeen banks, at most 131 slots: ACTIVE legacy, project/device remotes, selected mix/sends, current-bank Volume/Pan/eight Sends, Master/Cue and globals. |
| Drum | Canonical 16-pad window and bounded device candidates; a separate 64-pad proxy serves legacy Drum64. |
| Native maps | Complete 128-entry key/velocity tables; enabled notes restricted to claimed physical Push pads 36–99. |
| Output | 960×160 display, claimed regions, explicit temporary overlays, button/grid lights and touch strip. |
| Page presentation | Active mode only: Device/Editing windows of at most eight slots; Browser seven filters and 48 visible items. No new actuator authority. |
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
