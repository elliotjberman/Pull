---
status: active
created: 2026-08-05
scope: parameter-mutations
remove_when: supported snapback targets restore with full host-value precision and documented cancellation/addressability guarantees
---

# Snapback restoration limits

The shared [interaction lifecycle](../interaction-lifecycle.md) is integrated. This finding concerns
restoration precision and bounded failure behavior, not continued editing through offscreen proxies.

## Current safe contract

`SnapbackSession` owns Shift-triggered capture, settlement, restoration and semantic-action admission.
The first eligible mutation captures its target's authoritative baseline before the write. Further
changes reuse that baseline. Physical knob touch controls automation touch; it does not define the
Snapback session or become a synthetic long touch because Shift remains held.

Capture is bounded to **10 targets** (eight top encoders plus tempo/master), independently of the
installed parameter canopy. The deferred semantic-action queue holds at most **64 actions**.
A full capture set rejects temporary mutations rather than applying values it cannot restore.

Release flushes queued motion before ending the trigger. Navigation that invalidates active
parameters settles and restores them before dispatching the captured semantic action. Once a queue
exists, later actions cannot overtake it. If Shift remains held after the barrier, a fresh session
can begin. Views do not carry their own Snapback modifier checks or rebinding-button lists.

Settlement waits for two stable value observations, bounded to eight controller ticks. Restoration
submits the captured baseline through the exact retained target, then requires two consecutive
baseline observations. A later changed value resets confirmation and requests another restore
after two retry ticks. These are controller-tick limits, not wall-clock timing guarantees.

After **16 restoration ticks**, the core abandons remaining captures and releases queued actions;
it emits no final write after relinquishing the lease. Timeout means restoration is unconfirmed,
not successful. A target disappearing from read-back is likewise dropped. Neither path authorizes
writing through a rebound proxy. External automation or another controller can also be overwritten
by restoration to the earlier baseline; this remains the chosen momentary-change behavior.

Stable code owns exact actuator validation, baseline leases and best-effort fault/exit restoration.
`IParameter` restoration uses its immediate setter so takeover mode cannot reject the return.
Normal core startup can hydrate retained baselines from the shell snapshot. The existing input and
action gates are separate from the [unimplemented general reload drain](core-reload-quiescence.md).
Full Device remotes remain excluded until their [identity contract](parameter-target-proxy-coupling.md)
is proved; a bank's existence alone does not make its targets safe.

## Restoration precision

The earlier live page audit found that selected pan `0.5` and `0.5004887585532747` both read as
controller value `512`. A positive encoder step followed by its negative restored the integer,
but not the original raw pan. Native pan reset subsequently restored the known original `0.5`.
This proves that opposite turns are insufficient restoration evidence. It was not a direct live
Snapback precision reproduction.

The same loss is present in the restoration contract: `ParameterTargetHost.parameterTarget()`
samples `IParameter.getValue()` and restores with
`setValueImmediatly((int) Math.round(value))`. `SnapbackSession` captures `snapshot.value()`; the
parent lease copies that baseline rather than capturing independent normalized host precision.
Thus the current contract cannot guarantee preservation of arbitrary sub-controller-step values.
Exact two-state Boolean restoration does not establish continuous-parameter precision.

Keep encoder response/sensitivity units separate from restoration units. The correction should
retain normalized host baselines, or an opaque parent-owned full-precision baseline, restore through
the same exact target fence, and compare later host observations at that precision. It must not
introduce a pinned offscreen pool or invent Device identities to solve a value-units problem.

## Removal criteria

Retain existing behavioral coverage for multiple targets, delayed mutation/read-back, release
ordering, proxy rebinding, timeout and fault cleanup. Add precision regressions starting at values
that cannot be represented on the controller grid; separately advance the fake host so submission
cannot acknowledge itself. Verify exact before/after host values through the routed live path.

Delete this finding once supported targets preserve full host precision and their cancellation,
timeout and cleanup-addressability rules are documented in permanent architecture. Arbitrary
Device coverage or offscreen continuation is not a prerequisite for this bounded correction.
