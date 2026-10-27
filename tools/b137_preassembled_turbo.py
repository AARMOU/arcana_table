#!/usr/bin/env python3
"""Fail-closed source selector for Claude's already-assembled, verified B137 tree.

This replaces the B136 *source assembly* step ONLY. The existing source-pinned
Argentum preflight/build, export, SHA-256 seed/restore and Android QA stay intact.
Never run the old B136 integration script over this already-assembled source.
"""
from __future__ import annotations
import argparse
import json
from pathlib import Path
import subprocess

VERIFIED_B137_SOURCE = "3f8228e61d1d38d1c82f73244ce30ce303ac2198"
VERIFIED_B137_RUN = "40430360039"
# Nothing in these source/input paths may drift from the successful B137
# tree without a fresh intentional integration and review of this contract.
PROTECTED = (
    "android", "engine-upgrade", "argentum-patches",
    "tools/fast_apk_snapshot.py", "tools/lobby_tutorial_join_smoke.py",
    "tools/turbo_tutorial_port.py",
    ".github/workflows/release-look-followup-build134.yml",
)
REQUIRED = {
    "android/app/src/main/java/com/arcanatable/TableMultiLobby.kt":
        "val hostLobbyState = wifiMultiLobbyState",
    "android/app/src/main/java/com/arcanatable/TableArgentum.kt":
        "argentumBlockGuard.onStep(snapshot.stepKey)",
    "android/app/src/main/java/com/arcanatable/ArgentumBlockSubmissionGuard.kt":
        "class ArgentumBlockSubmissionGuard",
    "android/app/src/main/java/com/arcanatable/TutorialProgress.kt":
        "class TutorialRun",
    "android/custom-cards/src/test/kotlin/com/arcanatable/customcards/UlalekFusedAtrocityScenarioTest.kt":
        "opponentsEldraziCastDoesNotTriggerMyUlalek",
    "engine-upgrade/eldrazi-temple/engine-tests/EldraziTempleManaRestrictionTest.kt":
        "mixed unrestricted and Temple mana cannot launder",
}

def check() -> None:
    subprocess.run(["git", "merge-base", "--is-ancestor", VERIFIED_B137_SOURCE, "HEAD"],
                   check=True)
    changed = subprocess.check_output(
        ["git", "diff", "--name-only", VERIFIED_B137_SOURCE, "HEAD", "--", *PROTECTED],
        text=True,
    ).strip()
    if changed:
        raise ValueError("B137 verified compiled inputs changed; review/reseed, do not silently fall back to B136: " + changed)
    for file, marker in REQUIRED.items():
        path = Path(file)
        if not path.is_file() or marker not in path.read_text():
            raise ValueError(f"Missing verified B137 gameplay/tutorial marker: {file}")
    catalog = json.loads(Path("android/app/src/main/assets/tutorials/catalog.json").read_text())
    if len(catalog["chapters"]) != 20:
        raise ValueError("B137 tutorial chapter count drifted")
    for file in ("tools/check_android_regex.py", "tools/verify_launcher_branding.py",
                 "android/app/src/main/assets/argentum/custom-cards.jsonl"):
        if not Path(file).is_file():
            raise ValueError(f"Required B137 QA/card file missing: {file}")
    print(f"PASS: B137 all-in source from verified run {VERIFIED_B137_RUN}, SHA {VERIFIED_B137_SOURCE}")
    print("PASS: exact gameplay, 20-chapter tutorial, engine and card inputs protected; no source recreation")

if __name__ == "__main__":
    p = argparse.ArgumentParser()
    p.add_argument("operation", choices=["verify"])
    p.parse_args()
    check()
