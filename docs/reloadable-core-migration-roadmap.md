# Reloadable-Core Migration Roadmap

## Goal

Pull should eventually require a Bitwig restart only when changing the permanent resource canopy:
Bitwig API topology, physical hardware registration, parent-loaded API contracts, or hardware
transport. Controller mappings, modes, gestures, navigation, product behavior, and rendering policy
should reload through `pull-core`.

This document inventories the remaining Push behavior in the stable shell and separates:

1. policy that can move with the currently installed canopy;
2. policy that is movable after one reusable canopy expansion;
3. temporary migration scaffolding;
4. infrastructure that genuinely belongs in stable.

For the step-by-step execution procedure and a complete Play-button example, see
[`reloadable-core-migration-guide.md`](reloadable-core-migration-guide.md).

## Terminology

- **Stable shell**: parent-loaded code that owns Bitwig and Push resources for the extension's
  lifetime.
- **Reloadable core**: child-loaded pure Java behavior operating on immutable snapshots, normalized
  events, typed effects, and replayable desired output.
- **Canopy**: the bounded set of state, effects, inputs, and output transports created by the shell
  at initialization.
- **Migration debt**: product behavior that remains stable because migration was deferred, not
  because Bitwig requires the behavior itself to be parent-loaded. It is frozen and may be removed
  or migrated, but not extended with new semantics.

Moving a feature does not mean copying its existing class into `pull-core`. Legacy classes commonly
retain `IModel`, `PushControlSurface`, mode/view managers, observers, or scheduled callbacks. A
correct migration extracts their policy while the stable shell continues owning those resources.

## Current baseline

In the current baseline:

- drum-fill matching, launch-session policy, gesture state, and eight physical fill lights are
  core-owned by the selected Drum composition; melodic Note layouts do not claim that footprint;
- the shared 4x4 drum-play view owns playable-pad pressure and sixteen RGB lights in both the
  standalone Drum page and VS Live; lights follow target-aligned drum-window playing-velocity
  read-back, and the old shell observers/fade policy are deleted;
- four Bitwig-manually-mappable control pads, their replayable physical-to-semantic leases, and
  their authoritative mapped-state toggle and red/off policy are core-owned. API 44 adds 128
  permanent four-endpoint banks with core-owned append-only per-track allocation and observed
  document-storage acknowledgement before matching. Target presence/value remains raw; core owns
  the midpoint and failure indication. Original PAD actions remain ordinary-dispatch-only, and
  the four old shared endpoints are inert. General lifecycle TODOs remain in
  `findings/track-scoped-midi-learn-lifecycle.md`;
- Record, Shift+Record, and Select+Record are core-owned;
- VS Live selection and fixed-facet composition are core-owned;
- core owns page identity, history, temporary ownership and selection after semantic admission;
  frozen legacy requests enter its reducer, and Note-route reconciliation cannot select a page;
- stable adapters still realize Session grid/scene and page buttons; VS arrows and generic core
  page arrows are core-owned, while full Session over legacy pages declares its frozen arrows;
- the API 45 migration moved Project Macro/Master touches, Delete/automation release, Drum octave
  and native maps, raw touch-strip gestures/output, normal/VS Track, Volume/Pan/Send, global
  navigation, Tap Tempo, Undo/Redo, Metronome/Automation, Frame/Master entry and Accent into core.
  Its exact-build offline/live evidence is recorded in the migration smoke document; that earlier
  evidence does not verify the working API 46 page-ownership refactor;
- API 46 replaces per-page stable registrations and native-mode acknowledgement with a typed core
  page catalog, local navigation, independent musical backgrounds, and one generic inert adapter.
  Presentation models, family renderers and styling are separate from navigation;
- current-bank navigation, controller preferences/cursor-send metadata, and native application,
  Arranger/Mixer and browser activity are available as subscribed raw state. Classified parameter
  owner/domain/page/index metadata fences independently sampled publications;
- drum-grid pressure interpretation and selected-target Note routing policy are core-owned in both
  standalone and composite Drum layouts, while the permanent `NoteInput`, direct-route actuator, and MIDI
  neutralization remain stable;
- Note, Session, and Layout edges are core-exclusive with inert stable commands; selected-track
  observers no longer recall a stable preferred view, so the composed controller-state host is the
  only Pull Note-layout actuator;
- the practical existing Push input set is normalized by the stable input bridge;
- every registered Push button light and every physical grid-pad light has a generic explicit
  core-or-stable arbitration plane; unclaimed controls preserve their exact frozen stable output;
- transport, selected-track, controller-layout, bounded Session-bank, and bounded drum
  snapshots/effects exist; the Session bank includes visible track names and exact fenced track
  selection as well as bank Stop;
- Play and Record lights are core-owned through generic authoritative RGB output;
- the Master page's two button rows and graphics scene are core-owned through complete output
  arbitration; its generic stable scene interpreter contains no Master layout policy;
- one generic complete 960x160 base-scene plane projects core output on every Push page. The VS
  Live Project Macro or Track Mixer body and retained Track Selection view compose its fixed
  960x143 and 960x17 regions; Track/Mix owns named selected-track parameters, complete touches,
  menus, and rendering;
  Track Selection also owns its lower-row actions and authoritative RGB feedback. The deleted
  stable page/selection/light paths do not return on missing core output;
- a generic sparse 8x8 pad-grid overlay can freeze, temporarily replace, and restore stable pad
  output; animation geometry, color, cadence, and activation policy are core-owned;
- a generic complete 960x160 display overlay can temporarily replace and restore the inherited
  display page; overlay copy, geometry, color, and activation policy are core-owned;
- the sixteen drum-play, eight drum-fill, four drum-rate, and four mappable-control lights and the
  four control pads' semantic mapping leases are also core-owned; unclaimed button/grid policy and
  other Push output semantics remain frozen migration debt;
- Shift snapback policy, view-owned physical-to-parameter-slot admission, semantic action
  invalidation, restoration acknowledgement, and navigation ordering are core-owned; stable owns
  named bounded Bitwig parameter banks, exact actuator leases, identity fencing, effect execution,
  command-driven compatibility-intent adaptation, and compatibility-action dispatch;
- VS Live project-macro encoder mapping, relative mutation policy, display rendering, and snapback
  admission are core-owned. Its Track/Mix replacement likewise owns named selected-track
  parameters, touches, rendering, and relative turns. Project Macro and Track/Mix Delete/automation
  release and upper-row Track page-menu policy also run in core.

Before taking an item, inspect the active branch and in-flight work. This inventory describes
architectural ownership, not a promise that no adjacent PR has changed the exact files.

The active implementation inventory and remaining inherited families are tracked in
[`migrations/core-migration-plan.md`](migrations/core-migration-plan.md). Session slot/scene
observation is implemented, but general launcher release requires the contract decision described
in [`migrations/session-launcher-location-design.md`](migrations/session-launcher-location-design.md).

The working contract is Core API 46 and checkpoint schema 6. Capability revisions include bridge
snapshot 14, controller output state 4, controller pages 1, parameter targets 4, input routing 7,
current-track effects 2, transport effects 4, and controller-settings/application-UI effects 1.
`DesiredControllerState.page` carries opaque core references and complete local page ownership;
`DesiredControllerWorkspace` contains only fixed grid/adapter facets. `installedModeId` and native
mode effects are no longer in the page path. `ControllerPages` declares the finite page/background
catalog, and `ControllerPageCompositions` retains its actual grid/view instances during replacement.

Frozen callers submit bounded requests to core. The shell retains a monotonic retired request
prefix, publishes it even when pending requests are unsubscribed, and admits callbacks only while
a healthy consumer exists. Startup rebases the prefix independently of checkpoint compatibility;
quarantine abandons pending work and retires delayed callbacks. Raw browser activity is observed
separately, while core owns temporary Browser entry and exact-token return. These mechanisms do
not expose arbitrary Bitwig topology or migrate the remaining Browser/Device bodies. The final
API 46 integration gate and exact-build live smoke remain separate from previous baseline evidence.

## State and effect primitives

The production shell already exposes the following state and effect primitives. This inventory is
not a Class A readiness list: a physical control with feedback is Class B until its input, state,
effect, and complete feedback lane can migrate together. Run the migration guide's Class A/B/C
audit for every control. The first complete cut may need one shell change to install a reusable
output lane, make an existing semantic binding inert, and admit its exact exclusive input;
subsequent policy changes inside that installed vertical slice are core-only.

### Transport

- Play/stop and stopped Shift+Play rewind.
- Absolute arranger record, arranger overdub, launcher overdub, loop, metronome, and global fill
  state.
- Absolute tempo and arranger position.

Metronome and Automation now own their global gestures and complete temporary settings pages,
using unified Automation Write/reset primitives, pre-roll and tick settings, and the
generic page protocol. Native Tap Tempo and Undo/Redo are also migrated. Tempo, Play Position, and
other remaining controls still need full variant audits; the presence of a transport effect alone
is not permission to claim their exclusive inputs.

### Selected track

- activation and group expansion;
- record arm, mute, and solo;
- monitor mode;
- volume and pan;
- stop, return to Arrangement, and create a new clip.

API 41 owns Mute/Solo as one persistent selected-track view and Stop as part of `SessionView`.
Plain Stop preserves the inherited immediate actuator; page overlays retain the active grid-view
instances so their physical gestures remain continuous. Mute, Solo, Record-arm, and
launcher-overdub toggles queue bounded parity and wait for later authoritative acknowledgement
before submitting a dependent absolute write.
The former project-clear, master, layer, lock/long, page-row, pad, and note modifier variants were
explicitly removed as a product decision rather than carried into the composable view API.

### Selected drum pad

- selection;
- activation, mute, and solo;
- volume and pan.

The installed drum window is bounded. Behavior outside that fixed window or across new device-tree
shapes is not covered merely because selected-pad effects exist.

### Routed raw MIDI

- poly pressure;
- channel pressure;
- control change;
- pitch bend.

The shell owns the permanent `NoteInput`, normal Bitwig routing, and neutralization on reload,
selection boundaries, and shutdown. The core may own interpretation and requested MIDI values.

### Pure controller policy

- modifier interpretation;
- press/long/release state machines;
- fixed workspace selection and composition;
- behavior using only existing immutable snapshots and typed effects.

Logical timer DTOs exist in the Core API type hierarchy, but no timer capability or executor is
installed in the production shell. They are reserved/test-only while
`findings/logical-timer-production-gap.md` is active and production core behavior must not emit
them.

## Movable policy requiring reusable canopy expansion

This is the main body of remaining work. It is migration debt, but not a file-only move.

### 1. Complete remaining hardware output

API 37 completes reusable light arbitration for every registered Push button and all 64 physical
grid pads. The shell validates output against the permanent physical registry; an explicit core
owner replaces the frozen stable supplier, and an unclaimed control preserves that supplier
exactly. Core compilation also rejects a view that renders a light outside its declared output
claims. This installs the transport, but it does not silently migrate legacy meaning: each control's
action, authoritative state, and feedback must still move together.

The generic complete 960x160 base-scene projection is now installed. Master owns one complete scene,
and the VS Live Project Macro and Track Selection views compose disjoint, containment-checked
960x143 and 960x17 regions. Add bounded complete semantic ownership for the remaining surfaces:

- remaining non-raw ribbon configurations and musical touch-strip policies; the raw pitch-bend
  mode, position LEDs, and release behavior already form a complete core-owned slice;
- the other USB display pages using the installed scene buffer;
- transient notifications with explicit lifetime and replacement rules.

After this expansion, color choice, light meaning, display layout, and notification policy move to
core. Palette lookup, calibrated RGB conversion, USB packet encoding, and physical writes remain
stable.

This is the highest-leverage expansion because nearly every legacy mode/view mixes behavior with
rendering.

### 2. Visible track bank and mixer

The working API 46 canopy supplies an eight-slot `CurrentTrackBankSnapshot` independently of the Session bank, using
the two initialized main-bank windows and one effect bank. It observes exact row identities,
colors, group state and VU, and supports typed exact selection, duplication, removal, arm/expansion,
selected-group entry, and cursor-parent navigation. A separate navigation generation fences track
and scene offsets plus project and model-cursor ID/pin/position; primitives cover track/scene step
or page and cursor swap. The core owns arrow mappings, modifiers, and availability lights.

Normal Track, Volume, and Pan now use this state and the core footer. VS retains its Session-bank
footer. Named `SELECTED_TRACK`/`SELECTED_TRACK_SENDS` and `TRACK_VOLUME`/`TRACK_PAN` banks keep
parameter targets independent of physical providers. Current eligibility rechecks the current bank;
exact old addressability for touch/Snapback cleanup remains separate. Classified parameter metadata
allows the core to reject a stale rendered row when parameter-only publication has already changed
owner. General visible-track sends, Crossfade, and related mixer modes remain open slices.

Controller-level Play is now the reference transport migration: its stable command is inert, its
edge is core-exclusive, and core targets the remembered engine-owning project with one exact
origin/target transport effect. Stable owns the complete bounded navigate/readback/return operation,
so a child-core reload or quarantine cannot split or strand the transaction. The lightweight
`PROJECT` subscription keeps that policy available in every workspace without sampling Master VU.

This unlocks:

- ordinary track selection;
- remaining Send, Crossfade, and related mixer modes; Track, Volume, and Pan are already migrated;
- any future explicitly designed visible-track state controls;
- authoritative track-strip lights and display output on other pages.

Do not confuse this with the existing private selected-track snapshot. A selected target cannot
represent eight visible tracks.

### 3. Session grid

API 41 installs the bounded visible Session bank's track identities/names/types, track/scene offsets,
basic track state, generation-fenced bank-wide Stop, exact visible-track Select, and exact visible-track
Stop. `SessionView` uses it for Shift/Select Stop while plain Stop uses the private authoritative
selected target. Stop-plus-track captures generation/shape/index/channel at row `BEGIN`, stops that
track without selecting it, and fails closed if the bank changes before apply. The optional
`SESSION_CLIPS` domain now observes the bounded slot/scene window, including existence, content,
name, color and playback/queue state. That read-back is groundwork, not a launcher cutover.

Add effects for:

- launch and release/stop of a slot;
- launch of a scene;
- selecting a slot or scene where required;
- remaining Session-specific page/navigation actions; generic current-bank arrows are installed;
- creating a clip if the product behavior requires it.

Completing those slot capabilities unlocks:

- VS Live's upper Session grid and scene keys;
- migration of the remaining stable-adapter grid/scene portions of ordinary `SessionView`;
- clip-slot rendering and launch behavior;
- remaining Session page buttons and grid-dependent navigation.

General Session release has no native completion acknowledgement. The bounded location-actuator
proposal and required behavioral contract decision remain open in
[`migrations/session-launcher-location-design.md`](migrations/session-launcher-location-design.md).
Do not silently weaken release semantics to complete the exclusive grid migration.

The existing drum-fill catalog and actuator lease should eventually become a consumer of a generic
bounded clip/session capability rather than remain a parallel feature-shaped API.

### 4. Complete parameter-view migration and output

Core API 46 includes named active-compatibility, project/device remote, selected-track/send,
visible-track volume/pan/eight send columns, Master/Cue, and global parameter banks. Snapshots contain opaque actuator
identity, classified semantic owner/domain/page/index, name/value/display metadata, and optional
enabled state. Stable applies exact fenced absolute, relative, reset, enabled, and touch operations.

Project Macro, Track, Volume, Pan, Send, and Master now own complete parameter input and feedback. The
shared touch session preserves Delete/reset ordering, exact release, automation preferences, and
cross-page END handling. Generic ordered touch acquisition preserves Track send-enabled ordering;
complete desired leases remain replayable. Cleanup cannot release a replacement target after an
external mutable-proxy rebind.

Migrate remaining device and other parameter pages against reusable named banks and complete
typed core page declarations through the generic inert adapter. Their display scenes already have
a generic output transport.
Delete ACTIVE and remaining physical parameter providers only when all their consumers migrate.
The shell keeps exact actuation and lifecycle validation; core owns mappings, gestures, menus,
response curves, copy, geometry, and output policy.

### 5. Device, chain, and layer banks

Install bounded selected-device, device-page, chain, and layer snapshots with stable identity and
generation-fenced effects.

This unlocks:

- Device Params;
- Device Chains and Layer modes;
- layer volume, pan, and sends;
- Device Browser behavior once browser actions are also modeled.

Device-tree recursion must remain explicitly bounded. Do not imply that one cursor mirrors an
arbitrary nested project.

### 6. Clip content and step editing

Install a bounded selected-clip content model for the exact note/time window rendered by a view,
plus typed step-edit, note, length, velocity, expression, page, quantize, and duplicate effects as
required.

This unlocks:

- Sequencer and Poly Sequencer;
- Drum XoX, Raindrops, and related variants;
- clip length and note editing;
- step-page navigation.

This is a later migration because its state surface and asynchronous acknowledgement rules are
larger than transport or mixer behavior.

### 7. Scale, note layout, repeat, and controller configuration

Publish immutable configuration and scale/layout state needed by the core. Keep Bitwig preference
schema registration stable, but move interpretation into core. Add typed effects for supported
configuration changes.

This unlocks:

- Piano, Play, Chords, and Program Change layouts;
- Scales and Scale Layout modes;
- Accent and Note Repeat;
- fixed length and quantize policy;
- ribbon and pad-pressure configuration policy.

### 8. Application and browser actions

Undo/Redo and Frame application layout/options are implemented. APPLICATION_UI publishes the
native panel layout and thirteen Arranger/Mixer flags only while requested; the existing native
values remain eagerly interested. Exact project/layout contexts fence typed layout selection,
absolute observed-flag setters, and six unobservable native panel toggles. Core owns option policy
and later-readback feedback; a toggle submission does not invent visibility.

Raw browser activity is installed, and its temporary page lifecycle is core-owned. Browser search,
selection, commit/cancel, add-track/device/effect, duplicate/delete/double/convert, and other
application capabilities still need complete control migrations. Existing selected-track/current-bank
operations cover only their declared target scopes. Generic UI actions are not an arbitrary action
string or raw callback escape hatch.

## Stable policy families to retire

The following Push-specific families should shrink or disappear after their corresponding
capabilities migrate:

### Commands

Most classes under:

```text
pull-shell/src/main/java/de/mossgrabers/controller/ableton/push/command
```

Their permanent hardware registrations remain in setup, but semantic commands become inert during
cutover and can later be replaced by generic physical registrations.

### Modes

Most classes under:

```text
pull-shell/src/main/java/de/mossgrabers/controller/ableton/push/mode
```

Their parameter interpretation, modifier behavior, display decisions, and button colors belong in
core. Migrated temporary-mode policy now uses SELECT/TEMPORARY/RESTORE primitives, full raw
mode-origin fencing, and later acknowledgement. Registered `CorePageMode` callbacks stay inert;
remaining modes must migrate through complete page profiles, without adding new page facets.

### Views

Most classes under:

```text
pull-shell/src/main/java/de/mossgrabers/controller/ableton/push/view
```

Grid mapping, launch/edit policy, note layouts, navigation, and desired lights belong in core.
Stable note-input translation or Bitwig resource objects remain behind typed capabilities.

### Parameter providers

Push-specific providers should disappear after generic parameter-bank snapshots/effects can express
the same selection and mapping policy.

### Mixed-responsibility setup and surface code

`PushControllerSetup` remains, but should converge on resource creation and permanent physical
registration rather than constructing semantic commands and light policy.

`PushControlSurface` remains, but product applicability, active-view behavior, and render policy
should move out. MIDI/USB transport and concrete hardware objects stay.

`PushColorManager` should retain hardware palette/calibration concerns while semantic mappings such
as “recording should be muted pink” move to core output policy.

## Temporary migration scaffolding

These structures exist to bridge core-selected policy back into legacy mechanics and should not be
mistaken for the final architecture:

- `ControllerWorkspaceHost`;
- `WorkspaceFacetAdapter`;
- `WorkspaceView`;
- legacy `Views.WORKSPACE` realization and frozen `Modes` aliases required by unmigrated callers;
- the fixed-facet `DesiredControllerWorkspace` compatibility protocol once core owns the complete
  underlying input/output behavior;
- feature-shaped `desiredClipBindings` fields once a generic bounded session interaction API exists;
- fill-session fields in `ControllerRuntimeEnvironment` once a generic bounded Session interaction
  capability replaces the feature-shaped clip API.

Input arbitration itself is not temporary. Keep normalized input, route validation, edge ordering,
generation leases, and held-gesture safety. Remove the duplicate stable semantic implementation,
not the lifecycle protection.

The migration-debt rule above applies to every listed scaffold.

## Infrastructure that stays stable

The following are the intended long-term shell:

- extension definition, controller API version, UUIDs, port definitions, and discovery;
- Bitwig cursors, banks, observers, interested properties, and bounded actuator pools;
- MIDI and USB connections, Push display transport, pad-grid objects, and physical writes;
- permanent physical control registration and normalized event delivery;
- input arbitration, edge ordering, gesture generation fencing, and motion coalescing;
- immutable snapshot capture and subscription gating;
- effect preparation, validation, target-identity fencing, and controller-thread execution;
- the permanent `NoteInput` and cleanup of outstanding stateful MIDI;
- clip launch actuators and host-acknowledgement barriers, behind a more generic interface where
  useful;
- output arbitration and replay into physical hardware;
- core JAR watching, classloader isolation, transactional activation, fault handling, and
  checkpoint transfer;
- Bitwig settings schema registration;
- hardware color calibration, palette translation, USB packet encoding, and device-specific
  protocol details.

## Recommended program order

### Phase 1: Complete output arbitration

Extend complete light/display ownership to the remaining underlying grid policy, global controls,
display pages, non-raw ribbon configuration, and notification output. The temporary whole-grid overlay transport is
already installed. Each added surface needs explicit hardware smoke tests.

This comes before further ordinary behavior changes because output-only feature work is still
behavior. Do not edit an inherited stable renderer while waiting for this phase. A smaller feature
may land a bounded generic output lane early when it is reusable and completes one vertical slice.

### Phase 2: Common performance capabilities

The common current-bank, exact touch, native-map, raw-strip, generic page, controller-settings,
and application-UI capabilities are installed in the working API 46 tree. Current package validation
and live activation remain separate gates. Their shared consumers include Project/Master/Track,
Volume/Pan, Drum octave/strip, Track/Mix, Metronome/Automation, and Frame/Master entry.

Next complete the Session release contract and grid/scene effects, then remaining parameter-bank
contexts and their complete pages. `WorkspaceMode` has been deleted in favor of the generic
`CorePageMode`; `WorkspaceView` still carries Session grid/scene debt. Do not mark all Drum, Note, or configuration behavior migrated merely because
current Drum octave/native mapping and pressure/strip slices are core-owned.

### Phase 3: Complete vertical migrations

Migrate one complete semantic surface at a time using the guide's Class A/B/C audit. A completed
surface includes every changed input variant, state subscription, typed effect, light/display
meaning, reload state, and removal or inerting of the stable implementation. Input-only or
output-only cutovers are valid only for surfaces that genuinely have only that side; a control's
action and feedback migrate together.

Good early candidates are complete selected-track mappings, selected-drum-pad mappings, and
single-branch transport controls whose long-press/mode/display behavior is already representable.
Each first cutover may require one shell install for reusable capabilities. Group compatible canopy
work when useful, but never use checkpoint scope as a reason to put policy in stable code.

### Phase 4: Ordinary controller families

Migrate ordinary Session, Drum, track/mixer, project macro, Note, scale, and repeat behavior. Remove
the corresponding stable semantic commands/modes/views after each complete cutover.

### Phase 5: Deep editing

Add device-tree and clip-content capabilities, then migrate device modes, browser workflows, and
sequencers.

### Phase 6: Remove compatibility architecture

- remove stable semantic fallbacks;
- remove workspace adapters and legacy mode/view authority;
- generalize fill-shaped runtime APIs;
- make core desired state the only product-behavior authority;
- retain only the bounded resource kernel described above.

## Definition of done

The migration program is complete when:

1. Changing any existing Pull mapping, mode, gesture, navigation rule, display layout, or color
   policy requires only a core reload.
2. The stable Push setup constructs resources and physical bindings but no product behavior.
3. Missing or faulted core behavior is inert and reported; no stable semantic fallback runs.
4. All hardware feedback is rendered from authoritative subscribed state.
5. Tests explicitly separate input, requested effects, host advancement, snapshot read-back, and
   rendered output.
6. Every remaining Bitwig restart maps to a permanent canopy change: new Bitwig topology, new
   parent-loaded contract, new physical control/output transport, or expanded bounded capacity.
7. No migrated feature retains child-owned Bitwig objects, shell/framework references, observers,
   callbacks, or threads.

## Assignment checklist

Before handing one item to an implementation agent, specify:

- the exact controls and input kinds;
- every semantic variant that must be preserved;
- the installed snapshots, effects, and output lanes it may use;
- any approved reusable canopy addition;
- whether one first-run restart is expected;
- explicit exclusions;
- deterministic test cases;
- the live Bitwig/Push smoke test;
- which in-flight branches or files must not be touched.

If those fields cannot be filled in, the task is not yet small enough to delegate efficiently.
