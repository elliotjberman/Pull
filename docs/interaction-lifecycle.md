# Target-bound interaction lifecycle

Status: isolated implementation in `pull-core/.../interaction/InteractionLifecycle.java`, tested
without Bitwig or Push. It has **no production routing consumer yet**. API 46 and installed behavior
are unchanged. The user requested this contract be proved independently before integration.

## Policy

Capture the current exact target when a physical interaction begins. If that control's binding
disappears or names a different target, cancel the interaction. Do not continue editing an offscreen
target, move the gesture to another control, revive it when the binding returns, or start work on
the replacement target until physical release and a fresh BEGIN. Replaying an unchanged binding
does not interrupt it. A target still shown on a different control does not transfer the gesture.

Cancellation permits required cleanup only. Ordinary release may additionally run the feature's
release action. Cancellation overrides a normal release that has not yet been submitted; it cannot
undo an already submitted operation. Finishing gesture input and finishing host work are separate.

## Small shared mechanism

The class uses only Java values and collections: no host objects, callbacks, threads or timers.
One instance belongs to one externally assigned, never-reused core generation. Control and target
keys must be immutable; target equality means the same exact incarnation, not the same proxy slot
or display name. The installed control set, retained interactions and submitted operations are
explicitly bounded. At most one interaction may own a particular target until its cleanup settles.

| Call | Contract |
| --- | --- |
| `replaceBindings(map)` | Replace the complete current mapping; centrally cancel bindings that disappeared/changed. |
| `begin(control)` | Capture an exact target or report why admission failed. Even rejected presses stay suppressed until UP. |
| `current(control)` | Route companion motion to that still-active interaction; cancelled physical tails return empty. |
| `beginOperation(id)` | At actual submission time, admit bounded work against the captured target; stale/cancelled/full returns empty. |
| `completeOperation(id)` | Retire precisely that operation on a later host observation or explicit terminal failure. Duplicate/stale receipts do nothing. |
| `release(control)` | End physical input; keep unsettled work. Cancellation never turns back into normal release. |
| `targetLost(target)` | Remove invalid bindings and cancel their interactions. Do not infer completion or authorize cleanup through a replacement. |
| `readyToFinish()` / `beginFinish(id)` | Discover/recheck finalization after submitted work drains; claim exactly once, with the captured target and end reason. |
| `completeFinish(id)` | Report actual cleanup completion or explicit abandonment; physical tails still remain suppressed. |
| `hasTargetWork(target)` / `isIdle()` | Distinguish target reuse eligibility from complete physical/work quiescence within this manager. |
| `stop()` | Permanently close admission and cancel work; continue accepting genuine completion and release events. |

For Cutoff → Volume navigation: replace bindings, reject further Cutoff edits, settle any submitted
Cutoff write, finalize its touch once, then allow the old proxy to move. Its held physical tail
remains suppressed. A new Volume touch can proceed independently; a new Cutoff interaction waits
until old Cutoff cleanup finishes. Cleanup capacity is reserved separately from ordinary operations.

## Integration boundary

Feed normalized physical edges in ingress order; relate touch/turn and pad/pressure to the same
logical control before calling the manager. Capture interaction IDs before deferring semantic work;
never resolve an old queued action through `current(control)` at execution time. Controls without
physical touch need an explicit begin/end recipe, not an invented inactivity timeout here.

The central adapter must call `beginOperation` and `beginFinish` immediately before submission,
then apply live target fences. Returned values are not reusable permits for delayed execution.
Record admission before the host call so reentrant completion is safe. A failed submission must
report a terminal outcome too. Finalization cleans only resources that this interaction actually
acquired; a finalization message is not an instruction to blindly send touch-off to any proxy.

For navigation we control, cancel affected bindings and wait for their `hasTargetWork` to clear
before rebinding host resources; do not wait for the suppressed physical tail. Externally lost
targets require explicit abandonment/reporting, never a write to a replacement. Already submitted
work remains tracked until the executor can report a real outcome. There is no timeout that
fabricates completion and no promise that Bitwig provides an acknowledgement for every operation.

Device identity and Session release/retirement still require proof in their concrete adapters.
The manager does not acquire Bitwig targets, perform host writes, interpret deltas, render optimistic
feedback, or replace native musical `NoteInput`. It is not the parked application-wide reload drain.
Production adoption must replace the corresponding existing lifecycle code, cover action/feedback,
and close the [continuous-input](findings/core-continuous-input-capture.md) and relevant
[target](findings/parameter-target-proxy-coupling.md) gaps with routed tests and later live evidence.

Run the focused contract without host/hardware:

```bash
mvn -o -pl pull-core -am -Dtest=InteractionLifecycleTest -Dsurefire.failIfNoSpecifiedTests=false test
```

The tests assert target-specific requests, separately advanced observed state, cancellation versus
release, fresh-gesture gating, target exclusion, bounded failure and stale receipts. They make no
claim that actual Push routing or a Bitwig completion signal has been integrated.

Verification: `mvn -o -pl pull-core -am package` passes all **389 core tests**, including **14** new
lifecycle scenarios, and the core archive boundary check. No shell API or installed files changed.
