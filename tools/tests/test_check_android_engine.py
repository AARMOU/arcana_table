"""tools/check_android_engine.py on small real class files compiled here (javac), not mocks."""

import importlib.util
import re
import shutil
import subprocess
import tempfile
import unittest
import zipfile
from pathlib import Path

SPEC = importlib.util.spec_from_file_location("cae", Path(__file__).resolve().parents[1] / "check_android_engine.py")
cae = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(cae)

JAVAC = shutil.which("javac")
P = ("plat/",)  # stands in for java/ + javax/

# The "desktop" platform the engine compiles against, and the smaller one the "phone" has.
FULL = {
    "plat/Base.java": "package plat; public class Base { public void a() {} }",
    "plat/Iface.java": "package plat; public interface Iface { default void d() {} }",
    "plat/Thing.java": "package plat; public class Thing extends Base implements Iface {"
                       " public static int f; public void t() {} public void newCall() {} }",
    "plat/Gone.java": "package plat; public class Gone { public static void x() {} }",
    "lib/foo/Bar.java": "package lib.foo; public class Bar { public static void go() {} }",
}
PHONE = {k: v.replace(" public void newCall() {}", "") for k, v in FULL.items()
         if k not in ("plat/Gone.java",) and not k.startswith("lib/")}

OLD = {"com/wingedsheep/t/Old.java": "package com.wingedsheep.t; public class Old { void run(plat.Thing t) {"
       " t.t(); t.a(); t.d(); int x = plat.Thing.f; plat.Gone.x(); } }"}
NEW_CALL = {"com/wingedsheep/t/New.java": "package com.wingedsheep.t; public class New { void run(plat.Thing t) { t.newCall(); } }"}
NEW_LIB = {"com/wingedsheep/t/Lib.java": "package com.wingedsheep.t; public class Lib { void run() { lib.foo.Bar.go(); } }"}


@unittest.skipUnless(JAVAC, "javac not installed")
class CheckAndroidEngineTest(unittest.TestCase):
    def setUp(self):
        self.tmp = Path(tempfile.mkdtemp())
        self.addCleanup(shutil.rmtree, self.tmp)
        self.full = self.compile("full", FULL)

    def compile(self, name, sources, release="17", classpath=None) -> Path:
        src, out = self.tmp / name / "src", self.tmp / name / "out"
        for rel, body in sources.items():
            (src / rel).parent.mkdir(parents=True, exist_ok=True)
            (src / rel).write_text(body)
        out.mkdir(parents=True)
        cmd = [JAVAC, "-nowarn", "--release", release, "-d", str(out)]
        if classpath:
            cmd += ["-cp", str(classpath)]
        subprocess.run(cmd + [str(src / rel) for rel in sources], check=True, capture_output=True)
        return out

    def jar(self, name, *dirs, only=None) -> Path:
        path = self.tmp / name
        path.parent.mkdir(parents=True, exist_ok=True)
        with zipfile.ZipFile(path, "w") as z:
            for d in dirs:
                for f in sorted(d.rglob("*.class")):
                    rel = f.relative_to(d).as_posix()
                    if only is None or rel.startswith(only):
                        z.write(f, rel)
        return path

    def engine(self, folder, *sources, release="17"):
        out = self.compile(folder, {k: v for s in sources for k, v in s.items()}, release, self.full)
        return [self.jar(f"{folder}/argentum-rules-engine.jar", out)]

    def phone(self):
        return self.jar("android.jar", self.compile("phone", PHONE))

    def run_check(self, jars, baseline):
        return cae.check(jars, baseline, self.phone(), None, allowlist=set(), prefixes=P, app_files=[])

    def test_shipped_engine_is_clean_and_inherited_members_resolve(self):
        old = self.engine("old", OLD)
        r = self.run_check(old, old)
        self.assertTrue(r["ok"], r)
        # Gone.x() is missing on the phone, but the shipped engine already calls it: known, not new.
        self.assertIn("plat.Gone.x()V", r["knownMissingApis"])
        self.assertNotIn("plat.Thing.a()V", r["knownMissingApis"])  # inherited from Base
        self.assertNotIn("plat.Thing.d()V", r["knownMissingApis"])  # default method of Iface

    def test_new_call_missing_on_the_phone_blocks(self):
        r = self.run_check(self.engine("new", OLD, NEW_CALL), self.engine("old", OLD))
        self.assertFalse(r["ok"])
        self.assertEqual(["plat.Thing.newCall()V"], r["newMissingApis"])

    def test_allowlist_unblocks_a_named_call(self):
        r = cae.check(self.engine("new", OLD, NEW_CALL), self.engine("old", OLD), self.phone(), None,
                      allowlist={"plat.Thing.newCall()V"}, prefixes=P, app_files=[])
        self.assertTrue(r["ok"], r)
        self.assertEqual(["plat.Thing.newCall()V"], r["allowlisted"])

    def test_without_baseline_every_missing_call_blocks(self):
        r = self.run_check(self.engine("new", OLD), None)
        self.assertFalse(r["ok"])
        self.assertIn("plat.Gone.x()V", r["newMissingApis"])

    def test_new_library_dependency_blocks(self):
        r = self.run_check(self.engine("new", OLD, NEW_LIB), self.engine("old", OLD))
        self.assertFalse(r["ok"])
        self.assertEqual(["lib/foo"], r["newLibraries"])

    def test_bytecode_newer_than_java_17_blocks(self):
        out = subprocess.run([JAVAC, "-version"], capture_output=True, text=True)
        major = int(re.search(r"javac (\d+)", out.stdout + out.stderr).group(1))
        if major <= 17:
            self.skipTest(f"javac {major} cannot emit bytecode newer than Java 17")
        r = self.run_check(self.engine("new", OLD, release=str(major)), self.engine("old", OLD))
        self.assertFalse(r["ok"])
        self.assertEqual(1, r["bytecodeCount"])
        self.assertIn(f"class version {major + 44}", r["bytecode"][0])

    def test_report_names_the_problem_in_plain_words(self):
        r = self.run_check(self.engine("new", OLD, NEW_CALL), self.engine("old", OLD))
        md = cae.report(r, 26)
        self.assertIn("would not run reliably in the app", md)
        self.assertIn("plat.Thing.newCall()V", md)
        self.assertIn("Android 26", md)


class LibraryVersionTest(unittest.TestCase):
    def setUp(self):
        self.tmp = Path(tempfile.mkdtemp())
        self.addCleanup(shutil.rmtree, self.tmp)
        self.toml = self.tmp / "libs.versions.toml"
        self.toml.write_text('[versions]\nkotlin = "2.4.21"\nkotlinxSerializationJSON = "1.12.0"\n'
                             'kotlinxCoroutines = "1.11.0" # same\nother = "9"\n[libraries]\nkotlin = "x"\n')
        self.root = self.tmp / "root.gradle.kts"
        self.root.write_text('plugins {\n    id("com.android.application") version "8.9.0" apply false\n'
                             '    id("org.jetbrains.kotlin.android") version "2.4.0" apply false\n'
                             '    id("org.jetbrains.kotlin.plugin.compose") version "2.4.0" apply false\n}\n')
        self.app = self.tmp / "app.gradle.kts"
        self.app.write_text('dependencies {\n    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")\n'
                            '    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")\n}\n')

    def test_engine_versions_reads_only_the_versions_table(self):
        self.assertEqual({"kotlin": "2.4.21", "kotlinxSerializationJSON": "1.12.0", "kotlinxCoroutines": "1.11.0"},
                         cae.engine_versions(self.toml))

    def test_older_app_versions_are_reported(self):
        found = cae.version_problems(cae.engine_versions(self.toml), cae.app_versions([self.root, self.app]))
        self.assertEqual(3, len(found))  # two kotlin plugins, serialization; coroutines already matches
        self.assertTrue(any("kotlinxSerializationJSON 1.11.0 is older than the 1.12.0" in x for x in found))

    def test_align_changes_only_version_strings_and_only_upward(self):
        before_root, before_app = self.root.read_text(), self.app.read_text()
        changed = cae.align(cae.engine_versions(self.toml), [self.root, self.app])
        self.assertEqual(3, len(changed))
        self.assertEqual(before_root.replace('"2.4.0"', '"2.4.21"'), self.root.read_text())
        self.assertEqual(before_app.replace("json:1.11.0", "json:1.12.0"), self.app.read_text())
        self.assertIn('"8.9.0"', self.root.read_text())  # not a library the engine pins
        self.assertEqual([], cae.version_problems(cae.engine_versions(self.toml), cae.app_versions([self.root, self.app])))
        self.assertEqual([], cae.align({"kotlin": "2.3.0"}, [self.root]))  # never downgrades

    def test_version_order(self):
        k = cae.version_key
        self.assertLess(k("1.11.0"), k("1.12.0"))
        self.assertLess(k("1.12.0-RC2"), k("1.12.0"))
        self.assertLess(k("2.4.0"), k("2.4.21"))
        self.assertLess(k("1.9"), k("1.10"))
        self.assertEqual(k("2.4"), k("2.4.0"))

    def test_missing_declaration_is_reported(self):
        found = cae.version_problems({"kotlinxDatetime": "0.8.0"}, cae.app_versions([self.app]))
        self.assertEqual(["the engine is built with kotlinxDatetime 0.8.0, but no app build file declares kotlinxDatetime"], found)


if __name__ == "__main__":
    unittest.main()
