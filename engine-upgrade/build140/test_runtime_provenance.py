"""Fail closed when a rules-only runtime rebuild changes its retained dependencies."""
import copy
import importlib.util
import unittest
from pathlib import Path

spec = importlib.util.spec_from_file_location("write_manifest", Path(__file__).with_name("write_manifest.py"))
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class RuntimeProvenanceTest(unittest.TestCase):
    def setUp(self):
        self.previous = {"upstream": "pinned", "jars": {
            "argentum-rules-engine.jar": "old", "argentum-mtg-sdk.jar": "sdk",
            "argentum-mtg-sdk-tooling.jar": "tooling", "argentum-ai.jar": "ai"},
            "patches": {"old.patch": {"sha256": "original", "status": "applied"}},
            "builtBy": {"run": "original"}}
        self.new = copy.deepcopy(self.previous)
        self.new["jars"]["argentum-rules-engine.jar"] = "rebuilt"
        self.new["patches"]["azlask.patch"] = {"sha256": "fix", "status": "applied"}
        self.new["builtBy"] = {"run": "new"}

    def test_records_mixed_runtime_provenance_without_claiming_sdk_rebuild(self):
        result = module.retain_unchanged_runtime(self.previous, self.new)
        self.assertEqual(result["builtBy"]["modules"], ["rules-engine"])
        self.assertEqual(result["retainedRuntime"]["builtBy"]["run"], "original")
        self.assertNotIn("argentum-rules-engine.jar", result["retainedRuntime"]["jars"])

    def test_rejects_changed_sdk_ai_or_tooling(self):
        for name in self.previous["jars"]:
            if name == "argentum-rules-engine.jar":
                continue
            with self.subTest(name=name), self.assertRaisesRegex(AssertionError, "retained runtime"):
                changed = copy.deepcopy(self.new)
                changed["jars"][name] = "unrelated"
                module.retain_unchanged_runtime(self.previous, changed)

    def test_rejects_upstream_or_patch_drift(self):
        for field in ("upstream", "patches"):
            with self.subTest(field=field), self.assertRaises(AssertionError):
                changed = copy.deepcopy(self.new)
                changed[field] = "other" if field == "upstream" else {}
                module.retain_unchanged_runtime(self.previous, changed)

    def test_rejects_missing_module(self):
        del self.new["jars"]["argentum-ai.jar"]
        with self.assertRaisesRegex(AssertionError, "runtime modules"):
            module.retain_unchanged_runtime(self.previous, self.new)


if __name__ == "__main__":
    unittest.main()
