import sys
import unittest
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from next_apk_enable_deck_tools import (
    OLD_HOME, NEW_HOME, OLD_FLAG, NEW_FLAG, OLD_PICKER, NEW_PICKER, transform
)

BUILDER = "internal object DeckBuilderFlag {\\n" + OLD_FLAG + "\\n}"

PICKER = (
    'onImport = { selectedPlayer = localSeat; importError = null; importOpen = true },\n'
    'if (deckBuilderOpen && DeckBuilderFlag.enabled) DeckManagerDialog(\n'
    'onDecksChanged = { updated -> savedDecks = updated; saveSavedDecks(context, updated) }\n'
    + OLD_PICKER + '\n'
)


class DeckToolsCandidatePortTest(unittest.TestCase):
    def test_existing_importer_and_editor_dispatch_without_duplicate_logic(self):
        home, flag, picker = transform(OLD_HOME + "\\n" + OLD_PICKER, BUILDER, PICKER)
        self.assertIn(NEW_HOME, home)
        self.assertIn("onClick = onImport", home)
        self.assertIn("enabled = !argentumActive", home)
        self.assertIn(NEW_FLAG, flag)
        self.assertIn(NEW_PICKER, home)
        self.assertNotIn("Not yet available", home)
        self.assertNotIn(OLD_FLAG, flag)

    def test_second_pass_noop(self):
        once = transform(OLD_HOME + "\\n" + OLD_PICKER, BUILDER, PICKER)
        self.assertEqual(transform(*once), once)

    def test_missing_importer_cannot_silently_enable(self):
        with self.assertRaisesRegex(ValueError, "dispatch missing"):
            transform(OLD_HOME + "\\n" + OLD_PICKER, BUILDER, PICKER.replace("importOpen = true", "importOpen = false"))

    def test_missing_storage_cannot_silently_enable(self):
        with self.assertRaisesRegex(ValueError, "shared saved-decks storage"):
            transform(OLD_HOME + "\\n" + OLD_PICKER, BUILDER, PICKER.replace("saveSavedDecks(context, updated)", "Unit"))

    def test_unrecognized_ui_fails_closed(self):
        with self.assertRaisesRegex(ValueError, "Home Import new"):
            transform((OLD_HOME + "\\n" + OLD_PICKER).replace("Not yet available", "Unsupported"), BUILDER, PICKER)

    def test_existing_gameplay_import_button_is_not_forced_active(self):
        home, _, _ = transform(OLD_HOME + "\\n" + OLD_PICKER, BUILDER, PICKER)
        self.assertIn("enabled = !argentumActive", home)


if __name__ == "__main__":
    unittest.main()
