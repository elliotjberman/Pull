# Drum device tree

The playable Drum grid combines up to eight machines beneath the selected track. A note is
populated when any discovered machine has devices on that pad. Overlapping notes still produce
one native NoteInput stream to the selected track; Bitwig distributes it through the track's
device routing. Pressure is likewise emitted once. Existing first-machine editing and mixer
pages are not a composite mixer.

## Discovery and bounds

The stable shell walks devices and layers in depth-first bank order. Bitwig can expose the
selected layer through its selected-slot cursor too. Acknowledged native device equality skips
that alias before allocating another retained cursor; pending assignments cannot establish
overflow until their identities are known. The search no longer merges “first instrument” and
native-device matcher results.
Drum devices are leaves: instruments inside their pads are not separate kits.

Each tree installs eight retained drum cursors and eight retained container cursors. A chain
exposes its first sixteen devices; a container exposes its first eight layers and its selected
FX slot. API 25 provides no bank of every FX slot, so unselected FX slots are outside this
coverage. The root plus retained container windows comprise 73 chains and 1,168 source devices.
Each retained cursor has a one-device sibling reference window, a thin pinned source-parent
cursor and a one-layer reference window. Native comparisons check its root chain, the eight
possible source parents, their eight selected FX chains, the reference layer chain and the sibling
device. The source layer UUID must agree with the reference UUID; an acknowledged retained layer
UUID cannot be replaced by a newly moved window. Each tree uses 304 equality values (608 across
the independent model and selection trees), down from 1,424 per tree. Bitwig 6.1.4 scans all
registered equality values on every proxy target change, even on unrelated tracks.
Reference-window moves wait for subscribed read-back before accepting identity. A viable retained
target waits for its sibling reference before considering reacquisition; a new cursor can first
acquire the chain that its reference needs. Pending ancestor validation hides descendants while
retaining their assignments; actual ancestor reassignment retires those old descendants.
Topology is created during extension initialization. Idle and cancelled private cursors are
parked by pinning so editor selection does not rebind their child banks. Layer/pad wrappers
create a one-device bank for presence and entry; sibling navigation retains its own window.
Unchanged trees do not traverse again or resubmit cursor selection. Layer UUID changes invalidate
their child chains even when device type and position stay the same. Surviving retained targets are matched by native equality before reusing a positional slot.
Structural changes trigger a bounded rescan. Pending acquisitions wait for host read-back.
Each acquisition attempt has a two-second monotonic deadline. Expiry permits a fresh attempt
through the same unpin/read-back/select-and-pin sequence; it never substitutes for native identity
acceptance. Repeated reads do not advance the deadline or acknowledge an assignment.

The model cursor and private selection-following target have independent trees. The latter
establishes selected-track applicability even when the user pins the model cursor elsewhere.
Core requires their track identities to agree before enabling Drum actions and feedback.

The first eight discovered machines contribute. Machine, container, chain or layer overflow
sets an explicit limited-coverage flag; core displays a coverage notification. Ordinary effects
with empty FX slots can consume the container allowance even with fewer than eight machines.
Resource-specific diagnostics and discovery/cache optimization remain
[tracked work](findings/drum-discovery-cost-and-coverage.md). Limits do not alter
Bitwig's native musical routing, so undiscovered machines can still sound. Merely having more
than one FX slot does not set overflow: selected-slot-only coverage is an API limitation.

## Host state and actions

Private device cursors are moved without selecting anything in the editor. Acquisition observes
unpinning before submitting selection and pinning together, then observes native target
equality and pinning. This prevents same-update unpin/pin writes from collapsing across
selection while retaining each target before another unpinned cursor changes the shared selection.
A retained assignment is published only after native equality, pinning, existence, position and
drum capability agree
with the source. Source revision changes or lost equality revoke it. Its identity is the track
UUID plus an opaque assignment generation, never its name or Java wrapper. Selection changes
retire the old tree's assignments.

Native equality acknowledges the device assignment, not atomic delivery of its layer/pad
properties. Reads do not count as additional host observations. Child windows use the ordinary
subscribed bank read-back; delayed child publication still needs live characterization. No
controller-side delay is presented as an API acknowledgement of all descendants.

Each machine publishes its own pad window and identity. Core chooses the combined population
and requests matching octave windows. The native note map remains silent until every observed
window matches the requested base. Held playable pads defer window changes. Each secondary
window effect rechecks selected-track identity, model alignment, device assignment and old
window position at application; a submitted scroll is not read-back.

## Capability audit

This is **B: bounded capability expansion**, Core API 59 / Bitwig API 25. Existing playable-pad
and octave routes, modifiers, pressure conversion, lights, native-note lifecycle and reload
gesture fences remain owned by core. No new exclusive input claim is needed. The new shell
resources observe the device tree; the new API carries machine windows and an exact device-window
scroll primitive. Core owns union lights, alignment and overflow notification. Parameter pages,
alternate legacy drum layouts and multi-machine editing are outside this change.

The parent-loaded records, effect and eager native proxies require one matched shell install
and restart. A core-only reload cannot install them. The existing parameter-target and reload
findings remain active; this does not establish a general device UUID or all-project search.

The opt-in `tools/push-debug-selection run` trace records bounded `DRUM_TREE` and
`DRUM_TARGET` observations from existing subscribed values, at most four times per second.
These include source revisions, native equality, pinning and child counts for acquisition
diagnosis; the trace has the existing 60-second deadline and bounded output.

## Group target fallback (API 60)

Core chooses the first drum-bearing direct child of a selected group from an eight-track
window. One additional private track cursor retargets the existing model tree; the child
window adds eight one-device count observers and no native equality matrix. Actual selection
and its independent private observer stay on the group. Child UUID, selection generation,
catalog revision, model/group alignment and later pin/UUID read-back fence the musical owner.
An outgoing native note route is detached before its cursor can be reassigned. Mixer and
Session/transport actions retain actual selection; drum-specific behavior uses the child.
Core API 60 requires a matched shell install/restart. See the [temporary policy and TODO](findings/group-drum-target-selection.md).
