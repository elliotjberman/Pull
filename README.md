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
`48-51`, then `56-59`, then `64-67` (bottom row to top row, left to right). A verified ready pad is
dim red, becomes bright red while held, and is off while unassigned or still arming.

Each pad launches only its assigned clip. The shell keeps a private one-slot Bitwig actuator for
each pad and freezes it for the duration of a hold, so session scrolling, selection changes, and
new core builds cannot retarget the matching release. Only one fill is active at a time: pressing
a second ready fill releases the previous fill before launching the new one, and the superseded
pad's eventual physical release is harmless. This slice passes offline fake-host verification;
its first Bitwig/Push smoke test still remains.

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

Installing milestone 4 itself requires one extension copy and Bitwig restart because it widens the
stable API/shell bridge. After that, edits confined to `pull-core`—including fill matching, colors,
ordering, truncation, and behavior using the existing selected-track catalog and launch
effects—use `tools/reload-core` without restarting Bitwig.
