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
import signal
import uuid
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


class UnconfirmedProcessTree(RuntimeError):
    """Keep the exclusive lock for manual inspection when cleanup is uncertain."""


def stop_tree(process: subprocess.Popen) -> None:
    try:
        if os.name == "nt":
            # A dead root cannot be used to discover its remaining descendants.
            if process.poll() is not None:
                raise RuntimeError("Root exited before tree termination could be confirmed")
            subprocess.run(["taskkill", "/PID", str(process.pid), "/T", "/F"],
                           check=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                           timeout=30)
        else:
            os.killpg(process.pid, signal.SIGKILL)
            process.wait(timeout=30)
            # Reaping the root alone does not establish that every descendant exited.
            raise RuntimeError("Process-group termination needs manual descendant inspection")
        process.wait(timeout=30)
    except BaseException as error:
        raise UnconfirmedProcessTree("Process tree cleanup uncertain; retain validation.lock") from error


def run_logged(command: list[str], log) -> int:
    options = {} if os.name == "nt" else {"start_new_session": True}
    process = subprocess.Popen(command, cwd=ROOT, stdout=subprocess.PIPE,
                               stderr=subprocess.STDOUT, text=True, encoding="utf-8",
                               errors="replace", **options)
    try:
        assert process.stdout is not None
        for line in process.stdout:
            print(line, end="", flush=True)
            log.write(line)
        return process.wait()
    except BaseException:
        stop_tree(process)
        raise
    finally:
        if process.stdout is not None:
            try:
                process.stdout.close()
            except BaseException:
                # Do not mask an uncertain-tree error and accidentally release its lock.
                pass


def collect_reports(reports: Path, run: Path, result: int) -> None:
    # Reports were cleared before invocation; failed commands never publish XML.
    if result != 0:
        return
    files = sorted(reports.glob("TEST-*.xml"))
    if not files:
        raise RuntimeError("Successful command produced no current JUnit XML")
    destination = run / "xml"
    destination.mkdir()
    for xml in files:
        shutil.copyfile(xml, destination / xml.name)


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
    run = BUILD / (datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%S") + "-" + uuid.uuid4().hex)
    run.mkdir()
    retain_lock = False
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
        reports = ROOT / "android/custom-cards/build/test-results/test"
        if reports.exists():
            if reports.is_symlink() or reports.resolve() != reports.absolute():
                raise RuntimeError("Unsafe test report path")
            shutil.rmtree(reports)
        with (run / f"{mode}.log").open("w", encoding="utf-8") as log:
            result = run_logged(command, log)
        report["exit_code"] = result
        collect_reports(reports, run, result)
        return result
    except UnconfirmedProcessTree:
        retain_lock = True
        report["cleanup"] = "UNKNOWN; lock retained for manual process inspection"
        raise
    finally:
        report["jars_after"] = hashes()
        report["finished"] = datetime.now(timezone.utc).isoformat()
        (run / "manifest.json").write_text(json.dumps(report, indent=2) + "\n")
        if not retain_lock:
            lock.unlink()
        if report["jars_after"] != before:
            raise RuntimeError("Runtime jars changed during validation")


if __name__ == "__main__":
    sys.exit(main())
