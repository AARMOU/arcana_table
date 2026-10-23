#!/usr/bin/env python3
"""Package a previously validated candidate; refuse changed app/patch inputs or mixed JARs."""
from __future__ import annotations

import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import struct
import subprocess
import sys
import urllib.request
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parent.parent
CONFIG = json.loads((ROOT / "engine-upgrade/preview-config.json").read_text())
BUNDLE = ROOT / "engine-upgrade/runtime-bundle"
EVIDENCE = ROOT / "validated-evidence"
MODULES = {
    "mtg-sdk": "com/wingedsheep/sdk/model/CardDefinition.class",
    "mtg-sdk-tooling": "com/wingedsheep/sdk/tooling/CardLoader.class",
    "rules-engine": "com/wingedsheep/engine/core/GameAction.class",
    "ai": "com/wingedsheep/ai/engine/AIPlayer.class",
}
ASSETS = ("all-cards.jsonl", "por-cards.jsonl", "all-cards.index.tsv")


def require(condition, message):
    if not condition:
        raise SystemExit(message)


def git(*args):
    return subprocess.check_output(["git", "-C", str(ROOT), *args], text=True).strip()


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def fingerprint():
    # Engine input key deliberately excludes APK version and app-only edits.
    h = hashlib.sha256(CONFIG["upstream"].encode())
    paths = sorted((ROOT / "argentum-patches").glob("*.patch"))
    paths += sorted((ROOT / "engine-upgrade/forward-ports").glob("*.patch"))
    paths += [ROOT / "engine-upgrade/audit_patches.py", Path(__file__),
              ROOT / ".github/workflows/argentum-upgrade-preview.yml"]
    for path in paths:
        h.update(str(path.relative_to(ROOT)).encode() + b"\0" + path.read_bytes())
    return h.hexdigest()


def normalize_version(text):
    text = re.sub(r"versionCode = \d+", "versionCode = VERSION", text)
    return re.sub(r'versionName = "[^"]+"', 'versionName = "VERSION"', text)


def check_source():
    commit = CONFIG["validated_commit"]
    require(re.fullmatch(r"[a-f0-9]{40}", commit), "Pin an exact validated commit")
    require(re.fullmatch(r"[a-f0-9]{40}", CONFIG["upstream"]), "Pin an exact upstream commit")
    checked = ["android", "argentum-patches", "engine-upgrade/forward-ports", "engine-upgrade/audit_patches.py"]
    changed = git("diff", "--name-only", commit, "HEAD", "--", *checked).splitlines()
    require(set(changed) <= {"android/app/build.gradle.kts"},
            "Runtime/app inputs changed since validation; run full validation first: " + str(changed))
    path = "android/app/build.gradle.kts"
    old = subprocess.check_output(["git", "-C", str(ROOT), "show", f"{commit}:{path}"], text=True)
    new = (ROOT / path).read_text()
    require(normalize_version(old) == normalize_version(new), "Only APK version metadata may change without app revalidation")
    require(f'versionCode = {CONFIG["version_code"]}' in new and
            f'versionName = "{CONFIG["version_name"]}"' in new, "Version/config mismatch")
    require(CONFIG["version_code"] > int(re.search(r"versionCode = (\d+)", old)[1]), "Preview version must increase")
    require(CONFIG["package"] == "com.arcanatable.argentum", "Preview package must stay isolated")
    require(CONFIG["upstream"] in (ROOT / "engine-upgrade/audit_patches.py").read_text(), "Patch audit pin differs")

    url = f'https://api.github.com/repos/{os.environ["GITHUB_REPOSITORY"]}/actions/runs/{CONFIG["validation_run"]}'
    request = urllib.request.Request(url, headers={"Authorization": "Bearer " + os.environ["GH_TOKEN"],
                                                 "Accept": "application/vnd.github+json"})
    with urllib.request.urlopen(request, timeout=30) as response:
        run = json.load(response)
    require(run["head_sha"] == commit and run["conclusion"] == "success", "Validation run is not green for this exact source")
    require(run["name"] == "Argentum upgrade focused Android regression tests", "Wrong validation workflow")

    counts = dict(tests=0, failures=0, errors=0, skipped=0)
    reports = list(EVIDENCE.glob("**/TEST-*.xml"))
    require(reports, "Missing complete Android validation XML")
    for report in reports:
        root = ET.parse(report).getroot()
        for key in counts:
            counts[key] += int(root.get(key, "0"))
    require(counts == dict(tests=CONFIG["expected_tests"], failures=0, errors=0,
                           skipped=CONFIG["expected_skipped"]), "Validation XML totals differ: " + str(counts))
    manifests = list(EVIDENCE.glob("**/candidate-manifest.json"))
    require(len(manifests) == 1, "Missing unique validated runtime manifest")
    expected = json.loads(manifests[0].read_text())
    require(expected["upstream"] == CONFIG["upstream"], "Validated engine differs")
    shutil.copyfile(manifests[0], ROOT / "engine-upgrade/validated-runtime.json")
    print("Validated source and reports:", counts)
    output = os.environ.get("GITHUB_OUTPUT")
    if output:
        with open(output, "a") as f:
            f.write("engine_key=" + fingerprint() + "\n")


def stage():
    engine = ROOT / "argentum-engine"
    require(git("-C", str(engine), "rev-parse", "HEAD") == CONFIG["upstream"], "Wrong engine checkout")
    for module, identity in MODULES.items():
        eligible = []
        for path in (engine / module / "build/libs").glob("*.jar"):
            with zipfile.ZipFile(path) as archive:
                if identity in archive.namelist():
                    eligible.append(path)
        require(len(eligible) == 1, f"Expected one production JAR for {module}: {eligible}")
        dest = BUNDLE / "libs" / f"argentum-{module}.jar"
        dest.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(eligible[0], dest)
    for name in ASSETS:
        dest = BUNDLE / "assets" / name
        dest.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(engine / "arcana-export-out" / name, dest)
    files = {str(p.relative_to(BUNDLE)): sha(p) for p in sorted(BUNDLE.glob("*/*")) if p.is_file()}
    (BUNDLE / "manifest.json").write_text(json.dumps({"engine_key": fingerprint(), "files": files}, indent=2) + "\n")


def verify_and_install():
    expected = json.loads((ROOT / "engine-upgrade/validated-runtime.json").read_text())
    manifest = json.loads((BUNDLE / "manifest.json").read_text())
    require(manifest["engine_key"] == fingerprint(), "Cached engine input key differs")
    wanted = {f"libs/argentum-{m}.jar" for m in MODULES} | {f"assets/{n}" for n in ASSETS}
    require(set(manifest["files"]) == wanted, "Incomplete or mixed runtime bundle")
    for name, digest in manifest["files"].items():
        require(sha(BUNDLE / name) == digest, "Runtime bundle checksum mismatch: " + name)
    for module, identity in MODULES.items():
        name = f"argentum-{module}.jar"
        jar = BUNDLE / "libs" / name
        require(sha(jar) == expected["jars"][name]["sha256"], "Rebuilt JAR differs from tested JAR: " + name)
        with zipfile.ZipFile(jar) as archive:
            require(identity in archive.namelist(), "Missing class: " + identity)
            versions = [struct.unpack(">H", archive.read(n)[6:8])[0]
                        for n in archive.namelist() if n.endswith(".class")]
            require(versions and max(versions) <= 61, "Android runtime requires Java 17 bytecode")
        shutil.copyfile(jar, ROOT / "android/app/libs" / name)
    cards = (BUNDLE / "assets/all-cards.jsonl").read_text().splitlines()
    index = (BUNDLE / "assets/all-cards.index.tsv").read_text().splitlines()
    require(len(cards) == len(index) == expected["exported_cards"], "Card export/index count mismatch")
    for row, key in zip(cards, index):
        require(bool(key) and "\t" in key, "Invalid index entry")
        json.loads(row)
    for row in (BUNDLE / "assets/por-cards.jsonl").read_text().splitlines():
        json.loads(row)
    for name in ASSETS:
        shutil.copyfile(BUNDLE / "assets" / name, ROOT / "android/app/src/main/assets/argentum" / name)
    print("Installed four byte-identical tested JARs and matching exported card data:", len(cards))


if __name__ == "__main__":
    {"source": check_source, "stage": stage, "install": verify_and_install}[sys.argv[1]]()
