"""Isolated tests for catalog audit correctness; not card-behavior tests."""
import tempfile
import unittest
from pathlib import Path
import importlib.util

spec = importlib.util.spec_from_file_location("precon_audit", Path(__file__).with_name("audit.py"))
audit = importlib.util.module_from_spec(spec)
spec.loader.exec_module(audit)


def sample_source():
    decks = []
    for name, set_code in audit.DECKS:
        decks.append({
            "name": name, "set_code": set_code,
            "cards": [{"name": "Sol Ring", "count": 1}, {"name": "Wastes", "count": 98}],
            "commander": [{"name": name + " Commander", "count": 1}],
        })
    return decks


class AuditTests(unittest.TestCase):
    def test_all_decks_and_commander_counted(self):
        result = audit.audit(sample_source(), {"sol ring": "Sol Ring", "wastes": "Wastes"})
        self.assertEqual(len(result["decks"]), 6)
        self.assertEqual([d["total_cards"] for d in result["decks"]], [100] * 6)
        self.assertEqual(result["combined_missing_distinct"], 6)

    def test_broken_deck_rejected(self):
        source = sample_source()
        source[0]["cards"][1]["count"] = 97
        with self.assertRaisesRegex(ValueError, "expected 100"):
            audit.extract_decks(source)

    def test_missing_expected_deck_rejected(self):
        with self.assertRaisesRegex(ValueError, "Expected one deck"):
            audit.extract_decks(sample_source()[:-1])

    def test_custom_index_unions_with_catalog(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / "all.tsv").write_text("Sol Ring\tcard\n", encoding="utf-8")
            (root / "custom.tsv").write_text("Unclaimed Territory\tcard\n", encoding="utf-8")
            names = audit.read_index(root / "all.tsv") | audit.read_index(root / "custom.tsv")
            self.assertIn("unclaimed territory", names)
            self.assertIn("sol ring", names)

    def test_fifteen_batches_each_card_once(self):
        with tempfile.TemporaryDirectory() as tmp:
            result = audit.audit(sample_source(), {})
            audit.write_outputs(result, tmp, 15)
            batch_files = sorted((Path(tmp) / "batches").glob("*.json"))
            self.assertEqual(len(batch_files), 15)
            import json
            batch_names = [c["name"] for f in batch_files for c in json.loads(f.read_text())["cards"]]
            self.assertEqual(len(batch_names), len(set(batch_names)))
            self.assertEqual(len(batch_names), result["combined_missing_distinct"])


if __name__ == "__main__":
    unittest.main()
