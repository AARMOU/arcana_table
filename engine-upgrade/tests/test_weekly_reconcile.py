"""weekly.py reconcile: every Arcana custom card is kept; the version in play follows its scenario tests."""
import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
import weekly  # noqa: E402


class ReconcileHelpers(unittest.TestCase):
    def setUp(self):
        self.tmp = Path(tempfile.mkdtemp())

    def test_every_custom_card_definition_is_mapped(self):
        identifiers = weekly.card_identifiers()
        custom = weekly.names(weekly.ASSETS / "custom-cards.index.tsv")
        mapped = set(identifiers.values())
        # A definition the regex can't read is still found by its quoted name in a test.
        self.assertGreater(len(mapped & set(custom)), len(custom) * 0.95)

    def test_failing_test_implicates_only_the_cards_it_exercises(self):
        tests = self.tmp / "com/arcanatable/customcards"
        tests.mkdir(parents=True)
        (tests / "OakTest.kt").write_text('class OakTest { val d = registerCard(ScurryOak) }\n')
        (tests / "TaruTest.kt").write_text('class TaruTest { val n = "Tataru Taru" }\n')
        ids = {"ScurryOak": "Scurry Oak", "TataruTaru": "Tataru Taru", "Rewind": "Rewind"}
        with mock.patch.object(weekly, "CUSTOM_TESTS", self.tmp):
            hit = weekly.implicated({"com.arcanatable.customcards.OakTest"}, ["Scurry Oak", "Tataru Taru", "Rewind"], ids)
            self.assertEqual(hit, {"Scurry Oak"})
            hit = weekly.implicated({"com.arcanatable.customcards.TaruTest"}, ["Scurry Oak", "Tataru Taru"], ids)
            self.assertEqual(hit, {"Tataru Taru"})

    def test_failing_classes_come_from_junit_xml(self):
        (self.tmp / "TEST-a.xml").write_text(
            '<testsuite><testcase classname="x.OakTest" name="t"><failure message="no"/></testcase>'
            '<testcase classname="x.FineTest" name="t"/></testsuite>')
        self.assertEqual(weekly.failing_test_classes(self.tmp), {"x.OakTest"})

    def test_override_file_round_trip(self):
        target = self.tmp / "custom-overrides.txt"
        with mock.patch.object(weekly, "OVERRIDES", target):
            weekly.write_overrides({"Rewind", "Scurry Oak"})
            self.assertEqual(weekly.read_overrides(), ["Rewind", "Scurry Oak"])
            weekly.write_overrides(set())
            self.assertEqual(weekly.read_overrides(), [])
            self.assertEqual(target.read_text(), weekly.OVERRIDES_HEADER)

    def test_shipped_override_file_is_the_header_plus_card_names(self):
        # The base ships the header plus whatever a merged weekly upgrade reconciled (e.g. Sifter of Skulls
        # after the c1c04ca upgrade): one card name per line, nothing else.
        text = (weekly.ASSETS / "custom-overrides.txt").read_text()
        self.assertTrue(text.startswith(weekly.OVERRIDES_HEADER))
        names = text[len(weekly.OVERRIDES_HEADER):].splitlines()
        for name in names:
            self.assertTrue(name.strip() and name == name.strip() and not name.startswith("#"), repr(name))
        self.assertEqual(len(names), len(set(names)))

    def test_collisions_no_longer_block_the_card_check(self):
        source = Path(weekly.__file__).read_text()
        self.assertNotIn('report["collisions"] or', source)


if __name__ == "__main__":
    unittest.main()
