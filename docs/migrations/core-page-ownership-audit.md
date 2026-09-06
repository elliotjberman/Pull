# Core page ownership capability audit

Requested 2026-09-06. Base: migration checkpoint `7a0484ee`, integrated with Pull master
`00b341e0` at `9f0d575f`. This extends the migration by removing inherited page-manager ownership;
it does not declare the unmigrated Browser/Device/configuration/sequencer bodies migrated.

## Capability audit

- **Feature:** core owns page identity, composition, persistent/previous/temporary selection and
  all migrated page behavior/presentation. Stable supplies requested DAW data, host effects and
  generic hardware projection.
- **Inputs:** existing eight encoder RELATIVE/TOUCH routes, two button rows, page/navigation/global
  buttons and their established gesture/modifier variants. No second hardware callback.
- **Variants:** preserve normal/VS Track, Master short/Frame hold/Browser guards, Accent hold,
  Automation Delete/write/hold, Metronome latch/return, Track/Mix held return, mixer page menus,
  legacy page entry/exit and subsequent return, workspace default pages and grid retention.
- **State:** controller page state is authoritative in core. Actual parameter, selection, browser,
  transport and musical-route outcomes still require later host read-back. Local navigation is
  never injected into a Bitwig snapshot to make a fake acknowledgement.
- **Effects:** existing parameter/transport/project effects remain. Migrated page changes become
  local core transitions after the existing parameter-restoration barrier. Frozen legacy callers
  submit bounded sequenced requests; their reducer and history reside in core.
- **Output:** reuse complete scene/light/input arbitration. One inert installed page footprint
  realizes any core page; no shell catalogue of new core UI pages. Existing legacy bodies remain
  selected only through explicit compatibility references and retain their frozen claims.
- **Handoff:** checkpoint typed selected/previous/latching temporary page state and request
  acknowledgement. Physical holds remain generation-fenced; pending compatibility requests and
  in-progress projection cannot be lost during replacement.
- **Coverage:** named parameter banks, generic graphics primitives, lights, input claims and
  retained grid instances already exist. The missing mechanism is full replayable page projection
  plus a bounded legacy-request inbox, independent from grid/Note lifecycle.
- **Classification:** Class B. Core API and stable manager/adapter projection change once, requiring
  a build/install/restart. New pages, navigation and styles within this footprint then reload in core.
- **Out of scope:** new product interactions, general async quiescence redesign, Session release
  contract, migration of unrelated legacy page bodies and learned-MIDI protocol redesign.

## Typed page and presentation boundary

`PageId` and immutable `Page` describe a core page and its fixed behavior/presentation footprint.
The page registry owns definitions; navigation owns the selected identities; the composition
compiler checks claims and preserves the existing grid objects. Legacy mode aliases belong to a
compatibility mapping, not page identity or rendering.

Feature views project authoritative data into typed presentation models. Pure renderers accept
those values and shared/family styles, returning graphics and light output only. Renderers cannot
look up host proxies, emit effects, acquire targets or navigate. Avoid a universal configurable UI
schema and preserve existing family-specific geometry/formatting.

## Required regression evidence

- New arbitrary core page ID activates through the same installed footprint, without a new stable
  registration or mode enum value.
- Page selection, temporary ownership, nested/interleaved holds, stale deferred intent and legacy
  return are deterministic core state; rejected/stale legacy requests cannot replace a newer page.
- Ordered compatibility batches retain their common origin; replay/acknowledgement is idempotent.
- Legacy lifecycle callbacks cannot recurse through projection or revive an old Browser restore.
- Page replacement retains actual grid/gesture objects and exact parameter touch cleanup.
- Presentation scenes/row lights preserve the observed values and established visual output;
  control input cannot optimistically manufacture successful Bitwig state.
- Full deprecation-enabled package, finishing architecture/code-size review and exact-build live
  smoke in the user's `202arp` project. Record physical learned-MIDI limitations separately.

Retained compromises and any shortcuts continue in `migration-shortcuts-and-friction.md`.

## Original-view release follow-up

Elliot clarified that a release should return to the view that received the press, even when a page
changes while it is held. This belongs to one core input-lifetime mechanism, not individual buttons.

- Capture exact edge receivers and action ownership at BEGIN. LONG/END use that capture; unmatched
  releases are inert. A new press uses the current composition.
- Retain offscreen receivers' data and deferred actions until their interaction finishes. Their
  presence never grants them display/light ownership or new page input routes.
- Preserve exact parameter-touch continuation using already-applied target identity and the parent
  router's exclusive physical gesture/generation. A page switch cannot acquire a different target
  through the continuation permission.
- This changes edge completion routing. Continuous knob turns and pad pressure keep their existing
  transport policy; generalizing all motion relationships is a separate bounded canopy change.
- Preserve host target fences: retaining a view does not authorize an action on a different track,
  parameter binding or project. Runtime replacement still waits for the parent input boundary.
