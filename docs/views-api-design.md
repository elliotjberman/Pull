# Views, pages and composition

This is the current Core API 47 / checkpoint schema 6 view contract. [ARCH](../ARCH.md) maps the
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
  claims and physical regions. View/profile IDs are strings; no separate runtime registration language exists.
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

`ControllerPages` declares supported compositions in Java. For example, the ordinary Track page
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

The supported backgrounds include Note, Drum, full Session and VS Live. Plain Session declares
8×8; VS declares an upper 8×4 Session bank plus lower Drum views. The shell eagerly registers
exactly those Session shapes, preserves offsets on a switch and grants launcher feedback only
to the active bank. An undeclared shape fails before activation and needs a shell expansion.

VS initially combines Project Macros, the Session track-selection footer, upper Session grid and
scene keys, Drum play/octave/rate/fill/mapping views, musical route and raw pitch bend. Drum does
not own lower scene keys. `DrumFillView` participates directly in the composition so its target
and cancellation hooks are visible to the router. Other pages replace the parameter region.
There is no YAML loader, dynamic view plugin registry or arbitrary callback mapping layer.

`CompiledWorkspace` expands profiles/facets, validates claims and parameter/action bindings,
merges subscriptions and installed-bank requests, and produces one complete `CoreResult`.
Declaration order cannot decide ownership. A conflicting ordinary owner fails validation;
temporary whole-grid/display overlays use explicit separate planes, not implicit overlap.

## Target-bound input

`InputGestureRouter` applies the shared [interaction lifecycle](interaction-lifecycle.md).
BEGIN captures each receiver's target and semantic intent. A changed or missing binding cancels
that receiver before deactivation. Cancellation emits required cleanup, never an ordinary END.
Motion, pressure, LONG and END from that physical tail cannot reach a replacement or revive after
returning to the old binding. An unchanged visible binding survives a page overlay.

Only active views receive ordinary reconciliation/input/rendering. Cleanup effects retain their
needed subscriptions/banks through submission; resource retirement retains just its required
observations. Deferred actions capture their exact cancellation function, so an older canceled
action cannot clean up a newer gesture on the same control. The view's input projection excludes
canceled controls from modifiers and pressure. Raw host values remain unchanged.

Views declare `parameterTouchControls`; the router owns touch/reset/automation-release lifetime.
Mapped controls resolve through the compiler's aligned named parameter slots. Other controls
provide immutable `InputTarget` contexts; unavailable targets reject admission. The shell freezes
physical disposition and core generation through each edge and its motion/pressure companions.
A core replacement waits for the existing input/deferred-action fences; it does not inherit a
partially held physical gesture. This is not a general asynchronous-operation drain.

## Parameters and native music

Named parameters carry an opaque reference plus classified domain, owner, page and slot/role.
A Java wrapper surviving navigation is not a target identity. Current-bank and selected-track
views join parameter ownership to independently sampled track/project state before rendering,
writing or touching. Temporary disagreement stays blank/inert until later observations align.

Core returns the complete desired parameter-touch set. The shell releases omitted exact leases
before effects and acquires new touches afterward, so Delete reset precedes touch. The ordered
`AcquireParameterTouchEffect` also supports reset → touch → send-enable. A later `touchLeases`
sample proves resource retirement. Cleanup may address a retained exact actuator, but must abandon
and report it if an external rebind made that actuator point elsewhere. It may never clean up the
replacement. Touch retirement does not acknowledge every earlier parameter write.

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
[current smoke record](migrations/interaction-lifecycle-live-smoke.md). Keep per-feature behavior in
its core implementation and behavioral tests rather than duplicating it in this contract.
