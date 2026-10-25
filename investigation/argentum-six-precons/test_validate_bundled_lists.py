#!/usr/bin/env python3
"""Regression checks for official precon list fidelity and duplicate-row handling."""
import importlib.util
import tempfile
import unittest
from pathlib import Path

MODULE_PATH = Path(__file__).with_name("validate-bundled-lists.py")
SPEC = importlib.util.spec_from_file_location("validate_bundled_lists", MODULE_PATH)
validator = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(validator)


class PreconListValidatorTest(unittest.TestCase):
    def parse(self, rows):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "list.txt"
            path.write_text("[COMMANDER]\n1 Szarekh, the Silent King\n[OTHER SPELLS]\n" + rows, encoding="utf-8")
            return validator.parse_asset(path)

    def test_repeated_swamp_rows_are_summed_without_editing_historical_list(self):
        cards = self.parse("10 Swamp\n10 Swamp\n10 Swamp\n")
        self.assertEqual(30, cards[("swamp", False)])
        self.assertEqual(1, cards[("szarekh, the silent king", True)])

    def test_repeated_wastes_rows_are_summed(self):
        self.assertEqual(15, self.parse("8 Wastes\n7 Wastes\n")[("wastes", False)])

    def test_basic_land_matching_is_case_insensitive(self):
        self.assertEqual(3, self.parse("1 Island\n2 ISLAND\n")[("island", False)])

    def test_repeated_nonbasic_card_is_rejected(self):
        with self.assertRaisesRegex(AssertionError, "duplicate nonbasic"):
            self.parse("1 Sol Ring\n1 Sol Ring\n")

    def test_repeated_nonbasic_manifest_entry_is_rejected(self):
        expected = {}
        validator.add_card(expected, ("sol ring", False), 1, "test manifest")
        with self.assertRaisesRegex(AssertionError, "duplicate nonbasic"):
            validator.add_card(expected, ("sol ring", False), 1, "test manifest")

    def test_commander_and_main_deck_sections_remain_distinct(self):
        cards = self.parse("1 Szarekh, the Silent King\n")
        self.assertEqual(1, cards[("szarekh, the silent king", True)])
        self.assertEqual(1, cards[("szarekh, the silent king", False)])

    def test_actual_six_historical_lists_match_manifest(self):
        validator.main()


if __name__ == "__main__":
    unittest.main()
