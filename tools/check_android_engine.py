#!/usr/bin/env python3
"""Will this Argentum engine run inside the Android app? Checked from the jars, before anyone installs it.

The engine is built on a desktop JDK (upstream moves it: 21, then 25) and shipped as four jars in
android/app/libs/. Three things can make a jar that compiles and passes every JVM test still crash on a
phone, and each is checked here against the jars themselves rather than the build settings that are meant
to prevent it:

  1. Bytecode newer than Java 17 (class-file major version > 61). The app's dexer refuses it; if a build
     setting that targets Java 17 is ever lost, this is where it shows.
  2. Java platform APIs the phone does not have. A call compiled against the Java 17 API can still be
     missing on Android at the app's minSdk (Android ships its own java.* library). Every java.* and
     javax.* class, method and field the jars reference is looked up in that API level's android.jar.
     References the shipped jars already make are known to work (or to sit on paths that never run on a
     phone) and are reported, not blocking; only NEW ones block. A new one that the dexer rewrites for old
     phones itself (a D8 "backport", e.g. Objects.requireNonNullElse) goes in tools/android-engine-allowlist.txt
     with a reason.
  3. Libraries. The jars carry no dependency list: at run time they get whatever kotlin / kotlinx
     versions the app ships. The engine's own build (gradle/libs.versions.toml) says which versions it was
     compiled against; the app must ship the same or newer. `align` raises the app's versions to match
     (only version strings change). A reference to a library package the shipped jars never used (a new
     dependency the app does not ship) blocks.

Usage:
  check_android_engine.py check --jars android/app/libs --baseline-jars OLD_LIBS --android-jar ANDROID_JAR
                                 --engine-versions argentum-engine/gradle/libs.versions.toml [--out DIR]
  check_android_engine.py align --engine-versions argentum-engine/gradle/libs.versions.toml
  check_android_engine.py min-sdk          prints the app's minSdk

Exit 0 when the engine is safe for the app, 1 when it is not (details in --out: android.json, android.md).
Tests: tools/tests/test_check_android_engine.py
"""
from __future__ import annotations

import argparse
import json
import re
import struct
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
ANDROID = ROOT / "android"
APP_GRADLE = [ANDROID / "build.gradle.kts", ANDROID / "app" / "build.gradle.kts",
              ANDROID / "custom-cards" / "build.gradle.kts"]
ALLOWLIST = ROOT / "tools" / "android-engine-allowlist.txt"
ENGINE_JARS = re.compile(r"argentum-[a-z0-9-]+\.jar")
MAX_MAJOR = 61  # Java 17
PLATFORM = ("java/", "javax/")
ENGINE_OWN = ("com/wingedsheep/", "com/arcanatable/")
# Invokedynamic bootstraps the dexer desugars away: they are never called on the phone.
DESUGARED = {"java/lang/invoke/LambdaMetafactory", "java/lang/invoke/StringConcatFactory",
             "java/lang/runtime/ObjectMethods", "java/lang/runtime/SwitchBootstraps"}
# Signature-polymorphic methods: their call-site descriptor never matches the declared one.
POLYMORPHIC = {"java/lang/invoke/MethodHandle", "java/lang/invoke/VarHandle"}
# Engine version key (gradle/libs.versions.toml) -> how the app declares it. A library's version in each
# app build file must be >= the engine's.
LIBRARIES = {
    "kotlin": re.compile(r'(id\("org\.jetbrains\.kotlin\.[a-z.]+"\) version ")([0-9][0-9A-Za-z.+-]*)(")'),
    "kotlinxSerializationJSON": re.compile(r'("org\.jetbrains\.kotlinx:kotlinx-serialization-json:)([0-9][0-9A-Za-z.+-]*)(")'),
    "kotlinxCoroutines": re.compile(r'("org\.jetbrains\.kotlinx:kotlinx-coroutines-[a-z]+:)([0-9][0-9A-Za-z.+-]*)(")'),
    "kotlinxDatetime": re.compile(r'("org\.jetbrains\.kotlinx:kotlinx-datetime:)([0-9][0-9A-Za-z.+-]*)(")'),
}


# ---- class files -------------------------------------------------------------------------------------

class ClassInfo:
    __slots__ = ("name", "major", "super", "interfaces", "methods", "fields", "refs", "classes")

    def __init__(self):
        self.name, self.major, self.super = "", 0, None
        self.interfaces: list[str] = []
        self.methods: set[tuple[str, str]] = set()
        self.fields: set[str] = set()
        self.refs: set[tuple[str, str, str, str]] = set()  # (kind, owner, name, descriptor)
        self.classes: set[str] = set()


def parse_class(data: bytes, members: bool = False) -> ClassInfo:
    """Constant pool, hierarchy and (optionally) declared members of one class file."""
    if data[:4] != b"\xca\xfe\xba\xbe":
        raise ValueError("not a class file")
    info = ClassInfo()
    info.major = struct.unpack(">H", data[6:8])[0]
    count = struct.unpack(">H", data[8:10])[0]
    pool: list = [None] * count
    i, pos = 1, 10
    while i < count:
        tag = data[pos]
        if tag == 1:
            n = struct.unpack(">H", data[pos + 1:pos + 3])[0]
            pool[i] = ("utf8", data[pos + 3:pos + 3 + n].decode("utf-8", "replace"))
            pos += 3 + n
        elif tag in (7, 8, 16, 19, 20):
            pool[i] = ("ref1", tag, struct.unpack(">H", data[pos + 1:pos + 3])[0])
            pos += 3
        elif tag == 15:
            pos += 4
        elif tag in (9, 10, 11, 12, 17, 18):
            a, b = struct.unpack(">HH", data[pos + 1:pos + 5])
            pool[i] = ("ref2", tag, a, b)
            pos += 5
        elif tag in (3, 4):
            pos += 5
        elif tag in (5, 6):
            pos += 9
            i += 1
        else:
            raise ValueError(f"bad constant tag {tag}")
        i += 1

    def utf(idx):
        return pool[idx][1]

    def cls(idx):
        return utf(pool[idx][2])

    for entry in pool:
        if not entry:
            continue
        if entry[0] == "ref1" and entry[1] == 7:
            info.classes.add(utf(entry[2]))
        elif entry[0] == "ref2" and entry[1] in (9, 10, 11):
            nat = pool[entry[3]]
            kind = {9: "field", 10: "method", 11: "method"}[entry[1]]
            info.refs.add((kind, cls(entry[2]), utf(nat[2]), utf(nat[3])))
    access_this_super = struct.unpack(">HHH", data[pos:pos + 6])
    info.name = cls(access_this_super[1])
    info.super = cls(access_this_super[2]) if access_this_super[2] else None
    pos += 6
    n = struct.unpack(">H", data[pos:pos + 2])[0]
    pos += 2
    info.interfaces = [cls(struct.unpack(">H", data[pos + 2 * k:pos + 2 * k + 2])[0]) for k in range(n)]
    pos += 2 * n
    if not members:
        return info
    for target in ("fields", "methods"):
        n = struct.unpack(">H", data[pos:pos + 2])[0]
        pos += 2
        for _ in range(n):
            _, name_i, desc_i, attrs = struct.unpack(">HHHH", data[pos:pos + 8])
            pos += 8
            for _ in range(attrs):
                length = struct.unpack(">I", data[pos + 2:pos + 6])[0]
                pos += 6 + length
            if target == "fields":
                info.fields.add(utf(name_i))
            else:
                info.methods.add((utf(name_i), utf(desc_i)))
    return info


def jar_classes(jar: Path, members: bool = False, prefixes: tuple[str, ...] | None = None):
    with zipfile.ZipFile(jar) as z:
        for entry in z.namelist():
            if not entry.endswith(".class") or entry.endswith("module-info.class"):
                continue
            if prefixes and not entry.startswith(prefixes):
                continue
            yield entry, parse_class(z.read(entry), members)


def engine_jars(folder: Path) -> list[Path]:
    jars = sorted(p for p in Path(folder).iterdir() if ENGINE_JARS.fullmatch(p.name))
    if not jars:
        raise SystemExit(f"no argentum-*.jar in {folder}")
    return jars


class Platform:
    """The Java API a phone at the app's minSdk has: java.* and javax.* from that level's android.jar."""

    def __init__(self, android_jar: Path, prefixes: tuple[str, ...] = PLATFORM):
        self.prefixes = prefixes
        self.classes = {info.name: info for _, info in jar_classes(android_jar, True, prefixes)}

    def has(self, kind: str, owner: str, name: str, desc: str) -> bool:
        if owner.startswith("["):  # array pseudo-class: clone(), length
            return True
        if owner not in self.classes:
            return False
        if kind == "class":
            return True
        if owner in POLYMORPHIC and kind == "method":
            return any(m == name for m, _ in self.classes[owner].methods)
        seen, todo = set(), [owner]
        while todo:
            c = todo.pop()
            if c in seen or c not in self.classes:
                continue
            seen.add(c)
            info = self.classes[c]
            if kind == "field" and name in info.fields:
                return True
            if kind == "method" and (name, desc) in info.methods:
                return True
            todo += ([info.super] if info.super else []) + info.interfaces
        # Interfaces inherit java/lang/Object's methods.
        return kind == "method" and (name, desc) in self.classes.get("java/lang/Object", ClassInfo()).methods


def scan(jars: list[Path], prefixes: tuple[str, ...] = PLATFORM):
    """(bad bytecode versions, platform references, library roots) over a set of engine jars."""
    versions, refs, roots = [], set(), set()
    for jar in jars:
        for entry, info in jar_classes(jar):
            if info.major > MAX_MAJOR:
                versions.append(f"{jar.name}!{entry} (class version {info.major})")
            names = set(info.classes) | {r[1] for r in info.refs}
            for kind, owner, name, desc in info.refs:
                if owner.startswith(prefixes) and owner not in DESUGARED:
                    refs.add((kind, owner, name, desc))
            for n in names:
                n = n.lstrip("[").removeprefix("L").removesuffix(";")
                if not n or n.startswith(prefixes) or n.startswith(ENGINE_OWN) or "/" not in n:
                    if n.startswith(prefixes) and n not in DESUGARED:
                        refs.add(("class", n, "", ""))
                    continue
                parts = n.split("/")
                roots.add("/".join(parts[:1] if parts[0] == "kotlin" else parts[:2]))
    return versions, refs, roots


def ref_text(r) -> str:
    kind, owner, name, desc = r
    return owner.replace("/", ".") if kind == "class" else f"{owner.replace('/', '.')}.{name}{desc if kind == 'method' else ''}"


def load_allowlist(path: Path = ALLOWLIST) -> set[str]:
    if not path.is_file():
        return set()
    out = set()
    for line in path.read_text().splitlines():
        line = line.split("#", 1)[0].strip()
        if line:
            out.add(line.split()[0])
    return out


# ---- library versions --------------------------------------------------------------------------------

def version_key(v: str):
    """1.12.0 > 1.12.0-RC2 > 1.11.9; numeric parts compared as numbers."""
    main, _, pre = v.partition("-")
    nums = [int(x) if x.isdigit() else 0 for x in re.split(r"[.+]", main)]
    return nums + [0] * (4 - len(nums)), (1, "") if not pre else (0, pre)


def engine_versions(toml: Path) -> dict[str, str]:
    text, out, section = Path(toml).read_text(), {}, None
    for line in text.splitlines():
        line = line.split("#", 1)[0].strip()
        if line.startswith("["):
            section = line
        elif section == "[versions]" and "=" in line:
            k, v = (x.strip() for x in line.split("=", 1))
            if k in LIBRARIES:
                out[k] = v.strip('"')
    return out


def app_versions(files=APP_GRADLE) -> dict[str, list[tuple[str, str]]]:
    out: dict[str, list[tuple[str, str]]] = {k: [] for k in LIBRARIES}
    for f in files:
        if not Path(f).is_file():
            continue
        text = Path(f).read_text()
        for key, pattern in LIBRARIES.items():
            out[key] += [(str(Path(f).relative_to(ROOT)) if Path(f).is_relative_to(ROOT) else str(f), m.group(2))
                         for m in pattern.finditer(text)]
    return out


def version_problems(engine: dict[str, str], app: dict[str, list[tuple[str, str]]]) -> list[str]:
    found = []
    for key, want in sorted(engine.items()):
        if not app.get(key):
            found.append(f"the engine is built with {key} {want}, but no app build file declares {key}")
        for where, have in app.get(key, []):
            if version_key(have) < version_key(want):
                found.append(f"{where}: {key} {have} is older than the {want} the engine is built with")
    return found


def align(engine: dict[str, str], files=APP_GRADLE) -> list[str]:
    """Raise every older app version to the engine's. Only version strings change."""
    changed = []
    for f in files:
        f = Path(f)
        if not f.is_file():
            continue
        text = original = f.read_text()
        for key, want in engine.items():
            def bump(m, key=key, want=want):
                if version_key(m.group(2)) < version_key(want):
                    changed.append(f"{f.relative_to(ROOT) if f.is_relative_to(ROOT) else f}: {key} {m.group(2)} -> {want}")
                    return m.group(1) + want + m.group(3)
                return m.group(0)
            text = LIBRARIES[key].sub(bump, text)
        if text != original:
            f.write_text(text)
    return changed


def min_sdk(app_gradle: Path = ANDROID / "app" / "build.gradle.kts") -> int:
    m = re.search(r"^\s*minSdk\s*=\s*(\d+)", Path(app_gradle).read_text(), re.M)
    if not m:
        raise SystemExit("minSdk not found in " + str(app_gradle))
    return int(m.group(1))


# ---- the check ---------------------------------------------------------------------------------------

def check(jars, baseline_jars, android_jar, toml, allowlist=None, prefixes=PLATFORM, app_files=APP_GRADLE):
    versions, refs, roots = scan(jars, prefixes)
    _, old_refs, old_roots = scan(baseline_jars, prefixes) if baseline_jars else ([], set(), set())
    platform = Platform(android_jar, prefixes)
    allowed = load_allowlist() if allowlist is None else allowlist
    missing = {r for r in refs if not platform.has(*r)}
    known = {r for r in missing if r in old_refs}
    new_missing = sorted(ref_text(r) for r in missing - known if ref_text(r) not in allowed)
    allowed_hit = sorted(ref_text(r) for r in missing - known if ref_text(r) in allowed)
    new_roots = sorted(roots - old_roots) if baseline_jars else []
    lib = version_problems(engine_versions(toml), app_versions(app_files)) if toml else []
    result = {
        "ok": not (versions or new_missing or new_roots or lib),
        "bytecode": versions[:50], "bytecodeCount": len(versions),
        "newMissingApis": new_missing, "allowlisted": allowed_hit,
        "knownMissingApis": sorted(ref_text(r) for r in known),
        "newLibraries": new_roots, "libraryVersions": lib,
        "platformReferences": len(refs), "libraries": sorted(roots),
    }
    return result


def report(result: dict, level: int) -> str:
    ok = result["ok"]
    lines = ["### Engine on Android (minSdk %d)" % level, "",
             ("✅ The new engine is safe for the app: Java 17 bytecode, every Java API it calls exists on "
              f"Android {level}, and the app ships the library versions it was built with.") if ok else
             "❌ **The new engine would not run reliably in the app.** It was not installed for players. Details:"]
    if result["bytecodeCount"]:
        lines += ["", f"- **{result['bytecodeCount']} classes are newer than Java 17** (the phone cannot load them). "
                  "The Java 17 build setting (patch 0002, `arcana-android-target.gradle.kts`) did not apply. First few:"]
        lines += [f"  - `{x}`" for x in result["bytecode"][:8]]
    if result["newMissingApis"]:
        lines += ["", f"- **{len(result['newMissingApis'])} Java calls that phones at Android {level} do not have** "
                  "(the app would crash when they run). If the Android dexer backports one itself, list it in "
                  "`tools/android-engine-allowlist.txt` with the reason; otherwise the patch or upstream code "
                  "that calls it needs an Android-safe replacement:"]
        lines += [f"  - `{x}`" for x in result["newMissingApis"][:25]]
    if result["newLibraries"]:
        lines += ["", "- **The engine now uses libraries the app does not ship:** " +
                  ", ".join(f"`{x}`" for x in result["newLibraries"]) + ". Add them to the app (or patch them out)."]
    if result["libraryVersions"]:
        lines += ["", "- **Library versions:** the app must ship what the engine was built with "
                  "(`tools/check_android_engine.py align` raises them):"]
        lines += [f"  - {x}" for x in result["libraryVersions"]]
    if result["allowlisted"]:
        lines += ["", f"Allowed by `tools/android-engine-allowlist.txt`: {len(result['allowlisted'])}."]
    if result["knownMissingApis"]:
        lines += ["", f"<details><summary>{len(result['knownMissingApis'])} calls not in Android {level}'s API that the "
                  "shipped engine already makes (not new; not blocking)</summary>", ""]
        lines += [f"- `{x}`" for x in result["knownMissingApis"][:200]] + ["", "</details>"]
    return "\n".join(lines) + "\n"


def main(argv=None) -> int:
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    sub = ap.add_subparsers(dest="cmd", required=True)
    c = sub.add_parser("check")
    c.add_argument("--jars", required=True, type=Path)
    c.add_argument("--baseline-jars", type=Path)
    c.add_argument("--android-jar", required=True, type=Path)
    c.add_argument("--engine-versions", type=Path)
    c.add_argument("--out", type=Path)
    a = sub.add_parser("align")
    a.add_argument("--engine-versions", required=True, type=Path)
    sub.add_parser("min-sdk")
    args = ap.parse_args(argv)
    if args.cmd == "min-sdk":
        print(min_sdk())
        return 0
    if args.cmd == "align":
        for line in align(engine_versions(args.engine_versions)) or ["app library versions already match the engine"]:
            print(line)
        return 0
    result = check(engine_jars(args.jars), engine_jars(args.baseline_jars) if args.baseline_jars else None,
                   args.android_jar, args.engine_versions)
    md = report(result, min_sdk())
    if args.out:
        args.out.mkdir(parents=True, exist_ok=True)
        (args.out / "android.json").write_text(json.dumps(result, indent=1) + "\n")
        (args.out / "android.md").write_text(md)
    print(md)
    for x in result["newMissingApis"][:10]:
        print(f"::error title=Engine call missing on Android::{x}")
    for x in result["libraryVersions"] + result["newLibraries"] + result["bytecode"][:5]:
        print(f"::error title=Engine not Android-safe::{x}")
    return 0 if result["ok"] else 1


if __name__ == "__main__":
    sys.exit(main())
