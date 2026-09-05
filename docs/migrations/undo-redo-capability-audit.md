# Undo/Redo capability audit

Scope: the complete Push Undo button, including Shift Redo and its availability/pressed feedback.
The framework command executes Undo on normal UP and Redo on shifted UP; DOWN and LONG do nothing.
Shift is read at release. Other modifiers and active views add no variants. The button is off when
the selected history direction is unavailable, otherwise monochrome intensity 30 idle and 127 held.

Installed canopy: permanent `UNDO/BUTTON`, observed Shift and physical pressed state, project identity,
and generic monochrome output translation. `ApplicationImpl` already eagerly marks `canUndo()` and
`canRedo()` interested. API 25 `Application.undo()`, `redo()`, `canUndo()`, and `canRedo()` were verified
in the resolved source JAR; none is deprecated.

Expansion: publish the two availability flags with the existing lightweight PROJECT subscription,
and add a native project-fenced Undo/Redo effect. These are project history primitives; the stable
adapter does not inspect modifiers or physical controls. Prepare and apply recheck the exact current
project and availability. The native void command remains submission; later read-back drives lights.
No guessed history sequence, UI selection side effect, or completion receipt is introduced.

Core: one retained fixed Undo BUTTON input/output profile, observed Shift, release dispatch, and
lights from project availability and observed physical held state. The Push binding and supplier
become inert/core-only together; the shared framework command remains available to other controllers.
Missing/faulted core leaves Undo inert/off. The existing Shift edge route and core generation stay
frozen through the button gesture; release reads current Shift to preserve behavior.

Verification: exercise Undo/Redo release variants, dynamic Shift, LONG, unavailable history,
project disagreement, exact stable apply fencing, read-back-only lights, and missing-core ownership.
The live smoke must perform a known reversible edit in the controlled test project, observe its
authoritative value, Undo, observe restoration, Redo, observe reapplication, and verify button light
output under the singleton live lease. Offline command counts do not prove that Bitwig changed history.
