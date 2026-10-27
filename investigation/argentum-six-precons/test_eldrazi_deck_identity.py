#!/usr/bin/env python3
"""Regression tests for the exact-original Eldrazi precon identity gate (#150)."""
from __future__ import annotations

import importlib.util
import pathlib
import tempfile
import unittest

SCRIPT = pathlib.Path(__file__).with_name("eldrazi-incursion-cardkit-triage.py")
spec = importlib.util.spec_from_file_location("eldrazi_incursion_cardkit_triage", SCRIPT)
assert spec and spec.loader
audit = importlib.util.module_from_spec(spec)
spec.loader.exec_module(audit)


class OriginalDeckIdentityTest(unittest.TestCase):
    def setUp(self):
        self.cards = [
            {"name": "Eldrazi Temple", "quantity": 1},
            {"name": "Wastes", "quantity": 12},
            {"name": "Ugin, the Ineffable", "quantity": 1},
        ]

    def test_same_exact_cards_in_different_manifest_order_have_same_digest(self):
        self.assertEqual(
            audit.original_deck_digest(self.cards),
            audit.original_deck_digest(list(reversed(self.cards))),
        )

    def test_substituting_one_card_changes_original_identity(self):
        replaced = [dict(c) for c in self.cards]
        replaced[0]["name"] = "Temple of the False God"
        self.assertNotEqual(
            audit.original_deck_digest(self.cards),
            audit.original_deck_digest(replaced),
        )

    def test_changing_quantity_changes_original_identity(self):
        altered = [dict(c) for c in self.cards]
        altered[1]["quantity"] = 11
        self.assertNotEqual(
            audit.original_deck_digest(self.cards),
            audit.original_deck_digest(altered),
        )

    def test_split_wastes_rows_preserve_exact_card_quantities(self):
        split = [dict(c) for c in self.cards]
        split[1]["quantity"] = 8
        split.append({"name": "Wastes", "quantity": 4})
        self.assertEqual(
            audit.original_deck_digest(self.cards),
            audit.original_deck_digest(split),
        )

    def test_extra_duplicate_copy_changes_identity(self):
        with_extra = self.cards + [{"name": "Wastes", "quantity": 1}]
        self.assertNotEqual(
            audit.original_deck_digest(self.cards),
            audit.original_deck_digest(with_extra),
        )

    def test_zero_or_non_integer_quantity_is_rejected(self):
        for bad in (0, -1, 1.0, True):
            with self.subTest(bad=bad):
                with self.assertRaisesRegex(ValueError, "quantity"):
                    audit.original_deck_digest([{"name": "Wastes", "quantity": bad}])

    def test_empty_or_whitespace_name_is_rejected(self):
        for bad in ("", " Wastes", "Wastes "):
            with self.subTest(bad=bad):
                with self.assertRaisesRegex(ValueError, "name"):
                    audit.original_deck_digest([{"name": bad, "quantity": 1}])

    def test_missing_or_modified_official_deck_fails_closed(self):
        valid = {"Eldrazi Incursion": {"cards": self.cards}}
        expected = {"Eldrazi Incursion": audit.original_deck_digest(self.cards)}
        self.assertEqual(audit.verify_original_decks(valid, expected), expected)
        with self.assertRaisesRegex(ValueError, "Missing"):
            audit.verify_original_decks({}, expected)
        changed = {"Eldrazi Incursion": {"cards": self.cards[:-1]}}
        with self.assertRaisesRegex(ValueError, "identity"):
            audit.verify_original_decks(changed, expected)


class ShippedCustomExportIntegrityTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        root = pathlib.Path(self.tmp.name)
        self.index = root / "custom-cards.index.tsv"
        self.jsonl = root / "custom-cards.jsonl"

    def write(self, index: str, jsonl: str):
        self.index.write_text(index)
        self.jsonl.write_text(jsonl)

    def test_matching_custom_index_and_jsonl_is_accepted(self):
        self.write('Eldrazi Temple\tcard\nUgin, the Ineffable\tcard\n',
                   '{"name":"Eldrazi Temple"}\n{"name":"Ugin, the Ineffable"}\n')
        entries = audit.read_index_entries(self.index)
        self.assertEqual(audit.verify_custom_export(entries, self.jsonl), 2)

    def test_ghost_index_entry_is_rejected(self):
        self.write('Eldrazi Temple\tcard\nUgin, the Ineffable\tcard\n',
                   '{"name":"Eldrazi Temple"}\n')
        with self.assertRaisesRegex(ValueError, "index-only"):
            audit.verify_custom_export(audit.read_index_entries(self.index), self.jsonl)

    def test_unindexed_export_is_rejected(self):
        self.write('Eldrazi Temple\tcard\n',
                   '{"name":"Eldrazi Temple"}\n{"name":"Ugin, the Ineffable"}\n')
        with self.assertRaisesRegex(ValueError, "export-only"):
            audit.verify_custom_export(audit.read_index_entries(self.index), self.jsonl)

    def test_duplicate_index_and_duplicate_export_are_rejected(self):
        self.write('Eldrazi Temple\tcard\nEldrazi Temple\tcard\n',
                   '{"name":"Eldrazi Temple"}\n')
        with self.assertRaisesRegex(ValueError, "duplicate index"):
            audit.read_index_entries(self.index)
        self.write('Eldrazi Temple\tcard\n',
                   '{"name":"Eldrazi Temple"}\n{"name":"Eldrazi Temple"}\n')
        with self.assertRaisesRegex(ValueError, "duplicate exported"):
            audit.verify_custom_export(audit.read_index_entries(self.index), self.jsonl)

    def test_malformed_index_and_jsonl_fail_closed(self):
        self.write('Eldrazi Temple\tunknown\n', '{"name":"Eldrazi Temple"}\n')
        with self.assertRaisesRegex(ValueError, "invalid card index"):
            audit.read_index_entries(self.index)
        self.write('Eldrazi Temple\tcard\n', '{"name":\n')
        with self.assertRaisesRegex(ValueError, "invalid JSON"):
            audit.verify_custom_export(audit.read_index_entries(self.index), self.jsonl)

    def test_token_kind_is_not_a_card(self):
        self.write('Eldrazi Spawn\ttoken\nEldrazi Temple\tcard\n',
                   '{"name":"Eldrazi Spawn"}\n{"name":"Eldrazi Temple"}\n')
        entries = audit.read_index_entries(self.index)
        self.assertEqual(audit.verify_custom_export(entries, self.jsonl), 2)
        self.assertEqual({name for name, kind in entries.items() if kind == "card"},
                         {"Eldrazi Temple"})


if __name__ == "__main__":
    unittest.main()
