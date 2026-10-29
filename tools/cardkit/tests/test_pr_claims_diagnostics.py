"""Regression coverage for fail-closed GitHub open-PR claim diagnostics.

No live GitHub API calls or card/dispatcher changes.
"""
from __future__ import annotations

import subprocess
import sys
import unittest
from pathlib import Path
from unittest import mock

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
from cardkit_lib import gaps  # noqa: E402


class OpenPrClaimsDiagnosticsTests(unittest.TestCase):
    def test_success_preserves_open_pr_claims(self):
        payload = '{"number":419,"title":"Sporocyst","branch":"copilot/card","body":"owned"}\\n'
        with mock.patch("subprocess.run", return_value=subprocess.CompletedProcess([], 0, stdout=payload)):
            self.assertEqual(gaps.open_pr_claims(), [
                {"number": 419, "title": "Sporocyst", "branch": "copilot/card", "body": "owned"}
            ])

    def test_permission_failure_reports_status_and_never_raw_stderr(self):
        err = subprocess.CalledProcessError(
            1, ["gh"], stderr="gh: Resource not accessible by integration (HTTP 403) SECRET_TOKEN=do-not-log"
        )
        with mock.patch("subprocess.run", side_effect=err):
            with self.assertRaises(gaps.ClaimsUnavailable) as ctx:
                gaps.open_pr_claims()
        message = str(ctx.exception)
        self.assertIn("exit 1; HTTP 403; category=integration-permission", message)
        self.assertIn("claims unverified", message)
        self.assertNotIn("SECRET_TOKEN", message)
        self.assertNotIn("do-not-log", message)

    def test_rate_limit_is_classified_without_leaking_stderr(self):
        err = subprocess.CalledProcessError(
            1, ["gh"], stderr="gh: API rate limit exceeded (HTTP 429) PRIVATE=do-not-log"
        )
        with mock.patch("subprocess.run", side_effect=err):
            with self.assertRaises(gaps.ClaimsUnavailable) as ctx:
                gaps.open_pr_claims()
        self.assertIn("HTTP 429; category=rate-limit", str(ctx.exception))
        self.assertNotIn("PRIVATE", str(ctx.exception))

    def test_jq_error_is_classified_and_still_fails_closed(self):
        err = subprocess.CalledProcessError(1, ["gh"], stderr="jq: compile error: secret")
        with mock.patch("subprocess.run", side_effect=err):
            with self.assertRaises(gaps.ClaimsUnavailable) as ctx:
                gaps.open_pr_claims()
        self.assertIn("category=jq-filter", str(ctx.exception))
        self.assertNotIn("secret", str(ctx.exception))

    def test_timeout_is_fail_closed(self):
        with mock.patch("subprocess.run", side_effect=subprocess.TimeoutExpired(["gh"], 120)):
            with self.assertRaises(gaps.ClaimsUnavailable) as ctx:
                gaps.open_pr_claims()
        self.assertIn("timed out; claims unverified", str(ctx.exception))

    def test_missing_gh_is_fail_closed(self):
        with mock.patch("subprocess.run", side_effect=FileNotFoundError("sensitive-path")):
            with self.assertRaises(gaps.ClaimsUnavailable) as ctx:
                gaps.open_pr_claims()
        self.assertIn("execution unavailable (FileNotFoundError)", str(ctx.exception))
        self.assertNotIn("sensitive-path", str(ctx.exception))

    def test_malformed_json_remains_fail_closed(self):
        with mock.patch("subprocess.run", return_value=subprocess.CompletedProcess([], 0, stdout="{broken\\n")):
            with self.assertRaises(gaps.ClaimsUnavailable):
                gaps.open_pr_claims()


if __name__ == "__main__":
    unittest.main()
