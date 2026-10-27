#!/usr/bin/env python3
"""Exact applicability audit of Arcana's pinned patch series.

Refuses fuzzy acceptance, silently skipped patches and upstream version guessing.
A failing mandatory patch blocks the integrated build: this is a discovery gate,
NOT evidence that the feature is absent upstream.
"""
from __future__ import annotations

import json
import os
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
ENGINE = ROOT / "argentum-engine"
PATCHES = ROOT / "argentum-patches"
REPORT = ROOT / "engine-upgrade" / "patch-report.json"
TARGET = "621552754f6e9901ba5b11242f669b8f8816c5d6"
MANDATORY = [
    "0001-android-regex.patch",
    "0002-engine-trial-build.patch",
    "0003-full-library-export.patch",
    "0004-token-copy-triggered-abilities.patch",
    "0005-last-known-negative-subtype.patch",
    "0006-ai-decision-deadline.patch",
    "0007-ai-bounded-stack-search.patch",
    "0008-jvm-test-toolchain.patch",
]
EXPERIMENTAL = "0010-enumeration-absence-index.patch"


def run(*args: str) -> subprocess.CompletedProcess[str]:
    return subprocess.run(args, text=True, capture_output=True, check=False)


def main() -> int:
    expected = run("git", "-C", str(ENGINE), "rev-parse", "HEAD")
    if expected.returncode or expected.stdout.strip() != TARGET:
        raise SystemExit(
            "Wrong upstream revision: expected " + TARGET + "; got " + expected.stdout.strip()
        )
    if run("git", "-C", str(ENGINE), "status", "--porcelain").stdout.strip():
        raise SystemExit("Expected clean upstream checkout before patch audit")

    report: dict = {
        "upstream": TARGET,
        "status": "pending",
        "patches": [],
        "mandatory_blocker": None,
        "experiment": EXPERIMENTAL,
    }
    forward_ports = {
        "0004-token-copy-triggered-abilities.patch": ROOT / "engine-upgrade" / "forward-ports" / "0004-token-copy-triggered-abilities.patch",
        "0007-ai-bounded-stack-search.patch": ROOT / "engine-upgrade" / "forward-ports" / "0007-ai-bounded-stack-search.patch",
    }
    for patch_name in MANDATORY:
        patch = forward_ports.get(patch_name, PATCHES / patch_name)
        if not patch.is_file():  # weekly.py folds forward ports back into the refreshed series
            patch = PATCHES / patch_name
        if not patch.is_file():
            outcome = "missing"
            diagnostics = "Patch file not found"
        elif report["mandatory_blocker"] is not None:
            outcome = "not_attempted_after_blocker"
            diagnostics = "Earlier mandatory patch did not apply; cannot establish cumulative compatibility"
        else:
            check = run("git", "-C", str(ENGINE), "apply", "--check", str(patch))
            if check.returncode:
                outcome = "conflict_requires_review"
                diagnostics = (check.stderr + check.stdout).strip()[-6000:]
            else:
                applied = run("git", "-C", str(ENGINE), "apply", str(patch))
                outcome = "applied" if applied.returncode == 0 else "apply_failed"
                diagnostics = (applied.stderr + applied.stdout).strip()[-6000:]
        if outcome != "applied" and report["mandatory_blocker"] is None:
            report["mandatory_blocker"] = patch_name
        report["patches"].append(
            {"patch": patch_name, "status": outcome, "diagnostics": diagnostics}
        )
        print(f"{patch_name}: {outcome}", flush=True)
        if diagnostics:
            print(diagnostics[:1800], flush=True)

    if report["mandatory_blocker"] is None:
        check = run("git", "-C", str(ENGINE), "apply", "--check", str(PATCHES / EXPERIMENTAL))
        if check.returncode:
            report["patches"].append(
                {"patch": EXPERIMENTAL, "status": "experimental_conflict",
                 "diagnostics": (check.stderr + check.stdout).strip()[-6000:]}
            )
        else:
            # Do not apply experimental patch into baseline upgrade. Gate it independently.
            report["patches"].append(
                {"patch": EXPERIMENTAL, "status": "applies_but_not_included_in_core_runtime",
                 "diagnostics": "Requires distinct parity/performance validation"}
            )
    else:
        report["patches"].append(
            {"patch": EXPERIMENTAL, "status": "not_attempted_after_blocker",
             "diagnostics": "First port the mandatory baseline before assessing experimental index"}
        )

    diff_check = run("git", "-C", str(ENGINE), "diff", "--check")
    if diff_check.returncode:
        report["diff_check"] = (diff_check.stdout + diff_check.stderr).strip()[-6000:]
    report["status"] = (
        "mandatory_patches_applied"
        if report["mandatory_blocker"] is None and diff_check.returncode == 0
        else "blocked_requires_forward_port"
    )
    REPORT.write_text(json.dumps(report, indent=2) + "\n")
    summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary:
        with open(summary, "a") as out:
            out.write("## Pinned Argentum patch compatibility\n\n")
            out.write(f"Upstream: \`{TARGET}\`\n\n")
            out.write("| Patch | Status |\n|---|---|\n")
            for entry in report["patches"]:
                out.write(f"| \`{entry['patch']}\` | {entry['status']} |\n")
            out.write(f"\nOverall: **{report['status']}**\n")
    return 0 if report["status"] == "mandatory_patches_applied" else 1


if __name__ == "__main__":
    sys.exit(main())
