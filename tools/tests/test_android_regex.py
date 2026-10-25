"""Regression fixtures for Android ICU regex compatibility guard."""

import importlib.util
import unittest
from pathlib import Path

SPEC = importlib.util.spec_from_file_location("android_regex", Path(__file__).resolve().parents[1] / "check_android_regex.py")
android_regex = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(android_regex)


class AndroidRegexTest(unittest.TestCase):
    def test_crashing_mana_symbol_pattern(self):
        self.assertTrue(android_regex.scan(r'val cost = Regex("\\{([^}]+)}")'))

    def test_corrected_mana_pattern(self):
        self.assertEqual([], android_regex.scan(r'val cost = Regex("\\{([^}]+)\\}")'))

    def test_bare_close_bracket(self):
        self.assertTrue(android_regex.scan(r'val color = Regex("\\[([^\\]]*)]")'))

    def test_escaped_bracket(self):
        self.assertFalse(android_regex.scan(r'val color = Regex("\\[([^\\]]*)\\]")'))

    def test_escaped_hyphen_in_class(self):
        self.assertTrue(android_regex.scan(r'val trouble = Regex("[a\\-z]")'))

    def test_legitimate_quantifier_and_unicode_property(self):
        self.assertEqual([], android_regex.scan(r'val okay = Regex("[A-Z]{1,3}\\p{L}+")'))

    def test_triple_quoted_pattern(self):
        self.assertTrue(android_regex.scan('val failure = Regex("""\\{([^}]+)}""")'))

    def test_comments_and_strings_ignored(self):
        example = r'''// Regex("\\{([^}]+)}")
/* Pattern.compile("\\{([^}]+)}") */
val docs = "Regex(\\\"bad}\\\")"
val safe = Regex("[a-z]+")'''
        self.assertEqual([], android_regex.scan(example))

    def test_interpolated_patterns_are_dynamic(self):
        self.assertEqual([], android_regex.scan('val dynamic = Regex("${Regex.escape(keyword)}")'))

    def test_importer_bracket_regression(self):
        self.assertTrue(android_regex.scan(r'val bad = Regex("""\[Commander(?:\{[^]]*})?]""")'))
        self.assertFalse(android_regex.scan(r'val fixed = Regex("""\[Commander(?:\{[^\]]*\})?\]""")'))

    def test_java_pattern_compile(self):
        self.assertTrue(android_regex.scan('Pattern.compile("broken}");'))

    def test_escaped_literal_and_class_content(self):
        self.assertEqual([], android_regex.scan(r'val okay = Regex("[}\\]-]+\\}")'))


if __name__ == "__main__":
    unittest.main()
