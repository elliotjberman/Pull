---
status: active
created: 2026-10-20
scope: group-drum-target
remove_when: explicit or routing-aware target selection and descendant invalidation replace the gig fallback
---

# Group drum target selection

Implementation is not ready for activation. Review found that root identity/pin read-back
and an idle discovery pass do not prove that every descendant window has arrived. Caching
that apparent absence can skip the first drum child permanently. The user decision is pending:
use the first direct instrument track for the gig, or retain the broader first-drum-bearing
search and design a reliable discovery policy. Do not merge the negative-cache implementation.
The unrelated request-propagation and mixer-touch cleanup review findings are fixed; focused
production-core/bridge/lifecycle tests pass. The full offline package passed on 2026-10-20
(1,209 tests; zero failures/errors/skips; no deprecation warnings). No live installation or
DAW edits were made.

For the gig, selecting a group resolves its first direct note-capable child containing
Drum Machines, within eight child tracks. Core requests one child at a time through the
existing bounded drum scanner. The selected group remains the mixer/transport owner.
Drum pads, pressure, octave, roll, learned drum controls and fill scanning use the child.
The group fallback supports the core Drum Pad/VS controller; alternate legacy note layouts
remain unavailable on groups.

TODO: revisit this positional policy with Elliot after the gig. Prefer an explicit target
or actual routing when the host exposes it. API 25's SourceSelector reports audio/note
selection but not which port, so it cannot distinguish Pull - Pads from All Inputs.
Do not infer that distinction from names or monitoring/record-arm state.

This first version searches direct children only. It does not composite sibling drum tracks.
A selected child keeps the existing eight-machine/structural bounds. Pending or limited
searches are not proof that an earlier child is empty. Child identity/order, selection and
top-level device-count changes restart the search. A nested edit inside a previously rejected
child may require leaving and reselecting the group; full invalidation would need additional
observations or a different discovery architecture. Revisit this with the separate cache work.

Remove this finding when the target policy, nested-group coverage and invalidation rules have
been agreed and implemented, with matched native routing/feedback and cost measurements.
Live verification of this initial fallback is pending; another agent owns the current DAW work.
