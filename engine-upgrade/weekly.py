#!/usr/bin/env python3
"""Weekly Argentum upgrade: move Arcana onto upstream's latest engine without breaking imported cards.

    weekly.py rebase   ENGINE TARGET  rebuild the patch series as commits on the current pin, rebase
                       [--keep-conflict]  them onto TARGET (three-way merge, not strict patch application),
                                      then rewrite argentum-patches/ and the pins from the result;
                                      --keep-conflict leaves a conflict in place for a person or agent
    weekly.py checkout ENGINE         the series as one commit per patch on the pin, for hand fixes
    weekly.py finish   ENGINE         after a hand-resolved rebase or --fixup commits: record conflict
                                      resolutions in engine-upgrade/rerere/, rewrite patches and pins
    weekly.py handoff  KIND UPSTREAM  write weekly/handoff.md (conflict | build_failed | needs_work)
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
import os
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
PRECONS = ROOT / "investigation/argentum-six-precons/deck-manifest.json"
MODULES = {
    "mtg-sdk": "com/wingedsheep/sdk/model/CardDefinition.class",
    "mtg-sdk-tooling": "com/wingedsheep/sdk/tooling/CardLoader.class",
    "rules-engine": "com/wingedsheep/engine/core/GameAction.class",
    "ai": "com/wingedsheep/ai/engine/AIPlayer.class",
}
EXPORTS = ("all-cards.jsonl", "por-cards.jsonl", "all-cards.index.tsv")
PREFIX = "arcana: "
PATCH_DATE = "2000-01-01T00:00:00+0000"


def run(*args, cwd=None, check=True, env=None):
    result = subprocess.run(args, cwd=cwd, text=True, capture_output=True,
                            env={**os.environ, **env} if env else None)
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

STATE = ".git/arcana-weekly.json"  # inside the engine checkout: what checkout/rebase set up, for finish


def engine_git(engine):
    return lambda *a, check=True: run("git", *a, cwd=engine, check=check)


def prepare(engine, pin, mandatory):
    """The series as commits on the pin (the strict audit proves it applies there), with rerere primed."""
    git = engine_git(engine)
    git("config", "user.name", "arcana-weekly-bot")
    git("config", "user.email", "41898282+github-actions[bot]@users.noreply.github.com")
    git("config", "rerere.enabled", "true")
    git("config", "rerere.autoupdate", "true")
    cache = engine / ".git/rr-cache"
    cache.mkdir(parents=True, exist_ok=True)
    if RERERE.is_dir():
        shutil.copytree(RERERE, cache, dirs_exist_ok=True)
    git("checkout", "-q", "-f", "-B", "arcana", pin)
    for name in mandatory:
        git("apply", "--index", str(patch_file(name)))
        # Fixed dates: the date goes into each patch file's header, so a patch whose content did not
        # change comes out byte-identical instead of showing up as changed every week.
        run("git", "commit", "-q", "--allow-empty", "-m", PREFIX + name[:-len(".patch")], cwd=engine,
            env={"GIT_AUTHOR_DATE": PATCH_DATE, "GIT_COMMITTER_DATE": PATCH_DATE})


def save_state(engine, **state):
    (engine / STATE).write_text(json.dumps(state, indent=2) + "\n")


def rebase(engine_dir, target, *flags):
    """Rebase the series onto TARGET. --keep-conflict leaves an unresolvable conflict in place (for a
    person or agent to resolve, then `finish`) instead of aborting. With WEEKLY_FORCE=1 a branch whose pin
    already equals TARGET is still validated as-is (fix branches), instead of reporting up_to_date."""
    engine = Path(engine_dir).resolve()
    git = engine_git(engine)
    text, pin, mandatory = audit_state()
    target = git("rev-parse", target).stdout.strip()
    report = {"old_pin": pin, "target": target, "status": "pending", "patches": [], "conflicts": []}
    if target == pin:
        if os.environ.get("WEEKLY_FORCE") != "1":
            report["status"] = "up_to_date"
            write("rebase.json", report)
            print("Already on upstream", target)
            return 0
        prepare(engine, pin, mandatory)  # proves the series applies; nothing to rewrite
        report.update(status="rebased", upstream_commits=0, auto_resolved=[],
                      patches=[{"patch": n, "status": "unchanged"} for n in mandatory],
                      engine_head=git("rev-parse", "HEAD").stdout.strip())
        write("rebase.json", report)
        write("auto-resolved.md", "")
        print(f"Validating this branch's {len(mandatory)} patches as-is on {target}")
        return 0
    report["upstream_commits"] = int(git("rev-list", "--count", f"{pin}..{target}").stdout)
    prepare(engine, pin, mandatory)
    save_state(engine, pin=pin, target=target, mode="rebase")

    # Three-way rebase onto the new upstream, auto-resolving only the safe cases.
    # rerere cannot record "keep this file deleted", so files an Arcana patch deliberately replaces are
    # listed in rebase-rules.json; upstream edits to them are reported, never silently dropped.
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
                "kind": "modify/delete" if not git("ls-files", "-u", "--", path).stdout.count(" 3\t") else "content",
                "diff": git("diff", "--", path, check=False).stdout[-5000:],
            } for path in unresolved]
            write("rebase.json", report)
            write("rebase.md", conflict_markdown(report))
            if "--keep-conflict" in flags:
                print(f"Conflict left in place in {engine} for: {unresolved}\n"
                      "Resolve each file, `git add` it, run `git -c core.editor=true rebase --continue`, repeat until\n"
                      "the rebase completes, then run: python3 engine-upgrade/weekly.py finish " + str(engine_dir))
            else:
                git("rebase", "--abort", check=False)
                print("Conflict needs a person or agent:", unresolved)
            return 2
        step = run("git", "-c", "core.editor=true", "rebase", "--continue", cwd=engine, check=False)
        if step.returncode and "nothing to commit" in step.stdout + step.stderr:
            step = git("rebase", "--skip", check=False)

    export_series(engine, target, mandatory, text, report)
    print(f"Rebased {len([p for p in report['patches'] if p['status'] == 'refreshed'])} patches onto {target} "
          f"({report['upstream_commits']} upstream commits)")
    return 0


def checkout(engine_dir):
    """Set up the engine checkout for hand-editing: the current series as one commit per patch on the
    pin. Fix code, commit each fix as `git commit --fixup <that patch's commit>`, then `finish`."""
    engine = Path(engine_dir).resolve()
    text, pin, mandatory = audit_state()
    prepare(engine, pin, mandatory)
    save_state(engine, pin=pin, target=pin, mode="checkout")
    print(f"{engine}: branch `arcana`, {len(mandatory)} patch commits on {pin[:10]}:")
    print(run("git", "log", "--oneline", f"{pin}..HEAD", cwd=engine).stdout)
    print("Commit fixes with `git commit --fixup <commit>`, then: python3 engine-upgrade/weekly.py finish " + str(engine_dir))
    return 0


def finish(engine_dir):
    """After a hand-resolved rebase or hand-made fixes: record new conflict resolutions in
    engine-upgrade/rerere/ (so the robot replays them) and rewrite argentum-patches/ and the pins."""
    engine = Path(engine_dir).resolve()
    git = engine_git(engine)
    state_file = engine / STATE
    if not state_file.is_file():
        raise SystemExit("Run `weekly.py checkout` or `weekly.py rebase ... --keep-conflict` first")
    state = json.loads(state_file.read_text())
    if (engine / ".git/rebase-merge").exists() or (engine / ".git/rebase-apply").exists():
        raise SystemExit("A rebase is still in progress: resolve, `git add`, `git -c core.editor=true rebase --continue` until done")
    if git("status", "--porcelain", "--untracked-files=no").stdout.strip():
        raise SystemExit("Uncommitted changes in the engine checkout: commit them (as --fixup of the owning patch)")
    target, pin = state["target"], state["pin"]
    base = git("merge-base", "HEAD", target).stdout.strip()
    if base != target:
        raise SystemExit(f"HEAD is not on top of the target {target[:10]}")
    # Fold fixup! commits into the patch they fix.
    if git("log", "--format=%s", f"{target}..HEAD").stdout.count("fixup! "):
        run("git", "-c", "sequence.editor=true", "rebase", "-q", "-i", "--autosquash", target, cwd=engine)
    subjects = git("log", "--format=%s", f"{target}..HEAD").stdout.splitlines()
    strays = [s for s in subjects if not s.startswith(PREFIX)]
    if strays:
        raise SystemExit(f"Commits that belong to no patch (use --fixup): {strays}")
    recorded = []
    cache = engine / ".git/rr-cache"
    for entry in sorted(cache.iterdir()) if cache.is_dir() else []:
        if (entry / "postimage").is_file() and not (RERERE / entry.name).exists():
            shutil.copytree(entry, RERERE / entry.name)
            recorded.append(entry.name)
    text, _, mandatory = audit_state()
    report = {"old_pin": pin, "target": target, "status": "pending", "patches": [], "conflicts": [],
              "auto_resolved": [], "upstream_commits": int(git("rev-list", "--count", f"{pin}..{target}").stdout)}
    export_series(engine, target, mandatory, text, report)
    state_file.unlink()
    print(f"Rewrote argentum-patches/ from {len(subjects)} commits on {target[:10]}; "
          f"recorded {len(recorded)} conflict resolution(s) in engine-upgrade/rerere/: {recorded}")
    return 0


def export_series(engine, target, mandatory, text, report):
    """Rewrite argentum-patches/ from the `arcana: <patch>` commits on TARGET, then the pins. Absorbed patches
    drop out; changelog hunks never come back, so they cannot conflict again next week."""
    git = engine_git(engine)
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
    experimental = re.search(r'^EXPERIMENTAL = "([^"]+)"', text, re.M)
    if experimental and (PATCHES / experimental[1]).is_file():
        probe = git("apply", "--check", str(PATCHES / experimental[1]), check=False)
        report["experimental"] = {"patch": experimental[1], "applies": probe.returncode == 0}

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
    return kept


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
             f"`{report['old_pin'][:10]}`. A patch no longer merges on its own; see **Handoff** below.", ""]
    for conflict in report["conflicts"]:
        kind = conflict.get("kind", "content")
        lines += [f"### `{conflict['patch']}` → `{conflict['file']}` ({kind})", "", "```diff",
                  conflict["diff"].strip() or "(no text diff: the patch deletes this file and upstream edited it)", "```", ""]
    return "\n".join(lines)


def handoff(kind, upstream, run_url=""):
    """Write weekly/handoff.md: exact next steps for whoever picks up a red run (Claude, or GPT covering).
    KIND: conflict | build_failed | needs_work."""
    import datetime
    day = datetime.date.today().isoformat()
    fix = f"gpt/argentum-weekly-fix-{day}"
    clone = ["git clone --filter=blob:none https://github.com/wingedsheep/argentum-engine argentum-engine"]
    if kind == "conflict":
        start = "origin/gpt/arcana-integration-next"
        steps = [f"git fetch origin && git checkout -b {fix} {start}", *clone,
                 f"python3 engine-upgrade/weekly.py rebase argentum-engine {upstream} --keep-conflict",
                 "# resolve each conflicted file in argentum-engine/ (keep BOTH upstream's change and the patch's intent),",
                 "# then: git -C argentum-engine add <file> && git -C argentum-engine -c core.editor=true rebase --continue",
                 "# repeat until the rebase completes; a modify/delete conflict on a file Arcana replaces goes in",
                 "# engine-upgrade/rebase-rules.json (keep_deleted, with a `why`) instead, then re-run the rebase command",
                 "python3 engine-upgrade/weekly.py finish argentum-engine",
                 "git add argentum-patches engine-upgrade && git commit -m 'Resolve weekly upgrade conflict'",
                 f"git push -u origin {fix}   # starts a dry run automatically (read-only, publishes nothing)"]
        goal = "the patch series rebases onto the target; the resolution is recorded in engine-upgrade/rerere/"
    elif kind == "build_failed":
        start = "origin/bot/argentum-weekly"
        steps = [f"git fetch origin && git checkout -b {fix} {start}   # refreshed patches + new pin", *clone,
                 "python3 engine-upgrade/weekly.py checkout argentum-engine   # one commit per patch on the new pin",
                 "# fix the compiler errors listed above inside argentum-engine/; find the owning patch with",
                 "#   git -C argentum-engine log --oneline -- <file>",
                 "# commit each fix as: git -C argentum-engine commit -a --fixup <that patch's commit>",
                 "python3 engine-upgrade/weekly.py finish argentum-engine   # folds fixups, rewrites argentum-patches/",
                 "git add argentum-patches engine-upgrade && git commit -m 'Fix weekly upgrade engine build'",
                 f"git push -u origin {fix}   # starts a dry run automatically (read-only, publishes nothing)"]
        goal = "the patched engine compiles and the strict audit passes"
    else:
        start = "origin/bot/argentum-weekly"
        steps = [f"git fetch origin && git checkout -b {fix} {start}",
                 "# custom-card collision: compare upstream's card with Arcana's; run the card's positive and negative",
                 "#   scenarios against upstream's version; if upstream is correct, retire the custom card (definition,",
                 "#   CustomCards.kt entry, re-export); if not, record why it stays. Never exempt it from the test.",
                 "# failing test: find the root cause; never weaken, skip or delete the test (AGENTS.md rules)",
                 "cd android && ./gradlew --no-daemon :custom-cards:exportCards :custom-cards:test :app:testDebugUnitTest",
                 f"git push -u origin {fix}   # starts a dry run automatically (read-only, publishes nothing)"]
        goal = "every check in the table above is green"
    lines = ["### Handoff", "",
             f"**Who:** engine fixes are Claude's lane; GPT finishes when Claude is unavailable (issue #81). Card "
             f"decisions are GPT's lane. Full runbook: `engine-upgrade/HANDOFF.md`.", "",
             f"**Done when:** {goal}, and the dry run on `{fix}` is green except for anything listed as a card "
             f"decision. Then open a draft PR from `{fix}` into `gpt/arcana-integration-next`, link this run, post on #81. "
             "The owner merges.", "",
             f"**Start from:** `{start}` · upstream `{upstream}`" + (f" · run {run_url}" if run_url else ""), "",
             "```sh", *steps, "```", "",
             "Never: push to `bot/argentum-weekly` (the robot refuses to overwrite it and stops), weaken or skip a test, "
             "add a collision exemption, or merge."]
    write("handoff.md", "\n".join(lines) + "\n")
    print("\n".join(lines))
    return 0


# ---------------------------------------------------------------- docs

VENDORED = ROOT / "docs/agents/argentum-upstream"
VENDOR_FILES = {  # Arcana copy -> upstream path (docs/agents/argentum-upstream/README.md)
    "card-sdk-language-reference.md": "docs/card-sdk-language-reference.md",
    "sdk-index.md": "docs/sdk-index.md",
    "add-card-examples.md": ".agents/skills/add-card/examples.md",
}
# Files that state the pinned upstream commit. Only the pin may change in them
# (engine-upgrade/candidate_scope.py enforces that on the publish side).
PIN_FILES = ("AGENTS.md", "docs/agents/CARD-WORKFLOW.md", "docs/agents/argentum-upstream/README.md",
             "tools/cardkit/cardkit_lib/repo.py")


def short_pin(sha):
    return f"`{sha[:7]}\u2026`"


def docs(engine_dir):
    """Keep agent-facing docs on the new pin: re-vendor upstream's DSL docs and move the stated pin.

    The SDK index (docs/agents/generated/sdk-api.*) is regenerated from the new jar by
    `tools/cardkit/cardkit sdk-index` in the workflow, right after this.
    """
    engine = Path(engine_dir).resolve()
    report = json.loads((OUT / "rebase.json").read_text())
    old, new = report["old_pin"], report["target"]
    if not all(re.fullmatch(r"[0-9a-f]{40}", x) for x in (old, new)):
        raise SystemExit(f"Bad pins: {old!r} -> {new!r}")
    missing = []
    for name, upstream in VENDOR_FILES.items():
        shown = run("git", "show", f"{new}:{upstream}", cwd=engine, check=False)
        if shown.returncode:
            missing.append(upstream)
        else:
            (VENDORED / name).write_text(shown.stdout)
    stale = []
    for rel in PIN_FILES:
        path = ROOT / rel
        text = path.read_text()
        if old not in text and short_pin(old) not in text:
            stale.append(rel)  # the file does not state the pin we are moving from
        path.write_text(text.replace(old, new).replace(short_pin(old), short_pin(new)))
    write("docs.json", {"old_pin": old, "new_pin": new, "missing_upstream_docs": missing, "pin_not_found": stale})
    for item in missing:
        print(f"::error title=Vendored doc gone upstream::{item}")
    for item in stale:
        print(f"::error title=Pin not found::{item} does not mention {old[:10]}")
    return 1 if missing or stale else 0


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
    # Fail closed: a collision is never exempted here. CustomCardDataTest.noCustomCardShadowsAnArgentumCard
    # stays strict, so the upgrade stays red until each collision is retired or approved in its own change.
    report["collisions"] = sorted(n for n in custom if n.lower() in new_set)
    report["precons"] = precon_report(old_set, new_set, custom_set)
    write("cards.json", report)
    write("cards.md", cards_markdown(report))
    print(cards_markdown(report))
    blocked = report["deck_cards_missing"] or report["collisions"] or any(
        deck["newly_missing"] for deck in report["precons"])
    return 1 if blocked else 0


def precon_report(old_set, new_set, custom_set):
    """The six official precons, slot by slot (quantities count). Indexed means the name resolves to an
    Argentum or custom definition; it says nothing about Oracle-correct rules, which only the scenario
    tests establish."""
    if not PRECONS.is_file():
        return []

    def indexed(name, library):
        key = name.lower()
        front = key.split(" // ")[0]
        return any(k in library or k in custom_set for k in (key, front))

    decks = []
    for deck in json.loads(PRECONS.read_text())["decks"]:
        cards = deck["cards"]
        slots = sum(c["quantity"] for c in cards)
        before = sum(c["quantity"] for c in cards if indexed(c["name"], old_set))
        after = sum(c["quantity"] for c in cards if indexed(c["name"], new_set))
        decks.append({
            "name": deck["name"], "commander": deck["commander"], "slots": slots,
            "indexed_before": before, "indexed_after": after,
            "commander_indexed": indexed(deck["commander"], new_set),
            "newly_missing": sorted(c["name"] for c in cards
                                    if indexed(c["name"], old_set) and not indexed(c["name"], new_set)),
            "newly_indexed": sorted(c["name"] for c in cards
                                    if not indexed(c["name"], old_set) and indexed(c["name"], new_set)),
        })
    return decks


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
    if r.get("precons"):
        lines += ["", "**Six official precons** — slots whose name resolves (indexed). Indexed is not "
                  "rules-verified; Oracle-correct behavior comes only from the scenario tests.", "",
                  "| Deck | Commander | Indexed before | Indexed after | Newly missing |", "|---|---|---|---|---|"]
        for d in r["precons"]:
            lines.append(f"| {d['name']} | {d['commander']}{'' if d['commander_indexed'] else ' ❌'} | "
                         f"{d['indexed_before']}/{d['slots']} | {d['indexed_after']}/{d['slots']} | "
                         f"{', '.join(d['newly_missing']) or '—'} |")
        gained = sorted({c for d in r["precons"] for c in d["newly_indexed"]})
        if gained:
            lines += ["", "Newly indexed precon cards (upstream added them; need scenario coverage before "
                      "counting as verified): " + ", ".join(gained)]
    if r["collisions"]:
        lines += ["", "**❌ Upstream now implements these custom cards — decision required (blocks the upgrade):**",
                  "For each: compare upstream's implementation with Arcana's, run the card's positive and negative "
                  "scenarios against upstream's version, then retire the custom copy or record why it stays. "
                  "`CustomCardDataTest` stays strict until then.", ""]
        lines += bullet(f"{c}" + (f" — in {', '.join(r['deck_cards_now_upstream'][c])}"
                                  if c in r["deck_cards_now_upstream"] else "") for c in r["collisions"])
    else:
        lines += ["", "✅ No custom card collides with an upstream card."]
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
    sys.exit({"rebase": rebase, "checkout": checkout, "finish": finish, "handoff": handoff, "install": install,
              "docs": docs, "cards": cards, "summary": summary}[command](*args) or 0)
