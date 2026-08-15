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

The debugger is off by default. Enable it before installing the shell, then restart Bitwig once so
the extension constructs its local transports:

```bash
tools/capture-push2-display --enable
```

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
status reports the private selection-following target's `track_position`, stable `track_id`, and
identity `track_generation`. Bitwig track positions are local to the immediate parent group, so a
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
snapshot, returned result, successful stable application, and sparse activation/failure lifecycle
around each retained transaction. It does not sample semantic input, effects, or changed replayable
output. A controller tick or snapshot-change event is telemetry only when it has no effects and its
complete result is unchanged from the preceding retained result; only the newest such entry between
meaningful transactions is retained, and the number coalesced is reported in the TSV header.

Capture stops at 256 retained entries, 60 seconds, or two million serialized characters. The
transport retains at most 16 trace files. The controller thread only appends references to bounded
memory; structural object rendering, filesystem polling, serialization, and pruning run on the
trace worker. Structural rendering writes into the character bound directly instead of first
allocating an unbounded object string.
Automatic timeout still leaves the last trace retrievable with `stop`. A successful `APPLIED` row
means stable effect submission returned; completion of an asynchronous Bitwig transition still
requires a later authoritative snapshot or output observation.

Adding or expanding the generic debug bridge is a stable-shell change and needs one extension
install and Bitwig restart. Frame capture and client-side navigation recipes can then be reused
across core hot reloads.
