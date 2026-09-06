# Session launcher location and release boundary

Design only. The installed optional `SESSION_CLIPS` observation domain has no production consumer;
the grid/scene action and feedback owner remains the frozen Session adapter.

## Exact location and proposed capacity

A launcher location is a track channel identity plus absolute scene index, not a durable clip ID.
Capture Session generation/shape, visible slot, channel and observed content; recheck before proxy
acquisition. A display-bank wrapper cannot safely address delayed release after scrolling.
Identical content and scene insertion/deletion prevent name/content matching from proving clip identity.

A candidate `LauncherLocationHost` would install private non-selection-following cursor tracks with
one slot each: at most 64 held/pending pad gestures plus two retained clipboard locations, or 66
cursors. This is a proposed ceiling, not installed capacity. Validate initialization/parking cost
live before adoption.

Acquisition would unpin, select the exact channel, pin and scroll to the captured scene. Later
existence/channel/pin/scene/slot read-back must prove readiness. Publish bounded opaque lease IDs
and PARKING/READY/INVALID state; replay must not repark a ready lease. Invalidate stale acquisition,
report exhaustion and never silently reuse a launched actuator for another press.

Core would own modifier precedence, clipboard and pending intent. Generic effects would act on
leased locations for select/delete/create/record, main/alternate launch/release and copy. Dependent
create/select/launch waits for host read-back. Original-view edge capture does not itself preserve
an actuator or prove host completion.

## Decision blocking cutover

API 25 `launchRelease()` and `launchReleaseAlt()` return void and expose no generic receipt.
Configured release can leave playback unchanged; unchanged playing state cannot acknowledge it.
Fill-specific busy → non-busy Return logic does not cover arbitrary Session release settings.
`flush()` flushes controller output, `requestFlush()` requests that callback, and `scheduleTask()`
delays execution: none is a documented DAW-command fence.

A finite pool needs a defensible retirement/reuse point. Retaining forever exhausts it; reusing
after a delay assumes submission equals completion. Exclusive cutover remains blocked until a
supported lifetime/receipt mechanism is proved, or the user explicitly accepts a narrow
location-addressed **release submission** contract that distinguishes completion.

This is separate from [parked general reload quiescence](../findings/core-reload-quiescence.md).
API 25 gives extension exit no asynchronous cleanup grace period.

## API and acceptance constraints

The local API-25 source JAR was checked for cursor-track creation, channel selection/pinning,
one-slot launcher banks, select/record/create/browse, main/alternate launch/release, and
`destination.replaceInsertionPoint().copySlotsOrScenes(source)`. Deprecated `copyFrom` must not be
introduced. Reverify exact overloads when implementing. The existing fill adapter demonstrates
parking shape, not a generic Session release acknowledgement.

Preserve all legacy modifier/configuration variants and action/feedback ownership. Freezing the
release lane at BEGIN, removing duplicate record requests, or canceling a short tap before parking
are explicit behavior decisions. Tests must distinguish acquisition, readiness, submission,
advancement, output, release and retirement; cover 64 holds, exhaustion, bank/scene/selection
changes, clipboard replacement, page departure, faults and reload. Require exact-build routed live
proof before replacing the frozen owner.
