#!/usr/bin/env python3
"""Audit Ulalek patches against the exact B131 engine AFTER Arcana baseline patches.

This is source-only: never publish or replace app jars/assets. Stops on first
conflict rather than treating a fuzzy/reversed patch as an applied patch.
"""
from __future__ import annotations

import json
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ENGINE = ROOT / "argentum-engine"
OLD = ROOT / "staged-ulalek"
REPORT = ROOT / "engine-upgrade" / "ulalek" / "port-report.json"
UPSTREAM = "61e71e586b100faa661f86cac827ac3c3bbb5a78"
STAGED_SHA = "f159deae81bf6d2f01507cdc745a3567978339ba"
PATCH_DIR = OLD / "investigation/argentum-six-precons/staged-engine"
PATCHES = [
    "0004-stack-object-copying.patch",
    "0005-colorless-hybrid-sdk.patch",
    "0006-colorless-hybrid-floating-payment.patch",
    "0007-colorless-hybrid-auto-tap.patch",
]


def git_in(directory: Path, *args: str) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        ["git", "-C", str(directory), *args],
        text=True, capture_output=True, check=False
    )


def main() -> int:
    report: dict = {
        "purpose": "Source-only Ulalek patch audit; app runtime stays unchanged",
        "upstream": UPSTREAM,
        "staged_patch_commit": STAGED_SHA,
        "baseline": "not_verified",
        "status": "blocked",
        "first_blocker": None,
        "patches": [],
    }
    try:
        baseline = ROOT / "engine-upgrade" / "patch-report.json"
        if not baseline.is_file():
            raise RuntimeError("Missing mandatory baseline patch audit")
        base = json.loads(baseline.read_text())
        if (base.get("upstream") != UPSTREAM or
                base.get("status") != "mandatory_patches_applied"):
            raise RuntimeError("Build 131 baseline patches not fully verified")
        report["baseline"] = "mandatory_patches_applied"

        for path, expected, label in (
            (ENGINE, UPSTREAM, "engine"),
            (OLD, STAGED_SHA, "staged patch source"),
        ):
            result = git_in(path, "rev-parse", "HEAD")
            if result.returncode or result.stdout.strip() != expected:
                raise RuntimeError(
                    f"{label} pin mismatch: expected {expected}; got {result.stdout.strip()}"
                )

        for patch_name in PATCHES:
            patch = PATCH_DIR / patch_name
            entry = {"patch": patch_name, "status": "pending", "diagnostics": ""}
            report["patches"].append(entry)
            if not patch.is_file():
                entry.update(status="missing", diagnostics=str(patch))
                report["first_blocker"] = patch_name
                break

            check = git_in(ENGINE, "apply", "--check", str(patch))
            if check.returncode:
                entry.update(
                    status="conflict_requires_forward_port",
                    diagnostics=(check.stderr + check.stdout)[-6000:],
                )
                report["first_blocker"] = patch_name
                break
            applied = git_in(ENGINE, "apply", str(patch))
            if applied.returncode:
                entry.update(
                    status="apply_failed",
                    diagnostics=(applied.stderr + applied.stdout)[-6000:],
                )
                report["first_blocker"] = patch_name
                break
            entry["status"] = "applied_exactly"

        if report["first_blocker"] is None:
            diff = git_in(ENGINE, "diff", "--check")
            if diff.returncode:
                raise RuntimeError(
                    "git diff --check failed: " + (diff.stdout + diff.stderr)[-3000:]
                )
            report["status"] = "source_patches_apply; full build still required"
            report["diff_stat"] = git_in(ENGINE, "diff", "--stat").stdout[-8000:]
    except Exception as exc:
        report["error"] = str(exc)
    finally:
        REPORT.parent.mkdir(parents=True, exist_ok=True)
        REPORT.write_text(json.dumps(report, indent=2) + "\n")
        print(json.dumps(report, indent=2), flush=True)
    return 0 if report["status"].startswith("source_patches_apply") else 1


if __name__ == "__main__":
    sys.exit(main())
