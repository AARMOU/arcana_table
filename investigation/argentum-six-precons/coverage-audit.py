#!/usr/bin/env python3
"""Audit the six stock Commander decklists against Arcana's active Argentum card indexes.

Usage: python3 investigation/argentum-six-precons/coverage-audit.py
This does NOT mark cards supported based on card art or Scryfall lookup alone.

Extensions (tools/cardkit):
  --deck FILE        audit ManaBox / Moxfield text or CSV deck lists instead of the six-precon manifest
                     (repeatable; no 100-card / single-commander assertions for these)
  --readiness        classify every card SUPPORTED / NEEDS_REVIEW / ENGINE_BLOCKED / IMPLEMENTED_AND_TESTED
                     and route missing cards to Copilot, GPT or Claude (see docs/agents/CARD-WORKFLOW.md);
                     --json-output / --markdown-output then write the readiness report
"""
import argparse
import json
import sys
from pathlib import Path

parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
parser.add_argument("--json-output", type=Path, help="Write machine-readable per-deck and distinct missing-card audit")
parser.add_argument("--markdown-output", type=Path, help="Write a readable prioritized implementation worklist")
parser.add_argument("--deck", type=Path, action="append", help="Deck list file (ManaBox/Moxfield text or CSV); repeatable")
parser.add_argument("--readiness", action="store_true", help="Add readiness classification and routing (tools/cardkit)")
args = parser.parse_args()

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]

if args.readiness:
    sys.path.insert(0, str(ROOT / "tools" / "cardkit"))
    import cardkit  # noqa: E402

    argv = ["gaps"]
    for d in args.deck or []:
        argv += ["--deck", str(d)]
    if args.json_output:
        argv += ["--json-output", str(args.json_output)]
    if args.markdown_output:
        argv += ["--markdown-output", str(args.markdown_output)]
    sys.exit(cardkit.main(argv))

manifest = json.loads((HERE / "deck-manifest.json").read_text(encoding="utf-8"))
index_paths = manifest["baseline"]["cardIndexes"]

if args.deck:
    sys.path.insert(0, str(ROOT / "tools" / "cardkit"))
    from cardkit_lib import decklist  # noqa: E402
    decks = [decklist.load(p) for p in args.deck]
    for deck in decks:
        deck.setdefault("commander", next((c["name"] for c in deck["cards"] if c["commander"]), None))
    stock_precons = False
else:
    decks = manifest["decks"]
    stock_precons = True


def normalize(name: str) -> str:
    return name.replace("’", "'").strip().casefold()


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
for deck in decks:
    slots = sum(card["quantity"] for card in deck["cards"])
    if stock_precons:
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
    per_deck.append({"name": deck["name"], "commander": deck.get("commander"), "supportedSlots": slots - subtotal, "missingSlots": subtotal, "missingCards": [{"name": card["name"], "quantity": card["quantity"]} for card in missing]})
    print(f"{deck['name']}: {slots - subtotal}/{slots} supported, {subtotal} missing")

if stock_precons:
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
        title = "Six-precon implementation worklist" if stock_precons else "Deck implementation worklist"
        lines = [f"# {title}", "", "Index coverage only; every implementation still requires executable rules tests.", "", f"**Missing:** {missing_slots} of {total_slots} slots; {len(missing_names)} distinct card names.", "", "## Per-deck coverage", "", "| Deck | Supported | Missing |", "|---|---:|---:|"]
        lines.extend(f"| {d['name']} | {d['supportedSlots']} | {d['missingSlots']} |" for d in per_deck)
        lines.extend(["", "## Prioritized missing cards", "", "Shared cards first; this is a coverage worklist, not a claim of implementation complexity.", "", "| Card | Missing slots | Decks |", "|---|---:|---|"])
        lines.extend(f"| {c['name']} | {c['slots']} | {', '.join(c['decks'])} |" for c in priority)
        args.markdown_output.parent.mkdir(parents=True, exist_ok=True)
        args.markdown_output.write_text("\n".join(lines) + "\n", encoding="utf-8")
        print(f"Wrote worklist: {args.markdown_output}")
