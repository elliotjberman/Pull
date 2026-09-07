# Pull

An opinionated Push 2 controller for Bitwig Studio, built around a live-set workflow.
Based on [DrivenByMoss](https://github.com/git-moss/DrivenByMoss).

## Build and install

Install JDK 21, Maven and Git, then run `mvn clean package`. The reactor embeds the current core
through a resource-only bundle; `mvn -pl pull-shell -am package` builds the extension and its dependencies.
Copy `target/Pull.bwextension` into Bitwig Studio's extensions directory with Bitwig closed, then
start Bitwig. For shared development, hold `tools/with-pull-live --owner LABEL` through installation,
activation and the complete live test. Checkpoint source before a restart.

[TESTING](TESTING.md) defines offline checks and the opt-in full Push debugger. Current source uses
Core API 48 and Bitwig controller API 25; [ARCH](ARCH.md) records activation status and links exact
live evidence. Passing a build does not establish controller behavior.

## Core development

After installing the matching shell:

```bash
tools/with-pull-live --owner my-feature
tools/reload-core
# Verify the exact active build and complete the live smoke test.
exit
```

The command builds offline by default; use `--online` when dependencies need downloading.
Publication defaults to `~/.drivenbymoss/pull/reload`; `PULL_CORE_RELOAD_DIR` or an absolute
`--directory` selects the shell's configured directory. Success requires the exact build ID to
be acknowledged by the running shell.

Core policy, pages and styles hot reload inside the installed capabilities. API/proxy/capacity,
permanent binding and other shell changes need a shell install/restart. The API fingerprint rejects
incompatible core candidates; it deliberately does not detect unrelated shell implementation edits.
[ARCH](ARCH.md) explains assembly; the [runtime contract](docs/reloadable-controller-core-design.md)
and [views contract](docs/views-api-design.md) cover the boundaries. Remaining stable families and
prerequisites live in the [migration inventory](docs/reloadable-core-migration-roadmap.md).

## Live-set setup

Session selects the full 8×8 launcher grid. Shift + Session selects VS Live: an upper 8×4 Session
grid, lower Drum Controller, Project Macros and track-selection strip. Page changes retain the
musical background. An interaction whose target or binding disappears cancels; its remaining
physical motion/release cannot act on a replacement target.

While Note is selected, Pull resolves Drum Controller, the stored melodic layout or Clip Length
from the selected track's observed capabilities. The private selection-following cursor must agree
with the display/device cursor; pinning that cursor elsewhere makes affected controls blank/inert.
Drum detection covers a fixed four-candidate canopy, not arbitrary recursive device nesting.

The permanent Pads input is excluded from `All Inputs` and attaches directly to the selected
note-capable track while its musical view is active. Pull does not change track arm/monitor mode;
those settings still determine audibility. A track explicitly configured for the named `Pads`
input remains outside this selected-only guarantee.

**Drum Controller → Automatic arp / roll** defaults to On. It owns roll while Drum Controller is
engaged; leaving retires Repeat and restores the user's manual repeat parameters. Record toggles
selected-track arm, Shift + Record toggles launcher overdub, and Select + Record creates a clip.

### Fills

The eight fill pads use selected-track clips whose names contain `fill`, case-insensitively, in
scene order. Unassigned/arming pads are off, ready pads dim orange, and the observed active fill
bright orange. A launch request alone does not light a fill as playing.

Fill entry uses Immediate / Legato from Clip (or Project). Each fill's effective **ALT Release**
action must be **Return**, either locally or through its project setting. There is one active fill
and one latest pending intent. Replacement waits through the existing fill's observed Return
barrier; older held pads do not form a fallback stack. The
[runtime contract](docs/reloadable-controller-core-design.md#fill-session-and-host-barrier) specifies
that barrier and the limits of Bitwig's opaque return destination.

The four separate mapping pads use permanent track-scoped semantic endpoints. Native mappings
from the old shared protocol need relearning; browser debugger input cannot execute a Bitwig-learned
hardware action. See the [mapping contract](pull-core-api/src/main/java/de/mossgrabers/pull/core/api/CONTROLLER_MAPPING_IDENTITY.md)
for allocation and project limits.

### Drum pitch

Pitch setup is explicit project content. On each drum track:

1. Disable the Inspector's `P. Bend → Expr.` conversion so raw `BEND` reaches devices.
2. Put native Bend Note FX before Drum Machine and use a MIDI modulator in `BEND` mode to drive its
   Bend amount. This converts the control to note expression while preserving the drum-selecting note.
3. For a plug-in without Bitwig note-expression support, map a separate MIDI `BEND` modulator to
   that plug-in's pitch parameter.

Pull sends and neutralizes the raw bend; it does not insert or manage those devices. Verify audible
notes/pitch and physical learned mappings with the Push connected. The debugger can independently
verify routed controller actions, later subscribed host state and transmitted display/light/strip output.
