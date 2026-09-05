# Track / Mix button capability audit

Baseline legacy behavior: `e3c35508`, `TrackCommand.execute` and the `ButtonID.TRACK`
registration in `PushControllerSetup`. This slice is the single controller-level Mix button;
it does not claim the selected destination's encoders, rows, or display.

| Input | Requested behavior |
| --- | --- |
| Shift at BEGIN | Toggle the observed controller VU-meter preference. LONG and END do nothing. |
| Plain BEGIN in Track | Select the remembered global mix mode. |
| Plain BEGIN in Volume, Pan, Crossfader, Send 1–8 | Select Track. |
| Plain BEGIN in another mode | Capture the underlying nontemporary mode and select Track. |
| LONG after that last entry | Arm return to the captured underlying mode. |
| END after LONG | Select that captured mode; ordinary short END keeps Track open. |
| Any plain BEGIN with no current-bank selection | Also request selection of exact visible slot zero if it exists. |

Shift is read at BEGIN, unlike the Metronome and Automation release-time variants. Changing Shift
while Mix is held does not change its gesture. Starting from a temporary page captures the manager's
underlying mode, not its visible temporary page or previous-history slot. After an acknowledged
entry, an intervening external page change does not cancel the legacy long-release return.
The light reads the visible host mode: bright for Track, Track Details, Record Arm, Volume, Pan,
Crossfader, and Send 1–8; dim elsewhere. The permanent transport is a monochrome button: RGB
255 maps to MIDI intensity 127 and RGB 60 maps to intensity 30. No requested mode is rendered early.

## Installed mechanism and ownership

The fixed `TrackMixControlView` claims `MIX_BUTTON` input and output, observes Shift, and subscribes
to `CONTROLLER_LAYOUT`, `CONTROLLER_SETTINGS`, and `CURRENT_TRACK_BANK`. It uses existing generic
`SelectControllerModeEffect`, `SetControllerBooleanSettingEffect`, and exact `CurrentTrackTarget`
selection. No proxy, bank, MIDI binding, or feature-specific host recipe is added.

The view declares two semantic BEGIN variants: `SWITCH_PARAMETER_CONTEXT` invalidates
`ACTIVE_PARAMETERS`; `SET_CONTROLLER_PREFERENCE` invalidates the new genuine
`CONTROLLER_SETTINGS` scope. Thus a normal page entry settles Snapback before dispatch, while a
VU preference request does not disturb captured parameters. The BEGIN decision, layout generation,
underlying return mode, and optional first-track actuator identity are captured before deferral.
The stable mode and track executors independently recheck their exact observed origins at apply.

The retained controller-level view receives LONG and END across page compositions. A deferred
BEGIN owns a value-only gesture object, so a release received during Snapback settlement still arms
the corresponding return. Page return is submitted only after a later raw mode observation confirms
Track as the underlying and visible page. This does not mistake void command submission for entry.
A stale deferred page origin is rejected before submission, and an unobserved entry expires after
five seconds without a return. Once entry has been observed, later external page changes preserve
the original return semantics. Pending value-only continuations are bounded by the existing semantic
action queue capacity plus the current physical gesture; there are no acquired Bitwig resources.
VU writes use the existing bounded readback-driven toggle lane, including rapid repeated presses.

The permanent Mix command becomes inert and its light comes exclusively from the committed core
output; only `TRACK/BUTTON` is admitted to exclusive routing. Failed or absent core behavior remains
inert/off through existing ownership quarantine. The destination may still be a declared frozen
legacy page until that independent full page migrates.

## Verification

The focused core reactor passed `TrackMixControlViewTest` (9 tests) and `SnapbackSessionTest`
(9 tests, including genuine preference-scope isolation). These cover the full BEGIN/LONG/END table,
temporary underlying capture, first-track identity,
Shift changes, deferred LONG/END, stale origins, later entry acknowledgement, external-page return,
readback-only lights, rapid VU writes, and discarded old-generation continuations. A shared full
package build and first routed live Bitwig/Push smoke remain required before deployment is accepted.
This slice does not implement the separately parked general reload quiescence work.
