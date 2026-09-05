# Tap Tempo capability audit

Scope: complete `TAP_TEMPO/BUTTON` action and monochrome feedback in every workspace and page.
This is a bounded canopy expansion followed by core cutover; live verification remains required.

The existing `TapTempoCommand` sends native transport tap on unshifted DOWN and displays the
observed tempo. Shift UP toggles the metronome. LONG and unshifted UP are inert. Shift is read on
each edge, so pressing normally then adding Shift before release both taps and toggles; starting
shifted then releasing Shift before the button does neither. Select/Delete and active views add no
other branches. The permanent binding's normal light is monochrome intensity 30 idle and 127 held.

Installed inputs/state: the permanent Tap button, observed Shift and physical pressed state,
authoritative TRANSPORT tempo/metronome and PROJECT identity/command state. Existing project-fenced
absolute metronome effects and native host notification transport are sufficient for those parts.

Missing capabilities: the native tap primitive and correct RGB-brightness translation for a
monochrome output. Add project-fenced `TapTempoEffect` using the already initialized `ITransport`;
the exact native `Transport.tapTempo()` method is present and non-deprecated in API 25. Add generic
button-aware monochrome hardware translation beneath core output. Neither adapter interprets Tap
gestures, picks brightness, calculates tempo, or formats notification text.

Core policy: one retained fixed-footprint view, exclusive Tap BUTTON plus output claim, observed
Shift, native tap on BEGIN, authoritative serialized metronome toggle on shifted END, dim/bright
light from observed physical held state, and tempo notification from a later controller-tick host
sample. Tap notification is read-back of tempo, not a receipt proving which tap set that tempo.
Project disagreement clears pending notices/toggles. Complete tick demand is replayable and merged
from views so the delayed notice does not depend on an unrelated parameter view staying active.

The Push Tap binding becomes the existing inert core-owned command and uses only core light output.
Missing/faulted core leaves it inert/off. Other controllers may keep the untouched framework
`TapTempoCommand`; that shared class is not the Push binding or a fallback.

Tests: plain/shifted/dynamic-Shift edges, LONG/orphan handling, read-back-only notice, queued
metronome toggles across later observation, project changes, light/tick ownership, project fencing
at prepare/apply, exact exclusive admission and hardware monochrome intensity. Live smoke drives
the existing generic debugger button path, observes later transport tempo/metronome and successful
light output, and verifies the native notice under the singleton live lease.
