"""Source-only fast-build policy / snapshot safety. NO APKs built in this suite."""
import json
import os
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import fast_apk_snapshot as fast

WORKFLOW_PATH = Path(".github/workflows/fast-test-apk-manual.yml")


class FastWorkflowStructureTests(unittest.TestCase):
    def test_only_explicit_manual_dispatch_can_seed_or_build(self):
        value=WORKFLOW_PATH.read_text()
        start=value.split("\non:\n",1)[1].split("\npermissions:\n",1)[0]
        self.assertIn("  workflow_dispatch:\n",start)
        for forbidden in ("\n  push:","\n  pull_request:","\n  schedule:","\n  workflow_run:"):
            self.assertNotIn(forbidden,start)
        self.assertIn("if: github.repository == 'AARMOU/arcana_table' && inputs.mode == 'seed-engine'",value)
        self.assertIn("if: github.repository == 'AARMOU/arcana_table' && inputs.mode == 'fast-apk'",value)
        self.assertIn("Fast APK needs a real successful seed-engine run ID",value)
        self.assertIn("fast-engine-bundle/manifest.json",value)
        self.assertNotIn("wrangler deploy",value)
        self.assertNotIn("assembleRelease",value)
        self.assertNotIn("bundleRelease",value)
        self.assertNotIn("apksigner sign",value)

    def test_engine_seed_executes_full_pinned_patch_rules(self):
        for group in (fast.SEED_PREFLIGHT,fast.SEED_ENGINE,fast.SEED_CARDS):
            scripts=fast.named_scripts(group)
            self.assertEqual(set(scripts),set(group))
            self.assertTrue(all("set -euo pipefail" in script or "set -o pipefail" in script
                                for script in scripts.values()))

    def test_build136_exact_source_pins(self):
        self.assertEqual(set(fast.b136_source_pins()), fast.B136_SOURCE_KEYS)
        self.assertTrue(any("Ugin" in name for name in fast.SEED_CARDS))
        self.assertEqual(set(fast.ENGINE_SOURCE_KEYS), {"UGIN_BASE", "UGIN_SHA", "TEMPLE_SHA"})
        self.assertEqual(set(fast.LEGACY_B136_SOURCE_PINS), fast.B136_SOURCE_KEYS)

    def test_workflow_cwd_honored(self):
        self.assertEqual(fast.step_working_directory("Build all four upgraded Argentum runtime JARs"), "combined-argentum")
        self.assertEqual(fast.step_working_directory("Rebuild and test all bundled custom cards against exact upgraded runtime"), "android")

    def test_all_four_required_engine_jars_and_all_indexes_present(self):
        self.assertEqual(len(fast.BUNDLE_FILES),9)
        self.assertEqual(sum(name.endswith(".jar") for name in fast.BUNDLE_FILES),4)
        self.assertEqual(len(set(fast.BUNDLE_FILES)),9)
        self.assertIn("android/app/src/main/assets/argentum/custom-cards.index.tsv",fast.BUNDLE_FILES)


class FastArtifactChecksTests(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.oldcwd=os.getcwd()
        os.chdir(self.temp.name)
        self.addCleanup(os.chdir,self.oldcwd)
        Path("inputs").mkdir()
        for i in range(40):
            (Path("inputs")/f"src-{i}.patch").write_text(f"source-{i}\n")
        Path("workflow.txt").write_text(
            fast.UPSTREAM+"\n"+fast.PATCH_SHA256+"\n"+fast.PATCH_ARTIFACT_SHA256+"\n"
        )
        self.patchers=[
            patch.object(fast,"INPUT_ROOTS",("inputs",)),
            patch.object(fast,"WORKFLOW",Path("workflow.txt")),
            patch.object(fast,"b136_source_pins",return_value={key:"0"*40 for key in fast.B136_SOURCE_KEYS}),
            patch.object(fast,"FINGERPRINT",Path("fingerprint.txt")),
        ]
        for p in self.patchers:
            p.start()
            self.addCleanup(p.stop)
        for n in fast.BUNDLE_FILES:
            f=Path(n)
            f.parent.mkdir(parents=True,exist_ok=True)
            if n.endswith("all-cards.index.tsv"):
                f.write_text("Card\tCMM\n"*16001)
            elif n.endswith("custom-cards.index.tsv"):
                f.write_text("Custom\tABC\n"*51)
            else:
                f.write_bytes((f"VERIFIED {n}\n"*20).encode())
        self.expected=fast.fingerprint()
        fast.FINGERPRINT.write_text(self.expected+"\n")

    def seed(self):
        with patch.dict(os.environ,{"GITHUB_RUN_ID":"123456","GITHUB_SHA":"source-test","FAST_SEED_RUN_ID":"123456"}):
            fast.seed_manifest(Path("bundle"))

    def test_ui_pins_do_not_invalidate_reusable_verified_engine(self):
        baseline = fast.fingerprint()
        changed = {k: "0"*40 for k in fast.B136_SOURCE_KEYS}
        changed["LOBBY_FIX_SHA"] = "f"*40
        changed["OUTCOME_SHA"] = "e"*40
        changed["DECK_SHA"] = "d"*40
        with patch.object(fast, "b136_source_pins", return_value=changed):
            self.assertEqual(baseline, fast.fingerprint())
        changed["UGIN_SHA"] = "a"*40
        with patch.object(fast, "b136_source_pins", return_value=changed):
            self.assertNotEqual(baseline, fast.fingerprint())

    def test_legacy_b136_seed_accepts_only_exact_unchanged_engine_bytes_and_pins(self):
        # Run 40417570112 contains schema 1. Reconstruct its original
        # fingerprint using immutable B136 source pins.
        pins = dict(fast.LEGACY_B136_SOURCE_PINS)
        pins["LOBBY_FIX_SHA"] = "f"*40
        pins["OUTCOME_SHA"] = "e"*40
        with patch.object(fast, "b136_source_pins", return_value=pins):
            legacy = fast.fingerprint(legacy=True)
            self.seed()
            manifest = Path("bundle/manifest.json")
            content = json.loads(manifest.read_text())
            content["schema"] = 1
            content["input_fingerprint"] = legacy
            manifest.write_text(json.dumps(content))
            with patch.dict(os.environ, {"FAST_SEED_RUN_ID": "123456"}):
                fast.restore(Path("bundle"))
            (Path("inputs")/"src-2.patch").write_text("UNVERIFIED")
            with patch.dict(os.environ, {"FAST_SEED_RUN_ID": "123456"}):
                with self.assertRaisesRegex(ValueError,"source/patches/custom cards changed"):
                    fast.restore(Path("bundle"))
        pins["TEMPLE_SHA"] = "f"*40
        with patch.object(fast, "b136_source_pins", return_value=pins):
            with self.assertRaisesRegex(ValueError, "Legacy seed engine/card pins changed"):
                fast.fingerprint(legacy=True)

    def test_changed_sources_block_seed_reuse(self):
        self.seed()
        (Path("inputs")/"src-0.patch").write_text("DIFFERENT")
        with patch.dict(os.environ,{"FAST_SEED_RUN_ID":"123456"}):
            with self.assertRaisesRegex(ValueError,"source/patches/custom cards changed"):
                fast.restore(Path("bundle"))

    def test_tampered_binary_blocks_restore(self):
        self.seed()
        target=Path("bundle")/fast.BUNDLE_FILES[0]
        target.write_bytes(b"unverified binary")
        with patch.dict(os.environ,{"FAST_SEED_RUN_ID":"123456"}):
            with self.assertRaisesRegex(ValueError,"Corrupt or changed"):
                fast.restore(Path("bundle"))

    def test_wrong_run_id_blocks_reuse(self):
        self.seed()
        with patch.dict(os.environ,{"FAST_SEED_RUN_ID":"999999"}):
            with self.assertRaisesRegex(ValueError,"run identity"):
                fast.restore(Path("bundle"))

    def test_verified_snapshot_restores_exact_hashes(self):
        self.seed()
        self.assertEqual(2,json.loads(Path("bundle/manifest.json").read_text())["schema"])
        original={n:fast.sha(Path(n).read_bytes()) for n in fast.BUNDLE_FILES}
        for name in fast.BUNDLE_FILES:
            Path(name).write_text("old")
        with patch.dict(os.environ,{"FAST_SEED_RUN_ID":"123456"}):
            # Restore checks source fingerprint first; in real runs the tracked
            # baseline inputs differ from snapshot outputs. Unit fake tree
            # deliberately hashes only the source patches.
            fast.restore(Path("bundle"))
        self.assertEqual(original,{n:fast.sha(Path(n).read_bytes()) for n in fast.BUNDLE_FILES})

    def test_version_stamping_refuses_unexpected_release_identity(self):
        f=Path("android/app/build.gradle.kts")
        f.parent.mkdir(parents=True,exist_ok=True)
        f.write_text('versionCode = 131\nversionName = "0.33.9-build131-argentum-upgrade"\n')
        fast.version(136)
        self.assertIn("versionCode = 136",f.read_text())
        with self.assertRaisesRegex(ValueError,"base changed"):
            fast.version(137)
        with self.assertRaisesRegex(ValueError,"versionCode >=136"):
            fast.version(135)


if __name__=="__main__":
    unittest.main()
