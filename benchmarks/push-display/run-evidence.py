#!/usr/bin/env python3
"""Reproduce the local AArch64 evidence suite; runs benchmarks sequentially."""
import json
import os
from pathlib import Path
import subprocess
import time

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "target/display-benchmark"
FRAMES = OUT / "frames"
PROFILER = OUT / "deps/async-profiler-4.5-macos/lib/libasyncProfiler.dylib"
if not PROFILER.exists() or len(list(FRAMES.glob("*.png"))) != 8:
    raise SystemExit("See README: provision async-profiler 4.5 and eight 960x160 captures first.")
frame_arg = "-Dpull.benchmark.frames=" + str(FRAMES)
commands = []
def run(name, pattern, args):
    env = dict(os.environ, PULL_BENCH_FILTER=pattern)
    cmd = [str(ROOT / "tools/benchmark-push-display"), *args]
    print("START " + name, flush=True)
    start = time.monotonic()
    with (OUT / (name + ".log")).open("w") as log:
        subprocess.run(cmd, env=env, cwd=ROOT, stdout=log, stderr=subprocess.STDOUT, check=True)
    commands.append(dict(name=name, command=cmd, filter=pattern, seconds=time.monotonic()-start))
    (OUT / "commands.json").write_text(json.dumps(commands, indent=2) + "\n")
    print("DONE " + name, flush=True)

run("timings", r"\.FrameBenchmark\.(baseline|baselineBulkCopy|optimized)$",
    ["-p", "content=capture,random,gray,unchanged", "-f", "3", "-wi", "4", "-i", "5",
     "-jvmArgsAppend", frame_arg, "-rf", "json", "-rff", str(OUT/"timings.json")])
run("without-superword", r"\.FrameBenchmark\.optimized$",
    ["-p", "content=random", "-f", "3", "-wi", "4", "-i", "5",
     "-jvmArgsAppend", "-XX:-UseSuperWord", "-rf", "json", "-rff", str(OUT/"without-superword.json")])
run("latency", r"\.FrameBenchmark\.(baseline|optimized)$",
    ["-p", "content=capture,random", "-f", "2", "-wi", "3", "-i", "2", "-r", "3s", "-bm", "sample",
     "-jvmArgsAppend", frame_arg, "-rf", "json", "-rff", str(OUT/"latency.json")])
for method in ("baseline", "optimized"):
    for content in ("capture", "random"):
        run("profile-" + method + "-" + content, r"\.FrameBenchmark\." + method + "$",
            ["-p", "content=" + content, "-f", "1", "-wi", "3", "-i", "1", "-r", "12s",
             "-jvmArgsAppend", frame_arg, "-prof",
             "async:libPath=" + str(PROFILER) + ";event=cpu;interval=1000000;output=flamegraph;dir=" + str(OUT/"profiles")])
run("assembly", r"\.FrameBenchmark\.optimized$",
    ["-p", "content=random", "-f", "1", "-wi", "3", "-i", "1",
     "-jvmArgsAppend", "-Xbatch -XX:CompileCommand=print,de.mossgrabers.controller.ableton.push.controller.PushFrameEncoder::*"])
run("allocation", r"\.FrameBenchmark\.(baseline|optimized)$",
    ["-p", "content=capture,random", "-f", "1", "-wi", "3", "-i", "3",
     "-jvmArgsAppend", frame_arg, "-prof", "gc", "-rf", "json", "-rff", str(OUT/"allocation.json")])
print("Evidence suite complete.", flush=True)
