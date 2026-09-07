# Reloadable Controller Core

This is the current runtime and packaging contract for Core API 49, checkpoint schema 6 and
Bitwig controller API 25. These are three separate versions. [ARCH](../ARCH.md) explains how
controller behavior is assembled; [the views contract](views-api-design.md) explains authoring;
[the lifecycle contract](interaction-lifecycle.md) explains target-bound input. Installed-build
identities and tested coverage belong in [the live record](migrations/interaction-lifecycle-live-smoke.md),
not in this design document.

## Runtime boundary

[ARCH](../ARCH.md) owns module responsibilities and the migration inventory. Calls are in-process:
shell publishes immutable observations; core returns complete desired state and ordered effects.
Core registers no host callbacks, threads or suppliers in the parent graph. Unmigrated handlers
are frozen; missing/faulted core never revives deleted policy.

## Current lifecycle

`Push2ControllerExtensionDefinition` creates `PushControllerSetup`; `GenericControllerExtension`
delivers Bitwig lifecycle calls through `ReloadableControllerSetup`. Stable initialization creates
the resource graph and installs physical arbitration once. The reload supervisor starts after
stable startup. Each flush calls the runtime tick before the stable flush; shutdown closes the
runtime before releasing the setup's resources.

`ReloadableControllerRuntime` joins input, the controller bridge, clip host, reload supervisor and
optional debugger. `ControllerRuntimeEnvironment` builds snapshots and prepares/commits/applies
core results. `RuntimeManager` invokes the active child on the controller thread.

The shell eagerly installs the bounded topology but samples expensive domains only when requested.
Each result replaces the complete `DesiredBridgeSubscriptions`; unsubscribed bridge domains publish
typed empty values. A parameter-bank declaration likewise requests installed slots, not new Bitwig
proxies. Snapshot revision and dirty delivery are shell-owned: a change remains pending until a
core accepts a snapshot containing it, including changes raised while applying an earlier result.

## Snapshot and effects

`ControllerSnapshot` contains host read-back, capabilities, monotonic time/revision and physical
input state. It also carries the clip catalog, verified armed bindings and acquired fill-session
state. `ControllerCore.start(snapshot, previousState)` and `handle(event, snapshot)` each return a
complete `CoreResult`.

That result declares hardware output, input routes, subscriptions, clip bindings, controller state,
semantic actions, note-repeat ownership, parameter banks, parameter interaction/touch ownership,
execution requirements and ordered effects. Omission means relinquishing a desired-state category;
it does not mean retaining whichever old value happened to be installed.

The shell first validates routes, capabilities, ownership, subscriptions and exact targets, then
resolves effects. `commit` replaces a shell-owned pending result without applying external effects.
`apply` reconciles resources and submits the prepared operations. Existing parameter touches are
released before deferred actions and controller navigation; desired touches are acquired after the
ordered effects. Mutable host targets are checked again at application, not only during preparation.

A submitted effect is a request. Displays, lights and dependent operations use later authoritative
read-back. Neither the debugger's `APPLIED` transaction record nor a successful void Bitwig call
proves the DAW completed a write, launch or release. Separately sampled domains can disagree; views
join their explicit identities and stay blank/inert while target and rendering state are misaligned.

Logical timer DTOs still exist, but production has no executor for them. They must not be emitted;
see the [timer finding](findings/logical-timer-production-gap.md). Controller-cycle observation is
requested through the installed execution-requirements contract instead.

## Permanent input and gesture ownership

`PhysicalInputRouter` runs below consumed-button handling and continuous-command rebinding. At
BEGIN it freezes route disposition and core generation through LONG/END. The shell registers
encoder/strip touch-to-motion and pad-to-pressure companions once; relative values sum and
absolute/pressure values keep the latest sample. Companion motion flushes before END.

Absent routes retain unchanged stable behavior; `OBSERVE` also delivers to core; `EXCLUSIVE`
suppresses the stable command for a completely migrated control. Exclusive suppression is fixed
before any semantic-action barrier can queue a stable callback. Native `NoteInput` is separate
from this command arbitration.

The [interaction lifecycle](interaction-lifecycle.md) specifies migrated core cancellation and
resource retirement. [Session](migrations/session-launcher-location-design.md) captures launcher
locations independently of selected-track changes; shared host mutation guards release outstanding
presses before controller-driven rebinding. Native musical routing below has separate ownership.

## Selected-track musical routing

One private selection-following cursor supplies authoritative target identity and applicability.
It is separate from the user-pinnable model cursor; disagreement fails closed. The permanent Push
Pads `NoteInput` is excluded from `All Inputs`. Core declares one complete target-fenced layout,
native translation and route; `ControllerStateHost` realizes its lifecycle.

Entry submits the direct note-source attachment before activating the musical layout. Normal exit
relinquishes that layout, waits for held pad/sustain input to become idle, neutralizes parent-owned
MIDI state and detaches. Selection disagreement and core invalidation detach immediately and fence
re-entry until target/layout applicability agrees and musical input is idle. Replaying unchanged
state does not churn attachment. Ordinary view/target cleanup preserves physical debugger holds;
terminal/core invalidation also cancels debug-owned physical edges.

The shell owns neutralization of raw poly-pressure, CC, channel pressure and pitch bend emitted
through the permanent input. Those messages are target-neutral MIDI, not addressable cleanup on
an arbitrary old track. Bitwig determines which routed tracks receive them. API 25 supplies no
attachment read-back, so ordered route submission is not an audibility acknowledgement. Explicit
project routing to the named Pads input and host arm/monitor behavior remain relevant; the
controller does not rewrite those settings to make a test appear successful.

## Fill session and host barrier

`SelectedTrackFillClipHost` installs one eight-scene scanner and eight private one-slot actuators.
The scanner sweeps the selected track in finite pages, requires coherent samples and publishes only
complete catalogs. Selection/scene-topology changes first publish an empty generation fence.
Eight is the scan-page size and actuator capacity, not a maximum project scene count.

Core selects up to eight matching fill clips in catalog order. Desired bindings and verified armed
bindings are separate: an actuator arms only after its track, scene and clip observations agree.
A press during convergence is rejected rather than queued for a surprise launch.

A fill session has an opaque Bitwig-owned base, at most one acquired fill lease and at most one
value-only pending intent. The base may be launcher or Arrangement playback; the API supplies no
durable identity that covers both. A later press replaces the pending intent, never acquires a
second fill beneath the first, and never appears as the active session owner.

`ControllerRuntimeEnvironment` serializes replacement through the existing host barrier:

1. Observe the acquired fill busy (`playing`, `playbackQueued` or `stopQueued`).
2. Submit its native ALT release/Return through that exact actuator.
3. Observe a later sample in which the same acquired fill is no longer busy.
4. Retire the exact actuator and wait one further host sample.
5. Revalidate the latest pending owner, catalog generation and armed binding; only then acquire
   and launch it, if still held and eligible.

A pre-launch false sample cannot acknowledge Return. A failed release retains the exact lease for
retry. Releasing or invalidating a pending owner discards only that pending intent. The later
non-busy sample proves that the fill stopped; the extra sample is an opaque-base barrier, not
proof of which original clip or Arrangement source resumed.

The current core policy requests Immediate / Legato from Clip (or Project). The fill clip's effective
ALT Release action must resolve to **Return**; when it uses the project setting, that project
default must be Return. Bitwig exposes a release lane, not a direct “restore this base” operation.
Core renders active-fill feedback only from the acquired owner's observed playback state.

Shell-owned catalog/actuator/session state survives child replacement; a candidate hydrates from
read-back without synthesizing presses. External structural edits can still defeat scene-addressed
proxies because API 25 has no durable clip ID. [Session launcher cleanup](migrations/session-launcher-location-design.md)
preserves matching release submission within its addressable window; it does not inherit the fill
playback barrier merely by using the same lifecycle primitive.

## Transactional reload

The watcher reads immutable candidate publication off the controller path. The supervisor retains
one latest pending candidate and attempts activation on the controller thread when existing gates
permit it. Provider construction, checkpoint, startup, result preparation and pointer replacement
are serialized there.

Activation proceeds in this order:

1. Validate the candidate artifact, exact API/build identity and required capabilities.
2. Obtain a compatible value-only checkpoint and the authoritative shell snapshot.
3. Create/start the candidate and prepare its complete result.
4. Check that the candidate is still the latest request, then commit the shell-owned result buffer.
5. Publish the active child pointer/generation, apply external operations, and close the old loader.

A candidate load/start/prepare/commit failure leaves the previous core active. External application
happens after the pointer changes; a failure is reported and cannot roll back already submitted
host operations. During normal event handling, a child exception or rejected result after child
mutation quarantines that generation rather than continuing with potentially inconsistent state.
Quarantine retains passive output, relinquishes active resources and blocks further child input;
it does not restore a legacy implementation. A later valid candidate can replace it.

Replacement currently requires no pending semantic action in `DesiredParameterInteraction`, an
idle permanent input boundary, and an acknowledged legacy page-inbox retirement prefix. That
input boundary includes core-relevant held edges, queued motion and deferred stable callbacks.
A pure stable-only gesture with no semantic action does not fence replacement. Events retain their
original core generation, so a replacement never receives an old gesture's completion.

These gates are not a general asynchronous drain. Some pending operations have separate owners;
there is no contract proving that every queued toggle or deferred intent participates. General
reload quiescence is explicitly parked in [its active finding](findings/core-reload-quiescence.md).
Do not describe interaction cancellation as implementing that deferred work.

Checkpoint schema 6 stores bounded workspace/destination selection, engine-owner feedback, mixer
subselection, page/history/temporary ownership and acknowledged page-request sequence. It contains
no view instances, Java serialization or executable continuations. Incompatible, malformed or
unavailable checkpoints are discarded; startup uses the authoritative snapshot. The runtime closes
classloaders rather than relying on a child shutdown callback for host-resource cleanup.

## Classloading and packaging

The resource-only bundle orders core packaging before shell packaging without exposing child
classes on the shell classpath. Build steps purge stale nested/direct copies before packaging;
the shell extracts the embedded core JAR for loading.

`IsolatedCoreClassLoader` loads the shared API from the parent and JDK classes from the platform
loader. Other classes come only from the candidate, with no parent implementation fallback.
Bitwig, controller/framework and shell packages are denied. Candidate-local service discovery must
find exactly one `CoreProvider`; scoped context-classloader changes are restored in `finally`.
This is an architectural dependency boundary, not a sandbox for hostile code.

## Development loop

Hold `tools/with-pull-live --owner LABEL` continuously across publication/activation and the full
live test. `tools/reload-core` computes the local API fingerprint, packages the core and publisher,
publishes a unique candidate, and waits for the exact requested build ID to become active. Offline
builds need no live lease. Build completion alone is neither activation nor behavioral verification.

### Publication protocol

Default publication lives in `~/.drivenbymoss/pull/reload`; `PULL_CORE_RELOAD_DIR` or the command's
`--directory` option selects another directory. Protocol version 1 publishes a never-overwritten
`pull-core-<buildId>.jar` and atomically replaces `candidate.properties` with `apiVersion`,
`shellFingerprint`, `buildId`, `jar` and `sha256`. Embedded metadata must agree with the manifest.
The watcher verifies artifact hash/identity before loading candidate classes.

The running shell compares the candidate's fingerprint with its embedded parent-API fingerprint.
`tools/shell-fingerprint` covers `pull-core-api/pom.xml` and `pull-core-api/src/main`; unrelated shell
implementation edits deliberately do not change it. Compatibility therefore does not prove that a
new shell implementation was installed. Record shell/build provenance separately.

`status.properties` reports `active`, `failed` or `restartRequired`, requested and active build IDs,
and a reason. The client accepts only matching requested/active IDs and ignores stale status.
A client timeout is not cancellation of a still-pending candidate. Published immutable artifacts
remain available for diagnostics; the protocol does not provide automatic bounded pruning.

## Activation and terminal limits

Use [ARCH's restart boundary](../ARCH.md) and [TESTING](../TESTING.md) for build, live-lease and
verification procedures. A compatible core fingerprint does not activate changed shell code:
any shell implementation change still needs installation and restart.

Whole-extension disable/exit is different from child replacement. API 25 offers no post-exit
asynchronous completion/grace contract. Shell cleanup can submit best-effort restoration and MIDI
neutralization, but cannot promise Return acknowledgement before resources disappear.
`scheduleTask()` or blocking waits do not create that guarantee. A feature requiring guaranteed
restoration needs a directly addressable cleanup target and a proven host lifecycle contract.
