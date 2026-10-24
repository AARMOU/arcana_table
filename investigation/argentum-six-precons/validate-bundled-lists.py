#!/usr/bin/env python3
"""Ensure staged official precon assets contain *exactly* the saved Wizards manifest.

No internet calls, no alternate names, no silent substitutions, no library-dependent
coercion. This is decklist fidelity only; it does not imply Argentum can play the cards.
"""
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
MANIFEST = ROOT / "investigation/argentum-six-precons/deck-manifest.json"
ASSETS = ROOT / "android/app/src/main/assets/precons"

FILE_NAMES = {
    "Forces of the Imperium": "warhammer_forces_of_the_imperium.txt",
    "Tyranid Swarm": "warhammer_tyranid_swarm.txt",
    "Necron Dynasties": "warhammer_necron_dynasties.txt",
    "The Ruinous Powers": "warhammer_the_ruinous_powers.txt",
    "Eldrazi Unbound": "eldrazi_unbound.txt",
    "Eldrazi Incursion": "eldrazi_incursion.txt",
}
ENTRY = re.compile(r"^([1-9][0-9]*) (.+)$")
# Official manifests may repeat basic-land rows (3 x 10 Swamp in Necron Dynasties;
# 8 + 7 Wastes in Eldrazi Unbound). Preserve the historical rows in both files.
BASIC_LANDS = frozenset({"plains", "island", "swamp", "mountain", "forest", "wastes"})


def add_card(cards: dict, key: tuple, quantity: int, source: str) -> None:
    """Aggregate repeated basic-land rows; reject duplicate nonbasic card names."""
    name, _commander = key
    if key in cards and name not in BASIC_LANDS:
        raise AssertionError(f"{source}: duplicate nonbasic card name: {name}")
    cards[key] = cards.get(key, 0) + quantity



def parse_asset(path: Path) -> dict:
    if not path.is_file():
        raise AssertionError(f"Missing staged precon asset: {path}")
    cards = {}
    section = None
    for row in path.read_text(encoding="utf-8").splitlines():
        row = row.strip()
        if not row or row.startswith("#"):
            continue
        if row == "[COMMANDER]":
            section = True
            continue
        if row == "[OTHER SPELLS]":
            section = False
            continue
        match = ENTRY.fullmatch(row)
        if section is None or match is None:
            raise AssertionError(f"{path.name}: malformed line: {row!r}")
        quantity, name = int(match.group(1)), match.group(2)
        key = (name.casefold(), section)
        add_card(cards, key, quantity, path.name)
    return cards


def main() -> None:
    manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))
    decks = manifest["decks"]
    assert len(decks) == len(FILE_NAMES) == 6
    assert {d["name"] for d in decks} == set(FILE_NAMES)
    total = 0
    for deck in decks:
        expected = {}
        for card in deck["cards"]:
            key = (card["name"].casefold(), bool(card["commander"]))
            add_card(expected, key, int(card["quantity"]), deck["name"])
        actual = parse_asset(ASSETS / FILE_NAMES[deck["name"]])
        if actual != expected:
            missing = sorted(set(expected.items()) - set(actual.items()))
            extra = sorted(set(actual.items()) - set(expected.items()))
            raise AssertionError(
                f"{deck['name']}: staged list deviates from official manifest "
                f"missing={missing!r} extra={extra!r}"
            )
        assert sum(actual.values()) == 100, deck["name"]
        assert sum(v for (_, commander), v in actual.items() if commander) == 1, deck["name"]
        assert (deck["commander"].casefold(), True) in actual, deck["name"]
        total += sum(actual.values())
        print(f"PASS {deck['name']}: 100 exact card slots, correct commander")
    assert total == 600
    print("PASS all six historical official Commander precons: 600 exact card slots")


if __name__ == "__main__":
    main()
