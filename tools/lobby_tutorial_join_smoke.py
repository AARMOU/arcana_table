#!/usr/bin/env python3
"""Source-only rehearsal using the EXACT pinned no-goose combined-APK merge steps.

No Android build, Cloudflare deploy, GitHub push, production merge, signing,
or paid coding worker. Claude's source is fetched only at the agreed PR #283 SHA.
"""
import os
from pathlib import Path
import re
import subprocess
import sys

WORKFLOW = Path(".github/workflows/release-look-followup-build134.yml")
NAMES = [
    "Reconcile exact four known text conflicts on runner only",
    "Finish local merged source and integrate exact development head",
    "Stage reviewed DeckSupport keyword test fix from exact isolated SHA",
    "Align merged QA home fixture with the current welcome invitation",
    "Integrate exact latest AI, table UI and optional cross-network multiplayer heads",
    "Integrate Claude's exact lobby revision after four-player transport (runner-only)",
    "Port ONLY Claude's six reviewed quiet-import files (no dev/JAR merge)",
    "Enforce owner-requested mascot-free home on integrated source",
    "Validate PR 282 tutorial HAND port contract (source-only)",
    "Apply and verify exact PR 282 tutorial step-two fix AFTER integrated UI merge",
    "Validate post-121 changelog content, source preservation and next-build disclaimers",
    "Populate What's new with verified changes since Build 121 (after source merges)",
    "Verify owner-requested Import new and Deck Editor enablement contract",
    "Enable existing deck tools in combined next-test source",
]
PINS = {
    "UPGRADE": "e66eca84125c3180c676702a72ae6cd080f85797",
    "ELDRAZI": "7e6fc1aa8d6ccca30a6fab7fdebc7e08199efbb3",
    "DEV_SHA": "1751f1893cc4706572d4f4382e6b0a137bf46536",
    "FIX_BASE_SHA": "a7961ea55314610ecdf02f164570f215f81278ac",
    "TEST_FIX_SHA": "6cf456733fd2c870d4db6888caa067399336f357",
    "AI_SHA": "e99b4954ad80885af0e338f7df87b1b4587bc5df",
    "WEEKLY_SHA": "bfce3f0d24c3a8cad28a5a729df3dbba16dd079b",
    "TABLE_UI_SHA": "44105eead435b0e3b759b7d631a659990b7388e3",
    "ONLINE_SHA": "6641f1883f54ca3a13c8faa7ef0b01b05976ef74",
    "ONLINE_BASE_SHA": "6641f1883f54ca3a13c8faa7ef0b01b05976ef74",
    "LOBBY_SHA": "79dcd0c58837305fcd1f8790fbf052af214d204b",
    "CLAUDE_QUIET_SHA": "82eb18a96ddf80a9bd31b7adf2cc244b588e2f25",
    "CLAUDE_DEV_BASE_SHA": "9dd5fc9c8da129d8aeb58ef6ecbfecc9ff575c9f",
    "INCLUDE_ONLINE": "true",
}


def extract_steps(contents: str) -> dict[str, str]:
    heads = list(re.finditer(r"^      - (?:name|uses): .*?$", contents, re.MULTILINE))
    result = {}
    for index, head in enumerate(heads):
        tag = head.group().strip()
        if not tag.startswith("- name: "):
            continue
        name = tag[len("- name: "):]
        if name not in NAMES:
            continue
        span = contents[head.end(): heads[index + 1].start() if index + 1 < len(heads) else len(contents)]
        prefix = "        run: |\n"
        start = span.find(prefix)
        if start < 0:
            continue
        body = span[start + len(prefix):]
        script = []
        for raw in body.splitlines(keepends=True):
            if not raw.strip():
                script.append("\n")
            elif raw.startswith("          "):
                script.append(raw[10:])
            else:
                raise ValueError(f"Unexpected YAML indentation in {name}: {raw[:90]!r}")
        result[name] = "".join(script)
    return result


def main() -> None:
    contents = WORKFLOW.read_text()
    scripts = extract_steps(contents)
    missing = [name for name in NAMES if name not in scripts]
    if missing:
        raise SystemExit(f"Missing pinned source assembly step(s): {missing}")
    for label, sha in PINS.items():
        if label == "INCLUDE_ONLINE":
            continue
        line = f"{label}: {sha}"
        if line not in contents:
            raise SystemExit(f"Build workflow drift in {label}; re-review exact pins")
    env = {**os.environ, **PINS}
    for name in NAMES:
        print(f"--- RUNNING EXACT APK SOURCE STEP: {name} ---", flush=True)
        subprocess.run(["bash", "-euo", "pipefail", "-c", scripts[name]], check=True, env=env)
    files = [
        "android/app/src/main/java/com/arcanatable/TableMultiLobby.kt",
        "android/cloud-room/src/index.ts",
        "android/app/src/main/java/com/arcanatable/TableMainLayout.kt",
        "android/app/src/main/assets/tutorials/catalog.json",
        "combined-evidence/tutorial-hand-step2-acceptance.txt",
        "combined-evidence/lobby-worker-deployment-gate.txt",
    ]
    assert all(Path(p).is_file() for p in files), "Incomplete source merge or evidence"
    body = Path(files[2]).read_text()
    assert body.count('TutorialActions.tap("hand-handle")') == 2
    pins = Path("combined-evidence/next-apk-source-pins.txt").read_text()
    assert f'claude-fullscreen-ready-lobby {PINS["LOBBY_SHA"]}' in pins
    assert "PR282 8e4702065dff96ba7ce0a64cd5d420fb54def43d" in pins
    dialog = Path("android/app/src/main/java/com/arcanatable/TableDialogs.kt").read_text()
    assert "CURRENT NO-GOOSE TEST PREVIEW" in dialog
    assert "NEXT TEST APK · LOBBY, TUTORIAL AND DECK TOOLS" in dialog
    assert "BUILD 123 · LARGE END-STEP AI FIX" in dialog and "BUILD 122 · FIXES AND QUICK UI TIPS" in dialog
    assert Path("combined-evidence/build121-changelog-acceptance.txt").is_file()
    assert Path("combined-evidence/import-editor-candidate.txt").is_file()
    assert "quiet-import-notices" in pins
    assert "onClick = onImport" in Path("android/app/src/main/java/com/arcanatable/HomeScreen.kt").read_text()
    print("PASS: source-only next-build join includes Claude lobby + HAND tutorial + no-goose, exact pins", flush=True)
    print("HOLD: no Android executable or live Cloudflare Worker upgrade was made", flush=True)


if __name__ == "__main__":
    main()
