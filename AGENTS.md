# Repository Agent Instructions

## Repository topology

- `elliotjberman/Pull` is an intentionally one-time fork of
  `git-moss/DrivenByMoss`, not a branch expected to remain mergeable with upstream.
- Treat `elliotjberman/Pull:master` as the normal base and destination for Pull feature pull
  requests. Do not target `git-moss/DrivenByMoss` or interpret its current diff as the Pull review
  surface unless the user explicitly asks for an upstream contribution or resynchronization.

## Authoritative state and controller feedback

- Treat hardware input as a request to alter state, not as proof that the requested state change
  succeeded.
- Grid lights, displays, and other controller feedback should render authoritative state read back
  from the stable shell or Bitwig. Do not derive feedback directly from the latest press or an
  emitted effect merely to make it feel immediate.
- Treat submitted parameter writes, clip launches, transport actions, and other effects as
  requests. UI state and hardware feedback must come from a later subscribed host read-back, not
  from the value most recently sent to Bitwig.
- Prefer one controller-tick of honest read-back latency over optimistic feedback that can disagree
  with playback. If measured latency is genuinely disruptive, improve state propagation first;
  use optimistic rendering only as an explicit exception with reconciliation and failure tests.
- Tests should distinguish input, requested effects, applied host state, snapshot read-back, and
  rendered output so a mock cannot accidentally confirm an optimistic shortcut.
- Model selected-track/device applicability, visible view, and physical-control layout ownership as
  separate state. A layout may decide which pads it owns, but a generic active-view enum is not
  proof that the selected target has the capability a controller feature needs.
- Derive authoritative selected-track applicability from a private selection-following target, not
  a user-pinnable cursor that can remain attached to a previously selected track.
- When controller actions and rendering use different host proxies, compare their stable target
  identity and fail closed while they disagree. Never render or mutate a pinned model target while
  actions are aimed through a different selected target.
- Treat a successful void Bitwig API call as command submission, not completion of the requested
  playback transition. Serialize dependent commands from a later subscribed host observation, and
  keep any exact actuator/lease frozen until that acknowledgement arrives.
- Fakes for asynchronous Bitwig behavior must separate command submission from host advancement;
  never make a release call synchronously mutate playback state when production read-back is what
  permits the next action.
- A drum-fill session has one opaque Bitwig-owned base and at most one acquired fill lease. A new
  press may replace the value-only pending intent, but it must never retain an older fill beneath
  the replacement or make the pending owner active.
- Serialize a fill replacement across the full host barrier: observe the active fill busy, submit
  Return, observe it later non-busy, retire its exact actuator, wait one later host sample, and only
  then prepare and launch the latest still-valid pending intent.
- Do not use `scheduleTask()` or blocking waits to fake asynchronous cleanup after controller
  `exit()`; API 21 provides no post-exit grace/completion contract. Keep terminal cleanup explicitly
  best-effort, and require a directly addressable restore target if shutdown restoration must be
  guaranteed.

## Bounded stable capability canopy

- Create practical Push input routers and reusable Bitwig cursor, bank, transport, clip, and device
  proxies, interested values, and observers eagerly during extension initialization. A reloadable
  child core may compose only the state and operations already present in this stable bounded
  canopy.
- Do not confuse eagerly installed Bitwig topology with unconditional shell/core sampling. The
  core must return the complete replayable `DesiredBridgeSubscriptions` it currently needs;
  unrequested domains publish their typed `empty()` values and must not incur their high-rate
  snapshot work. Returning a later result replaces the whole subscription set.
- Keep every bank, scanner, actuator pool, and proxy window explicitly bounded. Document its
  capacity, identity/generation rules, selection scope, and any required Bitwig
  session setup next to the feature that consumes it.
- Treat controller input ownership as complete replayable state. An absent route is stable-owned,
  `OBSERVE` delivers to both the reloadable core and established controller behavior, and
  `EXCLUSIVE` is valid only for a migrated control whose permanent stable binding is intentionally
  semantically inert. Freeze an edge route and its active-core generation at gesture `BEGIN`
  through `LONG` and `END`, even across a route-map change or core reload. A stale completion is
  rejected instead of reaching either a new core or behavior that never received the press.
- Install input arbitration once, below button consumed-state handling and below any continuous
  command/parameter rebinding. Never add a second MIDI or hardware callback to observe a migrated
  input. Coalesce relative motion by summing deltas and absolute/pressure motion by keeping the
  latest sample until the controller tick; preserve edge ordering around touch release.
- Controller-command arbitration and Bitwig's native `NoteInput` are separate paths. An
  `EXCLUSIVE` pad or pressure route suppresses stable framework commands and state only; it does
  not suppress musical note data that Bitwig routes from the permanent Push `NoteInput`.
- Do not confuse a permanent physical binding with permanent behavior policy. If a control and
  input kind already exist in the stable `PhysicalInputRouter`, and the bridge already exposes the
  authoritative state and effect needed by the feature, implement its behavior in the reloadable
  core with `OBSERVE` or `EXCLUSIVE` routing. Complete the migration: move every semantic variant
  of an exclusively owned gesture into the core, replace its stable command with an inert binding,
  and admit that exact control-and-kind pair to exclusive routing. Do not keep a second stable
  implementation for missing, incomplete, or faulted cores. Missing or faulted core behavior is
  inert and must be reported; a rejected replacement candidate may leave the previously active
  core running because activation is transactional. A restart is required only when the physical
  input, required state/effect capability, or output transport is missing from the installed
  canopy.
- Input and output migration are independent. A core-owned button action may still use a stable
  light supplier when that supplier renders the same authoritative Bitwig read-back. Do not put
  action policy back in the shell merely because general light ownership has not migrated; expand
  the output canopy only when reloadable policy must also change what the light means.
- Keep the private selection-following cursor observation/action-only. Do not attach the permanent
  Push `NoteInput` with `Track.addNoteSource()` or exclude it from `All Inputs`; Pads and raw ribbon
  MIDI must follow ordinary Bitwig track-input, monitor, and record-arm routing.
- Parent-own cleanup for stateful raw MIDI sent through the permanent `NoteInput`. Neutralize
  outstanding CC, channel-pressure, and pitch-bend state when the active core generation changes,
  selection changes as a conservative safety boundary, or the extension shuts down. Do not model
  this target-neutral effect as selected-track MIDI or promise target-specific cleanup; Bitwig
  decides which normally routed tracks receive both the value and its neutralization.
- Generation checks at prepare time are not enough for effects aimed through mutable proxies.
  Recheck live selected-track identity before applying selected-track effects. For drum-pad
  effects, also fence the device ID, bank base MIDI note, pad channel ID, and alignment between the
  private selected target and rendering model cursor; fail closed if any identity changed.
- Do not imply that eager proxy/interested-value setup covers arbitrary project state. Bitwig proxy
  topology is initialization-owned, bank windows are finite, and projects are unbounded; a feature
  outside the installed canopy still requires a shell/API change and Bitwig restart.
- A core-only change inside the installed API/canopy hot reloads. Changing a parent-loaded API
  contract, adding a Bitwig proxy/property/observer, changing a permanent binding or proxy capacity,
  or broadening hardware output ownership requires a shell build/install and Bitwig restart.
- Core API 9 arbitrates general input, so mappings whose state and effects are already bridged
  belong in the reloadable core. Only the 12 drum-fill RGB lights have migrated output ownership; do not
  claim general Push light or display hot reload until stable complete-output arbitration exists
  for those surfaces.

## Bitwig controller API compatibility

- This repository currently targets Bitwig controller API 21. Keep the
  `com.bitwig:extension-api` version in `pom.xml` and
  `AbstractControllerExtensionDefinition.getRequiredAPIVersion()` exactly synchronized.
  Do not confuse this Bitwig API version with the separate reloadable-core API version.
- Before adding or changing a direct `com.bitwig.extension.*` call, verify the exact method
  and overload against the locally resolved `extension-api` JAR for the declared version.
  Do not rely on old examples, memory, or successful compilation alone.
- Do not introduce calls marked `@Deprecated` in the declared Bitwig API. Bitwig may reject
  deprecated controller methods at runtime instead of merely logging a warning.
- For changes that touch Bitwig API objects, run the complete package build with deprecation
  reporting enabled:

  ```bash
  mvn -o -Dmaven.compiler.showDeprecation=true package
  ```

  Treat every deprecation warning in changed code as a blocking failure.
- Fake-host tests do not exercise Bitwig's runtime proxy enforcement. After the offline build
  passes, explicitly call out when a first live Bitwig smoke test is still required.
- When a controller fails during startup, inspect `~/Library/Logs/Bitwig/BitwigStudio.log` and
  `~/Library/Logs/Bitwig/BitwigStudio-previous-run.log` for the full stack trace before patching.
