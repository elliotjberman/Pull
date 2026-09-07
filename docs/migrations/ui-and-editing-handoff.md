# UI and editing migration handoff

Start from Core API 50 / Bitwig API 25. Follow the [capability audit](../reloadable-core-migration-guide.md)
and [remaining checklist](../reloadable-core-migration-roadmap.md); core-owned page entry/return
does not imply the page body has migrated. Choose one complete action-and-feedback slice per PR.

## Work to migrate

| Family | Include | Prerequisite |
| --- | --- | --- |
| Browser | Filters, results, selection, audition, commit/cancel; reuse `BrowserPageNavigation`. | Bounded observations and operations; exact insertion/replacement destination. Raw activity alone is insufficient. |
| Settings/pages | Scales/Layout, Repeat, Fixed Length, User, remaining physical Ribbon behavior, Crossfade, Track/Layer Details; preferences, modifiers, touches, rows, display and lights. | Missing state/effects from the roadmap. Persistence may remain mechanical in shell. |
| Color | Target, inherited grid workflow, confirm/cancel and exact return. | Target alignment and native-note suppression; exclusive command ownership does not silence `NoteInput`. |
| Musical layouts/editing | Note/Clip and melodic/polyphonic/Drum sequencers; paging, selections, edits, playing feedback, pressure, scenes and clip length. | Bounded note/step/clip windows, identities, read-back and primitive edits reusable across layouts. |

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
  `core.ui.component` choices/toggles/rings/parameter values, and pure `core.ui.page` families.
  Info, Setup and Ribbon settings use these components; Setup/Ribbon still need integrated live validation. Extend the production
  visual catalog with normal and awkward states when adding a repeated pattern. Browser lists and
  sequencers need suitable models, not a universal configurable UI schema.

## Acceptance

Audit permanent bindings, providers and every semantic variant. Expose missing bounded primitives,
then migrate the full action/feedback slice and delete its legacy policy. No new stable fallback.
Views own input/targets/effects; pure renderers consume values and produce output.

Follow [TESTING](../../TESTING.md): real routed input, separately advanced host state and output;
binding changes, cancellation, fresh gestures, delayed read-back and replacement. Verify direct
Bitwig methods against the API 25 JAR and run the deprecation-enabled package. Any required shell
expansion needs matched-build live validation under the uninterrupted live lease. An earlier
build's smoke pass cannot validate a later migration.
