# Migration shortcuts and review ledger

Requested by Elliot. This concise record retains accepted compromises, rejected shortcuts and the
resolved findings from the two independent finishing reviews. Current architecture is in
[ARCH](../../ARCH.md), remaining scope in [the roadmap](../reloadable-core-migration-roadmap.md),
and exact-build evidence in [the smoke record](core-page-ownership-live-smoke.md).

## Retained compromises and explicit choices

| Choice | Boundary / removal or improvement |
| --- | --- |
| Frozen legacy compatibility | One inert core-page footprint replaces per-page shell registrations. Legacy aliases and a 64-request FIFO inbox remain only for unmigrated bodies; remove their callers with each complete migration. New core pages need no stable alias. |
| One temporary page | Core owns one replaceable slot and exact return token. Master/Automation/Accent/Metronome retain distinct latch/return rules; Track/Mix retains its explicit prior-page return. A stack or normalized gesture vocabulary requires a product decision. |
| Finite composition | Typed pages compile over retained Note/Drum/full Session/VS backgrounds. Fixed combinations are deliberate conflict validation, not arbitrary callback remapping. |
| Named proxy windows | Parameter slots are bounded movable proxies, not durable project-wide identities or a pinned actuator pool. Distinguish new-write eligibility from old exact addressability; retain the [target finding](../findings/parameter-target-proxy-coupling.md). |
| Mixed snapshot epochs | Parameter-only reconciliation can precede track/project sampling. Explicit domain/owner/page/role joins prevent mismatched writes and feedback. A coherent typed publication model may reduce repeated joins. |
| Frozen facets/output | Remaining adapters preserve old behavior only; [bidirectional facet/claim validation](../findings/stable-facet-claim-coupling.md) remains incomplete. |
| Compatibility constructors | Typed empty defaults ease source migration but can hide missing fixture state. Required production identities fail closed; remove unused overloads after callers migrate. No old binary compatibility is promised. |
| BEGIN admission | A button that may change page on LONG enters the parameter barrier at BEGIN; short taps can therefore wait too. Preserve early modifier consumption separately from deferred host effects. |
| Summed relative input | Packet order is lost: at a clamp, `+1,-1` can differ from net zero. Accent follows the installed sum contract; Crossfade/MIDI-channel callback-count behavior is still unresolved. |
| Preserved mixer quirks | Shift-arrow feedback reads bank-scroll flags while its action swaps the cursor. Selected Track and global mixer send menus have different pagination thresholds. Send widget type still derives from name: Volume fader, Pan/Panning slider, otherwise ring. Change these only as explicit product behavior. |
| Interaction lifetime | The later [API 47 integration](../interaction-lifecycle.md) replaces offscreen retention with central cancellation and touch→motion / pad→pressure capture. Deferred cleanup belongs to the exact resolved intent. Its remaining adapter constraints and validation are recorded there. |
| Snapback precision | Baselines/setters still use controller resolution for inherited paths. An equal opposite encoder step does not prove raw restoration; retain the [precision finding](../findings/snapback-v1-limitations.md). |

Master deliberately replaces the page over the exact selected composition; it no longer selects
the Master track and restores a track by index. A stale deferred origin is canceled, and an older
return token cannot revive when a similarly named page reappears. Newer unsubmitted global page
intent may supersede older intent; that is not a generic coalescing rule for host writes.

The old synchronous Browser observer made native activity and page projection appear equivalent.
Existing frozen Master-touch, tempo-knob, Select and Browse-light predicates now observe raw
`IBrowser.isActive()`. This preserves their Browser guard while core alone owns asynchronous page
admission and return.

## Rejected shortcuts

- No stable fallback or new stable semantic branch for an exclusively migrated control.
- No invented device UUID from name/slot/wrapper identity, and no inference of inert behavior from
  a mode callback when permanent hardware parameter bindings can bypass it.
- No Session release acknowledgement inferred from void return, unchanged playback, a timer or
  controller flush; see [the bounded decision](session-launcher-location-design.md).
- No arbitrary high-rate resampling, fake host readiness tick, or synchronous test acknowledgement.
- No global async drain hidden in this work: the user explicitly
  [parked quiescence](../findings/core-reload-quiescence.md).
- No relabeling generated display output as physical LED/audio proof, or debugger input as native
  learned-MIDI proof. Keep restoration precision and project identity checks separate from requests.

## Resolved review findings

All entries below were resolved in the reviewed production source `11e33477`. They preserve the
prior-findings ledger without retaining chronological test/review diaries.

| Finding | Status and structural correction |
| --- | --- |
| A1 / P1: stale deferred page effect quarantines core | Resolved: validate the whole frozen origin before emission; equivalent global mixer/footer paths covered. |
| A2 / P1: refresh forgets a still-addressable old touch | Resolved: addressability-fenced cleanup before lease retirement; rebind never releases the replacement target. |
| A3 / P1: acknowledgement timeout ends an acknowledged hold | Resolved: physical lifetime is independent from missing-ack expiry. |
| A4 / P2: older return closes a newer temporary page | Resolved: one exact temporary owner shared across controls, preserving their distinct product rules. |
| P1: inbox recovery depends on lost checkpoint | Resolved: parent retired prefix, consumer epoch and inert faulted callbacks; new core rebases independently of checkpoint. |
| P2: candidate starts from stale inbox metadata | Resolved: wait for the next normal snapshot containing the acknowledged prefix. |
| P1: Browser closes before delayed entry | Resolved: raw generation invalidates stale opens; exact token closes/reloads safely. |
| P1: footer and Session grid disagree about Stop | Resolved: one shared chord state across pages/grids; 26 combinations include page change before END. This defect was inherited. |
| P1: page replacement loses original release | Resolved: bounded generic receiver/action capture, deferred owner lifetime, current-only visuals and exact nonvisual touches; global release shim deleted. |
| P1: final END drops its effect dependencies | Resolved: retain the emitting owner's subscriptions/banks through the submission result; real parent Frame preparation covered. |
| Reconciliation advances fill state twice per event | Resolved: deterministic identity dedup across activation and deferred dispatch; original fill regression remains unchanged. |
| Rejected Shift/Scales request starts restoration | Resolved: conditional invalidation plus queue-wide FIFO preserves normal Shift and deferred Scales→Layout ordering. |
| Lost inherited navigation / premature consumption | Resolved: complete arrow action/feedback migration and immediate bounded consumption separated from deferred effects. |
| Reentrant note-route cleanup stack overflow | Resolved: retire ownership before foreign cleanup callbacks; correlated selection/return regression passes on API 46. |
| LC1/LC2 and page-size review | Resolved: shared remote reconciliation; obsolete wrappers/workspace utilities removed; one touch accessor and action validation path. Reviews rejected a universal configurable renderer because page geometry/formatting differ. |

## Review outcome and validation boundary

| Concern | Reviewed result |
| --- | --- |
| Semantic intent and authority | Original owner/action captured; later host read-back and exact target fences preserved. |
| Ownership and reload | Core pages/presentation; shell mechanism only; retired-prefix, generation and physical lifetime agree. |
| Compatibility | API 46 / checkpoint 6 distinguished from Bitwig API 25. |
| Tests | Production checkpoint passed 932 tests and scoped live checks; cleanup passes 826 behavior/boundary tests and offline renderer parity. |
| Remaining debt | Frozen families and active findings remain; no unresolved P0/P1 in the reviewed scope. |

The cleanup's independent architecture review found no P0/P1/P2 issue or lost required behavioral
coverage. Its stale status paragraph and the size review's remaining isolated-constructor finding
are corrected. Recommendation remains **merge with tracked debt**. The cleanup is not installed or
live tested; the physical Push is disconnected and the installed build remains unchanged.
