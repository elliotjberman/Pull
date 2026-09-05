# Volume and Pan capability audit

Status: implementation drafted; shared offline verification and first live verification pending.
The behavior source is `master` and checkpoint `e3c35508`; the full adjacent-family inventory is
[remaining mixer pages](remaining-mixer-pages-capability-audit.md). This slice excludes Crossfade,
Send, Track Details, and their distinct value/gesture policies.

## Complete page contract

[GlobalMixerControlsView](../../pull-core/src/main/java/de/mossgrabers/pull/core/runtime/view/GlobalMixerControlsView.java)
owns eight encoder turns/touches, eight upper soft keys and lights, and the 960×143 parameter
display region. Each instance declares the installed mode `VOLUME` or `PAN`; it does not select
the legacy Track facet. Composition reuses the normal
[CurrentTrackFooterView](../../pull-core/src/main/java/de/mossgrabers/pull/core/runtime/view/CurrentTrackFooterView.java)
for all lower-row gestures and feedback, including shared Record consumption and full Session
Stop targeting. The selected grid/note routing remains independent of the parameter page. The installed Volume/Pan
entries are generic inert `CorePageMode` adapters; their old mode classes, providers, and response
wrappers are deleted.

The global page never claims arrows or page keys. Their complete grid-dependent navigation is a
separate core view; the inert page adapter cannot provide the old Shift-swap recipe as a fallback.
The controller-level Mix button is likewise separate from the page body and reads the same
controller preferences. Master replaces the page while retaining the exact grid composition.

| Surface | Preserved behavior |
| --- | --- |
| Volume encoders | Named `TRACK_VOLUME[0..7]`; calibrated delta times `1 + .2 × observed normalized volume` |
| Pan encoders | Named `TRACK_PAN[0..7]`; half calibrated delta; center tolerance and .015 detent while moving toward center |
| Shift while turning | Configured fine sensitivity; ordinary turns use configured normal sensitivity |
| Encoder touch BEGIN | Acquire the exact observed parameter; Delete consumes Delete and requests reset before acquisition |
| Encoder touch END | Release that exact touch and stop active automation writing when configured, including missing parameters |
| Shift+Select touch | No extra Volume/Pan branch; this is a Send-page variant, not common touch behavior |
| Upper keys | BEGIN-only global menu; LONG/END do not repeat selection |
| Same selected menu item | Remember/select that global mode again; never toggle back to Track |

[TrackEncoderResponse](../../pull-core/src/main/java/de/mossgrabers/pull/core/runtime/view/TrackEncoderResponse.java)
now expresses Volume, Pan, and ordinary ranged response roles explicitly, with the existing slot
overload retained. Other global ranged controls do not inherit the Volume acceleration curve.
All values and slider/fader positions are rendered from subscribed host values, including
modulation; input and submitted effects never become feedback values.

## Global menu state

[GlobalMixerMenu](../../pull-core/src/main/java/de/mossgrabers/pull/core/runtime/view/GlobalMixerMenu.java)
preserves columns 0/1/7 for Volume/Pan/Crossfader. Extra send paging begins when the **sixth** model
cursor send (index 5) exists. Without it, columns 2–6 are sends 0–4 and the remembered offset is
reset to zero. With it, offset zero uses columns 2–5 for sends 0–3 and column 6 for `>`; offset four
uses column 2 for `<` and columns 3–6 for sends 4–7. This is deliberately distinct from selected
Track Mix's seventh-send threshold and six send encoders.

Send existence and names affect menu labels/highlights; a blank entry still selects its valid
computed SEND mode. Paging and mode requests emit absolute effects and feedback waits for later
observed preference/layout values. No optimistic local page index is used. The remembered global
mode remains synchronized with the installed controller preference for the remaining entry path.

The menu observes the existing **pinnable model cursor's** send window. It must not substitute the
private selected-track send bank. Track columns, meanwhile, follow `model.getCurrentTrackBank()`
and may show an effect bank while the cursor menu remains pinned elsewhere.

## Bounded parent mechanisms and identity

`CONTROLLER_SETTINGS` gates
[ControllerSettingsHost](../../pull-shell/src/main/java/de/mossgrabers/pull/shell/runtime/ControllerSettingsHost.java):
observed VU preference, remembered mode, send-menu offset, and at most eight model-cursor send names/
existence flags. The cursor metadata carries project/cursor/window generation and offset; it grants
no actuator or arbitrary-track lookup. No cursor, bank, observer, or raw hardware binding is created
on core activation. Absolute boolean/integer/mode preference effects validate their installed type,
range, and registered mode. They contain no menu or modifier recipe.

[ParameterTargetHost](../../pull-shell/src/main/java/de/mossgrabers/pull/shell/runtime/ParameterTargetHost.java)
reuses the installed eight-slot Volume and Pan banks. Ordinary write preparation and application
require the same current bank, project, slot proxy, channel ID, parameter role, and parameter wrapper.
An old touch's cleanup has a separate addressability check: switching the current bank does not
prevent touch-off on its still-exact parameter, but retargeting that proxy or changing projects does.

The existing parameter classifier now publishes
[ParameterTargetIdentitySnapshot](../../pull-core-api/src/main/java/de/mossgrabers/pull/core/api/ParameterTargetIdentitySnapshot.java)
alongside the opaque actuator reference. Global page inputs and graphics require `channel-volume`
or `channel-pan` plus the same channel ID as the current-bank slot. Unknown metadata fails closed.
This is necessary even though both host samplers use the same installed slot proxies:
`applyParameterLeases()` can publish a newer parameter table while retaining older current-bank
metadata. A later full refresh reconciles them. Physical END cleanup still runs through the shared
touch session during a mismatch; the new guard cannot strand that release.

This is not a pinned parameter pool and does not satisfy the broader
[parameter target finding](../findings/parameter-target-proxy-coupling.md). An externally retargeted
proxy remains unavailable for exact cleanup, and stable reports that loss rather than touching its
replacement.

## Display and verification

[GlobalMixerDisplayScene](../../pull-core/src/main/java/de/mossgrabers/pull/core/runtime/view/GlobalMixerDisplayScene.java)
preserves the normal global layout rather than reusing the selected-track Mix layout: Volume has
eight independent stereo meters and vertical faders; Pan has normal `C`/`L n`/`R n` text, horizontal
sliders, and no Track Mix parameter labels. Meter visibility follows the observed preference.
Inactive controls are gray; selected menu/arrow lights follow observed menu state. The footer is
the already migrated normal footer. Core owns every display primitive and color choice.

`GlobalMixerControlsViewTest` covers named-bank claims, role response, fine speed, pan detent,
menu index-5 threshold, 0/4 read-back paging, blank sends, missing settings/parameters, modifier
variants, exact touch/release, changed selection, owner mismatch, and authoritative graphics.
`CurrentTrackParameterBankHostTest` separates writes from host advancement and covers current-bank
handoff, same proxy exposed through another window, project changes, and exact touch cleanup.
`ControllerSettingsHostTest` covers the eight-send bound, pinned cursor identity, absolute requests,
later preference observation, and mode/range admission. `BoundedControllerBridgeTest` reproduces
the immediate-parameters/older-bank snapshot case using the real bridge reconciliation path.

Before claiming live completion, activate the exact package under the shared live lease and verify
Volume/Pan on both main and effect banks, pinned cursor menu names, five/six/eight sends and missing
sends, Shift sensitivity and pan centering, Delete touch/reset, configured automation release,
VU toggling, menu page changes, Master return, and navigation across a held/released touch. Inspect
later Bitwig state plus Push lights/display; submitting a command or building the package is not
proof of controller behavior.
