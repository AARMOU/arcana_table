"""B140-only regression: upstream collision retirement must not orphan playable cards.

This verifies exported library and registration *presence*, not complete Oracle behavior.
Actual positive/negative Argentum scenarios run in the paired GitHub Actions gate.
"""
import json
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "android/app/src/main/assets/argentum"
CARDS = ROOT / "android/custom-cards/src/main/kotlin/com/arcanatable/customcards"
EXPECTED = {
    "Black Market Connections": "BlackMarketConnections",
    "Commissar Severina Raine": "CommissarSeverinaRaine",
    "Jaheira, Friend of the Forest": "JaheiraFriendOfTheForest",
    "Scurry Oak": "ScurryOak",
    "Tataru Taru": "TataruTaru",
    "Wayward Swordtooth": "WaywardSwordtooth",
}


def validate_export(custom_index, upstream_index, json_lines, registry, source_files):
    """Fail closed on absent/duplicate names, missing source or empty scripted cards."""
    custom = [row.split("\t", 1)[0] for row in custom_index.splitlines() if row.strip()]
    upstream = [row.split("\t", 1)[0] for row in upstream_index.splitlines() if row.strip()]
    exported = [json.loads(row) for row in json_lines.splitlines() if row.strip()]
    names = [row["name"] for row in exported]
    for name, symbol in EXPECTED.items():
        assert custom.count(name) == 1, f"{name}: custom index count {custom.count(name)}"
        assert upstream.count(name) == 0, f"{name}: shadowed by upstream index"
        assert names.count(name) == 1, f"{name}: JSONL count {names.count(name)}"
        definition = next(row for row in exported if row["name"] == name)
        assert definition.get("script"), f"{name}: missing Argentum DSL export"
        assert re.search(r"\bEntry\(" + re.escape(symbol) + r",", registry), f"{name}: not registered"
        source = source_files.get(symbol, "")
        assert f'val {symbol} = card("{name}")' in source, f"{name}: no authoritative card definition"


class RestoredCollisionTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.custom = (ASSETS / "custom-cards.index.tsv").read_text()
        cls.upstream = (ASSETS / "all-cards.index.tsv").read_text()
        cls.jsonl = (ASSETS / "custom-cards.jsonl").read_text()
        cls.registry = (CARDS / "CustomCards.kt").read_text()
        cls.sources = {symbol: (CARDS / "cards" / f"{symbol}.kt").read_text()
                       for symbol in EXPECTED.values()}

    def check(self, **changes):
        values = dict(custom_index=self.custom, upstream_index=self.upstream,
                      json_lines=self.jsonl, registry=self.registry, source_files=self.sources)
        values.update(changes)
        validate_export(**values)

    def test_six_are_registered_and_exported_without_shadowing(self):
        self.check()

    def test_missing_custom_index_fails_closed(self):
        self.assertRaises(AssertionError, self.check,
                          custom_index=self.custom.replace("Jaheira, Friend of the Forest\tcard\n", ""))

    def test_upstream_duplicate_fails_closed(self):
        self.assertRaises(AssertionError, self.check,
                          upstream_index=self.upstream + "\nBlack Market Connections\tcard\n")

    def test_missing_jsonl_row_fails_closed(self):
        rows = [row for row in self.jsonl.splitlines()
                if not row.startswith('{"name":"Scurry Oak",')]
        self.assertRaises(AssertionError, self.check, json_lines="\n".join(rows))

    def test_missing_registry_fails_closed(self):
        self.assertRaises(AssertionError, self.check,
                          registry=self.registry.replace("Entry(TataruTaru,", "Entry(RemovedTataru,"))


if __name__ == "__main__":
    unittest.main()
