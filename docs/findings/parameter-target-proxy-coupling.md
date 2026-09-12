---
status: active
created: 2026-08-05
scope: controller-target-model
remove_when: remaining Device families have proved exact target identities and cancellation-safe actuators
---

# Remaining parameter target identity work

The shared [interaction lifecycle](../interaction-lifecycle.md) is integrated in working Core API 49.
It cancels when the active target/binding disappears, suppresses the physical tail, and observes
exact resource retirement. This finding now concerns the remaining host adapters, particularly the
Device family. The [retained cursor pool](../track-cursor-pool/README.md) supplies named Volume/Pan
actuators and opaque Device remote-page owners. Its scoped matched live gate passed, including nested
acquisition, deletion/undo and stale-tail suppression. Chain/layer identity, unobservable remote
remapping and persistent controller overrides remain unresolved.

## What the integration resolves

Core owns control-to-target bindings and cancellation. Migrated parameter pages use named bounded
banks, independent of physical encoders. Shell target references fence domain, owner, page, role and
generation; wrappers and display names are not identities. Mutable actuators are checked during
preparation and again during application.

These parameter identity and cleanup guarantees cover ordinary native mix roles and Device/page/slot
addresses without persistent controller manual overrides. They do not universally identify the
effective actuator after Bitwig applies an override.

Project remotes, selected Track mix/sends, global Volume/Pan/eight Send columns, and Master/Cue join
parameter identity to separately subscribed project/track state. Contradictory joins cannot render,
start a touch/reset, mutate or enter Snapback capture. This handles independently sampled domains;
it does not claim atomic publication across them.

`ParameterBridgeSnapshot.touchLeases` reports shell-owned touch resources. A page losing its binding
removes its desired touch; the lifecycle waits for a later sample without that exact lease before
reusing the target. Cleanup subscriptions do not keep the old page alive.

The shell distinguishes new-write eligibility from cleanup addressability. It releases an old touch
only through its still-exact actuator. Named Volume/Pan now retain their exact track UUID independently of Session paging; the old touch
can be released after its visible binding moves. Device remotes retain an exact pinned child and
independent page for outgoing cleanup after source navigation; their display joins the same opaque
owner/page/slot as their actions. Sends and chain/layer children retain existing target fences. If an external cursor change has already rebound a nonretained proxy,
it drops/reports the lease instead of touching the replacement. Retirement is not a DAW write ACK.
Snapback still waits for observed restoration, with the separate
[precision limitation](snapback-v1-limitations.md#restoration-precision).

## What remains

- Persistent controller manual overrides are distinct from remote-page editing reported by
  `RemoteControl.isBeingMapped()`. They can apply to a parameter proxy without a native hardware
  binding, so keeping a retained section private does not establish immunity. An override can change
  the effective actuator without changing its native track or Device/page/slot address. API 25 exposes
  no public override-identity check; matching names and values do not exclude an override. This is a
  separate limit on the ordinary native parameter guarantees above, including mix parameters.
- The Device remote slice still needs live characterization of preserved user-pinned devices across
  tracks, arbitrary nested topology, replacement and remapping. `CursorDevice.channel()` is its creation
  context, not an owning-track identity. Retained child equality and observed invalidation revisions
  passed the documented ordinary nested acquisition and deletion/undo checks; same-named remaps without an observable event
  remain outside a proved exact-cleanup contract.
- Remaining chain/layer controls need verified owners and parameter roles; distinct Device menu
  navigation/lights remain frozen. No device UUID may be invented from name, slot or wrapper.
- Two proxies exposing the same semantic parameter are not generally deduplicated. Document exact
  aliasing guarantees for any new adapter before allowing shared target acquisition.
- The inherited `ACTIVE` bank remains frozen support for unmigrated stable parameter modes. Delete
  those callers as each complete family moves; new core views use named banks and declared targets.
- Cancellation cannot undo an external rebind that already made an old actuator unreachable. Any
  feature requiring guaranteed host restoration needs a proved directly addressable cleanup target.

Bitwig API 25 banks and cursors are bounded mutable proxy windows created during initialization.
Observing a channel identity does not itself supply an actuator addressing that channel. A Java
parameter wrapper surviving navigation is likewise not proof that its musical target survived.
These are host constraints; distributing lifetime policy across pages is not required by them.

## Next adapter acceptance criteria

For each remaining family, record the installed capacity, selection scope, authoritative identity
and generation, subscribed read-back, primitive effects, and exact cleanup-addressability rule.
Exercise the real routed path with proxy rebinding between prepare/apply, target loss during a hold,
and later resource retirement. Submitted commands and later host observations must remain separate.

Delete this finding when the remaining families meet those criteria and their host constraints live
in permanent architecture documentation. A new pinned pool or offscreen-retention policy is not a
removal requirement. Live tests still require a matched installed shell/core and the singleton lease.
