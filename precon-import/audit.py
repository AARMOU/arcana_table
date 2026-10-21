#!/usr/bin/env python3
"""Read-only coverage inventory for the six requested official Commander precons.

Uses a pinned machine-readable precon dataset. A catalog hit means a named card
exists in exported assets; it does NOT establish correct Oracle/rules behavior.
No application or engine files are modified by this script.
"""
import argparse
import collections
import json
import pathlib
import re
import unicodedata

DECKS = (
    ("Forces of the Imperium", "40k"),
    ("Tyranid Swarm", "40k"),
    ("Necron Dynasties", "40k"),
    ("The Ruinous Powers", "40k"),
    ("Eldrazi Unbound", "cmm"),
    ("Eldrazi Incursion", "m3c"),
)
OFFICIAL = {
    "40k": "https://magic.wizards.com/en/news/announcements/warhammer-40000-commander-decklists",
    "cmm": "https://magic.wizards.com/en/news/announcements/commander-masters-commander-decklists",
    "m3c": "https://magic.wizards.com/en/news/announcements/modern-horizons-3-commander-decklists",
}


def key(name):
    return re.sub(r"\s+", " ", unicodedata.normalize("NFKC", name)).strip().casefold()


def read_index(path):
    lines = pathlib.Path(path).read_text(encoding="utf-8-sig").splitlines()
    return {key(line.split("\t", 1)[0]): line.split("\t", 1)[0] for line in lines if line.strip()}


def deck_rows(source):
    if isinstance(source, dict):
        source = source.get("data", source.get("decks", source))
    if not isinstance(source, list):
        raise ValueError("Expected a JSON array of deck objects")
    return source


def extract_decks(source):
    deck_index = collections.defaultdict(list)
    for deck in deck_rows(source):
        deck_index[(key(deck.get("name", "")), key(deck.get("set_code", "")))].append(deck)
    extracted = {}
    for deck_name, set_code in DECKS:
        candidates = deck_index[(key(deck_name), key(set_code))]
        if len(candidates) != 1:
            raise ValueError(f"Expected one deck {deck_name!r}/{set_code}, found {len(candidates)}")
        deck = candidates[0]
        cards = collections.Counter()
        for zone in ("cards", "commander"):
            for item in deck.get(zone) or []:
                name = item["name"]
                count = int(item.get("count", 1))
                if count < 1:
                    raise ValueError(f"Invalid count for {name} in {deck_name}")
                cards[name] += count
        if sum(cards.values()) != 100:
            raise ValueError(f"{deck_name} has {sum(cards.values())} playable cards, expected 100; check zone schema")
        if len({key(name) for name in cards}) != len(cards):
            raise ValueError(f"Duplicate names differing only by case or Unicode in {deck_name}")
        extracted[deck_name] = cards
    return extracted


def audit(source, catalog):
    decks = extract_decks(source)
    membership = collections.defaultdict(list)
    entries = []
    for deck_name, _ in DECKS:
        cards = decks[deck_name]
        missing = sorted((name for name in cards if key(name) not in catalog), key=key)
        for name in missing:
            membership[key(name)].append(deck_name)
        entries.append({
            "name": deck_name,
            "unique_names": len(cards),
            "total_cards": sum(cards.values()),
            "catalog_present_names": len(cards) - len(missing),
            "catalog_absent_names": len(missing),
            "catalog_absent_cards": missing,
            "cards": dict(sorted(cards.items(), key=lambda pair: key(pair[0]))),
        })
    representative = {}
    for deck in entries:
        for name in deck["catalog_absent_cards"]:
            representative.setdefault(key(name), name)
    all_missing = sorted(representative.values(), key=key)
    return {
        "disclaimer": "Catalog presence is not behavioral support. No Oracle/scenario/AI test was run.",
        "source": "taw/magic-preconstructed-decks-data decks_v2.json; verify against linked WotC decklists",
        "official_sources": OFFICIAL,
        "decks": entries,
        "combined_missing_distinct": len(all_missing),
        "combined_missing_names": all_missing,
        "membership": {representative[k]: membership[k] for k in sorted(membership)},
    }


def write_outputs(result, output_dir, batch_count):
    root = pathlib.Path(output_dir)
    root.mkdir(parents=True, exist_ok=True)
    (root / "inventory.json").write_text(json.dumps(result, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    lines = [
        "# Six-precon Argentum catalog audit",
        "",
        "**Important:** catalog presence is not proof of rules-correct behavior. Absent means no exported card name.",
        "Source: [machine-readable precons](https://github.com/taw/magic-preconstructed-decks-data) and [official WotC lists](https://magic.wizards.com/en/news/announcements/warhammer-40000-commander-decklists).",
        "",
        "| Deck | Copies | Unique names | Catalog present | Catalog absent |",
        "|---|---:|---:|---:|---:|",
    ]
    for d in result["decks"]:
        lines.append(f'| {d["name"]} | {d["total_cards"]} | {d["unique_names"]} | {d["catalog_present_names"]} | {d["catalog_absent_names"]} |')
    lines += ["", f'**Distinct catalog-absent names across all six: {result["combined_missing_distinct"]}**', ""]
    for d in result["decks"]:
        lines += [f'## {d["name"]}', "", ", ".join(d["catalog_absent_cards"]) or "_None_", ""]
    (root / "REPORT.md").write_text("\n".join(lines), encoding="utf-8")
    names = sorted(result["combined_missing_names"],
        key=lambda name: (-len(result["membership"][name]), key(name)))
    groups = [[] for _ in range(batch_count)]
    for i, name in enumerate(names):
        groups[i % batch_count].append({
            "name": name, "decks": result["membership"][name],
            "implementation": "pending", "scenario_test": "pending",
            "reprint_check": "pending", "mobile_export_check": "pending",
        })
    batches = root / "batches"
    batches.mkdir(exist_ok=True)
    for idx, cards in enumerate(groups, 1):
        (batches / f"{idx:02d}.json").write_text(
            json.dumps({"batch": idx, "cards": cards}, indent=2, ensure_ascii=False) + "\n",
            encoding="utf-8")
    assert sum(len(group) for group in groups) == result["combined_missing_distinct"]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--decks", required=True, help="decks_v2.json downloaded from pinned source")
    parser.add_argument("--all-index", required=True)
    parser.add_argument("--custom-index", required=True)
    parser.add_argument("--out", required=True)
    parser.add_argument("--batches", type=int, default=15)
    args = parser.parse_args()
    if not 1 <= args.batches <= 100:
        parser.error("--batches must be between 1 and 100")
    catalog = read_index(args.all_index) | read_index(args.custom_index)
    source = json.loads(pathlib.Path(args.decks).read_text(encoding="utf-8"))
    result = audit(source, catalog)
    write_outputs(result, args.out, args.batches)
    print(f'Validated {len(result["decks"])} decks and 600 card copies.')
    for d in result["decks"]:
        print(f'  {d["name"]}: {d["catalog_absent_names"]} distinct names absent')
    print(f'Combined: {result["combined_missing_distinct"]} distinct names absent; {args.batches} batches')
    print("No rules implementation, card scenarios, or Android build validation was performed.")


if __name__ == "__main__":
    main()
