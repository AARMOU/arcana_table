#!/usr/bin/env python3
"""Regression tests for the exact-original Eldrazi precon identity gate (#150)."""
from __future__ import annotations

import importlib.util
import pathlib
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

    def test_duplicate_name_is_rejected_not_collapsed(self):
        with self.assertRaisesRegex(ValueError, "Duplicate"):
            audit.original_deck_digest(self.cards + [dict(self.cards[0])])

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


if __name__ == "__main__":
    unittest.main()
