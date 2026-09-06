# Migration shortcuts and architectural friction

Requested by Elliot during the shell-to-core migration on 2026-09-05. This is a running ledger,
not a claim that the migration or live testing is finished. Keep resolved entries so the final
review can distinguish shortcuts we retained from defects we caught and removed.

## Retained compromises

| Item | What we did and why | Cost / architectural direction | Status |
| --- | --- | --- | --- |
| Registered inert mode adapters | The initial migration required a registered mode per core page. The page-ownership refactor replaces those bodies with one generic inert footprint; old names remain aliases for frozen legacy callers. | A new core `PageId` within the installed footprint needs no stable registration. Device/Browser and other legacy bodies still require compatibility projection. | Per-core-page registration removed in the API 46 refactor; frozen aliases remain. |
| Finite page/background combinations | `ControllerPages` now declares all typed pages over the existing Note, Drum, full Session and VS Live backgrounds. One compiler replaces the special Track/Master maps and native-mode selection paths. | The finite combinations are intentional startup validation; every page reuses the same retained grid objects. Adding new hardware geometry still requires a canopy expansion. | Duplicate selection machinery removed; bounded precompilation retained. |
| Raw legacy mode names and temporary-slot semantics | `PageNavigation` now owns selected/previous/temporary references and exact return tokens. `LegacyPageAliases` translates existing names; core-only pages may have no alias. | Legacy bodies submit a bounded ordered request inbox and inspect the committed compatibility projection. Their remaining page assumptions must migrate with those bodies. | Core page identity/history no longer depend on raw mode read-back. |
| Legacy page manager still has one temporary slot | One core navigation owner preserves the established single replaceable temporary page and the controls’ distinct latch/return behavior. The shell only projects the resulting value. | This deliberately preserves product behavior rather than introducing a new navigation stack during an ownership refactor. A later product change can alter it entirely in core. | Shell temporary-page policy removed; one core temporary owner retained. |
| Snapshot-domain alignment checks | Immediate parameter-lease reconciliation can refresh the parameter table while the rest of the bridge snapshot is older. Views compare published parameter owner/role metadata with the track or project they render. | Correct checks repeat across pages. A bridge publication model with explicit coherent domain epochs or reusable typed joins could reduce this burden. Blanket object identity checks cannot solve it. | Volume/Pan and selected Track/Master/Project guarded; expanded 823-test gate passed, live pending. |
| Named parameter banks remain proxy windows | Migrated pages bind to explicit bounded bank slots and then obtain opaque fenced targets. This reuses installed Bitwig proxies and avoids physical-knob identities. | It is not yet a durable semantic-target model or pinned actuator pool. Some navigation invalidates old targets, and two slots can represent the same musical parameter independently. | Retained; see `../findings/parameter-target-proxy-coupling.md`. |
| DTO compatibility constructors | Extended records keep old constructors with typed empty/default values so existing callers and tests can migrate incrementally. | Defaults can hide missing state in a fixture or caller. New production behavior must fail closed when required identity metadata is absent. Remove obsolete overloads after all production consumers are explicit. | Retained; no claim of old binary API compatibility. |
| Frozen legacy output and adapter facets | Unmigrated controls preserve their original stable behavior, and compositions declare explicit stable adapter claims. Migrated controls become inert in stable and own action plus feedback in core. | Two composition mechanisms coexist until the remaining families migrate. The facet/claim correspondence is not yet completely enforced in both directions. | Retained debt; `../findings/stable-facet-claim-coupling.md` remains active. |
| Baseline quirks preserved | Mixer Shift-arrow lights still show bank scrolling flags although the action swaps the model cursor. Send menu pagination thresholds differ between selected Track and global mixer pages. | These are compatibility choices, not an ideal new interaction design. Once migration is verified, decide whether to normalize them as explicit product changes. | Deliberately preserved; documented in capability audits. |
| Send display infers widget type from its name | The inherited renderer chooses a fader for a send name containing `Volume`, a pan slider for `Pan`/`Panning`, and a ring otherwise. Send names themselves are not printed by this page. | This couples a display decision to arbitrary user-facing text. An explicit parameter presentation role would make the rule predictable; changing it is a product decision separate from this migration. | Preserve in the Send migration and characterize it. |
| Different long-press return rules | Master checks Browser on each edge; Automation has Delete consumption/restore interactions; Metronome LONG latches its temporary page until a later tap. | A generic gesture helper must represent these differences explicitly, rather than silently normalize them. A product pass could standardize temporary-page interactions after parity is established. | Deliberately preserved; transport and Master capability audits record the branches. |
| Page-capable buttons enter the parameter barrier at BEGIN | Accent, Automation, and Metronome can change the parameter page on LONG. Their gesture starts now declare that dependency before later phases arrive. A short toggle on the same button may consequently wait for an outstanding parameter restore too. | This is a conservative timing choice. Phase-aware admission could avoid unnecessary waiting while retaining exact BEGIN context, but must not lose release or modifier-consumption timing. | Implemented and included in the 823-test package gate; separate from the parked shell quiescence redesign. |
| Ranged encoders follow summed relative motion | The repository explicitly requires summing relative motion before a controller tick. Accent therefore applies the summed delta to later observed velocity, as other ranged core controls do. At velocity 127, packets `+1, -1` can produce 126 under sequential clamp processing but 127 after net-zero coalescing. | Net movement is preserved; packet-boundary behavior at clamps is not. If that matters musically, the generic input API needs bounded ordered motion information. Do not claim exact packet-level parity. | Explicit input-contract tradeoff; implemented and offline tested. This does not resolve Crossfader's distinct one-step-per-callback policy. |

## Defects caught rather than retained

| Item | Finding and correction | Evidence / remaining check |
| --- | --- | --- |
| Lost Track navigation inheritance | Replacing `AbstractTrackMode` initially lost its bank paging and Shift-swap callbacks. Comparison against master exposed this; the complete action and light behavior moved into core `NavigationView` with exact current-bank effects. | Core and shell regressions pass in the 823-test package gate; live comparison pending. |
| Parameter value under the wrong track header | A partial parameter refresh could pair a new target with older track metadata. Added explicit owner/domain metadata and reject mixed-owner new actions/output instead of trusting wrapper identity. | Mixed-owner coverage for Volume/Pan and selected Track/Master/Project passes in the 823-test gate; live checks pending. |
| Treating any page generation change as Automation entry | The first Automation migration would allow a pending return after an unrelated mode change. It now requires a later observation of the actual temporary Automation page. An expired request is abandoned without restoring or turning a long press into a write toggle. | Exact-page/missing-ack regressions passed; the later independent review found shared slot-ownership and stale-emission defects recorded below. |
| Delaying modifier consumption behind an action barrier | Deferring the whole Automation Delete gesture could let Delete's stable release run before it was consumed. `ResolvedControllerAction` now separates bounded immediate button consumption from deferred semantic effects; parameter resets and page/transport changes still wait. | Core-only shared mechanism, at most eight consumed gestures; routed deferred-Delete regression passes in the 823-test gate. |
| Stale deferred page effects disabling core | Master, Automation and Metronome could submit a frozen effect after its origin changed. Production preparation would reject it and quarantine the active core. | Review A1 corrected, including the equivalent global mixer menu. Six real core/runtime/bridge cases and fresh-action recovery pass; Master and Send tests had previously asserted production-invalid stale effects; both now prove frozen intent separately from cancellation. |
| Touch lease lost during snapshot refresh | Removing every no-longer-current touch before cleanup forgot exact actuators that were still addressable after selection/bank changes. | Review A2 corrected; refresh performs addressability-fenced cleanup. Selection/bank-change and true-rebind regressions pass in production order. |
| Acknowledgement timeout used as a maximum hold duration | Accent retired its return obligation after five seconds even when entry was acknowledged and the button remained held. | Review A3 corrected; long acknowledged holds and intervening-page cancellation pass regression tests. |
| Multiple return debts against one temporary slot | An older Accent release could close a newer held page; equivalent independent pending lists existed in Automation and Master. | Review A4 corrected with one shared core owner; repeated and cross-control gestures, plus multiple admissions in one actual core result, pass. |

The independent [architecture and line-count report](core-migration-review.md) retains the original
findings and their resolution status. Its small accepted simplifications remove duplicated remote
reconciliation and obsolete wrappers; larger composition consolidation remains explicit debt.

## Shortcuts considered but not taken

| Item | Why it is not an implementation shortcut we can silently accept | Consequence |
| --- | --- | --- |
| Assume a Session release completed after a void API call or a delay | API 25 has no general clip-release completion signal, while the repository contract requires acknowledgement before reusing that exact actuator. A timer would not supply the missing proof. | Session grid cutover and actuator pool remain pending the requested contract decision. See `session-launcher-location-design.md`. |
| Treat a coalesced encoder delta as the old callback sequence | Crossfader and MIDI-channel controls step once per old callback; summing deltas loses packet boundaries and ordering around clamping. | These controls need a bounded input contract or an explicit behavior decision before migration; they are not declared migrated. |
| Disable observers, then declare state ready after one tick | A tick is not proof that Bitwig delivered fresh values after re-enabling observation. Application UI reuses eagerly interested native properties and gates DTO sampling only. | No artificial readiness delay added. |
| Keep a stable fallback for a migrated gesture | It would leave duplicate policy and make the next iteration need another restart. | Exclusive controls have inert stable actions and core-owned feedback; missing/faulted core stays inert or blank. |
| Call offline success a live smoke test | Fakes cannot prove native proxy enforcement, physical note routing, or displayed output in Bitwig. | Record exact-build installation, later host read-back, controller output, and reload evidence separately when live testing occurs. |
| Invent a device identity from its name or slot | The production cursor inherits [SpecificDeviceImpl.getID()](../../pull-shell/src/main/java/de/mossgrabers/bitwig/framework/daw/data/SpecificDeviceImpl.java), which returns an empty string; the named device-remote bank excludes blank owners. The local API-25 `Device`/`ObjectProxy` interfaces expose equality observations, not a device-instance UUID. | The [completed Device audit](device-family-capability-audit.md) proposes investigating a bounded retained cursor/equality handle. Acquisition, pinning, replacement and later read-back remain unproved; no Device cutover is claimed. A name/position string cannot become a safe retained target. |
| Infer routed behavior from a mode callback alone | Device Chains overrides `onKnobValue` with a no-op but inherits remote parameter bindings; the hardware path can mutate the bound parameter directly. Its initial subpage and row-light indexing also disagree with its displayed menu. | [Device characterization](device-family-capability-audit.md#chains-and-routed-navigation) must drive the actual binding path. These source-derived quirks have not been live-reproduced or deliberately corrected; do not copy an apparently inert callback as proof of parity. |

## Explicit behavior decisions

Master now consistently replaces the parameter page over the selected composition. The legacy
noncomposed branch that selected Bitwig's Master track and later restored a track by index was
removed because the repository's canonical architecture explicitly requires page replacement.
Track selection effects are therefore absent. This intentional behavior difference is recorded in
`frame-master-capability-audit.md`; it must be assessed separately from accidental regressions.

The review correction also cancels a deferred page effect when any part of its frozen origin has
changed. An acknowledged temporary page loses its old return obligation when another page or view
takes ownership; returning to the same named page does not revive that obligation. This changes the
old Master/Automation behavior that could restore whichever page happened to be current on release.
It follows the exact-intent and ownership rules, and receives explicit regression coverage. Changes
to an applied note map, pressure mode or base note alone do not end temporary-page ownership.

Among these four controls, a newer unsubmitted page intent supersedes the older one before deferred
admission. Multiple queued transitions therefore cannot act on the same old native slot. This is a
bounded page policy, not a generic coalescing rule for parameter writes or transport toggles.

## Architectural directions supported by this checkpoint

1. **Own page lifecycle in core.** Three related review failures came from separate controls
   managing one native temporary slot. The shared owner fixes those failures now. The remaining
   per-mode registrations and duplicate composition tables point toward a core page model over
   generic installed display/input footprints.
2. **Make target identity and observation alignment easier to use.** Cleanup must distinguish
   current eligibility from exact addressability; parameter/display snapshots need explicit joins.
   The device bank's missing production identity makes a bounded retained-target capability a real
   prerequisite, not optional cleanup. Preserve the genuine finite-proxy constraint.
3. **Define the input information contract before migrating callback-sensitive controls.** Summed
   motion is sufficient for many controls but cannot reproduce all packet-boundary/clamping rules.
   A bounded richer motion representation or an explicit product decision would smooth Crossfader
   and MIDI-channel migration.

The independent reviews accepted the corrected slices after the 838-test gate, with live validation
still pending. The page fix added 70 net production lines across its helper/consumers; it reduced
competing owners rather than source size. Small duplicate reconciliation/wrapper deletions were
also applied. The whole migration is still incomplete; the [inventory](core-migration-plan.md)
keeps untouched families and the Session release decision visible.

## Final review procedure

Before presenting the finished migration, update each entry with its final status and validation.
Add any further expedient branch, duplicated recipe, temporary API, hard-coded capacity, visual
approximation, unsupported test path, or explicit behavior deviation encountered. Link concrete
code/audits; do not classify a genuine initialization-only Bitwig constraint as a removable hack.
The final report should identify retained compromises and the few architectural improvements with
the clearest supporting evidence from this ledger.

## Live harness friction discovered on 2026-09-06

- **Stale navigation meaning, corrected in CLI:** the Mix recipe expected `workspace=false`, but
  that flag now means any core composition, including Track over Drum. Named recipes now wait for
  their actual page/grid IDs and have wire-plan regressions. No shell rebuild is needed for this fix.
  The older shell ROW1 Track-context guard has the same assumption; generic routed HTTP rows are
  used for smoke instead, and that named-shortcut guard remains explicit harness debt.
- **Released chord edge retained, corrected and live verified:** debug END marked an edge released but
  kept it in the map until the whole chord ended. Repeating a row while holding Master was rejected.
  Separate individual edge lifetime from shared debug-admission lifetime; preserve router-idle
  completion. Repeating I/O under a held Master now produces later false→true→false host state.
  This is debug transport lifecycle, not new stable product behavior.
- **Reentrant cleanup ownership, corrected and live retested:** footer selection invalidated the note
  route; neutralization synchronously released browser input, which refreshed the same route and
  recursively attempted cleanup until Bitwig reported a stack overflow. Both route and debug-edge
  owners must retire their logical ownership before callbacks, while retaining shared admission
  until callbacks return and the router is idle. Regression tests connect the real input router to
  both lifecycle hosts, including nested chord release and replacement attach-before-layout ordering.
  The earlier 851-test gate passed. The API 46 build now also passes a real
  `202arp` footer selection/return, including the exact route-invalidation release, without recursion.
  Its full package passes 932 tests; see `core-page-ownership-live-smoke.md`.
  This is a demonstrated lifecycle defect,
  separate from the parked general quiescence redesign; no blanket asynchronous drain is claimed.
- **Trace serialization cap:** large complete snapshots can fill the 2 MiB trace before a long
  scenario ends. Keep the cap; use short per-step samples and inspect truncation markers. The
  artifact scripts are test scaffolding and do not change the production bridge. Idle samples may
  have authoritative snapshots without a new core result. Use exact action-scoped traces to prove
  input/effect/touch ownership and later samples to prove host state; never fabricate a result from
  the idle sample. A selection-triggered cancellation must match the original browser BEGIN and be
  recorded separately from the later host selection that proves the feature worked.
- **UI automation limitation:** keyboard Save As created an independent scratch project, while
  custom-content clicks fail with out-of-window coordinates. The user supplied three mapped
  project remotes, and their routed writes/touch/reset were then verified. No shell feature or
  optimistic fake was added to work around this limitation.
- **Profile-dependent Frame layout:** the scratch host uses Bitwig's Dual Display (Studio) profile.
  MIX↔EDIT was observed; the submitted ARRANGE request was a no-op. API 25 documents that available
  panel layouts depend on the display profile, and master uses the same native request. No fallback
  remapping or stable policy was added to make an unsupported profile appear to pass.

- **Mixed physical/browser collision guard remains limited:** debugger admission still reads legacy
  `IHwButton.isPressed()` for physical collisions. A physically held exclusively routed button can
  evade that check. The smoke runs use one input operator; they do not prove mixed-ingress safety.
  A raw physical-state or exact-router-gesture query is the appropriate future mechanism.

## Core page ownership follow-up (2026-09-06)

The requested follow-up removes the duplicate shell/core page histories. `Page` is an immutable
core definition with a typed ID, fixed views, optional navigation owner, and named parameter
indications. `PageNavigation` owns the selected/previous/temporary references. Views project host
read-back into immutable presentation records; renderers and shared/family styles live separately
under `core.ui.page`. No renderer looks up a target, submits an effect, or changes navigation.

| Decision or friction | Why it exists | Status / future direction |
| --- | --- | --- |
| Frozen legacy inbox | Unmigrated Device, configuration and sequencer bodies still call the inherited manager. The shell converts calls into bounded sequenced values, and core admits them through the parameter-restoration barrier. | Retained compatibility mechanism, capacity 64. Remove callers as those bodies migrate; do not add new product behavior through it. |
| Captured legacy returns | Device hold-return and delayed Device navigation formerly depended on mutable mode-manager state. They now capture a value-only page reference or origin revision/token. | Generic lifecycle protection; arbitrary new core IDs can return through legacy pages. No stable catalogue of new pages. |
| Legacy post-selection notifications | Some old commands read the selected page immediately after requesting it. Notifications now run after the corresponding request is acknowledged. | Bounded parent-owned continuations for unchanged legacy notifications. Acknowledgement here means core navigation admission, not Bitwig parameter or playback completion. |
| Typed family presentations | Frame, Accent, transport settings, Macro, Track, Master, global mixer and footer have their own immutable data and pure renderers, sharing measured styles and drawing primitives. | Retained deliberate family boundaries. Avoid a universal configurable UI schema; existing visual output is characterized against the previous build. |
| One-time parent contract change | API 46 adds full page projection and the legacy request inbox; checkpoint schema 6 saves typed navigation. | Requires one shell install/restart. Later page definitions, navigation and styling within the footprint reload in core. Schema-5 state is not replayed across this parent API change. |
| Full Master snapshot requested globally | Core observes real DAW Master selection to preserve existing page-entry behavior formerly implemented by shell observers. | Bounded existing snapshot, no new Bitwig proxy. A smaller generic selected-channel snapshot could reduce sampling if measurement justifies it. |
| Local state vs host state | Core page changes no longer wait for a mode proxy to echo them. Parameter values, selected targets, native note routing and playback still require authoritative host read-back. | Removes synthetic page-acknowledgement workarounds without weakening real host barriers. |

New regressions cover FIFO admission when a parameter-restoration release and a new legacy page
request arrive in the same sample, and preserving a restored page while Master was already selected
before reload. The first prevents a later request from skipping an earlier acknowledgement; the
second prevents reload from manufacturing a new selection edge.

The final package passes 932 tests and the documented `202arp` page/release/reload smoke passes.
Mapped-macro writes remain pending because the project has no named macros and macOS is locked.
Physical learned-MIDI validation remains separate from debugger-driven controller input. See
`core-page-ownership-live-smoke.md` for exact scope and build identity.

The page-ownership finishing review reproduced an inherited Stop-owner mismatch on VS global mixer
pages, after the analogous Macro assembly was corrected. The structural correction is one owner
for the physical Stop gesture across all page/grid compositions, rather than a separate flag per
background. It also found real Browser and fault-recovery protocol gaps; those were corrected and re-reviewed, rather than retained as shortcuts. See `core-page-ownership-review.md` for evidence and final status.

The recovery correction uses a parent-owned monotonic retired request prefix rather than trying to
recover stream sequencing from a possibly rejected child checkpoint. This prefix is lifecycle
metadata and remains available even when the pending-request list is unsubscribed; it requires no
Bitwig sampling. This is an explicit exception to domain-empty publication. With no healthy core,
legacy callbacks cannot enqueue orphaned work. Healthy generation changes and faults retire delayed
old callbacks. The semantic page checkpoint and the transport’s retired prefix have distinct owners.

### Original-view release rule

Elliot requested one shared mechanism so a release goes to the view that received its press. The
compiler previously chose receivers from the currently visible composition for every edge; keeping
some shared view objects did not establish that guarantee. `CorePageGestureCaptureTest` reproduces
the missing Frame continuation before the correction (one expected failure in two tests).

The new core input-lifetime work captures edge receivers once, keeps offscreen interaction state
without rendering its old page, and retires it after release/deferred dispatch. Exact touch leases
may continue across a legacy page only when the target was already applied and the parent router
still owns that exact exclusive gesture in the same generation. This is a bounded lifecycle
permission, not permission to claim fresh legacy inputs. Initial parent mechanism tests: 64 passed.

Continuous motion remains a separate boundary: the shell currently freezes related strip motion,
but does not generally pair every encoder touch with relative turns or every pad with pressure.
This follow-up does not claim those relationships are solved. Extending them requires a reusable
physical relationship declaration plus exact target/binding fences, rather than per-button fixes.

Two integration failures reinforced the shared ownership boundary. An offscreen Frame release
initially lost its declared data subscription in the result carrying its effect, so the parent
correctly rejected it. The router now retains effect requirements through that result only. The
first full-core run also caught duplicate reconciliation within one event affecting drum-fill
arming; reconciliation must run once per view identity per event, including page activation. These
are corrected centrally, without special Frame or fill branches. Disposal is the cancellation
boundary for retained actions; a separate public queued-action cancellation API is not part of this
change.

### Browser compatibility observation

The old synchronous Browser observer made native Browser activity and the projected Browser page
appear interchangeable. Once core owns asynchronous admission, they can differ. Existing frozen
Master-touch, tempo-knob, Select and Browse-light Browser predicates now read `IBrowser.isActive()`
where they previously inferred native activity from the page manager. The guards/behavior stay the
same; this is an observation correction, not a new stable semantic branch. Browser navigation and
return ownership remain in core. Tests distinguish native-open/pre-projection from native-closed/
stale-projection, rather than forcing the host and controller page to change together.

### API 46 live harness findings

- The original-view Frame release passed through routed input, stable application and later native
  MIX/EDIT read-back while Track remained visible. The generic owner mechanism needed no Frame
  exception. A held encoder also deferred a real core replacement until release, without restarting
  Bitwig. This is evidence for the existing input boundary, not a general asynchronous drain.
- A page-tour assertion initially discarded numeric display text: the trace parser read a string
  such as `-10.0` as a number. The actual scene and framebuffer were correct. The harness now retains
  numeric text and still compares it with authoritative host display values. A structured typed
  trace format would remove this string-parser ambiguity; no renderer change was made.
- `202arp` has no named project remotes. Its macro write test stopped before input. GUI mapping was
  unavailable because macOS was locked; an attempt to open the previously mapped scratch project
  had not changed the active project. This is a test-environment limit, not a missing stable feature.

- The final restoration audit caught a precision shortcut: controller pan `512` represents both
  initial raw `0.5` and the quantized inverse-step result `0.5004887585532747`. The test now checks raw
  selected pan and displayed host text as well. Existing native pan reset restored the originally
  observed center exactly, confirmed by later read-back. A reusable test transaction should capture
  and restore normalized host baselines instead of assuming opposite encoder steps are lossless.
  This finding concerns the smoke harness; it is not evidence that Snapback restoration is broken.
