"""Keeps the hand-written agent docs honest.

- Every `Facade.member` written in code (Effects, Triggers, Conditions, Costs, DynamicAmounts, Filters,
  Targets, Patterns) must exist in the committed SDK index.
- Every fenced block preceded by `<!-- excerpt: path/to/File.kt -->` must appear verbatim (ignoring
  indentation) in that file, so the cookbook can only show code that really compiles and is tested.
- Every relative markdown link must point at a file that exists.
"""
from __future__ import annotations

import re
from pathlib import Path

from . import repo, sdk

DOCS = [repo.ROOT / "AGENTS.md", repo.ROOT / ".github/copilot-instructions.md", repo.DOCS / "CARD-PATTERNS.md",
        repo.DOCS / "CARD-WORKFLOW.md", repo.DOCS / "README.md"]

_CHAIN = re.compile(r"\b(Effects|Triggers|Conditions|Costs|DynamicAmounts|Filters|Targets|Patterns)\.([A-Za-z_]\w*)(?:\.([a-z]\w*))?")
_EXCERPT = re.compile(r"<!--\s*excerpt:\s*([^\s>]+)\s*-->\s*\n```[a-z]*\n(.*?)\n```", re.S)
_LINK = re.compile(r"\]\(((?!https?:|#|mailto:)[^)\s#]+)(?:#[^)]*)?\)")
_SUBJECT_CLASS = {"self": "ObjectTriggerSubject", "attached": "ObjectTriggerSubject", "a": "ObjectTriggerSubject",
                  "another": "ObjectTriggerSubject", "you": "PlayerTriggerSubject", "anOpponent": "PlayerTriggerSubject",
                  "anyPlayer": "PlayerTriggerSubject", "chosenOpponent": "PlayerTriggerSubject",
                  "player": "PlayerTriggerSubject", "oneOrMore": "BatchTriggerSubject", "oneOrMoreOther": "BatchTriggerSubject"}
_PATTERN_CLASS = {"Library": "LibraryPatterns", "Hand": "HandPatterns", "Exile": "ExilePatterns", "Group": "GroupPatterns",
                  "Mechanic": "MechanicPatterns", "CreatureType": "CreatureTypePatterns", "Sideboard": "SideboardPatterns"}


def _code_spans(md: str) -> list[str]:
    fences = re.findall(r"```[a-z]*\n(.*?)\n```", md, re.S)
    inline = re.findall(r"`([^`\n]+)`", re.sub(r"```.*?```", "", md, flags=re.S))
    return fences + inline


def check_file(path: Path) -> list[str]:
    errors = []
    if not path.exists():
        return [f"{repo.rel(path)}: missing"]
    md = path.read_text(encoding="utf-8")
    for code in _code_spans(md):
        for facade, member, sub in _CHAIN.findall(code):
            if len(member) == 1:  # placeholder such as `Effects.X`
                continue
            if not sdk.member_exists(facade, member):
                errors.append(f"{repo.rel(path)}: `{facade}.{member}` is not in the SDK index")
                continue
            if facade == "Triggers" and sub and member in _SUBJECT_CLASS:
                if not sdk.member_exists(_SUBJECT_CLASS[member], sub):
                    errors.append(f"{repo.rel(path)}: `Triggers.{member}.{sub}` is not in the SDK index")
            if facade == "Patterns" and sub and member in _PATTERN_CLASS:
                if not sdk.member_exists(_PATTERN_CLASS[member], sub):
                    errors.append(f"{repo.rel(path)}: `Patterns.{member}.{sub}` is not in the SDK index")
    for src, block in _EXCERPT.findall(md):
        target = repo.ROOT / src
        if not target.exists():
            errors.append(f"{repo.rel(path)}: excerpt source {src} does not exist")
            continue
        want = [l.strip() for l in block.splitlines() if l.strip() and l.strip() != "// …"]
        have = [l.strip() for l in target.read_text(encoding="utf-8").splitlines() if l.strip()]
        pos = 0
        for line in want:
            try:
                pos = have.index(line, pos) + 1
            except ValueError:
                errors.append(f"{repo.rel(path)}: excerpt line not found in {src} (in order): {line}")
                break
    for link in _LINK.findall(md):
        target = (path.parent / link).resolve()
        if not target.exists():
            errors.append(f"{repo.rel(path)}: broken link {link}")
    return errors


def run() -> list[str]:
    errors = []
    for p in DOCS:
        errors += check_file(p)
    return errors
