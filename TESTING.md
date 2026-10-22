# Testing

## Close the loop

The goal of testing is to prove the behavior through the same routed input, authoritative host
state, and controller output that a user experiences. A successful build, submitted command,
input log, or optimistic fake is not proof that the feature worked.

- Drive the real feature path whenever practical, then verify the result from a later Bitwig or
  stable-shell read-back and the resulting hardware/display output.
- If the harness cannot drive or observe a feature, add the smallest reusable, bounded debug
  capability needed to close that loop as part of the feature work. Do not silently substitute a
  manual assumption or a feature-shaped stable-shell shortcut.
- Prefer generic access to an established physical binding, core effect, authoritative state, or
  output seam so the capability can validate later features too.
- Keep debug transports opt-in and off by default. They must be local, bounded, non-blocking on the
  controller thread, and absent from the live-performance path unless explicitly enabled at
  startup or through the controller's Diagnostics settings. Document any installation restart.
- Turn live failures into deterministic offline regressions where the boundary can be modeled, but
  retain a live Bitwig/Push smoke test for behavior that fake-host tests cannot prove.

## Offline test loops

Keep tests that protect observable controller behavior or the real Bitwig, hardware and reload
contracts. A fake host is useful when it separates submitted input/effects from later host state;
assert the resulting action, target, feedback or failure behavior. Prefer that path over a second
test of its private bookkeeping. Do not retain constructor/getter checks, internal call counts,
serialized implementation hashes, or migration-only probes merely because they helped build the
feature. Share small fixtures when they remove repetition without hiding host advancement or
target identity. Git history preserves discarded scaffolding.

Run the reloadable controller core and its shell fakes without building the Bitwig extension:

```bash
mvn -pl pull-core -am test
```

After that first run has downloaded the test dependencies, repeat fully offline:

```bash
mvn -o -pl pull-core -am test
```

This loop uses deterministic fake time and does not launch or require Bitwig. Shell-side proxy,
routing, and lifecycle tests run with:

```bash
mvn -o -pl pull-shell -am test
```

For changes touching Bitwig API objects, follow `AGENTS.md` and run the complete package build with
deprecation reporting before the live smoke test.

## Group drum target fallback (API 60)

Capability audit: **B — bounded shell/API expansion**. No new physical input or output claim.
The existing Drum Pad/VS action and feedback slice follows one core-chosen child: playable
pads/pressure, octave, automatic roll, learned control mappings and fill clips. Mixer, Session
and transport keep actual group selection. The shell exposes eight direct child identities,
executes a target-fenced root assignment and preserves exact outgoing route cleanup. Core owns
the first-instrument fallback, without skipping to another sibling based on drum discovery.
Alternate legacy layouts and nested groups are out of scope. One shared root adds no native
equality registrations; source/API changes require shell installation and restart.

Offline coverage separates requests from native pin/track read-back, rejects replaced/removed
children and changed group selection, and checks that pending, absent or removed drums never
skip the first instrument child. Reordering children updates the target. Full core results retain
the child request through subscription decorators; child acquisition neutralizes MIDI without
interrupting a held group-volume touch. The musical owner differs from the selected mixer owner
only after authoritative child state. An idle root releases a restored pin through later host
read-back and resumes selection following without repeated pin writes.

The implementation passed `mvn -o -Dmaven.compiler.showDeprecation=true package` on
October 21, 2026: **1,211 tests**, zero failures, errors, skips or deprecation warnings.
The live test exposed editor-selection changes from moving unpinned track cursors. Both the
group root and shared retained pool now await pin read-back before selecting a target;
regressions cover initial acquisition and reuse without changing editor selection.

Native verification in Bitwig 6.1.4 used source `2a6a3183`, shell SHA-256
`f5eb8129000e9bb953c137ee8af49e0aa413fe044a8f77da432324b4719fed56`, and active core
`20261022T013551Z-e93e41ab48e6f44a843f5375b08ccdf1` (SHA-256
`6f110519b1325941552254cc6285f608f670d9ab5241390893c3869c951b35b8`). Under one live lease:

- College Town: AllDrum remained selected while Drums owned native notes, drum discovery and
  fill scanning. Samples stayed separate. With the child temporarily armed and group muted,
  note 36 read back at velocity 100, then zero on release. Octave moved the child's bank
  36 → 52 → 36. Selecting Samples and returning while holding a pad kept drum output inactive
  until release; a subsequent new press played normally.
- `alien2_revival`: both trees reported two machines without overflow; the populated-note union
  and transmitted pad lights included Dry and Spread. Native notes 36 and 38 read back at
  velocity 100 and cleared on release. `fill_breakdown` became the host-confirmed active fill,
  then retired after release; its light returned from active orange to available brown.
- Temporary arm/mute and playback changes were discarded by reopening the saved project
  baseline. No device or routing edits were made. Diagnostics were disabled after testing.

Representative traces in `~/.drivenbymoss/pull/debug/`: group note
`1792633005-54049-3192`, held-selection recovery `1792633079-54708-24902`, alien2 note
`1792633137-55116-7233`, and fill `1792633164-55264-4817` / `1792633165-55329-2033`.

Scope: group fill **catalog targeting** was observed; College Town had no fill-named child clip
to launch. Actual fill launch/return was exercised in alien2. Bitwig-learned mappings require
physical MIDI and were not actuated by the synthetic surface test.

## Composite Drum discovery (API 59)

Offline regressions separate native cursor requests, applied host state and observer delivery.
They cover nested/parallel machines, same-name replacement, selection changes, selected-slot
aliases, layer removal with surviving identities, the eight-machine cap, finite structural
windows, populated-note union, one pressure stream, and independently acknowledged octave
windows. Bridge tests reject replaced/removed devices at application and keep submitted scrolls
separate from later pad-window read-back. Acquisition has a two-second monotonic retry deadline;
timeout permits retry but never acknowledges identity. A native comparison budget protects the
host resource cost in the nested/parallel behavior test. Idle and cancelled private cursors
are also checked against later external editor selections; this regression failed with the
previous lifecycle and passes with parking enabled. Reference-window regressions delay native
window and equality observations independently, retain descendant identities through pending
ancestor validation, and reject stale identity joins after layer reorder.

The stable merge candidate passed `mvn -o -Dmaven.compiler.showDeprecation=true package`:
**1,201 tests**, zero failures, errors, skips or deprecation warnings. It includes current master,
the unchanged `9179ffe2` discovery implementation, clearer coverage-warning text, and an explicit
empty-FX-slot capability regression. The cache is excluded. This final integration is validated
offline; it was not installed or restarted for the split.

The preceding matched `9179ffe2` build passed 1,200 tests and was installed and verified under
the shared live lease in Bitwig 6.1.4 / controller API 25 on October 19, 2026:

- Source checkpoint: `9179ffe24e42fa2d3a569ac61123721ee0b3b7c1`.
- Shell SHA-256: `f3963be2706fb7d8d9293c0a97187bee547baac667fe5caca7567097b38a030b`.
- API fingerprint: `73fb5f87e9578817828b65922655f22031f561e9`.
- Active core: `20261019T215239Z-cc2b9441191b8107127baef1660b288a`;
  SHA-256: `64d8f0c436e2e523a000a17e2adb99c0416a2b845f2031017640d900545facc5`.

`Pull Drum Tree Tests` is saved under Bitwig Projects/2026. Its `Eight parallel` track contains
seven DryDrum copies and one SpreadDrum under an Instrument Layer, saved disarmed at **−∞ dB**.
Later native host snapshots and transmitted output established:

| Latest installed build | Observed result |
| --- | --- |
| Eight parallel machines | Eight distinct machines, aligned windows and no coverage warning. |
| Shared note 36 | One routed pad press reaches all seven populated Dry machines at velocity 96; release clears all seven. |
| Unique note 38 | The populated Spread pad observes velocity 96 and later zero. |
| Octave up/down | All eight bases move 36 → 52 → 36. |
| alien2 Drums | Dry notes 36/50; Spread notes 38/39/40/42/44/46/48/49/51. Transmitted lights match the union and leave gaps dark. |
| alien2 notes and octaves | Populated pads for notes 36/38 observe velocity 96 and later zero; both windows move 36 → 52 → 36. |
| alien2 track selection | All twelve requested Tetra/Minilogue/Drums selections are visible in each paired timing run. |

All synthetic input was released. The fixture was restored disarmed at −∞ dB and closed;
alien2 was left on Drums at its original −0.5 dB, monitoring on and disarmed. Existing automatic
drum roll remains enabled, so held-note traces alternate note-on/off samples. Velocity checks
must distinguish populated pads from empty pads that also report the incoming note. Instruments
and audio routing were not edited during this verification. Pull was restored enabled with the
exact active core above, debug workers retired and framebuffer capture off.

### Track-selection performance

The initial `89bfbde5` shell reproduced multi-second whole-UI stalls. Main-thread stacks caught
`BitwigStudioHost.targetChanged` scanning the global native equality list during proxy rebinding.
Containing-chain equality plus local-device equality reduces 37,376 comparisons to 2,848 without
reducing discovery coverage. The `082a8064` fix removed that reproduced stall: first captured
selection frames were 783/808 ms, versus 802 ms disabled; the old enabled build still showed the
previous selection at 2,371 ms. These captures include tool overhead and do not resolve smaller
latency. The user subsequently reported residual lag.

Idle/cancelled cursors are parked and layer/pad presence banks contain only their consumed entry.
On Bitwig 6.1.4, profiling the preceding `b00a934d` build attributed 76–78% of extra main-thread
samples to the global equality scan. The reference-window build `9179ffe2` reduces permanent
registrations from **2,848 to 608**, without reducing tree coverage. In alien2, each run contained
three rounds of Drums → Tetra → Minilogue → Tetra → Drums, with diagnostics off:

| Build / paired run | Enabled CPU | Disabled CPU | Difference |
| --- | ---: | ---: | ---: |
| Prior `b00a934d`, Bitwig 6.1.4 | 4,082 ms | 2,741 ms | 1,341 ms |
| Reference windows A, Bitwig 6.1.4 | 3,399 ms | 2,603 ms | 795 ms |
| Reference windows B, Bitwig 6.1.4 | 3,244 ms | 2,707 ms | 537 ms |

All windows were 30 seconds; every input/observation pair fell inside its recorded window.
Screenshots verify all twelve selections per run. These are JVM main-thread CPU totals including
idle work between clicks, **not click-to-paint latency**. Before/after builds use separate JVM
processes; the new enabled-minus-disabled overhead averages 666 ms versus the prior 1,341 ms,
but this is not a precise user-visible percentage speedup. Residual selection overhead is accepted
for the initial merge; further optimization and coverage diagnostics remain
[separate work](docs/findings/drum-discovery-cost-and-coverage.md).

A new 30-second CPU profile at 1 ms sampling recorded 272 main-thread samples in
`BitwigStudioHost.targetChanged`, versus 1,411/1,455 in the preceding enabled profiles.
The enclosing `ControlSurfaceObjectProxy.setTarget` path had 403 samples versus 1,765/1,747.
Sampling perturbs execution; these counts establish reduced work in the targeted host path,
not exact unprofiled durations. The new paired profile had 3,411 main samples enabled versus
2,762 disabled; equality scanning accounts for 272 of the 649 extra samples (42%), with its
enclosing proxy-rebind path accounting for 403 (62%). The remaining 608 registrations include a matrix of
possible source parents and selected FX chains; they are not 608 devices found in alien2.

The current run contained no plug-in hang warnings or controller exception loop. It did contain
isolated slow bridge ticks at activation, plug-in preset/metadata warnings, and EOF exceptions
while project audio engines were being stopped. The original plug-in hang burst has not been
causally tied to the comparison matrix.

Latest evidence lives in `target/drum-tree-live/reference-windows-2026-10-19/`: package log,
`activation.log`, `reference-eight-*.tsv`, `reference-alien2-*.tsv`, paired surface JSON,
`{enabled,disabled}-{a,b}-{start,end}.txt`, timestamped timing JSON and selection screenshots,
CPU profiles and the restored-session log. Earlier Bitwig 6.1.4 measurements and profiles remain
in `target/drum-tree-live/latency-6.1.4-2026-10-19/`; Bitwig 6.1.3 lifecycle measurements remain
in `target/drum-tree-live/latency-2026-10-19/`. Use complete trace records: full snapshots can
hit the existing 2 MB trace limit; a truncated final record is not evidence.

Selected-layer aliasing, first-layer removal with surviving identities, and Undo were verified
on the previous installed checkpoint `082a8064`, with shell SHA-256
`0a188d99d8babdad310977a5776452f6b648a3fe59cc7a7ce1fc6d0c78d30982` and core
`20261019T160807Z-220d9de89d3cc4b9891dcb305636ac1f`. Evidence remains in
`target/drum-tree-live/regression-2026-10-19/`. Ninth-machine overflow, held-pad octave deferral,
an additional enclosing Instrument Layer and Cut/Paste reordering retain earlier `89bfbde5` live
coverage and offline regressions; they were not repeated on the latest shell.

These checks establish discovery, routed note read-back and output within the
[tree contract](docs/drum-device-tree.md), not audio-level summing or performance for every project
size. Forced observer delays, deeper structural overflow and pressure deduplication remain
offline coverage. Unselected FX slots are outside API 25's selected-slot window.

## Retained parameter cutover (API 58)

The old layer providers, NoteParameter/timer and ACTIVE compatibility path are removed. Permanent
regressions run real core page compositions with independently advanced host samples: named sends
and layers, Crossfade callback order/clamping, Groove modifiers/reset, note page roles and spacers,
individual multi-note Snapback baselines, delayed readback, and held-tail rejection on owner/page
changes. Shell tests separate note clip acquisition and property delivery from writes/readback;
selection/grid changes and delete/Undo cannot revive captured targets. Candidate bank preparation
must preserve committed target identities.

The matched API 58 shell/core passed the routed sends, Device/Chains/layers, Note and
Groove/Crossfade smoke on September 19, 2026. Later host readback and captured Push output verify
normal edits, resets, distinct multi-note gain/velocity restoration and held-page cancellation.
The [acceptance record](docs/track-cursor-pool/README.md#api-58-parameter-cutover-acceptance) records
exact build identities, the 1,170-test package gate, trace references and the limits of live coverage.

## Retained note-copy regression

The legacy sequencer Duplicate gesture uses up to four private track/clip cursor pairs per editor
shape, each retaining one window with up to 128 pending notes for chord/pattern copies. Each copy
freezes the destination, page, resolution and expression values. It waits for observed pin/target
alignment, then a fresh note observation before expression writes; matching later values complete
the copy. Native grid setup is submitted before note creation; that order is not a geometry
acknowledgement. The fake applies queued geometry and note creation separately from delivering
note observations. Selection changes before capture cancel; changes after capture cannot redirect
it. Lost targets, observed note deletion and a 150-poll host deadline
(20 ms requested per poll, excluding diagnostic holds) retire the operation. Appending notes does
not extend that deadline. A full pool/window refuses additional copies. Bitwig exposes no stable
note ID, so an unobserved delete/recreate of the same cell cannot be distinguished from editing it.
Live API 25 gain reads use twice the setter scale: copy writes use the normalized framework value,
while completion compares against twice that value. These eager private proxies require a shell
installation and Bitwig restart.

In a scratch Launcher project, use real routed Duplicate-plus-pad input on a source note and an
empty destination. With debugging enabled, `tools/push-debug-selection hold-note-copies 30` holds
only the expression phase; wait for `COPY_CREATED`, select another clip or track, then run
`tools/push-debug-selection run 30` to release it while retaining the trace. Check later
`NOTE_OBSERVED role=copy-complete` raw values in `selection-trace.tsv`, reselect both clips and
verify the destination and unrelated clip independently. Repeat without the hold and with the
captured clip deleted during the hold. `stop` or the diagnostic deadline also releases the hold.
The hold is off by default, expires within 60 seconds and does not consume the normal copy deadline.

Live validation on 2026-09-10, Bitwig 6.1.1 / controller API 25: production commit `bc7d67c4`,
installed extension SHA-256 `866242a77d2d95b8eb027a1ecab987e8c86cc9b9b69443aa7fb1d91008c8f8db`,
active core `20260910T213932Z-9f3a7600d905eea8cbc4b0207b012f13`. The full deprecation-enabled
package gate passed 1,086 tests, including 12 asynchronous copy regressions. In the standalone
“Note Copy Smoke” project, routed Duplicate-plus-pad copied seven expression attributes with
matching later raw host read-back; the ordinary copy completed in 190 ms. Held copies completed
on their captured clip after switching Launcher clips and after selecting another track. The
same-position note in the other clip retained its original attributes, confirmed by later copy
read-back and the Push display. Deleting the captured clip produced an Empty observation and
cancelled before expression submission; Undo restored the basic note without a late expression
write. Local traces, inspected Push frames and the scratch project are archived under
`.codex-worktrees/acknowledgement-evidence-2026-09-12/note-copy-target/target/`.
This live evidence applies only to note copying.

On 2026-09-12 the standalone note-copy package gate passed 1,086 tests; stacked on the
navigation/transport changes it passed 1,105 tests. Both full deprecation-enabled builds included
the queued-geometry regression and had no failures, errors, skips or deprecation warnings.
The production note-copy implementation is unchanged from the prior live validation above;
the combined-build smoke below also verifies an ordinary copy; the prior navigation/deletion
stress evidence remains separately scoped.

## Host operation acknowledgements

Capability audit: **B — bounded shell expansion**, Core API 56 and Bitwig API 25 unchanged.
Existing commands retain their meanings; shell changes execute and validate those commands using
interested native values. A matching extension install/restart is required. The active migration
findings remain unresolved; this work does not migrate legacy navigation into core.

| Operation | Completion boundary |
| --- | --- |
| Sibling-bank page/select | Requested bank offset and every existing row's parent-local position match before selecting the destination row. Project, parent UUID, selected cursor UUID and item count must remain valid. |
| Group entry | Native cursor UUID matches the requested group before selecting its first child. One owner serializes selection submissions sharing that cursor; intermediate read-back cannot execute obsolete entries. |
| Stop/rewind | Later subscribed stopped state permits the zero-position write; later position read-back retires it. New transport/seek requests and project changes cancel the continuation. |
| Master engine/remote playback | Later engine/playback read-back resolves the command. A two-second warning retains the unresolved lane and remote target instead of treating elapsed ticks as success. |

Bank/group/rewind share a condition-and-continuation helper with bounded 150-poll cancellation
deadlines and 20 ms requested between samples. A deadline never authorizes the next operation.
Group and bank replacements retain one submitted operation and one latest intent without extending
the original deadline. Ordinary and queued relative paging share the same bounded destination
calculation, preserving the current window alignment and saturating at the final full or partial page. The helper owns scheduling and cancellation; each caller proves its own
target and completion conditions. Master retains its existing controller-tick observation loop.
Model cleanup cancels owners; no post-exit scheduling is promised.

Retained unchanged: browser insertion waits (open/closed is not correlated with an opening still in
flight), Add Track/device insertion and native Duplicate (no returned created-object identity),
device and flattened/filtered-bank paging (no proven offset-to-target identity mapping), generic
selection notifications. Musical timing, double-click/long-press windows, periodic flushes,
throttles and animation remain timers. API 58 removes the held-note expression timer entirely.

Offline regressions separate command submission, host advancement and subscribed observations.
They cover delayed/intermediate bank pages, rapid replacement/reversal, group supersession,
structural guards, shutdown, delayed rewind and Master acknowledgements beyond old deadlines.
On 2026-09-12 the full deprecation-enabled package gate passed 1,091 tests with no failures,
errors, skips or deprecation warnings. Rewind regressions also cover replacement phase reset
and one deadline spanning both stop and position acknowledgement. Paging regressions cover excess
Next presses, reversal after saturation and a window starting between global page boundaries.

Earlier scoped live validation on 2026-09-10 used Bitwig 6.1.1 / API 25, checkpoint `4566eb79`,
installed shell SHA-256 `6fb3b264471b3a43913146fd2a1dfdb8adab58d91e26dc97893d7e8ced2186b4`,
and active core `20260910T221931Z-1a529f49fa74a0b56f652612d435d628`
(SHA-256 `6f3c236566973525b72de1d5206dc71e63686447e8392908ffd60753e870e57e`).
In **Host Ack Smoke**, routed Shift+arrow paging selected positions 0 and 8 with matching track
UUIDs and Push output, including a nine-track partial last page and opposite page presses. Group
entry selected its first child; paging also selected position 8 inside a nine-child group. Master
engine off/on resolved from read-back. Remote Play/Stop observed playback before returning to the
original project and releasing the command lane. Traces, frames and provenance are archived under
`.codex-worktrees/acknowledgement-evidence-2026-09-12/host-acknowledgements/target/host-ack-evidence/`.
This predates the shared-helper
refactor. The combined-build smoke below covers the corrected paging and routed footswitch
rewind; group/Master scenarios were not repeated. Forced host delays are verified offline only.

## Earlier held-note edit target safety

The September 12 tests below characterized the previous 100 ms held-note editor and its
project/track/scene/grid fences. API 58 deletes that implementation and its timer; the retained
per-cell tests and acceptance record above describe the replacement. The earlier live evidence
remains useful history, but is not evidence for the current implementation.

## Combined acknowledgement smoke — 2026-09-12

Bitwig 6.1.1 / API 25, clean checkpoint `0ed3c03304d5ae0ef72ab49ec9c3550504d77392`,
installed shell SHA-256 `63cdfb0eb28400d13ee2c1df9e5f1e85efc62f61d7e4f5911cc5bfe9d3392098`,
active core `20260912T181358Z-2c04a349f50298478d67be795c401ba3`
(SHA-256 `e6825b8008a3de50c9faab77b3d6368fdc97dbefc6652870a3a3af9f3fb12721`).
The isolated **Host Ack Smoke** project verified:

- Routed paging with nine and twelve tracks: excess Next retained position 8 and Previous returned
  position 0, with matching UUIDs and Push output.
- Footswitch double-click rewind: later stopped state at a nonzero position preceded later zero
  position. Coalesced telemetry does not establish exact latency.
- A held velocity edit changed S1 from 55.4% to 35.9%. Native editor navigation to S2 cancelled
  further held motion; S2 stayed at its independently observed 61.9% during the hold and after
  release/reselection. S1 retained 35.9%. A fresh gesture on S2 then reached 69.7%.
- Ordinary Duplicate-plus-pad copied C1 to an empty D1 cell, with all seven requested expression
  values confirmed by later native read-back in 185 ms and the independently selected D1 display.

Local traces, inspected Push frames, input scripts and provenance are archived under
`.codex-worktrees/acknowledgement-evidence-2026-09-12/held-note-edit-targets/target/final-smoke-evidence/`.
Paging endpoint statuses supplement its truncated trace;
legacy held input is evidenced by the script/log, while traces and displays establish host results.
Native note callbacks can precede scene-index callbacks during retargeting, so those printed scene
labels are paired with actual editor selection and active Note-page values. Group/Master and held/
deleted-copy stress cases retain their earlier scoped evidence. The prior installed shell/core and
Footswitch 2 setting were restored after testing.

## Offline UI catalog

Run `tools/ui-component-catalog` to generate a local HTML gallery from production components and
page renderers. The command prints the output path under `pull-core/target/ui-component-catalog`;
open that HTML to inspect individual Components and complete Views, including normal, selected,
touched, unavailable and long-text fixtures with their actual row lights. Lato is loaded from the
installed Bitwig resources or `PULL_UI_FONT_DIR`; neither Bitwig nor the Push debugger needs to run. See the
[component library](docs/ui-component-library.md) for component contracts and preview limitations.
Use the shared Components color picker or hex field to compare states in arbitrary RGB colors;
Reset restores the default. Complete view fixtures keep their supplied colors.
Use existing routed behavior tests for submitted effects, later observed state and feedback; a
catalog fixture is visual evidence, not host/hardware or gesture validation.

Info's initial source validation passed 855 tests and parsed 28 catalog SVGs. The subsequent
[API 49 smoke](docs/migrations/session-core-live-smoke.md) verified observed hardware identity,
its transmitted display and Info/Setup navigation in the integrated build. Deferred row/tab actions,
stale-target cancellation and unavailable fixtures retain offline coverage. A retained hardware tuple
cannot prove that Push is still connected; disconnects without a new identity response remain unobservable.

## Live Push display loop

For startup light-refresh changes, verify button/pad output before the separate ten-second macOS
resend, then check that shutdown leaves the lights off. Offline tests cannot prove physical replay.

Bitwig, its loaded Pull shell/core, and the physical Push form one shared live environment. Acquire
its machine-wide lease before installing, reloading, restarting, or driving debug input, and keep
the lease through the complete smoke test:

```bash
tools/with-pull-live --owner my-feature
# install/reload, drive the test, and verify authoritative read-back
exit
```

Acquisition fails immediately when another agent owns the environment. Continue offline work and
retry when the current owner finishes. The wrapper is the sole OS-lock owner; commands and detached
descendants cannot keep the lock alive after their supervising wrapper exits, and a per-acquisition
token prevents those descendants from retaining live authorization. The OS releases the lease if
the wrapper exits or crashes. `tools/capture-push2-display --stats` remains available without the
lease because it is read-only.

The debugger is off by default. After installing the runtime-toggle shell and restarting Bitwig
once, use the controller's **Diagnostics → Debugging → Enable** button to start it at runtime.
Alternatively, enable it for the next extension startup:

```bash
tools/capture-push2-display --enable
```

Before ordinary playback, apply the performance preset:

```bash
tools/pull-performance
```

The command acquires the live lease automatically (or reuses the current lease), sets framebuffer
capture to `0`, removes the startup-enable marker, and waits for the running debugger to retire.
The controller's **Diagnostics → Performance mode → Disable** button also stops the debugger.
Disabling detaches input, trace, surface and display hooks, releases synthetic input and diagnostic
holds, and stops all six workers. The `OFF` receipt is published only after workers terminate and
released input drains. `tools/pull-debug-status` checks that receipt against its process ID;
it is not a build-identity attestation. Failure prevents re-enabling until extension restart.

These transitions use native settings callbacks and short-lived lifecycle work. Disabled mode has
no filesystem polling worker, lifecycle timer or new controller-tick branch. An already-enabled
worker notices CLI disable; CLI enable only selects the next startup because no disabled worker
remains to watch files. Shutdown allows two seconds for workers plus up to 100 input-drain callbacks
requested 20 ms apart. Extension exit is best-effort and never schedules post-exit host callbacks.

The preset works with Bitwig closed and is safe to repeat. Older shells still require a restart;
the client stops their reported event/selection traces and warns that runtime shutdown is unconfirmed.
Failed shutdown returns nonzero without undoing the preset. The command never restarts Bitwig.
To resume debugging, use the native Enable button and explicitly restore capture with
`tools/capture-push2-display --sample-rate 1` inside a live lease if images are needed.

Live capture-off requires the shell version that accepts sample rate `0`; older installed shells
ignore that value. The startup-disable marker works with either version. With the updated shell,
zero skips framebuffer copies and PNG writes, and fails explicit/pending capture requests instead
of returning cached images. Existing PNG artifacts remain on disk; they are not live status.

### Push debugger surface preview

Run the dependency-free local Push 2 visualizer with:

```bash
tools/push-debug-surface
```

It opens a local SVG surface following the [official Push 2 control overview](https://ableton-production.imgix.net/live-manual/12/Push2Overview.png).
Keep proportions, printed legends, glyphs and illumination placement consistent with that reference.
The eight printed beat divisions retain their `SCENE1`–`SCENE8` addresses; Octave/Page legends retain
their existing directional addresses. Printed labels do not introduce controller behavior. Font,
pad diffusion and the touch-strip dot marker are browser approximations of physical materials.
All 64 pads, physical buttons, continuous controls, and the display have the same canonical
`push.*` identifiers used by the input bridge. The local server polls the opt-in debugger's bounded
`surface-state.json`: every successful button-light send and complete successful pad-light send is
shown with its resolved Push palette RGB, pad blink color, and blink rate. Debugger-generated
button and pad edges pulse even when their DOWN/UP pair completes inside one controller tick;
longer physical holds remain visibly pressed. The existing `latest.png` stream fills the display.

When the page reports `input ready`, clicking a button or pad submits its DOWN/UP pair through the
same permanent hardware object and input arbitrator as Push. A pad also submits the matching raw
note-on/off packet through the permanent Push `NoteInput`; the active translation table and
selected-track route therefore decide whether it becomes a musical note just as they do for the
hardware. Holding the mouse retains the normal long-press lifecycle. Hovering any touch-bound
continuous control submits its touch BEGIN/END, and the deliberately plain slider below the
controller submits 0..127 poly-pressure for the last pad clicked through both the controller and
`NoteInput` paths. Up to eight browser edges may share one debug-input session, so independent
`/api/input` BEGIN requests can hold modifiers and buttons concurrently before matching END
requests release them. Pressure may accompany an exact held pad. Each edge has a five-second
controller-owned lease, renewed independently by the page; disappearing clients release their
controls and neutralize detached nonzero pressure. Dragging the touch strip sends `TOUCH` BEGIN,
coalesced `ABSOLUTE` CHANGE values from 0 through 16383, then the last value before `TOUCH` END.
The strip position shown in the browser comes from `surface-state.json.touchStrip`, recorded only
after successful hardware mode and position transmission; it is not a local pointer preview.
The local server accepts bounded same-origin JSON only with the active random extension-session
token, atomically queues at most 64 requests, and never invokes controller code itself.
Debugger output reaches the browser through a bounded Server-Sent Events stream. The event carries
only a change notification; state fetches and PNG decoding coalesce to the newest revision, so a
slow browser skips intermediate frames instead of accumulating display latency. A one-second poll
remains only as recovery if the event stream reconnects.

`file://` remains a static preview because it has no local process bridge. **Demo lights** exercises
RGB rendering without Bitwig. Set `PUSH_DEBUG_SURFACE_NO_OPEN=true` to run the server without opening
a browser, or `PUSH_DEBUG_SURFACE_PORT` to select another local port.

#### Agent handoff: build, bring up, and prove the browser path

Do not use a different worktree for any step in this sequence. First verify the checkout and run the
complete offline gate:

```bash
git rev-parse --show-toplevel
git status --short
python3 -m unittest tools/test_push_debug_surface_server.py
mvn -o -Dmaven.compiler.showDeprecation=true package
```

The remaining steps take ownership of the installed extension and running reloadable core. Coordinate
with anyone using Bitwig before doing them. With Bitwig closed, enable debugging, copy this exact
worktree's `target/Pull.bwextension` to Bitwig's extension directory as described in `README.md`, and
start Bitwig once:

```bash
tools/capture-push2-display --enable
```

If another worktree may have published a core after Bitwig started, run this worktree's
`tools/reload-core --timeout-ms 20000` before testing. Then start the surface from this worktree:

```bash
tools/push-debug-surface
```

Use the exact printed `http://127.0.0.1:<port>/` URL; the server rejects other Host and Origin values.

##### Hard boundary: Bitwig-learned mappings are physical-only

The browser surface is not a virtual MIDI source. Physical controller MIDI enters Bitwig's
`MidiIn`, where Bitwig can fire a learned `HardwareAction`, and also reaches Pull's raw-input
arbitrator. Browser input enters at `PushDebugInputHost`, after the `MidiIn` matcher, so it reaches
the Pull arbitrator but cannot fire the parallel learned action. Controller API 25 exposes neither
a controller-input injection method nor a way to invoke a `HardwareAction` source.

This specifically means browser presses on the four Drum Controller mapping pads (PAD29–32) can
reach `APPLIED`, produce core `BEGIN`/`END` events, and send musical `NoteInput`, while the action a
user manually mapped in Bitwig still does not run. Their red/off lights are authoritative Bitwig
read-back, not proof that browser actuation is available. Do not add a stable semantic fallback or
describe `APPLIED` as physical-controller equivalence. Test the learned mapping with the physical
Push, or inject through an external virtual-MIDI path before Bitwig's `MidiIn`.

Wait for `input ready`, then validate the supported layers separately:

- Hold and vertically drag encoder 1. The terminal input status must
  reach `APPLIED`, and a later `surface-state.json` event must report that encoder as `RELATIVE`,
  `UPDATE`, and the submitted signed delta.
- On a selected, armed, note-capable Drum Machine track, press and release the bottom-left pad. The
  terminal input status must reach `APPLIED` for both edges, and later surface events must contain the
  matching PAD `BEGIN` and `END`. Hearing the note is the required live evidence that Bitwig accepted
  the separate `NoteInput` packet; the installed canopy has no authoritative note-held read-back.
- Confirm that Bitwig-driven pad and button colors appear on the matching browser controls. A queued
  HTTP response proves only local ingress, and an `APPLIED` status proves only controller routing;
  neither by itself proves audible note delivery or later controller output.
- In Session or an engaged Drum layout, drag the strip through several positions and release.
  Check later `touchStrip` output for `PITCH_BEND` and the exact 14-bit position, then center 8192.
  With a sounding instrument, verify audible pitch and release centering independently. Hold the
  strip through a page replacement retaining the same raw-strip view: the bend must remain held.
  Leave that view: verify immediate centering and an inert physical tail, including after returning.
  Only a fresh touch may bend again. A raw `ABSOLUTE` request without an
  exact active browser TOUCH lease must fail. The generic `/api/input` endpoint can submit these
  values for repeatable checks; it retains the same live lease and session-token requirements.

Ordinary target/view changes neutralize debugger-injected native MIDI without synthesizing physical
releases. Keep renewing the original edge, then send its real END to test cancellation faithfully.
Core invalidation/replacement, expiry, and shutdown still retire debugger-owned physical leases.

The bounded files below make those distinctions inspectable without browser developer tools:

```bash
jq . ~/.drivenbymoss/pull/debug/surface-input-info.json
jq . ~/.drivenbymoss/pull/debug/surface-input-status.json
jq '{connected, latestEvent: .events[-1]}' ~/.drivenbymoss/pull/debug/surface-state.json
```

Disable the debugger with `tools/capture-push2-display --disable` when the next operator no longer
needs it; the runtime-toggle shell reports completion after its workers retire.

The live mirror is attached only while debugging is enabled. Installing this shell change and
restarting Bitwig once adds runtime lifecycle support; later core reloads reuse it.
The controller thread only copies the fixed Push footprint into one coalescing slot. JSON encoding,
filesystem writes, HTTP serving, and browser polling stay off the controller thread. Continuous
touch-strip position is part of the held browser drag path. Encoders turn only during a held
vertical pointer drag; their signed deltas run through the permanent continuous-control input arbitrator.
Browser pad presses exercise the extension-side permanent controller binding and Bitwig's
`NoteInput`, subject to the learned-mapping boundary above.

An agent can then select a bounded Push surface and capture the resulting Push 2 framebuffer in one
command:

```bash
tools/capture-push2-display mix
tools/capture-push2-display master
tools/capture-push2-display project-macros
tools/capture-push2-display session
```

`mix` selects the Track parameter page and retains the current grid and musical-input background;
`master` does the same for the Master page. `session` selects the full Session grid with the Track
page, while `project-macros` selects the declared Workspace grid and Project Macro page. The
navigation status field `workspace` reports whether any core-owned composition is active. It is
not a screen identifier: a Drum grid with the Track page can legitimately report `workspace=true`.
Recipes therefore verify their exact page/grid targets instead of using that flag to select a
screen.

Calling the tool without a target captures the current display. A targeted capture injects the
same permanent button gestures as the hardware, through the installed input arbitrator. The tool
waits until the input router and relevant physical controls are idle, submits each gesture once,
then waits for the stable shell to observe the target view and mode on two later controller ticks,
and finally arms a request for the next outbound framebuffer. That request bypasses passive frame
sampling, so even a heavily throttled debug stream cannot return a cached pre-navigation image or
time out waiting for the sampling interval.

The stable shell accepts only a bounded generic plan of explicitly admitted gestures and
authoritative controller-state predicates. Play and the two Master project-navigation buttons may
also use a `submitted` postcondition for live regression tests; this presses the real routed
hardware command exactly once and reports completion only after its input lifecycle is idle. Recording, deletion,
and project-file actions remain unavailable. Named recipes live in the command-line client, so
they can change without rebuilding the extension. Filesystem polling and PNG encoding run on owned
workers rather than the controller thread.

While debugging is enabled, the Push display transport atomically publishes its newest sampled
frame to `latest.png` and its metrics to `latest-frame.txt`. The default rate samples every
outbound frame. Intermediate samples are coalesced into one bounded writer slot if PNG encoding
cannot keep up; the metrics expose that count along with controller-thread framebuffer-copy time
and worker PNG time. Inspect or change the live rate without rebuilding:

```bash
tools/capture-push2-display --stats
tools/capture-push2-display --sample-rate 10
```

Exact pixel equality reuses immutable debug snapshots and their worker-encoded PNG. Identical
sampled frames still advance the observation revision and satisfy a pending `NEXT_FRAME` request
only after another outbound bitmap is observed. They do not rewrite `latest.png`; PNG-write time
is zero for that reuse. The ninth status field is a shell-epoch/content generation, separate from
the first observation revision. The local debugger server forwards both; the browser fetches and
decodes only changed content and retries failed fetches on a later observation. Older eight-field
shell status remains supported. Changed pixels, alpha or dimensions invalidate the snapshot, and
queued captures retain their observed bytes even if the host overwrites its bitmap.

The setting is read from `frame-sample-rate.txt`: `0` disables all framebuffer capture, and `1`
through `600` samples one per that many outbound frames. Deleting that file restores the
every-frame default. An already-running PNG write may finish while the worker observes `0`;
subsequent frames do not copy or encode. Capture-off leaves the debug command workers running.

The resulting request-correlated image path is printed on standard output; navigation state is
reported on standard error. The fixed local handshake directory is
`~/.drivenbymoss/pull/debug`. One client owns an atomic lock for the complete navigate-and-capture
transaction, and every request-correlated PNG is named for that request so another capture cannot
satisfy or overwrite it. The `enabled` marker chooses startup behavior; the native Diagnostics
buttons toggle the running transports. Run `tools/capture-push2-display --disable` to stop them.

For navigation without a framebuffer capture, use the generic client; named recipes stay
client-side:

```bash
tools/push-debug-request session \
    'SESSION/view=SESSION,mode=TRACK'
```

The navigation host's legacy Track guard still requires `workspace=false` for its eight ROW1
shortcuts. Those shortcuts reject migrated Track compositions; use the generic surface HTTP input
lane for these buttons until that bounded harness guard is updated. This does not prevent the
TRACK gesture or the named capture recipes. Every terminal
status reports the private selection-following target's `track_position`, stable `track_id`,
identity `track_generation`, `armed`, `muted`, `soloed`, `clip_playing`, and `monitor` state. It also reports authoritative
`repeat` and `latch` state plus parent-owned Note-route command state. Bitwig track positions are
local to the immediate parent group, so a
`track=N` predicate is accepted only alongside the exact `track-id=ID` fence. Position remains
useful context; it is never global proof. A client can first run a harmless already-satisfied plan
to discover the currently selected identity:

```bash
tools/push-debug-request identify 'TRACK/mode=TRACK'
```

Mute, Solo, and Stop Clip are admitted through their permanent routed buttons. Use `muted`,
`soloed`, and `clip-playing` predicates so completion comes from later selected-track host read-back,
not command submission. A Stop request with `clip-playing=false` is intentionally already satisfied
when no selected-track launcher clip is playing and therefore does not press the button.

A `repeat=true|false` postcondition waits for authoritative read-back from the permanent Push
NoteInput repeat engine. With the project-specific identities discovered from terminal statuses,
the following legacy navigation plan describes a Juno-to-Drum-Machine viewer transition without
leaving Note mode. Its ROW1 shortcuts currently require the guard correction described above;
for migrated Track pages, drive those buttons through the surface HTTP lane and verify the same
later selected-track identity and repeat state:

```bash
JUNO_ID='<juno-track-id>'
DRUM_ID='<top-level-drum-track-id>'
tools/push-debug-request juno-to-drums \
    'TRACK/mode=TRACK,repeat=false' \
    "ROW1_6/track=5,track-id=${JUNO_ID},repeat=false" \
    "NOTE/view=PLAY,track=5,track-id=${JUNO_ID},repeat=false" \
    "ROW1_1/view=DRUM_PAD,track=0,track-id=${DRUM_ID},repeat=true"
```

Layout and Shift + Layout use the same permanent routed bindings as the hardware. This checks both
preference cycles without calling the legacy view manager directly:

```bash
tools/push-debug-request note-layout \
    'NOTE/view=PLAY' \
    'LAYOUT/view=CHORDS' \
    'SHIFT_LAYOUT/view=SEQUENCER'
```

### Routed pad-output proof

The navigation lane can also hold one physical Push pad through the permanent input arbitrator and
close the feedback loop through core intent, stable light resolution, and the final MIDI output:

```bash
tools/push-debug-request drum-rate-pad \
    "PAD_OUTPUT_29_100/view=DRUM_PAD,mode=TRACK,workspace=false,track-id=${DRUM_ID},repeat=true"
```

`PAD_OUTPUT_<one-based-pad>_<velocity>` accepts pads 1 through 64 and velocities 1 through 127.
It requires one exact `view`, one exact `mode`, and an explicit `workspace=true|false` predicate.
The exact pad must also be present in the installed physical registry and have an active
core-owned `EXCLUSIVE` PAD route and a physical-to-semantic `DesiredControllerMappings` binding.
Absent and `OBSERVE` routes or an absent binding fail without emitting DOWN. Because stable matcher
activation can lag the desired lease by one controller tick, the probe waits until the permanent
semantic Bitwig action is actually accepting that physical note-on before emitting DOWN.
`mapping_active=false` while ordinary raw dispatch owns the pad and throughout either release-only
lane transition. The probe also waits for the subscribed authoritative
`ControllerMappingFeedbackSnapshot` sample to become available. This is a generic debug mechanism,
not pad policy: the production core/shell slice still owns which pads are routed, which semantic
endpoint is projected onto each pad, and which physical light is owned.

After DOWN, the probe requires a later successfully applied complete core result with an explicit
RGB entry for that pad. It resolves that RGB through the Push palette, waits for two matching stable
samples of the resolved `LightInfo`, and requires a successful outbound base-color transmission
(plus the matching blink transmission when blink is active). Terminal status reports `pad_probe`,
`pad_button`, normalized `pad_control`, physical `pad_midi_note`, `pad_velocity`, `pad_route`, core
`mapping_desired`, stable-host `mapping_active`, committed semantic `mapping_id`, and authoritative
`mapped_has_target` and
`mapped_value`, `desired_rgb`, `resolved_light`, and `transmitted_light`. The mapped fields come
only from the subscribed API-44 snapshot, keyed by the semantic endpoint in the committed binding.
`mapping_id` identifies that committed endpoint (for example `drum-controller.track.2.control.1`),
or `-` when no lease is committed; it does not identify or validate Bitwig's learned target.
`mapped_has_target` reports Bitwig's mapped-target presence and `mapped_value` retains its raw
normalized value, including fractional values. An observed absent target reports `false` while
retaining the independently observed raw value; unavailable or unsupported endpoint read-back
reports `-` in both fields. Neither field derives
from desired or transmitted RGB. The debugger applies no on/off threshold: the core alone
interprets the authoritative value for its next endpoint and LED policy. The separate route, desired
binding, applied semantic-matcher activation, mapped read-back, and output fields distinguish an
inactive view or lane transition from a feedback/render/transmission failure. `mapping_active`
likewise does not claim that a manual Bitwig mapping exists, fired, or changed host state. The
transmission tap observes only the one armed pad and records only after the existing MIDI send
returns successfully; it adds no MIDI callback or output owner.

If the later applied result leaves an already-correct pad color unchanged, the ordinary renderer
may suppress a redundant send. The first post-apply debug observation therefore resends that one
pad exactly once through the existing output path. This is a real opt-in outbound update; later
observations never resend.

The debugger then submits UP through the same permanent arbitrator. It retains its input/core
generation fence until the routed gesture is idle and a later complete core result has applied.
Context, route, or generation changes, timeout, quarantine, close, and output failures all run the
same bounded best-effort UP cleanup before releasing the debugger lifecycle. This proves the
controller path through the transmitted palette state; it does not opt the pad into routing,
choose its RGB policy, or repair a missing mapped-feedback subscription or observer.

The DOWN/UP pair manually enters the original physical PAD button's permanent extension
trigger/arbitrator seam. Those physical buttons have no MIDI matchers and are not learned mapping
identities. Controller API 25 does not expose a way to inject a raw controller MIDI packet back
through Bitwig's separate semantic hardware-action matcher, so the probe reports whether that
virtual action is admitting the physical note-on but does not itself fire the Bitwig-learned action.
A live physical press is still required to prove that Bitwig learned the semantic identity, changes
the target, and returns fresh mapped-light feedback; the probe closes the extension-side route,
semantic lease, RGB, palette-resolution, and transmission loop around that authoritative feedback.

### Track-scoped native mapping V1 smoke

API 44 changes the parent-loaded contract and creates 128 permanent banks of four endpoints. Build
with `mvn -o -Dmaven.compiler.showDeprecation=true package`, create a recoverable Git checkpoint,
and hold `tools/with-pull-live --owner LABEL` through installation, restart, exact-build activation,
and this smoke. The earlier API 43 identity probe does not validate the new registry or mappings.
Record final-build test results separately; this sequence is an acceptance procedure, not a result.
The source labels are fixed `Drum Controller N` names (1–512): bank 1 uses 1–4, bank 2 uses 5–8,
and so on. Numbers identify permanent controls, not track positions. Runtime track-name labels
are deferred: the installed host rejects `setName`
and `setLabel` outside initialization, even though the API 25 declarations omit that restriction.

1. Use a saved scratch project with two drum tracks. Select the first and wait for its mapping
   context and registry write to be read back. Record the pad probe's `mapping_id` and selected-track
   UUID. Physically learn one pad to a target on that track.
   Verify off → on → off through later host value and red/off pad output; release must not write.
2. Select the second track and wait for acknowledged context. Learn the same physical pad to a
   different target. Switch between tracks and verify each bank retains its own binding and only
   the active bank responds. Include a continuous target: set values below and above `0.5` from
   Bitwig and confirm later raw `mapped_value`, LED state, and the opposite next endpoint.
3. Reorder and rename a track, then move it into/out of a group. After each operation, verify its
   allocation and learned target remain associated with its UUID. Delete it, undo, and repeat the
   physical toggle. Duplicate a learned track and record both its fresh allocation and Bitwig's
   native binding-copy behavior; a fresh bank alone does not prove copied bindings are harmless.
4. Save, close, and reopen the scratch project; verify both allocations and learned actions.
   Exercise another project tab and a same-name copy without assuming the names identify documents.
   Record context propagation latency; native learned actions have no instantaneous selection fence.
5. Confirm the old shared `Drum Controller Toggle` mappings are inert after this install. Delete
   those entries and relearn against the new selected-track bank; existing shared targets cannot
   be migrated automatically. Leave Drum Controller and verify its endpoints stop accepting input.
6. Reload core, including once with a pad held, and verify leases retire/reactivate safely and
   registry read-back, rather than checkpointed toggle phase, determines the next endpoint.

Offline tests must separate write submission from later storage acknowledgement; cover corrupt or
foreign-document payloads, duplicate track IDs, delayed or stale writes, selection/document/storage
revision fences, tombstone retention, and the 128-bank limit. Corrupt or exhausted allocation must
emit amber pad output and no active mapping. Also cover unchanged replay without redundant writes,
raw fractional feedback, ignored release, and inert legacy endpoints. Do not corrupt a user's
project or allocate 128 live tracks merely to exercise deterministic validation branches.

General native mapping ownership, duplication, clearing/recycling, stronger document identity, and
DocumentState undo/atomicity remain tracked in
[`track-scoped-midi-learn-lifecycle.md`](docs/findings/track-scoped-midi-learn-lifecycle.md).

For an explicitly state-changing playback test, use the permanent routed Play binding and verify
its result from later authoritative framebuffer and host observations:

```bash
tools/push-debug-request play 'PLAY/submitted'
```

Only Play and Master row buttons 5/7/8 (Audio Engine, Previous, and Next) currently admit this
one-shot form. When another feature needs ingress or read-back, follow the close-the-loop policy
above: extend the bounded debug seam instead of adding a stable-shell semantic shortcut.

### Request-scoped runtime traces

Arm a bounded trace before reproducing an interaction, then stop it to print the artifact path:

```bash
trace_id="$(tools/push-debug-trace start button-test 15)"
tools/push-debug-request play 'PLAY/submitted'
tools/push-debug-trace stop "$trace_id"
```

The optional duration is 1 through 60 seconds. A trace records the exact core event, authoritative
snapshot, returned result, successful complete stable-result application, and sparse
activation/failure lifecycle around each retained transaction. It does not sample semantic input,
effects, or changed replayable output. A controller tick or snapshot-change event is telemetry only
when it has no effects and its complete result is unchanged from the preceding retained result;
only the newest completed transaction/application pair between meaningful transactions is retained
as `TRANSACTION_APPLIED`, and the number coalesced is reported in the TSV header.

Capture stops at 256 retained entries, 60 seconds, or two million serialized characters. The
transport retains at most 16 trace files. The controller thread only appends references to bounded
memory; structural object rendering, filesystem polling, serialization, and pruning run on the
trace worker. Structural rendering writes into the character bound directly instead of first
allocating an unbounded object string.
Automatic timeout still leaves the last trace retrievable with `stop`. A successful `APPLIED` row
means the complete stable result applied without throwing; completion of an asynchronous Bitwig
transition still requires a later authoritative snapshot or output observation.

Adding or expanding the generic debug bridge is a stable-shell change and needs one extension
install and Bitwig restart. Frame capture and client-side navigation recipes can then be reused
across core hot reloads.

## Arrange scrolling diagnostic

Enable the opt-in Push debugger and hold the live lease. Use
`tools/push-debug-selection run 30` to trace selection and scanner activity, or
`tools/push-debug-selection pause 30` to suspend scanner selection and paging while tracing.
Intervals last 1–60 seconds; a new request replaces the interval, expiry resumes scanning,
and startup ignores stale requests. No core reload is needed.

Pause preserves target invalidation and acquired-fill observation, release and retirement.
The catalog freezes while paused; avoid starting new fills during the comparison.

`~/.drivenbymoss/pull/debug/selection-status.txt` acknowledges the request ID and reports
mode and dropped entries. `selection-trace.tsv` distinguishes selection submissions, page
requests and later host read-back. It retains roughly 1 MB through a bounded 4096-entry queue;
file I/O runs on the worker. It does not observe viewport position: capture timestamped UI
observations during RUN / PAUSE / RUN while scrolling the selected track offscreen.

## Selected-track scan cutover

Capability audit: **B — bounded API/shell expansion**, Core API 56, Bitwig API 25 unchanged.
The existing eight-slot scanner and eight launch actuators suffice. Core owns applicability,
target choice and paging; shell owns observation, validation and acquired-launch cleanup.
See [ARCH](ARCH.md) for the capacity and subscription contract. API 56 requires a matched
shell installation/restart; subsequent scan scheduling changes can hot reload.

Coverage includes delayed host page advancement, two coherent samples before readiness,
new/renamed/deleted clips, no inactive scanner reads or aligned-track reselection, stale-target
rejection, exact held cleanup, and request propagation through core composition.
`mvn -o -Dmaven.compiler.showDeprecation=true package` passed all 1,072 tests with no failures,
errors, skips or deprecation warnings in changed code. Debug-client, live-lock and all eight
surface-server tests also passed.

Live acceptance on 2026-09-10 used source `f18c3c059c49bc487dfe82fc4020b519fd63cb59`:

- Shell SHA-256: `c34c62d9b9dcae87e52b866e1cd83e10232e9eec7a240cd03af5da27bc6a93e2`.
- Shell fingerprint: `682ad10336957a9313e22d70acb19d3804f342c1`.
- Active core: `20260910T192442Z-a443551d5d436255560cb5303a376de6`.
- Core SHA-256: `51765e8da93f0d68bb544284ebfa7996e44a809ff615e339644754836ea0b787`.

In `202arp3`, the earlier diagnostic build (`68cc6005`) reproduced snap-back during RUN,
allowed offscreen scrolling during PAUSE, and snapped back again on resuming RUN without
mouse input. With the cutover build, the selected Drum Machine track remained offscreen
for 22 seconds during active scanning and playback: 997 observations, 333 page requests
across offsets 0/8/16, and zero cursor reselections.

Creating and renaming a temporary clip without changing tracks grew the catalog from 9 to 10
and armed fills from 4 to 5; deleting it restored 9/4. Routed `push.pad.14` BEGIN/END produced
later active-fill host state and held/released pad output; a separate later snapshot confirmed
no retained launch targets or active owner. Switching to Session removed the scan request:
505 host samples contained no page or cursor requests. The temporary clip was removed,
the project saved, and diagnostics expired to OFF with zero dropped entries.

The held trace reached its size limit, so complete release state comes from the separate
stopped trace. Physical touch and audible restoration were not tested. The final seven-line
removal of redundant pending-page state passed the full offline package; that revised shell
binary has not been reinstalled or live-tested.
