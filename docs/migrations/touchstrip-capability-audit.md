# Shared raw pitch-bend migration capability audit

Status: capability, production cutover, and deterministic integration tests implemented. Exact-build
live verification remains required. This document is not evidence that the installed shell supports
the new output.

Feature: move the existing raw Session and Drum touch-strip action and indicator into core.

Physical inputs: `TOUCHSTRIP/TOUCH` BEGIN, LONG, END and `TOUCHSTRIP/ABSOLUTE` UPDATE, carrying the
complete 14-bit value. The permanent physical callbacks remain installed once.

Legacy variants: Session is raw regardless of selected-target applicability. The declared composite
Drum composition is also raw independently of applicability. Standalone Drum is raw only while
its layout is active and its authoritative controller engagement is true. Raw input ignores Shift,
Delete, Repeat, and configured ribbon modes, forwards pitch bend, echoes the physical position,
and centers on release. Other views retain unchanged CC, split CC/pitch, track-fader, focused
parameter, Repeat, Shift-configuration, and Delete-reset behavior.

Authoritative state: existing layout and drum engagement snapshots determine the selected fixed
profile. The strip indicator displays observed physical position, not host-applied pitch state.
The API supplies no host pitch-bend acknowledgement; musical playback must be tested separately.

Effects: existing `SendNoteInputMidiEffect` sends exact 14-bit pitch bend and release neutralization
through the permanent NoteInput. Ordinary Bitwig musical routing remains independent of physical
controller-command arbitration. The existing parent-owned MIDI state tracker handles selection,
generation, fault, and shutdown neutralization.

Output: one reusable `DesiredTouchStrip` in the complete hardware result carries explicit ownership,
hardware mode, and 14-bit indicator position. Unowned differs from owned-off. Every legacy writer
must pass through generic output arbitration; mode encoding and MIDI transmission remain stable.

Reloadable state: one shared, bounded raw gesture per core generation, including begin-time raw
eligibility and the latest observed position. The raw and continuation profiles share it across
workspace and page changes. A physical touch and its queued motion block core replacement; no
partial gesture is checkpointed into a new generation.

Existing coverage: normalized TOUCH/ABSOLUTE inputs, the fixed TOUCH_STRIP surface, raw MIDI effects,
layout and engagement state, generation fencing, motion coalescing, and parent MIDI cleanup.

Added coverage: generic touch-strip output and arbitration; exact exclusive input admission;
related-motion ownership frozen together with TOUCH; compiler output validation and composition;
production raw/continuation profile selection; debugger absolute/touch ingress and outbound-strip
observation for the closed live loop. Raw Session and Drum shell branches and the pitch-bend adapter
facet are deleted. The legacy command is inert whenever the committed strip output is owned; late
legacy mode/value writers cannot overwrite core output. Missing, invalidated, or faulted cores leave
owned-off output rather than reactivating a legacy implementation.

Ownership contract: freeze both TOUCH and its associated ABSOLUTE route/generation at BEGIN through
END, flushing motion first. A legacy-started touch cannot be promoted into raw after a layout change.
The old raw command permitted that promotion; this migration explicitly follows AGENTS.md's stronger
BEGIN-frozen ownership rule. A raw-started touch remains raw until release even after its originating
view disappears. Orphan raw UPDATE events do not acquire a gesture without BEGIN.

First-run restart: required for the shared output contract, output transport arbitration, and exact
input admission. Later policy changes within this installed capability reload core-only.

Explicit exclusions: the unrelated ribbon modes remain frozen legacy behavior; this slice does not
migrate their configuration pages, change selected-track musical routing, add host pitch read-back,
or implement the separate general quiescence finding.

Acceptance: deterministic tests distinguish physical samples, MIDI requests, indicator output,
later selection changes, and cleanup; cover raw/legacy transitions in both directions, modifiers,
coalescing, END order, core generation fencing, output ownership, and exact 14-bit values. The final
live smoke holds the singleton lease through exact shell/core activation, routed input, outbound
strip observation, audible pitch/center confirmation, and a core reload without another restart.

Offline evidence: `RawPitchBendViewTest`, `RawPitchBendCoreIntegrationTest`,
`PhysicalInputRouterTest`, `TouchStripOutputHostTest`, `ControllerRuntimeEnvironmentTest`,
`PushDebugInputHostTest`, `PushDebugSurfaceHostTest`, and the debugger HTTP trust-boundary tests.
The browser sends coalesced 14-bit strip samples inside the same bounded TOUCH lease, flushes the
last sample before END, and renders the returned hardware output instead of its local pointer.
