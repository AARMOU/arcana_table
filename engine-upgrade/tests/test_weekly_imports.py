"""weekly.py drops an import a replayed patch adds when upstream now has the same import (Kotlin rejects
the duplicate as ambiguous: patch 0035 vs upstream 04e15643, 2026-11-02)."""
import importlib.util
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
WEEKLY = ROOT / "engine-upgrade" / "weekly.py"
SPEC = importlib.util.spec_from_file_location("weekly", WEEKLY)
weekly = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(weekly)


class DedupeImports(unittest.TestCase):
    def test_only_exact_repeats_of_import_lines_go(self):
        text = ("package a\n\nimport x.Tapped\nimport y.Keyword\nimport x.Tapped\nimport x.TappedTwo\n\n"
                "val s = \"import x.Tapped\"\nimport y.Keyword\n")
        out, changed = weekly.dedupe_import_lines(text)
        self.assertTrue(changed)
        self.assertEqual("package a\n\nimport x.Tapped\nimport y.Keyword\nimport x.TappedTwo\n\n"
                         "val s = \"import x.Tapped\"\n", out)
        self.assertEqual((out, False), weekly.dedupe_import_lines(out))

    def test_rebase_exec_amends_the_patch_commit(self):
        repo = Path(tempfile.mkdtemp())
        git = lambda *a: subprocess.run(["git", *a], cwd=repo, check=True, capture_output=True, text=True)
        git("init", "-q", "-b", "up")
        git("config", "user.email", "t@example.com")
        git("config", "user.name", "t")
        src = repo / "S.kt"
        src.write_text("package a\n\nimport b.One\n\nclass S\n")
        git("add", "-A"); git("commit", "-qm", "pin")
        git("checkout", "-q", "-b", "arcana")
        src.write_text("package a\n\nimport b.One\nimport b.Tapped\n\nclass S { val t = Tapped }\n")
        git("commit", "-qam", "arcana: 0035-x")
        git("checkout", "-q", "up")
        src.write_text("package a\n\nimport b.Tapped\nimport b.One\n\nclass S\n")
        git("commit", "-qam", "upstream adds the same import")
        git("checkout", "-q", "arcana")
        hook = f"{sys.executable} {WEEKLY} dedupe-imports"
        r = subprocess.run(["git", "rebase", "--exec", hook, "up"], cwd=repo, capture_output=True, text=True)
        self.assertEqual(0, r.returncode, r.stdout + r.stderr)
        self.assertEqual(1, src.read_text().count("import b.Tapped"), src.read_text())
        self.assertEqual("arcana: 0035-x", git("log", "-1", "--format=%s").stdout.strip())
        self.assertEqual(3, int(git("rev-list", "--count", "HEAD").stdout))  # pin, upstream, the amended patch: no extra commit


if __name__ == "__main__":
    unittest.main()
