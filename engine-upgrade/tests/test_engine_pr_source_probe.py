"""Pure-source regression tests for the read-only per-PR weekly.py rebase probe."""
from __future__ import annotations

import importlib.util
import os
import pathlib
import sys
import unittest
from unittest.mock import patch

SOURCE = pathlib.Path(__file__).resolve().parents[1] / "engine_pr_source_probe.py"
sys.path.insert(0, str(SOURCE.parent))
spec = importlib.util.spec_from_file_location("arcana_pr_source_probe", SOURCE)
probe = importlib.util.module_from_spec(spec)
spec.loader.exec_module(probe)

OLD = "1" * 40
NEW = "2" * 40
BASELINE = f'TARGET = "{OLD}"\nMANDATORY = [\n    "0001-base.patch",\n]\nEXPERIMENTAL = "0010-experiment.patch"\n'


class SourceProbePolicy(unittest.TestCase):
    def test_preserves_order_of_untrusted_patch_names_but_never_executes_pr_audit(self):
        attacker = f'TARGET = "{NEW}"\nMANDATORY = [\n    "0001-base.patch",\n    "0011-stack-object-copying.patch",\n]\nraise RuntimeError("should never execute")\n'
        result = probe.materialize_audit(BASELINE, attacker)
        self.assertIn(f'TARGET = "{NEW}"', result)
        self.assertLess(result.index('"0001-base.patch"'), result.index('"0011-stack-object-copying.patch"'))
        self.assertNotIn("should never execute", result)
        self.assertIn("EXPERIMENTAL", result)

    def test_rejects_missing_duplicate_and_path_traversal_patches(self):
        for body in (
            f'TARGET = "{OLD}"\nMANDATORY = [\n]\n',
            f'TARGET = "{OLD}"\nMANDATORY = [\n    "0001-base.patch",\n    "0001-base.patch",\n]\n',
            f'TARGET = "{OLD}"\nMANDATORY = [\n    "0001-../../evil.patch",\n]\n',
            f'TARGET = "{OLD}"\nMANDATORY = [\n    "0001-path/escape.patch",\n]\n',
        ):
            with self.subTest(body=body), self.assertRaises(ValueError):
                probe.materialize_audit(BASELINE, body)

    def test_only_source_changing_patches_are_probed(self):
        rows = [
            {"pr": 1, "head_pin": OLD, "engine_paths": ["argentum-patches/0011-stack-object-copying.patch"]},
            {"pr": 2, "head_pin": OLD, "engine_paths": ["engine-upgrade/eldrazi-temple/patches/0001-mana.patch"]},
            {"pr": 3, "head_pin": OLD, "engine_paths": ["android/app/libs/argentum-ai.jar"]},
            {"pr": 4, "engine_paths": ["argentum-patches/0010-experiment.patch"]},
        ]
        self.assertEqual([r["pr"] for r in probe.candidates(rows, 2)], [1, 2])
        with self.assertRaisesRegex(ValueError, "refusing partial"):
            probe.candidates(rows[:2], 1)

    def test_sensitive_tokens_not_available_to_untrusted_patch_operation(self):
        with patch.dict(os.environ, {"GITHUB_TOKEN": "private",
                                     "ACTIONS_RUNTIME_TOKEN": "another",
                                     "API_PASSWORD": "unsafe",
                                     "PATH": "/usr/bin"},
                        clear=True):
            env = probe.safe_env()
            self.assertEqual(env["PATH"], "/usr/bin")
            self.assertNotIn("GITHUB_TOKEN", env)
            self.assertNotIn("ACTIONS_RUNTIME_TOKEN", env)
            self.assertNotIn("API_PASSWORD", env)
            self.assertEqual(env["GIT_TERMINAL_PROMPT"], "0")
            self.assertEqual(env["WEEKLY_FORCE"], "1")


if __name__ == "__main__":
    unittest.main()
