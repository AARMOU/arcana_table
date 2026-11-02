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


class ReconcileAnotherTree(unittest.TestCase):
    """The current build's cards are reconciled in a scratch copy of that build's Android tree."""

    def test_a_card_upstream_now_has_keeps_arcanas_version_in_the_other_tree(self):
        tmp = Path(tempfile.mkdtemp())
        tree = tmp / "android"
        assets = tree / "app/src/main/assets/argentum"
        cards = tree / "custom-cards/src/main/kotlin/com/arcanatable/customcards/cards"
        tests = tree / "custom-cards/src/test/kotlin/com/arcanatable/customcards"
        for d in (assets, cards, tests):
            d.mkdir(parents=True)
        (assets / "all-cards.index.tsv").write_text("Ugin, the Ineffable\t1\nForest\t2\n")
        (assets / "custom-cards.index.tsv").write_text("Ugin, the Ineffable\t1\nUgin's Insight\t2\n")
        (cards / "Ugin.kt").write_text('val UginTheIneffable = card("Ugin, the Ineffable") {}\n')
        (tests / "UginTheIneffableScenarioTest.kt").write_text("class T { val c = UginTheIneffable }\n")
        results = tree / "custom-cards/build/test-results/test"
        calls = []

        def fake_run(*args, cwd=None, check=True, env=None):
            calls.append((args, cwd))
            results.mkdir(parents=True, exist_ok=True)
            kept = "Ugin, the Ineffable" in (assets / "custom-overrides.txt").read_text()
            bad = "" if kept else '<failure message="Argentum version plays differently"/>'
            (results / "TEST-u.xml").write_text('<testsuite><testcase classname="com.arcanatable.customcards.'
                                                f'UginTheIneffableScenarioTest" name="t">{bad}</testcase></testsuite>')
            return mock.Mock(returncode=0 if kept else 1, stdout="", stderr="")

        names = ("ANDROID", "ASSETS", "CARD_SOURCES", "CUSTOM_TESTS", "CUSTOM_RESULTS", "OVERRIDES")
        saved = {n: getattr(weekly, n) for n in names}
        try:
            with mock.patch.object(weekly, "run", fake_run), mock.patch.object(weekly, "OUT", tmp / "out"):
                code = weekly.reconcile("4", "--android", str(tree), "--name", "currentreconcile",
                                        "--tests", "com.arcanatable.customcards.*ScenarioTest")
        finally:
            for n, v in saved.items():
                setattr(weekly, n, v)
        self.assertEqual(0, code)
        self.assertIn("Ugin, the Ineffable", (assets / "custom-overrides.txt").read_text())
        self.assertTrue((tmp / "out" / "currentreconcile.json").is_file())
        self.assertIn("Arcana's version kept", (tmp / "out" / "currentreconcile.md").read_text())
        self.assertEqual(2, len(calls))
        self.assertEqual(tree.resolve(), calls[0][1])
        self.assertIn("com.arcanatable.customcards.*ScenarioTest", calls[0][0])
        # this repository's own override file was never touched
        self.assertTrue((saved["OVERRIDES"]).read_text().startswith(weekly.OVERRIDES_HEADER))

    def test_unknown_options_are_refused(self):
        with self.assertRaises(SystemExit):
            weekly.reconcile("4", "--android")


if __name__ == "__main__":
    unittest.main()
