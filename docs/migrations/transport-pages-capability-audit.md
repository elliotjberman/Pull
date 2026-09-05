# Metronome and Automation capability audit

Status: implemented after the Track/Undo checkpoint. Retained core button views and full-display
page views now own both controls, mode choices, metronome volume, and all feedback. The two permanent
button commands are inert, their lights are core-only, and TRANSPORT/AUTOMATION use the generic
`CorePageMode` adapter. The four legacy Push command/page implementations were deleted. This is a
bounded canopy expansion; first live verification remains pending.

## Routed behavior to preserve

The permanent registrations are in `PushControllerSetup`; the command bodies alone do not describe
the result. `AbstractHwButton.dispatchLegacyRelease()` suppresses command UP when a button was
consumed, while LONG is still dispatched. Modifier state is read at each dispatched edge.

| Input | Actual current behavior |
| --- | --- |
| Metronome plain short release | Toggle metronome; if TRANSPORT is already visible, restore the previous mode instead. |
| Metronome Shift release | Toggle metronome tick playback. This does not close an already visible TRANSPORT page. |
| Metronome plain LONG | Consume METRONOME and select temporary TRANSPORT. The matching physical release never reaches the command, so the page remains latched. A later plain short press/release closes it. |
| Metronome Shift LONG | No action. Removing Shift before release invokes the plain-release behavior; modifiers are not captured at DOWN. |
| Automation plain release | Toggle Automation Write. |
| Automation Shift release | Legacy launcher-automation toggle; API 25 exposes the supported current Automation Write actuator under its historical arranger name. Shift still changes the enabled button color. |
| Automation LONG, plain or Shift | Set the command's restore flag and select temporary AUTOMATION. A release without Delete restores through the mode manager. |
| Automation with Delete | Every edge consumes DELETE and bypasses normal automation handling; DOWN resets automation overrides. Delete appearing only at release suppresses the pending restore. |

Automation DOWN clears its restore flag only when Delete is absent. A long press which started with
Delete can open the page if Delete is released before LONG. A later release after another mode was
selected still calls legacy `restore()` when the flag remains set. Preserve these routed latch and
restore quirks during migration; do not silently replace them with a safer but different gesture.
Other modifiers add no command variants.

Metronome light is monochrome intensity 30 when disabled and 127 when enabled, independent of held
state. Automation light is grey when disabled, red when enabled without Shift, and amber when enabled
with Shift. These are host read-back states, not evidence that the latest toggle succeeded.

## Complete page surfaces

Both pages replace the full parameter display and lower footer, including VS Live's track strip.
They retain the selected Session/Drum/Note grid, navigation, note routing, and raw ribbon lifecycles.

`MetronomeMode` / TRANSPORT:

- Lower ROW1 buttons 1–4 select pre-roll lengths 0, 1, 2, or 4 measures; button 6 toggles whether the
  metronome is audible during pre-roll. These execute on UP. Remaining lower and all upper buttons
  are inert and off.
- The display shows None / 1 Bar / 2 Bars / 4 Bars, the pre-roll metronome Yes/No choice, and volume in
  column 8. Selected choices are white; available unselected choices are grey, from host read-back.
- Encoder 8 controls metronome volume. Encoders 1–7 are empty. Neither this mode nor its base classes
  implements knob-touch/reset/stop-automation-on-release semantics; `AbstractMode.onKnobTouch()` is
  empty. Do not import Track/Project touch semantics into this page.
- Its ordinary ranged-parameter increment still uses the configured normal/Shift sensitivity and
  base step through `IValueChanger.calcKnobChange()`. Preserve that response using the installed
  encoder-configuration snapshot; the absence of page-specific modifiers does not mean a fixed
  increment independent of Shift.

`AutomationSelectionMode` / AUTOMATION:

- The first four lower ROW1 buttons select READ, LATCH, TOUCH, WRITE, in that order, on UP. The other
  lower buttons, all upper buttons, and all encoders/touches are inert.
- The full display presents those mode choices; selected choices are white and available choices
  grey. READ means Automation Write is disabled. Otherwise the selected option comes from the raw
  host automation-write-mode enum.
- Selecting LATCH/TOUCH/WRITE needs both the desired host mode and enabled write state. The core
  owns that sequence and renders later read-back. The shell must not copy the legacy recipe.

## Existing and missing canopy

Installed: permanent button/encoder/soft-key inputs, ordinary edge routing and generation fences,
button/grid/display output arbitration, compiler-owned display regions, project identity,
metronome enabled state, unified Automation Write state/effect, native notifications, parameter
lease/effect primitives, and a generation-fenced known-mode `SelectControllerModeEffect`.

The small reusable additions are:

1. Transport settings state: metronome tick playback, pre-roll enum, and audible-during-pre-roll
   boolean. These are bounded project transport properties. Add absolute typed settings effects;
   core toggles serialize against later observed state.
2. Raw automation-write-mode state and its absolute enum setter; native reset-automation-overrides
   submission. Both are project-fenced at prepare and apply. Extend the existing Automation host
   domain rather than adding a controller-specific settings host. Keep mode and write-enable
   sequencing in the core.
3. An independent project-scoped metronome-volume parameter slot. Extending GLOBAL with slot 2 and
   capacity 3 is sufficient; the wrapper is already initialization-owned. Preserve tempo/master
   slots and recheck project identity on apply. ACTIVE's mode-relative wrapper classification is
   not the intended long-term identity. The global eight-knob interaction still needs at most
   eight page targets plus the two separate global controls; do not grow its lease pool without a
   real simultaneous-target requirement.
4. Generic mode lifecycle state and operations, described below. No new facet per transport page.

All newly sampled domains must follow complete `DesiredBridgeSubscriptions` replacement. The
existing Automation subscription and new/extended transport subscription publish empty state when
not requested. Eager Bitwig value creation does not authorize unconditional high-rate sampling.

The resolved API 25 source JAR was inspected. Supported nondeprecated methods include
`isArrangerAutomationWriteEnabled()`, `automationWriteMode()`, `resetAutomationOverrides()`,
`isMetronomeTickPlaybackEnabled()`, `preRoll()`, `isMetronomeAudibleDuringPreRoll()`, and
`metronomeVolume()`. Separate launcher automation-write/toggle methods are deprecated. Do not use
`TransportImpl.setAutomationWriteMode()` as the new effect executor: its legacy implementation also
writes the deprecated launcher property. Native void calls are submissions, not acknowledgements.

## Shared page lifecycle

Keep semantic parameter-page selection separate from workspace/grid selection. A page identifies
fixed retained core views and any installed stable mode identifier needed by a remaining adapter.
Compile the finite grid-profile × page-profile combinations, merge only disjoint claims, and keep
the exact existing grid/note/ribbon instances when replacing a page. Both new pages claim the two
display regions and the two soft-key rows; their encoder profiles include deliberately inert slots.
Their registered legacy mode adapters become generic inert mechanical adapters at cutover.

The initiating Metronome and Automation gesture views are retained controller-level views present
in every composition. LONG may replace the page, but END still reaches the same gesture instance.
The permanent router already freezes BEGIN's route and core generation; do not solve this by
registering another callback or by moving gesture ownership into the newly opened page.

Observe visible mode, underlying active mode (`getActiveIDIgnoreTemporary()`), and previous mode.
The current visible `layout.modeId()` alone loses necessary restoration state. Legacy mode
management has **one temporary slot**, not a stack: `setTemporary()` replaces that slot while
retaining the underlying active mode; `setActive()` clears the temporary slot and changes previous
mode; `restore()` either clears temporary mode or selects previous mode. Preserve this distinction.
Advance the layout generation when any observed mode-state component changes, including a change
to underlying or previous mode that leaves the visible identifier unchanged.

Expose bounded generation-fenced operations for selecting a known mode, selecting one temporarily,
and restoring. These are generic mode-manager primitives; the core decides when each is appropriate.
The existing absolute select effect alone cannot reproduce temporary metadata because it invokes
`setActive()`. Keep the installed mode registry finite and reject unknown mode identifiers.

Core page state records the exact semantic base page/Track subpage, requested operation, initiating
gesture, workspace/page revision, and pending mode read-back. A release arriving before entry
read-back must remain pending until that operation is observed or invalidated. Compose and render
the resulting page from observed mode state rather than assuming an emitted effect succeeded.
When another page is selected during a held Automation gesture, preserve the characterized legacy
restore behavior; a blanket "only restore if still my page" check would alter it.

Persist selected page and underlying semantic base page at a quiescent core handoff, including a
latched TRANSPORT page. Do not persist or transfer active gestures to a new core generation. This
bounded page transition bookkeeping does not address the separately parked general quiescence work.

## Validation and cutover

Before exclusive admission, characterize through the real physical button path, including consumed
release handling. Cover mutable Shift/Delete, the Metronome latch, both nested long-button release
orders, a prior temporary page, normal Track/Project/Master/legacy Device entry, another page or
workspace selected during a hold, and release before later mode observation.

Separate requested settings from host advancement and later snapshot/output. Test stale project and
mode-generation rejection at prepare and apply; serialized setting toggles and raw-mode/write-enable
ordering; exact independent volume targets; full-page ownership and footer removal; unchanged held
Drum/Session/ribbon lifecycle instances; replay and quiescent reload; and failure leaving deleted
policy inert. Generic inert adapter registration and exact button/kind admission must land with
the complete core action, lights, and display. Leave every other legacy page unchanged.

Focused offline tests cover the retained gestures, later read-back, native property submission and
project fencing, complete page claims, and palette translation. Run the complete package build
with deprecation reporting. First live verification is still required
under `tools/with-pull-live`, through real routed buttons and later settings/parameter/controller
output observation. The migration has not yet changed the live Bitwig extension or project.

## Semantic admission and held continuation

Both global buttons declare `SWITCH_PARAMETER_CONTEXT` with `ACTIVE_PARAMETERS` invalidation at
physical BEGIN. The original resolved action waits behind Snapback; its gesture already exists,
so LONG and END record their own modifier, project, and exact mode-origin decisions while BEGIN
is deferred. `DeferredButtonAdmission` bounds live continuations to the semantic queue capacity
plus the current physical gesture, invokes admission only once, and invalidates old callbacks on
deactivation. Accent uses the same mechanical core helper. Product decisions remain in each view.

Metronome retains its consumed-release latch. Automation records actual entry submission time at
admission, and an early END cannot request RESTORE until a later snapshot identifies the temporary
AUTOMATION page. An unrelated layout generation or an ordinary AUTOMATION page is insufficient.
Five seconds without the required observation abandons the pending return; timeout does not imply
successful entry. Delete-BEGIN preserves the inherited return flag and the exact prior pending
entry, including the legacy case where Delete was released before Automation END.

Automation consumes an already-held Delete gesture immediately at the original BEGIN using the
bounded `ResolvedControllerAction.withImmediateConsumption` input-lifecycle mechanism. This occurs
before Snapback admission because physical Delete END may arrive first. The actual automation reset,
page selection, and write toggles remain deferred; replaying the queued action does not repeat that
consumption. LONG/END still read Delete at their own phase and consume it immediately when applicable.
No stable product policy or general shell quiescence mechanism is introduced.

## Offline verification update

The focused reactor passed 175 core tests and 23 shell tests after the semantic-barrier correction,
including real core routing through Snapback, authoritative restoration read-back before admission,
Delete consumption before its physical END, no repeated consumption on queued dispatch, retained
LONG/END decisions, actual temporary-page acknowledgement, expiry, and deactivation. Transport page
and host tests distinguish effect requests from state advancement and rendered feedback.
The final package gate and first routed live smoke remain required; isolated success does not
prove Bitwig startup or the live controller path.
