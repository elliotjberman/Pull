# Complete the Pull Core Migration

Started 2026-09-05 from `a905be84` (Core API 44, Bitwig API 25).

## Scope and completion

Preserve reachable controller behavior while moving product decisions out of `pull-shell`.
The initial focus is the shared mechanisms needed by Session, Drum Controller, Project Macro,
Track/Mix, and Master. The overall inventory also includes the inherited browser, device, scale,
configuration, and sequencer families; completing the first group does not complete the migration.
No inherited feature is retired without an explicit product decision.

The separate core-reload quiescence investigation is recorded on `codex/quiescence-finding` and is
not being implemented as an incidental part of this migration.

Completion means all supported mappings, gesture variants, navigation, layout, lights, and display
policy are core-owned. Stable retains initialization, observations, typed effect execution, target
validation, resource lifecycle, MIDI/USB transmission, and loading. Adapter names disappearing is
not sufficient if their behavior has merely moved into another stable class.

## Work sequence

| Work | Required shared capability | Status |
| --- | --- | --- |
| Project Macro touch and Delete reset | Exact parameter-touch lifetime, automation/configuration observations and effects | Implemented; focused and full offline tests passed; live pending |
| Track/Mix and Master touches | Reuse touch mechanism; include send-enabled and page-specific variants | Implemented; full offline package passed; live pending |
| Session grid, scenes, and navigation | Bounded slot/scene snapshots, exact actions, navigation and configuration, owned feedback | 21 characterization tests and optional slot/scene read-back implemented; release contract decision pending |
| Session/Drum raw pitch bend | Generic strip output plus core-owned gesture policy | Implemented with gesture continuity and browser debug ingress; offline tests passed; live pending |
| Drum octave and velocity mapping | Bounded desired musical map, authoritative base, output/notification ownership | Implemented and offline tested; musical-claim validation added; live pending |
| Track/Mix menus and ordinary mixer pages | Visible-track sends and I/O, parameter/page ownership | Implemented for normal and VS Live; full offline package passed; live pending |
| Device and browser pages | Bounded device/layer/browser state and actions | Pending |
| Note layouts, scales, repeat, configuration | Core-authored musical maps and configuration policy | Pending |
| Clip editing and sequencers | Bounded clip-content windows and editing effects | Pending |
| Remaining global controls and temporary pages | Complete transport/application/configuration capabilities and notifications | Tap Tempo and Undo/Redo implemented and offline tested; Metronome/Automation audit complete, implementation pending |
| Remove compatibility architecture | All remaining adapter consumers migrated; findings resolved individually | Pending |

Shared physical bindings require a full variant audit. A Session-only implementation must not make
the same scene, navigation, or octave control inert in an inherited view. Conversely, an exclusive
core route must never fall back to a second stable implementation of its claimed behavior.

## Validation

- Baseline `mvn -o -pl pull-shell -am test` passed on the starting checkout.
- Full `mvn -o -Dmaven.compiler.showDeprecation=true package` passed with **685 tests**
  (283 core, 11 publication, 391 shell) after the Project/Master touch, raw strip, Drum octave/map,
  Track page, Tap/Undo, output-fault cleanup, and optional Session read-back work. Six deprecation
  warnings were in unchanged legacy `TransportImpl`; none were in changed code. No live smoke
  test has run for this worktree.
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
