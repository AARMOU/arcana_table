#!/usr/bin/env python3
"""Record the provenance of the B140 runtime jars: upstream revision, every patch applied (sha256),
the jars' sha256 and the CI run that built them. Written next to this script as runtime-manifest.json."""
import hashlib
import json
import os
import argparse
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sha = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()


def retain_unchanged_runtime(previous, manifest):
    """A rules-only rebuild must not silently replace its SDK/AI dependencies."""
    assert previous["upstream"] == manifest["upstream"], "upstream changed"
    assert set(previous["jars"]) == set(manifest["jars"]), "runtime modules changed"
    retained = {name: digest for name, digest in previous["jars"].items()
                if name != "argentum-rules-engine.jar"}
    assert all(manifest["jars"][name] == digest for name, digest in retained.items()), "retained runtime JAR changed"
    assert all(manifest["patches"].get(path) == entry for path, entry in previous["patches"].items()), "previous patch provenance changed"
    manifest["retainedRuntime"] = previous.get("retainedRuntime", {
        "builtBy": previous["builtBy"], "jars": retained})
    manifest["builtBy"]["modules"] = ["rules-engine"]
    manifest["build"] = "engine-upgrade/build140/apply_engine.sh, then gradle :rules-engine:test :rules-engine:jar; SDK/tooling/AI retained and hash-verified"
    return manifest


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--rules-only", action="store_true")
    args = parser.parse_args()
    patches = sorted((ROOT / "argentum-patches").glob("*.patch"))
    patches += [ROOT / "engine-upgrade/eldrazi-temple/patches/0001-colorless-eldrazi-source-mana.patch",
                ROOT / "engine-upgrade/eldrazi-combined/patches/0002-devoid-after-temple.patch",
                ROOT / "engine-upgrade/build140/patches/0001-devoid-sdk-surface.patch",
                ROOT / "engine-upgrade/build140/patches/0002-temple-projected-source-subtypes.patch",
                ROOT / "engine-upgrade/build140/patches/0003-azlask-exact-colors-trigger.patch"]
    report = json.loads((ROOT / "engine-upgrade/patch-report.json").read_text())
    status = {p["patch"]: p["status"] for p in report["patches"]}
    manifest = {
    "schema": 1,
    "upstream": report["upstream"],
    "patches": {str(p.relative_to(ROOT)): {"sha256": sha(p), "status": status.get(p.name, "applied")} for p in patches},
    "jars": {p.name: sha(p) for p in sorted((ROOT / "android/app/libs").glob("argentum-*.jar"))},
    "builtBy": {"workflow": os.environ.get("GITHUB_WORKFLOW"), "run": os.environ.get("GITHUB_RUN_ID"),
                "sourceCommit": os.environ.get("GITHUB_SHA")},
    "build": "engine-upgrade/build140/apply_engine.sh, then gradle :mtg-sdk:jar :mtg-sdk-tooling:jar :rules-engine:jar :ai:jar",
    }
    destination = Path(__file__).resolve().parent / "runtime-manifest.json"
    if args.rules_only:
        retain_unchanged_runtime(json.loads(destination.read_text()), manifest)
    destination.write_text(json.dumps(manifest, indent=2) + "\n")
    print(json.dumps(manifest["jars"], indent=1))


if __name__ == "__main__":
    main()
