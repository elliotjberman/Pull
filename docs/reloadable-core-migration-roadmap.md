# Remaining core migration

[ARCH](../ARCH.md) is the current ownership/capacity inventory. Working Core API 54 renders all
ordinary Push display pages through shared core components and bounded raw observations. The
optional Clip piano roll remains unchanged. Core also owns Session, Setup/Ribbon settings and shared
interaction cancellation. Display, page entry/return and input lifecycle ownership do not migrate
remaining actions, providers or lights. Use the [capability audit](reloadable-core-migration-guide.md)
for each complete behavior slice.

## Checklist

- [x] Ordinary page displays, reusable components and one offline component/view catalog.
- [x] Color palette grid output; the inherited color-selection gesture remains stable.
- [ ] Device, chain and layer controls/providers/lights with verified target identities.
- [ ] Browser filtering/results/audition/operations; navigation lifecycle is already core-owned.
- [ ] Crossfade, Track/Layer Details and Color actions, including exact targets and return.
- [ ] Scales/Layout, Repeat, Fixed Length, User, remaining physical Ribbon behavior and remaining musical layouts.
- [ ] Clip/note/sequencer editing and scene/clip-length workflows, including Chords/Piano/Program
      Change, Raindrops and alternate drum layouts.
- [ ] Specialized piano-roll rendering, explicitly deferred in the [handoff](migrations/ui-and-editing-handoff.md).
- [ ] Add Track, Groove and Quantize actions/providers/lights.
- [ ] Global Tempo/Master/play-position knob variants and touch feedback. Direct parameter
      bindings do not migrate browser selection, loop start/length, zoom or notifications.
- [ ] Standalone New, Duplicate, Delete, Double, Quantize, Convert and footswitch commands.
      Session modifier handling does not complete those standalone gestures.
- [ ] Delete each family's stable policy, providers, facet claims and aliases/inbox callers as its
      complete action/feedback behavior moves. Keep generic resources and transport.

The [UI/editing handoff](migrations/ui-and-editing-handoff.md) scopes those separate tasks.
The migration ends when no frozen product-policy adapter remains.

The Session smoke record includes API 50: Setup/Info and plain/Shift track arrows passed the [scoped live check](migrations/session-core-live-smoke.md).
Ribbon, Session and reload were not rerun on API 50; earlier Session evidence and its
[bounded location/release limits](migrations/session-launcher-location-design.md) remain applicable only to the recorded builds.
API 54 requires a matching shell install and restart later. No install or live check has been
performed for this cutover. The UI checkpoint passed 1,038 offline package tests before integration.
Combined-source validation remains pending. The physical Color route handoff remains unresolved,
so this checkpoint is not ready for installation.

## Device, chain and layer

`SpecificDeviceImpl.getID()` returns blank in production, excluding selected-device remote targets.
API 25 supplies channel IDs and proxy equality observations, not a device UUID. Names, positions,
wrappers and Drum candidate paths cannot substitute. A retained cursor/equality recipe is unproved;
test pinning, ownership, duplicate names, replacement, deletion and page rebinding before adopting it.

Installed windows include eight siblings, displayed remote-page names, remotes, layers and sends
per layer/pad, sixteen drum pads, and a 100-device reorder bank. Nested correctness still needs
characterization. Display observations now describe these windows without granting actuator
authority. After identity is proven, expose actuator contexts, named layer parameter roles
and primitive navigation/Boolean/UI operations. Include Params/Chains, rows, preferences, touches,
pin/window controls, feedback and held return. Trace permanent parameter bindings as well as mode
callbacks. [Target identity and removal criteria](findings/parameter-target-proxy-coupling.md).

## Other families

Crossfade/MIDI-channel controls historically step per callback. Summed motion loses count and
ordering at clamps (`+1,-1` may differ from zero); resolve the input contract or explicitly change
behavior. Details need monitoring state separately from mode, absolute writes, pinning and MIDI
edit-channel read-back. Track Details display now waits when its observed bank/Master action target
is missing or differs from the cursor; frozen physical lights still read that cursor, and touches
remain intentionally inert. Color needs a target-bound grid, native-note suppression and exact return.

Browser display uses bounded filters/results/selection; its action migration still needs primitive
operations with an exact insertion or replacement destination. Reuse existing entry/return
ownership. Configuration storage may stay mechanical in shell. Note display now reads host values
separately from the legacy editor's optimistic working copy. Editing actions still need exact clip/note
identity, bounded windows and reusable edits. [Custom geometry](findings/custom-musical-surface-geometry.md) needs a complete
native-note/pressure/feedback capability expansion.

## Separate limits and acceptance

[General async reload draining](findings/core-reload-quiescence.md) is separate from this display cutover. Other
[active findings](findings/README.md) own unresolved parameter precision/addressability, MIDI-learn
lifecycle, facet/claim validation and live provenance. Update or delete them when
their removal criteria are met; do not duplicate their investigations in a feature diary.

Follow [TESTING](../TESTING.md): routed behavior, separately advanced host state and output; target
changes, cleanup, faults and replacement. New API/proxy/input/output capability requires a matched
shell build and restart. Core-only behavior uses hot reload. Report exact candidate coverage and
pending checks in the [smoke record](migrations/session-core-live-smoke.md).
