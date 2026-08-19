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

## Live Push display loop

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

It opens a local SVG surface derived from the measured control bounds in `PushControllerSetup`.
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
`NoteInput` paths. One browser edge may be held at a time; pressure may accompany that exact held
pad. A five-second controller-owned lease, renewed by the page, releases a control if the tab
disappears and neutralizes detached nonzero pressure.
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
the Pull arbitrator but cannot fire the parallel learned action. Controller API 21 exposes neither
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
touch-strip position is not yet part of browser input. Encoders turn only during a held vertical
pointer drag; their signed deltas run through the permanent continuous-control input arbitrator.
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
    'NOTE/workspace=false' \
    'TRACK/mode=TRACK,workspace=false' \
    'SESSION/view=SESSION,mode!=WORKSPACE|MASTER|MASTER_TEMP,workspace=false'
```

The eight upper display buttons are admitted while ordinary Track mode owns them. Every terminal
status reports the private selection-following target's `track_position`, stable `track_id`,
identity `track_generation`, `armed` state, and `monitor` mode. It also reports authoritative
`repeat` and `latch` state plus parent-owned Note-route command state. Bitwig track positions are
local to the immediate parent group, so a
`track=N` predicate is accepted only alongside the exact `track-id=ID` fence. Position remains
useful context; it is never global proof. A client can first run a harmless already-satisfied plan
to discover the currently selected identity:

```bash
tools/push-debug-request identify 'TRACK/mode=TRACK,workspace=false'
```

A `repeat=true|false` postcondition waits for authoritative read-back from the permanent Push
NoteInput repeat engine. With the project-specific identities discovered from terminal statuses,
this reproduces a Juno-to-Drum-Machine viewer transition without leaving Note mode and proves
automatic roll is scoped to Drum Controller:

```bash
JUNO_ID='<juno-track-id>'
DRUM_ID='<top-level-drum-track-id>'
tools/push-debug-request juno-to-drums \
    'TRACK/mode=TRACK,workspace=false,repeat=false' \
    "ROW1_6/track=5,track-id=${JUNO_ID},repeat=false" \
    "NOTE/view=PLAY,track=5,track-id=${JUNO_ID},repeat=false" \
    "ROW1_1/view=DRUM_PAD,track=0,track-id=${DRUM_ID},repeat=true"
```

Layout and Shift + Layout use the same permanent routed bindings as the hardware. This checks both
preference cycles without calling the legacy view manager directly:

```bash
tools/push-debug-request note-layout \
    'NOTE/view=PLAY,workspace=false' \
    'LAYOUT/view=CHORDS,workspace=false' \
    'SHIFT_LAYOUT/view=SEQUENCER,workspace=false'
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
`mapping_desired`, stable-host `mapping_active`, authoritative `mapped_on`, `desired_rgb`,
`resolved_light`, and `transmitted_light`. `mapped_on` is derived only from the subscribed API-32
Boolean snapshot, keyed by the semantic endpoint in the committed binding: `true` means Bitwig
resolved that virtual action's no-output background light on, `false` means either unmapped or
mapped-off, and `-` means the snapshot is not currently available. It never derives from desired or
transmitted RGB and intentionally makes no mapping-presence claim. The separate route, desired
binding, applied semantic-matcher activation, mapped read-back, and output fields distinguish an
inactive view or lane transition from a feedback/render/transmission failure. `mapping_active`
likewise does not claim that a manual Bitwig mapping exists, fired, or changed host state. The
transmission tap observes only the one armed pad and records only after the existing MIDI send
returns successfully; it adds no MIDI callback or output owner.

If the later applied result leaves an already-correct pad color unchanged, the ordinary renderer
may suppress a redundant send. The first post-apply debug observation therefore resends that one
pad exactly once through the existing output path. This is a real opt-in outbound update and may
consume an already-pending firmware fade for that pad; later observations never resend.

The debugger then submits UP through the same permanent arbitrator. It retains its input/core
generation fence until the routed gesture is idle and a later complete core result has applied.
Context, route, or generation changes, timeout, quarantine, close, and output failures all run the
same bounded best-effort UP cleanup before releasing the debugger lifecycle. This proves the
controller path through the transmitted palette state; it does not opt the pad into routing,
choose its RGB policy, or repair a missing mapped-feedback subscription or observer.

The DOWN/UP pair manually enters the original physical PAD button's permanent extension
trigger/arbitrator seam. Those physical buttons have no MIDI matchers and are not learned mapping
identities. Controller API 21 does not expose a way to inject a raw controller MIDI packet back
through Bitwig's separate semantic hardware-action matcher, so the probe reports whether that
virtual action is admitting the physical note-on but does not itself fire the Bitwig-learned action.
A live physical press is still required to prove that Bitwig learned the semantic identity, changes
the target, and returns fresh mapped-light feedback; the probe closes the extension-side route,
semantic lease, RGB, palette-resolution, and transmission loop around that authoritative feedback.

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
