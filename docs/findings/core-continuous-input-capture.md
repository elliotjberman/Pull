---
status: active
created: 2026-09-06
scope: controller-input-lifetimes
remove_when: declared physical edge-to-motion relationships and exact target fences are enforced and tested across core/core and core/legacy transitions for every supported relationship
---

# Edge ownership does not yet imply continuous-motion ownership

This is a Pull architecture boundary, not an established Bitwig API limitation.

The API46 page refactor adds one core `InputGestureRouter`: BEGIN captures exact view receivers,
LONG/END return there, and new presses use the current composition. The shell independently freezes
an edge's route disposition and core generation. Those mechanisms preserve original-view release
without retaining the old display or claiming new inputs on its behalf.

Continuous motion has a separate path. `PushControllerInputBridge` currently declares the existing
TOUCHSTRIP TOUCH→ABSOLUTE relationship to `PhysicalInputRouter`. It does not declare general encoder
TOUCH→RELATIVE or PAD→POLY_PRESSURE relationships. Consequently this refactor deliberately preserves
current-page motion routing, even while a prior view owns an outstanding touch release.

A complete extension needs:

- Bounded mechanical declarations for related physical edge/motion addresses, installed once.
- BEGIN-time capture of related route/generation in the parent, including transitions into legacy
  pages where controller callbacks would otherwise run before core sees the motion.
- Core receiver capture for those same relationships, without duplicating current and old handlers.
- Exact parameter slot/owner/page/target fences: a retained Java view can itself change send offset
  or encounter a rebound proxy. Retaining the view is not permission to write the new target.
- Tests that distinguish core/core, core/legacy, target change, release, cancellation and generation
  replacement, plus routed live evidence. A missing relationship must not silently claim coverage.

Do not add per-button shadow listeners or retain an old page's full exclusive route map as a
workaround. The original-view release guarantee can be completed independently of this broader motion work.
