# UI and editing migration handoff

This is work for a later session: migrate the remaining UI pages and musical editing behavior
into reloadable core. It is not an implementation or a promise that these families are independent
of input and target capabilities. [Migration part 1, PR #40](https://github.com/elliotjberman/Pull/pull/40)
merged as `8659c2d1`; its current source contract is Core API 46 / Bitwig API 25.
Installed-build provenance remains in the [validation record](core-page-ownership-live-smoke.md).

## Scope and starting points

Use the [roadmap](../reloadable-core-migration-roadmap.md) as the completion checklist and the
[migration guide](../reloadable-core-migration-guide.md) for each capability audit. Trace permanent
bindings in `PushControllerSetup` through commands, providers, state, effects and feedback; a
page's core-owned entry/return does not mean its body has migrated.

- **Browser:** inherited `DeviceBrowserMode`; filtering, results, selection, audition and
  commit/cancel behavior. Reuse `BrowserPageNavigation` for entry and exact return ownership.
- **Settings and other pages:** Scales/Scale Layout, Repeat/Ribbon, Fixed Length, Setup/Info,
  User, Crossfade and Track/Layer Details. Include preferences, modifier variants, touches,
  row actions, display and lights. Configuration storage can remain mechanical in the shell.
- **Color:** trace the caller and inherited grid workflow, including selected target, confirmation,
  cancellation, return page, and suppression of native notes while the grid selects colors.
- **Musical layouts and editing:** Note/Clip, melodic/polyphonic/Drum sequencers, scene and clip-length
  workflows. Audit note/step selection, paging, edits, playing feedback and pressure independently
  of which page happens to be visible.

Choose a complete small slice first, preferably a settings page whose required state/effects are
already available. Keep a checklist in the PR; split subsequent slices when their capability or
live-validation requirements differ. Do not claim the whole inventory as one mechanical port.

## Shared interaction work: decision versus shipping behavior

Concurrent work is an **isolated, offline-testable shared interaction lifecycle**, not integrated
production routing. Part 1 still ships its existing edge capture and target fences. Do not assume
the new lifecycle is available because its source or tests exist.

The user's chosen policy for that work is: **cancel an interaction when its target leaves the
active binding; suppress the remaining physical interaction until a fresh gesture starts**.
Do not silently redirect it to the new target, revive it when an old binding reappears, or create
per-feature held-target/timeout logic to bridge a missing shared contract. Confirm the integrated
definition of gesture end for each input kind before relying on it.

A captured view is not proof that a Bitwig proxy still addresses its old target. Cancellation also
does not authorize cleanup through a rebound proxy. Keep target validation and required touch/MIDI
neutralization explicit. The [continuous-input finding](../findings/core-continuous-input-capture.md)
and [target finding](../findings/parameter-target-proxy-coupling.md) describe current gaps; older
retention proposals there are not permission to override the user's cancellation decision.

## Capability dependencies and boundaries

For every missing capability, name its bounded capacity, selection scope, identity/generation,
subscribed read-back and primitive effects. Reuse existing capabilities first; add the smallest
generic mechanism only when the complete consuming slice is ready. Do not pass legacy mode/model
objects into core or add stable callbacks such as “run this page's action.”

- Browser needs bounded filter/result/selection observations and primitive browsing operations.
  Insertion/replacement must identify the actual destination; raw Browser activity is insufficient.
- Details/Color need the action target and rendered target to agree, including pinning and page
  changes. Color's grid command ownership alone does not silence Bitwig's separate `NoteInput`.
- Crossfade and MIDI-channel controls historically step once per callback. Summed relative input
  loses callback count and clamp ordering. Resolve this through a shared input contract or an
  explicit behavior decision; sign-of-sum is not parity.
- Editing needs bounded clip/note/step windows, selection/page identity, later read-back and
  primitive edits reusable across sequencers. Existing Session observation is not edit ownership.
- New musical geometry must cover native notes, pressure, feedback and target routing together;
  the [geometry finding](../findings/custom-musical-surface-geometry.md) records what is unproven.

**Outside this migration PR:** Session launcher actions/retirement, Device/chain/layer identity
research, and [general async reload quiescence](../findings/core-reload-quiescence.md). Browser
replacement or Layer Details may depend on Device identity; leave a dependent slice unready until
that prerequisite lands. Likewise, a gesture/target gap belongs in shared lifecycle work, not a
local workaround. Do not expand this handoff into those separate projects.

## Reuse the existing core UI structure

Follow [ARCH](../../ARCH.md) and the [views contract](../views-api-design.md). Keep `Page`/`PageId`
definitions, `PageNavigation` state/history, and fixed `ControllerView` claims separate. Reuse
`ControllerPageCompositions`; a new core page does not need a stable enum or page-specific adapter.

In `pull-core/.../ui/page`, build from the existing components:

- Typed immutable presentations and pure renderers, as in `SettingsPagePresentation`,
  `SettingsPageRenderer` and `SettingsPageStyle`, are the pattern for choice/settings families.
- `MixerDisplayScene` / `MixerControlStyle` provide parameter widgets; `ToggleRenderer` supplies
  the shared toggle drawing used by Macro and Master. Reuse them when the visual behavior matches.
- `TrackFooterRenderer` / `TrackFooterStyle` and `PageStyle` provide shared footer treatment and
  display geometry. Preserve compiler-owned clip scopes and explicit full-grid/display overlays.

Grow this library from actual repeated needs. Do not invent a universal UI schema or force Browser
lists and sequencer grids into an unsuitable existing widget. Feature views own input, targets and
effects; presentations carry values; renderers/styles produce display and light output without
host lookup, navigation or acquisition. All new product policy remains in core.

## Cutover and evidence

Migrate a control's complete action **and feedback** together, then delete its legacy policy and
providers when unused. Request `EXCLUSIVE` ownership only after every reachable semantic variant
is covered. Missing/faulted core stays inert; do not preserve a second stable behavior fallback.

Follow [TESTING](../../TESTING.md): exercise real routed input, submitted effects, separately
advanced host state and resulting controller output. Cover binding changes, cancellation, release,
page/grid return, delayed read-back and replacement where relevant. Prefer those regressions over
constructor tests, internal call counts or serialized implementation hashes.

Verify direct Bitwig calls against the resolved API 25 JAR, avoid deprecated methods, and run the
required deprecation-enabled package. Report required canopy expansion/restart and pending live
evidence explicitly; offline success cannot inherit part 1's live pass.

**Current user constraint: leave the installed Bitwig version alone.** Do not install, publish,
reload, restart Bitwig, or drive state-changing debugger input as part of this handoff. Prepare and
test offline. When the user later authorizes live validation, hold `tools/with-pull-live --owner
LABEL` continuously through exact-build activation and the full smoke; checkpoint before a
restart, identify the tested build, preserve project files and record authoritative read-back plus
actual display/light output. Keep durable results and remaining gaps concise in the canonical docs.
