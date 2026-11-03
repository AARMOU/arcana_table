"""weekly.py's RETIRED record: a patch that leaves the series stays named, with the reason, in
engine-upgrade/audit_patches.py, so the current-build check does not report it as missing."""
import importlib.util
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location("weekly", ROOT / "engine-upgrade" / "weekly.py")
weekly = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(weekly)

BASE = 'TARGET = "%s"\nMANDATORY = [\n    "0001-a.patch",\n]\nEXPERIMENTAL = "x.patch"\n' % ("a" * 40)


def record(text):
    block = re.search(r"^RETIRED = \{\n(.*?)^\}", text, re.M | re.S)
    return dict(re.findall(r'"([^"]+)": "([^"]*)"', block[1])) if block else None


class RetiredRecord(unittest.TestCase):
    def test_creates_the_record_after_the_series(self):
        text = weekly.add_retired(BASE, "0009-z.patch", "upstream has it as of bbbbbbbbbb")
        self.assertEqual({"0009-z.patch": "upstream has it as of bbbbbbbbbb"}, record(text))
        self.assertLess(text.index("MANDATORY"), text.index("RETIRED"))
        self.assertIn('EXPERIMENTAL = "x.patch"', text)

    def test_appends_once_and_keeps_earlier_entries(self):
        text = weekly.add_retired(BASE, "0009-z.patch", "first")
        text = weekly.add_retired(text, "0010-y.patch", "second")
        text = weekly.add_retired(text, "0009-z.patch", "again")
        self.assertEqual({"0009-z.patch": "first", "0010-y.patch": "second"}, record(text))

    def test_the_real_record_names_the_folded_patch(self):
        text = (ROOT / "engine-upgrade" / "audit_patches.py").read_text()
        self.assertIn("0008-jvm-test-toolchain.patch", record(text))
        self.assertNotIn('"0008-jvm-test-toolchain.patch"', text[text.index("MANDATORY"):text.index("RETIRED")])


if __name__ == "__main__":
    unittest.main()
