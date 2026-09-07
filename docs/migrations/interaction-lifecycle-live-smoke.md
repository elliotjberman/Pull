# Interaction lifecycle live smoke — 2026-09-07

Validation is in progress on `202arp3`, under one uninterrupted `lifecycle-api47-smoke` lease.
The original project file remains unchanged: SHA-256
`80e5cb6f30bfee568eb33c588926042491259faede2a2ae7a879720572cf4bf9`.
Raw request receipts, host snapshots, controller output and failed attempts are retained locally
in `target/live-api47`; only completed cases below count as evidence. A durable archive is at
`~/.drivenbymoss/pull/test-evidence/lifecycle-api47-20260907/evidence.tar.gz` (SHA-256
`0a99e7ccc653b83fff907cc5b031b6f92181987119228a383b97aa558be6f01b`).

## Completed checks

| Source | Real routed checks and later evidence |
| --- | --- |
| `32ecb416` | Track, Volume, Pan, Send, Master, Frame, Accent, Automation and Metronome pages; Browser open/cancel; Setup/User navigation. Host page state and actual output frames agree; temporary returns preserve the grid. |
| `32ecb416` | Existing Rippler project macro: exact mapped target On → Off → On, later host values and rendered text, baseline restored. |
| `32ecb416` | Track touch across Master/Device: old lease retires, old motion stays inert, fresh touch works, original pan resets exactly. Frame cancellation cannot change the layout; releasing an older temporary owner cannot close Accent. |
| `32ecb416` | Session clip launch/stop and transport start/stop; later host playback flags and transmitted pad/Play lights. |
| `4325a2da` | Selected track A → B → A with an encoder held: neither target changes from canceled motion. Canceled Mute release is inert; fresh Mute/Solo/arm changes are observed and restored. |
| `4325a2da` | Raw strip 12000 survives Master, centers on leaving its view, ignores both old tails, accepts fresh 10000 and centers on release. |
| `4325a2da` | VS fill: exact active owner/target, cancellation on track loss, later owner retirement, no revival on return, fresh acquisition and later release retirement. |

The drum rate/pressure run exposed an inherited grid-release leak and is **not** a clean workflow
pass: an old Drum PAD END reached the temporary Session handler and started playback. Commit
`bf5bc4d7` fixes this centrally and passes the full 858-test package (403 core, 11 publisher, 444
shell; only unchanged `TransportImpl` deprecations). The Mac locked before its install. Final matched
build validation and held-input hot reload remain pending; the installed shell is still `4325a2da`.

## Findings and shortcuts

- Fixed: the VS wrapper hid the fill view's target/cancellation hooks. Composing `DrumFillView`
  directly removes the wrapper behavior and 60 net lines. Its full-core regression failed before
  the fix and passes afterward.
- Fixed: debugger target cleanup synthesized physical releases, masking lifecycle bugs. Native
  MIDI neutralization now preserves physical holds; terminal/core invalidation still retires them.
- Fixed offline: the common grid dispatcher now captures its receiver at DOWN and invalidates it
  on view/target loss. Orphan LONG/UP cannot reach another view or revive on return. The routed
  regression also proves ordinary Session DOWN/UP still launches normally.
- Existing debug traces are bounded to 2 MB. Busy meter/Drum output can truncate the tail. Smoke
  helpers retain only complete records and use separate later host samples; missing required input
  evidence fails the case. Faster client polling reduces idle collection before the actual input.
- Reused smoke helpers stay in ignored build output. They are evidence tools, not added product
  tests or a second implementation of controller behavior.

## Limits

The full Push debugger includes transmitted lights/strip state and output frames, not just a screen
mock. Browser input cannot trigger Bitwig's native learned MIDI actions; physical mapping, audible
notes/pitch and hardware feel remain manual checks with the Push connected. Frozen Device controls
lack classified `ACTIVE` parameter read-back, so that boundary check proves old-target safety and
routing without claiming exact replacement-device parameter behavior. This is functional smoke,
not a timing benchmark or exhaustive validation of the remaining Device/Session editing families.
