#!/usr/bin/env python3
"""Build/reuse a strictly source-fingerprinted Argentum + custom-card snapshot.

Manual workflow only. Seed performs full engine/card validation WITHOUT any APK.
Fast mode consumes one explicitly selected successful seed workflow artifact.
All matching is *content-based* so UI-only edits can reuse the same engine.
Never substitutes repo/JARs from Claude's unrelated development branch.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys

from lobby_tutorial_join_smoke import PINS

UPSTREAM = "c38f145359c342a04f951b7ba48f741bcb1627ac"
PATCH_ARTIFACT_SHA256 = "2eca4e497de60cb3ff0d20a5df7c3635ac5be7e7881900a1b606926da1321cc0"
PATCH_SHA256 = "065ba023c1fab0c855318f0dd70e55592cc340cbf72263019ef6ea136e90231c"
WORKFLOW = Path(".github/workflows/release-look-followup-build134.yml")
FINGERPRINT = Path("fast-engine-inputs.sha256")
ASSET_DIR = Path("android/app/src/main/assets/argentum")
JAR_DIR = Path("android/app/libs")
BUNDLE_FILES = tuple(
    f"android/app/libs/argentum-{n}.jar"
    for n in ("mtg-sdk", "mtg-sdk-tooling", "rules-engine", "ai")
) + tuple(
    f"android/app/src/main/assets/argentum/{n}"
    for n in ("all-cards.jsonl", "por-cards.jsonl", "all-cards.index.tsv",
              "custom-cards.jsonl", "custom-cards.index.tsv")
)
INPUT_ROOTS = (
    "argentum-patches",
    "engine-upgrade/weekly.py",
    "engine-upgrade/audit_patches.py",
    "engine-upgrade/preview-config.json",
    "engine-upgrade/forward-ports",
    "engine-upgrade/eldrazi-temple",
    "engine-upgrade/eldrazi-combined",
    "engine-upgrade/corrupted-crossroads",
    "engine-upgrade/azlask",
    "android/custom-cards/src",
    "android/custom-cards/build.gradle.kts",
    "android/app/src/main/assets/argentum",
    "android/app/libs",
    "android/app/src/main/java/com/arcanatable/engine/ai",
    "android/app/src/main/java/com/arcanatable/engine/costs",
    "android/app/src/main/java/com/arcanatable/engine/priority",
    "android/build.gradle.kts",
    "android/settings.gradle.kts",
)
SEED_PREFLIGHT = ["Fetch and verify green upgraded-engine candidate evidence"]
SEED_ENGINE = [
    "Verify three-way base blob availability (read-only)",
    "Strict patch-series applicability on upgraded upstream",
    "Apply exact Eldrazi Temple battlefield subtype semantics and guard against regression",
    "Strictly apply Azlask colorless-death LKI patch on upgraded Argentum",
    "Inject pinned exact restricted-mana source regressions",
    "Build all four upgraded Argentum runtime JARs",
    "Verify Stack-copying SDK, Ulalek primitives and restricted mana in real engine",
    "Install coherent source-built Argentum engine and bundled library on disposable runner",
]
SEED_CARDS = [
    "Inject original Eldrazi Temple, Corrupted Crossroads and Azlask with exact scenarios",
    "Rebuild and test all bundled custom cards against exact upgraded runtime",
    "Prove source-built Ugin and additional Eldrazi Temple regression executed",
    "Enforce 94 original Eldrazi slots and Azlask scenarios on packaged card assets",
]


B136_SOURCE_STEP = "Integrate every owner-requested B136 feature from exact PR heads"
B136_SOURCE_KEYS = {"DECK_BASE", "DECK_SHA", "LOBBY_BASE", "LOBBY_FIX_SHA",
                    "OUTCOME_SHA", "UGIN_BASE", "UGIN_SHA", "TEMPLE_SHA"}
# These three pins alone can alter compiled Argentum/custom-card output.
# Lobby, blockers, deck UI and tutorial source NEVER bypass the source-only
# combination or their real Android tests; they simply do not force a JAR rebuild.
ENGINE_SOURCE_KEYS = ("UGIN_BASE", "UGIN_SHA", "TEMPLE_SHA")
# Old successfully sealed seed run 40417570112 used v1's over-broad fingerprint.
# Only this ONE audited historical pinset may be translated after verifying
# exact current engine pins and every engine/card input byte and sealed artifact.
LEGACY_B136_SOURCE_PINS = {
    "DECK_BASE": "9dd5fc9c8da129d8aeb58ef6ecbfecc9ff575c9f",
    "DECK_SHA": "5a64b631b9d7185c7fb4102a58afa9aa237746c0",
    "LOBBY_BASE": "79dcd0c58837305fcd1f8790fbf052af214d204b",
    "LOBBY_FIX_SHA": "b162ea69060a50030c55d32f176464cedeeaa57d",
    "OUTCOME_SHA": "abaf13ad913025762db889bde1bf92907cd5a133",
    "UGIN_BASE": "9dd5fc9c8da129d8aeb58ef6ecbfecc9ff575c9f",
    "UGIN_SHA": "49f7f7389b940accf2e2321130407e579cb2ee4c",
    "TEMPLE_SHA": "e57c462d17075ad3d1f2eee249d75edb04b2572a",
}

def b136_source_pins() -> dict[str, str]:
    workflow = WORKFLOW.read_text()
    match = re.search(r"^      - name: " + re.escape(B136_SOURCE_STEP) +
                      r"\n(?P<headers>.*?)^        run: \|\n",
                      workflow, re.MULTILINE | re.DOTALL)
    if match is None:
        raise ValueError("Build 136 integration step is missing")
    pins = dict(re.findall(r"^          ([A-Z_]+): ([a-f0-9]{40})$",
                           match.group("headers"), re.MULTILINE))
    if set(pins) != B136_SOURCE_KEYS:
        raise ValueError("Build 136 source pins changed; re-review")
    return pins

def step_working_directory(name: str) -> str | None:
    workflow = WORKFLOW.read_text()
    steps = list(re.finditer(r"^      - (?:name|uses): .*?$", workflow, re.MULTILINE))
    for i, match in enumerate(steps):
        if match.group().strip() != "- name: " + name:
            continue
        fragment = workflow[match.end():steps[i + 1].start() if i + 1 < len(steps) else len(workflow)]
        directory = re.search(r"^        working-directory: ([a-zA-Z0-9/_-]+)$",
                              fragment, re.MULTILINE)
        return directory.group(1) if directory else None
    raise ValueError(f"Missing pinned workflow step: {name}")

def sha(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def files_for_fingerprint() -> list[Path]:
    result: set[Path] = set()
    for name in INPUT_ROOTS:
        path = Path(name)
        if not path.exists():
            raise ValueError(f"Required input missing: {path}")
        for p in (path.rglob("*") if path.is_dir() else [path]):
            if p.is_file():
                if p.is_symlink():
                    raise ValueError(f"Unexpected source symlink: {p}")
                result.add(p)
    if len(result) < 35:
        raise ValueError(f"Unexpectedly small engine/card source set: {len(result)}")
    return sorted(result)


def fingerprint(legacy: bool = False) -> str:
    # v2 binds only source-built engine/custom-card pins and the exact *bytes*
    # that can change the engine/card output. This keeps reviewed Compose/UI
    # fixes from invalidating a verified 9-file engine cache.
    source_pins = b136_source_pins()
    if legacy:
        # Strict one-way compatibility with the exact B136 v1 seed format.
        # A changed engine pin always fails before any files are restored.
        if any(source_pins[k] != LEGACY_B136_SOURCE_PINS[k]
               for k in ENGINE_SOURCE_KEYS):
            raise ValueError("Legacy seed engine/card pins changed; rebuild seed")
        pins = LEGACY_B136_SOURCE_PINS
    else:
        pins = {k: source_pins[k] for k in ENGINE_SOURCE_KEYS}
    h = hashlib.sha256()
    for v in ("arcana-fast-snapshot-v1" if legacy else "arcana-fast-snapshot-v2",
              UPSTREAM, PATCH_ARTIFACT_SHA256,
              PATCH_SHA256,
              *[f"{k}={v}" for k,v in sorted(PINS.items()) if k != "INCLUDE_ONLINE"],
              *[f"B136:{k}={v}" for k,v in sorted(pins.items())]):
        h.update(v.encode() + b"\\0")
    body = WORKFLOW.read_text()
    for expected in (UPSTREAM, PATCH_ARTIFACT_SHA256, PATCH_SHA256):
        if expected not in body:
            raise ValueError(f"Full validated release workflow pin drift: {expected}")
    for path in files_for_fingerprint():
        data = path.read_bytes()
        h.update(str(path).encode() + b"\\0")
        h.update(str(len(data)).encode() + b"\\0")
        h.update(bytes.fromhex(sha(data)))
    return h.hexdigest()

def named_scripts(names: list[str]) -> dict[str,str]:
    # Extract *exact* step scripts from the same full Build135 workflow, do not
    # maintain a divergent second copy of fragile Argentum patch logic.
    source = WORKFLOW.read_text()
    headings = list(re.finditer(r"^      - (?:name|uses): .*?$", source, re.MULTILINE))
    scripts: dict[str,str] = {}
    for i, match in enumerate(headings):
        label = match.group().strip()
        if not label.startswith("- name: "):
            continue
        name = label[len("- name: "):]
        if name not in names:
            continue
        span = source[match.end():headings[i+1].start() if i+1<len(headings) else len(source)]
        if span.count("        run: |\n") != 1:
            raise ValueError(f"Not a literal run step: {name}")
        text = span.split("        run: |\n",1)[1]
        lines = []
        for line in text.splitlines(keepends=True):
            if not line.strip(): lines.append("\n")
            elif line.startswith("          "): lines.append(line[10:])
            else: raise ValueError(f"Invalid step indentation: {name}: {line[:100]!r}")
        scripts[name] = "".join(lines)
    if sorted(scripts) != sorted(names):
        raise ValueError(f"Missing exact checked scripts: {sorted(set(names)-set(scripts))}")
    return scripts


def run_phase(phase: str) -> None:
    steps = {"preflight": SEED_PREFLIGHT, "engine": SEED_ENGINE, "cards": SEED_CARDS}[phase]
    env = {**os.environ, **PINS}
    for name, shell in named_scripts(steps).items():
        print(f"EXECUTING SOURCE-PINNED FULL-VALIDATION STEP: {name}", flush=True)
        subprocess.run(["bash","-euo","pipefail","-c",shell], check=True,
                       env=env, cwd=step_working_directory(name))


def seed_manifest(directory: Path) -> None:
    if not FINGERPRINT.exists():
        raise ValueError("Fingerprint must be captured BEFORE engine/card outputs changed")
    expected = FINGERPRINT.read_text().strip()
    if not re.fullmatch(r"[0-9a-f]{64}", expected):
        raise ValueError("Invalid content fingerprint")
    hashes = {}
    for name in BUNDLE_FILES:
        p = Path(name)
        if not p.is_file() or p.stat().st_size < 100:
            raise ValueError(f"Missing validated JAR/data export: {name}")
        hashes[name] = sha(p.read_bytes())
    if len([x for x in hashes if x.endswith(".jar")]) != 4:
        raise ValueError("Expected four exact runtime JARs")
    rows = (ASSET_DIR/"all-cards.index.tsv").read_text().splitlines()
    custom = (ASSET_DIR/"custom-cards.index.tsv").read_text().splitlines()
    if len(rows) < 16000 or len(custom) < 50:
        raise ValueError(f"Unexpectedly incomplete exported card library: {len(rows)}+{len(custom)}")
    directory.mkdir(exist_ok=False, parents=True)
    for name in BUNDLE_FILES:
        p = directory/name
        p.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(name, p)
    manifest = {
        "schema": 2, "input_fingerprint": expected,
        "seed_commit": os.environ.get("GITHUB_SHA", "local"),
        "seed_run_id": os.environ.get("GITHUB_RUN_ID", "local"),
        "upstream": UPSTREAM, "engine_patch_sha256": PATCH_SHA256,
        "files": hashes, "exported_cards": len(rows), "custom_cards": len(custom),
    }
    (directory/"manifest.json").write_text(json.dumps(manifest,sort_keys=True,indent=2)+"\n")
    print(f"SEALED: {len(hashes)} SHA-256-checked files; {len(rows)} engine cards; {len(custom)} custom cards")


def restore(directory: Path) -> None:
    # MUST be checked before copying any cached JAR or generated card data.
    manifest = json.loads((directory/"manifest.json").read_text())
    schema = manifest.get("schema")
    if schema not in (1, 2) or manifest.get("upstream") != UPSTREAM:
        raise ValueError("Incompatible or unknown seed format/upstream")
    expected = fingerprint(legacy=(schema == 1))
    if manifest.get("input_fingerprint") != expected:
        raise ValueError("REJECT stale/mismatched engine cache: source/patches/custom cards changed. Refresh seed; no fallback to unverified binary.")
    if manifest.get("engine_patch_sha256") != PATCH_SHA256:
        raise ValueError("Engine patch pin mismatch")
    if set(manifest.get("files",{})) != set(BUNDLE_FILES):
        raise ValueError("Seed artifact has missing/unapproved runtime files")
    if str(manifest.get("seed_run_id")) != os.environ.get("FAST_SEED_RUN_ID"):
        raise ValueError("Seed run identity in artifact does not match exact selected run")
    for name in BUNDLE_FILES:
        file = directory/name
        if not file.is_file() or sha(file.read_bytes()) != manifest["files"][name]:
            raise ValueError(f"Corrupt or changed cached runtime file: {name}")
    for name in BUNDLE_FILES:
        target = Path(name)
        target.parent.mkdir(exist_ok=True,parents=True)
        shutil.copyfile(directory/name,target)
        if sha(target.read_bytes()) != manifest["files"][name]:
            raise ValueError(f"Restored runtime checksum differs: {name}")
    print(f"PASS: all 4 JARs + 5 card/index assets restored from VERIFIED {manifest['seed_run_id']}")
    print(f"PASS: current merged source/patches/custom-card inputs match {expected}")


def version(code: int) -> None:
    if not 136 <= code <= 999999:
        raise ValueError("Fast TEST APK requires new versionCode >=136")
    file = Path("android/app/build.gradle.kts")
    text = file.read_text()
    a='versionCode = 131'
    b='versionName = "0.33.9-build131-argentum-upgrade"'
    if text.count(a)!=1 or text.count(b)!=1:
        raise ValueError("BuildConfig base changed; review rather than silently replacing version")
    text=text.replace(a,f"versionCode = {code}",1).replace(b,f'versionName = "0.33.14-build{code}-fast-test"',1)
    file.write_text(text)
    print(f"PASS: disposable debug-test package versionCode={code}, no production app package signing")


def main() -> None:
    parser=argparse.ArgumentParser()
    choices=("source","preflight","engine","cards","fingerprint","seal","restore","version")
    parser.add_argument("phase",choices=choices)
    parser.add_argument("--bundle",default="fast-engine-bundle")
    parser.add_argument("--version-code",type=int,default=136)
    args=parser.parse_args()
    if args.phase=="source":
        subprocess.run([sys.executable,"tools/lobby_tutorial_join_smoke.py"],check=True)
        # Add the exact Build 136 reviewed source ports after the B135 base.
        script = named_scripts([B136_SOURCE_STEP])[B136_SOURCE_STEP]
        env = {**os.environ, **PINS, **b136_source_pins()}
        subprocess.run(["bash","-euo","pipefail","-c",script], check=True, env=env)
        if not Path("android/custom-cards/src/main/kotlin/com/arcanatable/customcards/cards/UginTheIneffable.kt").is_file():
            raise ValueError("Build 136 Ugin integration is missing")
        # Mandatory B137 source gate: no stale B136-only APK even when old
        # source-assembly scripts themselves completed successfully.
        required = (
            ("android/app/src/main/java/com/arcanatable/TableMultiLobby.kt",
             "val hostLobbyState = wifiMultiLobbyState"),
            ("android/app/src/main/java/com/arcanatable/TableArgentum.kt",
             "argentumBlockGuard.onStep(snapshot.stepKey)"),
            ("android/app/src/main/java/com/arcanatable/ArgentumBlockSubmissionGuard.kt",
             "class ArgentumBlockSubmissionGuard"),
            ("android/app/src/test/java/com/arcanatable/MultiplayerLobbyVisibilityTest.kt",
             "class MultiplayerLobbyVisibilityTest"),
            ("android/app/src/test/java/com/arcanatable/ArgentumBlockSubmissionGuardTest.kt",
             "class ArgentumBlockSubmissionGuardTest"),
        )
        for file, marker in required:
            if not Path(file).is_file() or marker not in Path(file).read_text():
                raise ValueError(f"Next-APK source missing mandatory lobby/combat regression: {file}")
        # Claude PR #300 has passed Android CI. Port its exact 23 reviewed
        # files after the lobby/blocker patch; fail rather than discarding
        # overlapping UI or tutorial behavior.
        subprocess.run([sys.executable, "tools/turbo_tutorial_port.py"], check=True)
        print("PASS: exact B136 plus lobby, blocker and reviewed tutorial; no APK compiled")
    elif args.phase=="fingerprint":
        FINGERPRINT.write_text(fingerprint()+"\n")
        print("SOURCE INPUT FINGERPRINT: "+FINGERPRINT.read_text().strip())
    elif args.phase=="seal": seed_manifest(Path(args.bundle))
    elif args.phase=="restore": restore(Path(args.bundle))
    elif args.phase=="version": version(args.version_code)
    else: run_phase(args.phase)


if __name__=="__main__":
    main()
