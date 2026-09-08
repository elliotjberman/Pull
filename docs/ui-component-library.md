# Pull UI component library

The reloadable core owns ordinary Push page rendering through a reusable UI library. Components
consume immutable presentation data; the offline catalog renders those same components and pages.
The optional Clip piano roll remains unchanged and deferred. Rendering migration does not transfer
the remaining legacy actions, parameter providers or hardware-light behavior.
The physical Color picker also remains unchanged; its drawing and selection must migrate together
under the [handoff TODO](migrations/ui-and-editing-handoff.md).

## Responsibilities

| Layer | Owns | Does not own |
| --- | --- | --- |
| `core.ui.PageStyle` | Shared display dimensions, column geometry and neutral palette | Page selection or host state |
| `core.ui.component` | Choices, lists, toggles, meters, sliders, response curves and bounded text/value drawing | Physical input, host lookup, target acquisition or effects |
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
| `ChoiceCell` / `OptionColumn` | Left-aligned label and 3 px full-height marker; selected/dim colors, no background fill. Empty or unavailable choices stay blank. Display selection is independent of each page's physical light policy. | Settings, Browser, Scales/Layout, Fixed Length, Add Track, Device and editing menus |
| `TextList` | Up to eight visible rows, bounded text, observed selection and optional trailing detail field | Browser results/filter counts, Scales and Note Repeat |
| `Toggle` | One shared on/off geometry, with supplied position and resolved color | Macro Boolean parameters, Master audio engine and editing Boolean values |
| `RingMeter` | Normalized value and explicit family geometry/colors produce a dotted meter | Mixer, Macro, Accent, Setup, Note Repeat, Device and editing controls |
| `ParameterValue` | Typed value/unit content fits within explicit separate fields | Mixer, Macro, settings, Device and editing controls |
| `VerticalMeter`, `FaderMarker`, `BipolarSlider` | Supplied levels, positions, bands and colors within explicit bounds | Global mixer and shared mixer cells |
| `ResponseCurve` | Bounded normalized samples with explicit width, height, stroke and supplied color | Setup calibration graph |
| `MixerDisplayScene` | Parameter-cell assembly, including knob/fader/pan and observed modulation | Track, global Volume/Pan/Sends, Master and Metronome |
| `TrackFooterRenderer` | Track label/icon, selection contrast and inactive treatment, plus observed row feedback | Current-bank footer, Session footer and Master |
| `PlaybackRippleRenderer` | Pure display/pad drawing from supplied progress and color | Existing project-playback animation and catalog frames |

Family styles retain intentional differences in geometry and color. Macro and mixer display-string
interpretation remains explicit in those families; sharing a drawing component does not make a
parameter name, formatted string or wrapper into a host target identity. Long Macro names and values
now use bounded text fitting instead of spilling into adjacent columns. Components do not insert
their own clip scopes; the compiler owns those scopes when display regions compose.

Info composes shared choice cells for its configuration menu and bounded text primitives for
firmware, board revision and serial number. Aligned three/two/three-column fields replace manual
spacing. Its feature view formats raw observed hardware values; the renderer receives only the
presentation strings and availability. Missing identity shows a waiting message while Info/Setup
navigation remains available. This field layout does not introduce a separate widget framework. Setup uses the same configuration
tabs and shared parameter/ring drawing, plus a response curve from observed hardware settings.
Ribbon uses the shared choice cells and option-row geometry; its numeric CC cell and quick-select
actions preserve the existing distinctions between display selection and physical light feedback.

For the remaining ordinary pages, API 54's `CONTROLLER_PAGE_DISPLAY` subscription carries raw
Device, Option and Editing observations under the observed mode ID. Core projectors format those
values and assemble complete pages; no host objects or actuator authority cross this rendering
boundary. In particular, note feedback reads host-observed values separately from the optimistic
working copy used by legacy note editing.

Track Details also observes the selected bank track or Master's raw `actionTargetId`. Its display
shows “Waiting for track target...” when that identity is missing or differs from the displayed
cursor, including a pinned cursor. The frozen physical lights still read that cursor; this display
check does not change their policy or migrate the legacy actions.

To add a page, first complete the [capability audit](reloadable-core-migration-guide.md). Project
its subscribed values into a family presentation, assemble existing components, and declare the
complete action/output footprint through a view. Add a new component only when real repeated needs
justify it. Reuse the established list, mixer and editing families without adding a universal UI
schema. Keep meaning in core projection and reusable drawing in the library.

## Typography

UI text uses Lato. The installed Bitwig 6.1 bitmap renderer supplies Lato Regular to Pull's drawing
context; pages choose sizes through their family styles and do not override the face. The catalog's
`CatalogTypography` loads the same Lato Regular file for text measurement and embeds it in every
SVG, including standalone exports. Catalog headings use Lato Semibold; other gallery text uses
Regular. This replaces the former mix of logical Sans Serif, browser defaults and system UI fonts.

Font files are read from the installed Bitwig resources without launching Bitwig. For another
installation location, set `PULL_UI_FONT_DIR` to a directory containing `Lato-Regular.ttf` and
`Lato-Semibold.ttf`. Missing or mismatched font files stop generation instead of silently changing
the measurement font. Font data is embedded only in generated output, so previews remain usable
without that installation afterward. Host rasterization may still differ from browser rendering.

## Offline catalog

Run from the worktree root:

```bash
tools/ui-component-catalog
```

The command builds offline and prints a local HTML path under `pull-core/target/ui-component-catalog`.
The searchable sidebar keeps **Components** and **Views** separate and shows one selected story.
Each story has a hash permalink; browser back/forward returns to previous selections. Search filters
the current section, and the mobile Browse control opens the same navigation.

**Components** shows individual choices, lists, toggles, meters, sliders, curves and parameter values
at their intrinsic size. Related meter, fader and slider states appear together for comparison.
The nearby shared color picker (or six-digit hex input) changes supplied component colors. Examples vary
state, value and text rather than duplicating every color. Neutral choices and ring tracks retain
their intended colors.
The SVG link opens the component with the current color and embedded Lato font. These controls work
in the generated HTML without a server; complete view fixtures retain their supplied colors.
**Views** shows complete known screens: Master, Track/global mixer, project macros, settings,
Device/Chains/layers, User, Browser, Scales/Layout, Fixed Length, Repeat, Add Track and ordinary editing
pages. Display content is separated from labeled upper/lower hardware-button rows. Button
lights align to the display columns; dashed buttons have no light state from the view, while black
buttons are off. Partial display regions retain their own height. Future custom plugin views belong
here once they have a production renderer; their reusable controls belong in Components.
Use it to inspect long names, missing values, unavailable/selected choices, touched controls and
value extremes before a live smoke. The catalog is a visual preview; its font rasterization and
fixture data do not establish Bitwig read-back, hardware pixels or gesture behavior.
Fixtures include normal and unavailable observations, value limits, long browser names with retained
hit counts, channel/device variants, editing pages and observed hardware identity.

This catalog is the shared offline visual validation path. The mixer text-stress regression and
catalog use the same input fixture; the former separate mixer PNG renderer has been removed.
The gallery shell lives in `tools/ui-component-catalog-app/index.html`, `catalog.css` and `catalog.js`;
the generator embeds them in the standalone artifact alongside the production-rendered stories.
Hardware buttons and screen frames use the debugger's shared `push-hardware.js` and
`push-hardware.css`. The generator embeds these presentational helpers, keeping the gallery
standalone without loading debugger input or live-state code.
The catalog replays production drawing commands, binding only the explicit component color input
to a CSS property; it does not redraw component geometry in JavaScript. Inline SVG IDs are scoped
per specimen so text clipping and icon masks cannot cross between previews. Additional inputs for
complete view presentations and debugger integration remain future work in this same catalog.

Keep representative fixtures with their production consumers. Test observable requests, later
host read-back and feedback through the existing routed tests; avoid snapshot hashes that merely
freeze a renderer's command list. Follow [TESTING](../TESTING.md) for live evidence. The initial
library extraction was core-only; Info, Setup and Ribbon added parent-loaded hardware/settings
contracts, and API 54 adds the bounded raw page observations. Final integrated package and live
validation remain pending.
Gallery review covers persistent navigation, search, direct links, component colors and tall
specimens on desktop and a 390px viewport. See
[ARCH](../ARCH.md) for the current source API, required restart, and scoped installed-build status.

## Next migration boundary

The [UI/editing handoff](migrations/ui-and-editing-handoff.md) tracks remaining actions/providers/lights
and the explicit piano-roll deferral. Info, Setup and Ribbon settings already own their complete
core control slices. Info's subscribed hardware tuple is described in
[ControllerHardwareSnapshot](../pull-core-api/src/main/java/de/mossgrabers/pull/core/api/ControllerHardwareSnapshot.java);
it does not guarantee physical connection or recover values discarded by the existing parser.
Remaining physical Ribbon behavior is frozen. Ordinary Scales/Layout, Repeat, User and Fixed Length
displays now use the library while their controls remain frozen. Fixed Length action migration
still depends on Session create/launch/overdub behavior. Rendering a page does not supply those
effects or prove its targets.
