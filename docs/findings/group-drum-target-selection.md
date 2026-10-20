---
status: active
created: 2026-10-20
scope: group-drum-target
remove_when: explicit or routing-aware ownership replaces the positional gig fallback
---

# Group drum target selection

For the gig, selecting a group targets its first direct instrument track within the first
eight children. An instrument candidate is a non-group track that can hold notes. Core keeps
that track even when drum discovery is pending or no Drum Machine exists. It does not scan
later siblings for drums or combine sibling tracks. In College Town, AllDrum targets Drums;
Samples remains separate. Actual selection stays on AllDrum for mixer, Session and transport.

The existing bounded device scanner discovers up to eight Drum Machines on that child.
Drum pads, pressure, octave, roll, learned drum controls and fill scanning use the child only
after authoritative drum read-back. Without drums, group drum controls remain unavailable.
The fallback supports the core Drum Pad/VS controller; alternate legacy note layouts and
nested group traversal remain outside this version. Child identity/order and note capability
changes update the target; device publication never causes a move to a sibling.

TODO: rethink this positional policy with Elliot after the gig, alongside the separate cache
work. Prefer an explicit target or actual routing when the host exposes it. API 25's
SourceSelector reports audio/note selection but not which port, so it cannot distinguish
Pull - Pads from All Inputs. Do not infer that distinction from names, monitoring or record arm.
Any future search that skips non-drum children needs a reliable absence policy: root identity
and pin read-back, scanner idleness or elapsed time do not acknowledge every descendant window.

Remove this finding when target selection and nested-group coverage are agreed and implemented,
with matched native routing/feedback and cost measurements. This fallback still needs an exact
shell/core installation and live smoke test; another agent is using Bitwig, so this work is offline.
