"""Executable tests for engine-upgrade/candidate_scope.py (the publish-side path/mode gate).

Run: python3 -m unittest -v engine-upgrade/tests/test_candidate_scope.py

Each test builds a throwaway repository, stages a change the way `git apply --index` would, and runs
the checker against it.
"""
from __future__ import annotations

import os
import re
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

OLD, NEW = "a" * 40, "b" * 40
CHECKER = Path(__file__).resolve().parents[1] / "candidate_scope.py"
AUDIT = '''"""audit"""
TARGET = "{pin}"
MANDATORY = [
{items}]
EXPERIMENTAL = "x.patch"


def main():
    return 0
'''


def audit(pin: str, names: list[str], extra: str = "") -> str:
    return AUDIT.format(pin=pin, items="".join(f'    "{n}",\n' for n in names)) + extra


class CandidateScope(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.repo = Path(self.tmp.name)
        self.git("init", "-q")
        self.git("config", "user.email", "t@example.com")
        self.git("config", "user.name", "t")
        self.write("argentum-patches/0001-a.patch", "old\n")
        self.write("engine-upgrade/forward-ports/0002-b.patch", "fp\n")
        self.write("engine-upgrade/audit_patches.py", audit("a" * 40, ["0001-a.patch"]))
        self.write("engine-upgrade/preview-config.json", '{"upstream": "%s"}\n' % OLD)
        self.write("AGENTS.md", f"Engine pinned at `{OLD}`, shipped as jars.\n")
        self.write("docs/agents/CARD-WORKFLOW.md", f"Upstream at the pinned commit (`{OLD[:7]}\u2026`).\n")
        self.write("tools/cardkit/cardkit_lib/repo.py", f'UPSTREAM_COMMIT = "{OLD}"\n')
        self.write("docs/agents/generated/sdk-api.md", "# SDK\n")
        self.write("docs/agents/argentum-upstream/sdk-index.md", "# idx\n")
        self.write("engine-upgrade/weekly.py", "print('trusted')\n")
        self.write("android/app/libs/argentum-rules-engine.jar", "jar\n")
        self.write("android/app/src/main/assets/argentum/cards.jsonl", "{}\n")
        self.write(".github/workflows/argentum-weekly-upgrade.yml", "on: push\n")
        self.git("add", "-A")
        self.git("commit", "-qm", "base")

    def tearDown(self):
        self.tmp.cleanup()

    def git(self, *args):
        return subprocess.run(["git", *args], cwd=self.repo, check=True, capture_output=True, text=True)

    def write(self, rel, content):
        path = self.repo / rel
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content)

    def check(self):
        result = subprocess.run([sys.executable, "-I", str(CHECKER)], cwd=self.repo, capture_output=True, text=True)
        return result.returncode, result.stdout + result.stderr

    def stage(self):
        self.git("add", "-A")

    def assertRejected(self, needle):
        code, out = self.check()
        self.assertEqual(code, 1, out)
        self.assertIn(needle, out)

    # --- what a real upgrade does: accepted ---------------------------------------------------
    def test_normal_upgrade_is_in_scope(self):
        self.write("argentum-patches/0001-a.patch", "refreshed\n")
        self.write("argentum-patches/0003-new.patch", "new\n")
        (self.repo / "engine-upgrade/forward-ports/0002-b.patch").unlink()
        self.write("engine-upgrade/audit_patches.py", audit("b" * 40, ["0001-a.patch", "0003-new.patch"]))
        self.write("engine-upgrade/preview-config.json", '{"upstream": "%s"}\n' % NEW)
        self.write("AGENTS.md", f"Engine pinned at `{NEW}`, shipped as jars.\n")
        self.write("docs/agents/CARD-WORKFLOW.md", f"Upstream at the pinned commit (`{NEW[:7]}\u2026`).\n")
        self.write("tools/cardkit/cardkit_lib/repo.py", f'UPSTREAM_COMMIT = "{NEW}"\n')
        self.write("docs/agents/generated/sdk-api.md", "# SDK v2\n")
        self.write("docs/agents/argentum-upstream/sdk-index.md", "# idx v2\n")
        self.write("android/app/libs/argentum-rules-engine.jar", "jar2\n")
        self.write("android/app/src/main/assets/argentum/cards.jsonl", "{\"a\": 1}\n")
        self.write("android/app/src/main/assets/argentum/new/index.tsv", "x\n")
        self.stage()
        code, out = self.check()
        self.assertEqual(code, 0, out)

    def test_empty_candidate_is_rejected(self):
        self.assertRejected("changes nothing")

    # --- anything else: rejected --------------------------------------------------------------
    def test_workflow_change_is_rejected(self):
        self.write(".github/workflows/argentum-weekly-upgrade.yml", "on: schedule\n")
        self.stage()
        self.assertRejected(".github/workflows/argentum-weekly-upgrade.yml: outside the upgrade scope")

    def test_automation_code_change_is_rejected(self):
        self.write("engine-upgrade/weekly.py", "print('tampered')\n")
        self.stage()
        self.assertRejected("engine-upgrade/weekly.py: outside the upgrade scope")

    def test_new_checker_or_tests_are_rejected(self):
        self.write("engine-upgrade/candidate_scope.py", "raise SystemExit(0)\n")
        self.write("engine-upgrade/tests/test_x.py", "\n")
        self.stage()
        self.assertRejected("engine-upgrade/candidate_scope.py: outside the upgrade scope")

    def test_path_with_spaces_cannot_hide(self):
        # `diff --git "a/x y" "b/x y"` headers defeated the old regex check; the index sees them.
        self.write("android/app/src/main/java/evil file.kt", "x\n")
        self.stage()
        self.assertRejected("outside the upgrade scope")

    def test_path_with_spaces_in_a_real_patch_is_caught(self):
        # Build the hostile patch with git itself, then apply it the way publish does.
        self.write("android/app/src/main/java/evil file.kt", "x\n")
        self.git("add", "-A")
        patch = self.git("diff", "--cached", "--binary").stdout
        # The pre-apply header regex used before this checker existed never sees this path.
        old_check = re.findall(r'^diff --git a/(\S+) b/(\S+)$', patch, re.M)
        self.assertEqual(old_check, [])
        self.git("reset", "-q", "--hard")
        (self.repo / "candidate.patch").write_text(patch)
        self.git("apply", "--index", "--binary", "candidate.patch")
        (self.repo / "candidate.patch").unlink()
        self.assertRejected("evil file.kt: outside the upgrade scope")

    def test_rename_out_of_scope_is_rejected(self):
        (self.repo / "android/app/src/main/java").mkdir(parents=True)
        self.git("mv", "argentum-patches/0001-a.patch", "android/app/src/main/java/a.patch")
        self.assertRejected("android/app/src/main/java/a.patch: outside the upgrade scope")

    def test_symlink_is_rejected(self):
        os.symlink("../../.github", self.repo / "android/app/src/main/assets/argentum/link")
        self.stage()
        self.assertRejected("mode 120000")

    def test_executable_bit_is_rejected(self):
        (self.repo / "argentum-patches/0001-a.patch").chmod(0o755)
        self.git("config", "core.fileMode", "true")
        self.stage()
        self.assertRejected("mode 100755")

    def test_forward_port_may_only_be_deleted(self):
        self.write("engine-upgrade/forward-ports/0002-b.patch", "changed\n")
        self.stage()
        self.assertRejected("change type M not allowed")

    def test_jar_outside_argentum_names_is_rejected(self):
        self.write("android/app/libs/other.jar", "x\n")
        self.stage()
        self.assertRejected("android/app/libs/other.jar: outside the upgrade scope")

    def test_audit_script_may_change_only_its_pins(self):
        self.write("engine-upgrade/audit_patches.py",
                   audit("b" * 40, ["0001-a.patch"], extra="\nimport os; os.system('id')\n"))
        self.stage()
        self.assertRejected("changes beyond the TARGET pin and MANDATORY list")


    def move_pin(self):
        self.write("engine-upgrade/preview-config.json", '{"upstream": "%s"}\n' % NEW)

    def test_pin_file_may_change_only_its_pin(self):
        self.move_pin()
        self.write("tools/cardkit/cardkit_lib/repo.py", f'UPSTREAM_COMMIT = "{NEW}"\nimport os\n')
        self.stage()
        self.assertRejected("tools/cardkit/cardkit_lib/repo.py: changes beyond the pinned upstream commit")

    def test_pin_file_cannot_change_without_a_pin_move(self):
        self.write("AGENTS.md", "Engine pinned at whatever.\n")
        self.stage()
        self.assertRejected("AGENTS.md: changed without a pin move")

    def test_pin_must_be_a_full_sha(self):
        self.write("engine-upgrade/preview-config.json", '{"upstream": "main"}\n')
        self.write("AGENTS.md", "Engine pinned at `main`, shipped as jars.\n")
        self.stage()
        self.assertRejected("AGENTS.md: changed without a pin move")

    def test_other_docs_are_rejected(self):
        self.write("docs/agents/CARD-PATTERNS.md", "x\n")
        self.write("docs/agents/argentum-upstream/LICENSE", "x\n")
        self.stage()
        code, out = self.check()
        self.assertEqual(code, 1, out)
        self.assertIn("docs/agents/CARD-PATTERNS.md: outside the upgrade scope", out)
        self.assertIn("docs/agents/argentum-upstream/LICENSE: outside the upgrade scope", out)


if __name__ == "__main__":
    unittest.main()
