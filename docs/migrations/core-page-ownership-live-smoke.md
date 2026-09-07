# Migration part 1: historical API 46 validation

This records PR #40 only. [Current activation and lifecycle evidence](interaction-lifecycle-live-smoke.md)
supersede its deployment status. The original-release/offscreen-touch cases below describe the
then-current policy; API 47 uses cancellation on binding loss.

## Tested production source

On 2026-09-06, production checkpoint `11e33477b352c3020df95272f24b957e17026d16`
passed the deprecation-enabled package: **932 tests** (458 core, 11 publisher, 463 shell), no
failures/errors/skips; six warnings were confined to unchanged `TransportImpl`.

Installation/publication and all routed input ran under uninterrupted live leases
(`core-page-ownership`, then `core-pages-202arp3`). Project files were backed up before input and
were not saved by the smoke. Evidence includes activation receipts, bounded traces, later host
observations, surface output and request-correlated 960×160 framebuffers. Before retiring the
part-1 worktree, its live/cleanup evidence and project backups were archived locally at
`~/.drivenbymoss/pull/test-evidence/pr-40-migration-part-1-20260906/evidence.tar.gz`.
The adjacent `manifest.json` records all 789 files, verified by SHA-256 against the source.

| Identity | Value |
| --- | --- |
| Installed extension SHA-256 | `76f9acd7cdc2c4994fc633683c181ab093b450fd26a48cb583e407390b49e251` |
| Tested shell fingerprint | `4a619320687a5034a602e606ac3050309f8d06b8` |
| Initial core | `20260906T191230Z-58fab0937c8e0f2912d74028445f4584` |
| Initial core SHA-256 | `0001b0e7bb4fb0cc63f46d0e97b234ec96f845ad50cf648c1daadb5506607415` |
| Post-hold-reload core | `20260906T191816Z-08e2740a0977a21696681a546110894e`, generation 3 |
| Post-reload core SHA-256 | `5f8d08d596b80cd78bfc004359175c4ece510d107d882154f2c9f07895ebc3b1` |
| 202arp initial/final file SHA-256 | `6111200b1feb101d5640453d9c3ddfe793e277b6823c206eea35592bd353540b` |
| 202arp3 initial/final file SHA-256 | `0bf1f3bff6765c3f780211446e00767cadd656d758f2eca765a5465c02a33394` |

Projects were the respective `202arp/202arp.bwproject` and `202arp3/202arp3.bwproject` under
`/Users/elliot/Documents/Bitwig Studio/Projects/2026/`. Later subscribed identity established the
active project; an OS-open acknowledgement or tab title did not. The corrected project `202arp3`
reported UUID `9e843a53-c90d-42b7-bbea-ce01321f2b2f`.

## Verified scope

| Scenario / evidence prefix | Later observation and output |
| --- | --- |
| Master and VS, `p46-master`, `arp3-master`, `*-vs` | Track↔Master and Macro↔Master preserve exact Session bank/native translation; VS uses 8×4. Master/Track parent light output changes 127/30↔30/127. |
| Original Frame release, `*-release`, `*-frame` | Leave Frame while holding its row button, then END emits exactly one original action while Track remains visible; later native MIX→EDIT, restored MIX. Long Frame holds exceed six seconds and return cleanly. |
| Page tour, `p46-tourb`, `arp3-tour` | Volume/Pan/Send1/Accent/Metronome/Automation identities, typed scenes, later state and framebuffers. This is not parameter-write coverage for every page. |
| Legacy return, `*-device`, `*-bopen`/`*-bclose` | Held Device returns to exact prior Macro/grid; raw Browser open→closed cancellation returns likewise. Legacy bodies remain unmigrated. |
| Track input, `p46-track-param`, `p46-selection-return` | Pan write/read-back and footer MC-202→Microtonic→MC-202 with aligned private/cursor/bank/parameter identities; no reentrant cleanup crash. |
| Exact touch continuation, `p46-touch2`, `arp3-touch2` | Already-applied Track pan target survives Device entry without a new legacy TOUCH route; original END releases while Device is still held. No movement in this scenario. |
| Mapped macro, `arp3-rippler` | Remote slot 2/knob 3: exact two-state target On→Off→On, later values 1023→0→1023, touch cleanup and observed display restoration; final framebuffer hash matches baseline. |
| Held reload, `reentrant-reload-*`, `p46-held-reload.log` | Candidate waits for old-generation touch END, then exact replacement activates; Bitwig PIDs 93672/93673 unchanged. |
| Restoration, `p46-pan-exact`, `*-final-state-check.json` | Original raw centered pan restored using native reset, semantic parameter/controller/transport/bank state restored, inputs released, saved file hashes unchanged. |

The `202arp3` audit checked 97 completed traces and 332 result-bearing rows with matching
same-generation application evidence and no failure/overflow/truncation markers. Application
receipts were not used as substitutes for later host read-back.

## Limits and part-1 cleanup

- `202arp` had no named project remotes; its macro-write scenario stopped before input.
  `202arp3` closes only the mapped **Boolean** macro gap. Arbitrary continuous precision,
  `PermReset`, and direct Snapback restoration were not tested.
- Integer inverse steps are not exact raw restoration. The harness now checks raw pan/display text;
  the inherited [Snapback precision limitation](../findings/snapback-v1-limitations.md) remains.
- Outgoing display/light transmission is not physical panel/LED proof; parent touch ownership has
  no direct Bitwig touch read-back. Debug input enters after native learned-MIDI matching.
- No audible playback/recording pass is claimed. The user attributed audio-engine errors to their
  changes and asked that they be ignored; audio configuration was left alone.
- General touch→motion and pad→pressure capture was separate from this build; see the later
  [API 47 lifecycle integration](../interaction-lifecycle.md), which has its own validation boundary.
  Release success does not prove a general asynchronous reload drain.
- Restored values do not mean identical in-memory history: dirty/undo flags and expected opaque
  handles/generations changed. The project remained unsaved; no history was cleared to hide this.

**At the part-1 handoff, post-cleanup live testing had not been performed.** The cleanup candidate
had not been installed or reloaded, and the installed source was `11e33477`. These historical
checks do not imply live coverage for that cleanup or for later lifecycle changes.

The cleanup code at `862c4db6` passed a fresh module-clean deprecation-enabled package: **826 tests**
(375 core, 11 publisher, 440 shell), no failures/errors/skips and no changed-code deprecations.
Macro ring/toggle/touch and Master engine on/off display/light output matches the exact tested
core JAR after renderer consolidation. This one-off comparison lives in ignored cleanup evidence,
not in the permanent test suite. The installed extension hash above was rechecked unchanged.
GitHub's Java tests workflow also passed for that commit, including the debugger-client, live-lease
and surface-server checks. The final part-1 handoff changes documentation only.

Earlier API-45 smoke history is superseded by this scoped record. Its reentrant cleanup failure
and resolved review history remain in Git; current compromises are in the
[shortcuts ledger](migration-shortcuts-and-friction.md).
