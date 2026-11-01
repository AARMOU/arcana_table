#!/usr/bin/env python3
"""Re-apply one Copilot review PR's single-card Cardkit decision onto the current integration base.

Review PRs all edit the same three data files (verified-support.json, known-blockers.json, oracle-cache.jsonl), so
every merged review conflicts every other open one, and a conflicted PR never gets an Arcana Cardkit run (GitHub
does not run pull_request workflows without a merge ref). An engine upgrade also leaves an older review bound to the
previous runtime. Instead of paying a Copilot session to rebase, this takes ONLY the one card entry the PR adds,
carries it onto the adopted runtime with Cardkit's own upgrade check (readiness.restamp_review: Oracle unchanged and
no named SDK member removed), and writes the trusted Oracle record from the card-work item. Anything else in the PR,
or a review the upgrade invalidated, is refused: the PR is left for a person or a fresh review.

The caller commits the result as a merge of the PR head and the base head, so the PR diff is exactly this entry and
Arcana Review Auto-merge validates it again after a green Cardkit run. Stdlib only.

Usage: review_refresh.py --base-dir DIR --card NAME --oracle FILE --pr-files DIR [--previous-sdk FILE]
  --pr-files holds <file>.base and <file>.head for verified-support.json and known-blockers.json (missing = absent).
Prints one JSON object: {"ok": true, "changed": [...], "restamp": ...} or {"ok": false, "reason": ...}. Exit 0 / 3.
"""
from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

DATA = "tools/cardkit/data/"
VS, KB = "verified-support.json", "known-blockers.json"


def norm(s) -> str:
    return re.sub(r"[^a-z0-9]+", " ", str(s or "").lower()).strip()


def dump(data) -> str:
    return json.dumps(data, indent=2, ensure_ascii=False) + "\n"


def changed_keys(before: dict | None, after: dict | None) -> list[str]:
    before, after = before or {}, after or {}
    return [k for k in dict.fromkeys([*before, *after]) if before.get(k) != after.get(k)]


def card_entry(base: dict | None, head: dict | None, card: str):
    """The PR's change to one data file: (None, None) = untouched, (key, entry) = exactly this card, else a reason."""
    if head is None or base == head:
        return None, None
    if base is None:
        return "file-added", None
    others = changed_keys({k: v for k, v in base.items() if k != "cards"}, {k: v for k, v in head.items() if k != "cards"})
    if others:
        return "changes-outside-cards: " + ",".join(others), None
    keys = changed_keys(base.get("cards"), head.get("cards"))
    if len(keys) != 1 or norm(keys[0]) != norm(card) or keys[0] not in (head.get("cards") or {}):
        return "not-exactly-this-card: " + ",".join(keys[:5]), None
    return keys[0], head["cards"][keys[0]]


def extract(pr: dict, card: str) -> dict:
    """pr: {file: {"base": dict|None, "head": dict|None}}. Pure. Returns the decisions or a refusal."""
    out = {}
    for f in (VS, KB):
        key, entry = card_entry(pr.get(f, {}).get("base"), pr.get(f, {}).get("head"), card)
        if key is None:
            continue
        if entry is None:
            return {"ok": False, "reason": f + ": " + key}
        out[f] = (key, entry)
    if not out:
        return {"ok": False, "reason": "no-review-decision"}
    if VS in out and not out[VS][1].get("reviewedLines"):
        return {"ok": False, "reason": "verified-support entry has no reviewedLines"}
    if KB in out and not out[KB][1].get("reason"):
        return {"ok": False, "reason": "blocker entry has no reason"}
    return {"ok": True, "decisions": out}


def apply(base_dir: Path, decisions: dict, restamp) -> dict:
    """Writes the decisions into the base checkout. `restamp(entry) -> str` carries a review to the adopted runtime."""
    changed, note = [], None
    for f, (key, entry) in decisions.items():
        path = base_dir / DATA / f
        text = path.read_text(encoding="utf-8")
        data = json.loads(text)
        if dump(data) != text:
            return {"ok": False, "reason": f + " on the base is not in Cardkit's canonical format"}
        entry = json.loads(json.dumps(entry))
        if f == VS:
            note = restamp(entry)
            if note not in ("current", "restamped"):
                return {"ok": False, "reason": "review no longer valid on the adopted engine: " + note}
        cards = data.setdefault("cards", {})
        stale = [k for k in cards if norm(k) == norm(key) and k != key]
        for k in stale:
            del cards[k]
        if cards.get(key) == entry and not stale:
            continue
        cards[key] = entry
        path.write_text(dump(data), encoding="utf-8")
        changed.append(DATA + f)
    return {"ok": True, "changed": changed, "restamp": note}


def main(argv=None) -> int:
    p = argparse.ArgumentParser()
    p.add_argument("--base-dir", required=True, type=Path)
    p.add_argument("--card", required=True)
    p.add_argument("--oracle", required=True, type=Path, help="the trusted Oracle record from the card-work item (JSON)")
    p.add_argument("--pr-files", required=True, type=Path)
    p.add_argument("--previous-sdk", type=Path, help="docs/agents/generated/sdk-api.json at the review's baseSha")
    a = p.parse_args(argv)

    def load(name):
        f = a.pr_files / name
        return json.loads(f.read_text(encoding="utf-8")) if f.exists() else None

    def fail(reason):
        print(json.dumps({"ok": False, "reason": reason}))
        return 3

    record = json.loads(a.oracle.read_text(encoding="utf-8"))
    if norm(record.get("name")) != norm(a.card) or not record.get("oracle_text") and not record.get("card_faces"):
        return fail("oracle record does not match the card")
    got = extract({f: {"base": load(f + ".base"), "head": load(f + ".head")} for f in (VS, KB)}, a.card)
    if not got["ok"]:
        return fail(got["reason"])

    sys.path.insert(0, str((a.base_dir / "tools/cardkit").resolve()))
    from cardkit_lib import oracle, readiness, sdk  # noqa: E402  (the base's own Cardkit, never the PR's)

    oracle.save([record])
    card = oracle.cached(record["name"])
    runtime = readiness.review_runtime_hash()

    def restamp(entry):
        if readiness.review_is_current(entry, card):
            return "current"
        if not a.previous_sdk or not a.previous_sdk.exists():
            return "previous-sdk-index-unavailable"
        previous = json.loads(a.previous_sdk.read_text(encoding="utf-8"))
        return readiness.restamp_review(entry, card, sdk.vocabulary(previous), sdk.vocabulary(), runtime)

    result = apply(a.base_dir, got["decisions"], restamp)
    if not result["ok"]:
        return fail(result["reason"])
    print(json.dumps(result))
    return 0


if __name__ == "__main__":
    sys.exit(main())
