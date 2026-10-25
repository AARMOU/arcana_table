"""Check what a weekly-upgrade candidate actually changed, after `git apply --index`.

Run from the repository root, on the publish side, from a copy taken BEFORE the candidate is applied
(the candidate must not be able to rewrite its own checker):

    cp engine-upgrade/candidate_scope.py "$RUNNER_TEMP/scope.py"
    git apply --index candidate.patch
    python3 -I "$RUNNER_TEMP/scope.py"

It reads the staged index (git's own view, so quoted paths, renames and mode changes cannot hide from
it) and fails unless every change is one the upgrade itself makes:
  * argentum-patches/<name>.patch            added, modified or deleted
  * engine-upgrade/forward-ports/<name>      deleted only (folded into the refreshed patch)
  * engine-upgrade/audit_patches.py          only its TARGET pin and MANDATORY list may change
  * engine-upgrade/preview-config.json       modified
  * android/app/libs/argentum-<module>.jar   modified
  * android/app/src/main/assets/argentum/... added, modified or deleted
Every resulting file must be a regular, non-executable file (no symlinks, submodules or +x).
Exit 0 when the candidate is in scope, 1 otherwise. Tests: engine-upgrade/tests/test_candidate_scope.py
"""
from __future__ import annotations

import re
import subprocess
import sys

RULES = [
    (re.compile(r"argentum-patches/[A-Za-z0-9._-]+\.patch"), "AMD"),
    (re.compile(r"engine-upgrade/forward-ports/[A-Za-z0-9._-]+"), "D"),
    (re.compile(r"engine-upgrade/audit_patches\.py"), "M"),
    (re.compile(r"engine-upgrade/preview-config\.json"), "M"),
    (re.compile(r"android/app/libs/argentum-[a-z0-9-]+\.jar"), "M"),
    (re.compile(r"android/app/src/main/assets/argentum/[A-Za-z0-9._/-]+"), "AMD"),
]
REGULAR = {"100644", "000000"}  # 000000 = the file is deleted


def git(*args: str) -> str:
    return subprocess.run(["git", *args], check=True, capture_output=True, text=True).stdout


def staged() -> list[tuple[str, str, str]]:
    """(new_mode, status, path) per staged change; renames are split into a delete and an add."""
    out = git("diff", "--cached", "--raw", "-z", "--no-renames", "--no-abbrev")
    fields = out.split("\0")
    changes, i = [], 0
    while i < len(fields) and fields[i]:
        meta = fields[i].lstrip(":").split()
        changes.append((meta[1], meta[4][0], fields[i + 1]))
        i += 2
    return changes


def strip_pins(source: str) -> str:
    source = re.sub(r'^TARGET = "[0-9a-f]{40}"$', "TARGET = <pin>", source, flags=re.M)
    return re.sub(r"^MANDATORY = \[\n.*?^\]$", "MANDATORY = <list>", source, flags=re.M | re.S)


def problems() -> list[str]:
    found = []
    changes = staged()
    if not changes:
        return ["the candidate changes nothing"]
    for new_mode, status, path in changes:
        rule = next((ok for pattern, ok in RULES if pattern.fullmatch(path)), None)
        if ".." in path.split("/"):
            found.append(f"{path}: path traversal")
        elif rule is None:
            found.append(f"{path}: outside the upgrade scope")
        elif status not in rule:
            found.append(f"{path}: change type {status} not allowed here (allowed: {rule})")
        if new_mode not in REGULAR:
            found.append(f"{path}: mode {new_mode} (only regular non-executable files are allowed)")
    if any(path == "engine-upgrade/audit_patches.py" for _, _, path in changes):
        before = git("show", "HEAD:engine-upgrade/audit_patches.py")
        after = git("show", ":engine-upgrade/audit_patches.py")
        if strip_pins(before) != strip_pins(after):
            found.append("engine-upgrade/audit_patches.py: changes beyond the TARGET pin and MANDATORY list")
    return found


def main() -> int:
    found = problems()
    for line in found:
        print(f"::error title=Candidate out of scope::{line}")
    if found:
        return 1
    print(f"{len(staged())} staged changes, all inside the upgrade scope")
    return 0


if __name__ == "__main__":
    sys.exit(main())
