# Session Capability Audit

Status: characterization before implementation, based on migration worktree baseline `a905be84`.
This audit does not claim that Session policy has migrated or that live behavior has been verified.

## Scope And Existing Boundary

Feature: inherited full 8x8 Session and the upper 8x4 Session portion of VS Live, including scene
keys, navigation, Stop-plus-pad, and their feedback. Shared physical controls also have non-Session
uses; those uses must be accounted for before making any permanent binding inert.

The source paths are
[Push SessionView](../../pull-shell/src/main/java/de/mossgrabers/controller/ableton/push/view/SessionView.java),
[WorkspaceView](../../pull-shell/src/main/java/de/mossgrabers/controller/ableton/push/view/WorkspaceView.java),
[AbstractSessionView](../../pull-shell/src/main/java/de/mossgrabers/framework/view/AbstractSessionView.java),
[AbstractView](../../pull-shell/src/main/java/de/mossgrabers/framework/featuregroup/AbstractView.java), and
[PushCursorCommand](../../pull-shell/src/main/java/de/mossgrabers/controller/ableton/push/command/trigger/PushCursorCommand.java).
Core `SessionView` already owns Stop button policy/light and consumes Stop chords, but stable grid
policy executes Stop-plus-pad. Core `SessionNavigationView` currently declares stable adapter claims.

Physical inputs: raw PAD edges/velocity and pressure, SCENE1..8 buttons (only SCENE1..4 in the upper
facet), arrows, PAGE buttons, Stop, and Shift/Select/Delete/Duplicate/Browse modifiers. Full Session
also gives OCTAVE buttons scene-page behavior; VS Live assigns those to Drum behavior instead.
Long pad presses are inert. Scene LONG and navigation LONG/UP are inert unless stated below.

## Semantic Inventory

| Surface | Existing behavior to preserve or explicitly change |
| --- | --- |
| Grid ordinary | Optional select-on-launch, then main launch on DOWN and main release on UP. Shift chooses alternate launch/release. |
| Grid combinations | Shift+Select birds-eye intercepts first. Otherwise Select-only precedes Delete, Duplicate, Stop, Browse. Combination consumption prevents trailing modifier actions; pad combinations consume pads. |
| Duplicate | A content slot becomes the source; a later empty slot copies that source. Source persists in the view. |
| Armed empty pad | Configured record, create fixed-length note clip with launcher overdub, or no action. |
| Stop-plus-pad | Stop the pad's visible track; Shift selects alternate stop. Do not select that track. |
| Scenes | Existing scene DOWN selects, displays its name, then launches; UP releases. Shift selects alternate lane. Select suppresses DOWN launch. Delete precedes Duplicate and selection. |
| Birds-eye | DOWN navigates track/scene pages using the current full/upper shape. UP does nothing. Grid shows available pages and current page. |
| Arrows | DOWN only. Up/down move one scene, or a scene page with Shift. VS Live left/right move track pages, or single tracks with Shift. Full Session left/right retain active-mode item-page behavior. |
| PAGE | DOWN moves track-bank pages in Session layouts. Outside Session these controls navigate sequencers. |
| OCTAVE | Full Session DOWN pages scenes. VS Live controls Drum octave instead. |
| Grid output | Clip color, selected/content, muted dimming, empty armed stripe, playing/recording, queued playback/record/stop blink, birds-eye state. |
| Scene/navigation output | Selected/existing/off scenes; arrow/page availability from authoritative banks or the applicable active mode. |

Important quirks are characterized rather than silently normalized. Grid release re-reads Shift,
so changing Shift between DOWN and UP changes the release lane. A scene's UP can issue release
even when Select/Delete/Duplicate suppressed its DOWN launch. Without intervening host content
read-back, armed-empty recording policy runs on both pad edges. Deciding whether these should
change requires an explicit behavior decision, not an unnoticed migration side effect.

## Capability Coverage

[SessionBankRegistry](../../pull-shell/src/main/java/de/mossgrabers/controller/ableton/push/workspace/SessionBankRegistry.java)
already owns eagerly created 8x8 and 8x4 banks and activates one for feedback. It preserves offsets
when shapes switch.
[SessionBankHost](../../pull-shell/src/main/java/de/mossgrabers/pull/shell/runtime/SessionBankHost.java)
currently supplies visible track identities/basic state and offsets, plus fenced track select,
track stop, and bank stop. It does not publish the slot/scene matrix or navigation readiness.

Missing reusable state:

- Bounded slot existence/content/name/color/selected/muted/playing/recording/queued state and
  identity or generation information; visible scene existence/name/selection/position.
- Bank totals and single/page navigation availability, and pending/readiness state while moving.
- Observed configuration for select-on-launch, armed-empty action, new clip length, record stripe.
- Defined lifetime and completion state for held launches, a clipboard source, and dependent
  clip-create/record/launch actions.

Missing effects: fenced slot/scene select, main/alternate launch and release, delete, scene
duplicate, slot copy source-to-destination, slot record/create, slot browser replacement, and
bounded bank navigation. Core must own create/select/launch/overdub sequencing; a stable executor
must not grow a feature-shaped "record this pad" recipe.

Generic button/pad RGB ownership is installed. Exact legacy blinking needs either a reusable
blink output contract with core-chosen colors/rate or core animation using production tick demand.
Logical timer effects have no production executor; see the
[timer finding](../findings/logical-timer-production-gap.md). Scene-name notifications need a
core-owned display result or a reusable presentation mechanism as part of that action slice.

## Exact Target And Release Questions

A movable Java slot/scene wrapper is not a durable target. Slot identity must distinguish bank
shape/generation, track channel identity, scene position, and any observed content replacement that
invalidates the intended operation. Do not invent a globally addressable clip ID that the Bitwig
API does not supply. Verify exact API 25 methods against the resolved JAR before implementation.

Freeze a held launch's target and chosen release semantics at admission if that is the selected
new contract. A retained wrapper alone cannot guarantee release after its bank has scrolled. The
design must determine whether own navigation drains held operations, whether an exact bounded
actuator lease can retain the old target, and what happens after external navigation/deletion.
Rejecting a stale release avoids hitting the wrong clip but does not prove cleanup of the old one.

The inherited clipboard's `sourceSlot` has the same problem. Prefer a fenced bounded source lease
or explicit invalidation; decide how source lifetime crosses view replacement, navigation, and core
reload. Core-only queued intent must drain, checkpoint completely, or cancel under an explicit
contract. Stable-owned cleanup may survive replacement; extension exit offers no later observation
guarantee and remains best effort. These questions are unresolved by the characterization tests.

### API 25 Actuator Investigation

The resolved `extension-api-25-sources.jar` exposes non-deprecated
`ControllerHost.createCursorTrack(id, name, sends, scenes, shouldFollowSelection)`,
`CursorChannel.selectChannel(Channel)`, `PinnableCursor.isPinned()`, and the cursor track's
scrollable `ClipLauncherSlotBank`. A private pinned track with one visible slot is therefore a
practical location lease, already used by
[SelectedTrackFillClipHost](../../pull-shell/src/main/java/de/mossgrabers/pull/shell/runtime/SelectedTrackFillClipHost.java).
Its acquisition waits for later matching track ID, pin state, scene index, existence/content/name
samples before becoming ready. General Session acquisition would take a fenced visible-bank track
as the source instead of assuming that only the selected track can be addressed.

One possible fixed pool is one one-slot cursor per held pad, plus one clipboard source, with
capacity chosen explicitly for the supported maximum concurrent holds. Park before launch and
freeze the actuator while leased. This survives display-bank scrolling, workspace switches, and
track-selection changes; it avoids retaining the moving display bank's wrapper. Acquisition is
asynchronous: a press during parking needs bounded pending intent and a defined policy for release
before readiness. Prewarming can reduce latency but does not remove identity checks. Every lease
must expose readiness/generation and capacity exhaustion instead of silently aliasing another slot.

This retains a track/scene location, not necessarily an original clip object. Scene insertion,
deletion, clip movement, or replacement can change the content at that location. Name/content/color
are observations, not unique clip identity; identical replacement can evade such fingerprints.
Track pinning does not solve scene identity. A stationary scene bank has the same limitation.

`PinnableCursorClip` is another possible anchor: it has `Clip.getTrack()` and
`Clip.clipLauncherSlot()`, so its slot can execute main/alternate release and serve as a copy source.
However `CursorClip.selectClip` accepts a `Clip`, not a `ClipLauncherSlot`; the API exposes no direct
slot-to-clip-cursor selection overload. Acquiring it by private cursor navigation, or by following
selection then pinning, needs a bounded protocol and live proof. Do not introduce editor selection
as an unnoticed side effect when select-on-launch is disabled or Duplicate normally only remembers
a source. `ObjectProxy.createEqualsValue(other)` can observe proxy equality if created eagerly, but
comparison between two movable proxies is not a durable identity token by itself.

Finally, `launchRelease()` and `launchReleaseAlt()` return void and expose no command receipt. A
normal Session release may leave the clip playing because of its configured release action, so
the fill-specific busy-to-nonbusy Return barrier is not a general Session acknowledgement. Define
and verify the release actuator's retirement rule before reusing it. The source documentation
alone does not prove that a later sample with unchanged playback acknowledges an inert release.

## Classification And Cutover

Class B: one shared bounded Session canopy expansion, requiring API/shell build, install, and a
restart before live tests. This is not a core-only change. Slot capabilities unlock the complete
grid; scene capabilities can form a smaller full action/feedback slice first. Stop-plus-pad cannot
be isolated by taking exclusive ownership of only one modifier branch of the same physical pad.

For every admitted exclusive control, remove the corresponding stable semantic dispatch and
feedback together. Scene keys/arrows/PAGE also serve other inherited views; preserve those complete
variants or defer that control's exclusive cutover. Keep raw PAD actions ordinary-dispatch-only;
Session clip control must not consume the learned controller-mapping endpoints.

## Characterization And Acceptance

[SessionBehaviorCharacterizationTest](../../pull-shell/src/test/java/de/mossgrabers/controller/ableton/push/SessionBehaviorCharacterizationTest.java)
invokes the existing full/upper view and navigation policy with dynamic host proxies. It verifies
mapping, modifier precedence/consumption, recording configuration, scene release quirks, navigation
differences, and authoritative lights. Requests do not mutate fake host state until `advanceHost`;
the model's recording helper is a boundary stub, not verification of its internal Bitwig recipe.
This fixture deliberately omits the physical router and actual Bitwig proxy enforcement.

Before claiming migration completion, add core parity tests, shell prepare/apply identity and
pending-bank tests, shared-control inert/exclusive-routing tests, and reload/fault tests across
held launches and queued host acknowledgements. Do not replace these with optimistic fake effects.
Cover 8x8/8x4 offset changes and retained grid state under Project/Mix/Device/Browse/Master pages.

Run the full package with deprecation reporting. The live test must own the live lease through
exact-build activation and routed inputs, later exact slot/scene host state, and output capture.
The debugger has generic edges and output observation, but selected-track `clip_playing` alone
cannot prove an exact Session slot or scene; add a bounded generic observation when needed.
Checkpoint before any restart and follow [TESTING.md](../../TESTING.md). No live verification is
claimed by this audit.
