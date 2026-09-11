# Retained track cursor pool

Working source implementation: Core API 56 / Bitwig API 25. A new shell installation and Bitwig
restart are required. The pool is integrated with named selected-track and visible-bank Volume/Pan
parameters, plus the Drum fill scanner and launch actuators. This is not an installed-capability or
live-validation claim; [ARCH](../../ARCH.md) remains the current inventory.

## Resources and ownership

| Resource | Bound and scope |
| --- | --- |
| Discovery bank | 64 flat project tracks at offset zero, independent of UI selection and Session paging. API 25 specifies nested, effect and master coverage; live coverage remains to be characterized. |
| Track cursors | 64 initialization-owned, individually named cursors; 55 mix resources, one eight-scene scanner, eight single-scene launch resources. |
| Mix profile | Retained volume and pan parameters. Selected and visible aliases for a track share its UUID assignment. Discovery parameters independently confirm acquisition values. |
| Clip profiles | Separate scanner and launcher slot windows. The launch resource is retained until the existing playback read-back barrier permits retirement. |
| Device/page and send profiles | Not installed by this change. Track retention alone cannot establish child mapping identity. |

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

The remaining custom parameter recipes serve send destinations and Device/chain/layer remotes.
Send reorder/remapping can change a child parameter without changing its track UUID. Device remotes
need a pinned device plus an independent page and verified replacement/remapping fences. Their
frozen control family also includes touches, modifiers, hierarchy navigation and feedback. Removing
those guards on the strength of track retention would be incorrect; these are explicit next adoption
steps, not completed migrations or a fallback pool implementation.

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

First live smoke remains required: matched shell/core startup, selected Volume/Pan after Session
paging with authoritative parameter and controller output, routed fill press/return with later playback
and retirement, collapsed/nested groups, effect/master coverage, rename/duplicate/reorder/delete/undo,
64/65 targets, project/core replacement, and realistic initialization/subscribed sampling costs.
Follow [TESTING](../../TESTING.md), checkpoint before restart, and retain the live lease through the
complete exact-build smoke. Offline fakes do not prove Bitwig runtime proxy behavior or sound.

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
