# Session launcher location design

Status: design only. Do not enable exclusive Session grid routes from this document. The existing
Session grid remains the production owner until the release-retirement question below is resolved
and the complete action/feedback slice passes its integration and live tests.

## What must survive a moving window

Session grid commands need an exact launcher location after the visible 8x8 or 8x4 bank moves.
The location is a track channel identity plus an absolute scene index. It is not a permanent clip
identity: inserting/deleting scenes, moving clips, or replacing content can change the clip at that
location. Matching name/content/color can reject some changes but cannot identify identical content.

The current `SessionClipWindowSnapshot` supplies bounded read-back and alignment. A grid press must
capture its Session generation, shape, visible track index, channel identity, absolute scene index,
and observed slot before issuing an action. Recheck those values immediately before acquiring a
mutable proxy. Display-bank read-back is sufficient for immediate fenced actions; a wrapper taken
from that bank is insufficient for delayed release or a retained Duplicate source.

## Proposed reusable lease capability

One parent-owned `LauncherLocationHost` could create private, non-selection-following cursor tracks
at initialization, each with one launcher slot. The upper bound is 64 simultaneously held or pending
pad gestures, plus two clipboard locations for the separately retained full and upper Session views.
The proposed 66-cursor bound needs a live initialization/parking latency check before adoption;
it is an explicit ceiling, not an invitation to allocate on demand.

Each acquisition verifies the visible source fence, then unpins the private cursor, selects the
source channel, pins it, and scrolls its one-slot bank to the captured scene index. A later host
sample must agree on cursor existence, channel identity, pin state, scene index, slot existence,
and any required content before the lease becomes ready. A stale acquisition fails closed and a
leased cursor never silently aliases another request. Source disappearance and observed target
disagreement invalidate the lease. Merely scrolling the display bank does not invalidate an already
parked lease.

The core-facing values would be:

- A generation-scoped opaque `LauncherLeaseId` and a value-only `LauncherLocationTarget` fence.
- Complete replayable `DesiredLauncherLeases`, with no executable callbacks or policy recipes.
- `LauncherLeaseSnapshot` with PARKING, READY, or INVALID status and subscribed slot read-back.
- Generic effects for select, delete, browse insertion, create empty clip with a length, record,
  main/alternate launch, main/alternate release, and copy from one leased source to another.
- Fenced absolute bank navigation for the grid's existing birds-eye variant.

The desired lease map owns resources; effects request primitive operations. Replaying the same map
must not repark a cursor. Pool exhaustion must be explicit and observable. A second press cannot
replace a launched lease merely because it uses the same physical pad.

Core policy would own modifier precedence, consumption, configured empty-slot behavior, clipboard
lifetime, and pending sequences. Create must wait for later content read-back before its dependent
select/launch; record must use later recording/queue read-back rather than a synchronous fake. A
shared core gesture session and retained release observer can finish acquired Session gestures after
their grid profile disappears. Fixed grid profiles own all pad actions and all pad feedback together.
Raw PAD ingress remains ordinary dispatch, independent of learned mapping endpoints and NoteInput.

## The unresolved retirement boundary

API 25 `ClipLauncherSlotOrScene.launchRelease()` and `launchReleaseAlt()` return `void`. Their
documentation describes sending the release gesture but exposes no receipt. The configured release
can intentionally leave playback unchanged. A later unchanged playing flag therefore cannot prove
that release completed. The fill-specific Return protocol, which waits for busy to become non-busy,
does not apply to arbitrary Session release settings.

`ControllerExtension.flush()` is documented as flushing pending updates **to the controller**;
`ControllerHost.requestFlush()` only requests that callback. Neither documents a DAW-command fence.
`scheduleTask()` documents delayed callback execution, not acknowledgement. Waiting one controller
tick or an arbitrary number of milliseconds would not establish a receipt.

The exact actuator can be retained through release submission, but a reusable finite pool also needs
a defensible point at which it can select a different target. Indefinite retention exhausts the pool;
release-once-plus-delay is only a submission-level assumption. The project currently requires
authoritative acknowledgement before retiring an actuator whose dependent action is outstanding.
No such generic acknowledgement has been identified here. Production cutover remains deferred
until either a supported receipt/lifetime mechanism is established or a narrow Session contract is
explicitly accepted that distinguishes location-addressed release submission from completion.

This is a bounded Session capability question. It does not propose solving the separately parked
general quiescence issue or pretending that extension `exit()` grants asynchronous cleanup time.

## Verified API 25 surface

These methods were checked in the locally resolved
`com.bitwig:extension-api:25` source JAR, not inferred from compilation:

- `ControllerHost.createCursorTrack(String, String, int, int, boolean)`;
  `CursorChannel.selectChannel(Channel)`; `PinnableCursor.isPinned()`;
  `CursorTrack.clipLauncherSlotBank()`; slot-bank scrolling and slot observations.
- `ClipLauncherSlot.select()`, `record()`, `createEmptyClip(int)`, and `browseToInsertClip()`.
- Main/alternate launch and release on `ClipLauncherSlotOrScene`.
- `destination.replaceInsertionPoint().copySlotsOrScenes(source)` for copying.
  `copyFrom` is deprecated and must not be introduced.

The private parked-cursor mechanism has a local precedent in `SelectedTrackFillClipHost.LiveAdapter`.
That precedent establishes the shape of acquisition, not a generic Session release acknowledgement.

## Acceptance before cutover

Preserve the characterization inventory, including all modifier branches and authoritative lights.
Any choice to freeze the main/alternate release lane at BEGIN, eliminate the inherited duplicate
recording request on release, or cancel a released press still waiting for acquisition must be an
explicit semantic decision rather than an incidental consequence of the new state machine.

Tests must distinguish acquisition request, proxy movement, later readiness, submitted action,
host advancement, output read-back, release request, and retirement. Include short taps before
parking completes, all 64 holds, capacity exhaustion, bank changes, selection changes, view/page
departure, duplicate source replacement, fault cleanup, and core replacement. Lights must derive
from host slot state; reusable blink output or production tick animation must migrate alongside
actions. Live testing must verify exact slot state and successful hardware output from the same
checkpointed shell/core under the singleton live lease.
