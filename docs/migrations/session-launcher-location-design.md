# Session launcher release boundary

The grid/scene action and feedback owner remains the frozen Session adapter. Optional
`SESSION_CLIPS` publishes bounded observation only; it has no production product consumer.

The chosen policy is [cancellation on binding loss](../interaction-lifecycle.md), including
controller-driven bank/view changes. Continuing offscreen editing is not required. The former
66-cursor proposal is not an installed capability or a required architecture.

## Decision still needed

A launcher location is a track channel identity plus absolute scene index, not a durable clip ID.
Scene insertion/deletion and identical content defeat name-based identity; a mutable display slot
cannot authorize delayed cleanup after rebinding.

API 25 `launchRelease()` and `launchReleaseAlt()` return void. Configured release can leave playback
unchanged, so neither return nor unchanged playing state proves completion. Fill's busy → non-busy
Return barrier covers its specific configuration, not arbitrary Session release settings.
`flush()`, `requestFlush()` and `scheduleTask()` supply no documented DAW-command fence.

Before exclusive migration, establish when a release actuator can safely be retired/reused, or
explicitly agree a narrower location-addressed **release submission** contract. Cancellation alone
does not answer that question. Do not reserve an unbounded pool, retain forever, or invent an ACK
from a delay. General [reload quiescence](../findings/core-reload-quiescence.md) is separate; extension
exit has no asynchronous grace period.

## Cutover evidence

Preserve the complete modifier/configuration, clipboard, create/record and main/alternate release
behavior together with feedback. Use bounded identity/readiness and primitive operations; verify
exact API 25 overloads before implementation and avoid deprecated copy APIs. Test bank/scene/target
changes, release before readiness, exhaustion, delayed host advancement, fault and reload through
real routing. Require matched-build live proof before replacing the frozen owner.
