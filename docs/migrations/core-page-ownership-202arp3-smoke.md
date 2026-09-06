# API 46 follow-up smoke in 202arp3

Date: 2026-09-06. The user corrected the project name to `202arp3`. This follow-up used the same
production checkpoint `11e33477b352c3020df95272f24b957e17026d16` as the
[previous page/release/reload smoke](core-page-ownership-live-smoke.md). The package gate remains
932 passing tests (458 core, 11 publisher, 463 shell); no production code changed during either
live pass. All project opening and debug input in this follow-up ran under the
`core-pages-202arp3` live lease, held through final restoration and verification, then released.

## Exact environment

- Project: `/Users/elliot/Documents/Bitwig Studio/Projects/2026/202arp3/202arp3.bwproject`.
- Observed project UUID: `9e843a53-c90d-42b7-bbea-ce01321f2b2f`.
- Initial and final file SHA-256: `0bf1f3bff6765c3f780211446e00767cadd656d758f2eca765a5465c02a33394`.
- Installed extension SHA-256: `76f9acd7cdc2c4994fc633683c181ab093b450fd26a48cb583e407390b49e251`.
- Shell fingerprint: `4a619320687a5034a602e606ac3050309f8d06b8`.
- Active core: `20260906T191816Z-08e2740a0977a21696681a546110894e`, generation 3.
- Core JAR SHA-256: `5f8d08d596b80cd78bfc004359175c4ece510d107d882154f2c9f07895ebc3b1`.

The project file was backed up before test input. Later subscribed project identity, rather than
the OS open acknowledgement or tab title alone, established the controller's active project.
The user identified audio-engine errors as resulting from their changes and asked us to ignore
them. The engine remained inactive; audio configuration was not changed and audible playback is
not part of this pass.

## Routed input, later state and output

Evidence lives in the persistent worktree's ignored `target/page-ownership-evidence/` directory:
`arp3-run.json`, bounded traces, parsed observations, correlated input receipts, surface output
JSON and request-correlated 960×160 framebuffers. These are the full Push debugger's backend,
including buttons, holds, encoders, pad/button light output and display output. Computer use
confirmed the project UI, but mouse clicks failed with an incorrect screen-coordinate transform.
The debugger drove the controller tests.

| Evidence prefix | Verified result |
| --- | --- |
| `arp3-discover` | Exact project, Drum Machine (Pitch) selected, Track page and full Session observed before inputs. |
| `arp3-master` | Track → Master → Track retains the Session bank and native musical translation. Actual parent light output swaps Master/Track palette values 127/30 → 30/127; see `arp3-button-output-proof.json`. |
| `arp3-frame` | Frame remains open beyond six seconds while Master is held; release returns to Track with no temporary owner left. |
| `arp3-release` | Press a Frame layout button, leave Frame while held, then release: exactly one original Frame action, Track stays visible, and later native layout changes MIX → EDIT. Original MIX layout restored and read back. |
| `arp3-tour` | Volume, Pan, Send1, Accent, Metronome and Automation page navigation, typed scenes, later host state and framebuffers. Original mixer preference restored. This tour does not claim parameter movement on each page. |
| `arp3-vs` | Eight-track/four-scene composition; Macro → Master → Macro preserves that grid and musical translation. |
| `arp3-device` | Frozen Device hold enters Device Parameters; release restores the exact prior Macro page/grid. |
| `arp3-bopen`, `arp3-bclose` | Raw Browser lifecycle advances closed → open → closed. Shift+Browse cancels and returns to the exact Macro page/grid. |
| `arp3-rippler` | Project remote slot 2, physical knob 3: exact target 93/generation 0 reads 1023/On → 0/Off → 1023/On after routed motion. Both touches release; all subscribed parameter DTOs return to baseline. Off/On framebuffers visually inspected; final framebuffer hash equals the origin. |
| `arp3-touch2` | Track Pan's already-applied target remains held across Device entry, without a new knob-2 TOUCH route on Device. Original END clears it while independent parent receipts/surface state still show Device held. Device release restores Track. No parameter movement. |
| `arp3-end`, `arp3-final-state-check.json` | Track/full Session, exact selected target, parameters, transport, automation, controller settings, bank contents and native translation match the initial observation. All core and parent physical inputs released. Installed extension and saved project hashes unchanged. |

An independent read-only audit checked 97 completed traces and 332 result-bearing rows. Applicable
results have matching same-generation parent application evidence; no failure, overflow or
truncation markers were present. Later host observations remain separate proof from input receipts
or successful command submission.

The project remains open and unsaved. Bitwig's dirty flag and undo availability changed during
testing, even though the observed values were restored. Browser/layout/bank generations and the
retired navigation prefix advanced normally; reacquired parameter handles changed opaque identity
while semantic domain/owner/page/index and live generations matched. The final audit explicitly
records those metadata differences rather than claiming byte-for-byte in-memory restoration.

## Remaining limits and useful physical checks

- Rippler is a verified two-state macro. Other macro labels/values were observed, but arbitrary
  continuous macro precision and `PermReset` behavior were not exercised. The inherited
  [Snapback precision limitation](../findings/snapback-v1-limitations.md) is documented separately;
  this pass does not reproduce Snapback restoration directly.
- Parent touch ownership has no direct Bitwig `touch()` read-back. Captures prove outgoing display
  and light transmission, not the physical panel or every individual LED.
- Device/Browser bodies remain frozen legacy code. This verifies their cooperation with core page
  ownership; it does not complete their migration.
- General encoder-touch-to-motion and pad-to-pressure capture remains an
  [active finding](../findings/core-continuous-input-capture.md). Original-view edge completion is
  the implemented primitive.
- With audio working, test physical drum/note playing, clip/scene launch and Stop chords while
  listening. Relearn and test the intended Bitwig MIDI mappings on the physical Push: debugger
  input enters after Bitwig's MIDI matcher and cannot invoke learned hardware actions.

The page/navigation/gesture smoke passed in the corrected project. No production patch or Bitwig
restart was needed for this follow-up. This is a scoped pass, not an all-features or audio pass.
