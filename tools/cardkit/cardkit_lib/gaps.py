"""Deck coverage with readiness: which cards are missing, why, and who should take them."""
from __future__ import annotations

import json
from collections import defaultdict
from pathlib import Path

import re

from . import decklist, readiness, repo, text

MANIFEST = repo.ROOT / "investigation/argentum-six-precons/deck-manifest.json"
ORDER = ["SUPPORTED", "NEEDS_REVIEW", "ENGINE_BLOCKED", "IMPLEMENTED_AND_TESTED"]


def load_decks(manifest: Path | None = None, deck_files: list[Path] | None = None) -> list[dict]:
    decks = []
    if deck_files:
        for p in deck_files:
            decks.append(decklist.load(p))
    else:
        data = json.loads((manifest or MANIFEST).read_text(encoding="utf-8"))
        for d in data["decks"]:
            decks.append({"name": d["name"], "cards": d["cards"]})
    return decks


class ClaimsUnavailable(RuntimeError):
    """Open-PR claims could not be read; callers that dispatch work must fail closed."""


def open_pr_claims(repo_slug: str = "AARMOU/arcana_table") -> list[dict]:
    """Every open PR (number, title, branch, body) via the GitHub CLI, all pages. Raises ClaimsUnavailable."""
    import subprocess
    try:
        out = subprocess.run(["gh", "api", "--paginate", f"repos/{repo_slug}/pulls?state=open&per_page=100", "--jq",
                              ".[] | {number, title, branch: .head.ref, body: (.body // \"\")[0:4000]}"],
                             capture_output=True, text=True, timeout=120, check=True).stdout
    except subprocess.CalledProcessError as exc:
        # gh's default exception hides captured stderr, obscuring HTTP 403/429 and jq failures.
        # Report only allowlisted diagnostics; never echo raw stderr or credential-bearing text.
        stderr = exc.stderr or ""
        http = re.search(r"\bHTTP\s+([1-5][0-9]{2})\b", stderr, re.IGNORECASE)
        category = (
            "rate-limit" if re.search(r"rate.limit", stderr, re.IGNORECASE) else
            "integration-permission" if "resource not accessible by integration" in stderr.casefold() else
            "jq-filter" if re.search(r"\bjq\b", stderr, re.IGNORECASE) else
            "unclassified"
        )
        status = f"; HTTP {http.group(1)}" if http else ""
        raise ClaimsUnavailable(
            f"could not read open PRs via gh: exit {exc.returncode}{status}; category={category}; "
            "raw stderr withheld; claims unverified"
        ) from exc
    except subprocess.TimeoutExpired as exc:
        raise ClaimsUnavailable("could not read open PRs via gh: timed out; claims unverified") from exc
    except OSError as exc:
        raise ClaimsUnavailable(
            f"could not read open PRs via gh: execution unavailable ({type(exc).__name__}); claims unverified"
        ) from exc
    try:
        return [json.loads(l) for l in out.splitlines() if l.strip()]
    except json.JSONDecodeError as exc:
        raise ClaimsUnavailable(f"unexpected gh output: {exc}") from exc


def _claimed_by(name: str, prs: list[dict]) -> tuple[str | None, list[str]]:
    """(claim from an open PR's title or branch, other open PRs whose body mentions the card)."""
    n = name.split(" // ")[0].casefold()
    slug = repo.val_name_for(name).casefold()
    dashed = "-".join(re.findall(r"[a-z0-9]+", n))
    claim, mentions = None, []
    for pr in prs:
        branch = pr["branch"].casefold()
        if claim is None and (n in pr["title"].casefold() or (dashed and dashed in branch)
                              or (len(slug) > 6 and slug in branch.replace("-", ""))):
            claim = f"PR #{pr['number']} ({pr['branch']})"
        elif re.search(rf"(?<![\w']){re.escape(n)}(?![\w'])", pr["body"].casefold()):
            mentions.append(f"#{pr['number']}")
    return claim, mentions


def analyze(decks: list[dict], use_refs: bool = True, claims: list[dict] | None = None) -> dict:
    verdicts: dict[str, dict] = {}
    usage: dict[str, dict] = {}
    per_deck = []
    for deck in decks:
        counts = defaultdict(int)
        for card in deck["cards"]:
            key = repo.normalize_name(card["name"])
            if key not in verdicts:
                verdicts[key] = readiness.classify(card["name"], use_refs=use_refs)
            v = verdicts[key]
            counts[v["readiness"]] += card.get("quantity", 1)
            u = usage.setdefault(key, {"decks": [], "slots": 0})
            u["decks"].append(deck["name"])
            u["slots"] += card.get("quantity", 1)
        slots = sum(c.get("quantity", 1) for c in deck["cards"])
        per_deck.append({"name": deck["name"], "slots": slots,
                         **{r: counts.get(r, 0) for r in ORDER},
                         "playableSlots": counts.get("IMPLEMENTED_AND_TESTED", 0)})
    cards = []
    for key, v in verdicts.items():
        cards.append({**v, "decks": usage[key]["decks"], "slots": usage[key]["slots"]})
    cards.sort(key=lambda c: (ORDER.index(c["readiness"]), -len(c["decks"]), -c["slots"], c["name"].casefold()))
    missing = [c for c in cards if c["readiness"] != "IMPLEMENTED_AND_TESTED"]
    for c in missing:
        by, mentions = _claimed_by(c["name"], claims or [])
        if by:
            c["claimedBy"] = by
        if mentions:
            c["mentionedInOpenPRs"] = mentions
    groups: dict[str, list[str]] = defaultdict(list)
    for c in missing:
        groups[(c["mechanics"] or ["other"])[0]].append(c["name"])
    claims_verified = claims is not None
    free = [c for c in missing if not c.get("claimedBy")]
    dispatch = {
        "copilot": [c["name"] for c in free if c["route"] == "copilot"],
        "claude": [c["name"] for c in free if c["route"] == "claude"],
        "gpt": [c["name"] for c in free if c["route"] == "gpt"],
        "claimed": {c["name"]: c["claimedBy"] for c in missing if c.get("claimedBy")},
        "claimsVerified": claims_verified,
        "dispatchable": claims_verified,
    }
    return {
        "schema": "arcana-cardkit-gaps/1",
        "note": "Readiness is evidence for routing, not a gameplay certification; every card still needs real scenario "
                "tests. dispatch.dispatchable is false unless open-PR claims were read successfully "
                "(--claims-from-github); automated dispatch must refuse when it is false.",
        "decks": per_deck,
        "totals": {r: sum(1 for c in cards if c["readiness"] == r) for r in ORDER},
        "missingByMechanic": {k: sorted(v, key=str.casefold) for k, v in sorted(groups.items())},
        "dispatch": dispatch,
        "cards": cards,
    }


def render_md(result: dict, title: str = "Card readiness") -> str:
    t = result["totals"]
    lines = [f"# {title}", "", result["note"], "",
             f"**Distinct cards:** {sum(t.values())} — implemented {t['IMPLEMENTED_AND_TESTED']}, "
             f"supported (Copilot-ready) {t['SUPPORTED']}, needs review {t['NEEDS_REVIEW']}, "
             f"engine-blocked {t['ENGINE_BLOCKED']}.", "",
             "## Per deck (slots)", "", "| Deck | Playable | Supported | Needs review | Blocked |", "|---|---:|---:|---:|---:|"]
    for d in result["decks"]:
        lines.append(f"| {d['name']} | {d['playableSlots']}/{d['slots']} | {d['SUPPORTED']} | {d['NEEDS_REVIEW']} | {d['ENGINE_BLOCKED']} |")
    lines += ["", "## Missing cards by mechanic", ""]
    for mech, names in result["missingByMechanic"].items():
        lines.append(f"- **{mech}** ({len(names)}): " + ", ".join(names))
    lines += ["", "## Missing cards", "", "| Card | Readiness | Route | Decks | Why |", "|---|---|---|---:|---|"]
    for c in result["cards"]:
        if c["readiness"] == "IMPLEMENTED_AND_TESTED":
            continue
        why = "; ".join(c["reasons"]).replace("|", "\\|")
        route = f"claimed: {c['claimedBy']}" if c.get("claimedBy") else c["route"]
        if c.get("mentionedInOpenPRs"):
            why += f" (mentioned in open {', '.join(c['mentionedInOpenPRs'])})"
        lines.append(f"| {c['name']} | {c['readiness']} | {route} | {len(c['decks'])} | {why} |")
    return "\n".join(lines) + "\n"


def queue_unit(cards: list[dict], base: str = "gpt/arcana-integration-next", review: list[str] | None = None) -> str:
    """An `[Arcana Queue]` issue body (dispatcher format). Printed, never filed. Callers enforce eligibility."""
    out = []
    if review:
        out += [f"> **Triage required:** {', '.join(review)} classified NEEDS_REVIEW; included by explicit --allow-review. "
                "Not an authorization.", ""]
    out += ["## Cards", ""]
    out += [f"- {c['name']}" for c in cards]
    out += ["", "## Acceptance", ""]
    for c in cards:
        rules = [l for l in text.split_lines((c.get("oracle") or {}).get("oracle_text", ""))]
        detail = "; ".join(rules) if rules else "exact Oracle behavior"
        refs_ = sorted({m["card"] for l in c.get("lines", []) for m in l.get("matches", [])[:1]})
        ref = f" (reference implementations: {', '.join(refs_)})" if refs_ else ""
        out.append(f"- {c['name']}: {detail}{ref}")
    out += [
        "- Exact current Oracle behavior (cost, types, every ability, targeting and timing), checked against authoritative card records",
        "- Executable regression scenarios for positive and negative behavior, registration and deterministic asset export",
        "- No placeholders, approximations or weakened tests; report exact commands and run IDs",
        "- Start from `tools/cardkit/cardkit newcard \"<name>\"`; `tools/cardkit/cardkit check` must pass",
        "", f"Base: {base}", "",
    ]
    return "\n".join(out)
