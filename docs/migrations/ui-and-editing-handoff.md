# UI and editing migration handoff

Working source is Core API 54 / Bitwig API 25. All ordinary Push display pages now use the shared
core UI library; the remaining display families consume bounded raw `CONTROLLER_PAGE_DISPLAY`
observations. Legacy actions,
parameter providers, modifiers and lights remain where not already migrated. Follow the
[capability audit](../reloadable-core-migration-guide.md) and [remaining checklist](../reloadable-core-migration-roadmap.md)
for each remaining complete behavior slice.

The specialized Clip piano roll remains unchanged. TODO: Elliot never used this so deferring how to migrate it to the new framework

## Work to migrate

| Family | Include | Prerequisite |
| --- | --- | --- |
| Browser | Filter/result actions, selection, audition, commit/cancel; reuse `BrowserPageNavigation`. Display already uses shared lists. | Bounded operations with exact insertion/replacement destination; display observations grant no actuator authority. |
| Settings/pages | Scales/Layout, Repeat, Fixed Length, User, physical Ribbon behavior, Crossfade and Track/Layer Details actions/providers/lights. Ordinary displays already use core components. | Missing state/effects and exact targets from the roadmap. Persistence may remain mechanical in shell. |
| Color | Target, inherited grid workflow, confirm/cancel and exact return. Palette output is core-owned; its click gesture is not. | Target alignment and native-note suppression; exclusive command ownership does not silence `NoteInput`. |
| Musical layouts/editing | Note/Clip and melodic/polyphonic/Drum sequencer gestures; paging, selections, edits, playing feedback, pressure, scenes and clip length. | Exact identities and reusable edits over bounded windows. Ordinary Note display already reads host values separately from the optimistic legacy working copy. |

Crossfade/MIDI-channel callback count and clamp order cannot be recovered from summed motion.
Resolve the shared input contract or agree a behavior change before migrating those controls.
New musical geometry must cover native notes, pressure, output and target routing together.

Session is core-owned under its [bounded location contract](session-launcher-location-design.md).
Device/chain/layer identity research and general async reload draining are separate work. Browser replacement or Layer Details may depend on Device identity; leave a
dependent slice unready rather than inventing a name/slot-based identity.

## Reuse

- Use [shared binding cancellation](../interaction-lifecycle.md). A view declares its exact target
  and cleanup; it does not create its own retained/offscreen touch sessions or timeout scheme.
- Follow [views and composition](../views-api-design.md): `Page`, `PageNavigation`, fixed claims and
  `ControllerPageCompositions`. New core pages need no stable enum or page-specific adapter.
- Use the [shared UI library and catalog](../ui-component-library.md): `core.ui.PageStyle`,
  `core.ui.component` choices/lists/toggles/meters/parameter values, and pure `core.ui.page` families.
  Extend the same searchable component/view catalog with normal and awkward observations, keeping
  individual specimens separate from complete screens and linking stories by their hash. Custom plugin
  views belong there when they have production renderers; no parallel debugger or preview harness
  is needed. See the [display cutover audit](ui-library-completion.md) for current bounds.

## Acceptance

Audit permanent bindings, providers and every semantic variant. Expose missing bounded primitives,
then migrate the full action/feedback slice and delete its legacy policy. No new stable fallback.
Views own input/targets/effects; pure renderers consume values and produce output.

Follow [TESTING](../../TESTING.md): real routed input, separately advanced host state and output;
binding changes, cancellation, fresh gestures, delayed read-back and replacement. Verify direct
Bitwig methods against the API 25 JAR and run the deprecation-enabled package. Any required shell
expansion needs matched-build live validation under the uninterrupted live lease. An earlier
build's smoke pass cannot validate a later migration.

API 54 requires a matching shell installation and restart later. This cutover has neither been
installed nor live tested. The clean offline package passes 1,038 tests. The physical Color
route handoff remains unresolved; this checkpoint is not ready for installation. The existing
API 50 Setup/Info smoke remains evidence only for that recorded build.
