# Mixer parameter-touch capability audit

## Complete Master slice

The Master page owns all eight top-encoder TOUCH gestures and existing core display feedback.
Knobs 1–4 touch/reset Master volume, Master pan, Cue volume, and Cue mix. Knobs 5–8 have no
parameter, but Delete plus touch still consumes Delete and every admitted release applies the
configured stop-Automation-Write preference. There is no separate Shift, Select, or long-touch
Master variant. Reset precedes touch begin. The ordinary dedicated MASTER_KNOB/TEMPO controls
remain separate global direct bindings and are outside this eight-encoder migration.

Installed API 45 now provides normalized physical touches, project-fenced unified automation state
and effect, exact-target bounded parameter-touch leases, reset, Delete consumption, and core
display output. Master parameter slots already have exact project identities. No new Bitwig proxy
or direct API call is needed for this slice. The Master core view shares the core release
session so page departure releases leases while physical END retains the release preference.
Its stable MasterMode touch handler is inert with complete core input and feedback.
The existing host-colored mixer policy does not tint on touch; physical touch is supplied to the
core-owned renderer while preserving that appearance.
Capacity is eight physical owners/four parameter targets; stale or externally rebound targets fail
closed. Fault/exit cleanup remains best effort and cannot release an old target through a new proxy.

## Track/Mix completion boundary

The normal Track page and VS Track parameter region share the same inert TrackMode adapter.
Their complete core profiles now own turns, touches, upper menus, display, and their respective
footer actions/lights. The obsolete stable selected-Track provider, send-page setting, and ACTIVE
parameter identity recipe are deleted. Named selected-track volume/pan and send banks supply the
actuators independently of physical bindings. Other inherited Volume/Pan/Send modes remain frozen
legacy behavior and retain their own providers and AbstractTrackMode touch/row handlers.

Track touch preserves Delete consumption/reset followed by touch begin/end and the automation
release preference. Shift+Select on a send consumes Select and requests an absolute enabled change
after touch begins. Both modifiers may apply with Delete. Empty installed send slots still consume
Select; Input & Output and the last two slots of send page2 have no send behavior. Eight independent
bounded toggle lanes wait for authoritative enabled read-back, coalesce dependent toggle parity,
and expire an unacknowledged request after five seconds. Selection/page departure clears pending
intent. Feedback never renders a submitted enabled value.

## Verification

Master: all eight touches, first four exact target leases/resets, empty knobs consume Delete,
preference on/off and unified authoritative writing, core-owned physical-touch-aware display with unchanged value
until host read-back, project/selection change, departure/reentry, and fault cleanup. Existing
Project touch tests must still pass. Focused tests cover parameter/UI authority, actual reset/touch/enabled ordering, replay and target
rebind rejection, dynamic slot confinement, and both footer profiles. Full package/deprecation
validation and a later exact-build
routed live smoke test remain required; no live actions occur during implementation.

## Accepted full TrackMode migration scope

The complete normal Track page and VS Track parameter region are one migration. Every composition
with an actual TRACK layout must select the corresponding core profile before TrackMode policy is
removed. Other inherited Volume, Pan, Send, and Crossfader modes retain their own existing code.

Top row on DOWN: Mix selects the Mix subpage; Input & Output selects a display-only metadata page
with all eight parameter controls empty. With Mix visible, left/right send-page keys select offsets
0/4 when the bounded eight-send bank exposes send7. The second page displays sends5–8 and leaves
its final two parameter controls empty. Page/send state must survive core checkpoint/reload.
Normal legacy turns use configured normal/fine sensitivity, a volume acceleration curve, and pan
half-speed/center detent; the existing VS Track parameter region uses decoded ticks times10. Core
must preserve these declared profile differences and select fine speed from physical Shift.

Normal lower row handles UP in precedence order: Duplicate consumes Duplicate and duplicates the
exact visible track; Delete consumes Delete and deletes it; Record consumes Record and toggles arm;
Select consumes Select (Bitwig framework multi-select currently has no implementation); otherwise
select an unselected track, or act on an already selected track. For an already selected group,
Shift toggles expansion, otherwise expand and enter its first child. An already selected nongroup
opens device parameters. LONG selects the main-bank parent and consumes that row key. The VS
footer keeps its existing core selection/Stop recipe, rather than inheriting the normal recipe.

Required reusable observation/effects:

- Named selected-track volume/pan and eight sends, independent of physical encoder bindings;
  identity/alignment to the private selected target is checked before publish and application.
- Optional parameter enabled state and absolute enabled writes; local API25 source verification
  confirms `Send.isEnabled()` (since18) and `SettableBooleanValue.set(boolean)` (since1) are supported.
- Generic encoder configuration (controller range/step, normal/fine preferences), with response
  policy and modifier choice in core.
- Current-track-bank snapshot (eight main-or-effect slots, exact bank identity/generation/offset,
  track state including expansion, model cursor identity/pinned), distinct from the Session main
  bank. Exact-target duplicate/delete/select/arm/expand/enter and main-parent primitives.
- A closed installed mode token for device-parameter selection; never arbitrary stable callbacks.
- Ordered exact-touch acquisition when a gesture needs a subsequent enabled write, so Delete reset
  precedes touch and Shift+Select send enabling follows touch. Desired touch leases remain complete
  and replayable, and every explicit acquisition must belong to that desired lease set.

The normal parameter/display/menu/footer profiles migrate together, including lights and
Input & Output labels. Read-back supplies values/enabled colors, Track Type/Monitor text, footer
selection/arm/active state and meters; input/effect submission must not masquerade as host state.

Ordered touch acquisition is a reusable primitive because the complete desired-touch map cannot
express a point between two one-shot effects. Runtime application releases omitted leases, runs
reset/acquire/enabled effects in emitted order, then reapplies the complete desired lease set;
acquisition is idempotent and an explicit acquisition absent from that set is rejected. The parent
owns exact touch cleanup on omission, invalidation and exit. When a bank wrapper has actually rebound,
cleanup skips that actuator and warns instead of releasing the new target; this remains best effort.

Normal full-Session composition also preserves Stop-plus-track at physical BEGIN against the exact
Session bank, suppressing footer release/long actions and the later plain Stop release. Normal
footer Record consumption shares a core gesture registry with transport so it cannot also toggle
transport record when released. Core selection waits for later controller-layout read-back after
requesting the installed Device Parameters mode. Core checkpoint schema5 retains page/send state.
