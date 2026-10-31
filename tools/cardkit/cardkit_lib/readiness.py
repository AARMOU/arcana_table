"""Machine-readable readiness for any card name.

SUPPORTED               every rules line has a near-identical line on an implemented card (custom or
                        Argentum library) and no keyword is unknown or blocked: existing primitives should
                        cover it. A Copilot/GPT candidate. Still needs an implementation and real tests.
ENGINE_BLOCKED          a recorded engine gap (tools/cardkit/data/known-blockers.json) applies. Claude.
NEEDS_REVIEW            anything uncertain: no cached Oracle text, an unmatched line, an unknown keyword,
                        a multi-face layout, an unregistered/untested or scaffolded definition.
IMPLEMENTED_AND_TESTED  an Argentum library card, or a registered custom card with a scenario test and no
                        scaffold markers.

The classifier never marks a card SUPPORTED on name or artwork alone, and never ENGINE_BLOCKED by guess:
blocked requires a recorded source; everything else that is unsure is NEEDS_REVIEW.
"""
from __future__ import annotations

import json
import re
from functools import lru_cache

from . import oracle, refs, repo, sdk, text

SUPPORTED_SCORE = 0.88
GAP_SCORE = 0.6
MULTI_FACE = {"transform", "modal_dfc", "meld", "flip", "split", "adventure", "double_faced_token", "reversible_card"}

MECHANICS = [
    ("engine-blocked", None),
    ("multi-face", None),
    ("counterspell", r"\bcounter target\b"),
    ("removal", r"\b(destroy|exile) (target|each|all|up to)\b"),
    ("mana ability", r"\{t\}[^:]*: add\b|\badd \{"),
    ("token creation", r"\bcreate\b.*\btoken"),
    ("counters", r"\+1/\+1 counter|-1/-1 counter|\bcounters? on\b"),
    ("enters trigger", r"\bwhen(ever)? [^.]*\benters\b"),
    ("attack/combat trigger", r"\bwhenever [^.]*\b(attacks|blocks|deals combat damage)\b"),
    ("dies/leaves trigger", r"\bwhen(ever)? [^.]*\b(dies|leaves the battlefield|is put into a graveyard)\b"),
    ("step trigger", r"\bat the beginning of\b"),
    ("cast trigger", r"\bwhenever [^.]*\bcasts?\b"),
    ("activated ability", r"^[^\"]*\}[^.\"]*:|^\{"),
    ("static anthem/grant", r"\b(get|gets|have|has) [+\-]n/[+\-]n|\bhave (flying|haste|trample|vigilance)"),
    ("cost modification", r"\bcosts? \{n\} less\b|\bcost \{n\} more\b"),
    ("library search", r"\bsearch your library\b"),
    ("graveyard recursion", r"\breturn [^.]*from (your|a) graveyard\b"),
    ("card draw", r"\bdraws? (a|n) cards?\b"),
    ("equipment/aura", r"\bequipped creature\b|\benchanted creature\b|\bequip\b"),
    ("modal", r"\bchoose (one|two|n)\b"),
    ("x spell", r"\{x\}"),
    ("sacrifice", r"\bsacrifice\b"),
    ("keyword-only", None),
]


@lru_cache(maxsize=1)
def blockers() -> dict:
    return json.loads((repo.DATA_DIR / "known-blockers.json").read_text(encoding="utf-8"))


def _blocked_card(name: str) -> dict | None:
    cards = {repo.normalize_name(k): v for k, v in blockers().get("cards", {}).items()}
    return cards.get(repo.normalize_name(name))


def _blocked_pattern(oracle_text: str) -> dict | None:
    """A recorded engine gap whose rules wording appears on this card (keeps equivalent cards consistent)."""
    norm = "\n".join(text.normalize_line(l) for l in text.split_lines(oracle_text))
    for entry in blockers().get("patterns", []):
        if re.search(entry["regex"], norm, re.I | re.M):
            return entry
    return None


@lru_cache(maxsize=1)
def verified_support() -> dict:
    path = repo.DATA_DIR / "verified-support.json"
    data = json.loads(path.read_text(encoding="utf-8")) if path.exists() else {}
    return {repo.normalize_name(k): v for k, v in data.get("cards", {}).items()}


def _reviewed_lines(entry: dict | None) -> set:
    """Normalized Oracle lines an engineer reviewed (verified-support.json `reviewedLines[].line`)."""
    if not entry:
        return set()
    return {text.normalize_line(r["line"]) for r in entry.get("reviewedLines", [])
            if isinstance(r, dict) and r.get("line") and r.get("primitives")}


def _blocked_keyword(keyword: str) -> dict | None:
    kws = {k.casefold(): v for k, v in blockers().get("keywords", {}).items()}
    return kws.get(keyword.casefold())


@lru_cache(maxsize=1)
def _implemented_line_heads() -> tuple[str, ...]:
    return tuple(r.norm for r in refs.index().refs)


@lru_cache(maxsize=512)
def keyword_known(keyword: str) -> bool:
    """In the SDK vocabulary (enum, helper, facade member), or printed as a keyword on an implemented card."""
    key = re.sub(r"[^a-z0-9]", "", keyword.casefold())
    vocab = sdk.vocabulary()
    if key in vocab or key + "ability" in vocab:
        return True
    k = keyword.casefold()
    return any(h.startswith(k) and (len(h) == len(k) or h[len(k)] in " {,:-n") for h in _implemented_line_heads())


def real_keywords(card: dict) -> list[str]:
    """Scryfall keywords minus ability words / flavor-named abilities ("Name — …")."""
    words = text.ability_words(card.get("oracle_text", ""))
    return [k for k in card.get("keywords", []) if k.casefold() not in words]


def mechanics(oracle_text: str, keywords: list[str]) -> list[str]:
    tags = []
    lines = [text.normalize_line(l) for l in text.split_lines(oracle_text)]
    joined = "\n".join(lines)
    for tag, pattern in MECHANICS:
        if pattern and re.search(pattern, joined, re.M):
            tags.append(tag)
    for k in keywords:
        tags.append(f"keyword: {k}")
    if not tags:
        tags.append("keyword-only" if keywords else "other")
    return tags


@lru_cache(maxsize=1)
def _custom_by_name() -> dict[str, repo.CardSource]:
    return {repo.normalize_name(c.name): c for c in repo.card_sources()}


def implementation_state(name: str) -> tuple[str | None, list[str], str | None]:
    """(readiness or None if not implemented, reasons, source) from repo state alone."""
    key = repo.normalize_name(name)
    custom = _custom_by_name().get(key)
    in_library = repo.library_index().get(key) in ("card", "basic")
    if custom and in_library and custom.val not in {e.val for e in repo.registry_entries()}:
        custom = None  # superseded: the app uses Argentum's library version
    if custom:
        registered = custom.val in {e.val for e in repo.registry_entries()}
        tests = repo.tests_by_val().get(custom.val, [])
        if custom.is_scaffold:
            return "NEEDS_REVIEW", [f"cardkit scaffold in progress: {repo.rel(custom.path)}"], "custom"
        if not registered:
            return "NEEDS_REVIEW", [f"definition exists but is not registered in CustomCards.kt: {repo.rel(custom.path)}"], "custom"
        if not tests:
            return "NEEDS_REVIEW", ["registered custom card without a scenario test"], "custom"
        own = repo.own_test(custom.val)
        level = "custom-card-scenario-test" if own else "custom-shared-test"
        return "IMPLEMENTED_AND_TESTED", [f"registered custom card {repo.rel(custom.path)}; "
                                          + (f"card-specific scenario test {repo.rel(own)}" if own else
                                             f"exercised by {len(tests)} shared test file(s), no card-specific scenario file")], "custom:" + level
    if repo.library_index().get(key) in ("card", "basic"):
        return "IMPLEMENTED_AND_TESTED", ["indexed in Argentum's library (implemented upstream; no Arcana card-specific "
                                          "scenario is implied)"], "argentum-indexed"
    return None, [], None


def classify(name: str, use_refs: bool = True) -> dict:
    result: dict = {"name": name, "readiness": None, "source": None, "reasons": [], "mechanics": [],
                    "lines": [], "keywords": [], "route": None}
    state, reasons, source = implementation_state(name)
    result["source"] = source
    if state == "IMPLEMENTED_AND_TESTED":
        result.update(readiness=state, reasons=reasons, route=None)
        return result
    result["reasons"] += reasons
    card = oracle.cached(name)
    blocked = _blocked_card(name)
    if blocked:
        result.update(readiness="ENGINE_BLOCKED", route="claude", mechanics=["engine-blocked"])
        result["reasons"].append(f"recorded engine gap: {blocked['reason']} ({blocked['source']}"
                                 f"{'' if blocked.get('verified', True) else ', not yet independently reproduced'})")
        if card:
            result["mechanics"] += mechanics(card.get("oracle_text", ""), card.get("keywords", []))
        return result
    pattern = _blocked_pattern(card.get("oracle_text", "")) if card else None
    if pattern:
        result.update(readiness="ENGINE_BLOCKED", route="claude", mechanics=["engine-blocked"])
        result["reasons"].append(f"rules text matches recorded engine gap '{pattern['name']}': {pattern['reason']} "
                                 f"({pattern['source']})")
        return result
    if not card:
        result.update(readiness="NEEDS_REVIEW", route="gpt", oracleMissing=True)
        result["reasons"].append("no cached Oracle record: run `tools/cardkit/cardkit oracle fetch` (needs Scryfall access)")
        return result
    otext = card.get("oracle_text", "")
    kws = real_keywords(card)
    result["keywords"] = kws
    result["mechanics"] = mechanics(otext, kws)
    result["oracle"] = {k: card.get(k) for k in ("mana_cost", "type_line", "oracle_text", "power", "toughness",
                                                   "loyalty", "layout") if card.get(k) is not None}
    gap = False
    for k in kws:
        b = _blocked_keyword(k)
        if b:
            result.update(readiness="ENGINE_BLOCKED", route="claude")
            result["mechanics"].insert(0, "engine-blocked")
            result["reasons"].append(f"keyword {k}: {b['reason']} ({b['source']})")
            return result
    unknown = [k for k in kws if not keyword_known(k)]
    if unknown:
        result["reasons"].append("keyword(s) not found in the SDK vocabulary: " + ", ".join(unknown))
    if card.get("layout") in MULTI_FACE:
        result["reasons"].append(f"multi-face layout ({card['layout']}): needs face/back-face DSL review")
        result["mechanics"].insert(0, "multi-face")
    worst = 1.0
    if use_refs:
        for line in text.split_lines(otext):
            if text.is_keyword_line(line, kws) and not any(not keyword_known(k) for k in kws if k.casefold() in line.casefold()):
                result["lines"].append({"text": line, "kind": "keywords", "score": 1.0, "matches": []})
                continue
            scored = refs.score_line(line, name, k=3, exclude=name)
            best = scored["score"]
            worst = min(worst, best)
            result["lines"].append({"text": line, "kind": "rules", **scored})
            if best < GAP_SCORE:
                gap = True
    variants = [l for l in result["lines"] if l["kind"] == "rules" and l.get("differences")]
    verified = verified_support().get(repo.normalize_name(name))
    if not unknown and card.get("layout") not in MULTI_FACE and worst >= SUPPORTED_SCORE and not result["reasons"]:
        if variants and not verified:
            result.update(readiness="NEEDS_REVIEW", route="gpt", reviewHint="near-variant")
            for l in variants:
                for d in l["differences"]:
                    result["reasons"].append(
                        f"wording differs from {d['against']}: card has {d.get('cardOnly') or '-'}, reference has "
                        f"{d.get('referenceOnly') or '-'} — confirm the engine supports that difference, then record it "
                        f"in tools/cardkit/data/verified-support.json")
            return result
        result.update(readiness="SUPPORTED", route="copilot")
        result["reasons"].append(f"every rules line matches an implemented card at >= {SUPPORTED_SCORE}"
                                 + (" with no rules-relevant wording difference" if not variants else ""))
        if verified:
            result["verifiedSupport"] = verified
            result["reasons"].append(f"wording differences verified against engine source: {verified['summary']}")
        return result
    weak = [l["text"] for l in result["lines"] if l["kind"] == "rules" and l["score"] < SUPPORTED_SCORE]
    reviewed = _reviewed_lines(verified)
    if (use_refs and weak and reviewed and not unknown and card.get("layout") not in MULTI_FACE and not result["reasons"]
            and all(text.normalize_line(l) in reviewed for l in weak)):
        # An engineer checked each weak line against engine source and named the primitive. The entry is bound to
        # the exact Oracle wording: if Scryfall's text changes, the line no longer matches and review is needed again.
        result.update(readiness="SUPPORTED", route="copilot", verifiedSupport=verified, reviewed=True)
        result["reasons"].append(f"{len(weak)} line(s) below {SUPPORTED_SCORE} reviewed against engine source: "
                                 + verified["summary"])
        return result
    if worst < SUPPORTED_SCORE:
        result["reasons"].append(f"{len(weak)} line(s) without a close implemented match (best {worst:.2f})")
    result["readiness"] = "NEEDS_REVIEW"
    result["route"] = "claude" if (gap or unknown) else "gpt"
    if gap or unknown:
        result["reviewHint"] = "possible-engine-gap"
    return result
