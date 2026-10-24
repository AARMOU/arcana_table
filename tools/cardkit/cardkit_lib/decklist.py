"""Reads deck lists: ManaBox text/CSV exports, Moxfield/Arena text, plain name-per-line lists."""
from __future__ import annotations

import csv
import io
import re
from pathlib import Path

_LINE = re.compile(r"^\s*(?:(\d+)\s*x?\s+)?(.+?)\s*(?:\(([A-Za-z0-9]{2,6})\)\s*([\w\-★]+)?)?\s*(?:\*[A-Z]+\*\s*)*$")
_SECTION = re.compile(r"^\s*(?://\s*)?(commanders?|deck|main ?board|sideboard|maybeboard|companion|tokens?)\s*:?\s*(?:\(\d+\))?\s*$", re.I)


def parse(text: str, name: str = "deck") -> dict:
    """Returns {"name", "cards": [{"name", "quantity", "commander", "set"?}]} merged by card name."""
    stripped = text.lstrip("﻿")
    first = stripped.splitlines()[0] if stripped.strip() else ""
    if "," in first and re.search(r"\bname\b", first, re.I) and re.search(r"\bquantity\b", first, re.I):
        return _parse_csv(stripped, name)
    cards: dict[str, dict] = {}
    section = "deck"
    for raw in stripped.splitlines():
        line = raw.strip()
        if not line:
            continue
        sec = _SECTION.match(line)
        if sec:
            section = sec.group(1).lower().replace(" ", "")
            continue
        if line.startswith(("#", "//")):
            continue
        if section in ("sideboard", "maybeboard", "tokens", "token"):
            continue
        m = _LINE.match(line)
        if not m:
            continue
        qty = int(m.group(1) or 1)
        card = m.group(2).strip().replace("  ", " ")
        card = re.sub(r"\s+//\s+", " // ", card)
        entry = cards.setdefault(card.casefold(), {"name": card, "quantity": 0, "commander": False})
        entry["quantity"] += qty
        entry["commander"] = entry["commander"] or section.startswith("commander")
        if m.group(3):
            entry.setdefault("set", m.group(3).upper())
    return {"name": name, "cards": list(cards.values())}


def _parse_csv(text: str, name: str) -> dict:
    cards: dict[str, dict] = {}
    for row in csv.DictReader(io.StringIO(text)):
        keys = {k.strip().casefold(): v for k, v in row.items() if k}
        card = (keys.get("name") or "").strip()
        if not card:
            continue
        qty = int((keys.get("quantity") or "1").strip() or 1)
        entry = cards.setdefault(card.casefold(), {"name": card, "quantity": 0, "commander": False})
        entry["quantity"] += qty
        board = (keys.get("board") or keys.get("category") or "").casefold()
        entry["commander"] = entry["commander"] or "commander" in board
        if keys.get("set code"):
            entry.setdefault("set", keys["set code"].upper())
    return {"name": name, "cards": list(cards.values())}


def load(path: Path) -> dict:
    return parse(path.read_text(encoding="utf-8"), path.stem)
