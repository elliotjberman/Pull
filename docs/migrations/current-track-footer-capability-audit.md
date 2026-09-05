# Current Track footer capability audit

Status: implemented. The complete package build with deprecation reporting passed (685 tests,
zero failures/errors). First live Bitwig/Push verification is pending.
This is the normal Track footer slice. VS Live retains its distinct Project macro footer policy.
The related Track mixer body, menu, encoder, and touch migration is documented separately.

## Existing behavior and complete ownership

The source is `AbstractTrackMode.onFirstRow`, `TrackMode.getButtonColor`, and the Track footer
projection in `TrackMode.updateDisplay2`. The source bank is `model.getCurrentTrackBank()`;
it may be an effect bank and is not interchangeable with the active Session bank.

| Input | Normal Track footer behavior |
| --- | --- |
| Row 1 BEGIN | Begin an action boundary; no normal track command yet |
| Row 1 END + Duplicate | Consume Duplicate; duplicate the visible current-bank track |
| Row 1 END + Delete | Consume Delete; remove that track |
| Row 1 END + Record | Consume Record; toggle that track's record arm through later read-back |
| Row 1 END + Select | Consume Select; no track operation (`ChannelImpl.toggleMultiSelect` is a no-op) |
| Row 1 END, unselected track | Select that track |
| Row 1 END, selected group + Shift | Toggle the group's expanded state through later read-back |
| Row 1 END, selected group | Request expansion and enter the already-selected group |
| Row 1 END, selected nongroup | Request the installed Device parameters mode |
| Row 1 LONG | Navigate the main-bank cursor to its parent; consume the row and suppress END |
| Session Stop + Row 1 BEGIN | Stop the exact visible Session-bank track; suppress row END and plain Stop END |

Modifier precedence follows the table. Normal modifiers and the current-bank target are sampled
at physical END; the Stop chord captures the Session target at physical BEGIN. An unavailable
slot still consumes the modifier. A missing track does not produce a mutation.

`CurrentTrackFooterView` owns all eight lower soft-key inputs, their lights, and the bottom
17-pixel display strip. It observes its modifiers. Lights use observed existence, activation,
arm state, and track color. The footer preserves twelve-character names, selected backgrounds,
inactive gray treatment, type/open-group icons, and the selected/pinned indicator.
No submitted selection, arm, expansion, or mode request substitutes for later host feedback.

## Parent-loaded capability and bounds

`CurrentTrackBankHost` references the two eagerly installed main-bank windows and the existing
effect bank. Its registry admits at most three distinct proxy objects and eight slots per
snapshot. It does not create a new bank, cursor, observer, scanner, or arbitrary-track lookup.
Unknown current-bank objects publish the typed empty snapshot. More registered banks require
an explicit canopy expansion. Registration identity is scoped to this shell lifetime.

`CURRENT_TRACK_BANK` independently gates sampling and publication. `CurrentTrackBankSnapshot`
contains the bank identity/generation/offset, eight value-only tracks, cursor channel ID/pin state,
and a separate parent-navigation generation from the actual cursor
parent availability plus main-window/cursor identity. Track values reuse the common track snapshot and
add observed group expansion and normalized stereo VU values. No Session clip sampling is needed.
The footer requests layout state to fence a mode change; the actual Session view supplies the
Session subscription when that composition is present.

`CurrentTrackTarget` identifies a current-bank generation, registered bank, slot, and channel ID.
Current-track actions are direct select, duplicate, remove, and enter-already-selected-group.
Boolean effects are absolute record-arm and group-expanded writes. Parent navigation uses its
separate main-window/cursor generation. Mode selection names an installed mode and its originating
layout generation. Runtime validation requires the corresponding state subscription.

Preparation validates snapshot identities. Apply rechecks the live current bank, offset, every
slot identity/position, and the addressed channel before invoking a track operation. Switching
main windows, entering the effect bank, scrolling, moving a track, or replacing a slot invalidates
a prepared action. Parent apply separately checks the main-bank window and cursor identity/pin.
Mode apply checks the live layout and only admits a registered mode.

`TrackImpl.enter()` has a delayed selection fallback for unselected tracks. The new actuator
requires the track to remain selected, grouped, and aligned with the actual model cursor at
prepare and apply, admitting only its immediate branch. API 25 `CursorTrack.selectFirstChild()`
does not depend on UI expansion being acknowledged; group visibility and child navigation are
separate operations. No timer is used to pretend a host acknowledgement occurred.

## Asynchronous gestures and composition

A semantic action can wait for Snapback restoration after physical BEGIN. The footer therefore
retains at most one provisional gesture per row. END/LONG captures immutable intent even if the
begin dispatch has not run. Mutable-target operations wait for that dispatch and revalidate the
captured target; they cannot silently retarget after bank movement. LONG wins over the later END.
Deactivation cancels pending gestures. The retained normal Track compositions share the same
footer instance. Master replaces the footer and cancels its local work; controller-level Record
consumption remains available for the physical release.

Mechanical modifier/row consumption occurs at the physical edge, before the deferred host action.
It is never replayed late onto another physical gesture. A bounded shared `ButtonGestureConsumption`
registry coordinates Record with the controller-level transport view: a footer chord consumes
Record immediately even if the target is unavailable or Snapback is still restoring. Record BEGIN
resets that gesture, and Record END takes the consumption before considering a plain Record action.

Record-arm and group-toggle lanes each have capacity eight. Repeated presses accumulate parity
and wait for later authoritative Boolean read-back before submitting a dependent write. Rebinding,
unavailability, deactivation, or the existing acknowledgement timeout discards stale pending work.
The existing broader reload-quiescence finding still applies to uncheckpointed toggle intent;
this migration does not claim to solve that independent lifecycle debt.

The normal footer shares `SessionStopGesture` with the full Session view. Its semantic action
resolver owns Stop+row because a resolved BEGIN bypasses ordinary view handlers. It targets the
Session bank rather than the current effect bank and suppresses the normal footer continuation.
The VS Live track strip retains its own shared upper-Session Stop composition.

## Verification and remaining live work

`CurrentTrackBankHostTest` separates command submission from host advancement. It covers the
three-bank bound, effect/main separation, exact selection/duplicate/delete, arm read-back, live
bank/offset/slot/position changes, selected-group entry, and separately fenced parent navigation.

`CurrentTrackFooterViewTest` covers the eight-key profile, all modifier variants and precedence,
release-time target capture, group/device navigation, LONG suppression, deferred BEGIN/END/LONG,
immediate consumption, stale-intent cancellation, authoritative lights, pin/group/name feedback,
Session Stop targeting, and non-Session behavior. Production core tests additionally cover the
shared Record chord, Snapback continuation, mode read-back, and retained composition.

`BoundedControllerBridgeTest` covers installed-mode admission, live layout fences, later mode
sampling, and modifier consumption. The complete `mvn -o -Dmaven.compiler.showDeprecation=true package` build passed. Existing
unchanged `TransportImpl` warnings remain; changed code introduced no deprecation warning.
No new direct Bitwig API overload was introduced; inherited immediate group-entry and parent
operations were checked against the locally resolved API 25 sources.

A first live test must exercise all footer modifiers on main and effect banks; move the bank
between input and action; use selected and pinned group cursors; defer a chord through Snapback;
release Record before the deferred row action runs; verify group icons/arm lights from later host
state; and verify Device mode from a later layout sample. Also verify Session Stop+row and Master
round-trips through the exact installed build. Offline tests do not prove Bitwig proxy enforcement
or the physical display presentation.
