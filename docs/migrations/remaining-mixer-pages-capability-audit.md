# Remaining mixer pages capability audit

Behavior audit baseline: `e3c35508`. The Volume/Pan implementation and verification status are
tracked in [their capability audit](volume-pan-capability-audit.md); Crossfade, Send, and Track
Details remain deferred. No deployment or live verification is claimed here. The normal selected-track Mix page and footer are already
core-owned. This audit covers `VOLUME`, `PAN`, `CROSSFADER`, `SEND1`–`SEND8`, and `TRACK_DETAILS`.
Generic mode observation/selection and inert adapters are being developed separately.

## Reachable entry and composition

The baseline `TrackCommand` (now migrated into
[TrackMixControlView](../../pull-core/src/main/java/de/mossgrabers/pull/core/runtime/view/TrackMixControlView.java))
implements Mix-button BEGIN: Shift toggles the VU preference; Track mode opens the remembered global
mix mode; a global mix mode returns to Track; another mode enters Track and remembers its previous
nontemporary mode. LONG only arms restoration after that latter transition; END restores it.
If the current bank has no selection, a normal BEGIN also requests selection of slot zero.
Its light uses `Modes.isMixMode`. These are controller-level entry semantics, not page recipes.

[SelectCommand](../../pull-shell/src/main/java/de/mossgrabers/controller/ableton/push/command/trigger/SelectCommand.java)
updates the active note mapping on every edge, ignores page changes in Browser, and on unconsumed
END toggles temporary Track Details or Device Layer Details depending on the current mode.
Track Details is a separate full page, not the global mix footer. Do not change this modifier's
other consumers or its temporary-page behavior as an incidental page migration.

The normal global pages share the same lower row and footer as `CurrentTrackFooterView`.
They can reuse that view, shared Record consumption, and the full Session Stop gesture when Session
is present. Master replaces the parameter page while retaining its grid/note composition.
A page migration must preserve this separation of selected grid, current bank, and selected mode.

## Global bank pages: common controls and feedback

[AbstractTrackMode](../../pull-shell/src/main/java/de/mossgrabers/controller/ableton/push/mode/track/AbstractTrackMode.java)
contains the shared contract. Indices below are zero-based.

- Eight encoder values operate on one role across the eight **current-bank** tracks. The bank may
  be either installed main window or the effect bank; it is not necessarily the Session bank.
- Every encoder touch BEGIN with Delete consumes Delete and resets the parameter, then submits
  touch-on. END submits touch-off and stops both active automation-writing domains when configured.
  This release behavior also applies to missing/no-op parameters. Other modifiers do not replace it.
- Lower-row BEGIN does nothing; END has Duplicate > Delete > Record > Select precedence, then
  select/group/device navigation. LONG navigates the main cursor's parent and consumes END.
  Reuse the complete existing core footer, not a second copy of those rules.
- Upper-row actions occur on BEGIN only. Columns 0/1/7 select Volume/Pan/Crossfader. Columns 2–6
  select send pages or move the **menu** between send offsets 0 and 4. Selection records the global
  mix preference and selects that installed mode; selecting the same item does not toggle to Track.
- Menu pagination is present when the model cursor's **sixth send, index 5**, exists. With no extra
  sends, columns 2–6 represent sends 0–4. At offset 0 with extra sends, columns 2–5 are sends 0–3 and
  column 6 is `>`. At offset 4, column 2 is `<` and columns 3–6 are sends 4–7. No extra sends resets
  the remembered offset to zero. This differs from the already migrated selected Track page, whose
  six send knobs and seventh-send threshold use a different menu layout.
- Send menu names/existence come from the **model cursor's** send bank, while the parameter columns
  act on all current-bank tracks. A blank send menu entry is not a command-admission check: the
  original BEGIN handler still selects its computed valid SEND mode. Preserve that distinction.
- Upper lights are white only for the selected existing item or an arrow; other entries are off.
  Lower lights are off for absent/deactivated tracks, red for record arm, otherwise track color.
  Display footers retain selected fill, deactivated gray treatment, twelve-character names, type/
  expanded-group icons, and the selected/pinned marker.

`handleSendEffect` in this base class has no callers in these pages at this baseline. Its alternate
"active send returns to Track" recipe is not the reachable upper-row behavior. Do not resurrect it.

## Per-page differences

| Page | Value policy | Display and special touch behavior |
| --- | --- | --- |
| [Volume (core replacement)](../../pull-core/src/main/java/de/mossgrabers/pull/core/runtime/view/GlobalMixerControlsView.java) | Eight `PushVolumeParameter` wrappers; decoded calibrated delta × `(1 + 0.2 × observed normalized volume)` | Eight vertical faders and per-track stereo VU meters, gated by the VU preference. Host volume text (eight chars), modulated value for the marker, track color and activation. |
| [Pan (core replacement)](../../pull-core/src/main/java/de/mossgrabers/pull/core/runtime/view/GlobalMixerControlsView.java) | Eight `PushPanParameter` wrappers; half-speed delta, center tolerance, and 0.015 detent while moving toward/crossing center | Horizontal pan controls, observed/modulated values, `C` or rounded `L n`/`R n` derived from host value; no VU meters. |
| [CrossfadeMode](../../pull-shell/src/main/java/de/mossgrabers/controller/ableton/push/mode/track/CrossfadeMode.java) | One discrete A/AB/B transition in the direction of each inherited callback, clamped at the ends; Delete resets AB | Generic ring visualization and host crossfade label. The wrapper's touch method is a no-op, but inherited release still handles automation. |
| [SendMode](../../pull-shell/src/main/java/de/mossgrabers/controller/ableton/push/mode/track/SendMode.java) | One fixed send index across eight tracks, normal calibrated parameter adjustment | Per-track send name/value/modulation; enabled appearance requires track activation **and** send enabled. After ordinary reset/touch handling, Shift+Select touch BEGIN consumes Select and toggles enabled. |

Send touch ordering is **Delete reset → touch-on → Select consumption → enabled toggle** when all
three modifiers are held. They are cumulative, not exclusive branches. `EmptySend` still implements
`ISend`, so Shift+Select consumes Select even when that parameter is unavailable. END stops automation
by the common rule. The existing `ParameterTouchControls`, shared touch session, ordered acquire
effect, enabled snapshot, and authoritative toggle lane cover this mechanism once the right named
parameter bank is present.

[TrackEncoderResponse](../../pull-core/src/main/java/de/mossgrabers/pull/core/runtime/view/TrackEncoderResponse.java)
already implements normal volume/pan response, but recognizes only the selected-track slot constants.
Extract a core parameter-role choice to reuse it for visible-bank slots; do not pass Volume/Pan banks
through its current generic fallback. Send uses the calibrated generic delta. VS Live's `delta × 10`
selected Track-page rule is not the behavior of these inherited global pages.

[CrossfadeParameter](../../pull-shell/src/main/java/de/mossgrabers/bitwig/framework/daw/data/CrossfadeParameter.java)
contains the sign-only step recipe. Calling its `inc()` via a generic Adjust effect would leave
controller policy stable. Core should choose the absolute observed enum state; the stable actuator
only validates/sets that state. Existing normalized setters can perform the mechanical enum encoding
if their role and state semantics are explicit and target-fenced.

There is a normalization issue to settle before claiming exact Crossfade behavior: controller
RELATIVE ingress coalesces summed deltas, while the old wrapper steps once per callback regardless
of magnitude. Opposite callbacks, clamping, and packet boundaries cannot all be reconstructed from
the sum. Characterize the routed path and agree a generic bounded observation contract or an explicit
behavior change; do not silently call the sign of the coalesced sum equivalent to the old sequence.

## Navigation is selected by the grid and the page

[PushCursorCommand](../../pull-shell/src/main/java/de/mossgrabers/controller/ableton/push/command/trigger/PushCursorCommand.java)
acts on BEGIN. Up/down always navigate the current bank's scene bank: ordinary scroll one scene,
Shift select a scene page. Left/right have two distinct paths:

- With the Session-navigation facet, ordinary selects a track page and Shift scrolls one track.
- Otherwise the active mode receives previous/next item-page. These global pages override Shift
  to swap the actual model cursor track with its previous/next sibling; ordinary selects a bank page.
  Track Details lacks that override, so it selects a bank page even with Shift.

Arrow lights read scene/track scrolling flags. Outside Session navigation, Shift left/right lights
use `hasPrevious/NextItem()` (bank scrolling flags), even though global mix actions swap the cursor.
That existing action/light applicability mismatch needs characterization; an audit cannot replace it
with a claimed precise swap-availability contract.

[PageLeftCommand](../../pull-shell/src/main/java/de/mossgrabers/controller/ableton/push/command/trigger/PageLeftCommand.java)
and its right counterpart use the separate Session-layout predicate: Session selects current-bank
pages on BEGIN regardless of Shift; otherwise they delegate only to a sequencer view and are inert
on ordinary Note views. The Session-layout and Session-navigation predicates must remain distinct.
Do not claim arrows or page buttons exclusively through a mixer profile until their whole routed
grid/page slice and lights move together. Existing navigation may remain frozen with explicit claims.

## Track Details requires a separate capability slice

[TrackDetailsMode](../../pull-shell/src/main/java/de/mossgrabers/controller/ableton/push/mode/track/TrackDetailsMode.java)
uses a full eight-column options display and its own two rows:

- Lower-row END toggles activation, arm, mute, solo, monitor, auto-monitor (columns 0–5); column 6
  toggles model-cursor pin for ordinary tracks and is unused for Master; column 7 opens track color.
  BEGIN/LONG are inert. There are no Duplicate/Delete/Record/Select variants for these buttons.
- Actions choose Master first when it is selected; otherwise they use the selected item of the
  current bank and do nothing if it has no selection. Pin is an operation on the actual model cursor.
- Lights/display instead read the model cursor. Under pinning, that can differ from the action
  target. A complete migration must expose both identities and fail closed while they disagree;
  copying this mismatch would violate the authoritative feedback contract.
- `isMonitor()` reads actual `isMonitoring()`, distinct from `monitorMode()`; AUTO may be actively
  monitoring. Monitor chooses OFF if currently monitoring, otherwise ON. Auto-monitor chooses OFF
  from AUTO, otherwise AUTO. The existing selected snapshot's mode alone cannot reproduce both.
- Ordinary/Master activation, arm, mute, solo, and monitoring branches are real inherited TrackImpl
  operations; MasterTrackImpl does not override them as no-ops. Only pin column 6 is expressly unused.
- Lower lights use observed active/arm/mute/solo/monitor/auto/pin flags with low/high palette colors;
  color is green. No cursor falls back to the base off state. Upper columns 6/7 remain on even if no
  track exists; others are off. The display shows type/name and eight options, or “Please select a
  track...” when the cursor is absent.
- Upper BEGIN columns 6/7 decrement/increment the MIDI insert/edit channel. Knobs 6/7 do the same
  based on direction; knobs 0–5 are inert. The setting clamps to channels 0–15 and displays 1–16.
  Its write is observed through the setting callback. These callbacks also have the sign-only
  coalescing issue described above. Modifier sensitivity does not change their one-step policy.
- All knob touches are inert: this class does not use the AbstractTrackMode touch implementation.
  Adding parameter reset/touch or automation-stop behavior here would be a semantic change.

Track color opens the shared [ColorView](../../pull-shell/src/main/java/de/mossgrabers/framework/view/ColorView.java)
with track target mode. Palette pad END sets the cursor's color (or selected Master when no cursor)
then restores the previous view; grid lights and color-page selection also belong to that workflow.
This is a full-grid dependency with its own target alignment, input ownership, native-note routing,
and restoration requirements. Do not invent a feature-shaped shell “open track color” recipe or
leave an incomplete exclusively owned Details row backed by that recipe. Migrate the palette slice
or use an already declared, fully audited frozen view adapter through generic view capabilities.

## Reusable canopy and smallest coherent sequence

1. **Volume + Pan pages first.** Reuse `TRACK_VOLUME`, `TRACK_PAN`, parameter reset/absolute/adjust
   effects, shared parameter touches and automation release, `CURRENT_TRACK_BANK`, the complete
   normal footer, and display/light transports. Add observed VU preference and reusable page/global
   mix selection state; core owns menu offset and last global-mode preference/checkpoint state.
   The common menu can select another explicitly declared frozen page via generic mode selection;
   that does not make the destination page's own controls migrated.
2. **Strengthen current-bank parameter fences before cutover.** Existing named Volume/Pan predicates
   validate a captured bank's slot/role but do not recheck `model.getCurrentTrackBank() == capturedBank`
   at apply. Separate ordinary-current eligibility from retained exact addressability, following the
   selected-track target implementation, so Snapback restoration can keep its original target while
   new writes fail closed on bank switches. Publish/validate consistent parameter-owner and footer
   identities when combining the two domains.
3. **Crossfade + visible send columns.** Add one eight-slot named crossfade bank and bounded named
   send columns (up to eight send roles × eight current tracks, or a selected-column subscription
   over that installed matrix). Extend capacities explicitly; request only the needed column's
   sampling. Existing selected-track sends are the wrong matrix orientation. Reuse enabled state,
   exact touch leases, send-role identity including send-bank scroll position, and absolute effects.
   Cursor send metadata for menu names needs an explicit identity/alignment rule; do not silently
   substitute private selected-target metadata when the model cursor is pinned elsewhere.
4. **Details + target/config/color dependencies.** Expose aligned current/master/cursor state,
   actual monitoring plus monitor mode, absolute track Boolean/monitor operations, cursor pin,
   and observed MIDI edit-channel setting with bounded absolute writes. Reuse existing current-track
   action identities and Master/project fences where applicable; a generic cursor capability must
   not imply arbitrary track lookup. Complete the color workflow or its declared adapter boundary
   before exclusive ownership. This is larger than reusing the normal footer.

No new display transport or physical encoder binding is inherently needed for these pages. Missing
named roles/properties, parent API values, fixed output claims, and mode/view lifecycle capabilities
still require a shell/API build and restart. Crossfade step granularity and target-proxy alignment
are unresolved design work, not evidence that these pages are already safe to claim.

## Acceptance evidence required before deletion

Characterize actual knob bindings (not only `onKnobValue`, which is empty on the parameter-bound
pages), all upper entries at 0/4 offsets with 0/5/6/8 sends, each lower modifier, empty sends, Delete+
Shift+Select touch ordering, ordinary versus fine curves, host modulation, VU preference, and pin/
effect-bank combinations. Test input → request → explicit host advancement → snapshot → feedback.
Cover bank switch/reorder between prepare/apply, old exact touch release, queued toggle acknowledgement,
mode change during a touch, reload checkpoint state, and rejected mode changes without optimistic UI.

For Details, add Master/no-selection/pinned-mismatch cases, monitoring under AUTO, channel clamps and
rapid/opposing knob samples, inert touches, temporary Select-page return, palette target changes,
and native-note silencing/restoration. Navigation tests must use the real cursor/page command path
with both Session predicates and verify later host location plus lights. A complete deprecation-enabled
package build and first live Bitwig/Push smoke test are required; the prior checkpoint's 685 passing
tests do not validate these still-unmigrated pages.
