"""Authoritative card records from Scryfall, with a committed offline cache.

Agent sandboxes (Copilot, some GPT runners) often cannot reach api.scryfall.com, so every lookup reads
tools/cardkit/data/oracle-cache.jsonl first. `cardkit oracle fetch` (or the cardkit CI job, which has
open network) refreshes it. Nothing here ever guesses card text: an uncached card is reported missing.
"""
from __future__ import annotations

import json
import time
import urllib.error
import urllib.request
from functools import lru_cache

from . import repo

CACHE = repo.DATA_DIR / "oracle-cache.jsonl"
API = "https://api.scryfall.com"
HEADERS = {"User-Agent": "ArcanaTableCardkit/1.0 (+https://github.com/AARMOU/arcana_table)",
           "Accept": "application/json"}

_FIELDS = ("name", "mana_cost", "type_line", "oracle_text", "power", "toughness", "loyalty", "defense",
           "colors", "color_identity", "keywords", "rarity", "set", "collector_number", "artist", "layout",
           "scryfall_uri")


def compact(card: dict) -> dict:
    """The fields cardkit uses, from a Scryfall card object. Multi-face cards keep their faces."""
    out = {k: card[k] for k in _FIELDS if k in card and card[k] not in (None, "")}
    image = (card.get("image_uris") or {}).get("normal")
    faces = card.get("card_faces") or []
    if faces:
        out["faces"] = [{k: f[k] for k in ("name", "mana_cost", "type_line", "oracle_text", "power",
                                           "toughness", "loyalty", "defense") if f.get(k) not in (None, "")}
                        for f in faces]
        if "oracle_text" not in out:
            out["oracle_text"] = "\n".join(f.get("oracle_text", "") for f in faces if f.get("oracle_text"))
        image = image or (faces[0].get("image_uris") or {}).get("normal")
    if image:
        out["image_uri"] = image
    return out


@lru_cache(maxsize=1)
def _cache() -> dict[str, dict]:
    out = {}
    if CACHE.exists():
        for line in CACHE.read_text(encoding="utf-8").splitlines():
            if line.strip():
                d = json.loads(line)
                out[repo.normalize_name(d["name"])] = d
                if " // " in d["name"]:
                    out.setdefault(repo.normalize_name(d["name"].split(" // ")[0]), d)
    return out


def cached(name: str) -> dict | None:
    return _cache().get(repo.normalize_name(name))


def save(cards: list[dict]) -> int:
    """Merges [cards] into the cache file (sorted, deterministic). Returns the number added or changed."""
    current = {repo.normalize_name(d["name"]): d for d in _cache().values()}
    changed = 0
    for c in cards:
        key = repo.normalize_name(c["name"])
        if current.get(key) != c:
            current[key] = c
            changed += 1
    unique = {d["name"]: d for d in current.values()}
    lines = [json.dumps(unique[n], ensure_ascii=False, sort_keys=True) for n in sorted(unique, key=str.casefold)]
    CACHE.parent.mkdir(parents=True, exist_ok=True)
    CACHE.write_text("\n".join(lines) + ("\n" if lines else ""), encoding="utf-8")
    _cache.cache_clear()
    return changed


def _request(url: str, payload: dict | None = None) -> dict:
    data = json.dumps(payload).encode() if payload is not None else None
    headers = dict(HEADERS)
    if data is not None:
        headers["Content-Type"] = "application/json"
    req = urllib.request.Request(url, data=data, headers=headers, method="POST" if data else "GET")
    with urllib.request.urlopen(req, timeout=30) as resp:
        return json.loads(resp.read().decode("utf-8"))


def fetch_many(names: list[str]) -> tuple[list[dict], list[str]]:
    """Looks up [names] on Scryfall (75 per request). Returns (cards, names Scryfall did not find)."""
    found: list[dict] = []
    missing: list[str] = []
    wanted = sorted({n for n in names if n.strip()}, key=str.casefold)
    for i in range(0, len(wanted), 75):
        batch = wanted[i:i + 75]
        body = _request(f"{API}/cards/collection", {"identifiers": [{"name": n.split(" // ")[0]} for n in batch]})
        found.extend(compact(c) for c in body.get("data", []))
        missing.extend(nf.get("name", "?") for nf in body.get("not_found", []))
        time.sleep(0.12)
    return found, missing


def lookup(name: str, online: bool = True) -> dict | None:
    """Cache first; then Scryfall if [online]. Raises OSError when online lookup is impossible."""
    hit = cached(name)
    if hit or not online:
        return hit
    try:
        cards, _ = fetch_many([name])
    except (urllib.error.URLError, TimeoutError) as exc:
        raise OSError(f"Scryfall unreachable ({exc}); run `tools/cardkit/cardkit oracle fetch` where the "
                      f"network allows it, or use the cardkit CI job") from exc
    if cards:
        save(cards)
        return cards[0]
    return None
