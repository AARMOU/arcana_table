#!/usr/bin/env python3
"""Ulalek engine stage, now part of the main patch series.

The six Ulalek / Zhulodok / Abaddon engine patches moved to argentum-patches/0011-0016 and are applied,
exactly and in order, by engine-upgrade/audit_patches.py. That lets the weekly upgrade
(engine-upgrade/weekly.py) carry them onto each new upstream engine instead of leaving them pinned
to Build 131.

Run after audit_patches.py. This script only confirms those patches were applied, copies the extra
engine scenario specs that travel with them, and writes apply-report.json for later stages
(the Myriad stage reads it). Workflows that call it keep working unchanged.
"""
from __future__ import annotations

import json
import shutil
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ENGINE = ROOT / "argentum-engine"
BASELINE = ROOT / "engine-upgrade" / "patch-report.json"
ENGINE_TESTS = Path(__file__).resolve().parent / "engine-tests"
ENGINE_TEST_DEST = ENGINE / "rules-engine/src/test/kotlin/com/wingedsheep/engine/scenarios"
REPORT = Path(__file__).resolve().parent / "apply-report.json"
ULALEK = [
    "0011-stack-object-copying.patch",
    "0012-colorless-hybrid-sdk.patch",
    "0013-colorless-hybrid-floating-payment.patch",
    "0014-colorless-hybrid-auto-tap.patch",
    "0015-granted-cascade.patch",
    "0016-granted-keyword-mana-value-cap.patch",
]


def main() -> int:
    report: dict = {"status": "blocked", "patches": []}
    try:
        baseline = json.loads(BASELINE.read_text())
        if baseline.get("status") != "mandatory_patches_applied":
            raise RuntimeError("Run engine-upgrade/audit_patches.py first; the main series did not apply")
        report["upstream"] = baseline["upstream"]
        applied = {p["patch"]: p["status"] for p in baseline["patches"]}
        for name in ULALEK:
            # weekly.py retires a patch upstream has fully absorbed; that counts as present.
            status = applied.get(name, "retired_upstream_has_it")
            if status not in ("applied", "retired_upstream_has_it"):
                raise RuntimeError(f"{name}: {status}")
            report["patches"].append({"patch": name, "status": status})
        added = []
        for spec in sorted(ENGINE_TESTS.glob("*.kt")):
            dest = ENGINE_TEST_DEST / spec.name
            if dest.exists():
                raise RuntimeError(f"{spec.name} would overwrite an upstream test")
            shutil.copyfile(spec, dest)
            added.append(spec.name)
        report["engine_tests_added"] = added
        report["status"] = "applied"
    except Exception as exc:  # report every failure, then fail the step
        report["error"] = str(exc)
    REPORT.write_text(json.dumps(report, indent=2) + "\n")
    print(json.dumps(report, indent=2), flush=True)
    return 0 if report["status"] == "applied" else 1


if __name__ == "__main__":
    sys.exit(main())
