---
status: active
created: 2026-08-05
scope: controller-action-model
remove_when: cross-cutting policy consumes semantic action intent and no longer infers downstream effects from physical control IDs
---

# Physical Inputs Are Coupled To Semantic Consequences

## Observation

Pull still has places where cross-cutting behavior predicts a semantic consequence from the
physical control that was pressed. The API 13 snapback implementation is the clearest example:
`SnapbackSession` maintains a set of button IDs believed to cause active parameter proxies to
rebind. A press in that set starts restoration and holds the stable command until read-back
acknowledges the retained baselines.

This reverses the intended dependency. A button does not intrinsically rebind a parameter. A view
or mode maps that input to an action such as selecting a track, changing a remote-control page, or
switching a workspace. Executing that action may then invalidate a parameter-target scope.

The distinction caused a live failure during the API 13 migration. Snapback requested routes for
`DEVICE_LEFT` and `DEVICE_RIGHT`, while Push 2 registers those physical inputs as `PAGE_LEFT` and
`PAGE_RIGHT`. The shell correctly rejected the unregistered routes, but rejecting the complete core
result unpublished the reloadable core. Snapback and the core-owned Shift+Session workspace then
appeared to fail together.

## Intended Lifecycle

The controller data flow should be:

```text
physical input
  -> active view maps input to semantic action intent
  -> interaction policies accept, delay, replace, or reject the intent
  -> typed effects request host/controller changes
  -> stable shell executes effects through exact actuators
  -> authoritative host read-back publishes the resulting state
  -> views render that state
```

For target-changing behavior, the relevant relationship is:

```text
semantic action intent
  -> declares the state or target scopes it may invalidate
  -> intersects retained interaction dependencies
  -> waits for restoration only when those sets overlap
```

A physical button belongs only to the first mapping. It must not appear in downstream parameter,
navigation, persistence, or restoration policy merely because its current command happens to
cause those effects.

## Why Post-Rebind Detection Is Not Enough

Observing that a control's target went out of view is a valuable invariant check, but it is too late
to be the primary barrier with the current Bitwig proxy model. By the time a movable proxy reports a
new identity, the old actuator may no longer address the retained target. Restoration through that
proxy would risk mutating the replacement target.

Until Pull has a bounded pinned actuator that remains directly addressable across navigation, the
action must be classified before it executes. An unexpected generation change while a lease is
retained should fail closed and expose missing action metadata; it should not be treated as normal
control flow.

## Consequences Of The Current Coupling

- Renaming or aliasing a physical input can break unrelated interaction policy.
- One semantic action triggered by multiple controls must be duplicated in every physical list.
- One button can have different effects under modifiers, modes, or composed views, making static
  button classification over-broad or incomplete.
- Rebinding policy can accidentally depend on controller layout details that Bitwig does not know.
- Tests can prove a list contains expected button names without proving the action lifecycle.
- A composed workspace cannot safely remap an action without auditing every downstream physical-ID
  check.

## Proposed Model

Views should resolve physical ownership and produce a semantic action before cross-cutting policies
run. The exact Java shape remains a design decision, but the data should resemble:

```java
record ActionIntent (
    ActionId action,
    Set<StateScope> invalidates,
    ActionPayload payload)
{
}

record RetainedInteraction (
    InteractionId interaction,
    Set<StateScope> dependencies)
{
}
```

Example actions and impacts:

```text
SelectTrack(track)             invalidates SELECTED_TARGET, ACTIVE_PARAMETERS
SelectRemotePage(page)         invalidates ACTIVE_PARAMETERS
SwitchWorkspace(workspace)     invalidates ACTIVE_PARAMETERS when bindings change
SetParameter(target, value)    invalidates nothing; it mutates that exact target
SetTempo(value)                invalidates nothing; tempo is fixed
```

Snapback should delay an action when its declared invalidation scopes intersect the dependencies of
one or more retained parameter leases. It should not know whether that action came from Page Left,
a row button, a display gesture, a future footswitch mapping, or an agent-authored composite view.

## Stable Shell And Reloadable Core

The reloadable core should own:

- Mapping physical input to semantic action intent in the active view/workspace.
- Modifier, composition, conflict-resolution, and interaction policy.
- Action impact declarations and ordering decisions.
- Selection of typed effects after an action is admitted.

The stable shell should own:

- Permanent physical registration and normalized input delivery.
- Bounded Bitwig proxies, exact actuators, and target generations.
- Validation and execution of typed effects.
- Authoritative snapshots and unexpected-generation safety checks.

During migration, a stable-owned command that can invalidate a target needs semantic action
metadata at its dispatch boundary. This is temporary compatibility for an unmigrated action, not a
reason to move a physical-button list into stable. As each action migrates, its stable command
becomes inert and core becomes the only owner of its meaning.

## Current Safe Compromise

API 13 still uses an audited physical-button set to defer known proxy-rebinding commands. The set is
bounded, uses the actual registered Push 2 IDs, and restores before allowing stable navigation. Live
target generations are rechecked and unexpected rebinding fails closed.

This is acceptable as a temporary snapback boundary, but it is not a reusable action model. New
features should not add another physical-ID consequence list. Adjacent migration work should move
navigation and selection toward semantic intents and delete entries from the temporary set as their
actions acquire explicit ownership.

## Investigation And Migration Steps

1. Inventory stable and core navigation, selection, page, mode, and workspace actions that can
   change active parameter bindings.
2. Define a small semantic action envelope and bounded invalidation scopes in core.
3. Route core-owned workspace and navigation behavior through that envelope before effects are
   emitted.
4. Add action metadata at the dispatch boundary for remaining stable-owned commands.
5. Make retained interactions declare their target dependencies and gate actions by scope
   intersection.
6. Treat an undeclared target-generation change as a tested safety failure.
7. Delete `SnapbackSession`'s physical rebinding-button set when all relevant actions participate.

## Required Tests

- The same semantic action has the same invalidation behavior when triggered by different physical
  controls.
- A context-sensitive button is gated according to the action selected by the active view, not its
  button ID.
- An action whose impact does not overlap a retained target executes immediately.
- An overlapping action waits for restoration acknowledgement before execution.
- An unexpected target-generation change never mutates the replacement target and identifies the
  missing semantic declaration.
- Remapping a view changes only the input-to-action binding; downstream interaction tests remain
  unchanged.

## Removal Criteria

Delete this finding when views and remaining stable compatibility adapters emit semantic action
intent before cross-cutting policy, every target-changing action declares its invalidation scope,
and snapback or similar interactions gate those actions without consulting physical control IDs.
Move the durable input-to-action-to-effect lifecycle into the permanent controller architecture
documentation in the same change.
