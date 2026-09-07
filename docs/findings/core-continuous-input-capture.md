---
status: active
created: 2026-09-06
scope: controller-input-lifetimes
remove_when: shared binding-loss cancellation, physical-tail suppression, declared edge-to-motion relationships and exact target fences are integrated and tested across core/core and core/legacy transitions
---

# Edge ownership does not yet imply continuous-motion ownership

This is a Pull architecture boundary, not an established Bitwig API limitation.

The user now chooses **cancellation when the target leaves its active binding**, with remaining
physical input suppressed until a fresh gesture. The [isolated lifecycle manager](../interaction-lifecycle.md)
implements and tests that decision independently; production integration is still pending. This
supersedes retaining offscreen edits as the goal below, while part 1's shipping behavior is unchanged.

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
- One integrated cancellation path for those relationships, without duplicating current/old
  handlers or delivering a cancelled physical tail to the replacement view.
- Exact parameter slot/owner/page/target fences: a retained Java view can itself change send offset
  or encounter a rebound proxy. Retaining the view is not permission to write the new target.
- Tests that distinguish core/core, core/legacy, target change, release, cancellation and generation
  replacement, plus routed live evidence. A missing relationship must not silently claim coverage.

Do not add per-button shadow listeners or retain an old page's full exclusive route map as a
workaround. Required cleanup still belongs to the exact original target; cancellation is distinct
from a normal release action. Standalone manager tests do not satisfy the integration removal criteria.
