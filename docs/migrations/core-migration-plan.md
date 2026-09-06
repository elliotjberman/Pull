# Complete the Pull Core Migration

Started 2026-09-05 from `a905be84` (Core API 44, Bitwig API 25).

## Scope and completion

Preserve reachable controller behavior while moving product decisions out of `pull-shell`.
The initial focus is the shared mechanisms needed by Session, Drum Controller, Project Macro,
Track/Mix, and Master. The overall inventory also includes the inherited browser, device, scale,
configuration, and sequencer families; completing the first group does not complete the migration.
No inherited feature is retired without an explicit product decision.

The separate [core-reload quiescence investigation](../findings/core-reload-quiescence.md) is
recorded here and is not being implemented as an incidental part of this migration.

Completion means all supported mappings, gesture variants, navigation, layout, lights, and display
policy are core-owned. Stable retains initialization, observations, typed effect execution, target
validation, resource lifecycle, MIDI/USB transmission, and loading. Adapter names disappearing is
not sufficient if their behavior has merely moved into another stable class.

## Work sequence

| Work | Required shared capability | Status |
| --- | --- | --- |
| Project Macro touch and Delete reset | Exact parameter-touch lifetime, automation/configuration observations and effects | Implemented; live volume/pan mutation, reset and exact touch release passed; final lifecycle-fix regression pending |
| Track/Mix and Master touches | Reuse touch mechanism; include send-enabled and page-specific variants | Implemented; representative live volume/pan/send and Master touch checks passed; final selection cleanup regression pending |
| Session grid, scenes, and page buttons | Bounded slot/scene snapshots, exact actions, configuration, owned feedback, and agreed release contract | Characterization and optional slot/scene read-back implemented; launcher release contract decision and exclusive grid/scene cutover remain pending |
| Session/Drum raw pitch bend | Generic strip output plus core-owned gesture policy | Implemented; transmitted positions, page continuity and release centering passed live; audible pitch not verified |
| Drum octave and velocity mapping | Bounded desired musical map, authoritative base, output/notification ownership | Implemented; live base36→52→36 and native tables passed; Accent velocity table read-back passed |
| Track/Mix menus and normal footer | Named selected-track/send parameters, I/O, current-bank state, exact effects | Normal and VS Track implemented; representative parameters passed live; footer selection exposed reentrant cleanup, fixed offline and awaiting live retest |
| Volume/Pan pages | Named current-bank parameters, exact owner metadata, shared footer, preferences, generic page registry | Implemented; representative live volume/pan changes and pan reset passed; full variant parity not claimed |
| Send pages 1–8 | Eight requested columns across eight current tracks, exact owner/position fences, shared menu/footer/touch machinery | Implemented with stable SendMode deletion; live Send1 move/reset/enable toggle and touch release passed; seven other columns not populated in fixture |
| Four-arrow navigation | Independent current-bank track/scene/cursor fence and installed inert arrow footprint | Core-owned for registered pages and VS; full Session legacy-page arrows and page/sequencer buttons remain declared frozen behavior; 838-test gate passed after review corrections |
| Global Track/Mix button | Raw mode/history read-back, generic mode effects, controller preferences, semantic action barrier | Complete gesture and feedback policy implemented; representative page transitions passed live; full variant parity not claimed |
| Device and browser pages | Bounded device/layer/browser state and actions | Pending |
| Note layouts, scales, repeat, configuration | Core-authored musical maps and configuration policy | Pending |
| Clip editing and sequencers | Bounded clip-content windows and editing effects | Pending |
| Metronome/Automation and temporary settings pages | Generic SELECT/TEMPORARY/RESTORE, transport settings, raw automation mode/reset, exact parameters | Implemented; live toggles, tick playback, metronome volume, latch/return and TOUCH automation mode passed; reset-overrides variants remain unverified live |
| Frame and Master entry | Generic application UI context, thirteen observed flags, native primitives, retained page/background composition | Implemented; live retained grid, Master pan, repeated I/O chord, MIX↔EDIT and >7s hold/return passed; ARRANGE unavailable in current display profile |
| Accent | Observed enabled/velocity preferences, core-authored Drum velocity map, generic page and input admission | Implemented; live velocity read-back/table and >7s hold/return passed; summed-motion clamp deviation recorded in the shortcut ledger |
| Remaining global controls and temporary pages | Complete target/configuration/application capabilities | Tap Tempo and Undo/Redo were included in the 685-test checkpoint; other inherited controls require their own full audits |
| Remove compatibility architecture | All remaining adapter consumers migrated; findings resolved individually | Pending |

Shared physical bindings require a full variant audit. A Session-only implementation must not make
the same scene, navigation, or octave control inert in an inherited view. Conversely, an exclusive
core route must never fall back to a second stable implementation of its claimed behavior.

## Working contract after the first checkpoint

The working parent-loaded API remains **45**; Bitwig controller API remains **25**. Core checkpoint
schema **5** preserves semantic page selection and Track Mix/I-O/send offset. Capability revisions
are bridge snapshot 13, parameter targets 4, controller output state 3, input routing 7, current-track
effects 2, controller-mode effects 2, transport effects 4, controller-settings effects 1, and
application-UI effects 1. API 45 was installed and activated live on 2026-09-06. Exact source,
shell hashes, active core IDs and per-build evidence are in [the live record](core-migration-live-smoke.md).

`ControllerPageCompositions` contains a finite set of page/background combinations that reuse the
actual retained Session/Drum/Note/ribbon views. `installedModeId` declares a registered inert page
footprint and does not select a native mode. Core requests SELECT/TEMPORARY/RESTORE and waits for
later visible/underlying/previous/temporary mode read-back. Project/Track/Master compatibility facets
must agree with that declaration; new pages do not introduce facets.

`ParameterTargetIdentitySnapshot` exposes semantic domain, owner, page and index alongside opaque
actuator identity. Volume/Pan validate row-owner agreement before rendering or mutation, including
when parameter-only publication temporarily precedes current-bank read-back. Exact retained cleanup
remains separate from current target eligibility. The current-bank navigation generation fences
bank/scene position, project, and model cursor ID/pin/position. Application UI effects fence the
observed project/layout context; thirteen cheap native values stay interested while the requested
domain gates DTO sampling. None of these mechanisms moves controller product policy into stable.

## Validation

- Baseline `mvn -o -pl pull-shell -am test` passed on the starting checkout.
- Full `mvn -o -Dmaven.compiler.showDeprecation=true package` passed with **685 tests**
  (283 core, 11 publication, 391 shell) after the Project/Master touch, raw strip, Drum octave/map,
  Track page, Tap/Undo, output-fault cleanup, and optional Session read-back work. Six deprecation
  warnings were in unchanged legacy `TransportImpl`; none were in changed code. No live smoke
  test had run at that checkpoint.
- The expanded deprecation-enabled package passed **823 tests** (385 core, 11 publication,
  427 shell) on 2026-09-05 at 19:47:15 EDT, after generic pages/navigation, Volume/Pan/Send,
  Track/Mix, Metronome/Automation, Frame/Master, Accent, and parameter-owner alignment. Log:
  `/private/tmp/pull-native-pages-package.log`. Six deprecation warnings remain in unchanged
  legacy `TransportImpl`; none are in changed code. Independent architecture/code-size review
  found three P1 defects and one bounded ownership issue, documented in `core-migration-review.md`.
  Those findings were corrected and re-reviewed; this earlier run remains a verification checkpoint, not live proof.
- Post-review deprecation-enabled package passed **838 tests** (398 core, 11 publication, 429 shell)
  at 20:06:20 EDT on 2026-09-05, after integration of `master` at `5537271f` and corrections.
  Log: `target/migration-evidence/post-review-package.log`. Six deprecation warnings remain only
  in unchanged `TransportImpl`. Independent bounded architecture re-review resolved A1–A4 and
  found no new material findings; the size review accepted the correction as written. A stale
  Send test expectation was replaced by separate frozen-selection and stale-layout-cancellation
  checks before this successful run. Live installation/testing was blocked by the locked Mac at that checkpoint.
- Live testing resumed on 2026-09-06 and found two generic cleanup defects: released debug chord
  edges stayed occupied, and selection-triggered cleanup reentered its own route/edge owners.
  The first correction passed 845 tests and live repeated-chord checks. The complete second
  correction passed **851 tests** (398 core, 11 publication, 442 shell) at 13:01:50 EDT, with
  only the same unchanged legacy deprecations. Checkpoint `bf367a06` is installed; its live retest
  requires unlocking the Mac. Earlier live successes do not sign off that replacement.
- Characterize existing behavior before each migration, including modifiers, release, target
  changes, output, and delayed host acknowledgement.
- Keep fake command submission distinct from explicit host advancement and later snapshots.
- Validate every direct Bitwig call against locally resolved API 25 and run the complete package
  build with deprecation reporting.
- Complete architectural/code-size review after implementation, then checkpoint before installing.
- Acquire `tools/with-pull-live` through exact-build activation and the complete live smoke test.
  Record offline and live results separately. A missing live observation capability requires a
  bounded reusable debugger extension, not an assumed success.
- Update `ARCH.md`, the migration roadmap, and affected findings only as their actual conditions
  are satisfied. Do not mark the whole migration complete after one successful slice.
