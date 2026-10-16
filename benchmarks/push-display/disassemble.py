#!/usr/bin/env python3
"""Decode HotSpot's AArch64 C2 hex dumps without installing hsdis into the host JVM."""
from pathlib import Path
import re
import sys
import capstone

text = Path(sys.argv[1]).read_text()
latest = {}
decoder = capstone.Cs(capstone.CS_ARCH_ARM64, capstone.CS_MODE_ARM)
decoder.skipdata = True
for block in text.split("Compiled method (c2)")[1:]:
    header = block.splitlines()[0].strip()
    if "PushFrameEncoder::" not in header or "%" in header:
        continue
    method = re.search(r"PushFrameEncoder::(\w+)", header).group(1)
    lines = []
    vectors = 0
    for address, encoded in re.findall(
        r"^\s*(0x[0-9a-f]+): ((?:[0-9a-f]{4} ?(?:\| )?)+)", block, re.M
    ):
        for insn in decoder.disasm(
            bytes.fromhex(encoded.replace("|", "")), int(address, 16)
        ):
            simd = bool(re.search(r"\bv\d+\.", insn.op_str))
            vectors += int(simd)
            lines.append(
                f"{insn.address:#018x}  {insn.mnemonic:10} {insn.op_str}"
                + ("  ; NEON" if simd else "")
            )
    latest[method] = (header, vectors, lines)
for method, (header, vectors, lines) in latest.items():
    print(f"\n{header}\nNEON vector-register instructions: {vectors}\n")
    print("\n".join(lines))
if not any(v[1] for v in latest.values()):
    raise SystemExit("No NEON arithmetic decoded; do not claim SIMD without evidence.")
