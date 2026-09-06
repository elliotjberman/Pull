---
status: active
created: 2026-09-05
scope: reloadable-core-runtime
remove_when: all asynchronous operation owners declare and test their replacement lifecycle through a bounded shared contract, including queued toggle intent
---

# Core Reload Does Not Account For Every Pending Operation

## Observation

Source inspection at commit `256d01ce` found a coherent central replacement gate, but no complete
contract requiring every core-owned asynchronous operation to participate. The concrete queued
toggle scenario below is a source-based finding; it has not been reproduced in a test or live
Bitwig. This document records an investigation and proposed direction, not a completed general fix.

[CoreReloadSupervisor](../../pull-shell/src/main/java/de/mossgrabers/pull/shell/runtime/CoreReloadSupervisor.java)
retains the newest candidate until `RuntimeManager.canReplaceActiveCore()` permits activation.
[RuntimeManager](../../pull-shell/src/main/java/de/mossgrabers/pull/shell/runtime/RuntimeManager.java)
checks the gate again, validates candidate startup before replacing the active generation, and
rejects events belonging to an older generation. Rejected candidate preparation preserves the old
core. All of this runs serially on the controller thread.

[ControllerRuntimeEnvironment](../../pull-shell/src/main/java/de/mossgrabers/pull/shell/runtime/ControllerRuntimeEnvironment.java)
currently permits replacement when the parameter interaction has no pending semantic actions,
the physical input lifecycle is idle, and the controller bridge permits replacement.
[PhysicalInputRouter](../../pull-shell/src/main/java/de/mossgrabers/pull/shell/input/PhysicalInputRouter.java)
counts core-relevant gestures, queued motion, and deferred stable dispatches as non-idle. This
already provides a common boundary for physical interactions, but button release does not imply
completion of the host operations requested by that button.

## Bounded Page-Inbox Recovery In API 46

The page migration adds a specific tested owner; it does not implement the general contract below.
`PushControllerPageManager` fences a healthy consumer's pending requests, captured temporary holds,
notifications and projection. A monotonic parent-owned retired prefix records acknowledged or
abandoned requests and bootstraps every new core independently of checkpoint compatibility. Fault
cleanup retires pending work, disables callback admission, and clears captured handles; old delayed
callbacks also expire when their consumer generation is replaced. With no healthy consumer, new
legacy callbacks cannot refill an unserviceable inbox or block recovery.

Routed `RuntimeManager`/bridge tests cover incompatible and discarded checkpoints, quarantine with
pending requests, callbacks during quarantine, replacement, and successful subsequent navigation.
These tests close the page-inbox recovery gap only. They do not establish completion or checkpoint
semantics for queued Mute/Solo/Record intent or every asynchronous owner listed below.

## Suspected Lost Toggle Intent

[AuthoritativeBooleanToggle](../../pull-core/src/main/java/de/mossgrabers/pull/core/runtime/view/AuthoritativeBooleanToggle.java)
stores an expected value and queued toggle parity in core-local fields. A dependent toggle waits
for authoritative acknowledgement of the preceding write before producing its effect.
[SelectedTrackMuteSoloView](../../pull-core/src/main/java/de/mossgrabers/pull/core/runtime/view/SelectedTrackMuteSoloView.java)
registers Mute/Solo requests on the button's `END` event, after which the input router retires the
physical gesture.

The candidate scenario is:

1. Start with a selected track authoritatively unmuted.
2. Press and release Mute; core submits the absolute request to mute it.
3. Before host acknowledgement, press and release Mute again; core queues the opposite toggle.
4. Activate a replacement core while no other gesture or snapback action blocks the gate.
5. Let the host acknowledge the first mute request.

Without replacement, the old toggle lane would now submit unmute. With replacement, the queued
second intent appears to be lost: the gate does not inspect toggle lanes, and
[PullControllerCore.checkpoint](../../pull-core/src/main/java/de/mossgrabers/pull/core/runtime/PullControllerCore.java)
serializes workspace/page selection and engine-owner state, not toggle state. Startup creates
fresh lanes through
[ControllerLevelViews](../../pull-core/src/main/java/de/mossgrabers/pull/core/runtime/view/ControllerLevelViews.java).
The shell receives only the first submitted absolute write; it cannot reconstruct the second
intent from later host read-back. Record/overdub lanes using the same helper need the same audit.

The existing `repeatedSelectedTrackTogglesWaitForEachAuthoritativeAcknowledgement` test in
[PullControllerCoreTest](../../pull-core/src/test/java/de/mossgrabers/pull/core/testing/PullControllerCoreTest.java)
covers queued presses without replacement. Establish the failure with a routed runtime regression
before choosing the implementation. Do not describe this as a confirmed live failure.

## Proposed Shared Lifecycle Contract

Inventory every operation that can outlive one controller event: queued toggle intent, snapback,
gesture/deferred dispatch, layout handoffs, fill leases, note-repeat restoration, and project
commands. Record its owner, bounded capacity, exact target/generation, completion observation,
replacement behavior, cancellation behavior, and fault/exit cleanup.

Prefer a complete replayable, bounded set of lifecycle descriptions or blockers over an anonymous
reference count. A description should identify who is waiting and why; adding an operation must
not depend on remembering another feature-specific condition in the runtime gate. Keep semantic
acknowledgement and cancellation decisions in core, with stable mechanics enforcing the declared
replacement boundary. Do not retain child callbacks or objects in a parent registry.

For newly admitted core-owned operations, draining before normal replacement is the proposed
default. Close an operation only after its defined later host acknowledgement or explicit terminal
outcome, not when the Bitwig API call returns. Design admission while a candidate waits so new work
cannot starve replacement indefinitely, while already-admitted work still receives events and host
observations. Never block the controller thread to wait for itself.

Other lifecycles must be explicit and tested:

- Checkpoint handoff: transfer complete value-only state with compatible schema and target fences.
- Stable ownership: the executor and required observations survive replacement without old core.
- Cancellation: define which intent can be abandoned and any cleanup that must finish first.

A global count of all host activity would unnecessarily block reload. Existing cross-project
transport execution intentionally survives replacement and quarantine; see the checkpoint and
project-operation sections of
[the runtime design](../reloadable-controller-core-design.md) and
`remoteProjectTransportSurvivesCoreQuarantineAndReturnsAfterReadback` in
[BoundedControllerBridgeTest](../../pull-shell/src/test/java/de/mossgrabers/pull/shell/runtime/BoundedControllerBridgeTest.java).
Do not move new product policy into the shell merely to classify it as surviving work.

## Current Precaution And Lifecycle Limits

For deliberate reload during development, let repeated toggle requests settle in host read-back
first. Existing gesture and snapback gates remain useful; they are not evidence that every async
intent is covered. A pending reload should expose named blockers rather than silently waiting.

Normal core replacement can wait while the shell continues processing. A faulted core cannot be
trusted to drain itself: quarantine needs parent-owned cleanup and explicit abandonment rules.
Extension shutdown is different again; API 25 gives `exit()` no asynchronous observation grace
period. Preserve best-effort terminal cleanup and never simulate completion with a delayed task.
Updating the shell itself still requires extension/host lifecycle handling, not a child-core swap.

## Acceptance And Removal Criteria

1. Reproduce the two-press Mute case through the real input router and runtime replacement path,
   then prove both admitted intents finish or follow an explicitly chosen cancellation contract.
2. Separate submitted effects from fake-host advancement, authoritative snapshots, and lights.
   Cover replacement before acknowledgement, between dependent writes, and after completion.
3. Cover Record/overdub, target change, missing acknowledgement/timeouts, rejected candidates,
   stale-generation events, and quarantine; prove no duplicate or misdirected writes.
4. Preserve gesture and snapback barriers and prove stable-owned transactions continue while a
   replacement activates. Test bounded capacity and useful diagnostics for blocked replacement.
5. Audit remaining async owners against the shared contract. Run the appropriate offline checks
   and an exact-build live smoke test, following `TESTING.md`, before claiming controller behavior.

Delete this finding when the inventory is complete, the selected contract closes the toggle gap,
and the boundary tests pass. Move the lasting ownership and replacement rules into `ARCH.md` and
the runtime design; Git history retains this investigation.
