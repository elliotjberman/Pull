# Target-bound interaction lifecycle

Core API 58 / Bitwig API 25. `InputGestureRouter` owns migrated input and cleanup directly.
[ARCH](../ARCH.md) identifies remaining shell handlers; moving their page navigation to core
does not make them inherit this contract.

## Contract

Views compile control ownership when the workspace is composed. Unheld motion uses those routes
directly; idle controls have no gesture bindings to rebuild. Capture the current target and receivers
at BEGIN, then recheck only active gestures and deferred actions against later host snapshots.
An unchanged binding survives a page overlay.
If it disappears or changes, cancel, perform required cleanup and suppress the physical tail until
UP and a fresh BEGIN. Returning to the old target cannot revive it. Cancellation never dispatches
an ordinary END action against a replacement.

A view supplies immutable `InputTarget` values and `cancel(control, kind, target, snapshot)` cleanup.
Defaults do not establish a host target or release a resource. Parameter pages declare
`parameterTouchControls`; the router resolves aligned named slots and owns touch/reset lifetime.
Other targets include the project, track, bank, device and generation required by their operation.
Each receiver's target is captured independently; view order cannot bypass another target fence.

The router retains at most 256 interactions, 256 view instances and 64 pending semantic actions.
A held control points to its gesture or an inert marker until release. Released/cancelled gestures
remain only while their host cleanup is outstanding; shell ingress fences core generations.

## Dispatch and cleanup

- Shell touch→motion and pad→pressure companions retain the edge's original disposition/generation.
  Relative motion sums; absolute/pressure keeps the latest sample. Motion flushes before END.
- Core rejects cancelled tails, including LONG, motion and pressure. Unheld encoder motion is a
  one-shot input; poly-pressure requires an admitted pad. Each receiver's input projection excludes
  cancelled modifiers and aggregates pressure only from its admitted pads. Host values are unchanged.
- Deferred actions retain their exact intent and cancellation function. Cancelling an older queued
  action cannot clean up a newer gesture on the same control.
- Only active views receive normal input/reconciliation/rendering. Cleanup effects retain their
  required subscriptions for submission; unfinished cleanup retains only retirement observations.
- Omitted parameter touches release before navigation/effects. Later `touchLeases` read-back proves
  resource retirement and drives touch emphasis. External proxy rebind may make cleanup unreachable:
  abandon/report the exact lease instead of touching its replacement.
- Fill retirement waits for the exact owner to disappear from `clipLaunchSessionTargets`.
  The clip executor cannot admit another clip for that logical control owner while its old session
  retires. Exact parameter leases do not require that additional control-owner fence.

The router submits cleanup once and drops its record after observed retirement. Neither
resource retirement nor a void host call proves every earlier DAW write completed. Existing fill,
Snapback and toggle executors retain their own authoritative barriers; the
[general reload drain](findings/core-reload-quiescence.md) remains parked.

## Session

Session uses exact launcher locations with the shared capture/cancel/tail contract. Its shell ledger
submits captured main/alternate releases before controller bank rebinding, replacement or shutdown.
A quick-tap create/record intent survives UP until later host acknowledgement, then submits its
matched launch/release; active pending continuations block replacement. Location loss cancels them.
This is a bounded Session continuation, not a general asynchronous operation drain.

See the [Session contract](migrations/session-launcher-location-design.md) and
[live evidence](migrations/interaction-lifecycle-live-smoke.md).
