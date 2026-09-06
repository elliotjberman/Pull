# Core migration live smoke record

Status: live execution began on 2026-09-06. The exact checkpointed API-45 shell was installed,
Bitwig started, and a uniquely published core was acknowledged. Representative controller/host
checks pass, but this is not a complete smoke sign-off. A generic debugger chord-release defect
was reproduced and is being corrected; remaining gesture variants and physical/audio checks remain open.

This supplements `TESTING.md`. A passing offline build, submitted debug request, or `APPLIED`
ingress result is not a passing host-state or output check. Record each layer separately.

## Build and environment

- Working branch: `codex/complete-core-migration`.
- Source regression baseline: `master` at `5537271f575852634a7a94e473eb57a404fd77a8`.
- Intended contract: Core API 45; Bitwig controller API 25; checkpoint schema 5.
- Initial live source checkpoint: `a6c7ccd03c6c01687a842bf1b2e74268966983af`.
- Candidate extension SHA-256: `b2101cbc7156cdc97ba9c769bf430f54da23cd518d9f05614f48c06c37cbe313`.
- Candidate API compatibility fingerprint: `b3f11d5fdbc8dfcfaa9b53cba761e293f45a8732`.
  This is not proof of the running shell's identity; the provenance finding remains active.
- Offline package passed 838 tests at 20:06:20 EDT on 2026-09-05; bounded finishing reviews cleared
  A1–A4 and LC1–LC2. Full log: `target/migration-evidence/post-review-package.log`.
- Initial live activation: core `20260906T162533Z-080374d0eb4b16da80b0c38085a23dee`, SHA-256
  `7d63965385abe4793bbe56e9d613d897407c6b1b2c1af826faaec0cb4e338690`; exact `Activated` receipt
  in `target/migration-evidence/first-live-reload.log`. Installed extension bytes matched the SHA above.
  The previously published API-44 core was correctly rejected before the API-45 publication.
- Live lease: acquire `tools/with-pull-live --owner complete-core-migration` and keep its shell
  open through installation, restart, exact activation, all input, and final observation.
- Preserve the user's open project. Use a saved scratch project for parameter, automation, and
  arrangement changes. The user authorized creating project macros for this smoke test.

## Scratch project and evidence

Saved independent `target/migration-evidence/Core Migration Smoke/Core Migration Smoke.bwproject`
from the recovered PR37 probe before parameter changes. The original project was not overwritten.
The scratch contains two Drum Machine tracks, Audio 3 and FX 1. Keyboard Save As worked; CUA
coordinate clicks fail with `windowNotFoundAtPosition` at negative desktop coordinates. The user
added three mapped project remotes (Volume, Volume, Pan), which were then exercised through the
controller debugger. The UI limitation did not require a production workaround.

The desired fixture has two distinguishable tracks, at least one note-capable
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

## First live observations (2026-09-06)

Artifacts below are in `target/migration-evidence/`, from this worktree under the uninterrupted
`complete-core-migration` live lease. A prior-run Bitwig library-indexer Java heap OOM occurred
before candidate installation; the recovered project also reports missing Track Controls devices.
Both current and previous Bitwig logs were inspected. These are not attributed to this candidate.

- `track-volume.tsv`: selected owner `5e10dc9a-b8c9-4771-bd30-c0a536b6ba2b`, effect entry 7,
  later host entry 12/revision 20: volume 553→642, −10.0→−6.1 dB. `track-before.png` and
  `track-after.png` show the same change. END entry 15 clears requested and observed touch state.
- `selected.tsv`: same owner, pan 512→542 (5.87%) and send 0→80 (−66.4 dB) on later samples.
  Both observed touch releases are retained. Later track-switch steps exceed the trace cap.
- `globals.tsv`: Track→Volume acknowledged; encoder 2 changes bank track owner
  `8153103e-aa55-41aa-a7b9-7694b6429635` from 598→642 on later host read-back.
- `master-frame.tsv`: Master retains DRUM_PAD; Master pan 512→542 on later read-back, then touch
  clears. Frame I/O changed its visible display (`frame-held.png`/`frame-io-toggled.png`), but
  the repeated row press failed because released debug edges remained owned while Master was held.
- `metro.tsv`: metronome and Shift tick-playback toggles both reach later true host observations.
- `automation.tsv`: write on/off both reach later host observations; temporary Automation entry
  is acknowledged. Later return/reset steps require short recaptures.
- `accent.tsv`: Accent enable is observed, then native nonzero velocities become 127. The fixed
  setting later becomes 124, but that long trace does not retain native 124 acknowledgement.
- `drum-before.tsv`, `drum-up.tsv`, `drum-restored.tsv`: authoritative base 36→52→36 and the
  corresponding applied 128-entry native key maps agree.
- `strip-12000-surface.json`, `strip-master-held-surface.json`, `strip-3000-surface.json`,
  `strip-released-surface.json`: successfully transmitted PITCH_BEND output 12000→12000 across
  Master page replacement→3000→8192 on release. Separate short traces preserve the retained
  Drum mapping. This does not prove audible pitch or native learned mapping actions.

The full-snapshot recorder reaches its 2 MiB serialization cap in some multi-step traces even
when the outer status says STOPPED. `SERIALIZATION_TRUNCATED` is a coverage limit; it is never
treated as a pass for later steps. Short per-step before/after host samples replace those gaps.
The artifact helpers `live_driver.py`, `summarize_trace.py` and scenario scripts are smoke
scaffolding, not new production interfaces. They use existing bounded, opt-in debug transports.

- User-assisted macro setup closed the fixture gap. `macros-before.tsv`/`macros-after.tsv` and
  matching PNGs show project remote 0 (LIVE32, project-remote owner matching this scratch project)
  468→518 / −14.4→−11.7 dB. `macro-touch-pan-*-observed.json` verifies exact target acquisition,
  later movement and release for remote 2; `macro-touch-pan-reset-applied-observed.json` verifies
  Delete+touch reset to 512/center, and the released snapshot has no remaining touch ownership.
