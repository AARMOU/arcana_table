#!/usr/bin/env python3
"""Run only trusted-main JUnit suites against an exact, separately checked-out candidate.
No agent credentials, no external Python packages, no arbitrary shell commands.
"""
import argparse
import json
import os
from pathlib import Path
import re
import shutil
import sys
import xml.etree.ElementTree as ET

KEY = re.compile(r"^[a-z][a-z0-9]*(?:-[a-z0-9]+)*$")
TEST = re.compile(r"^certification/[A-Z][A-Za-z0-9]*IndependentCertificationTest\.kt$")
CLASS = re.compile(r"^com\.arcanatable\.customcards\.[A-Z][A-Za-z0-9]*IndependentCertificationTest$")
DEST = Path("android/custom-cards/src/test/kotlin/com/arcanatable/customcards/")
RESULTS = Path("android/custom-cards/build/test-results/test")


def load_suite(manifest, key):
    data = json.loads(Path(manifest).read_text(encoding="utf-8"))
    if not KEY.fullmatch(key) or data.get("schema") != 1 or not isinstance(data.get("suites"), list):
        raise ValueError("Invalid certification suite selection")
    match = [s for s in data["suites"] if s.get("key") == key]
    if len(match) != 1:
        raise ValueError("Unregistered or ambiguous card suite")
    suite = match[0]
    if not TEST.fullmatch(suite.get("source", "")) or not CLASS.fullmatch(suite.get("testClass", "")):
        raise ValueError("Unsafe certification test path or class")
    if not suite["testClass"].endswith(Path(suite["source"]).stem):
        raise ValueError("Suite class does not match its source")
    if type(suite.get("tests")) is not int or not 3 <= suite["tests"] <= 100:
        raise ValueError("Invalid required test count")
    return suite


def prepare(suite, trusted, candidate, output_file):
    trusted = Path(trusted).resolve()
    candidate = Path(candidate).resolve()
    src = (trusted / suite["source"]).resolve(strict=True)
    if not src.is_relative_to(trusted / "certification"):
        raise ValueError("Test escaped trusted certification directory")
    destdir = candidate / DEST
    if not destdir.is_dir() or destdir.is_symlink() or not destdir.resolve().is_relative_to(candidate):
        raise ValueError("Candidate test destination missing or unsafe")
    dst = destdir / Path(suite["source"]).name
    if dst.is_symlink():
        raise ValueError("Candidate source path is a symlink")
    shutil.copyfile(src, dst)
    if dst.stat().st_size < 100:
        raise ValueError("Independent test file appears incomplete")
    # Step outputs contain only validation-constrained literals.
    with Path(output_file).open("a", encoding="utf-8") as f:
        f.write(f"testClass={suite['testClass']}\n")
    print(f"Trusted independent suite loaded: {suite['testClass']}")


def verify(suite, candidate):
    candidate = Path(candidate).resolve()
    xml = candidate / RESULTS / ("TEST-" + suite["testClass"] + ".xml")
    xml = xml.resolve(strict=True)
    if not xml.is_relative_to(candidate / RESULTS):
        raise ValueError("JUnit report escaped test-results directory")
    root = ET.parse(xml).getroot()
    count = int(root.attrib.get("tests", 0))
    blocked = {k: int(root.attrib.get(k, 0)) for k in ("failures", "errors", "skipped")}
    children = root.findall("testcase")
    if count != suite["tests"] or len(children) != suite["tests"] or any(blocked.values()):
        raise ValueError(f"Incomplete independent verification: tests={count}, cases={len(children)}, {blocked}")
    if any(case.find("failure") is not None or case.find("error") is not None or
           case.find("skipped") is not None for case in children):
        raise ValueError("A JUnit case did not pass")
    print(f"{count} trusted independent scenarios passed for {suite['name']}")


def main():
    p = argparse.ArgumentParser()
    p.add_argument("command", choices=("prepare", "verify"))
    p.add_argument("--manifest", required=True)
    p.add_argument("--key", required=True)
    p.add_argument("--trusted", default="trusted")
    p.add_argument("--candidate", default="candidate")
    p.add_argument("--github-output")
    args = p.parse_args()
    suite = load_suite(args.manifest, args.key)
    if args.command == "prepare":
        if not args.github_output:
            p.error("prepare requires --github-output")
        prepare(suite, args.trusted, args.candidate, args.github_output)
    else:
        verify(suite, args.candidate)


if __name__ == "__main__":
    try:
        main()
    except (ValueError, OSError, ET.ParseError) as exc:
        sys.exit(f"Certification blocked: {exc}")
