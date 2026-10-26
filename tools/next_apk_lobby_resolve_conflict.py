#!/usr/bin/env python3
"""Resolve the ONE reviewed PR283 vs Build134 tutorial conflict on CI runner.

Never edits Claude's branch. Refuses unknown paths or changed conflict text.
Retains the PR283 guest seat reset AND the Build134 tutorial checkpoint guard.
"""
from pathlib import Path
import re
import subprocess
import sys

PATH = "android/app/src/main/java/com/arcanatable/TableArgentum.kt"
OURS = (
    "            if (argentumOffline && !offlineRestoreRequested && "
    "!tutorialPracticeActive && wifiRole == null) {"
)
THEIRS = (
    "            wifiGuestSeatId = null\n"
    "            if (argentumOffline && !offlineRestoreRequested && wifiRole == null) {"
)
MERGED = (
    "            wifiGuestSeatId = null\n"
    "            if (argentumOffline && !offlineRestoreRequested && "
    "!tutorialPracticeActive && wifiRole == null) {"
)


def resolve(source: str, sha: str) -> str:
    pattern = re.compile(
        r"^<<<<<<< HEAD\n(?P<ours>.*?)\n=======\n(?P<theirs>.*?)\n>>>>>>> "
        + re.escape(sha) + r"\n", re.MULTILINE | re.DOTALL
    )
    matches = list(pattern.finditer(source))
    if len(matches) != 1:
        raise ValueError(f"Expected one exact TableArgentum conflict, found {len(matches)}")
    m = matches[0]
    if m.group("ours") != OURS or m.group("theirs") != THEIRS:
        raise ValueError(f"Unknown lobby/tutorial overlap:\nOURS={m.group('ours')!r}\nTHEIRS={m.group('theirs')!r}")
    out = source[:m.start()] + MERGED + "\n" + source[m.end():]
    if any(mark in out for mark in ("<<<<<<< ", "=======\n", ">>>>>>> ")):
        raise ValueError("Unresolved conflict markers remain")
    if out.count(MERGED) != 1:
        raise ValueError("Expected one exact guest seat reset and tutorial preservation")
    return out


def main() -> None:
    if len(sys.argv) != 2 or not re.fullmatch(r"[0-9a-f]{40}", sys.argv[1]):
        raise SystemExit("Usage: next_apk_lobby_resolve_conflict.py <pinned-40-hex-lobby-head>")
    sha = sys.argv[1]
    unmerged = subprocess.check_output(
        ["git", "diff", "--name-only", "--diff-filter=U"], text=True
    ).splitlines()
    if unmerged != [PATH]:
        raise SystemExit(f"FAIL: expected ONLY {PATH}, found {unmerged}")
    p = Path(PATH)
    p.write_text(resolve(p.read_text(), sha))
    print("PASS: keep Build134 tutorial-practice checkpoint guard and Claude guest seat reset")


if __name__ == "__main__":
    main()
