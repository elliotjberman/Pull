#!/usr/bin/env python3
"""Turn the evidence suite into a standalone report; requires Matplotlib."""
from collections import Counter
from evidence import flame_stats
import hashlib
import html
import json
from pathlib import Path
import shutil
import subprocess
import xml.etree.ElementTree as ET
import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np

ROOT = Path(__file__).resolve().parents[2]
INPUT = ROOT / "target/display-benchmark"
OUTPUT = ROOT / "benchmarks/push-display/results/2026-10-15-m5-max"
OUTPUT.mkdir(parents=True, exist_ok=True)
for name in (
    "timings.json",
    "without-superword.json",
    "latency.json",
    "commands.json",
    "timings.log",
    "without-superword.log",
    "latency.log",
    "assembly.log",
    "neon-assembly.txt",
    "package.log",
    "package-bitwig-runtime.log",
    "allocation.json",
    "allocation.log",
    "profile-baseline-capture.log",
    "profile-baseline-random.log",
    "profile-optimized-capture.log",
    "profile-optimized-random.log",
):
    if name == "package-bitwig-runtime.log" and not (INPUT / name).exists():
        continue  # Optional diagnostic from the original attempt to run compiler-fixture tests.
    shutil.copyfile(INPUT / name, OUTPUT / name)
manifest = INPUT / "frames/manifest.json"
if manifest.exists():
    shutil.copyfile(manifest, OUTPUT / "capture-manifest.json")
else:
    captures = [
        {"file": p.name, "sha256": hashlib.sha256(p.read_bytes()).hexdigest()}
        for p in sorted((INPUT / "frames").glob("*.png"))
    ]
    (OUTPUT / "capture-manifest.json").write_text(json.dumps(captures, indent=2) + "\n")
records = json.loads((INPUT / "timings.json").read_text())
assert len(records) == 12 and all(r["primaryMetric"]["score"] > 0 for r in records)
values = {
    (r["benchmark"].split(".")[-1], r["params"]["content"]): r["primaryMetric"]
    for r in records
}
labels = {
    "capture": "Real captured screens",
    "random": "Random RGB • all rows change",
    "gray": "Moving grayscale • all rows change",
    "unchanged": "Unchanged UI",
}
order = list(labels)
methods = [
    ("baseline", "Original", "#778594"),
    ("baselineBulkCopy", "Bulk copy only", "#e5a14a"),
    ("optimized", "Optimized Java", "#16a190"),
]
fig, ax = plt.subplots(figsize=(11, 5.6), layout="constrained")
y = np.arange(len(order))
for j, (method, label, color) in enumerate(methods):
    scores = [values[method, c]["score"] for c in order]
    errors = [values[method, c]["scoreError"] for c in order]
    bars = ax.barh(
        y + (j - 1) * 0.23,
        scores,
        0.21,
        xerr=errors,
        label=label,
        color=color,
        error_kw=dict(ecolor="#344455", capsize=2, lw=1),
    )
    ax.bar_label(bars, labels=[f"{s:.1f} µs" for s in scores], padding=6, fontsize=9)
ax.set_yticks(y, [labels[c] for c in order])
ax.invert_yaxis()
ax.set_xlabel("Microseconds per complete frame • lower is better")
ax.set_title(
    "Push display encoding on Apple M5 Max",
    loc="left",
    fontsize=17,
    weight="bold",
    pad=17,
)
ax.spines[["top", "right", "left"]].set_visible(False)
ax.grid(axis="x", alpha=0.15)
ax.set_axisbelow(True)
ax.set_xlim(0, max(v["score"] + v["scoreError"] for v in values.values()) * 1.18)
ax.legend(loc="lower right", frameon=False)
fig.savefig(OUTPUT / "comparison.png", dpi=180)
fig.savefig(OUTPUT / "comparison.svg")
plt.close(fig)


profiles = []
for method in ("baseline", "optimized"):
    for content in ("capture", "random"):
        path = (
            INPUT
            / "profiles"
            / f"de.mossgrabers.controller.ableton.push.controller.FrameBenchmark.{method}-AverageTime-content-{content}"
            / "flame-cpu-forward.html"
        )
        name = f"{method}-{content}-flamegraph.html"
        shutil.copyfile(path, OUTPUT / name)
        total, all_counts = flame_stats(path)
        counts = {
            name.rsplit("/", 1)[-1]: count
            for name, count in all_counts.items()
            if any(
                part in name
                for part in (
                    "PushFrameEncoder.",
                    "LegacyFrameEncoder.",
                    "PushColorCalibration.",
                )
            )
        }
        top = ", ".join(
            f"{html.escape(k)} {100*v/total:.1f}%"
            for k, v in sorted(counts.items(), key=lambda item: item[1], reverse=True)[
                :4
            ]
        )
        profiles.append(
            f'<article><a href="{name}">{method.title()} · {labels[content]}</a><p>{total:,} CPU samples. Inclusive shares: {top}.</p></article>'
        )
rows = []
for c in order:
    a, b, v = (values[m, c] for m in ("baseline", "baselineBulkCopy", "optimized"))
    rows.append(
        f'<tr><th>{labels[c]}</th><td>{a["score"]:.2f} ± {a["scoreError"]:.2f}</td><td>{b["score"]:.2f} ± {b["scoreError"]:.2f}</td><td>{v["score"]:.2f} ± {v["scoreError"]:.2f}</td><td><b>{a["score"]/v["score"]:.2f}×</b></td></tr>'
    )
control = json.loads((INPUT / "without-superword.json").read_text())[0]["primaryMetric"]
normal = values["optimized", "random"]
latencies = json.loads((INPUT / "latency.json").read_text())
latrows = []
for r in latencies:
    p = r["primaryMetric"]["scorePercentiles"]
    latrows.append(
        f'<tr><td>{r["benchmark"].split(".")[-1]}</td><td>{labels[r["params"]["content"]]}</td><td>{p["50.0"]:.1f}</td><td>{p["95.0"]:.1f}</td><td>{p["99.0"]:.1f}</td><td>{p["99.9"]:.1f}</td></tr>'
    )
allocation_rows = []
allocations = json.loads((INPUT / "allocation.json").read_text())
assert len(allocations) == 4
for r in allocations:
    m = r["secondaryMetrics"]
    allocated = (
        f'{m["gc.alloc.rate.norm"]["score"]:.2f}'
        if "gc.alloc.rate.norm" in m
        else "Unavailable"
    )
    allocation_rows.append(
        f'<tr><td>{r["benchmark"].split(".")[-1]}</td><td>{labels[r["params"]["content"]]}</td><td>{allocated}</td><td>{m["gc.count"]["score"]:.0f}</td></tr>'
    )
counts = Counter()
for module in ("pull-core", "pull-core-publisher", "pull-shell"):
    for f in (ROOT / module / "target/surefire-reports").glob("TEST-*.xml"):
        suite = ET.parse(f).getroot()
        for k in ("tests", "failures", "errors", "skipped"):
            counts[k] += int(suite.get(k, 0))
source_names = [
    "pull-shell/src/main/java/de/mossgrabers/controller/ableton/push/controller/" + s
    for s in (
        "PushFrameEncoder.java",
        "PushUsbDisplay.java",
        "PushColorCalibration.java",
    )
]
source_names += [
    "benchmarks/push-display/src/de/mossgrabers/controller/ableton/push/controller/" + s
    for s in ("FrameBenchmark.java", "LegacyFrameEncoder.java")
]
metadata = {
    "base_commit": "fd562c842a2079a0cf72026f91dd5c5b48f1714e",
    "cpu": subprocess.check_output(
        ["sysctl", "-n", "machdep.cpu.brand_string"], text=True
    ).strip(),
    "os": subprocess.check_output(["sw_vers"], text=True),
    "tests": dict(counts),
    "source_sha256": {
        s: hashlib.sha256((ROOT / s).read_bytes()).hexdigest() for s in source_names
    },
    "jvm": subprocess.check_output(
        [
            "/Applications/Bitwig Studio.app/Contents/PlugIns/JavaVM-arm64.bundle/Contents/Home/bin/java",
            "--version",
        ],
        text=True,
    ),
}
(OUTPUT / "metadata.json").write_text(json.dumps(metadata, indent=2) + "\n")
assembly = (OUTPUT / "neon-assembly.txt").read_text()
snippets = []
for line in assembly.splitlines():
    if "; NEON" in line and any(
        " " + m + " " in line
        for m in ("mul", "sshr", "ushr", "smin", "smax", "and", "orr", "eor", "bsl")
    ):
        snippets.append(line)
        if len(snippets) == 12:
            break
summary = {
    c: {
        "baseline_us": values["baseline", c]["score"],
        "bulk_only_us": values["baselineBulkCopy", c]["score"],
        "optimized_us": values["optimized", c]["score"],
        "speedup": values["baseline", c]["score"] / values["optimized", c]["score"],
    }
    for c in order
}
(OUTPUT / "summary.json").write_text(json.dumps(summary, indent=2) + "\n")
page = f"""<!doctype html><html lang="en"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Pull · Java SIMD benchmark</title>
<style>
body{{margin:0;background:#f5f7fa;color:#172c40;font:16px/1.6 system-ui,sans-serif}}
main{{max-width:1100px;margin:auto;padding:45px 28px 70px}}h1{{font-size:38px;letter-spacing:-1px;margin:8px 0}}
h2{{margin-top:40px;font-size:24px}}.eyebrow{{color:#087b70;font-weight:750;letter-spacing:2px;font-size:12px}}
.lead{{font-size:19px;max-width:900px}}.muted{{color:#576777}}a{{color:#087e75}}img{{width:100%;background:white;border-radius:12px}}
table{{border-collapse:collapse;width:100%;background:white;font-variant-numeric:tabular-nums;font-size:14px}}
th,td{{padding:13px 14px;border-bottom:1px solid #e0e7ed;text-align:left}}thead{{background:#e5edf2}}
.cards{{display:grid;grid-template-columns:1fr 1fr;gap:16px}}article{{padding:18px;background:white;border:1px solid #dce5eb;border-radius:10px}}
article a{{font-size:18px;font-weight:650}}article p{{font-size:13px}}pre{{overflow:auto;background:#152a3c;color:#d9f1e8;padding:20px;border-radius:10px;font-size:12px}}
.notice{{border-left:4px solid #e5a14a;padding:12px 20px;background:#fff5e5}}code{{font-size:.9em}}
@media(max-width:700px){{.cards{{grid-template-columns:1fr}}main{{padding:25px 14px}}.table{{overflow:auto}}h1{{font-size:30px}}}}
</style><main>
<div class="eyebrow">PULL / PERFORMANCE EVIDENCE / 15 OCTOBER 2026</div>
<h1>Faster Push frames, ordinary Java.</h1>
<p class="lead">On this Apple M5 Max, the optimized encoder is <b>{summary["capture"]["speedup"]:.2f}× faster on real captured screens</b>,
<b>{summary["random"]["speedup"]:.2f}× faster when every pixel changes randomly</b>, and
<b>{summary["unchanged"]["speedup"]:.2f}× faster for unchanged UI frames</b>. Every output byte retains the existing wire format.</p>
<p class="muted">Measured on Bitwig’s bundled Zulu Java 25.0.4.1. Java 21-compatible source; no native code or incubating Vector API.</p>
<img src="comparison.png" alt="Measured encoding time per frame with 99.9 percent JMH confidence intervals">
<h2>Complete CPU path, per frame</h2>
<div class="table"><table><thead><tr><th>Workload</th><th>Original µs</th><th>Bulk copy only µs</th><th>Optimized µs</th><th>Speedup</th></tr></thead><tbody>{"".join(rows)}</tbody></table></div>
<p class="muted">Mean ± JMH’s reported 99.9% confidence interval. Three separate JVM forks, four one-second warmups and five one-second measurements per fork. Eight input frames rotate in each workload. No measurements were discarded from this final suite.</p>
<div class="notice"><b>Scope:</b> these are offline measurements of encoding and the copy into direct USB memory. Rendering, controller/event handling, scheduling, locks and physical USB transfer are excluded. They do not establish end-to-end display latency or audio-engine performance. Real frames are replayed captures, not a live Bitwig profile.</div>
<h2>What changed</h2>
<ul><li>Bulk BGRA reads into reusable row arrays and bulk writes into the USB buffer.</li>
<li>Small integer loops that HotSpot compiles to 128-bit NEON operations; calibration and bit packing run across four integer lanes.</li>
<li>Exact cached-row equality and an existing-threshold grayscale fast path. Both avoid unnecessary work without approximating colors.</li>
<li>Signal shaping folds into packing; padded bytes are initialized once. Unchanged frames still follow the existing USB submission path.</li></ul>
<p>The encoder owns approximately 0.93 MiB of primitive storage, about 0.62 MiB more than the old byte buffer, plus object headers. The scalar short-narrowing pass remains separate because mixing it into the arithmetic loop prevented vectorization on this JVM.</p>
<h2>SIMD is measured, not assumed</h2>
<p>On the same changing-random workload, disabling HotSpot auto-vectorization with <code>-XX:-UseSuperWord</code> increases optimized time from
<b>{normal["score"]:.2f} µs</b> to <b>{control["score"]:.2f} µs</b> ({control["score"]/normal["score"]:.2f}× slower).
All rows change, so this control does not obtain its gain from row reuse. Native bulk-copy intrinsics remain enabled in both runs.</p>
<p>Capstone decoding of the actual C2 output confirms NEON vector-register instructions. <a href="neon-assembly.txt">Complete decoded assembly</a> · <a href="assembly.log">Original JVM hex dump</a>.</p>
<pre>{html.escape(chr(10).join(snippets))}</pre>
<h2>CPU flamegraphs</h2>
<p>Click a graph, then click stack frames to zoom or use its search button. Each profile sampled a separate warmed-up benchmark for 12 seconds at a 1 ms interval. Widths are proportions within each run; the faster run processes more frames, so cross-graph width alone is not an absolute speed comparison.</p>
<div class="cards">{"".join(profiles)}</div>
<h2>Sampled latency</h2>
<div class="table"><table><thead><tr><th>Encoder</th><th>Workload</th><th>p50 µs</th><th>p95 µs</th><th>p99 µs</th><th>p99.9 µs</th></tr></thead><tbody>{"".join(latrows)}</tbody></table></div>
<p class="muted">Separate JMH sample-time runs: two forks, three one-second warmups, two three-second measurements. These are observed distributions on a shared desktop, including scheduling interference, not real-time guarantees.</p>
<h2>Allocation and GC</h2>
<div class="table"><table><thead><tr><th>Encoder</th><th>Workload</th><th>Bytes allocated / frame</th><th>Observed collections</th></tr></thead><tbody>{"".join(allocation_rows)}</tbody></table></div>
<p class="muted">Separate JMH GC-profiler run: one fork, three one-second warmups and three one-second measurements. JMH did not expose byte-allocation measurements on this trimmed runtime; zero observed collections does not prove zero allocations or guarantee future pause behavior. The persistent row arrays are allocated once, outside measurement.</p>
<h2>Correctness and delivery</h2>
<p><b>{counts["tests"]:,} tests passed</b> in the full offline package gate, with {counts["failures"]} failures and {counts["errors"]} errors.
The new tests cover all 16,777,216 RGB values, varying alpha, grayscale threshold edges, cached-row changes, buffer byte order, read-only views, padding, and exact header/frame output through the actual display worker to a fake USB endpoint.</p>
<p>The encoder tests also passed on Bitwig’s bundled JVM. The complete suite uses the full development JDK because two existing class-loader tests compile Java fixtures; Bitwig’s trimmed runtime lacks that compiler. No changed-code deprecation warnings. No extension was installed and no running Bitwig session was changed; physical hardware verification remains unperformed.</p>
<h2>Raw evidence and reproduction</h2>
<p><a href="../../README.md">Benchmark README</a> · <a href="timings.json">Average-time JSON</a> ·
<a href="without-superword.json">SIMD control JSON</a> · <a href="latency.json">Latency JSON</a> · <a href="allocation.json">Allocation JSON</a> ·
<a href="commands.json">Exact commands</a> · <a href="metadata.json">Environment, source hashes and test counts</a> ·
<a href="capture-manifest.json">Capture hashes</a> · <a href="package.log">Package gate log</a></p>
<p class="muted">Baseline source: fd562c842a2079a0cf72026f91dd5c5b48f1714e. Captured project images stay outside Git. Original logs and raw per-fork measurement arrays are retained. Results apply to this CPU/JVM; other platforms need their own measurements.</p>
</main></html>"""
(OUTPUT / "index.html").write_text(page)
print(json.dumps(summary, indent=2))
print(OUTPUT / "index.html")
