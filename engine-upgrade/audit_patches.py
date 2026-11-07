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
TARGET = "173a09a2ed15763dce854e385b2df0d085e03975"
MANDATORY = [
    "0001-android-regex.patch",
    "0002-engine-trial-build.patch",
    "0003-full-library-export.patch",
    "0004-token-copy-triggered-abilities.patch",
    "0005-last-known-negative-subtype.patch",
    "0006-ai-decision-deadline.patch",
    "0007-ai-bounded-stack-search.patch",
    "0011-stack-object-copying.patch",
    "0012-colorless-hybrid-sdk.patch",
    "0013-colorless-hybrid-floating-payment.patch",
    "0014-colorless-hybrid-auto-tap.patch",
    "0015-granted-cascade.patch",
    "0016-granted-keyword-mana-value-cap.patch",
    "0017-colorless-eldrazi-source-mana.patch",
    "0018-devoid-after-temple.patch",
    "0019-devoid-sdk-surface.patch",
    "0020-temple-projected-source-subtypes.patch",
    "0021-azlask-exact-colors-trigger.patch",
    "0022-cant-be-sacrificed-by-effects.patch",
    "0023-face-down-exile-hidden-from-owner.patch",
    "0024-commander-cast-history-amount.patch",
    "0025-akromas-will-modal-combat-trick.patch",
    "0026-free-cast-once-each-turn.patch",
    "0027-opponents-must-attack-anyone.patch",
    "0028-base-power-toughness-at-most.patch",
    "0032-per-player-target-binding.patch",
    "0030-bloodthirst-keyword.patch",
    "0033-property-sum-of-collection.patch",
    "0034-commander-cast-count-counters-rider.patch",
    "0035-ai-idle-mana-deploy.patch",
    "0036-graveyard-card-activated-grant.patch",
    "0037-static-attacking-chosen-player.patch",
    "0038-chosen-color-land-mana-bonus.patch",
    "0039-graveyard-lookback-granted-types.patch",
    "0041-more-than-meets-the-eye-living-metal.patch",
    "0042-announced-draw-replaced-whole.patch",
    "0043-keyword-cant-have.patch",
    "0044-dependent-target-owned-by-target-controller.patch",
]
# Patches no longer in the series, and why. The weekly upgrade's current-build check reads this so a
# numbered build that still lists one is not reported as missing a patch; weekly.py adds upstream absorptions.
RETIRED = {
    "0008-jvm-test-toolchain.patch": "folded into 0002-engine-trial-build.patch (2026-11-02)",
}
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
