---
status: active
created: 2026-08-05
scope: controller-action-model
remove_when: every stable compatibility command resolves an exact payload-bearing semantic intent and no downstream policy infers consequences from command classes or physical controls
---

# Physical Inputs Are Coupled To Semantic Consequences

## Observation

Cross-cutting policy must consume the action selected by the active view or stable command, not
predict consequences from the button that happened to trigger it. A button can select different
behavior under modifiers, modes, or composed workspaces; target restoration cannot safely use a
static physical-button list.

The required lifecycle is:

```text
physical input
  -> active view or stable compatibility adapter resolves semantic intent at BEGIN
  -> interaction policy admits or delays that immutable intent
  -> behavior requests typed effects
  -> stable executes through exact actuators
  -> authoritative read-back publishes resulting state
  -> views render that state
```

Post-rebind detection is a useful invariant check but is too late to be the primary barrier. A
movable Bitwig proxy may already address a replacement target by then.

## Current Mitigation

Core API 22 carries `ControllerActionIntent` separately from physical input. Core-owned views
resolve complete executable intents at gesture `BEGIN`; deferred execution therefore cannot
reinterpret a released modifier or a replacement workspace. Existing stable-only commands remain
frozen migration debt, but `StableControllerActionResolver` examines the actual installed command
and its current mode path, publishes a typed `ControllerActionEvent`, and holds the same stable
dispatch behind core's declared scope barrier. An absent physical route preserves only that
unchanged legacy behavior; it is not an extension point for new semantics.

This removes the core-side physical navigation table. It does not yet satisfy the final model:

- The Master page now owns its encoder turns and both button rows in core, including exact
  project-identity payloads for project navigation, file actions, and absolute engine state.
- Play is a core-exclusive edge with an inert stable command. Core retains the engine-owning
  project identity and emits exact project-navigation and project-transport payloads; stable only
  validates the live project identity and applies the current-document proxy operation.

- Several stable mode commands expose only coarse command-level meaning, not a payload identifying
  the exact selected track, page, device, or workspace.
- `ButtonRowModeCommand` delegates into an active mode that has no semantic-intent contract, so the
  compatibility adapter must conservatively classify the command.
- Stable compatibility intent is still inferred from command types. It should disappear as those
  actions migrate into core-owned views.

## Target Model

Semantic actions should carry immutable payload and invalidation scopes, for example:

```text
SelectTrack(track)          invalidates SELECTED_TARGET, ACTIVE_PARAMETERS
SelectRemotePage(page)      invalidates ACTIVE_PARAMETERS
SwitchWorkspace(workspace)  invalidates ACTIVE_PARAMETERS when bindings change
SetParameter(target,value)  mutates one exact target; invalidates no target scope
```

Snapback delays an action only when those scopes intersect retained dependencies. It must not know
which Push button, footswitch, display gesture, or agent-authored composite produced the action.

## Required Tests

- A deferred Shift+Session retains its begin-time workspace payload after Shift is released.
- A frozen legacy command publishes semantic intent without converting its absent physical route to
  `OBSERVE`.
- A context-sensitive command is classified from the active command/mode path, not only its button.
- Non-overlapping actions execute immediately; overlapping actions wait for authoritative restore.
- An unexpected target-generation change never mutates the replacement target.

## Removal Criteria

Delete this finding when views and all remaining stable compatibility adapters emit exact
payload-bearing semantic intents, every target-changing action declares its invalidation scopes,
and no interaction policy or compatibility adapter infers semantic consequences from physical IDs
or broad command classes. Preserve the durable lifecycle in the permanent architecture document.
