"""End-to-end test of the hand-off path in engine-upgrade/weekly.py, on throwaway git repositories.

Run: python3 -m unittest -v engine-upgrade/tests/test_weekly_handoff.py

Covers what a person or agent (Claude, or GPT covering) does with a red weekly run:
  * conflict: `rebase --keep-conflict` leaves it in place, they resolve it, `finish` rewrites the patch and
    records the resolution, and the next automatic rebase replays that resolution with no help;
  * build fix: `checkout`, commit a `--fixup`, `finish` folds it into the owning patch;
  * fix branches: with WEEKLY_FORCE=1 a branch already on the target is validated as-is.
"""
from __future__ import annotations

import json
import os
import shutil
import subprocess
import tempfile
import textwrap
import unittest
from pathlib import Path

SCRIPT = Path(__file__).resolve().parents[1] / "weekly.py"


def sh(*args, cwd, check=True, env=None):
    result = subprocess.run(args, cwd=cwd, text=True, capture_output=True, env=env)
    if check and result.returncode:
        raise AssertionError(f"{args} -> {result.returncode}\n{result.stdout}\n{result.stderr}")
    return result


class WeeklyHandoff(unittest.TestCase):
    def setUp(self):
        self.tmp = Path(tempfile.mkdtemp())
        self.repo, self.engine = self.tmp / "arcana", self.tmp / "engine"
        (self.repo / "engine-upgrade/rerere").mkdir(parents=True)
        (self.repo / "argentum-patches").mkdir()
        shutil.copy(SCRIPT, self.repo / "engine-upgrade/weekly.py")
        self.env = {**os.environ, "PYTHONDONTWRITEBYTECODE": "1", "GIT_CONFIG_GLOBAL": str(self.tmp / "gitconfig")}
        (self.tmp / "gitconfig").write_text("[user]\n\tname = t\n\temail = t@t\n[init]\n\tdefaultBranch = main\n")

        # Upstream: pin A, then B edits the same line the Arcana patch edits, plus an unrelated file.
        self.engine.mkdir()
        self.git("init", "-q")
        (self.engine / "f.txt").write_text("one\ntwo\nthree\n")
        self.git("add", "."), self.git("commit", "-qm", "A")
        self.pin = self.git("rev-parse", "HEAD").stdout.strip()
        (self.engine / "f.txt").write_text("one\ntwo arcana\nthree\n")
        patch = self.git("diff").stdout
        self.git("checkout", "-q", "--", ".")
        (self.repo / "argentum-patches/0001-two.patch").write_text(patch)
        (self.engine / "f.txt").write_text("one\ntwo upstream\nthree\n")
        (self.engine / "g.txt").write_text("new upstream file\n")
        self.git("add", "."), self.git("commit", "-qm", "B")
        self.target = self.git("rev-parse", "HEAD").stdout.strip()
        self.set_pin(self.pin)

    def tearDown(self):
        shutil.rmtree(self.tmp, ignore_errors=True)

    def git(self, *args, check=True):
        return sh("git", *args, cwd=self.engine, check=check, env=self.env)

    def weekly(self, *args, check=True, extra=None):
        return sh("python3", "engine-upgrade/weekly.py", *args, cwd=self.repo, check=check, env={**self.env, **(extra or {})})

    def set_pin(self, pin):
        (self.repo / "engine-upgrade/audit_patches.py").write_text(textwrap.dedent(f'''\
            TARGET = "{pin}"
            MANDATORY = [
                "0001-two.patch",
            ]
            EXPERIMENTAL = "none.patch"
            '''))
        (self.repo / "engine-upgrade/preview-config.json").write_text(json.dumps({"upstream": pin}))

    def report(self):
        return json.loads((self.repo / "engine-upgrade/weekly/rebase.json").read_text())

    def test_robot_aborts_on_conflict(self):
        result = self.weekly("rebase", str(self.engine), self.target, check=False)
        self.assertEqual(result.returncode, 2)
        self.assertEqual(self.report()["status"], "conflict")
        self.assertEqual(self.report()["conflicts"][0]["file"], "f.txt")
        self.assertFalse((self.engine / ".git/rebase-merge").exists())

    def resolve_by_hand(self):
        result = self.weekly("rebase", str(self.engine), self.target, "--keep-conflict", check=False)
        self.assertEqual(result.returncode, 2)
        self.assertTrue((self.engine / ".git/rebase-merge").exists(), "conflict must be left in place")
        self.assertIn("finish", result.stdout)
        refused = self.weekly("finish", str(self.engine), check=False)
        self.assertNotEqual(refused.returncode, 0, "finish must refuse while the rebase is in progress")
        (self.engine / "f.txt").write_text("one\ntwo upstream arcana\nthree\n")
        self.git("add", "f.txt")
        sh("git", "-c", "core.editor=true", "rebase", "--continue", cwd=self.engine, env=self.env)
        self.weekly("finish", str(self.engine))

    def test_hand_resolution_rewrites_patch_and_pins(self):
        self.resolve_by_hand()
        patch = (self.repo / "argentum-patches/0001-two.patch").read_text()
        self.assertIn("+two upstream arcana", patch)
        self.assertIn("-two upstream", patch)
        self.assertIn(f'TARGET = "{self.target}"', (self.repo / "engine-upgrade/audit_patches.py").read_text())
        self.assertEqual(json.loads((self.repo / "engine-upgrade/preview-config.json").read_text())["upstream"], self.target)
        recorded = list((self.repo / "engine-upgrade/rerere").iterdir())
        self.assertEqual(len(recorded), 1)
        self.assertTrue((recorded[0] / "postimage").is_file())
        # The refreshed patch applies exactly to the target.
        self.git("checkout", "-q", "-f", self.target)
        self.git("apply", "--check", str(self.repo / "argentum-patches/0001-two.patch"))

    def test_next_robot_run_replays_the_recorded_resolution(self):
        self.resolve_by_hand()
        resolved = (self.repo / "argentum-patches/0001-two.patch").read_text()
        # Back to the old pin and the original patch, as development would be until the fix merges,
        # but keeping the recorded resolution.
        original = self.tmp / "orig.patch"
        self.git("checkout", "-q", "-f", self.pin)
        (self.engine / "f.txt").write_text("one\ntwo arcana\nthree\n")
        original.write_text(self.git("diff").stdout)
        self.git("checkout", "-q", "--", ".")
        shutil.copy(original, self.repo / "argentum-patches/0001-two.patch")
        self.set_pin(self.pin)
        shutil.rmtree(self.engine / ".git/rr-cache")
        self.weekly("rebase", str(self.engine), self.target)
        self.assertEqual(self.report()["status"], "rebased")
        self.assertEqual((self.repo / "argentum-patches/0001-two.patch").read_text(), resolved)

    def test_checkout_fixup_finish_folds_fix_into_owning_patch(self):
        self.resolve_by_hand()  # now pinned on the target
        self.weekly("checkout", str(self.engine))
        owner = self.git("log", "--format=%H", "-1", "--grep", "arcana: 0001-two").stdout.strip()
        (self.engine / "f.txt").write_text("one\ntwo upstream arcana fixed\nthree\n")
        self.git("commit", "-qa", "--fixup", owner)
        self.weekly("finish", str(self.engine))
        self.assertIn("+two upstream arcana fixed", (self.repo / "argentum-patches/0001-two.patch").read_text())
        subjects = self.git("log", "--format=%s", f"{self.target}..HEAD").stdout.split("\n")
        self.assertEqual([s for s in subjects if s], ["arcana: 0001-two"])

    def test_finish_refuses_commits_that_belong_to_no_patch(self):
        self.resolve_by_hand()
        self.weekly("checkout", str(self.engine))
        (self.engine / "f.txt").write_text("stray\n")
        self.git("commit", "-qam", "stray change")
        self.assertNotEqual(self.weekly("finish", str(self.engine), check=False).returncode, 0)

    def test_fix_branch_on_target_is_validated_with_force(self):
        self.resolve_by_hand()
        self.weekly("rebase", str(self.engine), self.target)
        self.assertEqual(self.report()["status"], "up_to_date")
        self.weekly("rebase", str(self.engine), self.target, extra={"WEEKLY_FORCE": "1"})
        report = self.report()
        self.assertEqual(report["status"], "rebased")
        self.assertEqual(report["patches"], [{"patch": "0001-two.patch", "status": "unchanged"}])

    def test_handoff_notes_name_the_right_starting_point(self):
        for kind, start in (("conflict", "origin/gpt/arcana-integration-next"),
                            ("build_failed", "origin/bot/argentum-weekly"),
                            ("needs_work", "origin/bot/argentum-weekly")):
            self.weekly("handoff", kind, self.target)
            notes = (self.repo / "engine-upgrade/weekly/handoff.md").read_text()
            self.assertIn(start, notes)
            self.assertIn(self.target, notes)
            self.assertIn("Never:", notes)


if __name__ == "__main__":
    unittest.main()
