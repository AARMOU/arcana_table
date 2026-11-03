"""A patch that does not apply on the pinned engine is reported as a conflict naming it, not a crash
(2026-11-03: B141's 0036 was made on B141's older engine; the run died with no rebase.json)."""

import importlib.util
import json
import subprocess
import tempfile
import unittest
from pathlib import Path
from unittest import mock

SPEC = importlib.util.spec_from_file_location("weekly", Path(__file__).resolve().parents[1] / "weekly.py")
weekly = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(weekly)


class UnappliedPatch(unittest.TestCase):
    def setUp(self):
        self.tmp = Path(tempfile.mkdtemp())
        self.engine = self.tmp / "engine"
        self.engine.mkdir()
        git = lambda *a: subprocess.run(["git", *a], cwd=self.engine, check=True, capture_output=True, text=True)
        git("init", "-q", "-b", "main"); git("config", "user.email", "t@example.com"); git("config", "user.name", "t")
        (self.engine / "docs").mkdir()
        (self.engine / "docs" / "ref.md").write_text("one\ntwo\nthree\n")
        git("add", "-A"); git("commit", "-qm", "upstream")
        self.pin = git("rev-parse", "HEAD").stdout.strip()
        patches = self.tmp / "patches"
        patches.mkdir()
        (patches / "0001-good.patch").write_text(
            "diff --git a/new.txt b/new.txt\nnew file mode 100644\n--- /dev/null\n+++ b/new.txt\n@@ -0,0 +1 @@\n+hello\n")
        (patches / "0002-other-engine.patch").write_text(  # its context is not what this engine has
            "diff --git a/docs/ref.md b/docs/ref.md\n--- a/docs/ref.md\n+++ b/docs/ref.md\n"
            "@@ -1,3 +1,3 @@\n one\n-TWO ON ANOTHER ENGINE\n+2\n three\n")
        self.patches = patches
        self.out = self.tmp / "weekly"

    def patched(self):
        return mock.patch.multiple(weekly, PATCHES=self.patches, FORWARD=self.tmp / "none", OUT=self.out,
                                   RERERE=self.tmp / "no-rerere")

    def test_prepare_names_the_patch_and_file(self):
        with self.patched():
            with self.assertRaises(weekly.PatchDoesNotApply) as caught:
                weekly.prepare(self.engine, self.pin, ["0001-good.patch", "0002-other-engine.patch"])
        self.assertEqual("0002-other-engine.patch", caught.exception.name)
        self.assertEqual(["docs/ref.md"], caught.exception.files)

    def test_rebase_reports_a_conflict_instead_of_crashing(self):
        report = {"old_pin": self.pin, "target": self.pin, "status": "pending", "patches": [], "conflicts": []}
        with self.patched():
            try:
                weekly.prepare(self.engine, self.pin, ["0001-good.patch", "0002-other-engine.patch"])
            except weekly.PatchDoesNotApply as e:
                code = weekly.report_unapplied(report, e)
        self.assertEqual(2, code)
        saved = json.loads((self.out / "rebase.json").read_text())
        self.assertEqual("conflict", saved["status"])
        self.assertEqual([("0002-other-engine.patch", "docs/ref.md", "does_not_apply_on_pin")],
                         [(c["patch"], c["file"], c["kind"]) for c in saved["conflicts"]])
        self.assertIn("does not apply even on the pinned engine", (self.out / "rebase.md").read_text())


if __name__ == "__main__":
    unittest.main()
