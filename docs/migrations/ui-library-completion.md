# Remaining display families: component-library cutover

Core API 54 moves all ordinary Push page rendering into the reloadable UI library. The shell
observes its bounded model and legacy page-local selection; core assembles the complete 960×160
display. Legacy actions, navigation, modifiers, parameter providers and hardware lights remain
frozen. The physical Color picker remains unchanged. This display cutover grants no new input or
actuator authority.

The optional Clip piano roll stays unchanged. TODO: Elliot never used this so deferring how to migrate it to the new framework

## Capability audit

- **Inputs/effects:** unchanged. Core does not acquire new input routes or emit effects for this
  display slice. Existing mode selection, browser touch selection, Add Track chooser state and
  parameter touch flags are observed values.
- **State:** `CONTROLLER_PAGE_DISPLAY` publishes `ControllerPageDisplaySnapshot`, identifying the
  observed mode and its immutable Device, Option or Editing data. Unrequested/unsupported state is
  empty; availability flags describe missing host items. Capture reuses initialized proxies and
  creates no Bitwig topology or listeners.
- **Output:** the selected core page owns the complete ordinary display. Its old `updateDisplay2`
  producer is removed. The permanent adapter sends the core scene through the existing clipped
  interpreter. Missing or mismatched page observations clear output. The explicitly deferred piano
  roll retains its existing specialized path.
- **Lifecycle:** capture/render are synchronous and value-only. This adds no gestures, effects,
  timers, actuator leases or handoff policy. General asynchronous replacement work is separate.
- **Activation:** the new parent-loaded state/subscription contract requires a shell installation
  and Bitwig restart. The matched installation and scoped live checks are recorded below.

## Device and editing families

| Family | Observed bound | Core presentation |
| --- | --- | --- |
| Device Params/Chains, User | At most eight siblings, remote-page names, chain names and visible parameters; existing selection/touch/preferences | Shared choices, parameter values, rings and menus |
| Layers, layer Volume/Pan/Sends, Track/Layer Details, Crossfade | Eight visible channels/parameters/sends, selected channel and raw local bank/selection flags | Shared mixer controls, details choices and channel footers |
| Clip parameters | Clip playback/loop/shuffle/accent values, eight touched encoders and current-track footer entries | Shared values, toggles and footer; optional piano roll excluded |
| Note editing | Existing selection count/page and first selected note's observed values, eight touched encoders | Duration, expression, repeat/recurrence and related editing fields |
| Quantize/Groove | Observed quantize preferences and five groove parameters | Shared option and parameter components |

Device names, slots and display observations are not actuator identities. The remaining Device
control migration still needs the exact target contract. Track Details carries the selected bank
track or Master's raw `actionTargetId` and shows “Waiting for track target...” when it is missing or
differs from the displayed cursor. This suppresses details for a mismatched pinned target, but its
frozen physical lights still read the cursor and its actions remain unchanged.

Note feedback uses `getObservedStep`, which preserves host read-back separately from the legacy
editor's optimistic working copy.
Submitting an edit cannot become its own display acknowledgement; later host observations supply
the shown note values. This does not migrate note-editing gestures or their effect lifecycle.

## Option/list families

| Family | Observed data and bound | Core presentation |
| --- | --- | --- |
| Browser | Existing local overview/result/filter selection; seven filter columns; at most 48 visible items, including names, selected flags and hit counts | Overview headings and options, eight columns of six list rows, explicit empty-results message, preview color |
| Scales | At most 128 scale names, twelve root names, selected scale/root, chromatic flag and reported note range | Six-row selected scale window, root choices at physical columns 1–6, chromatic option at column 7 |
| Scale Layout | Twelve layout names and selected ordinal | Six layout families, separate orientation at column 7 |
| Fixed Length | Observed default-length index | Eight immediate-create choices and eight stored-length choices; only the latter reflects selection |
| Note Repeat | Period/length values, up to 128 configured mode names, selected mode, octaves, latch/pressure/free-running/shuffle flags, groove values and physical touch | Two six-row timing windows, independent toggles, and bounded parameter fields/rings |
| Add Track/Device | Existing chooser kind and seven optional shortcut names | Track/device actions with core-resolved kind colors, primary Empty/Browse action, shortcut choices |

`OptionColumn` composes the shared `ChoiceCell` geometry; `TextList` uses the same full-height 3px
marker and bounded typography. Neither paints a filled selection background. Colors are supplied
values. All text uses the existing Lato transport and family styles. Names retain their observation
value; core bounds candidate text before font fitting to keep dense pages inside the display command
work budget. A full 48-item browser page must remain renderable even when every source name is long.

Browser filter hit counts have a separate bounded trailing field so long names cannot hide them;
their formatting, wildcard suppression and layout labels belong in core.
Selection still comes from host read-back: requesting the next browser result does not change the
published selected row until the host reports it. The legacy nearest-resolution lookup is retained
for Note Repeat; free-running means Sync is unselected, and shuffle and groove enablement remain
independent. Display selection is not used to infer hardware-light policy.

## Deferred Color picker

Physical pad drawing, target selection and return remain together in the existing implementation.
The display cutover adds no Color palette observation, renderer or catalog specimen.
TODO: migrate Color pad drawing/selection together once Note/Drum→Color native-note suppression and exact return handoff can be owned.

## Validation

Validated runtime source `62d36d0eaf1e81a01594c9db7c0f183931a97232` on 2026-09-08 UTC,
Core API 54 / Bitwig API 25. The deprecation-enabled package passed 1,064 tests (557 core,
11 publisher, 496 shell), with no deprecation warnings in changed code. The eight debugger-server
tests and JavaScript parse check passed. The [catalog](../ui-component-library.md#offline-catalog)
contains 10 components, 35 variants and 72 view stories; desktop and 390px review covered navigation,
search, direct links, colors and tall specimens.

| Activated artifact | Identity |
| --- | --- |
| Installed shell SHA-256 | `21e411359ff42f289dc943ffec75a6737e9f9ee3ab7d2327b00d6ec036a9b124` |
| Active core build | `20260908T034033Z-0a52783d6e366a6b2e3b693154c9a28e` |
| Active core SHA-256 | `f816f69f6859cae4a958869016309f257a44aec0cb45c2dfe750522912785978` |
| API fingerprint | `7a1c433c42bf85e118c49704c2756a946bcd85f9` |

The shell was built at `4ac66e3a`; its parent-loaded code is unchanged in the validated runtime
source. Restart, exact core activation and live testing held one uninterrupted live lease.
Existing debugger input drove Track, Master, Device, User, Clip, Scales, Scale Layout, Fixed Length,
Add Track, Quantize, Groove and Note Repeat. Later raw observations and transmitted display captures
confirmed their pages. Host Browse showed its overview and actual result names. Clip loop encoder
changes produced separately observed host `false` then `true` states and matching transmitted
toggles, restoring the original enabled state.

Remote Play from a second project produced later engine-owner `playing=true` read-back and
transmitted Perlin frames expanding from the bottom-left, with the original 24×20 pixels and a
fading trail. A second Play restored `playing=false`; closing the temporary project returned to
the original engine owner with no pending command. The display returned to its underlying page.

Other page variants have offline coverage only. These checks do not migrate remaining actions,
providers or lights, or validate the deferred Color picker and piano roll. Follow
[TESTING](../../TESTING.md) for further routed and live coverage.
