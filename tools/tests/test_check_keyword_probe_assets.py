"""Offline regressions for the B137 keyword fixture/card-export parity gate."""
from pathlib import Path
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from check_keyword_probe_assets import expected_names, verify


TEST = """@Test fun cardsDoNotInheritKeywordLabelsFromCreatedTokensOrAnimationEffects() {
    val customFile = File(assets, "argentum/custom-cards.jsonl")
    val names = setOf("Black Market Connections", "Sai, Master Thopterist")
}"""


class KeywordProbeAssetsTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.test = self.root / "DeckSupportTest.kt"
        self.test.write_text(TEST, encoding="utf-8")
        self.assets = self.root / "argentum"
        self.assets.mkdir()
        self.custom = self.assets / "custom-cards.jsonl"
        self.upstream = self.assets / "all-cards.jsonl"
        self.write_cards(self.custom, ["Sai, Master Thopterist"])
        self.write_cards(self.upstream, ["Black Market Connections"])

    @staticmethod
    def write_cards(path, names):
        import json
        path.write_text("".join(json.dumps({"name": n}) + "\n" for n in names),
                        encoding="utf-8")

    def test_accepts_upstream_migration_without_custom_shadow(self):
        self.assertEqual(verify(self.test, self.assets), (2, 1, 1))

    def test_accepts_custom_only_when_upstream_does_not_have_card(self):
        self.write_cards(self.custom, ["Sai, Master Thopterist", "Black Market Connections"])
        self.write_cards(self.upstream, [])
        self.assertEqual(verify(self.test, self.assets), (2, 2, 0))

    def test_rejects_missing_card_from_both_sources(self):
        self.write_cards(self.upstream, [])
        with self.assertRaisesRegex(ValueError, "Black Market Connections"):
            verify(self.test, self.assets)

    def test_rejects_shadowing_when_card_in_both_sources(self):
        self.write_cards(self.custom, ["Sai, Master Thopterist", "Black Market Connections"])
        with self.assertRaisesRegex(ValueError, "shadowing: Black Market Connections"):
            verify(self.test, self.assets)

    def test_rejects_missing_asset_file(self):
        self.upstream.unlink()
        with self.assertRaisesRegex(ValueError, "shipped card asset missing"):
            verify(self.test, self.assets)

    def test_rejects_invalid_jsonl(self):
        self.upstream.write_text('{"name":', encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "invalid JSONL"):
            verify(self.test, self.assets)

    def test_rejects_missing_fixture_set(self):
        with self.assertRaisesRegex(ValueError, "named fixture set is missing"):
            expected_names("@Test fun otherTest() {}")

    def test_rejects_empty_fixture_set(self):
        with self.assertRaisesRegex(ValueError, "fixture set is empty"):
            expected_names(TEST.replace(
                '"Black Market Connections", "Sai, Master Thopterist"', ""))


if __name__ == "__main__":
    unittest.main()
