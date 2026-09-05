# Frame page and Master button capability audit

Baseline source: `master` at `5537271f575852634a7a94e473eb57a404fd77a8`, inspected 2026-09-05.
This is preparation for the remaining migration; no implementation or live proof is claimed here.

## Frame page

`FrameMode` is a complete two-row/eight-column options page with inert encoder turns and touches.
All row actions use UP, with no modifier variants. Its mode activation enables Arranger and Mixer
observers, and deactivation disables them. That observation lifecycle belongs in the requested
bridge domain after the page becomes core-owned.

Lower row: select ARRANGE, MIX, or EDIT panel layout; toggle Note Editor, Automation Editor,
Devices, Mixer, or Inspector panels. The first three lights display observed layout selection;
the remaining lights stay at their ordinary on state because the legacy page does not observe
those panels' visibility. Do not invent optimistic selected feedback.

Upper row depends on the later observed application panel layout:

| Column | ARRANGE | MIX |
| --- | --- | --- |
| 1 | Clip launcher visibility | Clip launcher visibility |
| 2 | I/O visibility | I/O visibility |
| 3 | Cue markers visibility | Crossfader visibility |
| 4 | Timeline visibility | Device visibility |
| 5 | Effect tracks visibility | Meters visibility |
| 6 | Playback follow | Sends visibility |
| 7 | Double-height tracks | Inert/off |
| 8 | Fullscreen toggle | Fullscreen toggle |

EDIT/unknown layout has no upper-row actions and all upper lights off. Fullscreen does not receive
selected feedback. The display uses the same observed states as the lights. Existing application,
Arranger, and Mixer proxies already expose these domains; inspect the exact API 25 methods before
introducing direct calls or setters. Application panel layout and toggle methods are present in the
locally resolved API 25 Application.java source. Bounded project/UI-context fencing should prevent
a held release from accidentally invoking a newly selected project's settings.

## Master button

The current frozen `MastertrackCommand` ignores every edge while BROWSER is visible. DOWN captures
whether a composed workspace was active. LONG opens temporary FRAME and records that UP must
restore the prior mode. A normal UP toggles the MASTER page; the legacy noncomposed path can also
select Bitwig's Master and restore a remembered current-bank index on exit.

The repository's current architecture requires Master to be a page replacement over the exact
selected composition, retaining Session/Drum/Note routes and view lifecycles. The migration must
resolve the remaining noncomposed legacy selection branch against that existing contract rather
than carry an index-based target into the core. Do not leave a second stable Master behavior as
fallback. Frame and the initiating Master gesture must migrate together before exclusive cutover.

A retained global Master gesture must survive page replacement, LONG/END before entry read-back,
external mode changes, Browser appearing during the gesture, workspace changes, and reload
fencing. Rendering FRAME only follows later raw mode observation; the core page registry already
supports a finite page/background pairing and native mode SELECT/TEMPORARY/RESTORE.

## Required verification

Characterize the original routed DOWN/LONG/UP and option rows. Cover ARRANGE/MIX/EDIT/unknown,
all option lights, no optimistic visibility, inactive observer domains, precise target changes
between prepare/apply, nested temporary pages, and return to the same grid/note/ribbon ownership.
Use the generic debugger plus authoritative UI state during live smoke testing; an effect log
alone does not prove a panel changed.

## Application UI canopy implementation

`ApplicationUiHost` reuses the three existing initialization-owned application/Arranger/Mixer
proxies. All thirteen native boolean values remain interested; `APPLICATION_UI` controls DTO
sampling and publishes `ApplicationUiSnapshot.empty()` when unrequested. The Frame adapter must
therefore remove its old observer enable/disable lifecycle at cutover. Application panel layout
remains a shared interested native value used by existing framework code.

The bounded snapshot contains the raw panel-layout name, seven Arranger flags, six Mixer flags,
and a project/layout generation. Typed effects select a native layout, submit one of six native
unobservable panel toggles, or set one observed flag absolutely. They carry the exact snapshot
context and recheck project identity, live panel layout, and installed proxy identity at prepare
and apply. Setter submission never changes the published snapshot; later requested sampling is
the feedback source. Unrecognized panel layouts remain raw observed values.

Verified against `/Users/elliot/.m2/repository/com/bitwig/extension-api/25/extension-api-25-sources.jar`:
`Application.panelLayout()`, `setPanelLayout(String)`, the six native panel-toggle methods, all
thirteen `Arranger`/`Mixer` property accessors, and `SettableBooleanValue.set(boolean)` are present
and not deprecated. The framework gains thirteen mechanical absolute setters onto those existing
values; no new Bitwig proxy is created. Tests use the real framework wrappers over asynchronous
native-property fakes, check every primitive, gated sampling and stale-context rejection, and
exercise public bridge publication independently of parameter-only snapshot updates. Offline
results and first live proof are recorded by the root migration checkpoint.

## Implemented Master gesture boundary

`MasterButtonView` now exclusively owns the fixed Master button and its monochrome feedback in every
composition. The permanent binding is inert; `MastertrackCommand` and its stable semantic-action
resolver branch were deleted. The light retains intensity 127 for MASTER, MASTER_TEMP, or FRAME,
and 30 for another observed page. Submitted effects never change this feedback optimistically.

The former noncomposed branch selected Bitwig's Master and remembered a current-bank index to select
on exit. That behavior conflicts with the canonical AGENTS/ARCH contract: Master is a page replacement
over the exact selected composition and retains Session/Drum/Note routes. It is therefore removed,
not transferred into a new proxy recipe. Showing Master emits only generic controller-mode effects;
it never selects Bitwig's Master or restores a guessed bank index. The already established composed
page-only behavior remains: a short release from MASTER restores the mode manager; another mode,
including MASTER_TEMP, selects MASTER.

The view creates its physical gesture at BEGIN resolution before the active-parameter Snapback
barrier can defer dispatch. LONG and END are retained on that exact gesture, including a short
release's observed mode-generation target. Accepted LONG submits temporary FRAME. Its matching
release waits for a later snapshot to show FRAME in the manager's temporary slot before requesting
RESTORE. A request and its dependent restore cannot share a result. An already-temporary FRAME needs
a later host sample even when the mode generation does not change. After acknowledged entry, an
external page change still leaves the original long-release restore armed, as in the legacy command.
Rejected/unobserved entry expires after five seconds without restoring another page. These are
bounded value-only continuations; their capacity is the semantic-action queue capacity plus the
current physical gesture, and they acquire no Bitwig resource.

Browser suppression remains per edge. A Browser-visible LONG does not open FRAME; a Browser-visible
END does not restore or select a page. Leaving Browser before a later edge allows that edge's normal
behavior. The historical return flag resets only on a non-Browser BEGIN; thus an ignored Browser
BEGIN does not reinterpret earlier command state. Modifier buttons do not add variants. New core
generations discard old gesture state; the parent router independently fences replacement while a
core-relevant physical gesture is active.

`MasterButtonViewTest` covers the complete edge table, exact MASTER_TEMP handling, no DAW track
selection effects, deferred short/long releases, later acknowledgement with and without a generation
change, Browser suppression, nested/external pages, retained composition, stale generation cleanup,
and authoritative lights. Shared package and routed live verification remain pending.
