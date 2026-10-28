"""Find already-implemented cards whose rules text matches a line you need to implement.

Every Argentum library card (with its Oracle text, from the app's assets) and every Arcana custom card is
indexed line by line. A close match is evidence that the pinned engine already expresses that sentence,
and points to a real implementation to copy: a custom card's Kotlin + scenario test in this repo, or a
library card's source in the pinned upstream Argentum commit.
"""
from __future__ import annotations

import math
import re
from collections import Counter, defaultdict
from dataclasses import dataclass
from difflib import SequenceMatcher
from functools import lru_cache

from . import repo, text


@dataclass(frozen=True)
class RefLine:
    card: str
    source: str  # "custom" or "library"
    line: str
    norm: str
    toks: tuple[str, ...]


@dataclass
class Match:
    ref: RefLine
    score: float

    def to_json(self) -> dict:
        return {"card": self.ref.card, "source": self.ref.source, "score": round(self.score, 3),
                "line": self.ref.line, "where": where(self.ref.card, self.ref.source)}


def where(card: str, source: str) -> str:
    """Where to read the implementation of [card]."""
    if source == "custom":
        for c in repo.card_sources():
            if c.name == card:
                test = repo.own_test(c.val)
                return repo.rel(c.path) + (f" (test: {repo.rel(test)})" if test else "")
    path = repo.upstream_sources().get(repo.normalize_name(card))
    return repo.upstream_url(path) if path else f"Argentum library card '{card}' (pinned upstream)"


class Index:
    def __init__(self, refs: list[RefLine]):
        self.refs = refs
        self.by_token: dict[str, list[int]] = defaultdict(list)
        df: Counter = Counter()
        for i, r in enumerate(refs):
            for t in set(r.toks):
                self.by_token[t].append(i)
                df[t] += 1
        n = max(len(refs), 1)
        self.idf = {t: math.log(n / (1 + c)) + 1.0 for t, c in df.items()}
        self.exact: dict[str, list[int]] = defaultdict(list)
        for i, r in enumerate(refs):
            self.exact[r.norm].append(i)

    def search(self, line: str, card_name: str = "", k: int = 3, exclude: str | None = None) -> list[Match]:
        return self.search_norm(text.normalize_line(line, card_name), k, exclude)

    def search_norm(self, norm: str, k: int = 3, exclude: str | None = None) -> list[Match]:
        toks = text.tokens(norm)
        if not toks:
            return []
        results: dict[tuple[str, str], Match] = {}

        def consider(i: int, score: float) -> None:
            r = self.refs[i]
            if exclude and repo.normalize_name(r.card) == repo.normalize_name(exclude):
                return
            key = (r.card, r.source)
            if key not in results or results[key].score < score:
                results[key] = Match(r, score)

        for i in self.exact.get(norm, []):
            consider(i, 1.0)
        weights: Counter = Counter()
        for t in set(toks):
            postings = self.by_token.get(t, [])
            if len(postings) > 6000:  # stop-word: too common to rank candidates
                continue
            w = self.idf.get(t, 1.0)
            for i in postings:
                weights[i] += w
        for i, _ in weights.most_common(250):
            r = self.refs[i]
            score = SequenceMatcher(None, toks, list(r.toks), autojunk=False).ratio()
            consider(i, score)
        ranked = sorted(results.values(), key=lambda m: (-m.score, m.ref.source != "custom", m.ref.card))
        return ranked[:k]


COMBINATION_PENALTY = 0.04
TRIVIAL = {"n", "{n}", "{m}", "{x}", "x", "a", "an", "the", "~", "and", "of", "-", ":", "s", "'s",
           "white", "blue", "black", "red", "green", "that", "this", "it", "its", "one", "each"}


def proper_words(line: str) -> set[str]:
    """Lower-cased words written capitalized mid-sentence: subtypes and names (Astartes, Plains, Eldrazi)."""
    out = set()
    for sent in re.split(r"(?<=[.:\u2014\"])\s+", line):
        words = re.findall(r"[A-Za-z][\w'\-]*", sent)
        for w in words[1:]:
            if w[0].isupper():
                out.add(w.casefold())
    return out


def differences(query_norm: str, ref_norm: str, trivial: set[str]) -> dict:
    """Non-trivial words present in only one of two normalized lines (what changes the rules)."""
    q, r = text.tokens(query_norm), text.tokens(ref_norm)
    q_only, r_only = [], []
    for tag, i1, i2, j1, j2 in SequenceMatcher(None, q, r, autojunk=False).get_opcodes():
        if tag in ("replace", "delete"):
            q_only += q[i1:i2]
        if tag in ("replace", "insert"):
            r_only += r[j1:j2]
    ok = TRIVIAL | trivial
    q_only = [t for t in q_only if t not in ok]
    r_only = [t for t in r_only if t not in ok]
    return {"cardOnly": q_only, "referenceOnly": r_only} if (q_only or r_only) else {}


def score_line(line: str, card_name: str = "", k: int = 3, exclude: str | None = None) -> dict:
    """Whole-line matches plus clause-by-clause evidence for lines that combine implemented pieces.

    score = max(best whole-line similarity, weakest clause similarity - COMBINATION_PENALTY)."""
    norm = text.normalize_line(line, card_name)
    trivial = proper_words(line)
    whole = index().search_norm(norm, k, exclude)
    best_whole = whole[0].score if whole else 0.0
    parts = text.clauses(norm)
    clause_info = []
    if len(parts) > 1:
        for part in parts:
            ms = clause_index().search_norm(part, 1, exclude)
            clause_info.append({"clause": part, "score": round(ms[0].score, 3) if ms else 0.0,
                                "match": ms[0].to_json() if ms else None,
                                "differences": differences(part, ms[0].ref.norm, trivial) if ms else {}})
    combo = (min(c["score"] for c in clause_info) - COMBINATION_PENALTY) if clause_info else 0.0
    if combo > best_whole:
        diffs = [{"clause": c["clause"], "against": c["match"]["card"], **c["differences"]}
                 for c in clause_info if c["differences"]]
    else:
        diffs = ([{"against": whole[0].ref.card, **differences(norm, whole[0].ref.norm, trivial)}]
                 if whole and differences(norm, whole[0].ref.norm, trivial) else [])
    return {"score": round(max(best_whole, combo), 3), "wholeScore": round(best_whole, 3),
            "matches": [m.to_json() for m in whole], "clauses": clause_info, "differences": diffs}


@lru_cache(maxsize=1)
def clause_index() -> Index:
    refs_: list[RefLine] = []
    seen: set[tuple[str, str]] = set()
    for r in index().refs:
        for part in text.clauses(r.norm):
            if (r.card, part) not in seen:
                seen.add((r.card, part))
                refs_.append(RefLine(r.card, r.source, r.line, part, tuple(text.tokens(part))))
    return Index(refs_)


@lru_cache(maxsize=1)
def index() -> Index:
    refs: list[RefLine] = []
    seen: set[tuple[str, str]] = set()
    registered = {e.val for e in repo.registry_entries()}
    exported = repo.custom_index_names()
    for c in repo.card_sources():
        if c.is_scaffold or c.val not in registered or repo.normalize_name(c.name) not in exported:
            continue
        for line in text.split_lines(c.props.get("oracleText", "")):
            norm = text.normalize_line(line, c.name)
            if (c.name, norm) not in seen:
                seen.add((c.name, norm))
                refs.append(RefLine(c.name, "custom", line, norm, tuple(text.tokens(norm))))
    for card in repo.library_cards():
        name = card["name"]
        for line in text.split_lines(card["oracleText"]):
            norm = text.normalize_line(line, name)
            if (name, norm) not in seen:
                seen.add((name, norm))
                refs.append(RefLine(name, "library", line, norm, tuple(text.tokens(norm))))
    return Index(refs)
