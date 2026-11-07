"""Tests for `weekly.py docs`: re-vendor upstream DSL docs and move the stated pin.

Run: python3 -m unittest -v engine-upgrade/tests/test_weekly_docs.py
"""
from __future__ import annotations

import contextlib
import importlib.util
import io
import json
import subprocess
import tempfile
import unittest
from pathlib import Path

HERE = Path(__file__).resolve().parents[1]
ROOT = HERE.parent


def load_weekly():
    spec = importlib.util.spec_from_file_location("weekly_under_test", HERE / "weekly.py")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def git(cwd, *args):
    return subprocess.run(["git", *args], cwd=cwd, check=True, capture_output=True, text=True).stdout


class RealRepoPins(unittest.TestCase):
    """The files `docs` rewrites must state today's pin, or the next upgrade cannot move it."""

    def test_every_pin_file_states_the_current_pin(self):
        weekly = load_weekly()
        pin = json.loads((ROOT / "engine-upgrade/preview-config.json").read_text())["upstream"]
        for rel in weekly.PIN_FILES:
            text = (ROOT / rel).read_text()
            self.assertTrue(pin in text or weekly.short_pin(pin) in text, f"{rel} does not state {pin[:10]}")

    def test_vendored_files_exist(self):
        weekly = load_weekly()
        for name in weekly.VENDOR_FILES:
            self.assertTrue((ROOT / "docs/agents/argentum-upstream" / name).is_file(), name)


class DocsCommand(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        base = Path(self.tmp.name)
        self.root, self.engine = base / "arcana", base / "engine"
        self.engine.mkdir()
        git(self.engine, "init", "-q")
        git(self.engine, "config", "user.email", "t@example.com")
        git(self.engine, "config", "user.name", "t")
        self.weekly = load_weekly()
        for upstream in self.weekly.VENDOR_FILES.values():
            path = self.engine / upstream
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(f"new upstream {upstream}\n")
        git(self.engine, "add", "-A")
        git(self.engine, "commit", "-qm", "upstream")
        self.new = git(self.engine, "rev-parse", "HEAD").strip()
        self.old = "a" * 40
        w = self.weekly
        w.ROOT, w.OUT, w.VENDORED = self.root, self.root / "engine-upgrade/weekly", self.root / "docs/agents/argentum-upstream"
        w.OUT.mkdir(parents=True)
        w.VENDORED.mkdir(parents=True)
        (w.OUT / "rebase.json").write_text(json.dumps({"old_pin": self.old, "target": self.new}))
        for rel in w.PIN_FILES:
            (self.root / rel).parent.mkdir(parents=True, exist_ok=True)
            (self.root / rel).write_text(f"pin {self.old} short {w.short_pin(self.old)} other aaaaaaa\n")

    def tearDown(self):
        self.tmp.cleanup()

    def docs(self):
        # docs() prints ::error annotations on failure; keep them out of the real workflow log.
        with contextlib.redirect_stdout(io.StringIO()):
            return self.weekly.docs(str(self.engine))

    def test_moves_pins_and_revendors(self):
        self.assertEqual(self.docs(), 0)
        for rel in self.weekly.PIN_FILES:
            self.assertEqual((self.root / rel).read_text(),
                             f"pin {self.new} short {self.weekly.short_pin(self.new)} other aaaaaaa\n")
        for name, upstream in self.weekly.VENDOR_FILES.items():
            self.assertEqual((self.weekly.VENDORED / name).read_text(), f"new upstream {upstream}\n")
        report = json.loads((self.weekly.OUT / "docs.json").read_text())
        self.assertEqual((report["missing_upstream_docs"], report["pin_not_found"]), ([], []))

    def test_fails_when_upstream_dropped_a_vendored_doc(self):
        git(self.engine, "rm", "-q", "docs/sdk-index.md")
        git(self.engine, "commit", "-qm", "drop")
        new = git(self.engine, "rev-parse", "HEAD").strip()
        (self.weekly.OUT / "rebase.json").write_text(json.dumps({"old_pin": self.old, "target": new}))
        self.assertEqual(self.docs(), 1)
        self.assertIn("docs/sdk-index.md", json.loads((self.weekly.OUT / "docs.json").read_text())["missing_upstream_docs"])

    def test_fails_when_a_file_no_longer_states_the_pin(self):
        (self.root / "AGENTS.md").write_text("no pin here\n")
        self.assertEqual(self.docs(), 1)
        self.assertEqual(json.loads((self.weekly.OUT / "docs.json").read_text())["pin_not_found"], ["AGENTS.md"])


if __name__ == "__main__":
    unittest.main()
