# Controller pages and navigation during the migration

The API 45 working build exposes raw mode-manager state: the visible mode, underlying active mode,
previous mode, temporary-slot occupancy, and a generation covering that complete state. Generic
SELECT, TEMPORARY, and RESTORE effects validate the full observed origin again at apply. RESTORE
uses the installed manager's actual semantics; it is not a stack invented by the bridge.

A core page declares `installedModeId()` alongside its fixed claims. The compiler allows only one
registered page adapter per composition. The shell validates that adapter's permanently inert
encoder/touch/two-row footprint; page effects remain responsible for transitions. The declaration
alone does not call `setActive` and therefore cannot erase the manager's temporary-page history.
Legacy Track, Project Macro, and Master facets still provide compatibility selection and must agree
with their declared mode. New page families require no new facet.

`ControllerPageCompositions` compiles a finite set of page/background pairs. A background retains
its original grid, note-route, and ribbon view instances. Authoritative mode read-back selects the
new page; an emitted effect does not optimistically select its display. Metronome and Automation
button gestures remain in the controller-level composition across nested temporary pages. A new
workspace request supersedes the previous page, including while its old mode is still visible.

## Arrow migration and regression baseline

Source parity was checked against `master` at `5537271f575852634a7a94e473eb57a404fd77a8` on
2026-09-05. The earlier Track conversion had removed the bank attached to AbstractTrackMode and
its Shift swap overrides. This would disable normal paging/reordering outside VS Live. The core
navigation slice restores that behavior before the next validated checkpoint.

- Track and global mixer pages: left/right page the current track bank; Shift left/right swap the
  actual model cursor with its neighboring track. Lights preserve the original bank-scroll flags,
  which are not a promise that a swap will change the project.
- VS Live: its declared Session navigation takes precedence over the selected parameter page.
  Left/right page tracks; Shift left/right scroll one track. This is distinct from full Session.
- Other migrated pages: left/right remain inert, matching their original bank-less page adapters.
- All these profiles: up/down scroll one scene; Shift up/down select a scene page.
- Separate page-left/page-right buttons retain their audited frozen Session/sequencer paths in
  this slice. In particular, full Session's layout predicate and VS Live's navigation facet are
  not interchangeable.

The shell publishes track/scene scrolling flags and executes ten bounded navigation primitives.
A separate navigation generation includes current bank/window, scene offset, and cursor identity,
pin state, and position. Scene movement therefore does not churn parameter-target generations.
Effects recheck that full identity at apply. Left/right intents retain their original generation
and modifier choice across the parameter restoration barrier. Commands are submissions; lights
change only from later observed bank state.

## Verification status

Focused raw-mode, adapter admission, and temporary-page tests have passed offline. Further
navigation, Volume/Pan, and controller-level Mix tests are in progress. The full package and live
Bitwig smoke test are still required for this working state; this document does not claim them.
