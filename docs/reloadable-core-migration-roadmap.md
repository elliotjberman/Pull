# Reloadable-core migration roadmap

The goal is to move controller behavior into core while keeping initialization-owned Bitwig
resources in a bounded shell. [ARCH](../ARCH.md) inventories the current implementation;
[the views contract](views-api-design.md) defines composition and page ownership;
[the migration guide](reloadable-core-migration-guide.md) defines the capability audit and cutover
workflow. Do not add new policy to frozen stable adapters.

## Migration part 1

[PR #40](https://github.com/elliotjberman/Pull/pull/40) establishes core page ownership and migrates
the controls listed below. It does **not** complete the shell-to-core migration. This document is
the continuation checklist; owning navigation to a legacy page does not migrate that page's body.

Working contract: Core API 46, checkpoint schema 6, Bitwig API 25. The latest installed/live-tested
production source is `11e33477`; subsequent cleanup has not been deployed or live tested.
[The validation record](migrations/core-page-ownership-live-smoke.md) separates those states.

| Slice | Current boundary |
| --- | --- |
| Page ownership | Core `PageId`/`Page`, navigation/history, exact temporary tokens, retained backgrounds, presentation models/renderers/styles. One inert shell footprint projects arbitrary core pages; frozen callers use a 64-request sequenced inbox. |
| Original-view release | Core captures edge receivers and deferred action ownership. Current pages render; retained owners supply only continuation data, ticks and exact touches. General motion association remains an [active finding](findings/core-continuous-input-capture.md). |
| Mixer pages | Project Macros, normal/VS Track, Volume, Pan, eight Sends and Master use named parameters, exact touches, read-back, core menus/footers and output. |
| Global controls/pages | Play/Record, Mute/Solo, Tap Tempo, Undo/Redo, Track/Mix, Master/Frame, Accent, Metronome/Automation, migrated arrows and their feedback are core-owned. |
| Drum/Note | Selected-track applicability and Note/Layout policy, playable-pad pressure/lights, rates/roll, fills, octave/native maps and raw strip behavior are core-owned within the installed geometry. |
| Session | Grid, scenes and page-button mechanics remain frozen adapters. Stop remains OBSERVE for the adapted Stop-plus-pad chord; its direct stable command is inert. |
| Optional Session observation | `SESSION_CLIPS` is installed and useful, but has no production product consumer. It publishes a bounded slot/scene window only when explicitly subscribed; it does not establish action ownership or a release acknowledgement. |
| Other legacy families | Device/Chains/layers, Browser body, Crossfade, Details/Color, configuration, note/clip/sequencer editing and remaining providers remain migration work. Page compatibility is not their migration. |

## Installed capacity is not arbitrary project access

The shell eagerly creates its physical registry, interested values and finite proxy topology.
Subscriptions gate snapshot work, not resource construction. Current reusable capacity includes:

- Session shapes 8 tracks × 8 scenes and 8 × 4; current-bank observation admits the two installed
  main windows and effect bank, at most three registered banks with eight visible tracks each.
- Seventeen named parameter banks, at most 131 slots: ACTIVE compatibility, project/device remotes,
  selected-track volume/pan and eight sends, visible-track volume/pan/eight send columns, Master/Cue
  and globals. Snapback's ten-target bound is a separate interaction limit.
- Device framework windows: eight siblings, eight displayed remote-page names, eight remotes,
  eight layers, sixteen drum pads and eight sends per layer/pad. Reordering additionally uses a
  100-device channel bank; nested correctness still needs characterization.
- Complete base display, temporary grid/display overlays, generic registered-button and pad lights,
  native key/velocity maps within declared physical footprints, and permanent semantic MIDI
  mapping endpoints. These transports grant no product ownership on their own.

New banks, observers, permanent bindings, capacities, parent API shapes or output ownership require
a shell build and restart. Behavior inside the installed canopy reloads in core.

## Remaining work and prerequisites

The [UI/editing handoff](migrations/ui-and-editing-handoff.md) makes the reusable
[component library](ui-component-library.md) an explicit deliverable of each UI slice. The current
library serves existing core pages; it does not mark the remaining legacy families complete.

Keep these items open until their complete behavior and feedback live in core and the corresponding
stable policy is deleted. The sections below record the prerequisites and known limits.

- [ ] Session grid, scene and page-button behavior, including Stop-plus-pad and launcher retirement.
- [ ] Device, chain and layer pages: establish exact target identity before migrating their controls.
- [ ] Browser contents and operations; its page entry/return lifecycle is already core-owned.
- [ ] Crossfade, Track/Layer Details and Color workflows.
- [ ] Configuration screens, remaining musical layouts, and clip/note/sequencer editing.
- [ ] Remove the remaining facet adapters, legacy page aliases/inbox consumers and parameter
  providers as their last behavior migrates. Keep generic resource and transport mechanisms.

### Session actions and retirement

Keep the optional observation domain and frozen grid owner until the complete pad/scene action and
feedback slice is ready. Preserve configured empty-slot behavior, modifier precedence, main/alternate
launch and release, record/create sequencing, duplicate source lifetime, birds-eye navigation,
Stop chords, scene variants and read-back-driven lights.

The unresolved issue is reuse of an exact launcher actuator after release: API 25 release calls
return void and may leave playback unchanged. Neither a tick nor `requestFlush()` acknowledges
DAW completion. [The bounded location design](migrations/session-launcher-location-design.md)
records the proposed pool and the decision required before exclusive cutover.

### Device, chain, layer and Browser families

First establish a bounded device handle with acquisition/read-back and live equality fences.
Production `SpecificDeviceImpl.getID()` returns blank; `SELECTED_DEVICE_REMOTE` consequently excludes
those targets. API 25 exposes channel IDs and proxy equality observations, not a device-instance
UUID. A name, position, Java wrapper or Drum candidate-path ID cannot replace that identity.

A private retained cursor with initialization-created equality observations is a candidate, not a
proven guarantee. Verify pinning, actual channel ownership, duplicate names, replacement/deletion,
remote-page rebinding and acquisition latency live before relying on it. A cursor's creation channel
does not prove the owner of a separately pinned device. See the
[parameter-target finding](findings/parameter-target-proxy-coupling.md).

Then expose subscribed Device context, sibling/page and layer/pad windows; named layer parameter
roles; and primitive identity-fenced navigation, Boolean and UI effects. Migrate the complete entry,
held return, Params/Chains subpages, row actions, layer preferences, pin/window controls, display
and lights together. Follow actual hardware parameter bindings: a no-op `onKnobValue` does not
prove that a bound Device Chains encoder is inert. Characterize its existing subpage/light-index
quirks before changing them. Browser navigation lifecycle is core-owned, but filtering/results,
audition and insertion policy still live in its legacy body.

### Crossfade, Details and Color

Crossfade and MIDI-channel controls historically step once per callback. Summed relative input
loses callback count and ordering around clamps. Agree a reusable bounded input contract or an
explicit behavior change before migrating them; sign-of-sum is not exact parity.

Track/Layer Details need actual monitoring state separately from monitor mode, absolute Boolean
writes, cursor pin, and observed MIDI edit-channel settings. Their action target and rendered cursor
can disagree under pinning; expose both and fail closed. Track Details touches are intentionally
inert. Color selection is a full-grid workflow with target, native-note silencing and exact return
ownership; do not hide it behind a feature-shaped stable callback.

### Configuration, musical layouts and editing

Migrate Scale/Scales Layout, Repeat/Ribbon, Fixed Length, Setup/Info, User and remaining note-layout
settings as complete action/feedback slices. Configuration persistence may stay mechanical in shell.
Arbitrary musical geometry needs the [documented canopy expansion](findings/custom-musical-surface-geometry.md).

Clip and sequencer editing requires bounded note/step/clip windows, selection/page identity,
read-back and primitive edits. Reuse those capabilities across Drum, melodic, polyphonic, scene and
clip-length workflows; do not expose the inherited mode object graph or unbounded project scans.

## Cross-cutting work

- General async reload quiescence is **explicitly parked by the user**. Keep the
  [active investigation](findings/core-reload-quiescence.md); existing gesture and Snapback gates do
  not cover every queued toggle or operation. Do not treat this cleanup as implementing a drain.
- Parameter target identity/addressability, Snapback precision, learned-MIDI lifecycle, musical
  geometry, facet/claim coupling, logical timer execution and live provenance retain their
  [active findings and removal criteria](findings/README.md).
- Migrate `WorkspaceView`/facet adapters, legacy command/mode/view policy, physical parameter-provider
  recipes and setup/surface suppliers only as their complete semantic slices move. Generic input,
  proxy, validation, effect and hardware transport remains stable.
- Keep compatibility costs and explicit product deviations in the
  [shortcuts/review ledger](migrations/migration-shortcuts-and-friction.md), not separate audit diaries.

## Completion

For each slice, audit reachable inputs and feedback, identify missing generic capability, then
implement core policy and delete its stable policy together. Test command submission separately
from later host advancement; cover target changes, release, deferred work, fault and replacement.
Verify exact Bitwig API methods and deprecations, then run the required package and leased live
checks described in [TESTING](../TESTING.md). An unverified or deliberately deferred variant stays
explicitly out of scope. The migration ends when no frozen product-policy adapter remains.
