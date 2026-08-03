# Repository Agent Instructions

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

- Create and subscribe practical Push inputs and reusable Bitwig cursor, bank, transport, clip,
  and device proxies eagerly during extension initialization. A reloadable child
  core may compose only the state and operations already present in this stable bounded canopy.
- Keep every bank, scanner, actuator pool, and proxy window explicitly bounded. Document its
  capacity, identity/generation rules, selection scope, and any required Bitwig
  session setup next to the feature that consumes it.
- Do not imply that eager subscriptions cover arbitrary project state. Bitwig proxy topology is
  initialization-owned, bank windows are finite, and projects are unbounded; a feature outside the
  installed canopy still requires a shell/API change and Bitwig restart.

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
