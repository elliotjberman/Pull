# Core page ownership finishing review

Scope: API 46 / checkpoint 6 changes from `9f0d575f`. Two independent arch-nemesis reviews ran
against the implementation and tests. The architecture reviewer used isolated production probes;
the code-size reviewer measured tracked and untracked code, excluding generated output.

## Architecture findings and corrections

| Finding | Evidence | Correction / verification status |
| --- | --- | --- |
| P1 — inbox recovery depends on a discarded core checkpoint | After quarantine, a new core starts acknowledgement at zero while the shell sequence may be higher. A legacy callback with no healthy consumer can also prevent replacement from becoming idle. | Implemented: parent-owned monotonic retired prefix, explicit consumer lifecycle, and fresh-core bootstrap independent of checkpoint. Thirteen core and 94 shell tests pass, including actual runtime quarantine, incompatible/thrown checkpoints, callbacks without a healthy consumer, successful replacement and later navigation. Architecture re-review accepted; complete final package/live check remains pending. |
| P1 — Browser closure lost before page admission | Existing observer queues Browser entry on active, then checks only projected page on inactive. If Snapback delays entry, closure is discarded and the inactive Browser opens afterward. | Implemented: raw Browser lifecycle observation with core-owned page policy and exact temporary ownership. Focused runs passed 108 tests across Browser/Snapback/Shift/manager/bridge and compatibility guards; late closure cannot dismiss a newer page. |
| P1 — page footer and Session grid use different Stop owners | VS Volume/Pan/Send Stop+track stops the requested track, then plain Stop release also stops the selected track. Reproduced through the production core. This defect was inherited from `9f0d575f`, not introduced here. | One physical Stop gesture now has one owner shared by all pages and grids. Added a 26-combination full-Session/VS × Track/Macro/Volume/Pan/Accent/eight-Send regression, including page replacement before Stop release. All 430 core tests pass after this correction. Architecture re-review accepted; complete final package/live check remains pending. |

Earlier integration fixes remain covered: FIFO dispatch of released requests before newly offered
ones, restoring a page without manufacturing a Master selection edge, and projecting the correct
normal/VS Macro footer. Reentrant shell projection publishes ownership before foreign callbacks;
fault invalidation must skip activation of a superseded destination.

## Independent code-size review

Verdict: the expansion is justified by typed data/style/render separation and explicit lifecycle
ownership. The review identified approximately 68 removable production lines, without reducing
behavioral tests:

- Delete the three obsolete workspace utility shells; `ControllerPages` now contains their fixed
  grid declarations and bank/name constants.
- Remove redundant `ControllerPageTransitions.Request.closed`; membership in the one current
  request is already the authoritative validity test.

Both reductions are applied and the 430-test core gate passes. Formatting-only CRLF churn in old shell
files has also been removed. The reviewer found no credible large renderer consolidation that would
preserve the intentionally different layouts and formatting without adding a configuration system.

## Gate status

The initial full package passed 890 tests before the finishing corrections. Follow-up lifecycle
checks passed separately. A new complete deprecation-enabled package, architecture re-review, and
exact-build `202arp` live smoke are required before claiming this refactor verified.

## User-requested original-view release primitive

The user identified the broader gap behind the Stop discussion: a physical release must go to the
view that received its press. The shell already captures route disposition and core generation;
`CompiledWorkspace` still chose the currently selected view on every edge. A retained Java view
instance by itself did not solve that routing boundary.

The new core `InputGestureRouter` captures original edge receivers/action
owners and retain their nonvisual continuation requirements. The before-fix real-core Frame test
fails as expected after its page departs. Parameter touch continuation has one mechanical parent
permission: exact already-applied target, still-held exclusive edge, and matching active generation.
It keeps no old routes and allows no fresh legacy acquisition. Its 64 focused tests passed and the
architecture reviewer accepted that boundary. The actual Frame continuation also passes the real
parent result validator; subscriptions needed by a release effect survive in that same result,
then disappear on the next tick. The 42-test parent-runtime gate passes. Complete package and live
proof remain pending.

The independent size review accepted the router's distinct physical, deferred-action and departing
view lifetimes. Removing the obsolete global touch-release observer and the duplicate touch DTO
path eliminated approximately 75–80 production lines. Active and retained views now expose the same
nonvisual touch accessor. No further credible behavior-preserving reduction was identified.

The full core regression run exposed duplicate reconciliation during one event, affecting existing
drum-fill arming. The correction must deduplicate by view identity within that event, including
activation, and preserve the existing regression unchanged. Disposal remains the cancellation
boundary; this change does not introduce an independent queued-action cancellation API.

This does not generalize all continuous motion. Related strip motion already has a parent capture;
encoder TOUCH→RELATIVE and PAD→pressure transport relationships remain documented follow-up scope.
Stop-specific chord consumption remains product state; delivery of original-view releases belongs
to the generic router rather than that flag.
