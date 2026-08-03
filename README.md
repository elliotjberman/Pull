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
requires a live Bitwig smoke test after installing the Core-API-6 shell on Bitwig controller API
21.

The Push Pads note input is attached directly to a private Bitwig selected-track cursor and
excluded from `All Inputs`. Pads and the ribbon's raw pitch-bend messages therefore follow
selection regardless of monitor state; record arm controls recording, not which track can be
auditioned. The controller does not expose this cursor's pin control, so pinning the normal model
cursor cannot strand the Pads route on an old track. Changing selection does not create or
reconnect note inputs.
Explicitly selecting `Pads` as another track's input can still create a second conventional route;
leave other tracks on `All Inputs` when testing the no-duplication behavior.

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

For the live checkpoint, install this shell build and restart Bitwig once. With the selected drum
track unarmed and monitoring off or automatic, Push pads should still audition it and its MIDI
`BEND` modulators should follow the ribbon. A different armed track must not receive a duplicate
Pads stream. Change selection and verify both notes and bend move to the new track, then exercise
the fill A→B→release handoff to confirm the original base clip is retained.

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
succeeded. Each candidate carries an exact fingerprint of the local parent-loaded shell/API
sources. If that differs from the running extension, Bitwig reports `restartRequired` instead of
attempting an unsafe core reload—even when the shell change was already committed.

Installing this shell checkpoint requires one extension copy and Bitwig restart because it changes
the stable note-input topology: the existing Pads input is now directly attached to the
selected-track cursor and removed from Bitwig's global input pool. After that, selected-track
changes and edits confined to `pull-core` use `tools/reload-core` without restarting Bitwig. The
broader bounded capability-canopy roadmap is documented in
[`docs/reloadable-controller-core-design.md`](docs/reloadable-controller-core-design.md).
