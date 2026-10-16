#!/usr/bin/env python3
"""Build the second-round report from unedited JMH data and archived pilot experiments."""
from pathlib import Path
import hashlib, html, json, re, shutil, subprocess, tarfile
from evidence import flame_stats
import xml.etree.ElementTree as ET
import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / "target/display-benchmark/round2"
OUT = ROOT / "benchmarks/push-display/results/2026-10-16-cell-cache"
OUT.mkdir(parents=True, exist_ok=True)
for p in SOURCE.iterdir():
    if (
        p.name
        in {
            "timings.json",
            "timings.log",
            "debug-timings.json",
            "debug-timings.log",
            "without-superword.json",
            "without-superword.log",
            "latency.json",
            "latency.log",
            "assembly.log",
            "neon-assembly.txt",
            "commands.json",
            "package.log",
            "server-tests.log",
            "browser-cache-check.json",
            "bitwig-tests.log",
            "client-tests.log",
        }
        or p.name.startswith("profile-")
        and p.suffix == ".log"
    ):
        shutil.copyfile(p, OUT / p.name)
shutil.copyfile(
    ROOT / "target/display-benchmark/frames/manifest.json",
    OUT / "capture-manifest.json",
)
if (SOURCE / "browser-check/serve.py").exists():
    shutil.copyfile(SOURCE / "browser-check/serve.py", OUT / "browser-fixture.py")
labels = {
    "capture": "Captured screens",
    "random": "Changing random pixels",
    "gray": "Moving grayscale",
    "unchanged": "Unchanged UI",
    "playhead": "Moving 2-pixel playhead",
    "patch": "Moving 16×16 patch",
    "macro": "One macro column changes",
}


def read(name, count):
    data = json.loads((SOURCE / (name + ".json")).read_text())
    assert len(data) == count and all(
        r["primaryMetric"]["score"] > 0 for r in data
    ), name
    return {
        (r["benchmark"].split(".")[-1], r["params"]["content"]): r["primaryMetric"]
        for r in data
    }


values = read("timings", 14)
debug = read("debug-timings", 8)
control = read("without-superword", 1)["optimized", "random"]
fig, ax = plt.subplots(figsize=(11, 7), layout="constrained")
y = np.arange(len(labels))
for shift, method, label, color in [
    (-0.18, "rowCached", "First SIMD version", "#7b8998"),
    (0.18, "optimized", "Cells + whole-frame reuse", "#139c8b"),
]:
    scores = [values[method, c]["score"] for c in labels]
    errors = [values[method, c]["scoreError"] for c in labels]
    bars = ax.barh(
        y + shift,
        scores,
        0.32,
        xerr=errors,
        color=color,
        label=label,
        error_kw=dict(capsize=2, lw=1),
    )
    ax.bar_label(bars, labels=[f"{v:.1f} µs" for v in scores], padding=5, fontsize=9)
ax.set_yticks(y, list(labels.values()))
ax.invert_yaxis()
ax.set_xlim(0, max(v["score"] + v["scoreError"] for v in values.values()) * 1.2)
ax.set_xlabel("Microseconds per encoded frame and USB-memory copy · lower is better")
ax.set_title(
    "Pixel caching beyond the first Java SIMD version",
    loc="left",
    weight="bold",
    fontsize=16,
    pad=15,
)
ax.spines[["top", "right", "left"]].set_visible(False)
ax.grid(axis="x", alpha=0.15)
ax.set_axisbelow(True)
ax.legend(loc="lower right", frameon=False)
fig.savefig(OUT / "comparison.png", dpi=180)
fig.savefig(OUT / "comparison.svg")
plt.close(fig)


def table(data, before, contents):
    rows = []
    for content in contents:
        a, b = data[before, content], data["optimized", content]
        rows.append(
            f'<tr><th>{labels[content]}</th><td>{a["score"]:,.2f} ± {a["scoreError"]:.2f}</td><td>{b["score"]:,.2f} ± {b["scoreError"]:.2f}</td><td>{a["score"]/b["score"]:.2f}×</td></tr>'
        )
    return (
        '<div class="scroll"><table><tr><th>Workload</th><th>Before µs</th><th>After µs</th><th>Speedup</th></tr>'
        + "".join(rows)
        + "</table></div>"
    )


profiles = []
for p in sorted((SOURCE / "profiles").glob("*/flame-cpu-forward.html")):
    stem = p.parent.name.replace(
        "de.mossgrabers.controller.ableton.push.controller.", ""
    ).replace("-AverageTime-content-", "-")
    filename = stem + "-flamegraph.html"
    shutil.copyfile(p, OUT / filename)
    profiles.append(f'<a href="{filename}">{html.escape(stem)}</a>')
assert len(profiles) == 6
# Decode async-profiler's compact frame data without executing its HTML/JavaScript.
profile_shares = {}
for p in sorted(OUT.glob("*-flamegraph.html")):
    total, counts_by_name = flame_stats(p)
    # These selected method names do not recurse; each width is an inclusive CPU sample count.
    selected = {
        k: v / total * 100
        for k, v in counts_by_name.items()
        if any(
            n in k
            for n in [
                "Encoder.channel",
                "Encoder.luminance",
                "Encoder.copyTo",
                "PNGImageWriter.write_IDAT",
                "ArraysSupport.vectorizedMismatch",
            ]
        )
    }
    profile_shares[p.stem] = {"total_samples": total, "inclusive_percent": selected}
(OUT / "profile-summary.json").write_text(json.dumps(profile_shares, indent=2) + "\n")
pilots = json.loads((SOURCE / "pilots.json").read_text()) + json.loads(
    (SOURCE / "cell-pilots.json").read_text()
)
pilot_rows = []
for r in pilots:
    pilot_rows.append(
        "<tr><th>"
        + r["name"]
        + "</th>"
        + "".join("<td>" + str(r["results"].get(c, "—")) + "</td>" for c in labels)
        + "</tr>"
    )
with tarfile.open(OUT / "pilot-evidence.tar.gz", "w:gz") as archive:
    names = {
        "pilots.json",
        "cell-pilots.json",
        "pilot.py",
        "cell-pilot.py",
        "row-original.java",
    }
    for r in pilots:
        for extension in [".java", ".json", ".log"]:
            names.add(r["name"] + extension)
    for name in sorted(names):
        if (SOURCE / name).exists():
            archive.add(SOURCE / name, arcname=name)
(OUT / "pilots.json").write_text(json.dumps(pilots, indent=2) + "\n")
counts = {k: 0 for k in ["tests", "failures", "errors", "skipped"]}
for module in ["pull-core", "pull-core-publisher", "pull-shell"]:
    for p in (ROOT / module / "target/surefire-reports").glob("TEST-*.xml"):
        test = ET.parse(p).getroot()
        for k in counts:
            counts[k] += int(test.get(k, 0))
paths = list((ROOT / "benchmarks/push-display/src").rglob("*.java"))
paths += [
    ROOT
    / "pull-shell/src/main/java/de/mossgrabers/controller/ableton/push/controller"
    / n
    for n in [
        "PushFrameEncoder.java",
        "PushDebugFrame.java",
        "PushDebugCaptureHost.java",
        "PushColorCalibration.java",
    ]
]
paths += [
    ROOT / p
    for p in [
        "tools/push-debug-surface-app/push-surface.js",
        "tools/push-debug-surface-server.py",
        "tools/capture-push2-display",
    ]
]
metadata = {
    "before_commit": "28b7059c",
    "cpu": subprocess.check_output(
        ["sysctl", "-n", "machdep.cpu.brand_string"], text=True
    ).strip(),
    "jvm": subprocess.check_output(
        [
            "/Applications/Bitwig Studio.app/Contents/PlugIns/JavaVM-arm64.bundle/Contents/Home/bin/java",
            "--version",
        ],
        text=True,
    ),
    "java_tests": counts,
    "source_sha256": {
        str(p.relative_to(ROOT)): hashlib.sha256(p.read_bytes()).hexdigest()
        for p in paths
    },
}
(OUT / "metadata.json").write_text(json.dumps(metadata, indent=2) + "\n")
summary = {
    c: {
        "row_us": values["rowCached", c]["score"],
        "cell_us": values["optimized", c]["score"],
        "speedup": values["rowCached", c]["score"] / values["optimized", c]["score"],
    }
    for c in labels
}
summary["debug"] = {
    c: {
        "before_us": debug["baseline", c]["score"],
        "after_us": debug["optimized", c]["score"],
        "speedup": debug["baseline", c]["score"] / debug["optimized", c]["score"],
    }
    for c in ["capture", "random", "unchanged", "macro"]
}
(OUT / "summary.json").write_text(json.dumps(summary, indent=2) + "\n")
latency = json.loads((SOURCE / "latency.json").read_text())
assert len(latency) == 4
latrows = []
for r in latency:
    p = r["primaryMetric"]["scorePercentiles"]
    latrows.append(
        "<tr><td>"
        + r["benchmark"].split(".")[-1]
        + "</td><td>"
        + labels[r["params"]["content"]]
        + "</td>"
        + "".join(f"<td>{p[k]:.1f}</td>" for k in ["50.0", "95.0", "99.0", "99.9"])
        + "</tr>"
    )
assembly = (SOURCE / "neon-assembly.txt").read_text()
snippets = [
    line
    for line in assembly.splitlines()
    if "; NEON" in line and re.search(r"\b(mul|sshr|smin|smax)\s", line)
][:8]
assert snippets
page = f"""<!doctype html><html lang="en"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Pull · Pixel and debugger caching</title><style>
body{{margin:0;background:#f5f7fa;color:#172c40;font:16px/1.6 system-ui,sans-serif}}main{{max-width:1120px;margin:auto;padding:40px 24px 70px}}
h1{{font-size:37px;line-height:1.2;letter-spacing:-1px}}h2{{margin-top:36px}}a{{color:#087e75}}.eyebrow{{font-size:12px;font-weight:bold;letter-spacing:2px;color:#087e75}}
.lead{{font-size:19px}}.muted{{color:#586a79;font-size:14px}}img{{width:100%;border-radius:12px}}table{{border-collapse:collapse;width:100%;font-variant-numeric:tabular-nums;background:white;font-size:14px}}
th,td{{text-align:left;padding:10px 13px;border-bottom:1px solid #dfe6ec}}tr:first-child{{background:#e5edf2}}.scroll{{overflow:auto}}
pre{{padding:20px;background:#152a3c;color:#d9f1e8;overflow:auto;border-radius:10px;font-size:13px}}.notice{{padding:14px 20px;border-left:4px solid #e5a14a;background:#fff5e5}}
.links{{display:grid;grid-template-columns:1fr 1fr;gap:12px}}.links a{{display:block;padding:15px;background:white;border-radius:9px;overflow-wrap:anywhere}}
@media(max-width:700px){{.links{{grid-template-columns:1fr}}h1{{font-size:29px}}main{{padding:24px 14px}}}}</style><main>
<div class="eyebrow">PULL / PERFORMANCE EVIDENCE / 16 OCTOBER 2026</div>
<h1>Pixel caching, independent of display components.</h1>
<p class="lead">Beyond the first SIMD version: <b>{summary['capture']['speedup']:.2f}× faster on captured screens</b>,
<b>{summary['macro']['speedup']:.2f}× on a changing macro column</b>, and <b>{summary['playhead']['speedup']:.2f}× on a moving playhead</b>.
The unchanged debugger snapshot/PNG path improves by <b>{summary['debug']['unchanged']['speedup']:.1f}×</b>.</p>
<p class="muted">Apple M5 Max · Bitwig’s bundled Zulu Java 25.0.4.1 · ordinary Java 21-compatible source · comparison baseline 28b7059c.</p>
<pre>Any UI components, layouts or animation
                 ↓
       Finished 960×160 BGRA bitmap
          ↙                    ↘
PushFrameEncoder           Optional PushDebugFrame
exact pixel-cell cache     immutable raw snapshot + PNG cache
SIMD calibration/packing   capture worker + content generation
          ↓                    ↓
Existing USB sender        Debugger browser</pre>
<p>Neither cache knows about components, pages or semantic column ownership. New components and overlapping drawings need no invalidation hooks. Hardware calibration remains separate from the debugger’s original pre-calibration pixels.</p>
<img src="comparison.png" alt="First SIMD encoder versus cell-cached encoder across seven workloads">
<h2>Hardware encoder: before and after caching</h2>{table(values,'rowCached',labels)}
<p>Cache bookkeeping has a cost when everything changes: random pixels are {(values['optimized','random']['score']/values['rowCached','random']['score']-1)*100:.1f}% slower and moving grayscale is {(values['optimized','gray']['score']/values['rowCached','gray']['score']-1)*100:.1f}% slower. Local and unchanged updates benefit most.</p>
<p class="muted">Means ± JMH 99.9% confidence intervals. Three forks, four one-second warmups and five one-second measurements per fork. Each workload rotates eight frames. Only captured screens are replayed real frames; the playhead, patch and macro cases are controlled synthetic updates. No measurements were discarded from the final suite.</p>
<div class="notice">Offline CPU evidence: encoding plus the full USB-memory copy. Rendering, controller callbacks, locks, scheduling and physical USB transfer are excluded. This does not establish the hottest path across Pull or measure input-to-screen latency. Nothing was installed into Bitwig.</div>
<h2>Why this cache geometry</h2>
<p>A direct whole-frame comparison returns early for identical input. Changed frames are checked by row and then by <b>60×1-pixel cells</b>. Two cells fit a common 120-pixel macro column; one-row height avoids recalculating a whole column for a small label edit. Equality is exact, including alpha, with no hash-collision shortcut. Only changed cells are recalibrated. The complete wire frame is still submitted normally.</p>
<p>The encoder’s primitive arrays total 947,840 bytes, approximately 0.90 MiB. Row-local scratch arrays deliberately preserve matching loop indices. Calibration, channel packing and the final scalar short store remain separate so the JIT can vectorize the expensive arithmetic.</p>
<pre>Before (first SIMD version):
for each row:
    copy raw pixels
    if row changed: calibrate and pack the entire row
copy complete wire frame to USB buffer

After:
if whole frame differs:
    for each changed row:
        for each changed 60-pixel cell:
            remember raw pixels
            SIMD calibrate and pack that cell
copy complete cached wire frame to USB buffer</pre>
<h2>Calibration still uses SIMD</h2>
<p>Changing-random time: <b>{values['optimized','random']['score']:.2f} µs</b> normally versus <b>{control['score']:.2f} µs</b> with <code>-XX:-UseSuperWord</code> ({control['score']/values['optimized','random']['score']:.2f}× slower). This case changes every cell. The actual C2 output contains four-lane integer NEON multiplication, shifts and clamps.</p>
<pre>{html.escape(chr(10).join(snippets))}</pre>
<p><a href="neon-assembly.txt">Decoded assembly</a> · <a href="assembly.log">Original JIT dump</a> · <a href="without-superword.json">SIMD control data</a></p>
<h2>Debugger caching and CPU cost</h2>{table(debug,'baseline',['capture','random','unchanged','macro'])}
<p>These measurements combine raw snapshot capture and PNG generation on one benchmark thread. Production performs PNG work on its existing worker. Both benchmark versions use the same in-memory PNG sink; filesystem writes, HTTP transfer and browser decoding are excluded.</p>
<p>Identical samples reuse an immutable snapshot and its PNG, avoid rewriting <code>latest.png</code>, and preserve the browser’s decoded image. Changed samples use bulk BGRA-to-ARGB copying before PNG compression. Sampling counters and observation revisions keep advancing. A separate epoch/content generation changes only when the published image changes; older eight-field capture status remains supported.</p>
<p><code>NEXT_FRAME</code> still waits for a later outbound bitmap observation, even if its pixels match the preceding frame. Queued captures retain their observed pixels. Alpha/dimension changes invalidate the cache, and failed browser image loads retry on a later observation.</p>
<p>The <a href="browser-cache-check.json">isolated browser check</a> verified zero extra image downloads across three unchanged observations, one download for changed pixels, restart invalidation, same-content HTTP retry, and restoration after Reset. It used the production server and browser app with an isolated <a href="browser-fixture.py">synthetic fixture</a>.</p>
<h2>Sampled latency</h2><div class="scroll"><table><tr><th>Encoder</th><th>Workload</th><th>p50 µs</th><th>p95 µs</th><th>p99 µs</th><th>p99.9 µs</th></tr>{''.join(latrows)}</table></div>
<p class="muted">Separate two-fork sample-time runs, three one-second warmups and two three-second measurements. Shared-desktop observations, not real-time guarantees.</p>
<h2>Interactive CPU flamegraphs</h2><div class="links">{''.join(profiles)}</div>
<p>The macro workload shifts from calibration toward comparisons: vectorized exact matching accounts for about 40% of the new profile and the final output copy about 11%. For the unchanged debugger, the old PNG image-data writer accounts for about 79% of samples; the cached version spends essentially all its measured CPU in vectorized pixel comparison. These are inclusive shares within each run, not absolute time savings. <a href="profile-summary.json">Selected sample counts and shares</a>.</p>
<p class="muted">Separate warmed-up 12-second CPU profiles at 1 ms intervals. Frame widths are proportions within a run; the faster run processes more frames. Click to zoom or search.</p>
<h2>Rejected geometries and pilot evidence</h2>
<p>Exploratory one-fork runs used two warmups and two measurements. They selected the geometry; the final three-fork measurements above provide the comparison. Larger 120×8, 120×16 and full-column 120×160 cells were also tried.</p>
<div class="scroll"><table><tr><th>Pilot</th>{''.join('<th>'+v+' µs</th>' for v in labels.values())}</tr>{''.join(pilot_rows)}</table></div>
<p><a href="pilots.json">Pilot summary</a> · <a href="pilot-evidence.tar.gz">Raw pilot data, logs and source prototypes</a></p>
<h2>GPU assessment</h2>
<p>Apple Silicon supports <a href="https://developer.apple.com/documentation/metal/choosing-a-resource-storage-mode-for-apple-gpus">shared CPU/GPU memory</a>; a separate discrete-GPU memory round trip is not inherently necessary. CPU/GPU access still needs <a href="https://developer.apple.com/documentation/metal/mtlstoragemode/shared">synchronization</a>. A frame contains 614,400 source bytes and 327,680 wire bytes, about 0.90 MiB combined.</p>
<p>Our inference: for this small CPU-produced, CPU-consumed workload, native integration, submission and completion latency deserve measurement before expecting a GPU win. The GPU has not been benchmarked here. Pixel-cell reuse already removes most arithmetic for local changes.</p>
<h2>Validation and reproduction</h2>
<p><b>{counts['tests']:,} Java tests passed</b>, including all 16,777,216 RGB colors, cell/row/edge edits, immutable capture snapshots, request freshness, alpha/dimension invalidation and restart generations. The 19 focused encoder/capture tests also passed on Bitwig’s actual JVM, as did nine local HTTP-server tests and the debugger shell-client regressions. Live Bitwig/physical Push testing remains unperformed.</p>
<p><a href="../../README.md">Reproduction instructions</a> · <a href="timings.json">Encoder raw data</a> · <a href="debug-timings.json">Debugger raw data</a> · <a href="latency.json">Latency raw data</a> · <a href="commands.json">Exact commands</a> · <a href="metadata.json">Environment, tests and source hashes</a> · <a href="package.log">Package gate</a> · <a href="server-tests.log">Server tests</a> · <a href="client-tests.log">Client tests</a> · <a href="bitwig-tests.log">Actual-JVM tests</a> · <a href="../2026-10-15-m5-max/index.html">First optimization report</a></p>
</main></html>"""
(OUT / "index.html").write_text(page)
print(json.dumps(summary, indent=2))
print(OUT / "index.html")
