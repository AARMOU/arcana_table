"""Regression tests for the deliberately advisory upstream Assay bridge."""
from __future__ import annotations

import contextlib
import io
import json
import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import cardkit  # noqa: E402
from cardkit_lib import assay, repo  # noqa: E402


def ledger_file(root: Path, rows: list[dict], *, whole: int | None = None) -> Path:
    path = root / "assay-verdicts.json"
    data = {
        "corpus": len(rows),
        "whole": sum(row.get("kind") is None for row in rows) if whole is None else whole,
        "cards": rows,
    }
    path.write_text(json.dumps(data), encoding="utf-8")
    return path


class AssayLedgerTests(unittest.TestCase):
    def test_valid_upstream_bake_schema_and_normalized_lookup(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = ledger_file(Path(tmp), [
                {"name": "Ugin, the Ineffable"},
                {"name": "Eldrazi Temple", "kind": "UNREADABLE", "line": "Add {C}{C}."},
            ])
            data = assay.load_ledger(path)
        self.assertIsNone(data["ugin, the ineffable"]["kind"])
        self.assertEqual(data["eldrazi temple"]["kind"], "UNREADABLE")

    def test_rejects_bad_counts_and_duplicate_cards(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = ledger_file(Path(tmp), [{"name": "One"}], whole=0)
            with self.assertRaisesRegex(assay.InvalidLedger, "whole count"):
                assay.load_ledger(path)
            path = ledger_file(Path(tmp), [{"name": "One"}, {"name": "one"}])
            with self.assertRaisesRegex(assay.InvalidLedger, "duplicate"):
                assay.load_ledger(path)

    def test_rejects_invalid_row_fields_and_missing_input(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = ledger_file(Path(tmp), [{"name": "X", "kind": "", "line": "foo"}], whole=0)
            with self.assertRaises(assay.InvalidLedger):
                assay.load_ledger(path)
            path = ledger_file(Path(tmp), [{"name": "X", "line": "foo"}])
            with self.assertRaises(assay.InvalidLedger):
                assay.load_ledger(path)
            with self.assertRaises(assay.InvalidLedger):
                assay.load_ledger(Path(tmp) / "missing.json")

    def test_whole_is_advisory_never_promotes_review_or_dispatch(self):
        compiled = {"name": "Ugin, the Ineffable", "kind": None, "line": None}
        with mock.patch.object(assay.readiness, "classify", return_value={"readiness": "NEEDS_REVIEW"}):
            report = assay.review({"ugin, the ineffable": compiled}, ["Ugin, the Ineffable"],
                                  "a" * 40)
        self.assertEqual(report["totals"]["reviewCandidates"], 1)
        self.assertEqual(report["cards"][0]["cardkit"], "NEEDS_REVIEW")
        self.assertTrue(report["cards"][0]["upstreamReviewCandidate"])
        self.assertFalse(report["cards"][0]["pinnedEngineVerified"])
        self.assertFalse(report["dispatchEligibilityChanged"])
        self.assertFalse(report["sourceCommitMatchesPin"])
        self.assertFalse(report["ledgerCommitAuthenticated"])

    def test_decline_does_not_create_engine_blocker(self):
        row = {"name": "Forsaken Monument", "kind": "GRAMMAR_DECLINE", "line": "Colorless"}
        with mock.patch.object(assay.readiness, "classify", return_value={"readiness": "SUPPORTED"}):
            report = assay.review({"forsaken monument": row}, ["Forsaken Monument", "Forsaken Monument"],
                                  repo.UPSTREAM_COMMIT)
        self.assertEqual(len(report["cards"]), 1)
        self.assertEqual(report["cards"][0]["cardkit"], "SUPPORTED")
        self.assertEqual(report["cards"][0]["assay"], "declined")
        self.assertTrue(report["sourceCommitMatchesPin"])
        self.assertFalse(report["ledgerCommitAuthenticated"])
        self.assertEqual(report["totals"]["declined"], 1)

    def test_invalid_source_sha_is_rejected(self):
        with self.assertRaisesRegex(ValueError, "40-character"):
            assay.review({}, [], "main")

    def test_deck_selection_skips_implemented_by_default(self):
        decks = [{"cards": [{"name": "Sol Ring"}, {"name": "Forsaken Monument"},
                             {"name": "forsaken monument"}]}]
        def impl(name):
            return ("IMPLEMENTED_AND_TESTED" if name == "Sol Ring" else None, [], None)
        with mock.patch.object(assay.readiness, "implementation_state", side_effect=impl):
            self.assertEqual(assay.names_from_decks(decks), ["Forsaken Monument"])
            self.assertEqual(assay.names_from_decks(decks, include_implemented=True),
                             ["Sol Ring", "Forsaken Monument"])

    def test_cli_reports_source_provenance_and_json_without_reclassifying(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            ledger = ledger_file(root, [{"name": "Ugin, the Ineffable"}])
            target = root / "report.json"
            with mock.patch.object(assay.readiness, "classify", return_value={"readiness": "NEEDS_REVIEW"}), \
                    contextlib.redirect_stdout(io.StringIO()) as output:
                code = cardkit.main(["assay-review", "--ledger", str(ledger), "--assay-commit",
                                     "f" * 40, "--card", "Ugin, the Ineffable",
                                     "--json-output", str(target)])
            self.assertEqual(code, 0)
            self.assertIn("Advisory only", output.getvalue())
            data = json.loads(target.read_text())
            self.assertEqual(data["cards"][0]["assay"], "whole")
            self.assertFalse(data["dispatchEligibilityChanged"])

    def test_cli_rejects_untrusted_or_stale_format(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "bad.json"
            path.write_text('{"cards":[],"whole":0,"corpus":1}', encoding="utf-8")
            with contextlib.redirect_stderr(io.StringIO()) as errors:
                code = cardkit.main(["assay-review", "--ledger", str(path),
                                     "--assay-commit", "a" * 40, "--card", "X"])
            self.assertEqual(code, 2)
            self.assertIn("refused", errors.getvalue())


if __name__ == "__main__":
    unittest.main()
