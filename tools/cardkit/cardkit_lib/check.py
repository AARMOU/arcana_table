"""`cardkit check`: repository rules every card PR must keep. Offline, stdlib only, CI-friendly.

Errors (exit 1):
  - a registered card still carries CARDKIT-SCAFFOLD / CARDKIT-TODO markers (in its file or its test)
  - a registered card has no scenario test that names it (unless baselined)
  - a card file defines a card that is not registered (unless baselined, still a scaffold, or now shipped
    by Argentum's library)
  - duplicate custom card names, or a registry entry whose val has no definition
  - with --finished: a card file the PR adds or changes is still a scaffold or is not registered (a card PR
    must ship the finished card, not leave it for the reviewer)
Warnings:
  - a custom card whose name now exists in the Argentum library (the library version wins in the app)
  - a cached Oracle record disagrees with a definition's cost / type line / stats / Oracle text
"""
from __future__ import annotations

import json
import re

from . import oracle, repo

BASELINE = repo.DATA_DIR / "check-baseline.json"


def _baseline() -> dict:
    if BASELINE.exists():
        return json.loads(BASELINE.read_text(encoding="utf-8"))
    return {"untested": [], "unregistered": []}


def _norm_text(s: str) -> str:
    s = (s or "").replace("’", "'").replace("—", "-").replace("−", "-")
    return re.sub(r"\s+", " ", s).strip().casefold()


def run(strict_oracle: bool = False, finished: frozenset[str] = frozenset()) -> tuple[list[str], list[str]]:
    errors: list[str] = []
    warnings: list[str] = []
    base = _baseline()
    sources = repo.card_sources()
    by_val = {c.val: c for c in sources}
    entries = repo.registry_entries()
    registered = {e.val for e in entries}
    tests = repo.tests_by_val()

    seen: dict[str, str] = {}
    for c in sources:
        key = repo.normalize_name(c.name)
        if key in seen:
            errors.append(f"duplicate custom card name '{c.name}' in {repo.rel(c.path)} and {seen[key]}")
        seen[key] = repo.rel(c.path)
    for e in entries:
        if e.val not in by_val:
            errors.append(f"CustomCards.kt registers {e.val}, but no card file defines `val {e.val} = card(...)`")

    for c in sources:
        where = repo.rel(c.path)
        if c.val in registered:
            if c.is_scaffold:
                errors.append(f"{c.name}: registered but still a scaffold ({repo.SCAFFOLD_MARK}/{repo.TODO_MARK} in {where})")
            for t in tests.get(c.val, []):
                if repo.SCAFFOLD_MARK in t.read_text(encoding="utf-8"):
                    errors.append(f"{c.name}: registered but its test {repo.rel(t)} is still the cardkit scaffold")
            if not tests.get(c.val) and c.val not in base["untested"]:
                errors.append(f"{c.name}: registered without a scenario test (expected {repo.rel(repo.TESTS_DIR)}/{c.val}ScenarioTest.kt)")
        elif repo.library_index().get(repo.normalize_name(c.name)) == "card":
            pass  # kept for reference; Argentum's library now ships this card, so it is intentionally unregistered
        elif not c.is_scaffold and c.val not in base["unregistered"]:
            errors.append(f"{c.name}: defined in {where} but not registered in CustomCards.kt "
                          f"(register it, or mark it with {repo.SCAFFOLD_MARK} while unfinished)")

        if where in finished:
            if c.is_scaffold:
                errors.append(f"{c.name}: this PR changes {where} but leaves it a scaffold ({repo.SCAFFOLD_MARK}/"
                              f"{repo.TODO_MARK}); finish the card (Cardkit Finishing steps)")
            elif c.val not in registered and repo.library_index().get(repo.normalize_name(c.name)) != "card":
                errors.append(f"{c.name}: this PR changes {where} but does not register it in CustomCards.kt "
                              f"(and regenerate the card exports: Cardkit Finishing steps)")

        if repo.library_index().get(repo.normalize_name(c.name)) == "card" and c.val in registered:
            warnings.append(f"{c.name}: Argentum's library now has this card; the app uses the library version")

        cached = oracle.cached(c.name)
        if cached and not c.is_scaffold:
            diffs = []
            p = c.props
            if "manaCost" in p and cached.get("mana_cost") and p["manaCost"] != cached["mana_cost"] and "faces" not in cached:
                diffs.append(f"manaCost {p['manaCost']!r} vs Oracle {cached['mana_cost']!r}")
            if "typeLine" in p and cached.get("type_line") and _norm_text(p["typeLine"]) != _norm_text(cached["type_line"]) \
                    and "faces" not in cached:
                diffs.append(f"typeLine {p['typeLine']!r} vs Oracle {cached['type_line']!r}")
            for f in ("power", "toughness"):
                if f in p and str(cached.get(f, p[f])) != str(p[f]) and re.fullmatch(r"-?\d+", str(cached.get(f, ""))):
                    diffs.append(f"{f} {p[f]} vs Oracle {cached[f]}")
            if "oracleText" in p and cached.get("oracle_text") and _norm_text(p["oracleText"]) != _norm_text(cached["oracle_text"]):
                diffs.append("oracleText differs from the current Oracle wording")
            for d in diffs:
                (errors if strict_oracle else warnings).append(f"{c.name}: {d}")
    return errors, warnings


def write_baseline() -> dict:
    """Records today's known exceptions so CI starts green; new cards must comply."""
    sources = repo.card_sources()
    registered = {e.val for e in repo.registry_entries()}
    tests = repo.tests_by_val()
    data = {
        "_about": "Pre-existing exceptions when cardkit check was introduced. Do not add to these lists; fix the card instead.",
        "untested": sorted(c.val for c in sources if c.val in registered and not tests.get(c.val)),
        "unregistered": sorted(c.val for c in sources if c.val not in registered and not c.is_scaffold
                               and repo.library_index().get(repo.normalize_name(c.name)) != "card"),
    }
    BASELINE.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")
    return data
