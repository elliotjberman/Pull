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

On current `master`:

- drum-fill matching, launch-session policy, gesture state, and twelve fill lights are core-owned;
- Record, Shift+Record, and Select+Record are core-owned;
- VS Live selection and fixed-facet composition are core-owned;
- stable adapters still realize VS Live's Session, Drum, macro, track-strip, display, and navigation
  mechanics;
- drum-grid pressure interpretation is core-owned in the composite workspace, while the permanent
  `NoteInput`, ordinary Bitwig routing, and MIDI neutralization remain stable;
- the practical existing Push input set is normalized by the stable input bridge;
- transport, selected-track, controller-layout, and bounded drum snapshots/effects exist;
- Play and Record lights are core-owned through generic authoritative RGB output;
- the Master page's two button rows and graphics scene are core-owned through complete output
  arbitration; its generic stable scene interpreter contains no Master layout policy;
- a generic sparse 8x8 pad-grid overlay can freeze, temporarily replace, and restore stable pad
  output; animation geometry, color, cadence, and activation policy are core-owned;
- a generic complete 960x160 display overlay can temporarily replace and restore the inherited
  display page; overlay copy, geometry, color, and activation policy are core-owned;
- the twelve drum-fill lights are also core-owned; underlying grid policy and other Push output
  surfaces remain frozen migration debt;
- Shift snapback policy, view-owned physical-to-parameter-slot admission, semantic action
  invalidation, restoration acknowledgement, and navigation ordering are core-owned; stable owns
  named bounded Bitwig parameter banks, exact actuator leases, identity fencing, effect execution,
  command-driven compatibility-intent adaptation, and compatibility-action dispatch;
- VS Live project-macro encoder mapping, relative mutation policy, and snapback admission are
  core-owned. Stable `WorkspaceMode` remains only its touch/delete and display adapter.

Before taking an item, inspect the active branch and in-flight work. This inventory describes
architectural ownership, not a promise that no adjacent PR has changed the exact files.

## Installed state and effect primitives

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

Not every existing transport command is immediately a complete migration. Metronome, Automation,
Tempo, and Play Position combine temporary modes, configuration, notifications, or missing actions.
The migration guide scopes Play as the safe first transport cut.

### Selected track

- activation and group expansion;
- record arm, mute, and solo;
- monitor mode;
- volume and pan;
- stop, return to Arrangement, and create a new clip.

Existing Mute, Solo, and Stop commands also contain all-tracks, master, layer, lock-mode, or
view-dependent variants. Do not request exclusive ownership until those branches are migrated or
explicitly removed as a product decision.

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

Current limitation: stable validation and arbitration accept the twelve fill-pad lights, global
Play/Record lights, the Master page's two button rows and bounded declarative graphics scene, and a
temporary sparse whole-grid overlay plus a complete 960x160 display overlay. Inherited output
outside those lanes remains frozen stable migration debt; do not implement new output behavior
there.

Add bounded complete ownership for the remaining surfaces:

- Push button lights outside the migrated Master rows;
- all 64 grid-pad lights;
- scene lights and non-Master row-button lights;
- touch-strip mode and LEDs;
- the other USB display pages using the installed scene buffer or a deliberately expanded output
  canopy;
- transient notifications with explicit lifetime and replacement rules.

After this expansion, color choice, light meaning, display layout, and notification policy move to
core. Palette lookup, calibrated RGB conversion, USB packet encoding, and physical writes remain
stable.

This is the highest-leverage expansion because nearly every legacy mode/view mixes behavior with
rendering.

### 2. Visible track bank and mixer

Install a bounded visible-track-bank snapshot with stable identities and generations for eight
tracks, including the properties actually rendered by Pull. Add fenced effects for selection,
activation, arm, mute, solo, volume, pan, and bounded sends.

Controller-level Play is now the reference transport migration: its stable command is inert, its
edge is core-exclusive, and core targets the remembered engine-owning project through a bounded
identity-fenced navigate/toggle/return transaction. The lightweight `PROJECT` subscription keeps
that policy available in every workspace without sampling Master VU.

This unlocks:

- VS Live track-selection strip;
- ordinary track selection;
- Track, Volume, Pan, Send, Crossfade, and related mixer modes;
- multi-track variants of Mute, Solo, and Stop;
- authoritative track-strip lights and display output.

Do not confuse this with the existing private selected-track snapshot. A selected target cannot
represent eight visible tracks.

### 3. Session grid

Install a bounded visible Session bank with explicit track/scene offsets, stable slot identity, and
slot state such as existence, content, name, color, selected, playing, recording, and queued.

Add effects for:

- launch and release/stop of a slot;
- launch of a scene;
- selecting a slot or scene where required;
- bounded bank navigation;
- creating a clip if the product behavior requires it.

This unlocks:

- VS Live's upper Session grid and scene keys;
- ordinary `SessionView`;
- clip-slot rendering and launch behavior;
- Session navigation and paging.

The existing drum-fill catalog and actuator lease should eventually become a consumer of a generic
bounded clip/session capability rather than remain a parallel feature-shaped API.

### 4. Complete parameter-view migration and output

API 22 installs named bounded parameter snapshots for active compatibility, project remote,
selected-device remote, visible-track volume/pan, Master/Cue, and globals. Snapshots contain exact target
identity, name, raw and modulated values, authoritative displayed value, step count, and tolerance;
stable applies fenced absolute, relative-change, and reset effects. Project-macro relative input is
the first fully core-owned path.

Still add a safe automation-touch lifecycle before moving touch ownership. The Master page now has
complete display and row-light arbitration, including a core-authored declarative vector scene
executed by a generic stable interpreter. Complete the same boundary for other parameter pages
before moving their rendering. Migrate remaining stable parameter modes to
the named banks and delete the inherited active-window compatibility path when no consumers remain.

This unlocks:

- VS Live project macros;
- User/project parameter modes;
- large portions of device and track parameter rendering;
- removal of core-invisible parameter-provider policy from stable.

The shell owns Bitwig parameter objects and exact actuation. Core owns bank selection, encoder
mapping, and mutation semantics now; touch semantics and non-Master display layouts remain
migration work.

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

Add typed, capability-checked effects and authoritative availability state for undo/redo, browser,
add track/device/effect, duplicate, delete, double, convert, and other application-level actions.

These are conceptually reloadable mappings, but they cannot move safely while the core can only
emit transport, selected-track, drum, MIDI, and fill effects.

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
core. Temporary-mode behavior should become explicit reloadable state rather than an implicit
stable `ModeManager` side effect.

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
- `WorkspaceMode`;
- legacy `Views.WORKSPACE` and `Modes.WORKSPACE` realization;
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

Extend complete light/display ownership to the remaining underlying grid policy, general lights,
display pages, touch strip, and notification output. The temporary whole-grid overlay transport is
already installed. Each added surface needs explicit hardware smoke tests.

This comes before further ordinary behavior changes because output-only feature work is still
behavior. Do not edit an inherited stable renderer while waiting for this phase. A smaller feature
may land a bounded generic output lane early when it is reusable and completes one vertical slice.

### Phase 2: Common performance capabilities

Add, in order:

1. visible track bank;
2. Session grid;
3. remaining parameter-bank contexts beyond the API 22 named canopy.

Then migrate `WorkspaceMode` and `WorkspaceView` completely. Project-macro relative turns have
already moved; automation touch, display output, track strips, Session, and Drum adapters remain
good acceptance targets because their product behavior is specified and exercised in VS Live.

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
