#!/usr/bin/env python3
"""Fail a candidate build when its ORIGINAL Eldrazi Incursion card inventory is below target.

Presence is NOT gameplay acceptance. Run this after card export, before packaging.
"""
import argparse
import json
from pathlib import Path


def audit(root: Path, minimum: int) -> tuple[int, list[str]]:
    manifest = json.loads((root / "investigation/argentum-six-precons/deck-manifest.json").read_text())
    deck, = (d for d in manifest["decks"] if d["name"] == "Eldrazi Incursion")
    cards = deck["cards"]
    assert sum(c["quantity"] for c in cards) == 100, "Original 100-card manifest changed"
    assets = root / "android/app/src/main/assets/argentum"
    indexed = set()
    for filename in ("all-cards.index.tsv", "custom-cards.index.tsv"):
        path = assets / filename
        if not path.is_file():
            raise FileNotFoundError(f"Missing actual packaged index: {path}")
        indexed.update(row.split("\t", 1)[0] for row in path.read_text().splitlines() if row.strip())
    absent = [c["name"] for c in cards if c["name"] not in indexed]
    present = sum(c["quantity"] for c in cards if c["name"] in indexed)
    print(f"Original Eldrazi Incursion indexed: {present}/100; missing: {absent}")
    print("Index presence does not establish rules correctness or playable APK integration.")
    if present < minimum:
        raise SystemExit(f"Candidate below required {minimum}/100 original indexed slots")
    return present, absent


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[2])
    parser.add_argument("--minimum", type=int, default=90)
    args = parser.parse_args()
    audit(args.root, args.minimum)
