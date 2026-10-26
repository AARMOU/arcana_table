"""Fail-closed in-app What's new source injection after Build 121."""
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from next_apk_changelog_since_b121 import (
    ARCHIVE_MARKER,
    BEGIN,
    END,
    ENTRIES,
    UI,
    patched,
    validate,
)


class SinceBuild121ChangelogTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.original = UI.read_text()
        cls.updated = patched(cls.original)

    def test_all_milestones_and_next_build_are_visible_in_whats_new(self):
        for title, _ in ENTRIES:
            self.assertIn(title, self.updated)
        self.assertIn("BUILD 123 · LARGE END-STEP AI FIX", self.updated)
        self.assertIn("BUILD 122 · FIXES AND QUICK UI TIPS", self.updated)
        self.assertLess(self.updated.index(BEGIN), self.updated.index("BUILD 123 · LARGE END-STEP AI FIX"))
        self.assertLess(self.updated.index("BUILD 122 · FIXES AND QUICK UI TIPS"), self.updated.index(ARCHIVE_MARKER))
        self.assertLess(self.updated.index(ARCHIVE_MARKER), self.updated.index("BUILDS 105–106 · NEW CARDS AND DECKS"))

    def test_preserves_original_historical_text_without_editing_the_old_rows(self):
        original_prefix, original_suffix = self.original.split('                Text("BUILD 123 · LARGE END-STEP AI FIX"', 1)
        updated_prefix, updated_suffix = self.updated.split('                Text("BUILD 123 · LARGE END-STEP AI FIX"', 1)
        self.assertTrue(updated_prefix.startswith(original_prefix))
        # Only the new archive heading before the old Build 105 text differs.
        self.assertEqual(updated_suffix.replace(ARCHIVE_MARKER + "\n", ""), original_suffix)

    def test_idempotent(self):
        self.assertEqual(patched(self.updated), self.updated)
        validate(self.updated)

    def test_changed_baseline_build_123_fails_closed(self):
        with self.assertRaisesRegex(ValueError, "Existing What's new"):
            patched(self.original.replace("BUILD 123 · LARGE END-STEP AI FIX", "BUILD 123 · WRONG", 1))

    def test_partial_port_fails_closed(self):
        with self.assertRaisesRegex(ValueError, "Partial"):
            patched(self.original.replace('                Text("BUILD 123', BEGIN + '\n                Text("BUILD 123', 1))

    def test_explicitly_distinguishes_compiled_and_next_build(self):
        b134 = ENTRIES[0][1]
        nextup = ENTRIES[1][1]
        self.assertTrue(any("CI TEST certificate" in line or "test" in line.lower() for line in b134))
        self.assertTrue(any("NOT in the previous no-goose Build 134 APK" in line for line in b134))
        self.assertTrue(any("require Android device validation" in line for line in nextup))
        self.assertTrue(any("Worker is updated" in line for line in nextup))

    def test_no_premature_release_or_editor_claim(self):
        self.assertTrue(any("not enabled" in line for line in ENTRIES[0][1]))
        self.assertIn("BuildConfig.VERSION_CODE", self.updated)


if __name__ == "__main__":
    unittest.main()
