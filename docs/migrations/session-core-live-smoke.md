# Session and lifecycle smoke — 2026-09-07

Core API 49 / Bitwig API 25 passed the scoped routed smoke under the continuous
`session-core-api48` lease (the label predates the final API bump). Source `daa75713` passed
953 package tests: 463 core, 479 shell and 11 publisher, no failures/errors/skips or changed-code
deprecations. Six existing warnings remain in untouched `TransportImpl`. PR CI also passed.

| Identity | Value |
| --- | --- |
| Production source | `daa75713b451e4207e31969e6c432a8a759bb9ba` |
| Installed shell SHA-256 | `5250d874effb2cb52ae44acef47f60c169b2d823924b3ae6067b70845c6a1a90` |
| Core before held reload | `20260907T214630Z-4ad21bec2a7f3fffd6ec3fa390277f4a` |
| Core after held reload | `20260907T220952Z-3ed7e17671c6abb18a948253bec31924` |
| Final core SHA-256 | `bdf326c036e45bc39bc8e203b3c0a351cd116da382964148233aa16e31f329e6` |
| Parent fingerprint | `f54abb813e7d2f9f9e9a1af750e8c66efce69440` |

| Evidence prefix | Routed behavior and later observation |
| --- | --- |
| `final2-session` | Changed selection preserves exact main/alternate release; shape departure cancels once, old END stays inert, 8×4 aligns after release; scene navigation selects after bank read-back. |
| `final-track4` | Held clip follows its channel after an earlier track is duplicated; old END has no effect; Undo restores the bank. |
| `final-scene-delete` | Earlier scene deletion shifts the playing clip and reduces count 20→19; location generation changes, old END stays inert, Undo restores count/content. |
| `final-record2` | END precedes recording acknowledgement. Later recording read-back permits ordered launch/release on the exact target; playback and transmitted blink agree; only the new clip is deleted. |
| `final-playback`, `final-strip` | Clip/transport launch and stop; held raw bend survives Master, leaving its view centers it, old motion stays inert and fresh touch works. |
| `final-drum`, `final-fill` | Rate/pressure and fill owners retire on target loss; returning while held cannot revive them; fresh acquisition and release work. |
| `final-life`, `final-pages` | Held parameter cancellation through Master/Device, inert old motion, temporary-page ordering, Frame cancellation and page/settings/display agreement. |
| `final-integrated-pages2` | Selected footer opens Device while retaining Session; Info displays the observed firmware/board/serial tuple and returns to Setup. |
| `final49-reloaded` | Candidate waits for held touch END; new core inherits no touch and acquires/retires a fresh lease. |

The test used a copied `202arp3`, including its initial unsaved state. The original saved file's
SHA-256 remained `47d4ec6089c006aca4aebf1d53eef45da54992c69de01a78bddadf62f20c15d4`.
Tests ended stopped with no held controls or touch leases; the preserved pre-test copy was reopened,
then Bitwig was returned to the user. A durable copy with assets is kept under the evidence directory
at `preserved-project/202arp3/202arp3.bwproject`. Its baseline SHA-256 is
`38e192c58c71c8a1d8a668b403b2083e412867e84081707b65368bc546e0f269`.

Receipts, traces, snapshots, transmitted output and helpers, including failed attempts, are archived at
`~/.drivenbymoss/pull/test-evidence/session-core-api49-20260907/evidence-final.tar.gz`, SHA-256
`f8d13256b012f7db6102c7e90807ea6c035f09333269686f94c27d67a480114d`; `manifest-final.json` records provenance.
Earlier helper failures assumed scene insertion for the existing next-scene copy action, missed a
recording transition between short traces, or expected a serialized computed availability accessor.
Corrected cases above use authoritative observations; those failures are not counted as passes.

Native release-before-mutation ordering is established by source and production-adapter tests;
live traces show routed effects and later state, not an independent log of each native call.
API 25 release is submission, not completion. This smoke does not prove audible/native learned MIDI,
physical feel or exhaustive editing variants; create-clip preference variants retain offline coverage.
General asynchronous reload draining and remaining shell adapters stay explicitly tracked.

## API 50 installation and scoped follow-up

The combined source, including Setup/Ribbon settings, arrow navigation and light refresh, passes
1,018 package tests (522 core, 485 shell, 11 publisher), with no failures/errors/skips or changed-code deprecations.
It was installed and activated on 2026-09-07:

| Identity | Value |
| --- | --- |
| Source | `2fa6373650b168b150088965a9df1658c6dc578b` |
| Installed shell SHA-256 | `2cde489640cf876f533526181855be4bb5bf377372a337f3018b6dd822f99dc8` |
| Active core | `20260907T223632Z-c638c37020505beae7d7becd85c4ef89` |
| Core SHA-256 | `37840df6919ae5725d97979462d2f36e40adf2c8a85f2bd55f7c498a356de104` |

`api50-settings` passed physical Setup/Info entry and hardware read-back, held-encoder cancellation
across tabs, a fresh brightness edit with later value/display read-back and exact restoration, and
plain/Shift track arrows with restored bank/selection. It ended on Track with no held inputs.
Ribbon, Session and reload were not rerun on API 50; their earlier evidence is not an API 50 pass.

The working project is in the normal `202arp3 - Session core` folder; its saved SHA-256 is
`cc9bad4232c03b738eea35ecea9c97ec8991ff71e66f8443a131907721262872`.
The archive is `~/.drivenbymoss/pull/test-evidence/session-core-api50-20260907/evidence.tar.gz`
(SHA-256 `05f4673eb8067c03d2203c526863303c22f7cfbdba2cebbaa1e071d5c8ffcadd`);
`exact-install.json`, `activation.log`, `settings.log` and `api50-settings-verified.json` identify the evidence.
