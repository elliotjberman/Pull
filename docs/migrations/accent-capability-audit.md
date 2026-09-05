# Accent control and page capability audit

Baseline: inherited `AccentCommand`, `AccentMode`, `BaseMode`, `TwosComplementValueChanger`,
configuration observers, and native-note translation consumers inspected 2026-09-05. This audit
preceded exclusive cutover. The focused offline reactor passed Accent16, Drum38, controller-settings4,
and native-translation-arbiter4 cases; the full package and first live smoke remain pending.

## Complete physical behavior

Accent DOWN clears its long-press return state. Short UP toggles the configured fixed-accent enable
flag. LONG opens temporary ACCENT; UP restores only after the requested temporary entry is observed.
No Shift/Delete/Select variant changes the Accent button's operation. The permanent light reports
observed accent enabled state, not temporary-page visibility or the last request.

All eight top encoders edit the same fixed velocity. The inherited page explicitly chooses the
minimum sensitivity rather than the user's fine/default preference: decoded delta × installed base
step × 0.1, with an integer result clamped to 1..127. Installed Push calibration is base step10,
therefore one velocity per decoded tick. All eight touches are retained, but the first seven columns stay visually blank. Only
column8 displays Accent and changes brightness when touched. Its inherited ring ratio is
floor(velocity × 1023 / 127) / 1024 at the installed Push range, including the original maximum gap. No parameter automation-touch lease or Delete reset is
part of this page. Both row lights stay off. Upper-row actions are inert. Lower-row UP inherits
plain current-bank track selection from BaseMode, with no arm/delete/group modifier policy.

Core must preserve lower-row selection behind the semantic parameter-restoration barrier, capture
the exact target at BEGIN, execute at END, and fail closed if its owner/window changes. Session
Stop+row consumes the exact shared Session Stop gesture and stops the captured Session-bank track
without selecting it. Both full Session and VS retain their original Stop tokens through the page.

The migrated button declares its possible parameter-context change at BEGIN. A pending Snapback
therefore delays semantic admission, including a short tap when its eventual variant is not yet
known. LONG and END remain value-only continuation state until admission. This is a deliberate
application of the canonical target-safety barrier; the old Accent command bypassed classification.

## Shared canopy and configuration propagation

Controller settings gain observed `accentEnabled` and `accentVelocity`; typed absolute boolean and
bounded integer preference effects reuse installed Bitwig settings. Stable setting schemas and
callbacks remain mechanical. Core owns enable-toggle policy, velocity calculation, page rendering,
temporary entry/restore continuation, and feedback. Generic CorePageMode realizes the inert Accent
page; no new view facet or Bitwig proxy is needed.

The owned Drum native map currently supplies identity velocities. It must derive its complete
128-entry velocity table from later observed Accent settings: zero stays zero; nonzero velocities
use the configured fixed value while enabled, otherwise identity. Parent map actuation still waits
for physical note-input lifecycle safety. Core must not render a requested configuration value as
observed, or claim a newly desired map was already applied.

Unmigrated Play/Chords and sequencer consumers continue reacting to the same configuration unchanged.
Their existing native velocity-table callback remains a frozen legacy baseline; the parent arbiter
cannot overwrite an owned core map with that callback. Moving every remaining note-layout consumer
is a separate complete slice, not an excuse for stable Accent button/page policy.

## Relative-input contract and explicit tradeoff

The canonical permanent router sums relative motion before a controller tick. It does not preserve
individual packet order. At a clamp, sequential packet behavior can differ: starting at127, +1 then
-1 produces126 if each packet clamps, while their coalesced zero produces no core event and leaves127.
The migration deliberately follows the existing summed-relative-input contract, as required by
AGENTS.md; it does not invent packet history or silently claim packet-level parity. A different
clamp/reversal contract would require a broader bounded input-canopy decision and applies to other
ranged controls as well. Core still preserves calibration, bounds, modifier independence, and the
sum represented by every delivered event.

## Verification required

Cover short/long entry and late acknowledgement, END before entry read-back, external page changes,
all eight knobs, calibration/bounds, all touch columns, lower-row selection and Stop chords with
Snapback deferral, configuration submission separate from callback/read-back, and owned Drum map
propagation through parent application. Compare original display behavior at master where needed.
Run the full deprecation-enabled package and then the exact-build live smoke under the singleton
lease. No new direct Bitwig API method is planned; preference setters reuse verified installed
framework settings.
