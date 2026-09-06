# Migration finishing review

Scope: `a905be84..a9fc6961`, including all additions that were initially untracked. Two independent
reviewers examined the same code without seeing each other's conclusions. They made no edits and
did not rerun Maven. The reviewed checkpoint passed 823 offline tests; live validation is pending.
This review covers the implemented slices, not completion of the remaining migration inventory.

## Architecture findings, before corrections

| ID | Severity | Failure and invariant | Required correction | Current status |
| --- | --- | --- | --- | --- |
| A1 | P1 | Master, Automation and Metronome can emit a deferred mode effect after its captured layout becomes stale. Bridge preparation rejects it and RuntimeManager quarantines the active core. An existing Master test positively asserted this invalid sequence. | Revalidate the full frozen origin immediately before emission; cancel stale intent without retargeting. Exercise the production bridge/runtime boundary. | Implemented; focused regressions pass; independent re-review pending. |
| A2 | P1 | ParameterTargetHost refresh removes a touch when ordinary write eligibility changes, even when its exact old actuator remains addressable. Later cleanup cannot release the forgotten touch. Tests released before refresh and masked production ordering. | Release through the existing addressability fence before retiring the lease. Test refresh-before-cleanup on selection/bank changes and reject cleanup through truly rebound proxies. | Implemented; production-order host regressions pass; independent re-review pending. |
| A3 | P1 | Accent's five-second timeout retires an acknowledged long hold. Releasing after editing velocity for more than five seconds cannot return from the page. | Separate submission, acknowledgement and physical lifetime; only missing acknowledgement expires. | Implemented; focused regressions pass; independent re-review pending. |
| A4 | P2 | Independent pending gestures all claim the manager's single temporary slot. Accent BEGIN/LONG/END/BEGIN/LONG before read-back emits two entries; the older release closes the page during the newer hold. Master and Automation have equivalent ownership. | One bounded temporary-slot owner with explicit per-control return policy and supersession. Test repeated and overlapping gestures. Preserve Metronome's latch and Track/Mix's explicit prior-page semantics. | Shared ControllerPageTransitions implemented; repeated/cross-control and actual Snapback batch regressions pass; independent re-review pending. |

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

Pending: full package gate, independent re-review, master integration and exact-build live evidence.
The [shortcuts ledger](migration-shortcuts-and-friction.md) records retained architecture costs and
rejected shortcuts separately from the corrected defects.
