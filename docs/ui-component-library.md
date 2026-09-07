# Pull UI component library

The reloadable core owns a reusable UI library so layout, legibility and feedback can improve
across pages together. Components are actual production consumers of immutable presentation data;
the offline catalog renders those same components and pages.

## Responsibilities

| Layer | Owns | Does not own |
| --- | --- | --- |
| `core.ui.PageStyle` | Shared display dimensions, column geometry and neutral palette | Page selection or host state |
| `core.ui.component` | Choice cells, toggles, ring meters and bounded parameter-value drawing | Physical input, host lookup, target acquisition or effects |
| `core.ui.page` | Family presentation records, styles, page assembly and track footers | Navigation, gesture lifetime or mutable host objects |
| Feature `ControllerView` | Fixed surface claims, observed-state projection, exact input targets and requested effects | A second private copy of shared rendering |
| Workspace compiler | Disjoint composition and display clip scopes | Arbitrary control remapping |

Components receive values and local positions/styles. A page maps component feedback to the
physical output it claims. Selected and enabled visuals come from observed state, never merely
from a submitted action. Missing or unknown host state must not invent a selection. The shared
interaction lifecycle remains a separate prerequisite for new target-bound gestures; this library
does not integrate or replace production input routing.

## Components and real consumers

| Component | Contract | Consumers |
| --- | --- | --- |
| `ChoiceCell` | Label, availability and observed selection produce one fitted cell and matching light color. Empty/unavailable choices are blank and off. | Automation, Metronome, Frame and Info |
| `Toggle` | One shared on/off geometry, with page-supplied position and resolved color | Macro Boolean parameters and Master audio engine |
| `RingMeter` | Normalized value and explicit family geometry/colors produce a dotted meter | Mixer, Macro and Accent |
| `ParameterValue` | Typed value/unit content fits within explicit separate fields | Mixer, Macro and Accent |
| `MixerDisplayScene` | Parameter-cell assembly, including knob/fader/pan and observed modulation | Track, global Volume/Pan/Sends, Master and Metronome |
| `TrackFooterRenderer` | Track label/icon, selection contrast and inactive treatment, plus observed row feedback | Current-bank footer, Session footer and Master |

Family styles retain intentional differences in geometry and color. Macro and mixer display-string
interpretation remains explicit in those families; sharing a drawing component does not make a
parameter name, formatted string or wrapper into a host target identity. Long Macro names and values
now use bounded text fitting instead of spilling into adjacent columns. Components do not insert
their own clip scopes; the compiler owns those scopes when display regions compose.

Info composes shared choice cells for its configuration menu and bounded text primitives for
firmware, board revision and serial number. Aligned three/two/three-column fields replace manual
spacing. Its feature view formats raw observed hardware values; the renderer receives only the
presentation strings and availability. Missing identity shows a waiting message while Info/Setup
navigation remains available. This field layout does not introduce a separate widget framework.

To add a page, first complete the [capability audit](reloadable-core-migration-guide.md). Project
its subscribed values into a family presentation, assemble existing components, and declare the
complete action/output footprint through a view. Add a new component only when real repeated needs
justify it. Browser lists and sequencer grids may establish their own families without a universal
UI schema. Keep meaning in the feature view and reusable drawing in the library.

## Offline catalog

Run from the worktree root:

```bash
tools/ui-component-catalog
```

The command builds offline and prints a local HTML path under `pull-core/target/ui-component-catalog`.
Its 28 examples use production renderers with value fixtures and show their display output and row lights.
Use it to inspect long names, missing values, unavailable/selected choices, touched controls and
value extremes before a live smoke. The catalog is a visual preview; its font rasterization and
fixture data do not establish Bitwig read-back, hardware pixels or gesture behavior.
Info adds known identity, observed transport limits (including a signed serial), and waiting-state
examples to this same catalog.

This catalog is the shared offline visual validation path. The mixer text-stress regression and
catalog use the same input fixture; the former separate mixer PNG renderer has been removed.
The live debugger remains unchanged and continues to show actual hardware output. Future catalog
work may add Storybook-style inputs for track color, names, values and state, feeding these same
Java presentations. Interactive controls and debugger integration are deliberately deferred.

Keep representative fixtures with their production consumers. Test observable requests, later
host read-back and feedback through the existing routed tests; avoid snapshot hashes that merely
freeze a renderer's command list. Follow [TESTING](../TESTING.md) for live evidence. The initial
library extraction was core-only; Info adds a parent-loaded hardware contract. See
[ARCH](../ARCH.md) for the current source API, required restart, and unchanged installed-build status.

## Next migration boundary

The [UI/editing handoff](migrations/ui-and-editing-handoff.md) remains the checklist. Info's complete
action/feedback slice now lives in core, with the subscribed hardware tuple described
in [ARCH](../ARCH.md#core-owned-pages-and-working-contract). The tuple does not guarantee physical
connection or recover values discarded by the existing hardware parser. Setup and the other legacy
pages still need capability audits. Fixed Length additionally depends on Session create/launch/overdub
behavior; it cannot be treated as an eight-choice settings port. These are prerequisites, not
capabilities supplied by this library.
