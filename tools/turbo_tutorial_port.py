#!/usr/bin/env python3
"""Exact-source Claude tutorial port after the B136+137 runner source join.

Source-only; no engine builds, APK, signing, workers, cloud deployment or writes
to remote branches. Fail closed on unknown overlapping app code. Always include
the full 23-file tutorial improvement; never cherry-pick the tests alone.
"""
from __future__ import annotations

import subprocess
from pathlib import Path

BASE = "8e4702065dff96ba7ce0a64cd5d420fb54def43d"
SHA = "dfccf7ff456e953f099930c426b8dee5554314ba"
EXPECTED_PATHS = {
    "android/app/src/main/assets/tutorials/catalog.json",
    "android/app/src/main/java/com/arcanatable/AdjustUiPage.kt",
    "android/app/src/main/java/com/arcanatable/ArgentumCardPick.kt",
    "android/app/src/main/java/com/arcanatable/ArgentumCombatBoard.kt",
    "android/app/src/main/java/com/arcanatable/ArgentumScreens.kt",
    "android/app/src/main/java/com/arcanatable/HomeScreen.kt",
    "android/app/src/main/java/com/arcanatable/MainActivity.kt",
    "android/app/src/main/java/com/arcanatable/TableBoardViews.kt",
    "android/app/src/main/java/com/arcanatable/TableController.kt",
    "android/app/src/main/java/com/arcanatable/TableDialogs.kt",
    "android/app/src/main/java/com/arcanatable/TableEffects.kt",
    "android/app/src/main/java/com/arcanatable/TableHelpers.kt",
    "android/app/src/main/java/com/arcanatable/TableMainLayout.kt",
    "android/app/src/main/java/com/arcanatable/TableOverlays.kt",
    "android/app/src/main/java/com/arcanatable/TableOverlaysB.kt",
    "android/app/src/main/java/com/arcanatable/TutorialCoach.kt",
    "android/app/src/main/java/com/arcanatable/TutorialHubDialog.kt",
    "android/app/src/main/java/com/arcanatable/TutorialPracticeScenarios.kt",
    "android/app/src/main/java/com/arcanatable/TutorialProgress.kt",
    "android/app/src/test/java/com/arcanatable/TutorialRunTest.kt",
    "android/app/src/test/java/com/arcanatable/TutorialWalkthroughTest.kt",
    "android/app/src/test/java/com/arcanatable/ui/TutorialAuditUiTest.kt",
    "android/app/src/test/java/com/arcanatable/ui/TutorialScreensTest.kt",
}

def run(*args: str, check: bool = True) -> subprocess.CompletedProcess[str]:
    return subprocess.run(args, text=True, capture_output=True, check=check)

def main() -> None:
    run("git", "config", "user.name", "arcana-turbo-source-only")
    run("git", "config", "user.email", "arcana-turbo-source-only@users.noreply.github.com")
    run("git", "fetch", "--no-tags", "origin", BASE, SHA)
    assert run("git", "rev-parse", SHA).stdout.strip() == SHA
    run("git", "merge-base", "--is-ancestor", BASE, SHA)
    names = set(run("git", "diff", "--name-only", BASE, SHA).stdout.splitlines())
    if names != EXPECTED_PATHS:
        raise ValueError(f"Tutorial PR changed unexpectedly. Added={names-EXPECTED_PATHS}; missing={EXPECTED_PATHS-names}")
    # Commit only the ephemeral runner assembly, not the source branch.
    run("git", "add", "-A")
    staged = run("git", "diff", "--cached", "--quiet", check=False)
    if staged.returncode:
        run("git", "commit", "--no-gpg-sign", "-m", "Temporary verified pre-tutorial combined source")
    patch = run("git", "diff", "--binary", BASE, SHA).stdout
    patch_path = Path("combined-evidence/turbo-tutorial-300.patch")
    patch_path.parent.mkdir(parents=True, exist_ok=True)
    patch_path.write_text(patch)
    applied = run("git", "apply", "--3way", "--index", str(patch_path), check=False)
    if applied.returncode:
        # Three exact reviewed overlapping hunks. Keep each independent fix:
        # PR293 retained invalid blocker selections AND Claude's cue;
        # PR292 unified 2–4 player hosting AND Claude's lesson start target;
        # current private library peek AND Claude's legal-actions target.
        import re
        conflicts = set(run("git", "diff", "--name-only", "--diff-filter=U").stdout.splitlines())
        expected = {
            "android/app/src/main/java/com/arcanatable/ArgentumCombatBoard.kt",
            "android/app/src/main/java/com/arcanatable/HomeScreen.kt",
            "android/app/src/main/java/com/arcanatable/TableMainLayout.kt",
        }
        if conflicts != expected:
            raise ValueError(f"Unreviewed tutorial conflicts: {conflicts ^ expected}")
        for file in sorted(expected):
            p = Path(file)
            source = p.read_text()
            matches = list(re.finditer(r'^<<<<<<< ours\n(.*?)^=======\n(.*?)^>>>>>>> theirs\n',
                                       source, re.MULTILINE | re.DOTALL))
            if len(matches) != 1:
                raise ValueError(f"Unreviewed tutorial overlap count: {file} {len(matches)}")
            c = matches[0]
            ours, theirs = c.group(1), c.group(2)
            if file.endswith("ArgentumCombatBoard.kt"):
                assert "warning: String? = null" in ours
                assert "tutorialCue: Pair<String, String>? = null" in theirs
                assert ours.count("fun ArgentumBlockBoard(") == 1
                fixed = ours.replace("onDismiss: () -> Unit)",
                    "onDismiss: () -> Unit, tutorialCue: Pair<String, String>? = null)")
                assert fixed != ours
            elif file.endswith("HomeScreen.kt"):
                assert "friendMode == 5 -> onWifiMultiHost()" in ours
                assert "friendMode == 6 -> onOnlineMultiHost()" in ours
                assert "friendMode == 7 -> onOnlineMultiJoin()" in ours
                assert '.tutorialTarget("setup-start")' in theirs
                assert '.testTag("play-start"),' in ours
                fixed = ours.replace('.testTag("play-start"),',
                                     '.testTag("play-start").tutorialTarget("setup-start"),')
                assert fixed != ours
            else:
                assert file.endswith("TableMainLayout.kt")
                assert "Look at top of library · private" in ours
                assert "argentumTopLibraryOpen = true" in ours
                assert '.tutorialTarget("menu-legal-actions")' in theirs
                assert ours.count('if (argentumEnabled && gameInProgress) DropdownMenuItem') == 1
                fixed = ours.replace(
                    ' }, onClick = { topMenuExpanded = false; topMenuSection = null; run { argentumActionCard',
                    ' }, modifier = Modifier.tutorialTarget("menu-legal-actions"), onClick = { topMenuExpanded = false; topMenuSection = null; run { argentumActionCard'
                )
                assert fixed != ours
            output = source[:c.start()] + fixed + source[c.end():]
            if re.search(r'^(<<<<<<< |=======|>>>>>>> )',output,re.MULTILINE):
                raise ValueError(f"Conflict marker remains: {file}")
            p.write_text(output)
            run("git", "add", file)
            print(f"PASS: exact reviewed tutorial + existing app fix: {file}")
        remaining = run("git", "diff", "--name-only", "--diff-filter=U").stdout
        if remaining.strip():
            raise ValueError(f"Unresolved tutorial overlaps: {remaining}")
    run("git", "diff", "--cached", "--check")
    for file in (
        "android/app/src/test/java/com/arcanatable/ui/TutorialAuditUiTest.kt",
        "android/app/src/test/java/com/arcanatable/TutorialWalkthroughTest.kt",
        "android/app/src/test/java/com/arcanatable/TutorialRunTest.kt",
        "android/app/src/main/java/com/arcanatable/TutorialProgress.kt",
    ):
        if not Path(file).is_file():
            raise ValueError(f"Missing tutorial regression source: {file}")
    run("git", "commit", "--no-gpg-sign", "-m", "Runner only: Claude PR300 full 20-chapter tutor and actual UI audit")
    with Path("combined-evidence/next-apk-source-pins.txt").open("a") as f:
        f.write(f"claude-tutorial-all-20-chapters {SHA}\n")
    print("PASS: full 23-file Claude tutorial source and tests integrated; NO APK built")

if __name__ == "__main__":
    main()
