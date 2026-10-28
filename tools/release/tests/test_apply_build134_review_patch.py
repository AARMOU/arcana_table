"""Positive and negative tests for Build 134 source patch review guards."""
import hashlib
import importlib.util
from pathlib import Path
import subprocess
import tempfile
import unittest

MODULE = Path(__file__).resolve().parents[1] / "apply_build134_review_patch.py"
spec = importlib.util.spec_from_file_location("apply_build134_review_patch", MODULE)
tool = importlib.util.module_from_spec(spec)
spec.loader.exec_module(tool)


class ReviewPatchTests(unittest.TestCase):
    def test_accepts_source_only_patch(self):
        patch = b"diff --git a/README.md b/README.md\n--- a/README.md\n+++ b/README.md\n@@ -1 +1 @@\n-old\n+new\n"
        tool.verify_patch(patch, hashlib.sha256(patch).hexdigest())

    def test_rejects_modified_patch(self):
        with self.assertRaisesRegex(ValueError, "SHA256"):
            tool.verify_patch(b"modified", "0" * 64)

    def test_rejects_generated_jar(self):
        patch = b"diff --git a/android/app/libs/argentum-ai.jar b/android/app/libs/argentum-ai.jar\n"
        with self.assertRaisesRegex(ValueError, "JAR"):
            tool.verify_patch(patch, hashlib.sha256(patch).hexdigest())

    def test_rejects_ephemeral_fixture(self):
        patch = b"diff --git a/android/app/src/test/java/com/arcanatable/Build134ReleaseScopeTest.kt b/android/app/src/test/java/com/arcanatable/Build134ReleaseScopeTest.kt\n"
        with self.assertRaisesRegex(ValueError, "ephemeral"):
            tool.verify_patch(patch, hashlib.sha256(patch).hexdigest())

    def test_rejects_parent_traversal_and_absolute_paths(self):
        for name in (b"../escape.txt", b"/tmp/escape.txt", b"foo/../../escape.txt", b"foo\\\\bar.txt"):
            with self.subTest(path=name):
                patch = b"diff --git a/" + name + b" b/" + name + b"\n"
                with self.assertRaisesRegex(ValueError, "unsafe"):
                    tool.verify_patch(patch, hashlib.sha256(patch).hexdigest())

    def test_checkout_requires_exact_head_and_clean_tree(self):
        with tempfile.TemporaryDirectory() as temp:
            repo = Path(temp)
            def run(*args):
                return subprocess.run(["git", *args], cwd=repo, check=True,
                                      capture_output=True, text=True).stdout.strip()
            run("init", "-q")
            run("config", "user.email", "ci@example.invalid")
            run("config", "user.name", "CI")
            (repo / "README.md").write_text("old\n")
            run("add", "README.md")
            run("commit", "-qm", "baseline")
            head = run("rev-parse", "HEAD")
            tool.verify_checkout(repo, head)
            with self.assertRaisesRegex(ValueError, "exact"):
                tool.verify_checkout(repo, "0" * 40)
            (repo / "README.md").write_text("dirty\n")
            with self.assertRaisesRegex(ValueError, "clean"):
                tool.verify_checkout(repo, head)


if __name__ == "__main__":
    unittest.main()
