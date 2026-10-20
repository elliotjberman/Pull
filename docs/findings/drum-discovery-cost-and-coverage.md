---
status: active
created: 2026-10-19
scope: drum-discovery
remove_when: discovery resources are separated from retained drum targets, measured selection cost is accepted, and coverage diagnostics identify the exhausted resource
---

# Drum discovery cost and coverage

The installed `9179ffe2` implementation passed the alien2 and silent eight-machine
smoke described in [TESTING](../../TESTING.md#composite-drum-discovery-api-59).
Elliot accepts its residual track-selection lag for the initial merge. The retained
track cache and further discovery optimization continue separately in PR #68.

## Evidence and current consequences

The two independent trees install 608 native equality values. Bitwig 6.1.4 scans
its global equality list when a proxy changes target, including on unrelated tracks.
The reference windows reduced measured cost; they did not remove it. Caching whole
trees trades fewer warm rebinds for more permanently installed native machinery and
has not yet established a live performance improvement.

Each tree separately bounds eight machines, eight containers, sixteen devices per
chain and eight layers per container. A non-drum device with `hasSlots()` consumes
a container even if its selected FX slot is empty. Capability is not population.
A settled fake-host reproduction with two visible kits and nine empty slot-capable
effects exceeds container coverage while retaining both kits. The permanent
`reportsContainerCoverageForEmptyFxSlotsWithoutLosingKnownDrums` regression covers
that case and recovery when the extra container is removed.

College Town displayed the former generic maximum-eight notification on a track
expected to contain two kits. That exact native cause remains unconfirmed: the
installed debugger was disabled, so the attempted bounded trace did not start.
The initial merge changes the message to describe incomplete coverage without
claiming which limit fired. An incomplete search does not prove a kit is missing,
and discovering two kits does not prove the remaining branches contain none.
Bitwig's musical routing is unchanged; undiscovered machines may still sound.

## Follow-up and removal criteria

Separate shared discovery/acquisition machinery from retained drum targets. Prove
assignment acknowledgement, deletion/movement invalidation, alias handling and
rediscovery before reducing identity checks; a pinned cursor is not a documented
immutable device ID. Measure cold and warm selection and the fixed native footprint
on the exact installed build before selecting a cache capacity.

Report the limiting resource and location; do not suppress a coverage warning solely
because some kits were found. Validate College Town's actual topology and slot
capabilities under the live lease. Delete this finding when these criteria are met,
moving the resulting bounds and contracts into the permanent tree documentation.
