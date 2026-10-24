"""Find already-implemented cards whose rules text matches a line you need to implement.

Every Argentum library card (with its Oracle text, from the app's assets) and every Arcana custom card is
indexed line by line. A close match is evidence that the pinned engine already expresses that sentence,
and points to a real implementation to copy: a custom card's Kotlin + scenario test in this repo, or a
library card's source in the pinned upstream Argentum commit.
"""
from __future__ import annotations

import math
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
        norm = text.normalize_line(line, card_name)
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


@lru_cache(maxsize=1)
def index() -> Index:
    refs: list[RefLine] = []
    seen: set[tuple[str, str]] = set()
    for c in repo.card_sources():
        if c.is_scaffold:
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
