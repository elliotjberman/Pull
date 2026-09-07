# Views, pages and composition

This is the current Core API 50 / checkpoint schema 6 view contract. [ARCH](../ARCH.md) maps the
implementation and records live activation status. The [roadmap](reloadable-core-migration-roadmap.md)
identifies remaining stable families; page ownership does not imply their bodies have migrated.

## A view has a fixed footprint

A `ControllerView` owns behavior over named `SurfaceArea` regions. Its `ViewProfile` declares
required claims, fixed optional facets and the selected facet IDs. `ViewFacet` groups a coherent
optional footprint, such as the upper Session scene keys. The compiler snapshots each profile and
rejects invalid ownership. Configuration cannot remap callbacks onto raw controls.

The source interfaces are the authority:

- [`ControllerView`](../pull-core/src/main/java/de/mossgrabers/pull/core/view/ControllerView.java):
  subscriptions, parameter banks/bindings, semantic actions, targets, lifecycle and output.
- [`ViewProfile`](../pull-core/src/main/java/de/mossgrabers/pull/core/view/ViewProfile.java) and
  [`SurfaceArea`](../pull-core/src/main/java/de/mossgrabers/pull/core/view/SurfaceArea.java): fixed
  claims and physical regions. View/profile IDs are strings.
- [`Page`](../pull-core/src/main/java/de/mossgrabers/pull/core/view/Page.java): immutable page
  definition, separate from navigation state and presentation values.

## Claims and regions

| Claim | Meaning |
| --- | --- |
| `OBSERVE_INPUT` | Receives core input alongside established stable behavior. Multiple observers may coexist. |
| `EXCLUSIVE_INPUT` | Core owns the routed control; its permanent stable binding is semantically inert. |
| `DIRECT_INPUT` | Core input arrives through an existing permanent semantic route. |
| `MUSICAL_INPUT` | Owns native NoteInput translation for a fixed pad footprint, independently of command and RGB ownership. |
| `OUTPUT` | Core supplies complete replayable display/light output for the claimed region. |
| `STABLE_ADAPTER_INPUT` / `STABLE_ADAPTER_OUTPUT` | Explicit unmigrated behavior, requiring a declared stable adapter facet. Frozen debt, not a choice for new views. |

There may be only one owning command-input claim and one output claim for an atomic area.
Native musical ownership is validated separately. Observing input or claiming output does not
silence native notes. Stable facet/claim validation is currently one-way; the remaining
[bidirectional validation gap](findings/stable-facet-claim-coupling.md) is explicit.

Areas cover the eight encoders/touches, parameter display, bottom strip, both soft-key rows,
full/upper/lower pad grids and drum subregions, scene groups, arrows/page/octave navigation,
touch strip and individual migrated global controls. Grid coordinates use `(column, row)` with
row zero at the bottom, matching Push MIDI layout. New areas must describe reusable physical
footprints, never workspace-specific coordinate fragments.

A grid command claim includes pad edges, strike velocity and per-pad pressure. Pressure follows
the admitted pad owner. Aggregate channel pressure has no pad identity and uses a separate
surface claim; each receiving view sees only its own admitted pads.

## Declaring pages and backgrounds

`ControllerPages` currently declares supported compositions in Java. For example, the ordinary Track page
is assembled from a parameter body and current-bank footer:

```java
new Page(PageId.TRACK,
    List.of(new TrackMixerControlsView(trackState, true), normalFooter),
    mixerNavigation);
```

`Page` stores identity, fixed view instances, optional navigation and parameter indications.
`ControllerPageCompositions` compiles the finite page/background pairs. A background that already
owns arrows omits the page's optional navigation. It retains the actual Session/Drum/Note/ribbon
instances so a page replacement does not restart an unchanged musical view.

[ARCH](../ARCH.md) inventories installed backgrounds and geometry. The shell preserves Session
offsets across its two shapes and gives launcher feedback only to the active bank. `DrumFillView`
is directly composed so the router sees its target/cancel hooks. Configuration such as YAML may
construct the same page and view definitions, with the same claim, target and lifecycle validation.
No configuration loader is implemented yet.

`CompiledWorkspace` expands profiles/facets, validates claims and parameter/action bindings,
merges subscriptions and installed-bank requests, and produces one complete `CoreResult`.
Declaration order cannot decide ownership. A conflicting ordinary owner fails validation;
temporary whole-grid/display overlays use explicit separate planes, not implicit overlap.

## Target-bound input

Follow the [interaction lifecycle](interaction-lifecycle.md): declare exact targets and cleanup;
unchanged bindings survive overlays, changed bindings cancel without ordinary END dispatch.
Views declare `parameterTouchControls`; named slots supply aligned parameter targets. Only active
views receive normal reconciliation/input/output. The shared contract handles companion motion,
modifier filtering and cleanup observation; it does not create missing host capabilities.

Deferred semantic actions capture intent at BEGIN. A LONG-triggered navigation control may enter
the parameter barrier at BEGIN, so its short tap can wait too. Physical modifier consumption must
remain immediate and separate from deferred host effects.

## Parameters and native music

Named parameter targets and cleanup addressability follow the
[target contract](findings/parameter-target-proxy-coupling.md). Contradictory independently sampled
owner/page state cannot authorize display, touch or writes. The runtime releases omitted touches
before effects, then acquires desired touches; ordered acquisition supports reset → touch → enable.

Snapback captures an authoritative baseline and restores before navigation through the semantic
parameter barrier. The router resolves the gesture's intent at BEGIN. Frozen legacy commands
publish their actual semantic consequence into that same barrier; an EXCLUSIVE route suppresses
stable dispatch before it can queue a fallback. Remaining target and precision limits are in
[the parameter finding](findings/parameter-target-proxy-coupling.md) and
[Snapback limits](findings/snapback-v1-limitations.md).

A native key/velocity map is a complete 128-entry value. Enabled notes are restricted to physical
Push pads 36–99 and the producing view's `MUSICAL_INPUT` footprint; a map cannot enable a pad that another view
owns for controller input. Owned silence suppresses the
native map; unowned output relinquishes it to the latest frozen baseline. Normal translation changes wait for musical-input idle. Route loss/failure silences and neutralizes
immediately; normal exit relinquishes the layout before waiting to detach. Applied-map read-back
proves configuration, not note playback.

The selected-track Note route uses a private selection-following cursor aligned with rendering
state. The parent attaches before activating a layout; exit relinquishes the layout, waits for
physical idle, neutralizes parent-owned MIDI and detaches. Unchanged desired state does not churn
the route. Musical input, command dispatch and learned hardware actions remain separate paths;
[TESTING](../TESTING.md) describes what browser injection can and cannot prove.

## Navigation and legacy projection

`PageNavigation` owns selected/previous references and one temporary owner. Only its exact token
can return; a newer owner invalidates an older return even when the page names match. Local page
selection commits after semantic-action admission. It does not wait for a synthetic Bitwig mode
acknowledgement. Track/project values and musical destination changes still require host read-back.
Master replaces the page over the exact selected composition, retaining grid and Note routing.

`DesiredControllerState.page` publishes revision, selected/previous references, optional temporary
owner, acknowledged legacy-request prefix and parameter indications. References are CORE (opaque
ID), LEGACY (installed body) or NONE. `ControllerLayoutSnapshot` mode is a compatibility/debug
projection, not navigation authority. `LegacyPageAliases` translates frozen names outside page
definitions and rendering. New core pages require no stable alias.

`PushControllerPageManager` projects CORE IDs through one inert `CorePageMode`. Its remaining
legacy callers enter a 64-request contiguous inbox. Each request carries its origin page revision,
temporary token and sequence. Core reduces ordered requests through the same parameter barrier,
rejects stale origins and acknowledges only a dispatched prefix. Exact page references preserve
returns to core IDs unknown to the shell.

The inbox has several lifetime guarantees needed by those callers:

- Captured temporary holds pair by initiating request sequence; END closes only that hold's core
  token, while CANCEL retires the handle without navigating. Queued toggles remain ordered.
- A failed conditional entry cannot start restoration. It still waits behind earlier deferred
  work, then rechecks its condition at dispatch.
- Projection publishes state before callbacks. Old-body deactivation retains the old origin;
  new-body activation sees the new projection. Callbacks enqueue instead of recursively entering
  core, and the manager tracks the actually entered body separately from the visible projection.
- Pending inbox entries, temporary handles, notifications and projection fence healthy replacement.
  Quarantine retires them and invalidates callback epochs; an unhealthy consumer accepts no work.
- Parent-owned `retiredSequence` includes acknowledged or abandoned requests. Startup rebases from
  it independently of checkpoint compatibility. The prefix remains available when pending-request
  sampling is unsubscribed. A discarded checkpoint cannot replay retired page requests.

`BrowserPageNavigation` observes raw Browser activity and transition generation, then owns entry
and exact-token return through this barrier. A browser that closes before deferred entry cannot
leave a stale page. Browser filtering, results and operations remain in its inherited body.
The inbox is compatibility for those bodies, not a second page API for new features.

Schema 6 checkpoints store page references/history, latched temporary ownership, Track Mix/I-O/send
state and playback-owner state. They never serialize executable continuations or half a physical
gesture. On startup without a compatible checkpoint, the initial legacy alias supplies the page;
later projected modes cannot overwrite core navigation.

## Presentation and hardware output

Feature views project observed host data and controller-local state into immutable values in
`core.ui.page`. Family renderers consume those values plus `PageStyle` and family styles and return
`PageVisuals`: display scene and row lights. Renderers cannot look up targets, acquire resources,
navigate or emit effects. Shared primitives cover recurring parameter, toggle and footer drawing;
Browser lists and editing grids need suitable models rather than a universal UI schema.

The base display is 960×160. Split pages use a local 960×143 parameter region and 960×17 bottom
strip. The compiler rejects incomplete split output, unclaimed fragments, complete-scene overlap
and primitives outside their viewport. It wraps each accepted fragment in a renderer-enforced clip.
Temporary full-display/grid overlays have explicit ownership and reveal the underlying base when
removed. Core chooses content, geometry, colors and timing; shell rasterizes, clips, translates the
palette and transmits through the existing output lane.

Parameter values and host-derived lights come from later authoritative snapshots. Effect submission
cannot supply optimistic parameter text or a selected state. Where Bitwig exposes a native toggle
without visibility read-back, the UI does not invent an observed selection. Missing/faulted migrated
output stays blank/inert. Rejected replacement candidates may leave the previous valid core active.

Current feature ownership and capacities live in [ARCH](../ARCH.md), migration prerequisites in
[the roadmap](reloadable-core-migration-roadmap.md), and live coverage in the
[current smoke record](migrations/session-core-live-smoke.md). Keep per-feature behavior in
its core implementation and behavioral tests rather than duplicating it in this contract.

Shared display components and the production visual catalog live in the [UI library](ui-component-library.md).
Info, Setup and Ribbon settings use the same page and interaction lifecycle; its subscribed hardware tuple is described in
[ARCH](../ARCH.md#pages-and-input).
