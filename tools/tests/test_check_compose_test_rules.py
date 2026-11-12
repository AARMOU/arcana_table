"""Fast source-only regressions for Compose test API mistakes; no Android SDK needed."""

from pathlib import Path
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from check_compose_test_rules import kotlin_files, main, violations


class ComposeTestRuleGuardTests(unittest.TestCase):
    def test_rejects_nonexistent_assertion_imports(self):
        code = """import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
rule.onNodeWithText("lobby").assertDoesNotExist()
"""
        problems = violations(Path("LobbyTest.kt"), code)
        self.assertEqual(len(problems), 2)
        self.assertIn("LobbyTest.kt:1", problems[0])
        self.assertIn("LobbyTest.kt:2", problems[1])

    def test_rejects_set_content_on_empty_compose_rule(self):
        code = """@get:Rule val rule = createEmptyComposeRule()
fun test() {
    rule.setContent { LobbyScreen() }
}
"""
        problems = violations(Path("LobbyTest.kt"), code)
        self.assertEqual(len(problems), 1)
        self.assertIn("LobbyTest.kt:3", problems[0])
        self.assertIn("declared at line 1", problems[0])

    def test_allows_content_rule_and_interaction_methods(self):
        code = """import androidx.compose.ui.test.assertIsDisplayed
@get:Rule val rule = createComposeRule()
fun test() {
    rule.setContent { LobbyScreen() }
    rule.onNodeWithText("lobby").assertDoesNotExist()
    rule.onNodeWithText("lobby").assertExists()
}
"""
        self.assertEqual(violations(Path("LobbyTest.kt"), code), [])

    def test_allows_empty_rule_without_set_content(self):
        code = """@get:Rule val rule = createEmptyComposeRule()
fun test() { rule.waitForIdle() }
// rule.setContent { this is a comment }
"""
        self.assertEqual(violations(Path("LobbyTest.kt"), code), [])

    def test_scans_only_kotlin_and_fails_closed_for_missing_input(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "A.kt").write_text("import androidx.compose.ui.test.assertExists\n")
            (root / "README.md").write_text("ignored")
            self.assertEqual(kotlin_files([root]), [root / "A.kt"])
            self.assertEqual(main([str(root)]), 1)
            self.assertEqual(main([str(root / "missing")]), 2)


if __name__ == "__main__":
    unittest.main()
