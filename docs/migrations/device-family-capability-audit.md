# Device parameter, chain, and layer capability audit

Status: read-only audit; no Device control is newly migrated by this document. Compared with
`master` at `5537271f575852634a7a94e473eb57a404fd77a8` on 2026-09-05. The Device entry,
Params, Chains, and layer classes inspected below still match that baseline. The working tree's
new mixer/page infrastructure is reusable, but does not establish Device readiness.

Read [ARCH](../../ARCH.md), the [migration guide](../reloadable-core-migration-guide.md), and the
[parameter-target finding](../findings/parameter-target-proxy-coupling.md) before implementation.
Numbers below are physical columns 1–8; ROW1 is the lower row and ROW2 is the upper row.

## Decisive readiness gap

`SELECTED_DEVICE_REMOTE` is a declared eight-slot bank, not currently a working production
cursor-device identity capability. [ParameterTargetHost](../../pull-shell/src/main/java/de/mossgrabers/pull/shell/runtime/ParameterTargetHost.java)
classifies it with `model.getCursorDevice().getID()`. The production
[CursorDeviceImpl](../../pull-shell/src/main/java/de/mossgrabers/bitwig/framework/daw/data/CursorDeviceImpl.java)
inherits [SpecificDeviceImpl.getID()](../../pull-shell/src/main/java/de/mossgrabers/bitwig/framework/daw/data/SpecificDeviceImpl.java),
which returns `""`; [the classifier](../../pull-shell/src/main/java/de/mossgrabers/pull/shell/runtime/ParameterTargetIdentityResolver.java)
rejects blank owners. A fake cursor with an invented nonblank ID masks this gap. The separate
DrumDevice implementation returns a fixed candidate-path identity; that is not an arbitrary
cursor-device instance identity and cannot be substituted here.

The locally resolved API-25 sources (`com/bitwig/extension-api/25/extension-api-25-sources.jar`)
expose `Channel.channelId()`, but no device-instance UUID on `Device`/`ObjectProxy`.
Nondeprecated `CursorDevice.selectDevice(Device)`, `PinnableCursor.isPinned()`, and
`ObjectProxy.createEqualsValue(ObjectProxy)` provide a possible bounded handle mechanism.
`CursorDevice.channel()` reports the channel used to create that cursor; do not infer the actual
owner of an independently pinned device from a subsequently moved track cursor.

A candidate design is one private retained device cursor, with equality values against the main
cursor and each of its eight sibling slots, all created during initialization. A parent-owned
handle generation is admitted only after later equality/existence read-back proves acquisition;
it is invalidated before publishing a different target. Ordinary effects recheck equality at
prepare and apply. Remote controls on that retained cursor can provide exact touch/restore
actuators. This is an investigation design, not a proved API guarantee: establish selection/pin
behavior, replacement/deletion behavior, page coupling, and delayed acknowledgement with a live
API-25 test before claiming it sufficient. One name/position tuple or Java proxy identity is not
a substitute. Do not claim a durable project-wide device ID or arbitrary retained tree.

## Installed topology and reusable capabilities

| Capability | Present boundary and remaining work |
| --- | --- |
| Device proxies | [ModelImpl](../../pull-shell/src/main/java/de/mossgrabers/bitwig/framework/daw/ModelImpl.java) creates a selection-following, user-pinnable device cursor on the user-pinnable model track cursor. It is independent of the private selected-track Note target. |
| Finite windows | [ModelSetup](../../pull-shell/src/main/java/de/mossgrabers/framework/daw/ModelSetup.java) and [SpecificDeviceImpl](../../pull-shell/src/main/java/de/mossgrabers/bitwig/framework/daw/data/SpecificDeviceImpl.java) install 8 sibling devices, 8 displayed remote-page names, 8 remotes, 8 layers, 16 drum pads, and 8 sends per layer/pad. Nested navigation moves these windows; it does not mirror the tree. |
| Device reordering | CursorDeviceImpl separately installs a 100-device channel bank. Its swap operations address adjacent absolute positions in that bank. Capacity and nested-chain correctness need explicit validation; do not call it unbounded. |
| Device state | Existence/name/position/enabled; pin, expanded, remote-section/window visibility, nested/layer/pad/slot applicability, slot names, remote page index/count/names, and bank navigation are already observed inside framework proxies. They are not yet a typed subscribed core Device domain. |
| Parameters | Reuse named slots, exact target refs, relative/normalized/reset/enabled effects, touch acquisition/release, dynamic bindings, and Snapback. Repair device handle classification first. Add named selected-layer roles and eight visible-layer volume/pan/send windows; do not use `ACTIVE` as the new policy contract. |
| Controller page lifecycle | Reuse registered inert `CorePageMode`, installed mode ID, exact retained backgrounds, SELECT/TEMPORARY/RESTORE effects and later layout acknowledgement. Core needs shared Device subpage state and layer-mode preference. |
| Tracks and scene arrows | Reuse current-track snapshots/actions and scene navigation. Device lower-row behavior differs from the normal Track footer, so reuse primitives rather than copying that footer wholesale. |
| Feedback | Existing two row light lanes and complete display region claims suffice mechanically. Core must own menu/device/page names, selection color, parameter geometry, layer strips, empty-state messages, meters and touch emphasis. |

Add separate on-demand subscriptions for bounded Device context, remote-page/sibling windows, and
layer/pad windows. Publish empty typed state when unrequested. Parameter-only reconciliation can
run ahead of those windows: compare explicit device handle/page and layer channel owner/role in
rendering, mutation, touch BEGIN, and dynamic parameter bindings. Existing exact touch END must
survive a mismatched current snapshot. Pinning is intentional Device policy; private selected-track
Note applicability must not silently redirect a pinned Device page or be inferred from it.

## Device entry and Params

Sources: [DeviceCommand](../../pull-shell/src/main/java/de/mossgrabers/controller/ableton/push/command/trigger/DeviceCommand.java),
[DeviceParamsMode](../../pull-shell/src/main/java/de/mossgrabers/controller/ableton/push/mode/device/DeviceParamsMode.java),
[BaseMode](../../pull-shell/src/main/java/de/mossgrabers/controller/ableton/push/mode/BaseMode.java),
[AbstractParameterMode](../../pull-shell/src/main/java/de/mossgrabers/framework/featuregroup/AbstractParameterMode.java).

| Surface | Complete current variants |
| --- | --- |
| Device button DOWN | Already Params: set its `showDevices=true`. From a layer mode or Chains: select Params without arming momentary return. From another mode: save the underlying non-temporary mode, select Params, arm return eligibility. No modifier-specific branch. |
| Device LONG / UP | LONG arms restore only if DOWN switched from a non-device mode. UP restores the saved mode only for that armed long gesture, then clears all gesture fields. Short press leaves Params selected. Preserve held-entry acknowledgement and exact prior composition, including release before mode acknowledgement. |
| Device light | `Modes.isDeviceMode(activeID)`: Params and layer modes illuminate; Chains is explicitly handled on input but is excluded from this predicate. Do not silently include Chains during parity migration. |
| Eight turns / touches | Bound remote parameters use ordinary ranged response and configured sensitivity, not mixer-volume acceleration or pan detent. Touch stores emphasis; Delete BEGIN consumes Delete and resets, then still touches; END applies configured automation-stop policy. Missing remotes and repeated edges need characterization through real binding dispatch. |
| ROW1, device-list subpage | Existing current-bank track required. LONG toggles arm and consumes that row. UP precedence: Duplicate; Delete; Record arm; Select consume + `toggleMultiSelect` (currently no-op); otherwise select unselected track, or selected group Shift-expand / plain expand-and-enter. Selected nongroup does nothing. This differs from normal Track's LONG-parent and selected-track Device jump. |
| ROW1, bank subpage | UP selects the visible remote page when the cursor device exists. Other phases and modifiers have no special branch. |
| ROW2, device-list subpage | UP only, existing sibling required. Duplicate precedes Delete. A different sibling selects; the selected sibling without layers switches to banks; with layers it selects layer 0 if none is selected and enters the remembered layer mix mode. That dependent sequence needs later host acknowledgement. |
| ROW2, bank subpage | DOWN only: enabled; remote-section visible; expanded; toggle Chains/Params; show devices; pin; window; Up. Device-required entries no-op when absent; Chains and Up still have their own absence behavior. Boolean feedback comes from later host state. |
| Display / row lights | Device-list: device names/selection above, current-track names/type/color/selection below, remotes in the body. Lower lights off for absent/inactive tracks, red if armed, otherwise track color; upper devices selected orange, existing dim yellow. Bank subpage: eight page names below, selected orange/existing dim yellow; upper menu reflects enabled, UI flags, pin/window and Up. Body includes formatted/modulated value and physical touch emphasis. |

`showDevices` persists on the mode instance across ordinary page departures; it is not reset in
onActivate. Several entry paths explicitly override it. The core should retain/checkpoint this
bounded subpage state rather than let an inert adapter own it.

Up has a semantic hierarchy: Chains → Params; no device → Track entry; banks → device list;
nested device → request parent, then inspect the later parent and choose layer mix or Params,
set Params' banks subpage, and select that device's channel; top level → Master or Track entry.
The current implementation waits a fixed 300 ms after `selectParent()`. Replace that timer recipe
with a bounded acknowledged core transition and generic exact navigation primitives. Merely
moving the same callback into a reloadable scheduler would preserve the race, not the contract.

## Chains and routed navigation

[DeviceChainsMode](../../pull-shell/src/main/java/de/mossgrabers/controller/ableton/push/mode/device/DeviceChainsMode.java)
changes ROW1 UP to selecting one of the first eight named slot chains, then Params; ROW1 LONG
consumes the key and moves up. It draws host flags above slot names and an empty parameter body.
It overrides knob callbacks with no-ops, but inherits Params' actual parameter provider and
onActivate binding: [HwRelativeKnobImpl](../../pull-shell/src/main/java/de/mossgrabers/bitwig/framework/hardware/HwRelativeKnobImpl.java)
directly changes a bound parameter without calling `onKnobValue`. Do not infer inert turns from
the override. Characterize the real binding path before deciding how to preserve or correct it.

There are two further source-derived Chains inconsistencies, not live-reproduced findings:
its inherited `showDevices` starts true, so its upper row invokes inherited device-selection
behavior despite displaying the host menu; its color override delegates lower-row colors to
Params, then tests upper-row `index=-1` against slot count, making that comparison always true.
Treat an intentional correction separately from proving migration parity.

[PushCursorCommand](../../pull-shell/src/main/java/de/mossgrabers/controller/ableton/push/command/trigger/PushCursorCommand.java)
actually sends horizontal presses to `selectPrevious/NextItemPage` even with Shift. Device-list
Params therefore pages siblings normally and swaps the current device with Shift. Its Shift light
predicate instead calls `hasPrevious/NextItem`, i.e. sibling scrollability; it does not use the
index-based swap predicate in `hasPrevious/NextItemPage`. Bank-subpage horizontal presses move
eight remote pages through ParameterBankImpl, clamped to bounds, including with Shift. Chains
inherits this navigation through its own subpage state. Up/down arrows remain current-track scene
step/page (Shift) navigation. The physical PAGE_LEFT/RIGHT pair is a separate Session/sequencer
path, not these Device hierarchy buttons. Do not collapse these into one generic 'previous' recipe.

## Layer mixer pages

Sources: [DeviceLayerMode](../../pull-shell/src/main/java/de/mossgrabers/controller/ableton/push/mode/device/DeviceLayerMode.java),
[Volume](../../pull-shell/src/main/java/de/mossgrabers/controller/ableton/push/mode/device/DeviceLayerVolumeMode.java),
[Pan](../../pull-shell/src/main/java/de/mossgrabers/controller/ableton/push/mode/device/DeviceLayerPanMode.java),
[Send](../../pull-shell/src/main/java/de/mossgrabers/controller/ableton/push/mode/device/DeviceLayerSendMode.java).

The mode and provider independently observe `hasDrumPads`: choose the cursor's 8-layer or
16-pad bank. For pad selection index >7, display/column parameters use offset 8; otherwise 0.
This is the cursor-device pad bank, not the private selected-track Drum Controller candidate.

| Surface | Complete current variants |
| --- | --- |
| Selected-layer turns | Knobs 1/2 volume/pan, 3/4 empty, 5–8 sends 1–4 of the selected layer's current send window. Ordinary ranged response applies; providers do not wrap these with the removed normal Track curves. |
| Selected-layer touches | No selected layer: return. Delete BEGIN consumes Delete, resets its mapped role (3/4 have no role), and **returns before touch acquisition and send-enable policy**. Other BEGIN/END touch roles and run automation release. Shift+Select BEGIN on an actual send additionally consumes Select and toggles enabled. Preserve that Delete early return. |
| Column-page turns / touches | Volume/Pan/eight registered Send modes address the visible 8-layer/pad-half columns. Missing layer: return. Delete resets then still touches; END uses automation release. Send pages also Shift+Select toggle actual send enablement after reset/touch. All eight registered Send roles need classified targets, even though the four visible menu entries select only Send1–4 relative to the current send window. |
| ROW1 | DOWN inert. UP validates cursor/layer existence, selects an unselected visible layer; selected layer with devices enters it, selects Params, and sets `showDevices=true`; selected empty layer does nothing. LONG consumes the row and moves up even without an existing target. Modifiers add no independent duplicate/delete/arm behavior here. |
| ROW2 DOWN | 1/2 toggle all-layer Volume/Pan versus selected-layer Mix; 3 unused; 4 pages every layer's send bank (Shift previous with wrap; plain next with wrap); 5–7 choose/toggle send-column pages; 8 Shift chooses send 4, otherwise Up. Persist layer mix preference whenever a layer mode is selected. Other phases inert. |
| Layer Up | Missing device enters Track; otherwise Params + select device channel + `showDevices=true`. Mode/channel requests need identity fences and later state alignment. |
| Arrows | Inherit AbstractParameterMode bank-page operations horizontally; both plain and Shift dispatch those operations. Shift lights ask bank item scrollability, plain lights page scrollability. Vertical arrows remain current-track scenes. |
| Feedback | Complete shared menu and 8-layer strip; activated/selected layer colors; selected-layer volume/pan/four sends and optional stereo VU; Volume has per-column VU; Pan and sends use mixer geometry. Values/modulation, color/activation and enabled state must agree with the same layer target. Empty-device/no-layers/no-existing-layer messages retain Up. |

Menu details matter: send names come from layer-bank item 0; range label uses its first send's
absolute position; column 8 displays Up unless Shift or knob 8 touch reveals its send label.
Menu selection uses the active mode while hardware upper lights illuminate only active Volume,
Pan or send entries; the Up label is not itself an illuminated upper key in that policy.
The send-page loop uses `offset + i` for all `bank.getPageSize()` items, including a 16-pad bank
with offset 8. Characterize the upper-half out-of-range behavior before porting; do not silently
invent an expanded bank to accommodate the loop. Wrapping previous sends uses `itemCount / 4 * 4`,
which can equal the count; later host clamping must be tested independently of request submission.

## Details, Color, and Browser dependencies

[SelectCommand](../../pull-shell/src/main/java/de/mossgrabers/controller/ableton/push/command/trigger/SelectCommand.java)
toggles a temporary Track/Layer Details page on unconsumed UP, is inert for Browser, and asks the
active grid to update its note mapping on every edge. Preserve consumption for layer send chords.
[DeviceLayerDetailsMode](../../pull-shell/src/main/java/de/mossgrabers/controller/ableton/push/mode/device/DeviceLayerDetailsMode.java)
uses a **different** bank rule: active DRUM → cursor 16-pad bank, DRUM64 → separate 64-pad candidate,
COLOR → retain prior bank, otherwise cursor layers. ROW1 UP columns 1/3/4 toggle activation/mute/solo;
8 opens layer color selection. ROW2 UP 7/8 clear all pad mute/solo only for drum banks. Other rows
and knobs are inert; row colors and option display reflect these exact capabilities. Horizontal
bank navigation is inherited. [ColorView](../../pull-shell/src/main/java/de/mossgrabers/framework/view/ColorView.java)
casts the installed Details mode to retrieve that retained bank; migrating Details also requires
an exact color-target lifecycle and removing that product-specific cross-class dependency.

[BrowserCommand](../../pull-shell/src/main/java/de/mossgrabers/framework/command/trigger/BrowserCommand.java)
and Add Device are separate complete slices. Browser UP: Shift inserts before, Select inserts
after, plain replaces; in a layer mode insertion targets the selected cursor layer; without a
device it uses selected current-bank track or selected Master. While browsing, plain commits and
Shift cancels. [DeviceBrowserMode](../../pull-shell/src/main/java/de/mossgrabers/controller/ableton/push/mode/device/DeviceBrowserMode.java)
also commits on deactivation. It owns both rows, eight touches/turns, content-type navigation,
48-item result/filter list display and preview. These are not covered by remote-parameter banks.
Retain this frozen surface unchanged until its insertion/session/output capability audit is done;
new Device page entry must explicitly preserve Browser commit/departure behavior.

## Implementation order and acceptance

1. Prove a bounded Device identity/acquisition canopy first. Characterize missing IDs, duplicate
   names, same-slot replacement, nested selection, track/device pinning, deletion and project
   changes. Publish no selectable/writable handle before later acquisition read-back.
2. Implement shared core Device state and the closed Params + Chains + layer Mix/Volume/Pan/Send
   family, plus Device entry and page arrows. These classes directly call each other's
   `setShowDevices`; deleting Params policy alone leaves a stable semantic backdoor. Reuse generic
   current-track actions but implement its different ROW1 LONG and selected-nongroup rules.
   Keep Details/Color and Browser as explicit frozen destinations until their full surfaces migrate.
3. Add exact bounded Device/property/navigation effects and layer parameter banks. Avoid effects
   named after controller recipes such as 'move up and choose page'. Core submits parent/select
   requests, waits for authoritative target change, then chooses the next mode. Queued transitions
   must cancel on contradictory user navigation; cosmetic menu state is separate from host state.
4. Migrate Select/Details/Color together with retained color target, grid ownership and return
   lifecycle; migrate Browser/Add Device with bounded insertion targets and commit/cancel sessions.

This is a Class-B canopy expansion and requires a shell install/restart before core-only iteration.
The next useful independent work is the Device identity host/proof and routed characterization,
not an exclusive eight-knob or Device-button shortcut. No device subtree recursion, generic pinned
pool, or unsupported device ID should be promised by this audit.

Offline acceptance covers every table branch, all eight registered Send roles, both drum halves,
mode-entry LONG/END before host acknowledgement, snapback before navigation, modifier consumption,
mixed sampling epochs, exact touch cleanup, output read-back and missing/faulted-core inertness.
Host tests must advance selection/page/property values separately from requests. Verify exact
API-25 overloads before adding direct calls and run the complete deprecation-enabled package gate.
Under the singleton live lease, verify empty track/device, ordinary device remotes, >8 devices/pages,
nested layer/slot/drum containers, pinning across selection, reorder bounds, touch+navigation,
Browser departure and core reload. No live result is claimed here.
