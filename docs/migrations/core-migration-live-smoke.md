# Core migration live smoke record

Status: **not ready**. Source checkpoint `6272f48d` passed the representative checks recorded
below, but its final live regression failed during ROW1_1 track selection with a stack overflow
in selected-note-route cleanup. The observed cycle is `ControllerStateHost.detach` → neutralize
→ debug cancellation → `releaseAll` → END → refresh → detach. The bounded reentrancy correction passed the deprecation-enabled package gate: **851 tests,
zero failures/errors** (398 core, 11 publication, 442 shell), completed 2026-09-06
13:01:50 EDT; log `target/migration-evidence/reentrant-cleanup-package.log`. A new
checkpoint `bf367a06` is installed, but Bitwig has not launched with it: the Mac is locked.
Exact activation, focused selection regression, and held-reload rerun are still required.
Earlier passed cases remain evidence for `6272f48d`, not a sign-off for a later replacement.

This supplements `TESTING.md`. A passing offline build, submitted debug request, or `APPLIED`
ingress result is not a passing host-state or output check. Record each layer separately.

## Build and environment

- Reentrancy correction checkpoint: `bf367a06`; built/installed shell SHA-256
  `cac7132246385ce5f250a812807415c81b29f12bd530080dd187f7604a61eb10`.
  Both file hashes match in `reentrant-cleanup-installed-sha256.txt`. Bitwig was cleanly closed
  before installation, and the scratch project was saved. No active-core acknowledgement exists
  for this replacement yet. Automatic review initially could not see the lease; an explicit
  `require-pull-live` check proved ownership and allowed the retry, which then reported a locked Mac.
- Working branch: `codex/complete-core-migration`.
- Source regression baseline: `master` at `5537271f575852634a7a94e473eb57a404fd77a8`.
- Intended contract: Core API 45; Bitwig controller API 25; checkpoint schema 5.
- Initial live source checkpoint: `a6c7ccd03c6c01687a842bf1b2e74268966983af`.
- Initial extension SHA-256: `b2101cbc7156cdc97ba9c769bf430f54da23cd518d9f05614f48c06c37cbe313`.
- Last live-tested source checkpoint: `6272f48d4c137a9db46ccb4b37c10ff4d88d8471`.
- Last live-tested extension SHA-256:
  `8326a296840e928bad7e2d0996ec5de0bede4117c9a8590476360c70c4917565`.
  Both paths match in `target/migration-evidence/debug-fix-installed-sha256.txt`.
- Earlier deprecation-enabled package gate: **845 tests, zero failures/errors** (398 core,
  11 publication, 436 shell), completed 2026-09-06 12:43:10 EDT. Log:
  `target/migration-evidence/live-debug-fix-package.log`.
- Last active core before the crash: `20260906T164738Z-36252a87aa8a6b8dcf191cc8563e2619`, generation 4,
  SHA-256 `fa03e3e42804fcd575732937aa10acd35100237327f58945c49abb1873206f17`.
  `held-reload-activated.json` and `held-reload-release.tsv` identify the same activation.
- Candidate API compatibility fingerprint: `b3f11d5fdbc8dfcfaa9b53cba761e293f45a8732`.
  This is not proof of the running shell's identity; the provenance finding remains active.
- Offline package passed 838 tests at 20:06:20 EDT on 2026-09-05; bounded finishing reviews cleared
  A1–A4 and LC1–LC2. Full log: `target/migration-evidence/post-review-package.log`.
- Initial live activation: core `20260906T162533Z-080374d0eb4b16da80b0c38085a23dee`, SHA-256
  `7d63965385abe4793bbe56e9d613d897407c6b1b2c1af826faaec0cb4e338690`; exact `Activated` receipt
  in `target/migration-evidence/first-live-reload.log`. Installed extension bytes matched the initial extension SHA above.
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

All artifact names below are relative to `target/migration-evidence/`. A **verified** row is
limited to the listed interactions. Empty columns, every modifier combination, every send page,
audio and native learned mappings are not implied by a representative pass.

| Slice | Verified interaction and later observation | Evidence / limits |
| --- | --- | --- |
| Startup and exact build | Final installed extension hash matches; API 45 core activates after restart and again after held reload | Final hashes/gate above; `held-reload-activated.json`; startup stack traces inspected by operator |
| Project macros | Remote Volume 468→518; remote Pan 414→454→512 by movement and Delete+touch reset; exact project owner and released touch ownership; saved values persist after reopen/reload | `macros-*`, `macro-touch-*`, `held-reload-before/after.tsv`; initial movement/reset preceded final debugger-only shell fix; generation-4 final regression also verifies movement 518→558 and release before the later selection crash |
| Selected Track / global Volume | Exact selected-track volume 553→642, pan 512→542, send 0→80; global Volume edits another exact bank owner 598→642 | Initial live `track-volume.tsv`, `selected.tsv`, `globals.tsv`; final Track regression pending |
| Global Pan | Exact `TRACK_PAN:0` owner, motion 542→562, Delete reset→512; BEGIN ownership and END cleanup | Complete generation-4 `final-pan-*` short snapshots, action traces and captures |
| Global Send | `TRACK_SEND1:0` 80→120, Delete reset→0, Shift+Select touch enabled true→false; exact target remains stable and all releases clear ownership | Complete generation-4 `final-send-*`; separate reset and toggle gestures, not the untested combined Delete+Shift+Select variant; SEND2–8 not exercised |
| Master | Page entry/return retains WORKSPACE grid; Master pan 621→661 with exact project owner and touch cleanup | Generation-4 `final-frame-master-*` and touch action traces; Cue controls not exercised |
| Frame | Held temporary Frame: repeated I/O false→true→false, MIX→EDIT→MIX, still held >7s, release→TRACK with no pressed controls | `final-frame-*`; ARRANGE is a native no-op in the observed Dual Display (Studio) profile; available MIX/EDIT controls verified; not a migrated-input failure |
| Metronome | Plain toggle false→true; Shift tick playback false→true; long Transport page and release latch; independent volume 767→807; next press restores TRACK | Generation-4 `final-metro-*`; later project/parameter observations, capture and touch END result; pre-roll variants not rerun |
| Automation | Writing false→true; long temporary page; select TOUCH with writing enabled; release restores TRACK | Generation-4 `final-auto-*`; Delete/reset-overrides and release-before-entry variants not exercised in final live pass |
| Accent | Enable at velocity124, edit→122; applied native table exactly `[0] + [122]×127`; acknowledged page survives >7s physical hold, release→TRACK | Generation-4 `final-accent-*`; retained WORKSPACE grid and no pressed controls after release; audible/native learned mapping proof separate |
| Drum octave and strip | Base36→52→36 and applied native key maps; transmitted pitchbend12000 retained across Master→3000→center8192 on release | Initial live `drum-*` and `strip-*-surface.json`; not repeated after debugger-only shell fix; no audible pitch claim |
| Retained composition | Master, Frame, mixer, Metronome, Automation and Accent pages retain WORKSPACE grid; long holds and returns use later layout observations | Generation-4 final-case snapshots; selected NoteInput attachment/audio are separate proofs |
| Core hot reload | Published replacement waits while macro knob1 remains touched; old generation receives END and applies empty touch ownership; new generation activates ~81ms later without restarting Bitwig | Complete `held-reload-*`, exact IDs and unchanged PIDs 58202/58203; release trace details below |
| Final regression | Macro movement/release and preference restoration completed before ROW1_1 selection; Track selection and final cleanup failed | **Failed on 6272f48d:** recursive selected-note cleanup stack overflow; see completed `final-regression-*` artifacts and backed-up Bitwig log |

## Limits and remaining work

This live pass samples the migrated slices; it does not establish every semantic variant or all
inherited Push behavior. Session
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

## Final short-sample results (generation 4)

On the subsequently failed `6272f48d` build, the `final-accent`, `final-metro`, `final-auto`, `final-send`, `final-pan` and `final-frame`
driver logs end `VERIFIED`. Their short samples/action traces are complete, contain no runtime
failure stage, and avoid the earlier serialization truncation. Host samples may contain no fresh
core result when the core is idle. Parameter-touch assertions therefore use their separately
recorded BEGIN/END action result and same-generation APPLIED receipt; they do not skip ownership
checks or treat a missing idle result as empty output.

- **Accent:** revisions 93→97 observe enablement and applied124 mapping; revision 104 observes
  edited122 and an exact128-entry velocity table with zero→zero and all nonzero inputs→122.
  The already-acknowledged ACCENT page remains physically held for another 6.395s between the
  entry and long-hold samples; the full gesture exceeds 7s. Revision 107 restores TRACK and clears
  pressed controls. Grid remains WORKSPACE throughout.
- **Metronome:** later revisions 110 and 115 acknowledge metronome and tick playback. TRANSPORT
  remains latched with no pressed controls at 119. `GLOBAL:2`, exact project-global LIVE5, changes
  767→807 (-12.0→-10.1dB) at 121; END clears host touch at 122 and its explicit core result has no
  parameter-touch ownership. This knob intentionally has no DAW parameter-touch acquisition.
  Revision 127 restores TRACK.
- **Automation:** revision 132 acknowledges writing enabled; 136 acknowledges temporary
  AUTOMATION; 139 acknowledges TOUCH mode with writing enabled; 143 restores TRACK and clears
  pressed controls.
- **Send/Pan:** exact channel owner is `5e10dc9a-b8c9-4771-bd30-c0a536b6ba2b`.
  Send LIVE19 (channel-send, index0) changes 80→120 at 153, resets 0 at 157, then toggles enabled
  false at 164. Three separate END results are empty and final 168 has no touch/modifier held.
  Pan LIVE22 (channel-pan) changes 542→562 at 174, resets 512 at 178, and releases at 180.
- **Master/Frame:** exact project-master Pan LIVE13 changes 621→661 at 191 and releases at 192.
  FRAME retains underlying TRACK and WORKSPACE, I/O changes false→true at 204→false at 207,
  EDIT is observed at 212 and MIX restored at 215. The acknowledged held FRAME page persists
  another 7.672s between entry and final hold sample; 219 restores TRACK with no held controls.
  This closes the repeated-soft-key defect reproduced before the debugger fix.

The earlier Frame v2 I/O assertion ran in EDIT, where upper I/O is intentionally inactive. V3
proved repeated I/O under MIX but found ARRANGE did not change the observed host layout. A
separate action trace confirmed the correct ARRANGE effect and APPLIED receipt; the legacy
implementation uses the same native API call. The operator then read Settings → User Interface without changing it: the active profile is
**Dual Display (Studio)**, with two displays and selected display 2 at 200% scale. The visible
application exposes MIX and EDIT. ARRANGE remains a native no-op in this profile, preserving
the same legacy API call; it is not recorded as a migrated-input failure. Final Frame coverage
uses the available MIX and EDIT layouts.

## Held-reload delivery and cleanup

`held-reload-waiting.json` records candidate `20260906T164738Z-36252a87aa8a6b8dcf191cc8563e2619`
published while old build `20260906T164443Z-64f870a4132019d04c1e7d62bf5f5266` remains active.
The waiting trace shows generation 3 and knob1 ownership of exact LIVE6. Browser KEEPALIVEs keep
the physical touch alive across the build/publication; its timeout does not release the gesture.
In the complete release trace, entry 4 at 467646µs delivers TOUCH END to generation 3 and returns
empty requested touches with host touched-controls already empty. Entry 5 at 467724µs applies that
result. Entry 6 at 548140µs starts generation 4; entry 8 at 548516µs activates the exact candidate ID.
No completion is delivered to the replacement. The post-reload sample remains released and
preserves project macro values 518/617/512. Recorded Bitwig PIDs 58202/58203 are unchanged.

## Final regression failure — checkpoint 6272f48d is not ready

`final-regression-*` completed these generation-4 steps before the failure:

- Automation writing is off at revision 222; metronome and tick playback are off at 230.
- Accent velocity returns to 127 at 237, its page returns to TRACK at 240, and Accent is disabled
  at 244 with the applied velocity table restored to identity. Those snapshots have no leftover
  touch or button state after their respective releases.
- Project remote 0 retains exact project-remote owner and LIVE6; later revisions 250→252 show
  518→558 (-11.7→-9.8dB). Its BEGIN action result owns knob1→LIVE6; its END action result is
  empty and revision 253 has no touched controls. This is post-reload macro movement on generation 4.
- TRACK page selection itself is observed at 258. The following ROW1_1 track-selection BEGIN
  receives an ingress APPLIED receipt, but subsequent KEEPALIVE and cleanup never reach terminal
  success. **No successful track-selection or final cleanup is claimed.**

The backed-up `selection-stackoverflow-BitwigStudio.log` contains the stack overflow. The observed
recursive chain passes through selected-note detachment, MIDI neutralization, debugger edge
cancellation, synthetic END, state refresh, and detachment again. A bounded parent-owned
reentrancy fix is implemented and passes the 851-test gate. Preserve the earlier results as evidence for this failed
checkpoint; require a new checkpoint, exact shell/core activation, and the selection/reload
regressions before marking the live smoke ready. The held-reload success above does not erase
this later independent failure.

## Prepared final regression

After the Mac is unlocked, reacquire the live lease, launch Bitwig with the installed checkpoint,
open the saved scratch project, and run exact `tools/reload-core --timeout-ms 20000` activation.
Start this worktree's debug surface at its normal local port. The bounded artifact drivers are:

1. `python3 target/migration-evidence/verify_selection.py --prefix postfix-selection-v1`:
   two footer selections, exact private/cursor/bank/parameter-owner agreement, then volume motion
   and touch release on each. Route-invalidation cancellation is separately correlated to the
   exact BEGIN receipt; it is not treated as proof that selection succeeded.
2. `python3 target/migration-evidence/verify_reentrant_frame.py`: repeated row gestures while
   Master remains held, MIX/EDIT host read-back and long-hold return.
3. `python3 target/migration-evidence/verify_reentrant_macros.py`: project volume, pan reset and
   touch cleanup; leaves the macro page ready for the following held-touch reload.
4. `python3 target/migration-evidence/verify_reentrant_reload.py`: candidate waits for touch END,
   exact new build activates afterward, Bitwig PIDs remain unchanged, and released state persists.

These scripts are prepared and syntax checked, not executed on the replacement. Preserve new
driver logs, exact activation IDs, action traces, later snapshots and controller images under
`target/migration-evidence/`. Inspect the new Bitwig log for the original recursion and any new
fault, then save the scratch project and release the live lease.
