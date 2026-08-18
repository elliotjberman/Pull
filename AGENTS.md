# Repository Agent Instructions

## Singleton live Bitwig/Push environment

- Offline builds, tests, edits, rebases, and reviews may run concurrently in separate worktrees.
- Before replacing `Pull.bwextension`, publishing or reloading a core, quitting/launching/restarting
  Bitwig, or driving state-changing Push debugger input, enter `tools/with-pull-live --owner LABEL`.
  Keep that shell open through exact-build activation and the complete live smoke test; `exit`
  releases the lease. Do not acquire and release it around only the install/reload step.
- If the live environment is owned, continue useful offline work or report live verification as
  pending. Do not bypass the lease with raw copy, publication, process-lifecycle, or debug commands.
- Read-only log and status inspection may proceed without the lease, but do not treat observations
  made during another owner's test as evidence for a different build.

## Closed-loop testing

- Read `TESTING.md` before validating controller behavior or changing debug ingress.
- A build or submitted command is not proof that a feature worked. Drive the real routed path and
  verify later authoritative host state and controller output. If the harness cannot drive or
  observe the feature, add the smallest reusable, bounded, opt-in debug capability needed to close
  the loop as part of the feature work.

## Zero-new-stable-policy boundary

- Do not add or change Pull product behavior in `pull-shell`. Mappings, modifier meanings, gesture
  policy, navigation recipes, view/workspace selection, color meaning, light behavior, display
  layout, animation, and other controller semantics belong in the reloadable core.
- This rule is not conditional on the installed canopy already being sufficient. If a requested
  behavior needs missing input, authoritative state, effects, or output ownership, treat it as one
  bounded canopy expansion: add the smallest reusable parent-loaded mechanism, then implement the
  complete semantic behavior in `pull-core`. If that expansion is too broad for the task, stop and
  report the feature as not ready; do not implement a temporary stable version.
- Stable adapters are frozen migration debt. They may preserve existing legacy behavior unchanged
  while adjacent capability work lands, but they may not acquire a new semantic branch or satisfy a
  new feature request. A stable change must be resource creation, state observation, validation,
  effect execution, hardware translation/transmission, lifecycle safety, or deletion of legacy
  policy.
- Input and output transports are mechanically independent, but a controller surface's action and
  feedback are one semantic slice. When migrating or changing a control that has feedback, migrate
  its action and feedback together. Do not leave its light/display supplier stable and knowingly
  create another restart for the next iteration.

## View architecture

- Read `ARCH.md` before changing Push views, modes, workspaces, input routing, display ownership, or
  Session bank topology. `docs/views-api-design.md` is the detailed design contract.
- Treat `ControllerViewFacet` and the stable `WorkspaceView`/`WorkspaceMode` adapters as frozen
  migration scaffolding, not the final view API. Every selected adapter must belong to a
  fixed-footprint core `ControllerView` profile with explicit `STABLE_ADAPTER_*` claims. Preserve
  existing adapter behavior only until it migrates; never add new behavior to an adapter.
- A workspace selects declared views and facets; it must never remap arbitrary callbacks onto raw
  hardware controls.

## Stable-to-core migration

- Read `docs/reloadable-core-migration-guide.md` before moving controller behavior across the
  stable-shell/core boundary. Use `docs/reloadable-core-migration-roadmap.md` to choose reusable
  canopy work and avoid feature-shaped bridge additions.
- Use the guide's capability audit before every Push mapping, mode, view, rendering, or navigation
  change, even when the request is described as a small bug fix rather than a migration.
- Complete the guide's capability audit before requesting `EXCLUSIVE` input ownership. Preserve
  every semantic variant or defer the entire migration and leave the existing control unchanged;
  never extend the stable implementation or retain it as fallback for behavior claimed by core.

## Repository topology

- `elliotjberman/Pull` is an intentionally one-time fork of
  `git-moss/DrivenByMoss`, not a branch expected to remain mergeable with upstream.
- Treat `elliotjberman/Pull:master` as the normal base and destination for Pull feature pull
  requests. Do not target `git-moss/DrivenByMoss` or interpret its current diff as the Pull review
  surface unless the user explicitly asks for an upstream contribution or resynchronization.

## Worktree lifecycle

- Perform every feature change and other write operation in its own dedicated worktree; only
  read-only inspection may use the main checkout. Keep that checkout clean on `master` and
  fast-forwarded to `origin/master` as the shared synchronization point for concurrent work.
- After a pull request merges or its work is explicitly abandoned, require a clean worktree,
  remove it without force, and delete its local branch with a non-forcing delete. Never discard a
  dirty worktree during cleanup.

## Active findings

- Before architectural work, inspect `docs/findings/`; update or delete any finding whose removal
  criteria the change satisfies.

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
- Treat controller input ownership as complete replayable state. An absent route preserves only
  unchanged frozen legacy behavior,
  `OBSERVE` delivers to both the reloadable core and established controller behavior, and
  `EXCLUSIVE` is valid only for a migrated control whose permanent stable binding is intentionally
  semantically inert. Freeze an edge route and its active-core generation at gesture `BEGIN`
  through `LONG` and `END`, even across a route-map change. Do not replace the active core until the
  input router has no core-relevant active gesture, queued motion, or deferred stable callback; a
  pure stable-only `NONE` gesture with no semantic action does not cross the core boundary and does
  not fence replacement. A replacement must never receive the completion of a gesture whose press
  belonged to the previous generation.
- Install input arbitration once, below button consumed-state handling and below any continuous
  command/parameter rebinding. Never add a second MIDI or hardware callback to observe a migrated
  input. Coalesce relative motion by summing deltas and absolute/pressure motion by keeping the
  latest sample until the controller tick; preserve edge ordering around touch release.
- Controller-command arbitration and Bitwig's native `NoteInput` are separate paths. An
  `EXCLUSIVE` pad or pressure route suppresses stable framework commands and state only; it does
  not suppress musical note data that Bitwig routes from the permanent Push `NoteInput`.
- Do not confuse a permanent physical binding with permanent behavior policy. Implement controller
  behavior in the reloadable core with `OBSERVE` or `EXCLUSIVE` routing. Complete the migration:
  move every semantic variant of an exclusively owned gesture into the core, replace its stable
  command with an inert binding, and admit that exact control-and-kind pair to exclusive routing.
  Do not keep a second stable implementation for missing, incomplete, or faulted cores. Missing or
  faulted core behavior is inert and must be reported; a rejected replacement candidate may leave
  the previously active core running because activation is transactional. A restart is required
  only when the physical input, required state/effect capability, or output transport is missing
  from the installed canopy.
- Keep selected-track Note routing behind one stable lifecycle owner. The permanent Push
  `NoteInput` is excluded from `All Inputs`; while an aligned note-capable viewer is active, attach
  it to the private selection-following cursor with `Track.addNoteSource()`. The core returns one
  complete target-fenced layout-and-route value. Enter by attaching before activating the layout;
  exit by relinquishing the layout, waiting for physical input to become idle, neutralizing
  parent-owned MIDI state, and then detaching. Selection disagreement and core failure detach and
  fail closed. Replaying an unchanged core result must not churn the route. Bitwig tracks explicitly
  configured for the named `Pads` input remain outside this selected-only controller guarantee.
- Parent-own cleanup for stateful raw MIDI sent through the permanent `NoteInput`. Neutralize
  outstanding poly-pressure, CC, channel-pressure, and pitch-bend state when the active core
  generation changes, selection changes as a conservative safety boundary, or the extension shuts
  down. Do not model
  this target-neutral effect as selected-track MIDI or promise target-specific cleanup; Bitwig
  decides which normally routed tracks receive both the value and its neutralization.
- Generation checks at prepare time are not enough for effects aimed through mutable proxies.
  Recheck live selected-track identity before applying selected-track effects. For drum-pad
  effects, also fence the device ID, bank base MIDI note, pad channel ID, and alignment between the
  private selected target and rendering model cursor; fail closed if any identity changed.
- An `IParameter` wrapper is not a semantic target identity. Cursor remote-control wrappers survive
  selected-owner and page changes. Fence mutable parameter actuators by their live domain, owner,
  page, and slot/role at prepare and apply time; exclude an unclassified Bitwig parameter proxy
  rather than leasing it by Java object identity.
- Do not imply that eager proxy/interested-value setup covers arbitrary project state. Bitwig proxy
  topology is initialization-owned, bank windows are finite, and projects are unbounded; a feature
  outside the installed canopy still requires a shell/API change and Bitwig restart.
- A core-only change inside the installed API/canopy hot reloads. Changing a parent-loaded API
  contract, adding a Bitwig proxy/property/observer, changing a permanent binding or proxy capacity,
  or broadening hardware output ownership requires a shell build/install and Bitwig restart.
- Core API 37 arbitrates general input, complete composed controller state, semantic action intent,
  named bounded parameter banks, and exact parameter-target leases. Each active view contributes
  its fixed facets, Note layout, selected-track musical route, and other owned output; composite
  workspaces merge disjoint contributions and reject physical overlap. Project-macro encoder turns
  are the reference parameter migration: core owns
  the mapping, relative effect, and snapback policy while stable owns Bitwig proxies, identity
  validation, read-back, and effect execution. The Play action, sixteen authoritative
  playing-velocity drum-play RGB lights, eight drum-fill RGB lights, four drum-rate RGB lights,
  four Bitwig-manually-mappable drum-control pads and lights with a replayable
  physical-to-semantic mapping lease and authoritative Boolean mapped-state read-back from
  dedicated no-output background lights on permanent semantic Bitwig button identities. All 64
  original physical PAD actions are raw-dispatch-only and never define learned mapping identity;
  raw MIDI supplies ordinary dispatch outside a lease and the routed core gesture during one
  without becoming a second learned action. Play,
  Record, Mute, and Solo lights, both Master button rows, and the Master graphics display have
  migrated direct output ownership. Session Stop Clip has migrated as one composed action/feedback slice: a
  selected `SessionView` owns its observed edge and RGB light independently from the active
  Mix/Device/Browse page; plain Stop uses the private selected track's immediate actuator, while Shift/Select Stop is
  fenced to the active bounded 8x8 or 8x4 Session bank, and Select release is explicitly consumed.
  Its permanent direct command is inert, but `OBSERVE` intentionally preserves held-button state
  for the still-adapted Session grid's Stop-plus-pad chord; do not request `EXCLUSIVE` until that
  grid behavior migrates. Legacy long/locked Stop row overlays are removed. A persistent `SelectedTrackMuteSoloView` owns
  Mute/Solo edges and RGB feedback in every workspace, always targets the private authoritative
  selected track, and has no project-clear, lock, row-overlay, Master, device-layer, pad, or note
  modifier variants. Play and Record light policy renders
  authoritative engine, transport, overdub,
  and selected-track arm read-back in every workspace. Play targets the remembered engine-owning
  project through a bounded navigate/acknowledge/toggle/acknowledge/return transaction. Its stable
  command is inert;
  the stable bindings only preserve the physical seam and translate core RGB to the Push palette.
  API 36 installs generic explicit ownership for every registered Push button light and all 64
  physical grid-pad lights. A core view may render only inside its declared output claims; an
  explicit owner replaces the stable supplier, while an unclaimed light preserves that supplier
  exactly. This is transport, not semantic ownership: migrate a control's action, authoritative
  state, and feedback together before changing its meaning.
  The Master scene's copy, typography, geometry, color, clipping, and shape policy are core-owned;
  stable only interprets bounded generic primitives. The VS Live Project Macro or Track Mixer body
  and retained Track Selection view independently own the fixed 960x143 parameter region and
  960x17 bottom strip. Track Mixer owns its active-parameter rendering and relative encoder turns;
  its upper-row page menu and encoder touches remain explicit frozen adapters. Compilation
  requires complete coverage, confines every primitive with compiler-owned clip scopes to its
  claimed local viewport, and emits one 960x160 base scene. Track Selection owns its eight
  exclusive lower-row edges and authoritative lights; it resolves the exact visible target at
  gesture `BEGIN`, and the bounded Session effect revalidates generation, bank shape, index, and
  channel identity before selecting. The stable `WorkspaceMode` retains only project-macro touch/Delete adaptation,
  and missing core output stays blank rather than reviving its deleted page/track-strip policy.
  A generic display base plane projects complete core scenes on every Push page while retaining
  ordinary overlays above them. API 22 also installs a temporary sparse 8x8
  pad-grid overlay and a complete 960x160 display overlay whose activation and visuals are
  core-owned, plus a bounded pure mixer-control render service:
  Master, Project Macro, the ordinary stable Track Mix adapter, and the core-owned VS Live Track
  Mixer body use the same core-owned Volume/Pan/Knob renderer.
  Cue parameters and Track Mix sends are the same Knob component, not parallel lookalikes. Track
  Mix and Project Macro accept only column-local scenes structurally confined to the installed
  parameter-body region; remaining stable menus and footers stay frozen, and missing/faulted core
  output leaves all eight mixer-control slots blank rather than reviving a stable semantic fallback.
  Those stable menus,
  footers, and other workspace facets are frozen inherited migration debt, not valid extension
  points. Do not claim ribbon or semantic ownership of inherited display pages beyond Master and
  the composed VS Live page merely because the generic base-scene transport is installed. Logical
  timer DTOs have no production capability or executor and must not be emitted while
  `docs/findings/logical-timer-production-gap.md` is active.

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
