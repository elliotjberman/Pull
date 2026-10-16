#!/usr/bin/env python3
"""Reproduce the local AArch64 evidence suite; runs benchmarks sequentially."""
from evidence import EvidenceRunner, ROOT

suite = EvidenceRunner(ROOT / "target/display-benchmark")
suite.average(
    "timings",
    r"\.FrameBenchmark\.(baseline|baselineBulkCopy|optimized)$",
    "capture,random,gray,unchanged",
)
suite.without_superword()
suite.latency(r"\.FrameBenchmark\.(baseline|optimized)$", "capture,random")
for method in ("baseline", "optimized"):
    for content in ("capture", "random"):
        suite.profile(
            "profile-" + method + "-" + content, "FrameBenchmark", method, content
        )
suite.assembly()
suite.run(
    "allocation",
    r"\.FrameBenchmark\.(baseline|optimized)$",
    [
        "-p",
        "content=capture,random",
        "-f",
        "1",
        "-wi",
        "3",
        "-i",
        "3",
        "-jvmArgsAppend",
        suite.frame_arg,
        "-prof",
        "gc",
        *suite.result_args("allocation"),
    ],
)
print("Evidence suite complete.", flush=True)
