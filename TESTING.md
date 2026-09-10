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
  extension startup. Document any restart needed to enable or disable them.
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

## Retained note-copy regression

The legacy sequencer Duplicate gesture uses up to four private track/clip cursor pairs per editor
shape. Each copy freezes the destination, page, resolution and expression values; it waits for
observed pin/target alignment, then a fresh note observation before expression writes. A later
matching observation completes the copy. Selection changes before capture cancel; changes after
capture cannot redirect it. Lost targets, observed note deletion and a three-second host deadline
retire the operation. A full pool refuses additional copies. Bitwig exposes no stable note ID, so
an unobserved delete/recreate of the same cell cannot be distinguished from editing that note.
Copying also converts the framework gain snapshot back to Bitwig’s native scale, preserving source
gain instead of halving it. These eager private proxies require a shell installation and Bitwig restart.

In a scratch Launcher project, use real routed Duplicate-plus-pad input on a source note and an
empty destination. With debugging enabled, `tools/push-debug-selection hold-note-copies 30` holds
only the expression phase; wait for `COPY_CREATED`, select another clip or track, then run
`tools/push-debug-selection run 30` to release it while retaining the trace. Check later
`NOTE_OBSERVED role=copy-complete` raw values in `selection-trace.tsv`, reselect both clips and
verify the destination and unrelated clip independently. Repeat without the hold and with the
captured clip deleted during the hold. `stop` or the diagnostic deadline also releases the hold.
The hold is off by default, expires within 60 seconds and does not consume the normal copy deadline.

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

The debugger is off by default. Enable it before installing the shell, then restart Bitwig once so
the extension constructs its local transports:

```bash
tools/capture-push2-display --enable
```

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

Disable the debugger with `tools/capture-push2-display --disable` and restart Bitwig only when the
next operator no longer needs it.

The live mirror is constructed only when debugging was enabled before extension startup. Installing
this shell change and restarting Bitwig once adds the output observer; later core reloads reuse it.
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

The setting is read from `frame-sample-rate.txt` and accepts one sample per 1 through 600 outbound
frames. Deleting that file restores the every-frame default.

The resulting request-correlated image path is printed on standard output; navigation state is
reported on standard error. The fixed local handshake directory is
`~/.drivenbymoss/pull/debug`. One client owns an atomic lock for the complete navigate-and-capture
transaction, and every request-correlated PNG is named for that request so another capture cannot
satisfy or overwrite it. Unless the `enabled` marker exists in that directory at extension
startup, no debugger transport is constructed. Run
`tools/capture-push2-display --disable` and restart Bitwig to turn the transports off again.

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

Enable the opt-in Push debugger at startup and hold the live lease. Use
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
