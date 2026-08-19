# Pull

An opinionated Push 2 control surface for Bitwig Studio, bent around a specific live-set workflow.

Pull is based on [DrivenByMoss](https://github.com/git-moss/DrivenByMoss).

## Building and installing the extension

1. Install JDK 21, [Maven](https://maven.apache.org/install.html), and Git.
2. Run `mvn clean package` in this repo's root.
3. Copy `target/Pull.bwextension` into Bitwig Studio's extensions folder.

For a targeted extension build, use `mvn -pl pull-shell -am package`. The reactor builds the
current core first and embeds that resolved artifact through the resource-only core bundle.

## Testing and live debugging

See [`TESTING.md`](TESTING.md) for offline test commands, the opt-in Push debugger, and the
closed-loop validation policy used for feature work.

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
coverage through the shell test loop documented in [`TESTING.md`](TESTING.md).

The first reloadable behavior occupies the 3x4 Drum Pads region directly above the four yellow
rate pads. Selected-track clips whose names contain `fill` case-insensitively are assigned in scene
order, one clip per pad, up to 12; later matches are ignored. The physical pad order is MIDI notes
`48-51`, then `56-59`, then `64-67` (bottom row to top row, left to right). An unassigned or
still-arming pad is off, and a verified ready pad is dim orange. Only the shell-reported active fill
is fully lit orange; a press request does not optimistically change the light before that
authoritative snapshot read-back.

The Pull controller settings include **Drum Controller → Automatic arp / roll**. It defaults to
On. Turning it Off immediately retires the drum layout's controller-input arpeggiator and blanks
the four rate pads; turning it back On re-enables the roll engine while the drum layout is engaged.
Leaving Drum Controller always retires Repeat so the controller roll cannot carry into melodic
note views. The user's manual mode, octave, rate, gate, latch, free-running, pressure and shuffle
settings are restored; Repeat can then be enabled manually outside Drum Controller if desired.

While Note is visible, Pull resolves the selected track through its private selection-following
target. A drum-capable target receives Drum Controller after Bitwig reports the aligned device; an
ordinary instrument receives its stored melodic view (or Play by default), and an audio track
receives Clip Length. The core keeps the request active until the visible layout reports the same
view, so asynchronous selection/device updates cannot leave the preceding track's pad layout on
screen.

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
requires a live Bitwig smoke test after installing the Core-API-28 shell on Bitwig controller API
21.

The permanent Push Pads note input is excluded from Bitwig's `All Inputs` pool. While a musical
Note viewer is active and its private selected-target identity is aligned, Pull routes that input
directly to the selected note-capable track. Pads do not silently fall back to every armed track
when Note view exits or the core fails. Pull does not change record arm or monitor mode: live API-21
testing found that a track in Bitwig's default `Auto` monitor mode must be armed to sound. A project
track explicitly configured for the named `Pads` input can still receive the same hardware stream
and is outside this selected-only route.
Each active view contributes one declarative controller-state bundle: its fixed controller facets,
full-grid Note layout when applicable, and selected-track musical route. Composite views merge
those contributions and reject overlapping physical owners. The full-grid Note viewer and the
lower-half drum controller therefore preserve the same selected-track route in their respective
workspaces; the drum controller also preserves its automatic-roll lease in Shift+Session.
One stable lifecycle owner applies the composed state. Routing is submitted before a musical
surface can activate, Session/failure uses a real neutral layout, and route removal waits for held
pads and sustain. Bitwig exposes no route-attachment read-back, so live note observation—not a
successful void API call—is the final smoke-test evidence.
The controller-private cursor also provides authoritative state, actions, identity, and drum
capability and is never exposed to Push's pin command.
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
independent of Pull. Disable it if track arm should not follow selection; Pull's direct Note-view
route does not change arm or monitor mode itself.
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

For the live checkpoint, install this shell build and restart Bitwig once. Leave instrument inputs
on `All Inputs`, enter Note view, and verify that Push pads and the ribbon sound only the selected
note track when it is armed, even when other tracks are armed. Verify that disarming the selected
track in `Auto` monitor mode makes it silent, that Session view stops the controller-owned route
after held controls are released, and that melodic↔drum selection changes retain the right layout
without leaking automatic roll. A track explicitly set to `Pads` is a deliberate exception to the
selected-only guarantee. Then exercise the fill A→B→release handoff to confirm the original base
clip is retained.

## Reloading a core during development

After installing and starting a reloadable shell, build, publish, and activate a core without
restarting Bitwig:

```bash
tools/with-pull-live --owner my-feature
tools/reload-core
# Run the complete live smoke test, then exit the lease shell.
exit
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
the stable note-input topology and parent-loaded API: the existing Pads input leaves `All Inputs`,
and the stable shell gains the bounded direct selected-track route consumed by the core's complete
composed controller-state lifecycle. After that, selected-track policy changes confined to `pull-core` use
`tools/reload-core` without restarting Bitwig. The broader bounded capability-canopy roadmap is
documented in
[`docs/reloadable-controller-core-design.md`](docs/reloadable-controller-core-design.md).
