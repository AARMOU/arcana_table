"""Validate a source-only solver overlay without writing app/libs or adopting an engine.

Baseline: python investigation/filter-land-51/validate.py --baseline
Candidate: python investigation/filter-land-51/validate.py
Full custom cards: add --all
Requires the existing Android/JDK environment and a local Argentum git object store.
"""
from __future__ import annotations

import argparse
import ast
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
from datetime import datetime, timezone

ROOT = Path(__file__).resolve().parents[2]
HERE = Path(__file__).resolve().parent
BUILD = HERE / "build"
SOURCE = "rules-engine/src/main/kotlin/com/wingedsheep/engine/mechanics/mana/ManaSolver.kt"
UPSTREAM = "f5dc31813bfe12dbc12c412f69687452e86bd63f"
RUNTIME_COMMIT = "7e823fd747f31eb3a920c48f6a885fc34b0cb4be"


def output(*args: str, cwd: Path = ROOT) -> bytes:
    return subprocess.check_output(args, cwd=cwd)


def hashes() -> dict[str, str]:
    return {p.name: hashlib.sha256(p.read_bytes()).hexdigest()
            for p in sorted((ROOT / "android/app/libs").glob("argentum-*.jar"))}


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--baseline", action="store_true")
    parser.add_argument("--all", action="store_true")
    parser.add_argument("--engine-source", type=Path, default=ROOT / "investigation/crayola-filter-engine")
    args = parser.parse_args()
    BUILD.mkdir(parents=True, exist_ok=True)
    lock = BUILD / "validation.lock"
    # Fail closed rather than starting a duplicate Crayola validation. A stale lock is inspected manually.
    fd = os.open(lock, os.O_CREAT | os.O_EXCL | os.O_WRONLY)
    os.write(fd, str(os.getpid()).encode())
    os.close(fd)
    before = hashes()
    mode = "baseline" if args.baseline else "candidate"
    report = {"mode": mode, "started": datetime.now(timezone.utc).isoformat(),
              "upstream": UPSTREAM, "runtime_commit": RUNTIME_COMMIT, "jars_before": before}
    try:
        for name in before:
            path = "android/app/libs/" + name
            expected = output("git", "rev-parse", f"{RUNTIME_COMMIT}:{path}").strip()
            actual = output("git", "hash-object", str(ROOT / path)).strip()
            if actual != expected:
                raise RuntimeError(f"{name} differs from the recorded runtime; re-establish provenance first")
        command = [str(ROOT / "android" / ("gradlew.bat" if os.name == "nt" else "gradlew")),
                   "-p", str(ROOT / "android"), "--offline", "--no-daemon", "--max-workers=2"]
        if not args.baseline:
            # Build one source file in a disposable git directory; never reset or edit the source store.
            stage = BUILD / "stage"
            if stage.exists():
                if stage.resolve().parent != BUILD.resolve():
                    raise RuntimeError("Unsafe staging path")
                shutil.rmtree(stage)
            stage.mkdir()
            subprocess.run(["git", "init", "-q", str(stage)], check=True)
            source_file = stage / SOURCE
            source_file.parent.mkdir(parents=True)
            source_file.write_bytes(output("git", "-C", str(args.engine_source.resolve()),
                                           "show", f"{UPSTREAM}:{SOURCE}"))
            audit = ast.parse(output("git", "show", f"{RUNTIME_COMMIT}:engine-upgrade/audit_patches.py"))
            mandatory = next(ast.literal_eval(n.value) for n in audit.body
                             if isinstance(n, ast.Assign) and any(isinstance(t, ast.Name)
                                and t.id == "MANDATORY" for t in n.targets))
            for name in mandatory:
                patch = output("git", "show", f"{RUNTIME_COMMIT}:argentum-patches/{name}")
                if ("diff --git a/" + SOURCE + " b/" + SOURCE).encode() not in patch:
                    continue
                for check in (True, False):
                    cmd = ["git", "apply", "--include=" + SOURCE]
                    if check:
                        cmd.append("--check")
                    subprocess.run(cmd + ["-"], input=patch, cwd=stage, check=True)
            patch = (HERE / "filter-retry.patch").read_bytes()
            subprocess.run(["git", "apply", "--check", "-"], input=patch, cwd=stage, check=True)
            subprocess.run(["git", "apply", "-"], input=patch, cwd=stage, check=True)
            overlay = BUILD / "overlay"
            overlay.mkdir(exist_ok=True)
            shutil.copyfile(source_file, overlay / "ManaSolver.kt")
            report["overlay_sha256"] = hashlib.sha256(source_file.read_bytes()).hexdigest()
            command += ["-I", str(HERE / "overlay.init.gradle")]
        command += [":custom-cards:test", "--rerun-tasks"]
        if not args.all:
            command += ["--tests", "com.arcanatable.customcards.FilterLandAffordabilityTest"]
        with (BUILD / f"{mode}.log").open("w", encoding="utf-8") as log:
            process = subprocess.Popen(command, cwd=ROOT, stdout=subprocess.PIPE,
                                       stderr=subprocess.STDOUT, text=True, encoding="utf-8", errors="replace")
            assert process.stdout is not None
            for line in process.stdout:
                print(line, end="", flush=True)
                log.write(line)
            result = process.wait()
        report["exit_code"] = result
        reports = ROOT / "android/custom-cards/build/test-results/test"
        if reports.exists():
            destination = BUILD / (mode + "-xml")
            destination.mkdir(exist_ok=True)
            for xml in reports.glob("TEST-*.xml"):
                shutil.copyfile(xml, destination / xml.name)
        return result
    finally:
        report["jars_after"] = hashes()
        report["finished"] = datetime.now(timezone.utc).isoformat()
        (BUILD / f"{mode}-manifest.json").write_text(json.dumps(report, indent=2) + "\n")
        lock.unlink()
        if report["jars_after"] != before:
            raise RuntimeError("Runtime jars changed during validation")


if __name__ == "__main__":
    sys.exit(main())
