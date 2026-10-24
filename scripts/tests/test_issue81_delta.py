"""Offline tests for the Issue #81 read-only, bounded notes reader.

Run: python3 -m unittest discover -s scripts/tests -p 'test_issue81_delta.py'
"""
import contextlib
import importlib.util
import io
import sys
import unittest
from pathlib import Path
from unittest.mock import patch
from urllib.parse import parse_qs, urlparse

SOURCE = Path(__file__).resolve().parents[1] / "issue81_delta.py"
spec = importlib.util.spec_from_file_location("issue81_delta", SOURCE)
delta = importlib.util.module_from_spec(spec)
spec.loader.exec_module(delta)


class TestIssue81Delta(unittest.TestCase):
    def test_snapshot_marker(self):
        self.assertGreater(delta.read_marker(), 0)

    def test_empty_delta(self):
        def api(url):
            if "/issues/comments/" in url:
                return {"id": 10, "created_at": "2026-10-24T06:00:00Z"}
            self.assertIn("since=2026-10-24T05%3A59%3A00Z", url)
            return []

        with patch.object(delta, "get_json", side_effect=api):
            self.assertEqual(delta.collect_comments(10), [])

    def test_filters_and_orders_new_comments(self):
        def api(url):
            if "/issues/comments/" in url:
                return {"id": 10, "created_at": "2026-10-24T06:00:00Z"}
            return [{"id": 7}, {"id": 14}, {"id": 12}]

        with patch.object(delta, "get_json", side_effect=api):
            comments = delta.collect_comments(10)
        self.assertEqual([item["id"] for item in comments], [12, 14])

    def test_multiple_pages(self):
        calls = []

        def api(url):
            calls.append(url)
            if "/issues/comments/" in url:
                return {"id": 10, "created_at": "2026-10-24T06:00:00Z"}
            page = parse_qs(urlparse(url).query)["page"][0]
            if page == "1":
                return [{"id": i} for i in range(1, 101)]
            return [{"id": 102}, {"id": 101}]

        with patch.object(delta, "get_json", side_effect=api):
            comments = delta.collect_comments(10)
        self.assertEqual(len(comments), 92)
        self.assertEqual((comments[0]["id"], comments[-1]["id"]), (11, 102))
        self.assertEqual(len(calls), 3)

    def test_marker_mismatch_fails_closed(self):
        with patch.object(delta, "get_json", return_value={"id": 777}):
            with self.assertRaisesRegex(ValueError, "not confirmed"):
                delta.collect_comments(10)

    def test_bad_api_collection_fails_closed(self):
        with patch.object(delta, "get_json", side_effect=[
            {"id": 10, "created_at": "2026-10-24T06:00:00Z"}, {}
        ]):
            with self.assertRaisesRegex(ValueError, "non-list"):
                delta.collect_comments(10)

    def test_summary_and_full_output(self):
        items = [
            {"id": 11, "created_at": "2026-10-24T06:17:00Z", "body": "## Note\n" + "A" * 700},
            {"id": 12, "created_at": "2026-10-24T06:25:00Z", "body": "## Handoff\nDetails"},
        ]
        for args, expected, excluded in [
            (["issue81_delta.py", "--limit", "1"], "NOT SHOWN", "A" * 700),
            (["issue81_delta.py", "--full", "--limit", "3"], "A" * 700, "NOT SHOWN"),
        ]:
            with patch.object(delta, "collect_comments", return_value=items), patch.object(sys, "argv", args):
                output = io.StringIO()
                with contextlib.redirect_stdout(output):
                    delta.main()
                self.assertIn(expected, output.getvalue())
                self.assertNotIn(excluded, output.getvalue())

    def test_invalid_limit(self):
        with patch.object(sys, "argv", ["issue81_delta.py", "--limit", "0"]):
            with contextlib.redirect_stderr(io.StringIO()):
                with self.assertRaises(SystemExit) as caught:
                    delta.main()
        self.assertEqual(caught.exception.code, 2)


if __name__ == "__main__":
    unittest.main()
