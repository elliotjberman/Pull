# Core migration live smoke record

Status: planned; no live execution or installation from this migration worktree yet. Initial UI
inspection on 2026-09-05 was blocked because the Mac was locked; the user has been asked to unlock
it. Code fixes continue independently. No attempt was made to bypass the lock.

This supplements `TESTING.md`. A passing offline build, submitted debug request, or `APPLIED`
ingress result is not a passing host-state or output check. Record each layer separately.

## Build and environment

- Working branch: `codex/complete-core-migration`.
- Source regression baseline: `master` at `5537271f575852634a7a94e473eb57a404fd77a8`.
- Intended contract: Core API 45; Bitwig controller API 25; checkpoint schema 5.
- Exact checkpoint, shell fingerprint, extension hash, active core build ID: pending.
- Offline package and independent finishing review for the exact installed build: pending.
- Live lease: acquire `tools/with-pull-live --owner complete-core-migration` and keep its shell
  open through installation, restart, exact activation, all input, and final observation.
- Preserve the user's open project. Use a saved scratch project for parameter, automation, and
  arrangement changes. The user authorized creating project macros for this smoke test.

## Scratch project and evidence

Create or reuse a scratch project with two distinguishable tracks, at least one note-capable
instrument/Drum Machine track, and effects/sends needed to exercise the migrated send columns.
Create named project macros mapped to observable targets. Do not interpret a blank macro slot as
a working parameter path. Keep the exact selected track identity alongside its name/position.

Use permanent routed Push controls through the debugger, with bounded traces started before each
interaction and stopped after later host read-back. Capture the outbound display for visual checks;
compare against master if a layout or inherited gesture is in doubt. Record logs and artifact paths
in the table. Actual note sound and native learned mappings require their separate physical/audio
proof described in `TESTING.md`; controller ingress alone cannot supply it.

| Slice | Routed interaction | Authoritative completion / output evidence | Result |
| --- | --- | --- | --- |
| Startup and exact build | Install checkpointed extension, restart Bitwig, activate exact core | Correct API/fingerprint/build acknowledgement; no startup stack trace; populated subscribed domains | Pending |
| Project macros | Select Project Macro page, touch/turn/release a mapped encoder | Later target value and display agree; touch lease releases; empty slots remain inert | Pending |
| Selected Track | Select each scratch track, edit volume/pan/send, use menu and arrows | Matching owner/name/value, later track/bank change, expected light/display; no old-owner write | Pending |
| Global Volume/Pan | Mix button to global page, upper menu selection, encoder gesture | Exact current-bank track changes; observed parameter and row metadata agree | Pending |
| Global Sends | Visit send pages, adjust and toggle a send; include Delete+Shift+Select touch | Reset/touch/toggle order, later enabled/value state, matching display; absent sends remain inert | Pending |
| Master | Short page replacement and return; touch/turn/release; LONG Frame and release | Master/Cue parameter read-back, retained grid/note composition, exact return without DAW track selection | Pending |
| Frame | Toggle representative Arranger/Mixer properties and switch UI layout | Later application snapshot and visible Bitwig UI agree with corresponding controller feedback | Pending |
| Metronome/Automation | Plain/Shift/Delete variants; LONG pages; early release while entry pending | Later setting/write-mode read-back; documented latch/return; modifier release consumed correctly | Pending |
| Accent | Toggle Accent and edit fixed velocity; switch between Drum and legacy Note layouts | Observed configuration, desired and applied native velocity tables, zero stays zero; physical/audio note check separately | Pending |
| Drum octave and strip | Octave changes; strip drag, held page change, release | Later base/applied translation; exact strip position then center; independent audible check if available | Pending |
| Retained composition | Move through Master/temporary/mixer pages over VS Live and ordinary Note | Same grid/note routing and held gesture lifecycle persist through page replacement | Pending |
| Core hot reload | Reload exact candidate from this shell, including a held supported gesture | Replacement waits for old gesture; no completion reaches replacement; settings, mapped parameter output, and composition recover | Pending |
| Final regression pass | Repeat representative macro, Track, Master, Send interactions after reload | Later host values and outbound frame, no runtime faults or leaked touch state | Pending |

## Limits and remaining work

This first live pass covers the completed migration slices, not all inherited Push behavior. Session
grid actuation still awaits its release contract decision, and device/browser, Crossfade, remaining
configuration/note/clip/sequencer families remain in the migration inventory. Any failure must be
recorded with the exact build and turned into an offline regression when its boundary can be modeled.
Do not mark this document passed by copying results from another task's live environment.
