---
status: active
created: 2026-08-05
scope: controller-target-model
remove_when: remaining Device families have proved exact target identities and cancellation-safe actuators
---

# Remaining parameter target identity work

The shared [interaction lifecycle](../interaction-lifecycle.md) is integrated in working Core API 48.
It cancels when the active target/binding disappears, suppresses the physical tail, and observes
exact resource retirement. This finding now concerns the remaining host adapters, particularly the
Device family. It is not a proposal to retain offscreen editing or add pinned pools by default.

## What the integration resolves

Core owns control-to-target bindings and cancellation. Migrated parameter pages use named bounded
banks, independent of physical encoders. Shell target references fence domain, owner, page, role and
generation; wrappers and display names are not identities. Mutable actuators are checked during
preparation and again during application.

Project remotes, selected Track mix/sends, global Volume/Pan/eight Send columns, and Master/Cue join
parameter identity to separately subscribed project/track state. Contradictory joins cannot render,
start a touch/reset, mutate or enter Snapback capture. This handles independently sampled domains;
it does not claim atomic publication across them.

`ParameterBridgeSnapshot.touchLeases` reports shell-owned touch resources. A page losing its binding
removes its desired touch; the lifecycle waits for a later sample without that exact lease before
reusing the target. Cleanup subscriptions do not keep the old page alive.

The shell distinguishes new-write eligibility from cleanup addressability. It releases an old touch
only through its still-exact actuator. If an external cursor change has already rebound that proxy,
it drops/reports the lease instead of touching the replacement. Retirement is not a DAW write ACK.
Snapback still waits for observed restoration, with the separate
[precision limitation](snapback-v1-limitations.md#restoration-precision).

## What remains

- The full Device/chain/layer family needs a verified identity recipe for each selected owner,
  remote page, layer and parameter role. No device UUID may be invented from name, slot or wrapper.
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
