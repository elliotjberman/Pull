# Stable-to-Core Migration Guide

## Purpose

This is the execution guide for moving one Pull behavior from the parent-loaded stable shell into
the reloadable core. It is written for an implementation agent that should be able to complete a
small migration without first reconstructing the entire architecture.

For the inventory and recommended order of the remaining work, see
[`reloadable-core-migration-roadmap.md`](reloadable-core-migration-roadmap.md).

The goal is not to move Java files unchanged. Existing DrivenByMoss commands, modes, and views hold
parent-owned Bitwig and controller objects and often register callbacks that cannot be unloaded.
Instead, split each feature into:

```text
stable shell                                      reloadable core
------------                                      ---------------
create Bitwig and Push resources                  interpret normalized input
observe interested host state                     apply gesture and mode policy
publish immutable snapshots       ----------->    request typed effects
validate and execute effects       <-----------    render complete desired output
write hardware output                             retain reloadable feature state
```

## Non-negotiable rules

Read `AGENTS.md` before changing code. In particular:

1. No new or changed product semantics may be implemented in `pull-shell`. This includes mappings,
   modifiers, gestures, navigation recipes, view/workspace selection, colors, light meaning,
   display layout, and animation.
2. Missing canopy coverage requires a reusable Class-B capability expansion or a Class-C stop; it
   never authorizes a temporary stable implementation.
3. A press is a request, not proof that Bitwig changed. Render later authoritative read-back.
4. Do not let a fake synchronously apply an effect and then call that read-back.
5. A migrated input has one semantic implementation. Its stable command becomes inert; there is no
   missing-core fallback implementation.
6. Migrate every modifier, long-press, touch, release, and mode-dependent variant before requesting
   `EXCLUSIVE` ownership. Otherwise defer the migration and leave the existing stable behavior
   unchanged; do not add the requested semantics there.
7. Keep permanent hardware bindings, Bitwig objects, observers, actuator pools, effect execution,
   generation fencing, and class loading in the shell.
8. Do not pass `IModel`, `PushControlSurface`, modes, views, or other shell/framework objects into
   the core.
9. Reuse an installed snapshot, effect, input, and output capability when it is sufficient. Do not
   add feature-shaped API fields as a shortcut.
10. A new parent-loaded DTO, effect, subscription, Bitwig proxy/property, exclusive-control
   admission, or output lane requires a shell build/install and Bitwig restart.
11. A behavior change composed only from installed capabilities requires only a core reload.
12. Direct Bitwig API changes must use controller API 25, avoid deprecated calls, and pass the full
    package build with deprecation reporting.

## The migration decision

Before editing, record a short capability audit using this template. It may live in the task or PR;
retain a separate document only when it contains a durable design decision:

```text
Feature:
Physical inputs and input kinds:
All legacy semantic variants:
Authoritative state required:
Effects required:
Hardware output required:
Reloadable state that must survive a core handoff:
Existing canopy coverage:
Missing canopy coverage:
First-run restart required and why:
Explicitly out of scope:
```

Classify the result:

### Class A: Core-only

All inputs are registered, the shell already admits the required exclusive routes, and every state,
effect, and output surface is installed. Implement and test entirely in `pull-core`.

### Class B: One bounded canopy expansion

The behavior is conceptually reloadable, but one stable resource, executor, exclusive route, or
output lane is missing. Add the smallest reusable capability to `pull-core-api` and `pull-shell`,
put all semantic policy in `pull-core` in the same vertical slice, then install/restart once.

The capability must be reusable. Prefer `VISIBLE_TRACK_BANK` over `TrackSelectionStripState`, and
prefer a generic button-light output lane over `PlayButtonLight`.

Do not narrow scope by leaving a semantic part of the requested behavior in stable code. A stable
adapter may remain only for a separate surface the request does not touch. A control's action and
feedback are one semantic slice and migrate together.

### Class C: Not ready as one migration

The legacy command combines variants that require several missing capability families. Split the
work at a real semantic boundary, or first land the shared canopy capability. Do not claim
exclusive ownership of only the easy branch, and do not implement the feature in stable while
waiting for the missing capabilities.

## Repeatable implementation workflow

### 1. Trace the complete legacy behavior

Start from its permanent registration in `PushControllerSetup`, then inspect the complete command,
mode, view, light supplier, display/grid renderer, and configuration paths it invokes. Use this
audit for every behavior change, including a small visual fix in an inherited stable view; do not
reserve it only for tasks explicitly called migrations.

Record behavior by input phase and modifier. Do not infer behavior from the class name.

### 2. Audit the installed canopy

Check these locations instead of guessing:

- snapshots and effects: `pull-core-api/src/main/java/de/mossgrabers/pull/core/api`
- physical input registry and exclusive admission:
  `pull-shell/src/main/java/de/mossgrabers/pull/shell/runtime/PushControllerInputBridge.java`
- snapshot capture and effect execution:
  `pull-shell/src/main/java/de/mossgrabers/pull/shell/runtime/BoundedControllerBridge.java`
- core view composition: `pull-core/src/main/java/de/mossgrabers/pull/core/runtime/view`
- surface ownership: `pull-core/src/main/java/de/mossgrabers/pull/core/view/SurfaceArea.java`
- deterministic core test harness: `pull-core/src/test/java/de/mossgrabers/pull/core/testing`

### 3. Define one fixed core-owned surface

Add or reuse a `SurfaceArea`. The view declares:

- `EXCLUSIVE_INPUT` for a fully migrated semantic control;
- `OBSERVE_INPUT` for a modifier whose established behavior must continue elsewhere;
- `OUTPUT` only when the shell already provides complete output arbitration for that surface;
- the minimum `BridgeSubscription` set needed for authoritative state.

If the requested behavior changes an output surface that lacks complete arbitration, stop this
step and add a reusable output lane as Class B. Do not edit the stable renderer to produce the new
meaning.

The complete workspace compiler derives replayable input routes and subscriptions from these
claims. Do not mutate routing imperatively.

### 4. Implement pure core behavior

Create a small `ControllerView` with:

- a stable ID;
- fixed claims;
- explicit subscriptions;
- a deterministic `handle(event, snapshot)`;
- replayable `render(snapshot)` output where applicable.

Effects should carry absolute requested state whenever possible. Compute toggles from the supplied
authoritative snapshot, not from a remembered last write.

### 5. Complete stable cutover when required

For an exclusively migrated control:

1. Keep its permanent hardware registration.
2. Replace the stable semantic command with an inert command.
3. Add only that exact control-and-kind pair to the shell's exclusive admission set.
4. If the control has feedback, install reusable output arbitration and make the old stable
   supplier inert; core emits the complete desired output from authoritative read-back.
5. Remove the obsolete stable command or supplier class if it has no remaining references.

Do not add a second MIDI callback or a second hardware binding.

### 6. Test behavior and the real boundary

Keep tests that prove controller-observable behavior or a real shell, Bitwig or reload contract.
Prefer the production core entry point and parent bridge when that path can demonstrate the
outcome. Cover the meaningful phase/modifier variants, unavailable state, requested effect order,
later host read-back and resulting output, plus target changes or replacement where relevant.
Fakes must separate submission from host advancement so they cannot confirm optimistic feedback.

Do not require separate route, claim, constructor or internal lifecycle tests when the same outcome
is already proved through the real core path. Keep a focused boundary test when it exercises a
distinct failure or safety contract that the behavioral test cannot reach; do not mirror the
implementation merely to preserve its current structure. One-off migration fixtures, diagnostic
drivers and capability-audit files need not remain tracked after durable invariants and relevant
evidence have been recorded. Use [TESTING](../TESTING.md) for the repository verification policy.

Before verification, account for every changed `pull-shell` line as resource creation,
authoritative observation, validation/fencing, effect execution, lifecycle safety, hardware/output
translation, or deletion of legacy policy. A shell line that chooses a control meaning, color,
layout, animation, navigation recipe or page behavior leaves the migration incomplete.

### 7. Verify the exact candidate

Run focused behavioral tests while iterating, then the required package gate:

```bash
mvn -o -Dmaven.compiler.showDeprecation=true package
```

For a core-only change, publish and verify the exact build ID. A shell/API change needs extension
installation and a Bitwig restart. Hold the live lease through activation and the complete smoke
test, and verify later authoritative host state and controller output through the routed path.
Record source/build identity, observed behavior and limits; distinguish submitted input from
applied effects, read-back and hardware output.

If live testing is unavailable or deferred, say it was not performed. A previous build's live pass
does not validate later source cleanup. No new install or live run is part of documentation cleanup.

## Reference slice: Play

Play illustrates a complete action-and-feedback migration: one permanent button registration feeds
an inert stable command; core owns every gesture variant and the light's meaning; generic shell
lanes publish transport/project state, validate effects and transmit RGB output. Core renders from
later host state. Adding the missing input admission or output transport is Class B; changing Play
semantics after those capabilities are installed is Class A.

For current control readiness and remaining prerequisites, use the
[roadmap](reloadable-core-migration-roadmap.md). Avoid duplicating that inventory in a feature audit.
