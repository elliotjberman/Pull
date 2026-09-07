# Session launcher contract

Core API 49 moves Session actions and feedback into the reloadable `SessionView`, using the shared
interaction lifecycle. Shell Session/Workspace views retain only neutral hardware and bank setup.
The installed windows are 8×8 and 8×4, with at most 64 slot and eight scene launch presses.

A launcher location contains project identity, bank generation/shape, track channel identity and
absolute scene position. It is an address in a ready, aligned window, not a durable clip-content ID.
Selected-track changes alone do not invalidate it. Prepare and apply both verify live addressability.

Core owns modifiers, select-on-launch, armed-empty-pad preferences, copy-source lifetime, create/record
sequencing, birds-eye/page navigation, Stop chords, scene variants and colors/blink rates. Only an
actual launch acquires a matching main/alternate release, captured at BEGIN. Modifier-only actions
have no orphan release. A normal quick tap retains create/record intent until later host read-back;
then it submits launch and matching release in order. Pending continuations fence core replacement;
view or target loss cancels them. Paging selects only after the requested window is observed aligned. A workspace change that alters
native note translation waits for physical pads to become idle before admitting its bank/layout.

The shell exposes primitive slot/scene operations and absolute bank positions. One shared host
wrapper installs the bounded press ledger's structural-mutation guard. Actual track, scene and
bank proxy methods enter it before Bitwig submission, so core and frozen callers inherit the same
cleanup. Opaque application edits, history and project navigation conservatively end outstanding
holds before submission, even when the eventual edit affects something else. Ordinary selection,
arming and parameter writes preserve holds. Handlers do not call Session cleanup. The ledger also
releases on core replacement, fault or exit. External scene edits or proxy rebinding can remove addressability
first; cleanup then retires with a diagnostic and never mutates a replacement target.

API 25 `launchRelease()` / `launchReleaseAlt()` are void submissions. Configured release can leave
playback unchanged. This contract preserves correctly targeted release submission; it does not
promise completion, arbitrary offscreen retention or guaranteed restoration after external edits.
Timers and `flush()` do not provide that stronger acknowledgement.

Offline routed behavior and host-boundary tests cover both shapes, modifier variants, delayed host
read-back, target changes, cleanup ordering and blinking output. Exact API 49 live smoke is pending.
