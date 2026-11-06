"""Advisory cross-check against upstream Argentum Assay's baked whole-card ledger.

Argentum Assay's 'bake' output is a JSON object with corpus, whole, cards[].
The upstream compiler answers whether *its own* Oracle grammar reads a whole card.
It does not prove that Arcana's pinned SDK, runtime, or UI implements that card.

This module never changes readiness, engine blockers, claims, or dispatch eligibility.
"""
from __future__ import annotations

import json
import re
from pathlib import Path

from . import readiness, repo

_SHA = re.compile(r"^[0-9a-fA-F]{40}$")


class InvalidLedger(ValueError):
    """Invalid or inconsistent Assay bake artifact."""


def load_ledger(path: Path) -> dict[str, dict]:
    """Read Argentum VerdictLedger's exact bake schema; fail closed on malformed data."""
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, UnicodeError, json.JSONDecodeError) as exc:
        raise InvalidLedger(f"cannot read Assay ledger: {exc}") from exc
    if not isinstance(data, dict) or not isinstance(data.get("cards"), list):
        raise InvalidLedger("expected Assay bake JSON with a cards array")
    rows = data["cards"]
    if type(data.get("corpus")) is not int or data["corpus"] != len(rows):
        raise InvalidLedger("Assay corpus count does not match cards array")
    if type(data.get("whole")) is not int or not 0 <= data["whole"] <= len(rows):
        raise InvalidLedger("invalid Assay whole count")
    out: dict[str, dict] = {}
    whole = 0
    for i, row in enumerate(rows):
        if not isinstance(row, dict) or not isinstance(row.get("name"), str) or not row["name"].strip():
            raise InvalidLedger(f"Assay row {i}: missing card name")
        name = row["name"]
        key = repo.normalize_name(name)
        if key in out:
            raise InvalidLedger(f"duplicate Assay card: {name}")
        kind = row.get("kind")
        if kind is not None and (not isinstance(kind, str) or not kind.strip()):
            raise InvalidLedger(f"Assay row {i}: invalid decline kind")
        line = row.get("line")
        if line is not None and not isinstance(line, str):
            raise InvalidLedger(f"Assay row {i}: invalid decline line")
        if kind is None:
            if line is not None:
                raise InvalidLedger(f"Assay row {i}: success must not contain a decline line")
            whole += 1
        out[key] = {"name": name, "kind": kind, "line": line}
    if whole != data["whole"]:
        raise InvalidLedger("Assay whole count does not match successful rows")
    return out


def review(ledger: dict[str, dict], names: list[str], assay_commit: str) -> dict:
    """Compare without modifying existing Cardkit verdicts or granting engine support."""
    if not _SHA.fullmatch(assay_commit):
        raise ValueError("--assay-commit must be a full 40-character Git SHA")
    commit = assay_commit.lower()
    seen: set[str] = set()
    cards = []
    totals = {"whole": 0, "declined": 0, "absent": 0, "reviewCandidates": 0}
    for name in names:
        key = repo.normalize_name(name)
        if key in seen:
            continue
        seen.add(key)
        existing = readiness.classify(name)
        row = ledger.get(key)
        state = "absent" if row is None else "whole" if row["kind"] is None else "declined"
        totals[state] += 1
        candidate = state == "whole" and existing["readiness"] == "NEEDS_REVIEW"
        if candidate:
            totals["reviewCandidates"] += 1
        cards.append({
            "name": name,
            "cardkit": existing["readiness"],
            "assay": state,
            "declineKind": row["kind"] if row else None,
            "declineLine": row["line"] if row else None,
            "upstreamReviewCandidate": candidate,
            "pinnedEngineVerified": False,
        })
    return {
        "source": "Argentum Assay VerdictLedger (caller-supplied)",
        "assayCommitDeclared": commit,
        "arcanaPinnedEngineCommit": repo.UPSTREAM_COMMIT,
        "sourceCommitMatchesPin": commit == repo.UPSTREAM_COMMIT,
        "ledgerCommitAuthenticated": False,
        "dispatchEligibilityChanged": False,
        "warning": ("Assay's whole-card verdict is evidence about its own grammar, not evidence of "
                    "compilation, playability, or timing on Arcana's pinned SDK. A parser decline "
                    "does not prove an engine blocker. The source commit is caller-declared, "
                    "not embedded in or authenticated by the bake artifact. Keep Cardkit "
                    "classification and worker admission unchanged; verify with the pinned SDK "
                    "and positive/negative executable engine scenarios."),
        "totals": totals,
        "cards": cards,
    }


def names_from_decks(decks: list[dict], include_implemented: bool = False) -> list[str]:
    """Choose missing cards from the requested decks; never treat Assay as coverage."""
    names = []
    seen: set[str] = set()
    for deck in decks:
        for item in deck["cards"]:
            name = item["name"]
            key = repo.normalize_name(name)
            if key in seen:
                continue
            seen.add(key)
            if include_implemented or readiness.implementation_state(name)[0] != "IMPLEMENTED_AND_TESTED":
                names.append(name)
    return names
