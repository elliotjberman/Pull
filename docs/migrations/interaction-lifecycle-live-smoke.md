# Interaction lifecycle smoke — 2026-09-07

The corrected API 47 shell/core passed routed live smoke in `202arp3` under the continuous
`lifecycle-api47-final-smoke` lease. Earlier runs used `lifecycle-api47-smoke` and found the bugs
below; only the corrected `checked-*` runs establish final coverage. The saved project was reopened
without saving smoke changes and left stopped, with no held controls or parameter-touch leases.

## Exact build and evidence

| Item | Identity |
| --- | --- |
| Tested production source | `bf5bc4d7`; subsequent edits are documentation only. |
| Installed extension SHA-256 | `bf07cba27029bca9287969e0198fc2898dc1cfeb84747af47adcc638802b7f18` |
| Core before held reload | `20260907T193458Z-fdbd9b0cfabf6f9c7f35c22fe97d9279` |
| Core left active | `20260907T193849Z-23bb97b20e244e723f9009132986fe42` |
| Active core SHA-256 | `0d0ff99a5801901f0760114c3fd554e721ce0eb9db15aa73e6c668a9226562b1` |
| Parent API fingerprint | `00ce7163ad429e18f0d4d8ec3d4484293a67b339` |
| Bitwig PIDs before/after held reload | `66685`, `66686`, unchanged. |
| Saved project SHA-256 before/after | `80e5cb6f30bfee568eb33c588926042491259faede2a2ae7a879720572cf4bf9` |

Raw receipts, bounded traces, host snapshots, output frames, failed attempts and helper scripts are
in ignored `target/live-api47`. Durable archive:
`~/.drivenbymoss/pull/test-evidence/lifecycle-api47-20260907/evidence-final.tar.gz`, SHA-256
`df56999e83c4f29e60bd8f9a5f31d46e7b5ada570c082995f8d1b142b535ad91`;
`manifest-final.json` alongside it records final installed identities and restored project state.

The deprecation-enabled full package passes **858 tests** (403 core, 11 publisher, 444 shell), with
no failures/errors/skips and no changed-code deprecations. Six warnings remain in untouched
`TransportImpl`. Independent read-only review checked the critical live receipts/read-back/output.
The separate two-agent arch-nemesis finishing review has not run for this integration; the earlier
attempt hit the agent limit. This record does not claim that review passed.

## Verified behavior

| Evidence prefix | Routed action and later observation |
| --- | --- |
| `checked-drum` | Hold rate/play pad through track A → B → A: canceled pressure/rate tails stay inert, original END cannot launch Session playback, fresh rate/pressure works, release retires it. |
| `checked-fill` | VS fill exact active owner/target and playing state; track loss retires that owner while the physical pad remains held, return cannot revive it, fresh acquisition/release retires later. |
| `checked-playback` | Session clip launch/stop and transport start/stop, later playback flags and transmitted pad/Play lights. |
| `checked-target` | Held pan target A → B → A: lease retires, old motion changes neither raw baseline. Canceled Mute release is inert; fresh Mute/Solo/arm changes and restores observed state. |
| `checked-rippler` | Existing mapped Boolean project macro: exact target On → Off → On, later host value and rendered toggle/text; original value restored. |
| `checked-tour` | Track, Volume, Pan, Send, Accent, Metronome and Automation pages; authoritative page/settings and actual frames agree, temporary returns preserve grid and preferences. |
| `checked-life` | Parameter hold across Master/Device: old lease retires and motion remains inert after return; fresh touch works. Frame cancellation cannot perform an old action; older temporary release cannot close newer Accent. |
| `api47-reload` / `api47-reloaded` | Old core remains active while a touch is held after candidate publication; replacement activates after END, inherits no touch, then acquires and retires a fresh exact lease. |
| `checked-strip` | Held 12000 survives Master; leaving the raw-strip view centers to 8192. Old motion stays inert after return; fresh 10000 works and release centers. |
| `checked-extra2` | Master/Frame and held Device return; Frame remains held beyond six seconds. Setup/User enter/return; Browser opens and cancels back to exact page/grid. Host state and output frames agree. |
| `checked-restored` | Reopened original `202arp3`, transport stopped, no pressed/touched controls or touch leases, original raw pan 0.5 and saved file hash unchanged. |

## Bugs and harness limits

Smoke exposed and fixed three gaps: the VS wrapper hid fill target/cancel hooks (now composed
directly, 60 net lines removed); debugger MIDI cleanup synthesized physical releases (now preserves
holds); and the stable grid dispatcher sent an old Drum END to Session (now retires its captured
receiver on binding loss). Each production fix has a deterministic routed regression.

Busy output can exceed the 2 MB trace bound; helpers use complete records and fresh later samples.
The critical old Drum END and later playback observation were untruncated. Setup/User are stable
bindings with no child input event; an initial overly strict harness assertion was corrected to
use correlated permanent-ingress receipts and later host/UI output, as for Device/Browse.

The full debugger observes transmitted display, lights and strip state. It cannot trigger Bitwig's
native learned MIDI actions or prove audible notes/pitch and physical feel; those remain checks with
the Push connected. Frozen Device controls lack classified `ACTIVE` parameter read-back, so their
boundary test proves old-target safety and routed page return, not exact replacement-device writes.
This is functional smoke, not a timing benchmark or exhaustive Device/Session editing validation.
