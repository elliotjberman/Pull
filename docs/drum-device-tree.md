# Drum device tree

The playable Drum grid combines up to eight machines beneath the selected track. A note is
populated when any discovered machine has devices on that pad. Overlapping notes still produce
one native NoteInput stream to the selected track; Bitwig distributes it through the track's
device routing. Pressure is likewise emitted once. Existing first-machine editing and mixer
pages are not a composite mixer.

## Retained track cache

One `DrumDeviceCache` belongs to the framework host. The pinnable model cursor and private
selection-following cursor each register a view. They keep their independent track identities,
but the same project/track shares one discovery entry and the same native device handles.
Core still requires model/actual-selection alignment before enabling Drum actions and feedback.

Four private pinned track entries retain their root banks, device cursors and child windows.
An unchanged cached track is available on lookup without native track/device selection. A first
visit acquires a track through unpin/read-back/select-and-pin/read-back, then discovers its tree.
Both consumers' current tracks are protected; a miss evicts the least recently used idle entry.
Selection observers park unfinished child/reference acquisitions when the last consumer leaves,
without waiting for another drum read. Ready assignments remain cached. Project observers cancel
pending work even when selected track UUIDs repeat. Eviction or a project identity change retires
old assignments before any native rebind. Device
identities include the permanent tree namespace and monotonically increasing assignment, so
reacquisition in another entry cannot resurrect an old ID. The project key uses the existing root
channel ID plus project-name boundary; it does not claim a new native project UUID contract.

Structural observers stay attached offscreen. Device/layer additions, removals and replacements
dirty only their entry, including cached empty results. Refresh waits until that entry is read;
parameter values such as volume do not trigger tree discovery. Cached resources are still live
Bitwig proxies, not serialized copies of the project. A changed tree may temporarily expose no
ready machines while identity/window observations settle.

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
selected FX chains, the reference layer chain and the sibling device. A matching globally unique
layer channel UUID already identifies the reference chain; the former per-handle parent-comparison
matrix is removed. An acknowledged retained layer UUID cannot be replaced by a newly moved window.
Each tree uses 176 equality values, down from 304. Four cached entries therefore install 704
comparisons versus the previous two selection-driven trees' 608. Source-device proxies increase
from 2,336 to 4,672, and full model drum wrappers from eight to 32 (plus any alternate pad windows).
This trades a larger fixed footprint for eliminating warm-selection rebinding; it is not a claim
of lower startup/memory cost. Bitwig 6.1.4 scans all registered equality values on every proxy target
change, even on unrelated tracks. Cold and warm live performance both still require measurement.
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

The first eight discovered machines contribute. Machine, container, chain or layer overflow
sets an explicit limited-coverage flag; core displays a coverage notification. Ordinary effects
with empty FX slots can consume the container allowance even with fewer than eight machines.
Resource-specific diagnostics and discovery/cache optimization remain
[tracked work](findings/drum-discovery-cost-and-coverage.md). Limits do not alter
Bitwig's native musical routing, so undiscovered machines can still sound. Merely having more
than one FX slot does not set overflow: selected-slot-only coverage is an API limitation.

Cached track acquisition observes pinning before navigation, then waits for the requested track
UUID. This differs from the device-cursor acquisition below: an unpinned **track** cursor can
change editor selection. Group targeting selects the model's cache entry through its musical
root; the separate actual-selection consumer remains on the group.

## Host state and actions

Private device cursors are moved without selecting anything in the editor. Acquisition observes
unpinning before submitting selection and pinning together, then observes native target
equality and pinning. This prevents same-update unpin/pin writes from collapsing across
selection while retaining each target before another unpinned cursor changes the shared selection.
A retained assignment is published only after native equality, pinning, existence, position and
drum capability agree
with the source. Source revision changes or lost equality revoke it. Its identity is the track
UUID plus an opaque tree/assignment generation, never its name or Java wrapper. Selection changes
retain ready assignments; entry eviction and project changes retire them.

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

The parent-loaded records, effect and eager native proxies require a matched shell install
and restart. A core-only reload cannot install them. The cache changes native topology without
changing Core API 59 or Bitwig API 25. It is currently offline-validated, not installed or live-verified. The existing parameter-target and reload
findings remain active; this does not establish a general device UUID or all-project search.

The opt-in `tools/push-debug-selection run` trace records bounded `DRUM_TREE` and
`DRUM_TARGET` observations from existing subscribed values, at most four times per second.
These include source revisions, native equality, pinning and child counts for acquisition
diagnosis; the trace has the existing 60-second deadline and bounded output.

## Group target fallback (API 60)

Core chooses the first direct instrument child of a selected group from an eight-track
window and stays there even when no drums are found. One additional private track cursor
retargets the existing model tree. The child window observes track identity and note capability;
it adds no device banks or native equality registrations. Actual selection and its independent
private observer stay on the group. Child UUID, selection generation,
catalog revision, model/group alignment and later pin/UUID read-back fence the musical owner.
Eight thin parent-track observations fence the child rows; any existing row with a mismatched
parent withholds the whole catalog. The retained root has its own parent witness, so a new
group request cannot be acknowledged by the previous group's pinned child. These nine parent
proxies add no native equality registrations.
An outgoing native note route is detached before its cursor can be reassigned. Mixer and
Session/transport actions retain actual selection; drum-specific behavior uses the child.
Core API 60 requires a matched shell install/restart. See the [temporary policy and TODO](findings/group-drum-target-selection.md).
