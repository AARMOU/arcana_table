"""Offline regression tests for the read-only Argentum engine-PR guard."""
import datetime as dt
import importlib.util
import io
import pathlib
import unittest
from contextlib import redirect_stdout

SOURCE = pathlib.Path(__file__).resolve().parents[1] / "engine_pr_guard.py"
spec = importlib.util.spec_from_file_location("arcana_engine_pr_guard", SOURCE)
guard = importlib.util.module_from_spec(spec)
spec.loader.exec_module(guard)

OLD = "1" * 40
NEW = "2" * 40
HEAD = "3" * 40
WHEN = dt.datetime(2026, 10, 25, tzinfo=dt.timezone.utc)
AUDIT = f'TARGET = "{NEW}"\nMANDATORY = [\n    "0001-a.patch",\n    "0011-stack-object-copying.patch",\n]\n'
PR = {
    "number": 171, "title": "Eldrazi source", "html_url": "https://github.com/AARMOU/arcana_table/pull/171",
    "state": "open", "head": {"sha": HEAD, "repo": {"full_name": "AARMOU/arcana_table"}},
    "created_at": "2026-10-20T00:00:00Z",
}
PATCH = [{"filename": "argentum-patches/0011-stack-object-copying.patch", "status": "added"}]


class EvaluateTests(unittest.TestCase):
    def test_ordinary_card_only_pr_is_not_engine_pr(self):
        self.assertIsNone(guard.evaluate(
            PR, [{"filename": "android/custom-cards/src/main/kotlin/Card.kt"}],
            AUDIT, NEW, NEW, WHEN))

    def test_current_pin_is_not_stale_but_requires_split_landing(self):
        out = guard.evaluate(PR, PATCH, AUDIT, NEW, NEW, WHEN)
        self.assertEqual(out["errors"], [])
        self.assertTrue(any("SPLIT_LANDING" in x for x in out["warnings"]))
        self.assertTrue(any("LONG_LIVED" in x for x in out["warnings"]))

    def test_old_pin_fails_and_new_upstream_is_separate_warning(self):
        out = guard.evaluate(PR, PATCH, AUDIT.replace(NEW, OLD), NEW, HEAD, WHEN)
        self.assertTrue(any("STALE_ENGINE_PIN" in x for x in out["errors"]))
        self.assertTrue(any("UPSTREAM_DRIFT" in x for x in out["warnings"]))

    def test_patch_must_be_registered_as_mandatory(self):
        out = guard.evaluate(PR, PATCH, AUDIT.replace('    "0011-stack-object-copying.patch",\n', ''),
                             NEW, NEW, WHEN)
        self.assertIn("UNTRACKED_ENGINE_PATCH", out["errors"][0])

    def test_recorded_experimental_patch_not_treated_as_ship_ready_mandatory(self):
        out = guard.evaluate(PR, [{"filename": "argentum-patches/0010-enumeration-absence-index.patch", "status": "modified"}],
                             AUDIT, NEW, NEW, WHEN)
        self.assertEqual(out["errors"], [])

    def test_missing_audit_blocks_without_exception(self):
        out = guard.evaluate(PR, PATCH, None, NEW, NEW, WHEN)
        self.assertEqual(len(out["errors"]), 1)

    def test_engines_in_forward_ports_and_jars_are_detected(self):
        self.assertTrue(guard.is_engine_file("engine-upgrade/forward-ports/0004.patch"))
        self.assertTrue(guard.is_engine_file("engine-upgrade/rerere/aa/postimage"))
        self.assertTrue(guard.is_engine_file("android/app/libs/argentum-rules-engine.jar"))
        self.assertTrue(guard.is_engine_file("engine-upgrade/ulalek/patches/stack.patch"))
        self.assertFalse(guard.is_engine_file("engine-upgrade/tests/test_weekly_handoff.py"))
        self.assertFalse(guard.is_engine_file("android/custom-cards/TestCard.kt"))

    def test_requires_exact_target_pin(self):
        self.assertEqual(guard.read_pin(AUDIT), NEW)
        with self.assertRaises(ValueError):
            guard.read_pin('TARGET = "main"\n')

    def test_separate_upstream_warning_does_not_certify_rules(self):
        out = guard.evaluate(PR, PATCH, AUDIT, NEW, OLD, WHEN)
        self.assertEqual(out["errors"], [])
        self.assertTrue(any("UPSTREAM_DRIFT" in x for x in out["warnings"]))
        with redirect_stdout(io.StringIO()):
            self.assertIn("not** a compilation", guard.report([out], dev="development"))

    def test_no_hidden_success_when_api_has_no_read_access(self):
        class NoAccess:
            def get(self, path):
                raise ValueError("GitHub forbidden")
        with self.assertRaisesRegex(ValueError, "forbidden"):
            guard.scan(NoAccess(), "AARMOU/arcana_table", "development", None, NEW, WHEN)

    def test_malformed_repository_rejected(self):
        with self.assertRaisesRegex(ValueError, "Invalid GITHUB_REPOSITORY"):
            guard.scan(object(), "../outside", "development", None, NEW, WHEN)


if __name__ == "__main__":
    unittest.main()
