"""No-APK tests for the already-assembled Claude B137 -> Turbo bridge."""
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import b137_preassembled_turbo as bridge

class B137BridgeTests(unittest.TestCase):
    def test_source_guard_follows_successful_real_b137_commit(self):
        self.assertEqual(bridge.VERIFIED_B137_SOURCE,
                         "3f8228e61d1d38d1c82f73244ce30ce303ac2198")
        self.assertEqual(bridge.VERIFIED_B137_RUN, "40430360039")
        self.assertIn("android", bridge.PROTECTED)
        self.assertIn("engine-upgrade", bridge.PROTECTED)
        self.assertIn("argentum-patches", bridge.PROTECTED)

    def test_rejects_modified_compiled_source(self):
        with patch.object(bridge.subprocess, "run"), patch.object(
            bridge.subprocess, "check_output", return_value="android/app/src/main/AndroidManifest.xml\n"
        ):
            with self.assertRaisesRegex(ValueError, "compiled inputs changed"):
                bridge.check()

    def test_rejects_missing_source_marker(self):
        with patch.object(bridge.subprocess, "run"), patch.object(
            bridge.subprocess, "check_output", return_value=""
        ), patch.object(bridge.Path, "is_file", return_value=False):
            with self.assertRaisesRegex(ValueError, "gameplay/tutorial marker"):
                bridge.check()

    def test_manual_workflow_never_rebuilds_b136_source(self):
        workflow = Path(".github/workflows/fast-test-apk-manual.yml").read_text()
        self.assertIn("tools/b137_preassembled_turbo.py verify", workflow)
        self.assertNotIn("tools/fast_apk_snapshot.py source", workflow)
        self.assertIn("tools/fast_apk_snapshot.py fingerprint", workflow)
        self.assertIn("tools/fast_apk_snapshot.py restore", workflow)
        self.assertIn("tools/fast_apk_snapshot.py engine", workflow)
        self.assertIn("tools/fast_apk_snapshot.py cards", workflow)
        self.assertIn("arcana-fast-engine-b137-claude-pinned", workflow)
        self.assertIn("r.get('head_branch') == os.environ['GITHUB_REF_NAME']", workflow)
        self.assertIn("data['seed_commit'] == r['head_sha']", workflow)
        events = workflow.split("\non:\n", 1)[1].split("\npermissions:\n", 1)[0]
        self.assertIn("workflow_dispatch:", events)
        self.assertNotIn("push:", events)
        self.assertNotIn("schedule:", events)

if __name__ == "__main__":
    unittest.main()
