#!/usr/bin/env python3
"""Fail-closed PR #282 tutorial-step-2 port for the NEXT combined APK only.

The existing Build 134 workflow assembles the Android source on a throwaway CI
runner from multiple pinned branches. The PR #282 one-line fix must be applied
AFTER that assembly, without merging/changing Claude's multiplayer-lobby lane.
No engine rules, Cloudflare transport, deck editor, release gates or saved games
are changed by this script.
"""
import argparse
import json
from pathlib import Path

DEFAULT_UI = Path("android/app/src/main/java/com/arcanatable/TableMainLayout.kt")
DEFAULT_CATALOG = Path("android/app/src/main/assets/tutorials/catalog.json")

TABLE_OLD = (
    'modifier = Modifier.weight(1f).testTag("hand-peek-table").tutorialTarget("hand-handle"),\n'
    '                    onClick = { run { showHand = true } }'
)
TABLE_FIXED = (
    'modifier = Modifier.weight(1f).testTag("hand-peek-table").tutorialTarget("hand-handle"),\n'
    '                    // The table-view handle must signal the same tutorial tap as the player-view handle.\n'
    '                    onClick = { run { showHand = true; TutorialActions.tap("hand-handle") } }'
)
PLAYER_REQUIRED = (
    'modifier = Modifier.fillMaxWidth().height(handControlHeight).testTag("hand-peek").tutorialTarget("hand-handle"),\n'
    '                        onClick = { run { if (canControlSelectedPlayer || argentumEnabled) '
    '{ showHand = true; TutorialActions.tap("hand-handle") } } }'
)


def verify_lesson(catalog: dict) -> None:
    chapters = catalog.get("chapters", [])
    if len(chapters) != 20:
        raise ValueError(f"Expected the existing 20 tutorial chapters, found {len(chapters)}")
    first = next((c for c in chapters if c.get("id") == "first-turn"), None)
    if first is None:
        raise ValueError("Missing first-turn tutorial")
    steps = first.get("steps", [])
    if len(steps) != 16:
        raise ValueError(f"Expected 16 existing first-turn lesson steps, found {len(steps)}")
    if steps[1].get("id") != "hand-handle" or steps[1].get("target") != "hand-handle":
        raise ValueError("Tutorial step 2 no longer targets the actual HAND handle")
    if steps[1].get("advance") != {"kind": "tap"}:
        raise ValueError("Tutorial step 2 no longer advances on a HAND tap")
    if steps[2].get("title") != "Play a land" or steps[2].get("advance", {}).get("event") != "LandPlayed":
        raise ValueError("Tutorial step 3 is no longer an engine-confirmed land play")


def patch_ui(source: str) -> str:
    if source.count(PLAYER_REQUIRED) != 1:
        raise ValueError("Player-view HAND callback was changed; review the integration")
    if source.count(TABLE_FIXED) == 1 and source.count(TABLE_OLD) == 0:
        return source  # Idempotent if PR #282 is already present in a later candidate.
    if source.count(TABLE_OLD) != 1 or source.count(TABLE_FIXED) != 0:
        raise ValueError("Table-view HAND callback changed or duplicated; review PR #282 manually")
    updated = source.replace(TABLE_OLD, TABLE_FIXED, 1)
    if updated.count('TutorialActions.tap("hand-handle")') != 2:
        raise ValueError("Expected exactly one tutorial gesture on each HAND handle")
    return updated


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source", type=Path, default=DEFAULT_UI)
    parser.add_argument("--catalog", type=Path, default=DEFAULT_CATALOG)
    parser.add_argument("--verify-only", action="store_true")
    args = parser.parse_args()
    verify_lesson(json.loads(args.catalog.read_text()))
    before = args.source.read_text()
    after = patch_ui(before)
    if args.verify_only and after != before:
        raise SystemExit("FAIL: tutorial HAND fix is not integrated in candidate")
    if not args.verify_only and after != before:
        args.source.write_text(after)
    if after.count('TutorialActions.tap("hand-handle")') != 2:
        raise SystemExit("FAIL: expected both Table and Player HAND gesture callbacks")
    print("PASS: first-turn tutorial step 2 opens HAND and advances once in Table + Player views")
    print("PASS: following Play a land step still requires Argentum LandPlayed event")
    print("PASS: 20 tutorial chapters / 16 first-turn steps retained")


if __name__ == "__main__":
    main()
