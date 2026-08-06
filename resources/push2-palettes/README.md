# Push 2 indexed color palette

This directory is the source of truth for Push 2 pad and RGB-button colors:

- `profiles/bitwig-calibrated-v2.json` is the production palette.
- `profiles/current.json` preserves the exact pre-change DrivenByMoss palette.
- `calibration/evidence.json` records the compact physical evidence behind the
  production compensation.
- `calibration/README.md` explains the methodology and its limitations.

`PushPaletteData.java` is generated from the selected JSON profile and includes
its SHA-256. Do not edit that Java file by hand.

## Why this exists

Bitwig supplies ordinary RGB colors. Push 2 grid messages do not: each pad
receives a 7-bit index into 128 device palette slots. The slots can be
programmed with RGB bytes, but the physical result is not an sRGB display. The
LEDs, pad diffuser, firmware, brightness, and white-balance settings produce
nonlinear and hue-dependent shifts.

Ableton's factory palette is a set of hand-chosen device colors, not a
published inverse model for arbitrary Bitwig RGB. The old DrivenByMoss path
reduced Bitwig colors to 27 canonical entries; that made pale colors collapse
to white and unrelated colors share a pad appearance.

Every production entry therefore stores two values:

- `targetRgb`: the logical color used for nearest-color matching;
- `programmedRgb`: the compensated bytes written to the Push palette.

Each profile also stores all 128 indexed `whiteValues`. This is a parallel white-only palette value,
not a fourth emitter mixed into the RGB pad color. Push palette writes replace RGB and that value
together, so a complete profile lets startup write known values without first reading every slot.
The values were cross-checked against Ableton Live 11's Push 2 `COLOR_TABLE`; slots 64–127 were also
captured from the tested Push, and the hardware read-back is retained where available.

The hardware-specific lookup remains at the final output boundary:

```text
Bitwig RGB -> nearest logical target/index -> programmed Push RGB -> pad
```

Matching uses Euclidean OKLab distance. The Push LCD is a separate direct-RGB
path and never uses this indexed palette.

## Startup synchronization

Once the Push answers the bounded device inquiry, the controller sends all 128 RGBW entries and the
documented Reapply command before flushing the first cached grid frame. This mirrors Ableton Live's
set-first sequence and avoids briefly rendering session colors through a stale device palette.
The upload path does not issue palette Get commands and has no fixed one-second wait.

After the first frame, a background pass reads one entry at a time. It repairs and rechecks a
mismatch, but verification cannot delay visible startup. Bitwig's console reports both the time to
queue the palette and the total background-verification time.

## Index constraints

The 128 positions are not interchangeable. Existing controller behavior
expects state colors at fixed indices, and the framework DAW block occupies
indices 70–96. The production roster keeps 52 semantic entries fixed and uses
the other 76 for exact Bitwig factory colors selected for perceptual coverage.

Palette index 0 is always `#000000` and physically off. Dynamic Bitwig black
instead resolves to index 1, programmed as `#040404`: the dimmest neutral that
was clearly visible on the tested Push. One step darker appeared yellow, so
this is a guarded hardware-floor exception rather than a curve-fit value.

## Profiles and generation

Only two profiles are intentionally checked in. The baseline exists for
comparison and rollback; it is not Ableton's native palette. Superseded
experiments are summarized in the calibration methodology rather than kept as
additional 128-entry runtime choices.

```sh
bash tools/push2-palette.sh list
bash tools/push2-palette.sh status
bash tools/push2-palette.sh use current
bash tools/push2-palette.sh use bitwig-calibrated-v2
bash tools/push2-palette.sh check
```

`use` validates the selected JSON, updates `active.txt`, and regenerates
`PushPaletteData.java`. Rebuild the extension and fully restart Bitwig after a
profile change; JVM hot swap does not reinitialize the device palette.

## Production result

The logical roster covers Bitwig Studio 6.0.11's 135 unique factory swatches
with 102 exact matches. OKLab distance multiplied by 100 is mean `0.551`, p95
`3.102`, maximum `3.611`.

Unmeasured entries use a compact response model fitted from 32 physical
comparisons. Ten material colors use physically approved local drive values.
Successful held-out values remain validation evidence instead of growing the
override table. See [calibration/README.md](calibration/README.md) for the
measurement process and caveats.
