# Push 2 color calibration lab

This standalone macOS tool preserves the hardware calibration interface used
to develop the production Push 2 palette. It intentionally lives on the
`codex/push2-color-lab` branch rather than in the production palette PR.

The tool can inspect palette slots without writing them, render LCD reference
colors, temporarily program an 8x8 candidate bank, record selections, and
restore the original palette. It is experimental hardware tooling, not part of
the Bitwig extension build.

## Safety

`calibrate` is the normal interactive workflow. It backs up every palette slot
it will touch and restores those values when the window closes or **Finish &
Restore** is selected. If the process is interrupted, run the printed
`restore` command before another calibration. Do not disconnect or power-cycle
the Push while temporary palette values are active.

Only `calibrate` and `restore` write palette entries. Both require the Push
Live Port. Read-back is verified before a temporary value is treated as active.

## Run

JDK 21 and Apple's command-line developer tools are required. Build the main
project once so its classes are available, then list the MIDI port names:

```sh
mvn -o package
bash tools/push2-color-test.sh list
```

Start the built-in calibration bank using case-insensitive substrings from the
reported Live Port names:

```sh
bash tools/push2-color-test.sh calibrate \
  --input "Push 2 Live Port" \
  --output "Push 2 Live Port"
```

Pass an optional bank JSON path after `calibrate` to load a custom 8x8 bank.
Run `bash tools/push2-color-test.sh --help` for inspection, dump, comparison,
validation, and explicit restoration commands.

Calibration backups and the interrupted-restore marker are stored under
`~/.drivenbymoss/push2-color-calibration/` unless
`PUSH2_COLOR_CALIBRATION_DIR` overrides that directory.
