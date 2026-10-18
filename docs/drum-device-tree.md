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
coverage. The root plus retained container windows comprise 1,168 source device proxies and
18,688 native equality values per tree. Topology is created during extension initialization;
unchanged trees do not traverse again or resubmit cursor selection. Layer UUID changes invalidate
their child chains even when device type and position stay the same. Surviving retained targets are matched by native equality before reusing a positional slot.
Structural changes trigger a bounded rescan. Pending acquisitions wait for host read-back.

The model cursor and private selection-following target have independent trees. The latter
establishes selected-track applicability even when the user pins the model cursor elsewhere.
Core requires their track identities to agree before enabling Drum actions and feedback.

The first eight discovered machines contribute. Machine, container, chain or layer overflow
sets an explicit limited-coverage flag; core displays a notification. Limits do not alter
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
