"""Safety invariants for .github/workflows/argentum-weekly-upgrade.yml.

Run: python3 -m unittest -v engine-upgrade/tests/test_weekly_workflow.py   (needs PyYAML)

The workflow builds upstream Argentum, i.e. runs third-party build scripts. These tests pin the trust
boundary so it cannot quietly regress:
  * the job that runs upstream code holds a read-only token and stores no credentials, so it cannot
    push, open PRs or edit issues, on a dry run or otherwise;
  * the job that can write never checks out or runs upstream code and never runs on a dry run;
  * the upstream engine is checked out by exact commit, never a moving branch.
"""
from __future__ import annotations

import json
import re
import unittest
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parents[2]
WORKFLOW = ROOT / ".github/workflows/argentum-weekly-upgrade.yml"
WRITE_COMMANDS = re.compile(r"\bgit\s+push\b|\bgh\s+(pr|issue|api|release|workflow)\b|\bcurl\b.*-X\s*(POST|PUT|PATCH|DELETE)")
UPSTREAM_CODE = re.compile(r"gradle|wingedsheep|argentum-engine|weekly\.py\s+(rebase|install)|audit_patches|setup-java|setup-android")


def load():
    data = yaml.safe_load(WORKFLOW.read_text())
    data["on"] = data.pop(True, data.get("on"))  # YAML 1.1 reads the key `on` as True
    return data


def text(node) -> str:
    return json.dumps(node)


class WeeklyWorkflowSafety(unittest.TestCase):
    def setUp(self):
        self.wf = load()
        self.jobs = self.wf["jobs"]
        self.validate = self.jobs["validate"]
        self.publish = self.jobs["publish"]

    def test_default_token_has_no_permissions(self):
        self.assertEqual(self.wf.get("permissions"), {})

    def test_only_two_jobs(self):
        self.assertEqual(set(self.jobs), {"validate", "publish"})

    def test_validate_token_is_read_only(self):
        self.assertEqual(self.validate["permissions"], {"contents": "read"})

    def test_validate_checkouts_store_no_credentials(self):
        checkouts = [s for s in self.validate["steps"] if str(s.get("uses", "")).startswith("actions/checkout")]
        self.assertGreaterEqual(len(checkouts), 2)
        for step in checkouts:
            self.assertIs(step.get("with", {}).get("persist-credentials"), False, step)

    def test_validate_never_writes_remotely(self):
        for step in self.validate["steps"]:
            self.assertIsNone(WRITE_COMMANDS.search(step.get("run", "")), step.get("name"))
            self.assertNotIn("GH_TOKEN", text(step.get("env", {})), step.get("name"))
            self.assertNotIn("github.token", text(step), step.get("name"))
            self.assertNotIn("secrets.", text(step), step.get("name"))

    def test_upstream_checked_out_by_exact_commit(self):
        upstream = [s for s in self.validate["steps"]
                    if s.get("with", {}).get("repository") == "wingedsheep/argentum-engine"]
        self.assertEqual(len(upstream), 1)
        self.assertEqual(upstream[0]["with"]["ref"], "${{ steps.target.outputs.sha }}")
        resolve = next(s for s in self.validate["steps"] if s.get("id") == "target")
        self.assertIn("[0-9a-f]{40}", resolve["run"])

    def test_publish_skipped_on_dry_run(self):
        self.assertIn("github.event_name != 'push'", self.publish["if"])
        self.assertEqual(self.publish["needs"], "validate")

    def test_publish_runs_no_upstream_code(self):
        for step in self.publish["steps"]:
            self.assertIsNone(UPSTREAM_CODE.search(text(step)), step.get("name") or step.get("uses"))
        checkout = next(s for s in self.publish["steps"] if str(s.get("uses", "")).startswith("actions/checkout"))
        self.assertNotIn("repository", checkout.get("with", {}))
        self.assertEqual(checkout["with"]["ref"], "${{ needs.validate.outputs.base_sha }}")

    def test_publish_verifies_candidate_before_applying(self):
        names = [s.get("name", "") for s in self.publish["steps"]]
        verify = names.index("Verify the candidate before using it")
        self.assertLess(verify, names.index("Push the upgrade branch"))
        run = self.publish["steps"][verify]["run"]
        self.assertIn("sha256sum -c", run)
        self.assertIn("outside the upgrade scope", run)
        self.assertNotIn(".github", run.split("allowed =")[1].split(")")[0])

    def test_only_publish_pushes(self):
        pushers = [(job, s.get("name")) for job, spec in self.jobs.items() for s in spec["steps"]
                   if re.search(r"\bgit\s+push\b", s.get("run", ""))]
        self.assertEqual(pushers, [("publish", "Push the upgrade branch")])


if __name__ == "__main__":
    unittest.main()
