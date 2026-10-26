#!/usr/bin/env python3
"""Strict exact-head reconciliations for the runner-only next APK source join.

No permissive 'ours'/'theirs' checkout: preserve both behavioral changes.
Abort immediately if a conflict hunk differs from the reviewed, known shape.
"""
from __future__ import annotations

import re
import subprocess
import sys
from pathlib import Path

TABLE_SHA = "44105eead435b0e3b759b7d631a659990b7388e3"
HELPERS = "android/app/src/main/java/com/arcanatable/TableHelpers.kt"
LAYOUT = "android/app/src/main/java/com/arcanatable/TableMainLayout.kt"

CONFLICT = re.compile(
    r"^<<<<<<< HEAD\n(?P<ours>.*?)^=======\n(?P<theirs>.*?)^>>>>>>> (?P<head>[0-9a-f]{40})\n",
    re.MULTILINE | re.DOTALL,
)


def run(*args: str) -> str:
    return subprocess.check_output(args, text=True).strip()


def resolve_table(head_sha: str) -> None:
    if head_sha != TABLE_SHA:
        raise RuntimeError(f"Unreviewed table PR head {head_sha}; expected {TABLE_SHA}")
    paths = sorted(run("git", "diff", "--name-only", "--diff-filter=U").splitlines())
    if paths != [HELPERS, LAYOUT]:
        raise RuntimeError(f"Unexpected table conflicts: {paths!r}")

    for name in paths:
        path = Path(name)
        original = path.read_text()
        matches = list(CONFLICT.finditer(original))
        if len(matches) != 1:
            raise RuntimeError(f"{name}: expected exactly one conflict, got {len(matches)}")
        match = matches[0]
        ours, theirs = match.group("ours"), match.group("theirs")
        if match.group("head") != head_sha:
            raise RuntimeError(f"{name}: unreviewed incoming commit")
        if name == HELPERS:
            # Release tutorial's instrumented ZoneChip must survive while the
            # increased 44dp touch target from #277 must also apply.
            if ours.strip() != ".heightIn(min = 34.dp).wrapContentHeight(Alignment.CenterVertically)":
                raise RuntimeError("Unexpected instrumented ZoneChip branch")
            if not (
                'Text("$name  $count"' in theirs
                and "Modifier.heightIn(min = 44.dp)" in theirs
                and "onClick = onClick" in theirs
            ):
                raise RuntimeError("Unexpected table ZoneChip change")
            if '.tutorialTarget(target)' not in original or 'TutorialActions.tap(target)' not in original:
                raise RuntimeError("Release tutorial ZoneChip instrumentation missing")
            replacement = ours.replace("min = 34.dp", "min = 44.dp")
        else:
            # Keep release-only extra-seat capability gate; rename only the
            # token-action label, as the popup audit intended.
            if (
                'if (!onlineMode && ReleaseFeatures.multiplayerPreviewEnabled)' not in ours
                or 'Text("Add token")' not in ours
                or 'if (!onlineMode) DropdownMenuItem' not in theirs
                or 'Text("Add token…")' not in theirs
                or ours.count("\n") != 2
                or theirs.count("\n") != 2
            ):
                raise RuntimeError("Unexpected game-tools release/menu collision")
            replacement = ours.replace('Text("Add token")', 'Text("Add token…")')
        result = original[:match.start()] + replacement + original[match.end():]
        if any(tag in result for tag in ("<<<<<<<", "=======", ">>>>>>>")):
            raise RuntimeError(f"Remaining merge markers in {name}")
        path.write_text(result)
        subprocess.check_call(["git", "add", name])
        print("Reviewed table conflict resolution:", name)

    remaining = run("git", "diff", "--name-only", "--diff-filter=U")
    if remaining:
        raise RuntimeError(f"Unresolved conflicts: {remaining}")
    subprocess.check_call(["git", "diff", "--cached", "--check", "--", HELPERS, LAYOUT])
    print("PASS: retained release tutorial chip targets, 44dp hit area, gated AI-seat UI, and revised token text")


if __name__ == "__main__":
    if len(sys.argv) != 3 or sys.argv[1] != "table":
        raise SystemExit("usage: next_apk_resolve_conflicts.py table <exact-pr-head>")
    resolve_table(sys.argv[2])
