# Migration shortcuts and architectural friction

Requested by Elliot during the shell-to-core migration on 2026-09-05. This is a running ledger,
not a claim that the migration or live testing is finished. Keep resolved entries so the final
review can distinguish shortcuts we retained from defects we caught and removed.

## Retained compromises

| Item | What we did and why | Cost / architectural direction | Status |
| --- | --- | --- | --- |
| Registered inert mode adapters | Core-authored pages still name an installed `CorePageMode` and use the legacy mode manager's SELECT/TEMPORARY/RESTORE operations. This preserves integration with unmigrated controls while making the page's behavior reloadable. | Adding another named page can still require stable registration. A core-owned page lifecycle over a generic physical footprint would remove those per-page registrations after legacy consumers migrate. | Retained migration scaffolding; not the final view API. |
| Finite page/background combinations | `ControllerPageCompositions` precompiles pages over the existing Note, Drum, full Session, and VS Live backgrounds, retaining the exact view instances. Existing special Track/Master composition handling still coexists with it. | The finite combinations and alias registrations grow with both page and background families. A single composition model with independently retained grid, parameter page, and overlays would remove duplicate selection machinery. | Retained; bounded and tested, but review for simplification. |
| Raw legacy mode names and temporary-slot semantics | The bridge exposes active/previous/visible mode IDs and temporary occupancy. Core button gestures preserve the real manager's behavior instead of pretending it is an arbitrary stack. | Core recipes still understand legacy names and awkward nested-page behavior. A typed core navigation state with acknowledged page transitions could replace this after all callers migrate. | Retained compatibility surface. |
| Legacy page manager still has one temporary slot | Accent, Automation, Master/Frame and Metronome now share `ControllerPageTransitions`. It owns one outstanding page intent and return debt; the button views retain their distinct gesture policies. Track/Mix still returns to an explicit prior page. | This removes repeated acknowledgement machinery and the reproduced old-release/new-hold race. The native one-slot model still constrains nested pages; a fully core-owned page lifecycle remains the longer-term direction. | Shared owner and regression coverage pass the focused core and real-runtime gates; re-review and full 838-test gate passed; live pending. |
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
