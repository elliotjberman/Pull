# Push 2 pad calibration methodology

This directory explains why the production palette contains compensated Push
RGB values and records the normalized result in `evidence.json`. It does not
contain the exploratory calibration application or raw 8×8 sessions; those are
lab artifacts, not production dependencies.

## What was measured

The process kept four values separate:

1. Bitwig's logical sRGB target.
2. The controller-safe index selected for that target.
3. The RGB bytes programmed into the Push palette entry.
4. The light emitted by one physical Push 2.

All physical passes used LED brightness `127` and pad white balance R/G/B
`440 / 475 / 324`. The comparisons are subjective visual matches on one unit,
not colorimeter measurements or a universal Push 2 characterization.

## Logical roster

Bitwig Studio 6.0.11 ships 135 unique factory swatches. Fifty-two existing
controller-state entries had to stay at their expected indices. The remaining
76 slots were filled with exact Bitwig colors using deterministic greedy
facility-location medoids in OKLab. Nearest-color matching always uses this
logical roster; it never compares Bitwig colors with device-compensated bytes.

## Broad physical model

Four early 8×8 sessions compared 32 targets with power-curve candidates. A
compact RGB-dependent gamma fit performed better under held-out target and
held-out session checks than one shared gamma:

```text
gamma = 2.56 + 0.46*luminance - 1.06*chroma - 0.29*redShare
```

This is a provisional fallback, not a claim that the Push is a smooth color
space. The original candidate layout could not identify small signed hue
errors, and the device behaves discontinuously near its LED floor.

## Direct tuning

Important targets were then tested by repeating one programmed RGB down each
physical column. A desktop swatch supplied the stable target; the Push LCD was
useful for nearby hue comparison but not authoritative for very dark
brightness. Dragging adjusted one shared column offset in OKLab, and palette
read-back was verified before a pad press could confirm the value.

The first pass tuned eight representative session colors. A held-out risk pass
tested eight new logical entries: seven were accepted without a material
correction, while saturated orange needed a small local shift away from red.
The correction was deliberately not extrapolated because a slightly larger
move quickly appeared coral.

## Black and the LED floor

Programmed `#000000` and several near-black values were physically unlit.
This explains Ableton's likely product choice to show black content with a dim
visible color: true emitted black is indistinguishable from absence.

A neutral ladder tested programmed values from `#020202` through `#202020`.
`#040404` was selected as the dimmest clearly visible neutral. One adjustment
step darker turned yellow, exposing a channel/white-balance threshold rather
than a smooth brightness curve.

The runtime therefore preserves two semantics:

- index 0, programmed `#000000`: empty/off;
- dynamic Bitwig black, resolved to index 1 and programmed `#040404`: visible
  black content.

## Promotion policy

The broad model handles entries without direct evidence. A physical result is
promoted only when the mismatch is material and repeatable. Tiny hue changes
described as imperceptible or “vibes” stay validation evidence. This prevents
the final palette from becoming an opaque 128-color hand-tuned lookup while
still respecting the Push's real discontinuities.

`evidence.json` records the device settings, fit coefficients and validation
metrics, ten promoted anchors, and summaries of the physical passes. The final
palette JSON records the actual bytes used at every index.
