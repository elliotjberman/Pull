# Pull

An opinionated Push 2 control surface for Bitwig Studio, bent around a specific live-set workflow.

Pull is based on [DrivenByMoss](https://github.com/git-moss/DrivenByMoss).

## Building and installing the extension

1. Install JDK 21, [Maven](https://maven.apache.org/install.html), and Git.
2. Run `mvn clean package` in this repo's root.
3. Copy `target/Pull.bwextension` into Bitwig Studio's extensions folder.

For a targeted extension build, use `mvn -pl pull-shell -am package`. The reactor builds the
current core first and embeds that resolved artifact through the resource-only core bundle.

## Fast core test loop

Run the reloadable controller core and its shell fakes without building the Bitwig extension:

```bash
mvn -pl pull-core -am test
```

After that first run has downloaded the test dependencies, repeat fully offline:

```bash
mvn -o -pl pull-core -am test
```

This loop uses deterministic fake time and does not launch or require Bitwig.

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
and then waits for the stable shell to observe the target view and mode on two later controller
ticks. The stable shell accepts only a bounded generic plan of safe navigation gestures and
authoritative view predicates; it cannot invoke transport, recording, deletion, or project-file
actions. The four named recipes above live in the command-line client, so they can change without
rebuilding the extension. Filesystem polling and PNG encoding run on owned workers rather than the
controller thread.

The resulting request-correlated image path is printed on standard output; navigation state is
reported on standard error. The fixed local handshake directory is
`~/.drivenbymoss/pull/debug`. One client owns an atomic lock for the complete navigate-and-capture
transaction, and every PNG is named for that request so another capture cannot satisfy or
overwrite it. Unless the `enabled` marker exists in that directory at extension startup, neither
debugger worker is constructed. Run `tools/capture-push2-display --disable` and restart Bitwig to
turn the transports off again.

For navigation without a framebuffer capture, use the generic client; named recipes stay client-side:

```bash
tools/push-debug-request session \
    'NOTE/workspace=false' \
    'TRACK/mode=TRACK,workspace=false' \
    'SESSION/view=SESSION,mode!=WORKSPACE|MASTER|MASTER_TEMP,workspace=false'
```

The client accepts only the installed safe-navigation protocol. Transport, scene launch, parameter
motion, and drum fills require a future core-owned debugger ingress, not stable-shell shortcuts.

Adding the generic debug bridge is a stable-shell change and needs one extension install and Bitwig
restart. Frame capture and client-side navigation recipes can then be reused across core hot
reloads.

## Reloadability end state

The architectural goal is for every Push behavior policy to be reloadable: mappings, gestures,
modes, views, navigation, and all pad/button/ribbon/display rendering decisions. The stable shell
should eventually be only a resource kernel. It must continue to create and own Bitwig proxies and
interested values, MIDI and USB connections, physical hardware bindings, display transport,
classloading, effect validation, and controller-thread execution, but it should not decide what a
Push control means or what the controller renders.

The current mixed-ownership input machinery—`InputRouteMode`, `DesiredInputRoutes`, `NONE`,
`OBSERVE`, stable command callbacks, and their arbitration—is temporary migration scaffolding, not
the desired final API. It exists because migrated and unmigrated Push controls currently share one
running controller. Once all Push policy has moved into `pull-core`, physical input can become an
unconditional event stream to the active core and these fallback/routing types and the old stable
Push commands, modes, and views should be deleted.

That cutover should be staged rather than attempted as a mechanical rewrite. The Push-specific
package currently contains roughly 17,500 lines across 103 classes, plus shared framework behavior.
The intended sequence is to install complete output arbitration, expand generic bounded state and
effect capabilities, migrate coherent behavior families with parity tests, and finally remove the
mixed-ownership layer. The detailed boundary and restart rules live in
[`docs/reloadable-controller-core-design.md`](docs/reloadable-controller-core-design.md).

Milestone 4 also has shell-side selected-track scanner, pinned-actuator, routing, and launch-lease
tests:

```bash
mvn -o -pl pull-shell -am test
```

The first reloadable behavior occupies the 3x4 Drum Pads region directly above the four yellow
rate pads. Selected-track clips whose names contain `fill` case-insensitively are assigned in scene
order, one clip per pad, up to 12; later matches are ignored. The physical pad order is MIDI notes
`48-51`, then `56-59`, then `64-67` (bottom row to top row, left to right). An unassigned or
still-arming pad is off, and a verified ready pad is dim orange. Only the shell-reported active fill
is fully lit orange; a press request does not optimistically change the light before that
authoritative snapshot read-back.

Each pad launches only its assigned clip. The shell keeps a private one-slot Bitwig actuator for
each pad and freezes it while its launch remains in the session, so session scrolling, selection
changes, and new core builds cannot retarget the matching release. The shell retains at most one
active fill above an opaque Bitwig-owned base. Pressing another ready fill records only the latest
pending intent, waits for the active fill to report busy, submits its native `Return`, observes it
stopped, waits one additional host sample, and only then resolves and launches the replacement.
The replacement therefore returns to the original base rather than the previous fill. Older held
pads are not a fallback stack; after a replacement ends they require a fresh release and press.

Fill entry is controller-defined: it launches immediately and uses Bitwig's Legato from Clip (or
Project) mode, so neither the source clip nor the fill clip needs a special launch quantization or
play mode. Release invokes the fill clip's ALT release action immediately. That effective ALT
release action must be `Return`: normally leave the fill clip on `Use Project Setting` and retain
Bitwig's default Project Settings → Clip Launcher → ALT Release setting, or set a local fill-clip
override to `Return`. A fill clip's loop enablement and length remain session content.

The slice passes offline fake-host verification. The exact immediate-legato/ALT-Return path still
requires a live Bitwig smoke test after installing the Core-API-9 shell on Bitwig controller API
21.

The Push Pads note input remains in Bitwig's ordinary input pool. Pads and the ribbon's raw
pitch-bend messages therefore follow the project's track-input, monitor, and record-arm routing;
selection alone does not force a track to receive them. The controller-private selected-track
cursor is used only for authoritative state, actions, identity, and drum-capability detection. It
is never exposed to Push's pin command and is not a musical note route. Changing selection does
not create or reconnect note inputs. More than one track can receive Pads when the project routes
and monitors or arms more than one track that way.
The same private cursor exposes a fixed four-candidate drum-device canopy: a native Drum Machine
match in the selected track chain, Bitwig's semantic first instrument when it reports drum pads,
plus native Drum Machine matches in that instrument's first layer and cursor slot. This catches the
common ungrouped and one-container grouped layouts while keeping selected-target drum capability
separate from which Push layout is visible and which physical controls that layout owns. It does
not recursively scan arbitrary nesting or parallel layers.
The framework's display/device cursor remains pinnable. If Track Pin makes it diverge from the
private selected target, Pull temporarily disables and blanks its drum controls until both proxies
represent the same track; it never displays one drum track while applying drum-controller actions
to another.
Bitwig's Dashboard → Settings → Recording → Auto-arm selected → Instrument tracks preference is
independent of Pull. Disable it if track arm should not follow selection. With ordinary routing,
the intended track must accept `Pads` or `All Inputs` and satisfy its monitor/record-arm settings.
The reloadable core exclusively owns every Push Record gesture: plain Record toggles selected-track
arm from authoritative read-back, Shift+Record toggles launcher overdub, and Select+Record creates
a new clip. The permanent Record binding is deliberately inert and exists only to retain the input
and authoritative-light seam; missing or faulted core behavior never invokes a second shell
implementation. Global arranger record remains available in Bitwig but is not bound to the Push
Record button.

Drum pitch remains an explicit Bitwig session setup rather than controller-managed project
content. On each drum track:

1. Disable the track Inspector's `P. Bend → Expr.` conversion so raw `BEND` reaches devices.
2. Put Bitwig's native Bend Note FX before Drum Machine and use a MIDI modulator in `BEND` mode to
   drive the desired Bend amount. This converts the track-wide control into note expression for
   compatible Bitwig instruments while preserving the MIDI note that selects the drum pad.
3. For a plug-in that does not consume Bitwig note expression, such as Kick 2, add a separate MIDI
   `BEND` modulator and map it directly to that plug-in's pitch parameter.

The controller does not insert, identify, repair, or own those devices. Raw bend alone is only a
message; the session's Bend/modulator mappings define what it changes. This is intentionally
manual and visible in the project instead of relying on a fragile hidden macro or helper registry.

For the live checkpoint, install this shell build and restart Bitwig once. Arm the intended drum
track or enable monitoring, set its input to accept `Pads` or `All Inputs`, and verify that Push
pads sound and the track's MIDI `BEND` modulators follow the ribbon. Disarm or stop monitoring it
and verify that ordinary Bitwig routing stops the input. Check that another track's arm button
remains independently clickable, then exercise the fill A→B→release handoff to confirm the
original base clip is retained.

## Reloading a core during development

After installing and starting a reloadable shell, build, publish, and activate a core without
restarting Bitwig:

```bash
tools/reload-core
```

The command runs Maven offline by default. Use `tools/reload-core --online` once if Maven still
needs dependencies. It publishes to `~/.drivenbymoss/pull/reload`; set
`PULL_CORE_RELOAD_DIR` or pass an absolute `--directory` to use the shell's configured directory.

Success means the running shell acknowledged the exact requested build ID—not merely that Maven
succeeded. Each candidate carries an exact fingerprint of the local parent-loaded core API
sources. If that differs from the running extension, Bitwig reports `restartRequired` instead of
attempting an unsafe core reload—even when the API change was already committed. Shell-only
implementation edits do not change this compatibility fingerprint.

Installing this shell checkpoint requires one extension copy and Bitwig restart because it changes
the stable note-input topology and parent-loaded API: the existing Pads input returns to ordinary
Bitwig input/monitor/record-arm routing, and raw MIDI effects target that stable input rather than a
selected track. After that, selected-track changes and edits confined to `pull-core` use
`tools/reload-core` without restarting Bitwig. The broader bounded capability-canopy roadmap is
documented in
[`docs/reloadable-controller-core-design.md`](docs/reloadable-controller-core-design.md).
