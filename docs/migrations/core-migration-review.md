# Migration finishing review

Scope: `a905be84..a9fc6961`, including all additions that were initially untracked. Two independent
reviewers examined the same code without seeing each other's conclusions. They made no edits and
did not rerun Maven. The reviewed checkpoint passed 823 offline tests; live validation is pending.
This review covers the implemented slices, not completion of the remaining migration inventory.

## Architecture findings, before corrections

| ID | Severity | Failure and invariant | Required correction | Current status |
| --- | --- | --- | --- | --- |
| A1 | P1 | Master, Automation and Metronome can emit a deferred mode effect after its captured layout becomes stale. Bridge preparation rejects it and RuntimeManager quarantines the active core. An existing Master test positively asserted this invalid sequence. | Revalidate the full frozen origin immediately before emission; cancel stale intent without retargeting. Exercise the production bridge/runtime boundary. | Resolved in independent re-review; focused and full gates pass. |
| A2 | P1 | ParameterTargetHost refresh removes a touch when ordinary write eligibility changes, even when its exact old actuator remains addressable. Later cleanup cannot release the forgotten touch. Tests released before refresh and masked production ordering. | Release through the existing addressability fence before retiring the lease. Test refresh-before-cleanup on selection/bank changes and reject cleanup through truly rebound proxies. | Resolved in independent re-review; production-order host and full gates pass. |
| A3 | P1 | Accent's five-second timeout retires an acknowledged long hold. Releasing after editing velocity for more than five seconds cannot return from the page. | Separate submission, acknowledgement and physical lifetime; only missing acknowledgement expires. | Resolved in independent re-review; focused and full gates pass. |
| A4 | P2 | Independent pending gestures all claim the manager's single temporary slot. Accent BEGIN/LONG/END/BEGIN/LONG before read-back emits two entries; the older release closes the page during the newer hold. Master and Automation have equivalent ownership. | One bounded temporary-slot owner with explicit per-control return policy and supersession. Test repeated and overlapping gestures. Preserve Metronome's latch and Track/Mix's explicit prior-page semantics. | Resolved in independent re-review; shared slot, cross-control and actual Snapback batch regressions pass. |

The stronger conjecture that duplicate RESTORE effects necessarily unwind multiple pages was **not
established**: shell apply-time layout checks reject subsequent stale operations. The demonstrated
older-release/newer-hold race is sufficient evidence for A4.

The three page findings share an architectural cause: submission, acknowledgement, physical hold
and return obligation were modeled independently in each control despite one native slot. The touch
cleanup issue has a different, smaller cause: current eligibility was conflated with addressability.
Neither correction requires the separately parked general reload-quiescence redesign.

### Initial architecture scorecard

| Concern | Status | Evidence |
| --- | --- | --- |
| Semantic intent resolution | Partial | Frozen intents/immediate consumption improved; stale mode emission still quarantines core. |
| Authoritative state | Partial | Parameter joins and toggle acknowledgement improved; temporary transitions lack one owner. |
| Ownership and dependency direction | Partial | Policy moved to core and stable adapters are inert; touch cleanup breaks its own distinction. |
| Reload and lifecycle fencing | Partial | Retained views/generation checks exist; bounded gesture/resource lifetimes need the named fixes. |
| API compatibility | Resolved | Core API 45, checkpoint schema 5, capability revisions and Bitwig API 25 are distinguished. |
| Test realism | Partial | Async coverage is broad; cleanup ordering and stale-effect expectations masked failures. |
| Legacy deletion | Partial | Many commands/modes/providers deleted; remaining families remain explicit migration debt. |

Initial recommendation: **Do not merge.** Fix A1–A4, verify the boundary regressions, run live smoke,
and refresh stale inventory statements. A later review must retain this findings ledger.

## Independent line-count audit

Verdict: **Shorter**. Measured production `+8,819/-3,253`, tests `+8,384/-185`, other
`+2,680/-211`; no generated or vendored additions identified. Production net by area: API +1,299,
core +4,265, shell −69, tooling +71. The reviewer identified roughly 55 credible net deletions.

| ID | Opportunity | Required behavior proof | Current status |
| --- | --- | --- | --- |
| LC1 | Consolidate identical project/device remote-target reconciliation, retaining explicit bank loops and a live owner supplier. | Blank owner rejection, short/null banks, project/device isolation, page/owner rebinding, unchanged proxy reuse, stale retained lease rejection. | Shared implementation and live-owner regression pass the focused host gate. |
| LC2 | Remove unused GlobalMixer render/alignment overloads and volume/pan/project-global pass-through wrappers. | Whole-repository reference search, compilation and existing current-bank/transport tests. | Removed; reference search and focused compilation/tests pass. |

Two larger opportunities are tracked without speculative line savings: reuse DeferredButtonAdmission
for Master, and unify the old special Master/Track composition machinery with the newer page registry.
The latter must preserve exact retained views, project-navigation Master leases, strip routing and
workspace precedence. It is not a safe cosmetic deletion.

The reviewer judged most net expansion justified by explicit observations, typed effects, leases,
asynchronous barriers and policy formerly hidden in inheritance. Do not reduce code by collapsing
distinct temporary-page semantics, weakening owner joins, deleting characterization tests or forcing
different mixer renderers through a configurable universal renderer.

## Reverification

Implemented A1 across the four global controls and the remaining deferred mixer-menu producer.
CurrentTrackFooter's device-page entry now checks the full frozen layout, extending its existing
generation check. Track/Mix already guards its entry and deliberately selects an explicit prior
page on return; it does not use the native temporary slot. No new stable policy or API capability
was needed. Master now also reuses DeferredButtonAdmission.

Focused gates passed: 158 core and 99 shell for page transitions, including the actual child-loaded
core, RuntimeManager and production BoundedControllerBridge. The boundary test cancels six stale
paths and proves a later fresh Master action still works. An additional 34 core and 24 shell tests
pass for mixer/footer stale origins, selected/current-bank cleanup and shared remote reconciliation.
Logs are retained under `target/migration-evidence/` in this durable worktree.

Master `5537271f` was integrated. The complete deprecation-enabled package passed **838 tests**
(398 core, 11 publication, 429 shell) at 20:06:20 EDT on 2026-09-05. Six deprecation warnings are
confined to unchanged TransportImpl. An initial full attempt exposed one additional Send test
that expected a stale effect; it now distinguishes frozen pagination intent from layout cancellation.

### Bounded architecture re-review

A1, A2, A3 and A4 are **Resolved**. No new material findings. The reviewer accepted one shared
transition owner as the structural correction while preserving per-button latch/modifier behavior.
Track/Mix's explicit prior-page return remains separate. Updated scorecard:

| Concern | Status | Evidence |
| --- | --- | --- |
| Semantic intent resolution | Resolved for reviewed slices | Stale frozen origins cancel; immediate consumption remains separate. |
| Authoritative state | Resolved for reviewed slices | Entry acknowledgement, physical hold and return ownership are distinct. |
| Ownership and dependency direction | Resolved for reviewed slices | One core slot owner; exact stable touch cleanup. |
| Reload and lifecycle fencing | Partial | Bounded defects resolved; general quiescence remains explicitly parked. |
| API compatibility | Resolved | Correction introduces no new API-45 or Bitwig-25 contract. |
| Test realism | Partial | Production boundaries and full package pass; native live validation remains. |
| Legacy deletion | Partial | Implemented slices deleted policy; remaining families/scaffolding remain documented. |

Architecture recommendation: **Merge with tracked debt**, conditional on the exact-build live gate.
The reviewer originally also named the full package gate; the parent subsequently confirmed it
passed. This is architecture readiness for the reviewed slices, not whole-migration completion.

### Bounded line-count re-review

Verdict: **Justified as written**; zero additional necessary reductions. At `25aa5b6f`:
production `+8,893/-3,316`, tests `+8,820/-186`, other `+2,968/-218` versus `a905be84`.
The later Send test correction and this reporting update change only tests/docs. LC1, LC2 and
Master admission duplication are resolved. Composition duplication is appropriately deferred.

The shared transition helper plus its four consumers/wiring adds **70 net production lines** versus
the pre-correction checkpoint. This is a correctness expansion, not a net reduction: one slot owner,
centralized origin/acknowledgement checks and independent physical lifetime earn those lines.
The reviewer advised against another generic button abstraction; remaining control-specific branches
represent real differences.

### Remaining gate

Live validation resumed on 2026-09-06: exact shell installation/core activation and representative
parameter, Drum-map and touch-strip checks succeeded. A generic debugger chord-release defect was
reproduced; the live gate remains partial until its correction and remaining checks are recorded in
`core-migration-live-smoke.md`. Do not infer untested native behavior from the offline tests.
The [shortcuts ledger](migration-shortcuts-and-friction.md) records retained architecture costs and
rejected shortcuts separately from the corrected defects.
