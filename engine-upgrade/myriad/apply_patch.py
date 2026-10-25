#!/usr/bin/env python3
"""Fail-closed Myriad source integration after the B131 baseline + Ulalek patch stack.

This is a source-only candidate. Never modifies committed Android runtime JARs.
PR #41's patch was authored against a newer upstream; if it does not apply
exactly to the pinned B131 source, STOP and port it explicitly.
"""
from pathlib import Path
import json
import subprocess
import sys

root = Path(__file__).resolve().parents[2]
engine = root / "argentum-engine"
patch = Path(__file__).with_name("myriad-defender-choice.patch")
baseline = root / "engine-upgrade" / "patch-report.json"
ulalek = root / "engine-upgrade" / "ulalek" / "apply-report.json"

def main():
    for report, expected in ((baseline, "mandatory_patches_applied"), (ulalek, "applied")):
        if not report.exists() or json.loads(report.read_text()).get("status") != expected:
            raise RuntimeError(f"Required engine patch stage missing: {report}")
    result = subprocess.run(["git", "-C", str(engine), "apply", "--check", str(patch)],
                            capture_output=True, text=True)
    if result.returncode:
        raise RuntimeError("Myriad patch does not apply exactly to pinned B131+Ulalek engine; "
                           "port #41 rather than silently upgrading upstream: " + result.stderr)
    subprocess.run(["git", "-C", str(engine), "apply", str(patch)], check=True)
    subprocess.run(["git", "-C", str(engine), "diff", "--check"], check=True)
    print("Myriad defender-choice source patch applied; runtime and consumer tests NOT YET verified.")

if __name__ == "__main__":
    try:
        main()
    except Exception as exc:
        print(f"BLOCKED: {exc}", file=sys.stderr)
        sys.exit(1)
