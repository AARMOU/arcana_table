import json
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import review_refresh as rr  # noqa: E402

ENTRY = {"summary": "s", "verifiedBy": "copilot", "references": ["X.kt"], "reviewedLines": [{"line": "l", "primitives": ["Effects.May"]}],
         "baseSha": "a" * 40, "oracleHash": "o" * 64, "runtimeHash": "r" * 64}
BASE = {"_about": "x", "cards": {"Other": {"summary": "o"}}}


def head(**cards):
    return {"_about": "x", "cards": {**BASE["cards"], **cards}}


class Extract(unittest.TestCase):
    def test_exactly_one_card(self):
        got = rr.extract({rr.VS: {"base": BASE, "head": head(**{"Armory Automaton": ENTRY})}}, "Armory Automaton")
        self.assertTrue(got["ok"])
        self.assertEqual(got["decisions"][rr.VS], ("Armory Automaton", ENTRY))

    def test_refuses_anything_else(self):
        for pr in ({rr.VS: {"base": BASE, "head": head(**{"Armory Automaton": ENTRY, "Sneaky": ENTRY})}},
                   {rr.VS: {"base": BASE, "head": {**head(**{"Armory Automaton": ENTRY}), "_about": "rewritten"}}},
                   {rr.VS: {"base": BASE, "head": head(**{"Wrong Card": ENTRY})}},
                   {rr.VS: {"base": BASE, "head": {"cards": {}}}},
                   {rr.VS: {"base": BASE, "head": head(**{"Armory Automaton": {**ENTRY, "reviewedLines": []}})}},
                   {rr.KB: {"base": BASE, "head": head(**{"Armory Automaton": {"note": "no reason"}})}},
                   {rr.VS: {"base": BASE, "head": BASE}}):
            self.assertFalse(rr.extract(pr, "Armory Automaton")["ok"], pr)


class Apply(unittest.TestCase):
    def setUp(self):
        self.dir = Path(tempfile.mkdtemp())
        (self.dir / rr.DATA).mkdir(parents=True)
        (self.dir / rr.DATA / rr.VS).write_text(rr.dump(BASE), encoding="utf-8")

    def read(self):
        return json.loads((self.dir / rr.DATA / rr.VS).read_text(encoding="utf-8"))

    def test_restamped_entry_is_written_in_canonical_format(self):
        def restamp(e):
            e["runtimeHash"] = "n" * 64
            return "restamped"
        out = rr.apply(self.dir, {rr.VS: ("Armory Automaton", ENTRY)}, restamp)
        self.assertEqual(out, {"ok": True, "changed": [rr.DATA + rr.VS], "restamp": "restamped"})
        self.assertEqual(self.read()["cards"]["Armory Automaton"]["runtimeHash"], "n" * 64)
        self.assertEqual(ENTRY["runtimeHash"], "r" * 64, "the PR's entry object is not mutated")
        self.assertEqual((self.dir / rr.DATA / rr.VS).read_text(encoding="utf-8"), rr.dump(self.read()))

    def test_a_review_the_upgrade_invalidated_is_refused_and_nothing_written(self):
        out = rr.apply(self.dir, {rr.VS: ("Armory Automaton", ENTRY)}, lambda e: "sdk-members-removed: Effects.May")
        self.assertFalse(out["ok"])
        self.assertIn("sdk-members-removed", out["reason"])
        self.assertEqual(self.read(), BASE)

    def test_non_canonical_base_is_refused(self):
        (self.dir / rr.DATA / rr.VS).write_text(json.dumps(BASE), encoding="utf-8")
        self.assertFalse(rr.apply(self.dir, {rr.VS: ("Armory Automaton", ENTRY)}, lambda e: "current")["ok"])

    def test_already_on_base_changes_nothing(self):
        (self.dir / rr.DATA / rr.VS).write_text(rr.dump(head(**{"Armory Automaton": ENTRY})), encoding="utf-8")
        self.assertEqual(rr.apply(self.dir, {rr.VS: ("Armory Automaton", ENTRY)}, lambda e: "current")["changed"], [])


if __name__ == "__main__":
    unittest.main()
