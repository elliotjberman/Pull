# Retained track cursor pool

Working implementation: Core API 57 / Bitwig API 25, installed and smoke-tested on September 12, 2026.
Upgrading from API 56 requires a matched shell installation and Bitwig restart.
The pool is integrated with named selected-track and visible-bank Volume/Pan
parameters, the Drum fill scanner and launch actuators, and the Device page’s eight remote encoders
and parameter display. [ARCH](../../ARCH.md) records working ownership; the scoped live evidence below
records the tested pool checkpoint and Device expansion separately.

## Resources and ownership

| Resource | Bound and scope |
| --- | --- |
| Discovery bank | 64 flat project tracks at offset zero, independent of UI selection and Session paging. Effect/master discovery was observed live; nested/collapsed groups and overflow still require live characterization. |
| Track cursors | 64 initialization-owned, individually named cursors; 53 mix resources, two device/page resources, one eight-scene scanner, eight single-scene launch resources. |
| Mix profile | Retained volume and pan parameters. Selected and visible aliases for a track share its UUID assignment. Discovery parameters independently confirm acquisition values. |
| Clip profiles | Separate scanner and launcher slot windows. The launch resource is retained until the existing playback read-back barrier permits retirement. |
| Device/page profile | Two pinned child devices with separate named eight-remote pages: one current page and bounded outgoing cleanup. Device equality and opaque generations provide child authority. |
| Send and chain/layer profiles | Not migrated. Track retention alone cannot establish child mapping identity. |

`RetainedCursorPool` is a host-independent state machine; `RetainedCursorHost` provisions Bitwig
resources and executes assignments. Existing complete core bank/scan/binding requests drive each
consumer namespace. Shell allocation is mechanical first-free selection within a declared profile;
it does not choose tracks, fill names, controls, views or navigation policy. No Bitwig object crosses
into core.

The lookup key is the observed project generation and track UUID. Handles also include cursor slot
and monotonically increasing assignment generation. Names and positions are never target keys.
There is no arbitrary-UUID Bitwig fetch: a first acquisition needs a discovered target. Existing
retained targets remain available outside the discovery window while their exact cursor is valid.
The catalog reports total count and FULL/PARTIAL/PENDING coverage. In projects above 64 targets,
undiscovered UUIDs are unavailable, not deleted. Active/retiring cursors are never evicted to satisfy
new requests; profile exhaustion returns unavailable.

Each controller tick advances host observation time once. Getters, input handling, effect preparation
and command submission cannot acknowledge an acquisition. The initial catalog is sampled once;
after that, an unused pool stops high-rate discovery sampling. Occupied resources and pending demand
keep their required observations active. No complete selected-track model is duplicated per cursor.

## Acquisition, effects and cleanup

1. Reconcile complete per-consumer requests. Replaying an unchanged request never unpins or reselects
   its target.
2. At assignment, recheck the discovery proxy's UUID and project before submitting selection/pinning.
   A rebinding discovery proxy rejects acquisition without faulting core or consuming a slot.
3. Wait for later host observations of existence, pinning and UUID. Mix readiness additionally compares
   the complete parameter values with an independently observed, UUID-checked discovery target over
   two later controller samples. A UUID update alone cannot publish the previous track's values.
4. Every prepared operation rechecks the live handle at application. Observed loss, unexpected retarget,
   project change and resource reuse permanently invalidate old handles. Undo needs fresh acquisition.
5. Exact holds outlive removed desired assignments. Release submits a host command; it does not unlock
   an actuator. Retirement follows later authoritative playback read-back. Lost exact targets retire
   with a diagnostic rather than attempting cleanup on a replacement.

Project ownership uses the existing subscribed project identity. This pool does not establish a
stronger identity contract for indistinguishable copied/open projects. Core replacement replays
normal desired state after existing parent cleanup; it never adopts an old gesture. Exit remains
best-effort with no assumed post-exit callback window.

## Consumer cutover

The mix and Device parameter guarantees below cover ordinary native roles without persistent
controller manual overrides. These overrides are distinct from remote-page editing reported by
`RemoteControl.isBeingMapped()` and can apply without a native hardware binding. They can change a
proxy's effective actuator without changing its native track or Device/page/slot address. API 25
exposes no public override-identity check; matching names and values do not exclude an override.
Private retained proxies therefore do not provide universal parameter identity or cleanup guarantees.

Selected Volume/Pan now address the private selected UUID after Session paging moves that track off
screen. New editing still requires the same authoritative selected target/generation. Visible-bank
Volume/Pan cancel ordinary editing when their visible binding changes, while an outgoing touch or
indication can still be released through the exact retained track. Control Return's existing
selection cancellation and restoration precision remain unchanged.

The fill host no longer constructs, selects, unpins or repins its own track cursors. Its adapter only
reads/operates the shared provisioned clip slots. Pool readiness replaces duplicated track acquisition;
clip scene/content coherence, catalog generations, one active fill, replacement sequencing and exact
release leases remain feature-specific contracts. Session structural-mutation guards remain in place.
A retained track does not make a numbered launcher slot an immutable clip identity.

The Device page’s eight encoder turns and touches now use named retained remotes, with Shift,
Delete/reset, touch cancellation and automation cleanup handled by the shared core lifecycle. Its
parameter values and touch display use those same observed targets. The old Device provider, direct
touch/reset body and duplicate raw parameter sampling are removed. Distinct Device menu rows,
hierarchy navigation and lights remain frozen; Chains keeps its existing provider explicitly.

Bitwig allows one main remote page following Device selection. The pool observes the existing
framework page; each retained child creates its own named independent page. A child first selects
the exact source Device, waits for observed equality, then selects its independent
remote page and waits for coherent slots. The ordinary unoverridden semantic address is the retained Device,
unfiltered page index and slot. Remote-control equality is not usable for this check: the tested
Bitwig runtime compares separately allocated parameter-target wrappers and reports unequal even
for matching mapped controls. Slot names and values confirm observation delivery; they are not IDs.
Source device/page navigation cancels new editing while outgoing cleanup retains its own pinned
child and page. Observed disappearance, unpinning, page
changes and mapping invalidations revoke the old generation permanently. A same-named remap with
no observable invalidation is not an established exact-cleanup guarantee. The creation-channel UUID
is acquisition context, not an inferred owning-track identity for a user-pinned Device. Ordinary
nested acquisition passed live testing; preserved cross-track and nested pins remain unproved.

Remaining recipes serve send destinations and chain/layer remotes. Send reorder/remapping can change
a child parameter without changing its track UUID. Those guards and the distinct frozen navigation
controls remain until their complete target and interaction contracts migrate.

## Verification and next acceptance

The pool was tested independently before consumer integration. Deterministic tests advance cursor
assignment, profile-property delivery, parameter writes, scene scrolling and playback separately.
They cover stale acquisition values, no-churn replay, two independent holds, pending/retiring capacity,
64/65 coverage, deletion/undo, same-UUID project changes, prepare/apply races, exact outgoing cleanup,
fill release acknowledgement, and idle sampling. Existing routed core and bridge tests verify
parameter feedback still follows later host state.

Run the complete gate before installation:

```bash
mvn -o -Dmaven.compiler.showDeprecation=true package
```

The API 56 pool checkpoint `5bd61bfc62d994550c557616ebdd47f4c07bdfd1` passed the full
1,097-test package gate and a matched Bitwig smoke on September 10, 2026. Installed extension SHA-256:
`56f22a72f06e27738893bc79c1e0ab82551245db48261855182200deb3c6bed4`; active core:
`20260911T033852Z-79722d02e929eeb73158e09f3c40e61c` (SHA-256
`6b7ce36713ab01fb654ede511508fe374e81fbabfff19f733d4aad9b477920be`).

- Routed knob1 adjustment: same track/target read-back changed from 553 to 642 after 16.9 ms;
  Push then displayed −6.1 dB instead of −10.0 dB. Touch retirement followed END.
- Paging: bank offset 8 was observed with the old exact touch still retained; subsequent selection
  read-back retired that touch. The old physical END caused no replacement touch or parameter write.
- Fill: exact target 33 remained playing after routed END/release submission. A later snapshot on the
  same selected generation showed no launch-session target or active owner; transmitted pad13 changed
  from `F27E00` to `A76B22`. Separate bounded traces prove eventual retirement, not its exact instant.
- The private catalog observed 4, 15 and 16 targets as scratch projects changed, including effect and
  master targets. This does not establish collapsed-group or 64/65 live coverage.

Evidence is retained locally under `.live-cursor-pool/evidence`: volume trace
`1789097998-95987-17618`, paging `pool-dde20d8282fd`, and fill release/later traces
`pool-9b3de4ffe6d8` / `pool-c0f2e872c1b7`. Large drum snapshots exceeded the text cap; only intact
records are used for the claims above.

The API 57 integration passed all 1,118 package tests on September 12, 2026, with no
failures, errors, skips or deprecation warnings. The staged native tests cover partial/empty pages,
independent child navigation, later property delivery and outgoing cleanup. The real hardware-button
regression confirms an empty-slot Delete chord consumes the legacy deletion release. Debug client,
eight surface-server and isolated live-lock tests also pass.

The matched API 57 smoke used source checkpoint `857d02b4`, installed extension SHA-256
`a67e077d5886e31b9205d6d67e8e50e8991dad8c52f2fb92a6ad3ca1940c4761`, and active core
`20260912T165011Z-44796e20aba3777d2e857b0eaa37e725` (SHA-256
`83d7d5d8463852c209e016e6f41d3c23158ea12ae46eff9eb44aae32dff6851c`).

- A partial Arps page exposed only Gain in slot 7. Routed knob8 changed the same target from
  512 / 0.0 dB to 592 / +3.8 dB on a later recorded host sample, 59.3 ms after input. Transmitted
  Push frames showed the corresponding 0.0 and 3.8 values; the observed touch lease retired after END.
- Shift changed Gain to 688, then restored the captured 592 after release and later host read-back.
  Delete plus mapped touch reset it to 512; Delete plus an empty slot consumed the chord without
  changing the device or acquiring a parameter.
- Compressor+ Color→GR navigation retired the outgoing touch, temporarily removed ready parameters,
  and acquired a fresh owner. A later GR turn changed Threshold from −40.9 to −37.1 dB. Returning
  to Primary preserved the observed value under another fresh owner.
- A nested Drum Machine remote acquired and changed Output from 813 / 0.0 dB to 893 / +2.4 dB.
  Its transmitted Push frame showed 2.4. Deletion during a hold retired its touch; captured stale
  turns were inert both during reacquisition and after the replacement Bend parameter was ready.
  Undo acquired a fresh Drum Machine owner without reviving the old touch.
- Fill target 20 remained the exact launch-session target after routed release submission. A later
  snapshot 2.471 seconds after END had no session target or active owner; transmitted pad13 changed
  from `F27E00` to `A76B22`. This proves eventual retirement and output, not the exact barrier instant
  or an independent audio measurement of the restored base.

The local evidence directory contains exact package/reload/status records, original traces,
`device-smoke-analysis.md`, `api57-fill-smoke-analysis.md`, and transmitted PNGs. Main Device traces
are `pool-753867174075` (turn), `pool-300f7865a750` (Shift), `pool-922d82b5f0e0` (reset),
`pool-979518a5a2ab` (empty Delete), `pool-7dbaf5a257b0` (page change), and
`poolz-1789232367877883000` (replacement-ready stale tail). Fill traces are
`poolz-1789232473294992000`, `poolz-1789232473715956000`, and `poolz-1789232476334722000`.
Only intact rows support claims when serialization was capped. A pin attempt observed the existing
navigation release the source pin on track selection; it does not prove preserved cross-track pins.

Remaining live characterization includes preserved cross-track pins, arbitrary nested topology,
duplicate/replaced devices and remote remapping; broader pool cases include collapsed groups,
64/65 targets, track rename/reorder/delete/undo, pending project/core replacement, and initialization
and subscribed sampling costs. Deterministic tests cover the bounded lifecycle cases, but do not
prove those runtime proxy contracts. Follow [TESTING](../../TESTING.md), checkpoint before restart
and hold the live lease throughout each exact-build smoke.

## API reference and future child retention

Methods were checked against the local `extension-api-25-sources.jar`: flat
`ControllerHost.createTrackBank(int,int,int,boolean)`, named
`createCursorTrack(String,String,int,int,boolean)`, `CursorChannel.selectChannel(Channel)`,
`channelId()`, `exists()`, `isPinned()`, current parameter values, and launcher slot APIs. No deprecated
method is introduced.

The earlier design's device/clip direction remains valid: `PinnableCursorDevice`,
`PinnableCursorClip`, and the named three-argument `createCursorRemoteControlsPage` support retained
children and UI-independent remote pages. [TDBitwig's cursor documentation](https://derivative.ca/UserGuide/TDBitwig_User_Guide#Cursors)
provides prior art; it does not prove Pull's exact cleanup guarantees after deletion/replacement or
remapping. Provision each next child profile only with its complete tested consumer and explicit bounds.
