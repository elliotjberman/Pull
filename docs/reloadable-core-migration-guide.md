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

1. A press is a request, not proof that Bitwig changed. Render later authoritative read-back.
2. Do not let a fake synchronously apply an effect and then call that read-back.
3. A migrated input has one semantic implementation. Its stable command becomes inert; there is no
   missing-core fallback implementation.
4. Migrate every modifier, long-press, touch, release, and mode-dependent variant before requesting
   `EXCLUSIVE` ownership. Otherwise keep the input observed or leave it stable-owned.
5. Keep permanent hardware bindings, Bitwig objects, observers, actuator pools, effect execution,
   generation fencing, and class loading in the shell.
6. Do not pass `IModel`, `PushControlSurface`, modes, views, or other shell/framework objects into
   the core.
7. Reuse an installed snapshot, effect, input, and output capability when it is sufficient. Do not
   add feature-shaped API fields as a shortcut.
8. A new parent-loaded DTO, effect, subscription, Bitwig proxy/property, exclusive-control
   admission, or output lane requires a shell build/install and Bitwig restart.
9. A behavior change composed only from installed capabilities requires only a core reload.
10. Direct Bitwig API changes must use controller API 21, avoid deprecated calls, and pass the full
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
install/restart once, then put all semantic policy in `pull-core`.

The capability must be reusable. Prefer `VISIBLE_TRACK_BANK` over `TrackSelectionStripState`, and
prefer a generic button-light output lane over `PlayButtonLight`.

### Class C: Not ready as one migration

The legacy command combines variants that require several missing capability families. Split the
work at a real semantic boundary, or first land the shared canopy capability. Do not claim
exclusive ownership of only the easy branch.

## Repeatable implementation workflow

### 1. Trace the complete legacy behavior

Start from its permanent registration in `PushControllerSetup`, then inspect the complete command,
mode, view, light supplier, and configuration paths it invokes.

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
3. Keep a stable light supplier temporarily if it already renders authoritative Bitwig state and
   output ownership has not migrated.
4. Add only that exact control-and-kind pair to the shell's exclusive admission set.
5. Remove the obsolete stable command class if it has no remaining references.

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

### 7. Verify proportionally

Run focused tests while iterating, then:

```bash
mvn -o -Dmaven.compiler.showDeprecation=true package
```

For a core-only change, publish the core and verify its exact build ID becomes active. For any shell
change, install the extension, restart Bitwig once, and perform the documented live smoke test.

## Worked example: migrate the Play button

The Play button is the recommended first transport migration. Its complete legacy behavior is
small and the transport snapshot/effects already exist.

### Capability audit

```text
Feature: Push Play button
Physical inputs and input kinds: PLAY / BUTTON; SHIFT / BUTTON as an observed modifier
Legacy behavior:
  - plain DOWN while stopped: play
  - plain DOWN while playing: stop
  - Shift+DOWN while stopped: stop and rewind
  - Shift+DOWN while playing: no action
  - LONG and UP: no semantic action
Authoritative state: TransportSnapshot.available and playing
Effects:
  - SetTransportStateEffect(PLAYING, enabled)
  - SetTransportValueEffect(POSITION_BEATS, 0) for rewind
Output: existing stable Play light already reads authoritative transport state
Reloadable retained state: none
Existing canopy: input is registered; transport snapshot/effects exist; Shift can be observed
Missing canopy: PLAY/BUTTON is not yet admitted to EXCLUSIVE ownership; stable binding is active
Restart: yes, once, for exclusive admission and the inert permanent binding
Out of scope: general light ownership and unrelated transport controls
```

### Required changes

#### Reloadable core

1. Add `PLAY_BUTTON` to `SurfaceArea`.
2. Add a `PlayControlView` with:
   - `PLAY_BUTTON` as `EXCLUSIVE_INPUT`;
   - `SHIFT_MODIFIER` as `OBSERVE_INPUT`;
   - `BridgeSubscription.TRANSPORT`.
3. Compose it in `DefaultWorkspace`.
4. On Play `BEGIN`:
   - if transport is unavailable, emit nothing;
   - if Shift is not held, request `PLAYING = !snapshot.playing()`;
   - if Shift is held and playback is stopped, request `PLAYING = false`, followed by
     `POSITION_BEATS = 0`;
   - if Shift is held and playback is active, emit nothing.
5. Do not change a local `playing` flag after emitting the effect.

Pseudocode:

```java
if (!isPlayBegin(event) || !snapshot.transport().available())
    return List.of();

if (!snapshot.pressedControls().contains(SHIFT))
    return List.of(setPlaying(!snapshot.transport().playing()));

if (snapshot.transport().playing())
    return List.of();

return List.of(setPlaying(false), setPositionBeats(0));
```

#### Stable shell

1. In `PushControllerSetup`, retain the permanent Play button registration and authoritative
   `t::isPlaying` light supplier, but replace `PushPlayCommand` with an inert action.
2. Add exactly `PLAY/BUTTON` to `PushControllerInputBridge.CORE_OWNED_INPUTS`.
3. Delete `PushPlayCommand` if no references remain.
4. Do not change MIDI bindings, add observers, or move the light into core in this PR.

### Required tests

Core tests should prove:

1. Play is exclusive and Shift is observed.
2. The transport subscription is present.
3. Plain Play requests the inverse of each authoritative playing snapshot.
4. Shift+Play while stopped requests stop then position zero, in that order.
5. Shift+Play while playing emits nothing.
6. Play `END` and `LONG` emit nothing.
7. An unavailable transport emits nothing.
8. An effect does not alter the fake host's transport snapshot until explicit host advancement.

Shell tests should prove:

1. `PLAY/BUTTON` is accepted as exclusive.
2. A different newly requested exclusive control is still rejected.
3. The stable binding performs no semantic transport mutation when core owns Play.

Live smoke test after the one required restart:

1. Play toggles playback.
2. Shift+Play rewinds only while stopped.
3. The Play light follows actual Bitwig playback, including playback changed in Bitwig itself.
4. Reload the core and repeat without restarting Bitwig.
5. Hold Shift across a core reload, release it, and verify no stuck modifier or unexpected Play.

## Remaining transport controls are separate migrations

Do not treat “transport” as one automatically easy PR:

| Control | Current readiness | Missing or complicated behavior |
| --- | --- | --- |
| Record | Already core-owned | Stable authoritative light remains intentionally |
| Play | Ready after the one small shell cutover above | None after cutover |
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
and do not add fallback behavior or duplicate callbacks. Add deterministic core tests plus shell
tests for any canopy change. Run the full API-21 deprecation build and clearly state whether the
first live test requires a Bitwig restart.

Scope: <exact controls and variants>
Out of scope: <explicit exclusions>
Acceptance: <behavior and live smoke cases>
```
