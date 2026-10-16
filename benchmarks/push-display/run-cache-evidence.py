#!/usr/bin/env python3
"""Sequential evidence for pixel-cell and debugger caching, using the installed Bitwig JVM."""
from evidence import EvidenceRunner, ROOT

suite = EvidenceRunner(ROOT / "target/display-benchmark/round2")
suite.average(
    "timings",
    r"\.FrameBenchmark\.(rowCached|optimized)$",
    "capture,random,gray,unchanged,playhead,patch,macro",
)
suite.average(
    "debug-timings",
    r"\.DebugFrameBenchmark\.(baseline|optimized)$",
    "capture,random,unchanged,macro",
)
suite.without_superword()
suite.latency(r"\.FrameBenchmark\.(rowCached|optimized)$", "capture,macro")
for benchmark, methods, content in [
    ("FrameBenchmark", ("rowCached", "optimized"), "capture"),
    ("FrameBenchmark", ("rowCached", "optimized"), "macro"),
    ("DebugFrameBenchmark", ("baseline", "optimized"), "unchanged"),
]:
    for method in methods:
        suite.profile(
            "profile-" + benchmark + "-" + method + "-" + content,
            benchmark,
            method,
            content,
        )
suite.assembly()
print("Evidence suite complete.", flush=True)
