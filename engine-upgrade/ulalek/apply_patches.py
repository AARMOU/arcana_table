#!/usr/bin/env python3
"""Apply the Ulalek engine patches on top of the Build 131 engine + mandatory Arcana baseline.

Run after engine-upgrade/audit_patches.py. Source-only: it never touches app jars or assets.
Patches must apply exactly (no fuzz) and match the recorded sha256, or the run stops.
Adapted from GPT's PR #136 port_preflight.py; the patches are PR #60's (f159dea), unmodified.
"""
from __future__ import annotations

import hashlib
import json
import shutil
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ENGINE = ROOT / "argentum-engine"
PATCH_DIR = Path(__file__).resolve().parent / "patches"
# Extra engine scenario specs that travel with the patches (kotest, run by :rules-engine:test).
ENGINE_TESTS = Path(__file__).resolve().parent / "engine-tests"
ENGINE_TEST_DEST = ENGINE / "rules-engine/src/test/kotlin/com/wingedsheep/engine/scenarios"
REPORT = Path(__file__).resolve().parent / "apply-report.json"
UPSTREAM = "61e71e586b100faa661f86cac827ac3c3bbb5a78"
PATCHES = {
    "0004-stack-object-copying.patch": "355e9bc4ae1479ff3f2e36be8250af2e741e39d89f65209118c47d61c5675899",
    "0005-colorless-hybrid-sdk.patch": "957ba557b52f504cc55f860d151dc9b0408e13361ba86f90a9678c2fd6ca55ef",
    "0006-colorless-hybrid-floating-payment.patch": "a2630a36d9c88b6664abad5f6356e954e6cbd4f434e71684395fea173da078c0",
    "0007-colorless-hybrid-auto-tap.patch": "838a6e867960cccb7b97e057e3144ec61a067699c8af6158d19f4cbfcc0edbe4",
    # PR #60 engine-patches/0001-granted-cascade.patch, renamed only (Zhulodok, later Abaddon).
    "0008-granted-cascade.patch": "1074333a3bb6846655d5d2999debdb8925899dd26888534b1356f5e47ffaf2d1",
    # Claude: GrantKeywordToOwnSpells.maxManaValue + duringYourTurnOnly, honoured by granted cascade (Abaddon).
    "0009-granted-keyword-mana-value-cap.patch": "7f5e11c422199a7c5a281f261529fc5c593795f66c1c8abc181ccb11b1bf948b",
}


def git(*args: str) -> subprocess.CompletedProcess[str]:
    return subprocess.run(["git", "-C", str(ENGINE), *args], text=True, capture_output=True, check=False)


def main() -> int:
    report: dict = {"upstream": UPSTREAM, "status": "blocked", "patches": []}
    try:
        baseline = json.loads((ROOT / "engine-upgrade" / "patch-report.json").read_text())
        if baseline.get("upstream") != UPSTREAM or baseline.get("status") != "mandatory_patches_applied":
            raise RuntimeError("Mandatory Arcana baseline patches are not applied")
        head = git("rev-parse", "HEAD").stdout.strip()
        if head != UPSTREAM:
            raise RuntimeError(f"engine pin mismatch: {head}")
        for name, digest in PATCHES.items():
            patch = PATCH_DIR / name
            actual = hashlib.sha256(patch.read_bytes()).hexdigest()
            if actual != digest:
                raise RuntimeError(f"{name} sha256 {actual} != recorded {digest}")
            check = git("apply", "--check", str(patch))
            if check.returncode:
                report["patches"].append({"patch": name, "status": "conflict", "diagnostics": check.stderr[-4000:]})
                raise RuntimeError(f"{name} does not apply exactly")
            applied = git("apply", str(patch))
            if applied.returncode:
                raise RuntimeError(f"{name} failed to apply: {applied.stderr[-2000:]}")
            report["patches"].append({"patch": name, "status": "applied", "sha256": digest})
        diff = git("diff", "--check")
        if diff.returncode:
            raise RuntimeError("git diff --check failed: " + (diff.stdout + diff.stderr)[-2000:])
        added = []
        for spec in sorted(ENGINE_TESTS.glob("*.kt")):
            dest = ENGINE_TEST_DEST / spec.name
            if dest.exists():
                raise RuntimeError(f"{spec.name} would overwrite an upstream test")
            shutil.copyfile(spec, dest)
            added.append(spec.name)
        report["engine_tests_added"] = added
        report["status"] = "applied"
        report["diff_stat"] = git("diff", "--shortstat").stdout.strip()
    except Exception as exc:  # report every failure, then fail the step
        report["error"] = str(exc)
    REPORT.write_text(json.dumps(report, indent=2) + "\n")
    print(json.dumps(report, indent=2), flush=True)
    return 0 if report["status"] == "applied" else 1


if __name__ == "__main__":
    sys.exit(main())
