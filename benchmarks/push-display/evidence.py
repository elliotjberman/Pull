"""Shared command recipes and async-profiler decoding for the two evidence suites."""

import ast
from collections import Counter
import json
import os
from pathlib import Path
import re
import subprocess
import time

ROOT = Path(__file__).resolve().parents[2]


class EvidenceRunner:
    def __init__(self, output):
        self.output = output
        self.commands = []
        frames = ROOT / "target/display-benchmark/frames"
        self.profiler = (
            ROOT
            / "target/display-benchmark/deps/async-profiler-4.5-macos/lib/libasyncProfiler.dylib"
        )
        if not self.profiler.exists() or len(list(frames.glob("*.png"))) != 8:
            raise SystemExit(
                "See README: provision async-profiler 4.5 and eight 960x160 captures first."
            )
        output.mkdir(parents=True, exist_ok=True)
        self.frame_arg = "-Dpull.benchmark.frames=" + str(frames)

    def run(self, name, pattern, args):
        cmd = [str(ROOT / "tools/benchmark-push-display"), *args]
        print("START", name, flush=True)
        start = time.monotonic()
        with (self.output / (name + ".log")).open("w") as log:
            subprocess.run(
                cmd,
                cwd=ROOT,
                env=dict(os.environ, PULL_BENCH_FILTER=pattern),
                stdout=log,
                stderr=subprocess.STDOUT,
                check=True,
            )
        self.commands.append(
            dict(
                name=name, filter=pattern, command=cmd, seconds=time.monotonic() - start
            )
        )
        (self.output / "commands.json").write_text(
            json.dumps(self.commands, indent=2) + "\n"
        )
        print("DONE", name, flush=True)

    def result_args(self, name):
        return ["-rf", "json", "-rff", str(self.output / (name + ".json"))]

    def average(self, name, pattern, content, jvm_args=None):
        self.run(
            name,
            pattern,
            [
                "-p",
                "content=" + content,
                "-f",
                "3",
                "-wi",
                "4",
                "-i",
                "5",
                "-jvmArgsAppend",
                jvm_args or self.frame_arg,
                *self.result_args(name),
            ],
        )

    def without_superword(self):
        self.average(
            "without-superword",
            r"\.FrameBenchmark\.optimized$",
            "random",
            "-XX:-UseSuperWord",
        )

    def latency(self, pattern, content):
        self.run(
            "latency",
            pattern,
            [
                "-p",
                "content=" + content,
                "-f",
                "2",
                "-wi",
                "3",
                "-i",
                "2",
                "-r",
                "3s",
                "-bm",
                "sample",
                "-jvmArgsAppend",
                self.frame_arg,
                *self.result_args("latency"),
            ],
        )

    def profile(self, name, benchmark, method, content):
        self.run(
            name,
            r"\." + benchmark + r"\." + method + "$",
            [
                "-p",
                "content=" + content,
                "-f",
                "1",
                "-wi",
                "3",
                "-i",
                "1",
                "-r",
                "12s",
                "-jvmArgsAppend",
                self.frame_arg,
                "-prof",
                f"async:libPath={self.profiler};event=cpu;interval=1000000;output=flamegraph;dir={self.output / 'profiles'}",
            ],
        )

    def assembly(self):
        self.run(
            "assembly",
            r"\.FrameBenchmark\.optimized$",
            [
                "-p",
                "content=random",
                "-f",
                "1",
                "-wi",
                "3",
                "-i",
                "1",
                "-jvmArgsAppend",
                "-Xbatch -XX:CompileCommand=print,de.mossgrabers.controller.ableton.push.controller.PushFrameEncoder::*",
            ],
        )


def flame_stats(path):
    """Return total samples and inclusive counts by full method name, without executing HTML."""
    source = path.read_text()
    pool = ast.literal_eval(re.search(r"const cpool = (\[.*?\]);", source, re.S)[1])
    for i in range(1, len(pool)):
        pool[i] = pool[i - 1][: ord(pool[i][0]) - 32] + pool[i][1:]
    width = 0
    total = None
    counts = Counter()
    for kind, raw in re.findall(r"^([fun])\(([^)]*)\)", source, re.M):
        args = [None if value == "U" else int(value) for value in raw.split(",")]
        width_index = 3 if kind == "f" else 1
        if len(args) > width_index and args[width_index] is not None:
            width = args[width_index]
        if total is None:
            total = width
        counts[pool[args[0] >> 3]] += width
    return total, counts
