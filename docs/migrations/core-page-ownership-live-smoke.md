# API 46 page ownership live smoke

Follow-up: the user later corrected the project to `202arp3`. Its
[separate smoke record](core-page-ownership-202arp3-smoke.md) verifies a mapped Boolean macro,
repeats the page/gesture checks, and records restored values and unchanged project-file hash.
The environment limitations below describe this earlier `202arp` pass.

Date: 2026-09-06. Source checkpoint: `11e33477b352c3020df95272f24b957e17026d16`.
Persistent worktree: `.codex-worktrees/core-page-ownership`. All installation, publication, project
opening and routed input occurred under one `core-page-ownership` live lease.

The complete deprecation-enabled package passed **932 tests** (458 core, 11 publisher, 463 shell),
with zero failures, errors or skips. Existing deprecations are confined to unchanged `TransportImpl`.
The independent architecture review found no unresolved P0/P1 findings; retained debt is documented
in [the review](core-page-ownership-review.md) and [friction ledger](migration-shortcuts-and-friction.md).

## Exact activation

- Installed extension SHA-256: `76f9acd7cdc2c4994fc633683c181ab093b450fd26a48cb583e407390b49e251`.
- Shell fingerprint: `4a619320687a5034a602e606ac3050309f8d06b8`.
- First activated core: `20260906T191230Z-58fab0937c8e0f2912d74028445f4584`.
- Core JAR SHA-256: `0001b0e7bb4fb0cc63f46d0e97b234ec96f845ad50cf648c1daadb5506607415`.
- Initial old API 45 publication was rejected by the API 46 shell. The matching publication above
  was then acknowledged active before any smoke input. That compatibility rejection is expected.

The exact requested file was opened:
`/Users/elliot/Documents/Bitwig Studio/Projects/2026/202arp/202arp.bwproject`.
Initial file SHA-256: `6111200b1feb101d5640453d9c3ddfe793e277b6823c206eea35592bd353540b`.
The original project and previous installed extension were copied into the evidence directory
before installation or test input. No project save is part of this smoke.

## Evidence method and results

`target/page-ownership-evidence/` contains the build log, activation metadata, bounded TSV traces,
parsed observations, exact ingress receipts and request-correlated 960×160 outbound framebuffers.
Each scenario checks the exact project identity and stable core generation. Submitted effects and
successfully applied results are distinct from later authoritative host observations.

| Scenario / artifact prefix | Result |
| --- | --- |
| `p46-discover` | Exact `202arp`, selected MC-202 track, stopped transport, Track page and fresh framebuffer observed. |
| `p46-master` | Track → Master → Track; Session bank identities and musical translation preserved. |
| `p46-release` | Press a Frame layout button, leave Frame while holding it, then release: exactly one original Frame action, Track stays visible, later Bitwig MIX→EDIT read-back. Original MIX layout restored and read back. |
| `p46-frame` | Frame remains open with its original button held for over six seconds; release returns to Track with no temporary owner left. |
| `p46-track-param` | Exact selected-track pan touch, one routed encoder step, later changed host value/display, inverse step restores controller-resolution value, release clears touch ownership. Raw pan restoration is separately verified by `p46-pan-exact` below. |
| `p46-vs` | VS Live establishes eight tracks/four scenes; Macro → Master → Macro preserves that bank and musical translation. |
| `p46-device` | Frozen Device hold enters Device Parameters; release restores the exact prior Macro page and VS grid. |
| `p46-browser-open`, `p46-browser-close` | Later raw Browser state confirms opening; Shift+Browse cancels without committing. Closure restores the exact Macro page/grid with no held inputs. |
| `p46-tourb` | Volume, Pan, Send1, Accent, Metronome and Automation pages: exact typed page/scene, later state and framebuffer; original Track page, mixer preference, selected target and musical settings restored. Captures visually inspected. |
| `p46-selection-return` | MC-202 → Microtonic → MC-202 through footer input; private selection, cursor, bank and parameter identities agree afterward. Return triggers a correlated route-invalidation release without the earlier reentrant crash. No parameter writes. |
| `reentrant-reload-*`, `p46-held-reload.log` | A held encoder postpones replacement; new core `20260906T191816Z-08e2740a0977a21696681a546110894e` activates after END. Bitwig process IDs remain `93672`/`93673`. |
| `p46-touch2` | Track knob 2 retains its exact applied target/bank across Device entry without claiming a new legacy TOUCH route; original END clears ownership while Device remains physically held; Device release returns Track. No parameter movement. |
| `p46-pan-exact` | Final comparison detected integer/raw precision differences. Since initial pan was exactly native center `0.5`, existing Delete+pan-touch reset restored that exact raw value; later read-back confirmed it. |
| `p46-end`, `final-state-check.json` | Exact `202arp`, MC-202, Track/full Session restored. Transport, controller settings, pan, volume and Session bank shape match initial state; all inputs released; project file SHA-256 unchanged. |

The page/release/reload smoke passed on the installed source. Project-macro write validation remains
blocked by the environment limitation below; no universal all-features pass is claimed.

## Explicit boundaries

`202arp` currently exposes no named project remote controls. `p46-macro-param` stopped at this
precondition before sending any parameter input. The Mac was locked and computer use could not
unlock it, so adding a mapping through the GUI was unavailable. A copy of the user-mapped saved
`Core Migration Smoke` project was opened through the OS, but Bitwig did not change active project;
its log stops at the opening request. Any pending dialog could not be inspected while locked. The
user was asked to unlock the Mac. All subsequent verified observations still identify `202arp`.
The mapped-macro write test remains pending, not passed.

The generic guarantee is edge completion: BEGIN captures receivers, LONG/END go back to them.
General encoder-touch-to-turn and pad-to-pressure capture remains a documented
[architecture finding](../findings/core-continuous-input-capture.md). Hardware-learned MIDI actions
require physical input before Bitwig's MIDI matcher; browser/debug ingress cannot validate those
learned actions. No audible playback or recording test is claimed by this page smoke.

The two corrected harness assumptions were numeric text parsed as numbers and legacy Device held
state being absent from the core-only pressed-controls set. The revised checks use typed scene text
and fresh correlated parent KEEPALIVE plus physical surface state. The parameter harness now also
checks raw selected pan and host display text before claiming restoration; equal controller integers
alone are insufficient. No production correction was needed during this live pass.
