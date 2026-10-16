# Push display CPU benchmark

Benchmarks the complete CPU encoding path: a 960×160 BGRA bitmap, existing color calibration,
BGR565 packing, 1024-pixel row stride, signal-shaping XOR, and the copy into a direct USB buffer.
One operation is one frame. Rendering, controller callbacks, scheduling, locks and physical USB
transmission are excluded; these results do not measure input-to-screen or audio latency.

The retained scalar reference is the encoder/copy body from
`fd562c842a2079a0cf72026f91dd5c5b48f1714e`. `baselineBulkCopy` changes only the final copy.
`optimized` calls the production `PushFrameEncoder`. Every fixture's complete output is checked
against the reference before measurement. JMH consumes an output byte at a changing index.

## Implementation contract

This is a mechanical hardware-translation change. Physical input, view ownership, page drawing,
calibration constants, effects, core subscriptions, asynchronous submission and host acknowledgements
are unchanged. The existing display mutex still serializes encode/copy. No Bitwig API calls,
proxies or parent/core contracts were added. Activating the changed shell requires an installation
and restart; the benchmark does neither.

The encoder owns one 960×160 raw input cache, one 960-int input row, eight 60-int scratch arrays
and 163,840 output shorts: 947,840 bytes of primitive array storage plus object headers. A whole-frame
NIO mismatch check can reuse unchanged input without first copying it; changed rows are checked in
60×1-pixel cells. Two cells fit a common 120-pixel column, but cells carry no layout semantics.
Comparisons are exact; no hash match can hide changed pixels. Every frame still reaches the existing
USB sender. Padding is initialized once in its final shaped representation.

`PushFrameEncoder` is a separate pixel-only transport class. It never reads display components,
views, layout ownership or drawing commands. Any renderer can supply the finished bitmap; adding
or overlapping components needs no cache invalidation hooks. `PushDebugFrame` separately caches
immutable pre-calibration pixels and worker-generated PNG bytes for the optional debugger. See
`TESTING.md` for observation versus content generations and capture freshness contracts.

Small loops over row-local int arrays permit HotSpot automatic vectorization. Identical indices
avoid possible overlapping-array dependencies. Calibration preserves signed integer division's
rounding, the chroma threshold and clamping exactly. Packing and the final short narrowing are
separate because this JVM does not vectorize the mixed-width loop. The grayscale fast path applies
only when every pixel in a cell is already exempt from the existing calibration.

## Reproduce

Requires Python 3, a JDK compiler and the JVM to measure. The tool defaults to Homebrew javac and
Bitwig's installed arm64 JVM on this Mac. Override `PULL_BENCH_JAVAC` and `PULL_BENCH_JAVA` for
another installation. It compiles with `--release 21` and downloads pinned JMH 1.37 dependencies
only into ignored `target/display-benchmark/deps`; they are never packaged in Pull.

```sh
tools/benchmark-push-display -p content=ui,random,gray,unchanged -f 3 -wi 4 -i 5 \
  -rf json -rff target/display-benchmark/timings.json
```

Each workload rotates eight input frames. Random RGB and moving grayscale change every row.
Synthetic UI changes colored bars on a black background. Unchanged UI repeats identical pixels.
For real screens, put eight unique, pre-calibration 960×160 PNG captures in
`target/display-benchmark/frames`, then select `content=capture` and pass
`-jvmArgsAppend '-Dpull.benchmark.frames=ABSOLUTE_DIRECTORY'`.
Captured project images are intentionally kept out of Git; the evidence includes their SHA-256
manifest. They are old captures replayed offline, not observations of the running Bitwig session.

`run-evidence.py` runs the full sequential suite: three-fork average-time comparisons, a
`-XX:-UseSuperWord` control, two-fork sampled latency, separate async-profiler CPU profiles and
a JIT assembly dump and a GC-allocation check. It requires the official
[async-profiler 4.5 macOS archive](https://github.com/async-profiler/async-profiler/releases/download/v4.5/async-profiler-4.5-macos.zip)
extracted under `target/display-benchmark/deps`. The profiler runs inside standalone benchmark
processes; it does not attach to Bitwig. Raw arguments are saved in `commands.json`.

`disassemble.py` decodes the AArch64 C2 hex dump using Capstone.
`make-report.py` renders results with Matplotlib and writes the standalone HTML report.
Their Python dependencies can be installed locally under
`target/display-benchmark/deps/python`, with that directory supplied as `PYTHONPATH`:

```sh
python3 -m pip install --target target/display-benchmark/deps/python capstone matplotlib
mvn -o -Dmaven.compiler.showDeprecation=true package > target/display-benchmark/package.log 2>&1
python3 benchmarks/push-display/run-evidence.py
PYTHONPATH=target/display-benchmark/deps/python python3 benchmarks/push-display/disassemble.py \
  target/display-benchmark/assembly.log > target/display-benchmark/neon-assembly.txt
PYTHONPATH=target/display-benchmark/deps/python python3 benchmarks/push-display/make-report.py
```

## Verification and results

`PushFrameEncoderTest` covers every 24-bit RGB color, varying alpha, row-cache updates,
grayscale threshold edges, heap/direct/read-only buffers, byte order, row padding and the exact
header/frame sent through the real `PushUsbDisplay` worker to a fake USB endpoint.

[Measured report and evidence](results/2026-10-15-m5-max/index.html).

The runtime is Bitwig's bundled Zulu Java 25.0.4.1 on an Apple M5 Max, using default VM flags.
The incubating Vector API is absent from that runtime. The optimization uses ordinary Java and
HotSpot's 128-bit NEON code generation. Performance on other CPUs/JVMs needs its own measurements.

## Cell-cache follow-up

[Cell and debugger cache evidence](results/2026-10-16-cell-cache/index.html) compares production with
`RowFrameEncoder`, the first SIMD implementation frozen at `28b7059c`. Seven encoder workloads
include controlled moving-playhead, 16×16-patch and one-column macro updates. Larger 120-pixel-wide
2D cells and a whole-frame-only shortcut were measured in the archived pilot experiments.

`DebugFrameBenchmark` compares snapshot plus PNG CPU work, with the same in-memory compression
sink in both versions. It excludes filesystem/HTTP/browser costs. Debug captures retain original
BGRA colors and alpha; the hardware wire encoder's calibration is not applied to screenshots.

```sh
mkdir -p target/display-benchmark/round2
mvn -o -Dmaven.compiler.showDeprecation=true package > target/display-benchmark/round2/package.log 2>&1
python3 tools/test_push_debug_surface_server.py > target/display-benchmark/round2/server-tests.log 2>&1
python3 benchmarks/push-display/run-cache-evidence.py
PYTHONPATH=target/display-benchmark/deps/python python3 benchmarks/push-display/disassemble.py \
  target/display-benchmark/round2/assembly.log > target/display-benchmark/round2/neon-assembly.txt
PYTHONPATH=target/display-benchmark/deps/python python3 benchmarks/push-display/make-cache-report.py
```

The report builder also expects the archived pilot summaries and sources from this session under
`target/display-benchmark/round2`; unpack `pilot-evidence.tar.gz` there to recreate this report.
The final timing suite itself needs only the fixture/profiler prerequisites above. Benchmarks run
as standalone JVMs and never attach to the live Bitwig host.

The report's `browser-fixture.py` serves the real debugger application against generated pixels
on port 8766. Run it from the repository root. It uses only the ignored
`target/display-benchmark/round2/browser-check` directory and disables input; `allow-image`
controls its initial HTTP-failure condition. `browser-cache-check.json` records the observed
request counts, decoded-image transitions and exact browser/server source hashes.
