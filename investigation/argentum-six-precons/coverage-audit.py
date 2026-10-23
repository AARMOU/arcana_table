#!/usr/bin/env python3
"""Audit the six stock Commander decklists against Arcana's active Argentum card indexes.

Usage: python3 investigation/argentum-six-precons/coverage-audit.py
This does NOT mark cards supported based on card art or Scryfall lookup alone.
"""
import argparse
import json
from pathlib import Path

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--json-output", type=Path, help="Write machine-readable per-deck and distinct missing-card audit")
parser.add_argument("--markdown-output", type=Path, help="Write a readable prioritized implementation worklist")
args = parser.parse_args()

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
manifest = json.loads((HERE / "deck-manifest.json").read_text(encoding="utf-8"))
index_paths = manifest["baseline"]["cardIndexes"]

def normalize(name: str) -> str:
    return name.strip().casefold()

available = set()
for rel in index_paths:
    path = ROOT / rel
    for line in path.read_text(encoding="utf-8").splitlines():
        if not line.strip():
            continue
        available.add(normalize(line.split("\t", 1)[0]))

missing_names = set()
missing_by_name = {}
missing_slots = 0
all_names = set()
total_slots = 0
per_deck = []
for deck in manifest["decks"]:
    slots = sum(card["quantity"] for card in deck["cards"])
    assert slots == 100, f"{deck['name']} has {slots} cards instead of 100"
    commanders = [card for card in deck["cards"] if card["commander"]]
    assert len(commanders) == 1 and commanders[0]["quantity"] == 1, deck["name"]
    missing = [card for card in deck["cards"] if normalize(card["name"]) not in available]
    for card in deck["cards"]:
        all_names.add(normalize(card["name"]))
    for card in missing:
        key = normalize(card["name"])
        missing_names.add(key)
        item = missing_by_name.setdefault(key, {"name": card["name"], "decks": [], "slots": 0})
        item["decks"].append(deck["name"])
        item["slots"] += card["quantity"]
    subtotal = sum(card["quantity"] for card in missing)
    missing_slots += subtotal
    total_slots += slots
    per_deck.append({"name": deck["name"], "commander": deck["commander"], "supportedSlots": slots - subtotal, "missingSlots": subtotal, "missingCards": [{"name": card["name"], "quantity": card["quantity"]} for card in missing]})
    print(f"{deck['name']}: {slots - subtotal}/100 supported, {subtotal} missing")

assert total_slots == 600, total_slots
print(f"TOTAL: {len(all_names)} distinct cards, {len(missing_names)} unique missing, "
      f"{missing_slots} missing deck slots out of {total_slots}")

if args.json_output or args.markdown_output:
    priority = sorted(missing_by_name.values(), key=lambda item: (-len(item["decks"]), -item["slots"], item["name"].casefold()))
    result = {"source": "active Argentum indexes (name coverage, not gameplay verification)", "totalSlots": total_slots, "missingSlots": missing_slots, "distinctMissingCards": len(missing_names), "decks": per_deck, "missingCardNames": sorted(missing_names), "prioritizedMissingCards": priority}
    if args.json_output:
        args.json_output.parent.mkdir(parents=True, exist_ok=True)
        args.json_output.write_text(json.dumps(result, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
        print(f"Wrote audit: {args.json_output}")
    if args.markdown_output:
        lines = ["# Six-precon implementation worklist", "", "Index coverage only; every implementation still requires executable rules tests.", "", f"**Missing:** {missing_slots} of {total_slots} slots; {len(missing_names)} distinct card names.", "", "## Per-deck coverage", "", "| Deck | Supported | Missing |", "|---|---:|---:|"]
        lines.extend(f"| {d['name']} | {d['supportedSlots']} | {d['missingSlots']} |" for d in per_deck)
        lines.extend(["", "## Prioritized missing cards", "", "Shared cards first; this is a coverage worklist, not a claim of implementation complexity.", "", "| Card | Missing slots | Decks |", "|---|---:|---|"])
        lines.extend(f"| {c['name']} | {c['slots']} | {', '.join(c['decks'])} |" for c in priority)
        args.markdown_output.parent.mkdir(parents=True, exist_ok=True)
        args.markdown_output.write_text("\\n".join(lines) + "\\n", encoding="utf-8")
        print(f"Wrote worklist: {args.markdown_output}")
