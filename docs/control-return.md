# Control return

In Bitwig’s Pull controller settings → **Control Return**, choose **Curve → Custom**
and set **Time** (0–2,000 ms; 0 is instant).

Save this spring-shaped example as `~/.drivenbymoss/pull/config.yaml`:

```yaml
control_return:
  curve:
    interpolation: smooth
    keyframes:
      # time, remaining displacement
      - [0.00,  1.00]
      - [0.35, -0.25]
      - [0.60,  0.10]
      - [0.80, -0.03]
      - [1.00,  0.00]
```

Time is a fraction of the duration. Displacement 1 is the released value; 0 is the original;
negative goes past the original. Use an original value away from the parameter’s limits to hear
both sides of the bounce. Hold Shift, change a supported knob, release Shift.

`linear` joins points directly. `smooth` uses cubic curves with automatic tangents, flat endpoints,
and no extra overshoot between points. Use 2–32 points, strictly increasing times (at least
0.000001 apart), displacement −4..4, and endpoints exactly `[0, 1]` and `[1, 0]`.
Actual parameter writes are clamped to their legal range.

After editing, reload the core (`tools/reload-core` inside `tools/with-pull-live --owner control-return`).
The core reads YAML on every reload; no Bitwig restart is needed for configuration edits. A running
return finishes before replacement. No valid YAML (missing, empty, unreadable or malformed) means
Linear. Invalid YAML shows a brief notification. Linear/Ease-out selections ignore the custom curve.
Unknown keys, duplicate keys, aliases,
multiple documents, files over 16 KiB, and nesting beyond 8 levels are invalid and use that fallback.

**First install requires the updated shell and a Bitwig restart.** The renamed settings may need
reselecting. Afterwards YAML edits only need a core reload.

Implementation: core owns both `CoreConfigurationFile` (bounded file I/O) and
`ControlReturnConfiguration` (YAML parsing); `KeyframeCurve` evaluates immutable data; `SnapbackSession` owns the existing lifecycle.
