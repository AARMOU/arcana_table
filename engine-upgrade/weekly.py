#!/usr/bin/env python3
"""Weekly Argentum upgrade: move Arcana onto upstream's latest engine without breaking imported cards.

    weekly.py rebase   ENGINE TARGET  rebuild the patch series as commits on the current pin, rebase
                                      them onto TARGET (three-way merge, not strict patch application),
                                      then rewrite argentum-patches/ and the pins from the result
    weekly.py install  ENGINE         copy the four rebuilt Java 17 jars and exported card data into the app
    weekly.py cards    OLD_ASSETS     imported-card report: decks, custom cards, corpus diff
    weekly.py summary  RESULTS_DIR    test totals from JUnit XML

Conflicts are auto-resolved only where that is safe: CHANGELOG files (upstream wins) and resolutions
recorded in engine-upgrade/rerere/ (git rerere). Anything else stops the run with a report for a
person or agent to resolve, record and commit. Every step writes engine-upgrade/weekly/*.json|md,
which the workflow turns into the pull request body.
"""
from __future__ import annotations

import json
import re
import shutil
import struct
import subprocess
import sys
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
PATCHES = ROOT / "argentum-patches"
FORWARD = ROOT / "engine-upgrade/forward-ports"
AUDIT = ROOT / "engine-upgrade/audit_patches.py"
CONFIG = ROOT / "engine-upgrade/preview-config.json"
RERERE = ROOT / "engine-upgrade/rerere"
RULES = ROOT / "engine-upgrade/rebase-rules.json"
OUT = ROOT / "engine-upgrade/weekly"
ASSETS = ROOT / "android/app/src/main/assets/argentum"
LIBS = ROOT / "android/app/libs"
SHADOWED = ROOT / "engine-upgrade/shadowed-custom-cards.txt"
MODULES = {
    "mtg-sdk": "com/wingedsheep/sdk/model/CardDefinition.class",
    "mtg-sdk-tooling": "com/wingedsheep/sdk/tooling/CardLoader.class",
    "rules-engine": "com/wingedsheep/engine/core/GameAction.class",
    "ai": "com/wingedsheep/ai/engine/AIPlayer.class",
}
EXPORTS = ("all-cards.jsonl", "por-cards.jsonl", "all-cards.index.tsv")
PREFIX = "arcana: "


def run(*args, cwd=None, check=True):
    result = subprocess.run(args, cwd=cwd, text=True, capture_output=True)
    if check and result.returncode:
        raise SystemExit(f"{' '.join(args)} failed:\n{result.stdout}\n{result.stderr}")
    return result


def write(name, data):
    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / name).write_text(data if isinstance(data, str) else json.dumps(data, indent=2) + "\n")


def audit_state():
    text = AUDIT.read_text()
    pin = re.search(r'^TARGET = "([0-9a-f]{40})"', text, re.M)[1]
    block = re.search(r"^MANDATORY = \[\n(.*?)^\]", text, re.M | re.S)[1]
    return text, pin, re.findall(r'"([^"]+\.patch)"', block)


def patch_file(name):
    forward = FORWARD / name
    return forward if forward.is_file() else PATCHES / name


# ---------------------------------------------------------------- rebase

def rebase(engine_dir, target):
    engine = Path(engine_dir).resolve()
    git = lambda *a, check=True: run("git", *a, cwd=engine, check=check)
    text, pin, mandatory = audit_state()
    target = git("rev-parse", target).stdout.strip()
    report = {"old_pin": pin, "target": target, "status": "pending", "patches": [], "conflicts": []}
    if target == pin:
        report["status"] = "up_to_date"
        write("rebase.json", report)
        print("Already on upstream", target)
        return 0
    report["upstream_commits"] = int(git("rev-list", "--count", f"{pin}..{target}").stdout)

    git("config", "user.name", "arcana-weekly-bot")
    git("config", "user.email", "41898282+github-actions[bot]@users.noreply.github.com")
    git("config", "rerere.enabled", "true")
    git("config", "rerere.autoupdate", "true")
    cache = engine / ".git/rr-cache"
    cache.mkdir(parents=True, exist_ok=True)
    if RERERE.is_dir():
        shutil.copytree(RERERE, cache, dirs_exist_ok=True)

    # 1. The series as commits on the pin: the strict audit already proves it applies there.
    git("checkout", "-q", "-B", "arcana", pin)
    for name in mandatory:
        git("apply", "--index", str(patch_file(name)))
        git("commit", "-q", "--allow-empty", "-m", PREFIX + name[:-len(".patch")])

    # 2. Three-way rebase onto the new upstream, auto-resolving only the safe cases.
    #    rerere cannot record "keep this file deleted", so files an Arcana patch deliberately replaces are
    #    listed in rebase-rules.json; upstream edits to them are reported, never silently dropped.
    rules = json.loads(RULES.read_text()) if RULES.is_file() else {}
    keep_deleted = {entry["path"]: entry for entry in rules.get("keep_deleted", [])}
    report["auto_resolved"] = []
    step = git("rebase", target, check=False)
    while step.returncode:
        unresolved = git("diff", "--name-only", "--diff-filter=U").stdout.split()
        for path in [p for p in unresolved if p.endswith("CHANGELOG.md")]:
            git("checkout", "--ours", "--", path)  # during a rebase "ours" is upstream
            git("add", "--", path)
        for path in [p for p in unresolved if p in keep_deleted]:
            upstream_edits = git("log", "--format=%h %s", f"{pin}..{target}", "--", path).stdout.splitlines()
            git("rm", "-q", "--", path)
            report["auto_resolved"].append({"path": path, "rule": "keep_deleted",
                                            "why": keep_deleted[path].get("why", ""),
                                            "upstream_commits": upstream_edits})
        unresolved = git("diff", "--name-only", "--diff-filter=U").stdout.split()
        if unresolved:
            current = git("log", "-1", "--format=%s", "REBASE_HEAD", check=False).stdout.strip()
            report["status"] = "conflict"
            report["conflicts"] = [{
                "patch": current.removeprefix(PREFIX) + ".patch",
                "file": path,
                "diff": git("diff", "--", path, check=False).stdout[-5000:],
            } for path in unresolved]
            git("rebase", "--abort", check=False)
            write("rebase.json", report)
            write("rebase.md", conflict_markdown(report))
            print("Conflict needs a person or agent:", unresolved)
            return 2
        step = run("git", "-c", "core.editor=true", "rebase", "--continue", cwd=engine, check=False)
        if step.returncode and "nothing to commit" in step.stdout + step.stderr:
            step = git("rebase", "--skip", check=False)

    # 3. Rewrite the series from the rebased commits. Absorbed patches drop out; changelog hunks never
    #    come back, so they cannot conflict again next week.
    commits = git("log", "--reverse", "--format=%H %s", f"{target}..HEAD").stdout.splitlines()
    kept = []
    for line in commits:
        sha, subject = line.split(" ", 1)
        name = subject.removeprefix(PREFIX) + ".patch"
        body = git("format-patch", "-1", "--stdout", "--no-signature", "--zero-commit", sha,
                   "--", ".", ":(exclude,glob)**/CHANGELOG.md").stdout
        if "\ndiff --git " not in body:
            continue
        (PATCHES / name).write_text(body)
        kept.append(name)
    for name in mandatory:
        status = "refreshed" if name in kept else "retired_upstream_has_it"
        report["patches"].append({"patch": name, "status": status})
        if name not in kept:
            (PATCHES / name).unlink(missing_ok=True)
        (FORWARD / name).unlink(missing_ok=True)  # forward ports are folded into the refreshed patch

    # Experimental patch: report only, never part of the runtime.
    experimental = re.search(r'^EXPERIMENTAL = "([^"]+)"', text, re.M)[1]
    probe = git("apply", "--check", str(PATCHES / experimental), check=False)
    report["experimental"] = {"patch": experimental, "applies": probe.returncode == 0}

    # 4. Pins: audit target and mandatory list, preview config, recorded resolutions now obsolete.
    listing = "".join(f'    "{n}",\n' for n in kept)
    text = re.sub(r'^TARGET = "[0-9a-f]{40}"', f'TARGET = "{target}"', text, flags=re.M)
    text = re.sub(r"^MANDATORY = \[\n.*?^\]", "MANDATORY = [\n" + listing + "]", text, flags=re.M | re.S)
    AUDIT.write_text(text)
    config = json.loads(CONFIG.read_text())
    config["upstream"] = target
    CONFIG.write_text(json.dumps(config, indent=2) + "\n")
    report["status"] = "rebased"
    report["engine_head"] = git("rev-parse", "HEAD").stdout.strip()
    write("rebase.json", report)
    write("auto-resolved.md", auto_resolved_markdown(report))
    print(f"Rebased {len(kept)} patches onto {target} ({report['upstream_commits']} upstream commits)")
    return 0


def auto_resolved_markdown(report):
    if not report.get("auto_resolved"):
        return ""
    lines = ["### Upstream edited files Arcana replaces", "",
             "Resolved by `engine-upgrade/rebase-rules.json` (Arcana's replacement kept). Check upstream's change "
             "is covered by the replacement:", ""]
    for entry in report["auto_resolved"]:
        lines.append(f"- `{entry['path']}` — {entry['why']}")
        lines += [f"  - upstream {commit}" for commit in entry["upstream_commits"][:5]]
    return "\n".join(lines) + "\n"


def conflict_markdown(report):
    lines = [f"Upstream `{report['target'][:10]}` is {report.get('upstream_commits', '?')} commits ahead of the pin "
             f"`{report['old_pin'][:10]}`. A patch no longer merges on its own.", "",
             "**To unblock:** run the series onto the target with `git rerere` enabled, resolve the hunk, "
             "then copy the new `.git/rr-cache/<id>` folder into `engine-upgrade/rerere/` and commit it. "
             "The next weekly run replays that resolution automatically.", ""]
    for conflict in report["conflicts"]:
        lines += [f"### `{conflict['patch']}` → `{conflict['file']}`", "", "```diff", conflict["diff"].strip(), "```", ""]
    return "\n".join(lines)


# ---------------------------------------------------------------- install

def install(engine_dir):
    engine = Path(engine_dir).resolve()
    for module, identity in MODULES.items():
        jars = [p for p in (engine / module / "build/libs").glob("*.jar")
                if identity in zipfile.ZipFile(p).namelist()]
        if len(jars) != 1:
            raise SystemExit(f"Expected one production jar for {module}: {jars}")
        with zipfile.ZipFile(jars[0]) as archive:
            versions = [struct.unpack(">H", archive.read(n)[6:8])[0]
                        for n in archive.namelist() if n.endswith(".class")]
        if not versions or max(versions) > 61:
            raise SystemExit(f"{module}: Android needs Java 17 bytecode (found class version {max(versions)})")
        shutil.copyfile(jars[0], LIBS / f"argentum-{module}.jar")
    out = engine / "arcana-export-out"
    for name in EXPORTS:
        shutil.copyfile(out / name, ASSETS / name)
    cards = (ASSETS / "all-cards.jsonl").read_text().splitlines()
    index = (ASSETS / "all-cards.index.tsv").read_text().splitlines()
    if len(cards) != len(index):
        raise SystemExit("Card export and index disagree")
    for row in cards:
        json.loads(row)
    print("Installed four Java 17 jars and", len(cards), "exported cards")


# ---------------------------------------------------------------- imported cards

def names(index_file):
    return [line.split("\t")[0] for line in Path(index_file).read_text().splitlines() if line.strip()]


def rows(jsonl):
    return {json.loads(line)["name"].lower(): line for line in Path(jsonl).read_text().splitlines() if line.strip()}


def cards(old_dir):
    old_dir = Path(old_dir)
    old_lib, new_lib = names(old_dir / "all-cards.index.tsv"), names(ASSETS / "all-cards.index.tsv")
    old_set, new_set = {n.lower() for n in old_lib}, {n.lower() for n in new_lib}
    custom = names(ASSETS / "custom-cards.index.tsv")
    custom_set = {n.lower() for n in custom}
    decks = json.loads((ASSETS / "commander-decks.json").read_text())

    deck_cards = {}
    for deck in decks:
        for card in [deck.get("commander"), *deck.get("cards", {})]:
            if card:
                deck_cards.setdefault(card, []).append(deck["name"])

    def resolves(name, library):
        key = name.lower()
        front = key.split(" // ")[0]
        return key in library or key in custom_set or front in library or front in custom_set

    old_rows, new_rows = rows(old_dir / "all-cards.jsonl"), rows(ASSETS / "all-cards.jsonl")
    report = {
        "library": {"before": len(old_lib), "after": len(new_lib),
                    "added": sorted(n for n in new_lib if n.lower() not in old_set),
                    "removed": sorted(n for n in old_lib if n.lower() not in new_set)},
        "custom_cards": len(custom),
        # Argentum's version wins at runtime, so these imported cards now play with upstream's rules.
        "now_upstream": sorted(n for n in custom if n.lower() in new_set and n.lower() not in old_set),
        # Only a card that resolved before and no longer does is a regression; known swaps stay known.
        "deck_cards_missing": {c: d for c, d in sorted(deck_cards.items())
                               if resolves(c, old_set) and not resolves(c, new_set)},
        "deck_cards_newly_playable": {c: d for c, d in sorted(deck_cards.items())
                                      if not resolves(c, old_set) and resolves(c, new_set)},
        "deck_cards_still_swapped": sorted(c for c in deck_cards if not resolves(c, new_set)),
        "deck_cards_changed": {c: d for c, d in sorted(deck_cards.items())
                               if c.lower() in old_rows and c.lower() in new_rows
                               and old_rows[c.lower()] != new_rows[c.lower()]},
        "decks": len(decks),
    }
    report["deck_cards_now_upstream"] = {c: deck_cards[c] for c in report["now_upstream"] if c in deck_cards}
    # The data test refuses shadowed custom cards; list them so the upgrade can land and retirement
    # follows as its own reviewed change.
    shadowed = sorted(n for n in custom if n.lower() in new_set)
    SHADOWED.write_text("".join(n + "\n" for n in shadowed))
    report["shadowed_total"] = len(shadowed)
    write("cards.json", report)
    write("cards.md", cards_markdown(report))
    print(cards_markdown(report))
    return 1 if report["deck_cards_missing"] else 0


def bullet(items, limit=40):
    items = list(items)
    shown = [f"- {item}" for item in items[:limit]]
    if len(items) > limit:
        shown.append(f"- …and {len(items) - limit} more (see `engine-upgrade/weekly/cards.json`)")
    return shown or ["- none"]


def cards_markdown(r):
    lib = r["library"]
    lines = ["### Imported cards", "",
             f"Library {lib['before']} → {lib['after']} cards (+{len(lib['added'])} / −{len(lib['removed'])}). "
             f"{r['custom_cards']} custom cards, {r['decks']} premade decks checked.", ""]
    if r["deck_cards_missing"]:
        lines += ["**❌ Deck cards that no longer resolve (blocks the upgrade):**", ""]
        lines += bullet(f"{c} — {', '.join(d)}" for c, d in r["deck_cards_missing"].items())
    else:
        lines += ["✅ Every premade-deck card that worked before still resolves."]
    if r["deck_cards_newly_playable"]:
        lines += ["", "**🎉 Deck cards upstream now makes playable** (no longer swapped for a basic land):", ""]
        lines += bullet(f"{c} — {', '.join(d)}" for c, d in r["deck_cards_newly_playable"].items())
    if r["deck_cards_still_swapped"]:
        lines += ["", "Still swapped (unchanged): " + ", ".join(r["deck_cards_still_swapped"])]
    lines += ["", "**Custom cards upstream now implements** (the app switches to Argentum's version; "
              "retire the custom copy in a follow-up):", ""]
    lines += bullet(f"{c}" + (f" — in {', '.join(r['deck_cards_now_upstream'][c])}"
                              if c in r["deck_cards_now_upstream"] else "") for c in r["now_upstream"])
    lines += ["", "**Deck cards whose upstream rules changed** (worth a phone check):", ""]
    lines += bullet(f"{c} — {', '.join(d)}" for c, d in r["deck_cards_changed"].items())
    if lib["removed"]:
        lines += ["", "**Removed from the library:**", ""] + bullet(lib["removed"])
    return "\n".join(lines) + "\n"


# ---------------------------------------------------------------- tests

def summary(results_dir):
    totals = dict(tests=0, failures=0, errors=0, skipped=0)
    failed = []
    for report in Path(results_dir).glob("**/TEST-*.xml"):
        root = ET.parse(report).getroot()
        for key in totals:
            totals[key] += int(root.get(key, "0"))
        for case in root.iter("testcase"):
            if case.find("failure") is not None or case.find("error") is not None:
                failed.append(f"{case.get('classname', '').split('.')[-1]}.{case.get('name')}")
    totals["failed"] = failed[:50]
    write("tests.json", totals)
    print(totals)
    return 0


if __name__ == "__main__":
    command, *args = sys.argv[1:]
    sys.exit({"rebase": rebase, "install": install, "cards": cards, "summary": summary}[command](*args) or 0)
