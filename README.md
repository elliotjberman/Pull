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
changes, and new core builds cannot retarget the matching release. The shell owns one ordered chain
of native Bitwig `Return` ancestry. Pressing a later ready fill makes it active while retaining the
older fills beneath it. Releasing a non-active ancestor is a no-op; releasing the active fill
unwinds the complete chain from newest to oldest and returns to the original source clip. Pressing
a retained ancestor again unwinds only the newer fills and reveals that ancestor, even if catalog
ordering changed while it was retained. Retained targets are reserved from assignment to another
pad until their Return frame is released. The latest active owner is therefore the one audible fill
even when several launch frames are retained.

Fill entry is controller-defined: it launches immediately and uses Bitwig's Legato from Clip (or
Project) mode, so neither the source clip nor the fill clip needs a special launch quantization or
play mode. Release invokes the fill clip's ALT release action immediately. That effective ALT
release action must be `Return`: normally leave the fill clip on `Use Project Setting` and retain
Bitwig's default Project Settings → Clip Launcher → ALT Release setting, or set a local fill-clip
override to `Return`. A fill clip's loop enablement and length remain session content.

The slice passes offline fake-host verification. The exact immediate-legato/ALT-Return path still
requires a live Bitwig smoke test after installing the API-5 shell.

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

Installing the API-5 milestone requires one extension copy and Bitwig restart because it widens
the stable API/shell bridge with `effect.clip-launch-hold` version 3 and
`snapshot.clip-launch-session` version 1. After that, edits confined to `pull-core`—including fill
matching, colors, ordering, truncation, active-fill policy, launch quantization, launch mode, and
the Main-vs-ALT release lane—use `tools/reload-core` without restarting Bitwig.
