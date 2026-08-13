---
status: active
created: 2026-08-06
scope: reloadable-core-runtime
remove_when: generation-fenced logical timers are installed and capability-gated in the production shell with a shell/core integration test, or the unexecutable timer DTOs and test support are removed from Core API
---

# Logical Timer DTOs Have No Production Executor

## Observation

Core API 24 contains `ScheduleTimerEffect`, `CancelTimerEffect`, and `TimerElapsedEvent`, and the
deterministic core test host executes them. The production `ControllerRuntimeEnvironment` has no
timer executor and deliberately rejects those effects as unsupported during result preparation.

This mismatch escaped the isolated core tests on 2026-08-06: the remote-project Play animation
emitted `ScheduleTimerEffect`, the fake advanced it successfully, and the live shell rejected its
first result. At the time, a result-preparation rejection also fault-evicted the whole active core,
blanking all core-owned output.

## Current Mitigation

The remote-project animation declares explicit tick demand, is driven from monotonic controller
ticks independent of parameter sampling, and has a regression test that forbids
`ScheduleTimerEffect`. A prepare-time rejection or core exception now quarantines the generation,
preserves its last committed output, and stops invoking the child instead of invalidating all
core-owned output.

Production core behavior must not emit either logical timer effect while this finding is active.
The fake timer facility is test infrastructure for the proposed contract, not evidence of an
installed shell capability.

## Required Decision And Tests

Choose one complete resolution:

- Install a bounded, generation-fenced stable timer executor, advertise it as a shell capability,
  reject candidates that require it when absent, and test a real `RuntimeManager` plus
  `ControllerRuntimeEnvironment` round trip through schedule, replacement, cancellation, and stale
  generation delivery; or
- Remove the timer effects/events from Core API and delete their fake-only executor.

In either case, core tests must not silently provide a capability unavailable to the production
runtime.

## Removal Criteria

Delete this finding once the selected resolution makes the test and production boundaries agree.
Preserve the durable rule that fake hosts cannot grant undeclared production capabilities in the
permanent architecture document.
