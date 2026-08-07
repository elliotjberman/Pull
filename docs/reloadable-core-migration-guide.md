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
12. Direct Bitwig API changes must use controller API 21, avoid deprecated calls, and pass the full
    package build with deprecation reporting.

## The migration decision

Before editing, write a short capability audit using this template:

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

### 6. Test the state boundary

At minimum, core tests cover:

- the compiled route mode and subscription set;
- every phase/modifier branch;
- unavailable state;
- effect ordering;
- no optimistic snapshot mutation after an effect;
- a later host snapshot causing the authoritative rendered state;
- reload/checkpoint behavior for any retained gesture or mode state.

Shell tests cover any new exclusive admission, snapshot capture, effect validation/execution, and
identity fencing. A fake host must separate command submission from host advancement.

Before verification, account for every changed `pull-shell` line as one of: resource creation,
authoritative observation/snapshot publication, validation/fencing, effect execution, lifecycle
safety, generic hardware/output translation, or deletion/inerting of legacy policy. If a shell line
chooses a control meaning, color, layout, animation, navigation recipe, or workspace behavior, the
migration is incomplete.

### 7. Verify proportionally

Run focused tests while iterating, then:

```bash
mvn -o -Dmaven.compiler.showDeprecation=true package
```

For a core-only change, publish the core and verify its exact build ID becomes active. For any shell
change, install the extension, restart Bitwig once, and perform the documented live smoke test.

## Worked example: migrate the complete Play slice

Play is the reference for a complete vertical migration, not for split ownership. The historical
input-only version left its light policy stable and forced another shell restart as soon as Play
became project-aware. Do not repeat that sequence.

### Capability audit

```text
Feature: Push Play action and authoritative light
Physical inputs and input kinds: PLAY / BUTTON; any observed modifiers used by the preserved behavior
All semantic variants: every BEGIN/LONG/END and modifier branch in the legacy command
Authoritative state: transport plus any project/engine identity required by the requested meaning
Effects: typed transport/project requests with apply-time identity fences
Hardware output: generic core-owned button-light lane for PLAY
Reloadable retained state: only state required by the requested transaction or feedback policy
Existing canopy coverage: normalized input and available transport/project snapshots and effects
Missing canopy coverage at first migration: exclusive PLAY admission, inert stable binding, and
  generic button-light arbitration
Restart: yes, once, for those reusable parent-loaded mechanisms
Out of scope: unrelated transport controls whose action or feedback meaning is unchanged
```

This is Class B. The generic Play input admission and button-light lane are stable mechanisms; Play
semantics are not.

### Required changes

#### Reloadable core

1. Declare the complete Play input and output surface.
2. Request only the authoritative transport/project subscriptions needed by the behavior.
3. Implement every input phase and modifier variant.
4. Emit typed effects without mutating the supplied snapshot optimistically.
5. Render the complete Play light from later authoritative read-back, including every color/state
   distinction introduced by the request.
6. Retain only reloadable transaction state; checkpoint it only when replay is safe.

#### Stable shell

1. Keep the permanent Play hardware registration but replace its semantic command with an inert
   binding.
2. Admit exactly `PLAY/BUTTON` to exclusive core routing.
3. Observe and publish the reusable authoritative state required by the core.
4. Validate and execute typed effects with live identity fences.
5. Arbitrate the generic Play light lane and translate the core RGB value to Push hardware.
6. Remove or inert the legacy Play light supplier. The shell must contain no white/green/purple,
   engine-owner, modifier, or navigation policy.

### Required tests

Core tests prove every phase/modifier branch, requested effect order, output color/state derived from
authoritative snapshots, no optimistic feedback, and safe reload/checkpoint behavior. Shell tests
prove exclusive admission, inert stable behavior, effect validation, output-lane validation, and
mechanical palette translation. The fake host advances transport/project state explicitly after
submission.

The first live smoke test follows the installed behavior through action, later Bitwig read-back,
light output, and a core reload without another restart. Any later change to Play meaning or color
that uses the installed canopy must be core-only.

## Remaining transport controls are separate migrations

Do not treat “transport” as one automatically easy PR:

| Control | Current readiness | Missing or complicated behavior |
| --- | --- | --- |
| Record | Core-owned action and light | Preserve complete modifier and authoritative feedback behavior |
| Play | Core-owned action and light | Current project-aware transaction is documented in `ARCH.md` |
| Metronome | Plain toggle is representable | Long-press temporary Transport mode and its display |
| Automation | Partial transport state exists | Reset-overrides action, Flip Record configuration, long-press mode/display |
| Play-position knob | Absolute position effect exists | Relative stepping policy, Select+loop-length behavior, touch notifications |
| Tempo knob | Tempo state/effect exists | Relative/rastered policy, Shift+shuffle, touch display semantics |
| Tap Tempo | Input exists | A typed tap-tempo effect and any tempo indication semantics |

Split these only at complete semantic boundaries. If exact legacy behavior is required, add the
shared capability before taking exclusive ownership.

## Agent handoff template

Use this compact task description when assigning the next migration:

```text
Migrate <feature> from stable Push behavior into the reloadable core.

Read AGENTS.md and docs/reloadable-core-migration-guide.md first. Preserve every existing input
phase, modifier, long-press, and mode-dependent semantic branch. Start with the guide's capability
audit and stop before implementation if the feature cannot be completely owned with the declared
scope.

Keep Bitwig/Push resources and effect execution stable; move only policy. Use authoritative
snapshot read-back, make the stable semantic binding inert before requesting EXCLUSIVE ownership,
move the control's feedback with its action, and do not add fallback behavior or duplicate
callbacks. If the canopy is incomplete, add a reusable capability or stop; never implement the
feature in stable. Account for every shell diff as mechanism or legacy-policy deletion. Add
deterministic core tests plus shell tests for any canopy change. Run the full API-21 deprecation
build and clearly state whether the first live test requires a Bitwig restart.

Scope: <exact controls and variants>
Out of scope: <explicit exclusions>
Acceptance: <behavior and live smoke cases>
```
