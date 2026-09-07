# Pull architecture

This is the assembly map for the current source: Core API 47, checkpoint schema 6, Bitwig API 25.
Core owns page composition and the shared target-bound interaction lifecycle. Some inherited
controller families still have stable implementations; the inventory below identifies them.

The corrected `bf5bc4d7` shell and matched API 47 core passed the scoped live smoke in `202arp3`
and remain installed. See the [smoke record](docs/migrations/interaction-lifecycle-live-smoke.md)
for exact build identities, coverage and physical-only limits.

## Modules and data flow

```text
Push input / Bitwig observations
              |
              v
pull-shell: permanent callbacks, finite proxies, input arbitration
              |
              | CoreEvent + authoritative ControllerSnapshot
              v
pull-core: page selection, fixed views, interaction policy, presentation
              |
              | complete CoreResult + ordered one-shot effects
              v
pull-shell: validate targets, execute effects, transmit display/lights/MIDI
              |
              +---- later host observations feed the next snapshot
```

| Module | Responsibility |
| --- | --- |
| `pull-core-api` | Parent-loaded immutable events, snapshots, desired state and effects. No Bitwig or framework objects cross this boundary. |
| `pull-core` | Child-loaded controller behavior and UI. Synchronous, bounded handlers; no host callbacks, threads or I/O. |
| `pull-shell` | Bitwig extension and resource lifetime, input/output transport, observed state, effect execution and exact actuator validation. Also contains the explicitly unmigrated framework bodies. |
| `pull-core-bundle` | Packages the core JAR as a resource, without exposing its implementation on the shell classpath. |
| `pull-core-publisher` | Publishes development candidates and waits for exact activation acknowledgement; `tools/reload-core` drives the build. |

The shell eagerly creates its bounded proxy topology during extension initialization. Core returns
its complete `DesiredBridgeSubscriptions` and named parameter-bank selection; unsubscribed domains
publish typed empty values without their high-rate sampling. Eager resource creation does not mean
every domain is sampled on every tick.

A `CoreResult` replaces the previous desired routes, subscriptions, page/workspace state, parameter
touches, musical routing and owned output. Effects are requests, not proof of success. Displays and
lights use later authoritative read-back. The shell checks mutable target identity at preparation
and again at execution; an old Java proxy object does not prove that its track or parameter survived.

## Pages, views and presentation

These are separate layers:

| Type / owner | Meaning |
| --- | --- |
| `PageId` / `Page` | An immutable page definition: identity, fixed views, optional arrow navigation and parameter indications. It contains no mutable navigation history or Bitwig objects. |
| `PageNavigation` | Selected/previous page and one replaceable temporary owner. Only that owner's exact token can return. |
| `ControllerView` | A unit of behavior with fixed physical claims, state subscriptions, input targets, effects and owned output. |
| `ControllerPages` | Declares the finite page catalog and Session/Note/Drum/VS backgrounds in Java. |
| `ControllerPageCompositions` / `CompiledWorkspace` | Compose those definitions, reject conflicting ownership and produce deterministic routing/output. |
| `core.ui.page` | Immutable presentation values, pure family renderers and shared styles. Renderers return display/light values; they cannot navigate, resolve host targets or emit effects. |

For example, Track consists of `TrackMixerControlsView` plus a footer. The body projects observed
parameters into `TrackMixerPagePresentation`; `TrackMixerPageRenderer` and its styles decide how
those values look. Selecting Master replaces the page while retaining the actual active grid and
musical view instances. Page selection and track selection are independent.

`SurfaceArea` gives each view named, inspectable control/display regions. Input ownership, native
musical ownership and output ownership are separate claims. A workspace selects declared profiles
and facets; it cannot bind arbitrary callbacks to raw hardware. New views use core-owned claims.
`STABLE_ADAPTER_*` and `ControllerViewFacet` identify frozen remaining behavior, not extension points.

Core page IDs need no new shell enum or registered page. `PushControllerPageManager` projects every
core page through one inert `CorePageMode`. Unmigrated bodies use explicit LEGACY references and a
bounded 64-request inbox to ask core for page changes. The manager supplies lifecycle safety; core
owns history, temporary return and Browser entry/return decisions.

The [views contract](docs/views-api-design.md) specifies claims, composition, presentation and the
remaining legacy projection protocol.

## Input lifetime

One policy applies to migrated controls: capture the current receivers and targets at BEGIN.
If a binding disappears or changes, cancel it, perform required cleanup, and ignore its physical
tail until a fresh press/touch. Returning to the old target does not revive it. A page overlay that
retains the same visible binding preserves the gesture.

`InteractionLifecycle` is the host-independent state machine. `InputGestureRouter` applies it to
views, semantic actions, parameter touches and companion motion. Cancellation never dispatches an
ordinary END action. Only active views run; cleanup retains the observations needed to retire an
exact resource, not an offscreen page.

The parent `PhysicalInputRouter` preserves ingress disposition and core generation through the
edge and its motion/pressure companions. The common stable grid dispatcher also retires its captured
receiver on view/target loss, so a remaining legacy callback cannot release into another view.
Native `NoteInput` remains a separate musical path with parent-owned neutralization.

Physical release, shell resource retirement and DAW completion are distinct. Later touch-lease
read-back proves touch retirement; fill ownership retires after its host barrier. Neither proves
that every previously submitted write completed. The [lifecycle contract](docs/interaction-lifecycle.md)
describes the current mechanism and its addressability limits. General asynchronous reload draining
is still [parked work](docs/findings/core-reload-quiescence.md).

## Supported compositions and ownership

Plain Session uses an 8-track × 8-scene grid. Shift + Session selects VS Live: an 8 × 4 Session grid
and scene keys above the lower Drum Controller, with Project Macros and the track-selection footer.
Track/Mix or another page replaces the parameter body while retaining the grid. Note/Layout resolve
the selected target's observed capabilities and preference; later layout read-back completes the
musical destination handoff.

| Surface / behavior | Current implementation |
| --- | --- |
| Project Macros, Track, Volume/Pan, eight Sends, Master/Cue | Core page definitions, named parameter targets, turns/touches/reset, menus, display and lights. Normal Track uses the current-bank footer; VS uses the Session track strip. |
| Global transport and pages | Core Play/Record, Mute/Solo, Tap, Undo/Redo, Track/Mix, Master/Frame, Accent, Metronome/Automation and migrated arrows, including their feedback. |
| Drum / selected Note behavior | Core applicability, Note/Layout selection, playable-pad pressure/lights, rate/roll, fills, octave/native maps and raw strip policy within the installed geometry. Separate views share the same target/cancellation machinery. |
| Session | Grid, scenes and page buttons remain frozen adapters. Core owns VS arrows and Stop policy; Stop stays OBSERVE for the inherited Stop-plus-pad chord. |
| Device/Chains/layers, Browser contents, Crossfade, Details/Color, settings and editing | Remaining stable bodies/providers. Their core-owned page entry/return does not migrate their controls or rendering. |

`DrumFillView` is composed directly in both standalone Drum and VS. `DrumControllerView` supplies
the composite musical route/facet, rather than wrapping other views' lifecycle methods. The lower
Drum footprint contains a 4×4 playable block, four rate pads, eight fill pads and four native
mapping pads; it does not claim the lower scene keys. `RawPitchBendView` owns the strip separately.

Selected-track Mute/Solo is independent of the visible page. Session Stop targets the private
selected track normally, or the exact Session bank/track for the supported chords. Shared gesture
consumption prevents a chord release from also performing plain Stop. Toggle lanes serialize
subsequent writes across authoritative read-back.

Learned controls use 128 permanent banks of four semantic endpoints, allocated per document to
track UUIDs. All 64 physical PAD actions remain ordinary-dispatch-only. Allocation, next toggle
value and light policy are core-owned; native Bitwig mappings actuate the targets. The
[mapping contract](pull-core-api/src/main/java/de/mossgrabers/pull/core/api/CONTROLLER_MAPPING_IDENTITY.md)
and [remaining lifecycle limits](docs/findings/track-scoped-midi-learn-lifecycle.md) define this boundary.

## Installed capacity and restart boundary

| Capability | Bound / identity rule |
| --- | --- |
| Session | Exactly 8×8 and 8×4 shapes; track/scene offsets retained when switching. Only the active bank owns launcher feedback. |
| Current-track banks | Two main windows and one effect bank, eight visible tracks each; track identity and independent navigation generation fence actions. |
| Named parameters | Seventeen banks, at most 131 slots: ACTIVE legacy window, project/device remotes, selected-track mix/sends, current-bank volume/pan/eight send columns, Master/Cue and globals. Only requested banks are sampled. |
| Drum | Canonical 16-pad window with bounded device candidates. The additional 64-pad proxy belongs to the legacy Drum64 body. |
| Native note maps | Complete 128-entry key/velocity tables, enabled notes restricted to the producing view's claimed physical Push pads (36–99). |
| Controller output | 960×160 base display with claimed regions, explicit temporary overlays, registered button/grid lights and touch strip. Output transport alone grants no product ownership. |

Parameter targets carry domain, owner, page, slot/role and generation. Selected/current/rendered
owners must agree before new writes or feedback. Project remotes also fence their project/page;
Master/Cue fence the project. Exact old cleanup remains separate from eligibility to acquire the
new target. `ACTIVE` is frozen legacy support. Selected-device remotes are excluded while their
production device identity is blank; a slot or name cannot repair that missing guarantee.

Core behavior, pages, styles and compositions inside these installed capabilities hot reload.
Changing parent-loaded API types, proxies/observers, capacities, permanent bindings, settings schema
or hardware transports requires a shell install and Bitwig restart. Arbitrary project-wide objects
and runtime YAML/plugin registration are not supported. The [runtime contract](docs/reloadable-controller-core-design.md)
covers classloading, transactional activation and host barriers.

## Where to change things

- Start controller/UI work with the [views contract](docs/views-api-design.md) and the
  [capability audit](docs/reloadable-core-migration-guide.md). Put all new product policy in core;
  move a migrated control's complete action and feedback together and delete its old policy.
- Follow the [remaining migration inventory](docs/reloadable-core-migration-roadmap.md) and
  [UI/editing handoff](docs/migrations/ui-and-editing-handoff.md) for unfinished families. Device
  identity and Session release/reuse still need host guarantees; the shared lifecycle does not
  create them.
- Read adjacent [active findings](docs/findings/README.md). Keep unresolved limits there and delete
  findings when their removal criteria are met. Git history holds superseded designs and reviews.
- Validate real routed behavior, later host state and transmitted output under
  [TESTING](TESTING.md). The [shortcuts ledger](docs/migrations/migration-shortcuts-and-friction.md)
  records current compromises; smoke records hold exact-build evidence, not architecture authority.
