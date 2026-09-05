# Current-bank Send pages capability audit

Scope: all eight registered SEND1–SEND8 parameter pages, including inherited upper menu, encoder
turn/touch policy, parameter display, current-bank footer and navigation. Source audit completed
before core cutover against `SendMode`, `AbstractTrackMode`, `SendParameterProvider`, and
`TrackMixerComponent`. The global Mix button is already core-owned.

## Complete routed contract

Each fixed page selects one send column across eight tracks in the current main/effect bank. The
send column is relative to each track's current bounded send-bank window, not a project-global
send ID. It is independent of the model cursor's sends used to name the upper menu. Encoder turns
use ordinary calibrated relative adjustment, including configured Shift sensitivity; these are not
the custom selected Track page's delta-times-ten writes or Volume/Pan wrapper curves.

Every encoder touch runs the inherited reset/touch path. Delete BEGIN consumes Delete and resets
the exact parameter before touch-on. END releases the exact acquired target and honors the existing
stop-automation-on-release setting, including absent/no-op parameters and a departed page. A further
Shift+Select BEGIN consumes Select and toggles the send's enabled state. These branches accumulate:
Delete consumption → reset → touch acquisition → Select consumption → enabled request. An empty
send still consumes Select. Multiple enable toggles wait for authoritative readback, and changed
parameter identity cancels the old pending toggle rather than retargeting it.

Upper menu actions run on BEGIN. Reuse `GlobalMixerMenu` unchanged: Volume/Pan/Crossfader selection,
Send 1–5 without pagination, sixth cursor-send existence enabling 0/4 menu offsets, arrows, remembered
global mode, and selection of a valid send page even when its menu name is absent. Lower row and
footer reuse `CurrentTrackFooterView`; normal or Session horizontal navigation remains chosen by
the enclosing composition. A page does not remap raw controller callbacks.

Parameter feedback uses each track's actual send name, host value/modulated value, displayed text
limited to eight characters, and `track.activated && send.enabled`. Absent or misaligned targets
have no parameter graphic. The global menu begins with Volume, so the inherited
`TrackMixerComponent` does **not** print parameter-name labels. It uses the name to choose a widget:
contains `Volume` → fader with zero VU; equals `Pan` or contains `Panning` → slider; otherwise ring.
Preserve that inherited heuristic and record it in the migration friction list for later cleanup.
The value text stays host-formatted even for a pan-named send, and the marker follows modulation.
Track colors supply active control color; inactive controls use the inherited gray treatment.
All feedback follows later host snapshots, not the requested adjustment or toggle.

## Bounded capability and ownership

Add eight named `ParameterBankId.TRACK_SEND1`–`TRACK_SEND8` banks, each exactly eight slots, with
`ParameterBankId.trackSend(sendIndex)` and `ParameterSlot.trackSend(sendIndex, trackIndex)` helpers.
The model already installs finite per-track send windows; no arbitrary device lookup or scanner is
needed. Only requested send columns are reconciled/captured. The host must fence current-bank
identity, track channel identity, relative send role and live send-bank scroll position at prepare
and apply. Exact already-acquired release/restore targets may outlive current-bank eligibility.
`ParameterTargetSnapshot` already provides name/value/modulation/displayed/enabled and classified
identity, so no new feature-specific DTO is needed. The existing `channel-send` identity uses owner
track channel ID, page zero, and the absolute send-bank position in `index`.

The core additionally compares the classified parameter owner with the corresponding independently
observed current-bank track before actions or rendering. A stable bank slot alone is not proof that
the footer and parameter point at the same track. The named bank's host admission owns exact
send-column/scroll-role validation; the core does not invent another absolute send ID.

`GlobalMixerControlsView.send(sendIndex, sharedTouchSession)` provides fixed encoder turns/touches,
upper row and parameter-region ownership. Existing generic inert `CorePageMode` adapters install
SEND1–SEND8. The core composer retains grid/note/ribbon and shared footer lifecycles. Once complete,
the eight legacy SendMode registrations and class are deleted; missing/faulted core feedback stays
blank/inert and no fallback command path is retained.

## Acceptance

Tests must distinguish requested writes, explicit host advancement, and rendered output. Cover all
eight fixed banks, normal/Shift calibration, full reset/touch/Select ordering, absent sends, rapid
enable toggles, departure and exact touch release, changed bank/track/send position between prepare
and apply, parameter/footer disagreement, menu pagination and remembered mode, disabled track/send
graphics, ordinary and name-selected widgets, modulation, and inert failed-core paths. Reuse the
already characterized footer/menu tests rather than copy their policies. A full deprecation-enabled
package build and first routed live smoke remain required before this slice is accepted live.

## Implementation and verification status

All eight Send registrations now use generic inert CorePageMode adapters; SendMode is deleted.
The shared GlobalMixerControlsView owns each fixed column, menu, enabled lane, touches and display.
A dynamic parameter-binding filter also rejects contradictory owners before Snapback captures a
baseline. Truly absent sends preserve the documented no-op-parameter release/consumption behavior;
contradictory available owners reject new touch admission while retaining existing release cleanup.

The focused shared reactor passed SendMixerControlsViewTest (10), GlobalMixerControlsViewTest (12),
MasterButtonViewTest (11), FramePageViewTest (7), TransportSettingsViewsTest (9), and 117 selected
shell host/runtime/input/parity tests. Log: /private/tmp/pull-send-focused.log. This run preceded the
mechanical final Send registrations/deletion; the complete package gate and routed live smoke remain
required after that cutover.
