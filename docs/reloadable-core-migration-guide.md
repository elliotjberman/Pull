# Stable-to-core migration guide

Read `AGENTS.md`, [ARCH](../ARCH.md), adjacent [active findings](findings/README.md) and the
[remaining checklist](reloadable-core-migration-roadmap.md). New product policy belongs in core.
Migrate each control's complete action and feedback together; keep only initialization-owned
resources, observations, validation, execution and transport in shell.

## Capability audit

Before every mapping, view, rendering or navigation change, trace its permanent registration in
`PushControllerSetup` through commands, providers, modes, configuration and output suppliers.
Record the audit in the task or PR; create a document only for a durable decision:

```text
Feature and physical inputs/kinds:
Every modifier, long-press, touch, release and mode-dependent variant:
Authoritative state and target identity needed:
Primitive effects and cleanup ordering needed:
Display/light/native-musical ownership needed:
Bounded reloadable state and handoff behavior:
Installed capability coverage:
Missing capability, capacity, selection scope and generation rules:
Restart required and why:
Explicitly out of scope:
```

Inspect the API snapshots/effects, `PushControllerInputBridge` admission,
`BoundedControllerBridge` observation/execution, core compositions and `SurfaceArea` claims.
A no-op mode callback does not prove a permanently bound parameter is inert. Command exclusivity
also does not silence the separate native `NoteInput` route.

| Result | Action |
| --- | --- |
| **A: Core-only** | Required input admission, state, effects and output are installed. Implement in core and hot reload. |
| **B: Bounded capability expansion** | Add the smallest reusable API/shell mechanism and its complete consuming core slice together; install/restart once. |
| **C: Not ready** | Several missing families or an unresolved host contract prevent a complete slice. Split at a real semantic boundary or establish the prerequisite. Leave existing behavior unchanged. |

A missing capability never authorizes a new stable semantic branch or a temporary fallback.
Prefer a visible-bank observation or generic light lane over a callback such as “run this page.”
Do not expose Bitwig, model, view or controller objects across the parent/child boundary.

## Implement and cut over

1. Define a fixed `ControllerView` profile using existing physical `SurfaceArea` regions. Declare
   the required subscriptions, named parameter banks, targets and output. The compiler derives
   complete replayable routes; do not remap arbitrary raw callbacks.
2. Implement policy with [shared interaction cancellation](interaction-lifecycle.md), exact target
   fences and explicit resource cleanup. Returning to an old target must not revive a held gesture.
   Resolve cleanup before any effect or navigation that makes the target unreachable.
3. Render observed host state. Effects are requests; dependent writes and feedback need later
   read-back. Prefer absolute writes; do not infer a toggle's success from its last request.
4. After every semantic variant is covered, keep the physical registration, make the stable
   command inert and admit the exact control/kind for EXCLUSIVE routing. Migrate its output in
   the same slice, then delete obsolete policy/providers. Missing/faulted core stays inert.

Account for every changed shell line as resource creation, observation, validation, execution,
lifecycle safety, hardware translation or policy deletion. Any line choosing a modifier meaning,
view, color, layout or navigation recipe leaves the semantic migration incomplete.

## Verify

Follow [TESTING](../TESTING.md). Prefer real core/router/bridge behavior tests with separately
advanced fake host state. Cover distinct variants, unavailable/rebound targets, cleanup order,
late acknowledgements, resulting output and replacement/fault paths. Keep focused boundary tests
when they prove a separate invariant; remove constructor checks and one-off migration scaffolding
when routed coverage proves the same outcome.

Verify new direct Bitwig methods and overloads against the resolved API 25 JAR; no deprecated calls.
Run focused tests while iterating, then the required gate:

```bash
mvn -o -Dmaven.compiler.showDeprecation=true package
```

For authorized live validation, checkpoint before restart and hold `tools/with-pull-live --owner LABEL`
through exact activation and the complete smoke. Record the source/build identity, routed input,
later authoritative state and transmitted output, with physical/audible limitations explicit.
An earlier build's pass cannot validate a later change. Report pending live checks honestly.

Play is an existing complete slice: one inert permanent binding, core gesture/light policy and
generic transport/project observations, effect execution and output. Use it as a boundary example,
not as a requirement to duplicate its implementation structure.
