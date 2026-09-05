# Controller page and navigation capability audit

The installed page declaration is mechanical state. `installedModeId` selects a registered
`CorePageMode` footprint for admission; it does not call `setActive()`. Existing Project, Track,
and Master facets retain their prior mechanical selection during their conversion. Their declared
mode must agree with that selection. New temporary pages use generation-fenced SELECT, TEMPORARY,
and RESTORE operations and render only later mode read-back. The mode manager has one temporary
slot, and `restore()` leaves `previousID` unchanged. Layout generation includes visible, underlying,
previous, and temporary state as well as the existing view/layout fields.

`CorePageMode` makes top encoder bindings, turns, touches, and both soft-key row callbacks inert.
Its final row light methods transmit core RGB state and its base display is blank. The same installed
class supplies the reusable input/light footprint to runtime validation. An arbitrary registered
legacy mode cannot claim that footprint. Page metadata never remaps callbacks or revives deleted
policy after failure. Master retains only its existing host indication lifecycle.

## Navigation parity and scope

The original Track mode inherited ordinary current-bank page movement and Shift cursor-track swap
from AbstractTrackMode. Those methods were lost when its adapter became a bankless BaseMode; they
must be restored in core before the migration checkpoint is treated as navigation-complete.
Volume and Pan inherit the same recipe. Other migrated parameter pages have inert left/right
behavior. All pages use current-bank scene step on Up/Down and scene page with Shift. VS Live's
SESSION_NAVIGATION profile instead uses current-track page on Left/Right and track step with Shift,
including when a legacy parameter page is visible. Full Session is distinct from that VS profile.
Scene/page/sequencer buttons outside these four arrows remain unchanged.

Core owns those recipes, mutable modifiers, press phases, and lights. Arrow commands trigger on DOWN
and submit regardless of whether the current availability light is off. Normal available arrow
lights use RGB60 (mono intensity30); unavailable arrows are black. Left/right semantic navigation
waits behind parameter restoration; up/down uses the direct event path. The permanent arrow command
and light suppliers become inert for declared migrated page/VS profiles only after complete core
input, behavior and feedback are composed.

## Reusable bounded canopy

CurrentTrackBankSnapshot gains independent track/scene navigation values, scene offset, and a
navigation generation. The independent generation fences the existing finite current-bank window,
scene offset, project identity and model cursor ID/pin/position without invalidating track actions
solely because the scene window moves. CurrentTrackNavigationEffect requests one typed track step,
track page, scene step, scene page, or cursor swap operation. Prepare and apply both recheck the
observed navigation origin; availability booleans are feedback, not permission to submit.

The host reuses exactly the installed three current-bank resources and their scene banks. No bank,
Bitwig proxy, or observer is created by a reloadable core. Cursor swaps reuse the existing initialized
CursorTrackImpl actuator and its bounded large track bank; they remain aimed at the explicitly
observed model cursor, including its pin state. No global by-ID actuator or unbounded scan is added.
Sampling occurs only under CURRENT_TRACK_BANK. A later unsubscribe publishes typed empty state.

Offline checks cover raw temporary state and hidden-origin changes, inert adapter admission,
conflicting facet declarations, exact bank/scene/cursor/project fences, command submission separate
from later host advancement, and unchanged legacy arrow behavior. Root owns routed core parity and
live verification under the singleton lease. The first live smoke test is still required.
