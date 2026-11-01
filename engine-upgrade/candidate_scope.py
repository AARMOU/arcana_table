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
  * docs/agents/generated/sdk-api.{md,json}  modified (regenerated from the new jar)
  * docs/agents/argentum-upstream/<3 files>  modified (re-vendored from upstream at the new pin)
  * AGENTS.md, docs/agents/CARD-WORKFLOW.md, docs/agents/argentum-upstream/README.md,
    tools/cardkit/cardkit_lib/repo.py        only the pinned upstream commit may change
  * tools/cardkit/data/verified-support.json only `cardkit restamp`: the same cards, each either unchanged or
                                             with a new runtimeHash plus exactly one appended `restamps`
                                             record {fromRuntimeHash: old, toRuntimeHash: new, check}; nothing else
Every resulting file must be a regular, non-executable file (no symlinks, submodules or +x).
Exit 0 when the candidate is in scope, 1 otherwise. Tests: engine-upgrade/tests/test_candidate_scope.py
"""
from __future__ import annotations

import json
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
    (re.compile(r"docs/agents/generated/sdk-api\.(md|json)"), "M"),
    (re.compile(r"docs/agents/argentum-upstream/(card-sdk-language-reference|sdk-index|add-card-examples)\.md"), "M"),
]
# Files where only the pinned upstream commit (full SHA, or `abc1234…`) may change.
PIN_ONLY = ("AGENTS.md", "docs/agents/CARD-WORKFLOW.md", "docs/agents/argentum-upstream/README.md",
            "tools/cardkit/cardkit_lib/repo.py")
RULES += [(re.compile(re.escape(path)), "M") for path in PIN_ONLY]
RESTAMP_FILE = "tools/cardkit/data/verified-support.json"
RULES += [(re.compile(re.escape(RESTAMP_FILE)), "M")]


def restamp_problems(before: str, after: str) -> list[str]:
    """`cardkit restamp` may only re-bind reviews to a new runtime hash and log that it did."""
    try:
        old, new = json.loads(before), json.loads(after)
    except ValueError:
        return [f"{RESTAMP_FILE}: not valid JSON"]
    if not isinstance(old, dict) or not isinstance(new, dict):
        return [f"{RESTAMP_FILE}: not a JSON object"]
    if {k: v for k, v in old.items() if k != "cards"} != {k: v for k, v in new.items() if k != "cards"}:
        return [f"{RESTAMP_FILE}: changes outside the card entries"]
    oc, nc = old.get("cards") or {}, new.get("cards") or {}
    if not isinstance(oc, dict) or not isinstance(nc, dict) or set(oc) != set(nc):
        return [f"{RESTAMP_FILE}: cards added or removed"]
    found, targets = [], set()
    for name, a in oc.items():
        b = nc[name]
        if a == b:
            continue
        rest = lambda e: {k: v for k, v in e.items() if k not in ("runtimeHash", "restamps")}
        prev, logs = a.get("restamps", []), b.get("restamps", [])
        if not (isinstance(a, dict) and isinstance(b, dict) and rest(a) == rest(b) and isinstance(logs, list)
                and logs[:len(prev)] == prev and len(logs) == len(prev) + 1):
            found.append(f"{RESTAMP_FILE}: {name}: changes beyond a runtime restamp")
            continue
        log, to = logs[-1], b.get("runtimeHash")
        if not (isinstance(log, dict) and set(log) == {"fromRuntimeHash", "toRuntimeHash", "check"}
                and log["fromRuntimeHash"] == a.get("runtimeHash") and log["toRuntimeHash"] == to
                and isinstance(to, str) and re.fullmatch(r"[0-9a-f]{64}", to)):
            found.append(f"{RESTAMP_FILE}: {name}: restamp record does not match the hash change")
        targets.add(to)
    if len(targets) > 1:
        found.append(f"{RESTAMP_FILE}: restamped to more than one runtime hash")
    return found
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
    if any(path == RESTAMP_FILE and status == "M" for _, status, path in changes):
        found += restamp_problems(git("show", f"HEAD:{RESTAMP_FILE}"), git("show", f":{RESTAMP_FILE}"))
    pin_changes = [path for _, _, path in changes if path in PIN_ONLY]
    if pin_changes:
        try:
            old = json.loads(git("show", "HEAD:engine-upgrade/preview-config.json"))["upstream"]
            new = json.loads(git("show", ":engine-upgrade/preview-config.json"))["upstream"]
        except (subprocess.CalledProcessError, KeyError, ValueError):
            old = new = ""
        for path in pin_changes:
            before, after = git("show", f"HEAD:{path}"), git("show", f":{path}")
            if not (old != new and all(re.fullmatch(r"[0-9a-f]{40}", str(x)) for x in (old, new))):
                found.append(f"{path}: changed without a pin move in engine-upgrade/preview-config.json")
            elif after.replace(new, old).replace(f"`{new[:7]}\u2026`", f"`{old[:7]}\u2026`") != before:
                found.append(f"{path}: changes beyond the pinned upstream commit")
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
