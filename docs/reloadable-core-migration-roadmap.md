# Remaining core migration

[ARCH](../ARCH.md) is the current ownership/capacity inventory. Core API 48 includes Session and shared
interaction cancellation; page entry/return and input lifecycle ownership do not migrate remaining
handler bodies. Use the [capability audit](reloadable-core-migration-guide.md) for each complete slice.

## Checklist

- [ ] Device, chain and layer controls with verified target identities.
- [ ] Browser filtering/results/audition/operations; navigation lifecycle is already core-owned.
- [ ] Crossfade, Track/Layer Details and Color.
- [ ] Scales/Layout, Repeat/Ribbon, Fixed Length, Setup/Info, User and remaining musical layouts.
- [ ] Clip/note/sequencer editing and scene/clip-length workflows.
- [ ] Delete each family's stable policy, providers, facet claims and aliases/inbox callers as its
      complete action/feedback behavior moves. Keep generic resources and transport.

The [UI/editing handoff](migrations/ui-and-editing-handoff.md) scopes those separate tasks.
The migration ends when no frozen product-policy adapter remains.

Session is implemented in core; its bounded location/release limits are recorded in the
[Session contract](migrations/session-launcher-location-design.md).

## Device, chain and layer

`SpecificDeviceImpl.getID()` returns blank in production, excluding selected-device remote targets.
API 25 supplies channel IDs and proxy equality observations, not a device UUID. Names, positions,
wrappers and Drum candidate paths cannot substitute. A retained cursor/equality recipe is unproved;
test pinning, ownership, duplicate names, replacement, deletion and page rebinding before adopting it.

Installed windows include eight siblings, displayed remote-page names, remotes, layers and sends
per layer/pad, sixteen drum pads, and a 100-device reorder bank. Nested correctness still needs
characterization. After identity is proven, expose subscribed contexts, named layer parameter roles
and primitive navigation/Boolean/UI operations. Include Params/Chains, rows, preferences, touches,
pin/window controls, feedback and held return. Trace permanent parameter bindings as well as mode
callbacks. [Target identity and removal criteria](findings/parameter-target-proxy-coupling.md).

## Other families

Crossfade/MIDI-channel controls historically step per callback. Summed motion loses count and
ordering at clamps (`+1,-1` may differ from zero); resolve the input contract or explicitly change
behavior. Details need monitoring state separately from mode, absolute writes, pinning and MIDI
edit-channel read-back. Compare action and rendering targets under pinning; Track Details touches
are intentionally inert. Color needs a target-bound grid, native-note suppression and exact return.

Browser needs bounded filters/results/selection and primitive operations with an exact insertion
or replacement destination. Reuse existing entry/return ownership. Configuration storage may stay
mechanical in shell. Musical editing needs bounded note/step/clip windows, target identity, read-back
and reusable edits. [Custom geometry](findings/custom-musical-surface-geometry.md) needs a complete
native-note/pressure/feedback capability expansion.

## Separate limits and acceptance

[General async reload draining](findings/core-reload-quiescence.md) is explicitly parked. Other
[active findings](findings/README.md) own unresolved parameter precision/addressability, MIDI-learn
lifecycle, facet/claim validation, logical timers and live provenance. Update or delete them when
their removal criteria are met; do not duplicate their investigations in a feature diary.

Follow [TESTING](../TESTING.md): routed behavior, separately advanced host state and output; target
changes, cleanup, faults and replacement. New API/proxy/input/output capability requires a matched
shell build and restart. Core-only behavior uses hot reload. Report exact candidate coverage and
pending checks in the [smoke record](migrations/interaction-lifecycle-live-smoke.md).
