# Session migration boundary

Session grid/scene/page handlers and their feedback still live in the frozen adapter. Core can
observe bounded 8×8 or 8×4 slot/scene state but cannot yet perform the complete Session feature.
The shared [interaction lifecycle](../interaction-lifecycle.md) supplies capture, cancellation and
suppressed tails once a core view declares its actual location target and required cleanup.

## Missing installed capability

- Location-fenced slot/scene main and alternate launch/release; select, delete, copy, browse,
  create and record; absolute bank positioning for birds-eye navigation. Current Session effects
  cover track selection and Stop. Drum fill effects serve fixed owners and cannot replace these.
- Observed Session select-on-launch, armed-empty-pad action, clip length and record-stripe settings.
- Cleanup execution before controller-driven bank/window rebinding. `ControllerRuntimeEnvironment`
  currently applies controller state before ordinary effects; returning a release from `cancel()`
  alone therefore cannot guarantee correct order. Parameter touches already release before state.
- Exclusive admission for migrated pads/scene/page controls and complete native-note silence.
- Reusable blink color/rate output: core pad RGB output currently clears the stable Session blink.

Add these bounded mechanisms, put every Session recipe and its feedback in core, then delete the
stable handlers/facet claims. This needs a shell/API build and restart, not arbitrary offscreen
retention, the old 66-cursor proposal, or a general asynchronous reload drain.

## Release contract

Current stable Session submits `launchRelease()` / `launchReleaseAlt()` on release; it does not
observe their completion. API 25 returns void and configured release can leave playback unchanged.
A migration can preserve correctly targeted **release submission** without first proving a stronger
completion/reuse guarantee. Neither a timer nor `flush()` supplies that stronger acknowledgement.

Capture a launcher location: project/bank context, track channel identity and absolute scene index
in a ready/aligned window. It is not a durable clip-content ID. Controller navigation must submit
required cleanup through the old valid location before rebinding. External scene edits or proxy
rebinding may remove that addressability first; fail closed and report unavailable cleanup rather
than mutate a replacement. Guaranteed restoration after arbitrary external edits is separate work.

Preserve all modifiers/settings, clipboard source lifetime, create/record sequencing, main/alternate
release and feedback together. Characterize Shift changes between press/release and scene
Select/Delete/Duplicate release behavior. Verify API 25 overloads and nondeprecated copy operations;
prove routed target changes, cleanup order, later read-back and matched-build live behavior.

## Current release regression

At `cc1c6264`, selected-track generation changes call `BoundedControllerBridge.resetNoteInputMidiState`,
which clears every stable grid receiver. Session DOWN → unrelated track selection → UP then loses
its `launch(false)` submission even when the slot remains visible. Review reproduced this with the
real routed Session handler; the earlier live smoke omitted it. This P1 remains open. Separate
selected-note neutralization from actual Session binding loss; do not synthesize END on a new view.
