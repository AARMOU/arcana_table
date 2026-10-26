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
            if not (
                'val target = when (name.uppercase()) {' in ours
                and '.tutorialTarget(target)' in ours
                and 'TutorialActions.tap(target); onClick()' in ours
                and ours.count('min = 34.dp') == 1
                and 'Text("$name  $count"' in ours
            ):
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



AI_SHA = "e99b4954ad80885af0e338f7df87b1b4587bc5df"
CONTROLLER = "android/app/src/main/java/com/arcanatable/TableController.kt"
OVERLAYS = "android/app/src/main/java/com/arcanatable/TableOverlays.kt"
AI_HOST = "android/app/src/main/java/com/arcanatable/engine/LocalArgentumHost.kt"


def resolve_ai(head_sha: str) -> None:
    if head_sha != AI_SHA:
        raise RuntimeError(f"Unreviewed AI PR head {head_sha}; expected {AI_SHA}")
    paths = sorted(run("git", "diff", "--name-only", "--diff-filter=U").splitlines())
    if paths != sorted((CONTROLLER, OVERLAYS, AI_HOST)):
        raise RuntimeError(f"Unexpected AI conflicts: {paths!r}")

    for name in paths:
        path = Path(name)
        original = path.read_text()
        matches = list(CONFLICT.finditer(original))
        if len(matches) != 1 or matches[0].group("head") != AI_SHA:
            raise RuntimeError(f"Unexpected {name} AI conflict shape")
        match = matches[0]
        ours, theirs = match.group("ours"), match.group("theirs")
        if name == CONTROLLER:
            old_count = '    var offlineAiCount by run { mutableStateOf(ReleaseFeatures.aiCount(optionPrefs.getInt("offline_ai_count", 1))) }\n'
            new_count = '    var offlineAiCount by run { mutableStateOf(optionPrefs.getInt("offline_ai_count", 1).coerceIn(1, 3)) }\n'
            if ours != old_count or not theirs.startswith(new_count):
                raise RuntimeError("Unexpected saved-count/AI-difficulty collision")
            if theirs.count("fun setOfflineAiDifficulty(") != 1:
                raise RuntimeError("Missing difficulty persistence")
            replacement = old_count + theirs[len(new_count):]
        elif name == OVERLAYS:
            old_count = '                onAiCount = { offlineAiCount = ReleaseFeatures.aiCount(it); optionPrefs.edit().putInt("offline_ai_count", offlineAiCount).apply() },\n'
            new_count = '                onAiCount = { offlineAiCount = it; optionPrefs.edit().putInt("offline_ai_count", it).apply() },\n'
            if not ours.startswith(old_count) or not theirs.startswith(new_count):
                raise RuntimeError("Unexpected release seat gating/source overlay collision")
            if ours.count('TutorialActions.tap("setup-start")') != 1:
                raise RuntimeError("Tutorial start hook missing")
            if theirs.count('aiDifficulties = offlineAiDifficulties,') != 1 or theirs.count('onAiDifficulty = ') != 1:
                raise RuntimeError("Difficulty controls missing")
            difficulty = (
                "                aiDifficulties = offlineAiDifficulties,\n"
                "                onAiDifficulty = { seat, level -> setOfflineAiDifficulty(seat, level) },\n"
            )
            replacement = old_count + difficulty + ours[len(old_count):]
        else:
            old_head = '                val chosen = if (tutorialPassiveOpponent) safePass(current, priority)\n'
            new_head = '                val chosen = timed("aiChoice")'
            if not ours.startswith(old_head) or not theirs.startswith(new_head):
                raise RuntimeError("Unexpected training AI / guarded AI collision")
            if ours.count('bot.chooseAction(current)') != 1:
                raise RuntimeError("Tutorial baseline differs")
            if (
                theirs.count('aiGuarded("aiChoiceErrors")') != 1
                or theirs.count('OfflineAiPolicy.chooseAction(bot, seatOf(priority).difficulty, current)') != 1
                or theirs.count('?: safePass(current, priority)') != 1
            ):
                raise RuntimeError("Guarded AI recovery is missing or modified")
            # Tutorial's deliberately passive training bot still passes, while
            # normal games use Claude's difficulty-aware guarded action selection.
            replacement = old_head + "                    else (" + theirs[len('                val chosen = '):].rstrip("\n") + ")\n"
        result = original[:match.start()] + replacement + original[match.end():]
        if any(tag in result for tag in ("<<<<<<<", "=======", ">>>>>>>")):
            raise RuntimeError(f"Remaining AI merge markers in {name}")
        path.write_text(result)
        subprocess.check_call(["git", "add", name])
        print("Reviewed AI conflict resolution:", name)

    remaining = run("git", "diff", "--name-only", "--diff-filter=U")
    if remaining:
        raise RuntimeError(f"Unresolved AI conflicts: {remaining}")
    subprocess.check_call(["git", "diff", "--cached", "--check", "--", CONTROLLER, OVERLAYS, AI_HOST])
    print("PASS: retained release AI-seat limit/tutorial passive opponent and Claude's difficulty + action recovery")


ONLINE_SHA = "6641f1883f54ca3a13c8faa7ef0b01b05976ef74"
ARGENTUM = "android/app/src/main/java/com/arcanatable/TableArgentum.kt"


def resolve_online(head_sha: str) -> None:
    if head_sha != ONLINE_SHA:
        raise RuntimeError(f"Unreviewed online PR head {head_sha}; expected {ONLINE_SHA}")
    paths = sorted(run("git", "diff", "--name-only", "--diff-filter=U").splitlines())
    if paths != sorted((ARGENTUM, OVERLAYS)):
        raise RuntimeError(f"Unexpected online conflicts: {paths!r}")
    for name in paths:
        path = Path(name)
        original = path.read_text()
        matches = list(CONFLICT.finditer(original))
        if len(matches) != 1 or matches[0].group("head") != ONLINE_SHA:
            raise RuntimeError(f"Unexpected {name} online collision")
        match = matches[0]
        ours, theirs = match.group("ours"), match.group("theirs")
        if name == ARGENTUM:
            tutorial = "            if (argentumOffline && !offlineRestoreRequested && !tutorialPracticeActive && wifiRole == null) {\n"
            old = "            if (argentumOffline && !offlineRestoreRequested && wifiRole == null) {\n"
            lobby_reset = "            wifiGuestWaiting = false\n            wifiGuestLobbyState = null\n"
            if ours != tutorial or theirs != lobby_reset + old:
                raise RuntimeError("Unexpected tutorial/new-lobby state collision")
            replacement = lobby_reset + tutorial
        else:
            tutorial_next = (
                "                tutorialStep = if (tutorialChapterId != null) tutorialStep else null,\n"
                "                onTutorialNext = { tutorialAdvance() }, onTutorialExit = { stopTutorial() },\n"
            )
            old_join = '                onOnlineJoin = { playSheetOpen = false; run { onlineCodeInput = ""; onlineJoinOpen = true } },\n'
            if not ours.startswith(old_join):
                raise RuntimeError("Unknown old online join callback")
            # Preserve tutorial arguments but take incoming online multi host/join callbacks.
            if ours[len(old_join):] != "".join(tutorial_next):
                raise RuntimeError("Missing tutorial screen inputs")
            for required in ("onOnlineMultiHost = ", "onOnlineMultiJoin = ", "onOnlineJoin = ", "wifiMultiOnlineJoin = false"):
                if theirs.count(required) != 1:
                    raise RuntimeError(f"Incoming online callbacks changed: {required}")
            replacement = theirs + "".join(tutorial_next)
        result = original[:match.start()] + replacement + original[match.end():]
        if any(tag in result for tag in ("<<<<<<<", "=======", ">>>>>>>")):
            raise RuntimeError(f"Remaining multiplayer merge markers in {name}")
        path.write_text(result)
        subprocess.check_call(["git", "add", name])
        print("Reviewed online conflict resolution:", name)

    remaining = run("git", "diff", "--name-only", "--diff-filter=U")
    if remaining:
        raise RuntimeError(f"Unresolved online conflicts: {remaining}")
    subprocess.check_call(["git", "diff", "--cached", "--check", "--", ARGENTUM, OVERLAYS])
    print("PASS: retained tutorial-start guard and hooks, plus directed online host/join and lobby reset")


if __name__ == "__main__":
    if len(sys.argv) != 3 or sys.argv[1] not in ("table", "ai", "online"):
        raise SystemExit("usage: next_apk_resolve_conflicts.py table|ai|online <exact-pr-head>")
    if sys.argv[1] == "table":
        resolve_table(sys.argv[2])
    elif sys.argv[1] == "ai":
        resolve_ai(sys.argv[2])
    else:
        resolve_online(sys.argv[2])
