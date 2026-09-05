# Drum octave and native note translation capability audit

Status: core cutover implemented; complete offline package validation passed and live validation
is pending. The baseline audit was taken at `a905be84`. This covers the standalone Drum Pad and
composite lower-half Drum controller, not the inherited Drum64,
Drum sequencer, Play, Piano, or Session octave behaviors.

## Existing complete behavior

Sources: [DrumPadView](../../pull-shell/src/main/java/de/mossgrabers/controller/ableton/push/view/DrumPadView.java),
[WorkspaceView](../../pull-shell/src/main/java/de/mossgrabers/controller/ableton/push/view/WorkspaceView.java),
[DrumPadControls](../../pull-shell/src/main/java/de/mossgrabers/controller/ableton/push/view/DrumPadControls.java),
[Scales](../../pull-shell/src/main/java/de/mossgrabers/framework/scale/Scales.java).

- Standalone and composite implementations have the same octave policy. Only button DOWN changes
  the position; LONG and UP do nothing. The Drum controller must currently be engaged.
- Normal movement uses the drum default offset, currently 16. Shift uses four semitones. The
  modifier is sampled at DOWN, so a subsequent modifier change does not affect the operation.
- Movement clamps the base to 4 through 100. Previously a press at a limit repeated the unchanged
  bank request and notified. The migrated core notifies the existing authoritative range directly.
- The notification is `Offset: N (NOTE)` where N is base minus 36; base 36 is `Offset: 0 (C1)`.
  Note names use flats: C, Db, D, Eb, E, F, Gb, G, Ab, A, Bb, B. Push2Display.notify sends this to
  the DAW through showNotification; it does not add a controller display overlay.
- Both octave lights use whether a full default-offset step fits. Shift does not change this
  predicate: for example, at base 12 the down light is off although Shift+Down can reach base 8.
- The public reset callback sets the base to the drum note start, currently 36, clears framework
  pressed keys, updates translation, and scrolls the bank with `adjustPage=true`. Ordinary octave
  changes use `false`. Reset produces no range notification. No current Push caller was found for
  reset on these two views; framework automatic reset invokes the separate AbstractDrum64View.
- Engagement scrolls the active drum bank to the current base and enables its indication.
  Disengagement disables the old device indication. Changing the model device while engaged moves
  indication to the new device without resetting the base.
- Entering the engaged state sets identity velocity translation. Leaving computes the current
  accent configuration: identity when inactive, otherwise fixed velocity clamped to 0–127, with
  velocity zero preserved as zero. A device change within engagement does not reapply velocity.
- The native key map has exactly 128 entries. Physical notes 36–39, 44–47, 52–55, and 60–63 map
  in order to base through base+15. Every other physical note is disabled with -1.
- The right half of the lower grid and the upper grid remain disabled in native NoteInput; their
  controller actions have separate ownership. An EXCLUSIVE controller route does not disable
  Bitwig's permanent NoteInput by itself.

The setters for a custom drum matrix, note start/end, and default offset have no production callers
in this repository. Their current values are framework defaults, not exposed Push preferences.
Preserve the actual configured accent preference; do not invent a reset gesture or new setting.

## Selection, layout, and native input are distinct

[ResolvedNoteViewer](../../pull-core/src/main/java/de/mossgrabers/pull/core/runtime/view/ResolvedNoteViewer.java)
already resolves selected-target-fenced native routing. Composite Drum requires an existing
note-capable selected track aligned with the private selection-following NoteView snapshot and a
compatible Drum target. Standalone resolves the preferred note viewer; another note layout is not
implicitly eligible for Drum octave just because it can hold notes.

The current engaged flag is reconciled by stable DrumPadControls, while the drum snapshot provides
device identity, selected target identity, model alignment, and observed bank base. A core migration
must compare these independently. During selected-track/model disagreement, wrong device identity,
or an unacknowledged bank move, it must not project the new map onto an old visible target.

The lower-half composite must preserve the upper Session routes and lifecycle. Master retains
the active composition and must retain the same octave and native-note owner. Whole-surface note
layout selection is owned by NoteViewControllerView and must not be duplicated by an octave view.

## Implemented bounded capability

The parent-loaded addition is a complete replayable 128-entry key and velocity translation
value attached to the existing selected-note lifecycle. The core supplies all mapping policy.
The stable owner validates values, target identity and composition, applies native tables, rejects
delayed legacy table writes while owned, and releases ownership through the existing input-idle
and neutralization boundary. Unchanged replay must not churn NoteInput attachment or table writes.
Faulted core output must fail closed; it must not resurrect the deleted Drum table policy.

Changing the map while a native pad is held requires explicit lifecycle treatment. Clearing
framework pressed-key bookkeeping is not evidence that Bitwig received a note-off. Preserve the
old map until musical input is idle, or use an explicitly verified native-note termination contract.
Do not assume controller-command exclusivity controls native note transmission.

A generic absolute drum-bank position effect carries observed context generation, selected
channel identity, requested base, and the API's page-adjustment flag. Parent execution must recheck
the live target/device identity before mutating the existing bank proxy. Shift, clamping, note-name
formatting, and whether to notify belong in core. A separate bounded notification effect carries
already formatted text. A command submission cannot establish bank read-back or new light
state; those must follow a later sampled snapshot.

Core constants may own the existing 4–100 bounds, 16/4 steps, 36 reset, and four-by-four matrix,
because these are controller semantics. If previously unobserved settings are actually exposed,
add typed configuration sampling instead of embedding getters or policy in the stable adapter.

## Ownership cutover conditions

- Migrate octave actions, both lights, native key map, and engaged velocity behavior together.
  The generic existing input/light capability is necessary but does not complete the slice.
- The complete physical OCTAVE button still serves inherited non-Drum note layouts and full
  Session. Do not make its permanent command globally inert until those branches are migrated,
  or a declared fixed profile supplies a semantically inert migrated binding without fallback.
- Remove standalone and composite stable octave branches when their matching core profiles own
  them. Do not retain stable missing-core behavior behind EXCLUSIVE routes.
- Coordinate the existing DrumControllerView pitch-bend facet and octave claims with the separate
  touch-strip migration. Neither view may silently duplicate the other's musical lifecycle output.

The cutover adds [DrumOctaveView](../../pull-core/src/main/java/de/mossgrabers/pull/core/runtime/view/DrumOctaveView.java)
to the retained standalone and VS Live Drum compositions, including Master. Stable DrumPadView
and WorkspaceView octave methods and light predicates are inert; inherited non-Drum octave
behavior remains unchanged. The lifecycle-owning core views contribute native translation.
DrumPlayPadView accepts pressure and renders pads only when requested base, observed bank, and
successfully applied native translation agree. Rate, fill, and mapped control-pad state address
their own established targets and do not derive meaning from the native pad note map.

At most one accumulated absolute movement waits while playable pads are held. Context changes
or departure cancel it; shared Master retains the same view instance. No unsent intent survives
physical release. Rapid later presses compose from shell-held requested Scales state, which survives
core reload. One disposable cosmetic notification waits for later matching requested and observed
bank read-back; rejected or unadvanced requests do not announce a new range.

DrumPadControls no longer writes velocity policy. Existing PlayView accent observers publish the
current legacy baseline at initialization and on setting changes; the generic arbiter caches those
writes while the core owns identity velocity and restores the current baseline on relinquishment.
Stable target engagement still aligns the existing drum bank to the requested base and manages its
indication. This preserved resource lifecycle is not an extension point for new navigation policy.

Owned native tables require the producing view's explicit `MUSICAL_INPUT` footprint, including
owned silent tables. The parent-loaded value rejects enabled physical keys outside Push notes
36–99; the compiler rejects enabled keys outside the producer's claim and on another view's
controller-owned pad. Musical ownership does not create controller callback routes or RGB claims.

## Required verification

The complete offline package build passed after the octave cutover, including 37 Drum octave/map
policy tests, 21 Session characterization tests, existing core integration tests, and shell note
translation, exact bank-effect, and lifecycle tests. Musical-claim negative tests are added
separately; their validation is included in the final migration build.

Core tests must cover both composition modes, DOWN versus LONG/UP, Shift sampled at BEGIN,
clamps and light thresholds, note naming, limits that still notify, all 128 map entries, absent and
misaligned targets, and observed base lag after a submitted movement. The fake host must record
commands separately from explicit host advancement and snapshot capture.

Stable tests must cover delayed legacy table writes being rejected, unchanged replay being idle,
map replacement while pads are held, release ordering, selection/device changes, fresh and failed
core replacement, extension exit, and current accent state upon leaving Drum. Test exact actuator
validation at both preparation and execution, not only the snapshot used by the core.

A first live API25 smoke test remains required after build/install: standalone and VS Live octave
navigation with and without Shift, held-pad transitions, Session upper-grid isolation, layout and
track changes, accent entry/exit, Master retention, and a core-only hot reload. Verify later host
drum-bank state, native received notes/velocities, and controller lights, not just submitted effects.
Use the singleton live lease and preserve the exact activated build through the whole smoke test.
