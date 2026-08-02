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

The shell also exposes a generic eight-slot selected-track remote bridge for existing track Remote
Controls pages tagged `pull`. The exact `Pull` / `Drum Pitch` identity is reserved for a managed
native helper instead of a session Macro. On the first ribbon move for a selected track containing
a top-level Drum Machine, the shell inserts its bundled, neutral `Pull Drum Pitch Helper v1`
Bend Note FX immediately before the first such Drum Machine. Bend applies pitch expression without
changing the note key that selects a drum pad. The reloadable core maps the ribbon's centered
±12-semitone policy into the native device's full ±48-semitone parameter range. This puts both clip
and live notes through the same Note FX path without mapping the individual devices inside each
drum rack.

The managed helper is persistent Bitwig project content, so its first insertion marks the project
dirty. Ownership is the exact preset name and creator embedded in that helper, not adjacency or
device type: the shell never adopts or rewrites an arbitrary user Bend device, and inserting
another Note FX between the helper and Drum Machine does not lose it. The scan is bounded to 16
top-level Bend devices; duplicates, overflow, unsupported nested Drum Machines, and an owned
helper moved after the target Drum Machine fail closed. Hardware feedback remains authoritative:
the ribbon stays at the last subscribed `SEMITONES` value until Bitwig reports a submitted
insertion or write. Raw live-input pitch bend is never used as a fallback because it does not affect
clip-triggered Drum Machine voices. Each new note holds the selected offset for two seconds and
then returns over two seconds; this deliberately optimizes for drum hits rather than indefinitely
held notes. The controller does not promise to preserve authored bend independently of this global
offset.

For the live checkpoint, install this shell build and restart Bitwig once. Select a track whose
first top-level Drum Machine plays an obviously pitched repeating clip, enter Push's drum view, and
move the ribbon. The first move should add exactly one `Pull Drum Pitch Helper v1` Bend
device before the Drum Machine; after subscribed read-back catches up, new clip and Push hits should
follow the ribbon from -12 to +12 semitones, with center producing no shift. Moving another Note
FX between the helper and Drum Machine must not add a second helper. The track Inspector's incoming
pitch-bend conversion is not part of this path and should not need a special session setting.

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

Installing this shell checkpoint requires one extension copy and Bitwig restart because it widens
the stable API/shell bridge with native device matchers, bounded Bend proxies, preset-file
insertion, and specific-device parameter subscriptions. After that, selecting new drum tracks,
provisioning or recreating their helpers, and edits confined to `pull-core` use `tools/reload-core` without
restarting Bitwig. The broader bounded capability-canopy roadmap is documented in
[`docs/reloadable-controller-core-design.md`](docs/reloadable-controller-core-design.md).
