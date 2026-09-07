# Target-bound interaction lifecycle

Working source: Core API 47 / Bitwig API 25. `InputGestureRouter` uses the shared host-independent
`InteractionLifecycle`. The matched shell/core passed [routed live validation](migrations/interaction-lifecycle-live-smoke.md)
in `202arp3`. UI/editing migration remains a [separate task](migrations/ui-and-editing-handoff.md).

## One rule

Capture a control's current target and receivers at BEGIN. If that binding disappears or changes,
cancel it. Cancellation performs required cleanup; it never dispatches an ordinary END action.
Ignore the physical tail until UP and a fresh BEGIN. Returning to the old view or target cannot
revive the interaction. A page replacement that leaves the same target/view visible preserves it.

`InteractionLifecycle<C,T>` is the bounded, host-independent state machine. `replaceBindings`,
`begin`, `current`, `cancel`, `release` and `targetLost` control admission. `beginOperation` /
`completeOperation` track independently completed work; `beginFinish` / `completeFinish` separate
cleanup submission from retirement. The standalone tests exercise delayed completion, duplicate
receipts, capacity limits and different generations without Bitwig or Push.

## Production wiring

- The shell registers TOUCH→RELATIVE/ABSOLUTE and PAD→POLY_PRESSURE companions once. They keep
  the edge's original disposition and core generation, flush before END, and cannot leak into a
  replacement stable command. Native musical `NoteInput` remains a separate path.
- The common stable grid dispatcher captures its receiver at DOWN. View/target loss retires that
  receiver and its held-key state; an orphan LONG/UP cannot reach a newly active legacy view.
  Debugger target neutralization preserves the physical hold so this path can be tested faithfully.
- Core derives its physical footprint from `SurfaceArea`, with at most 256 retained interactions,
  256 view instances and 64 pending semantic actions. Lifecycle IDs remain local to that core
  instance; shell generation fencing remains at permanent ingress.
- Each view declares immutable `InputTarget` values. Parameter mappings resolve through the
  compiler's aligned named slots to exact shell target references. Track, Session, project and
  drum contexts include the identity/generation required by their operation. Every observer's
  context is captured independently; view ordering cannot bypass another receiver's target fence.
- `ControllerView.cancel(control, kind, target, snapshot)` releases resources for its captured
  interaction. Deferred actions attach cancellation to their exact resolved intent when needed;
  an old queued action cannot clean up a newer gesture on the same control.
- Views receive an input projection excluding cancelled physical tails, so a cancelled Stop cannot
  become a modifier for a new row press. Aggregate channel pressure sees only that receiver's
  admitted pads. Host values and the raw ingress snapshot are unchanged. Unheld encoder motion is
  an independent one-shot input; poly-pressure requires an admitted PAD.
- Parameter pages declare `parameterTouchControls`; the router owns touch/reset/automation-release
  lifetime once. Removing a touch releases its shell lease before navigation/effects execute.
  Touch emphasis reads the later shell lease snapshot, rather than highlighting a replacement
  parameter merely because the physical knob is still held.
- A later `ParameterBridgeSnapshot.touchLeases` sample proves shell touch-resource retirement.
  A fill waits for its exact owner to disappear from `clipLaunchSessionTargets`. Neither a desired
  output nor a successful void call is treated as playback/write completion.

Only active views receive input, reconciliation and rendering. Cleanup effects retain their required
subscriptions for that result; unfinished resource retirement retains only the observations it needs.
Snapback and asynchronous toggle/clip executors still settle their submitted requests using their
existing authoritative observations. This integration does not replace those with fabricated generic
receipts or implement the separately parked [application-wide reload drain](findings/core-reload-quiescence.md).

## Adapter limits

Two concrete adapter constraints remain visible:

1. The clip executor addresses release by logical control owner. That owner cannot admit a different
   clip while its previous acquired session retires. Parameter targets have separate exact leases;
   they do not need that additional control-owner fence.
2. External Bitwig cursor changes may make an old parameter unreachable before cleanup. The shell
   abandons/reports that exact lease rather than touching its replacement. Touch-lease retirement
   means resource retirement, not proof that Bitwig acknowledged every earlier parameter write.

There are no replacement timeouts, pinned offscreen pools, or stable product-policy additions.
Core cancellation never manufactures ordinary END events. Full Device identity and Session grid/scenes release guarantees still need
proof when those remaining families migrate; a shared lifecycle does not create missing host APIs.

Exact package results, routed smoke evidence and physical-only limits are in the
[live record](migrations/interaction-lifecycle-live-smoke.md). The corrected matched shell/core
passed the scoped lifecycle run; broader Device/Session migration remains separate.
