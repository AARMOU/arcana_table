#!/usr/bin/env python3
"""Fast, read-only preflight for two recurring Arcana Compose test compile failures.

This is intentionally a narrow guard, not a Kotlin compiler. Run it before Gradle:
    python3 tools/check_compose_test_rules.py
or pass a Kotlin test directory / individual .kt files explicitly.
"""

from __future__ import annotations

import argparse
from pathlib import Path
import re
import sys

# These are SemanticsNodeInteraction methods, not importable top-level extensions.
INVALID_IMPORT = re.compile(
    r"^\s*import\s+androidx\.compose\.ui\.test\.(assertExists|assertDoesNotExist)\s*(?://.*)?$"
)
EMPTY_RULE = re.compile(
    r"\b(?:val|var)\s+(\w+)\s*=\s*createEmptyComposeRule\s*\(\s*\)"
)


def violations(path: Path, content: str) -> list[str]:
    """Return stable path:line diagnostics for invalid Compose test patterns."""
    lines = content.splitlines()
    issues: list[str] = []
    empty_rules: dict[str, int] = {}
    for number, line in enumerate(lines, 1):
        match = INVALID_IMPORT.match(line)
        if match:
            issues.append(
                f"{path}:{number}: invalid Compose import {match.group(1)}; "
                "call the method on SemanticsNodeInteraction without importing it"
            )
        if line.lstrip().startswith("//"):
            continue
        rule = EMPTY_RULE.search(line)
        if rule:
            empty_rules[rule.group(1)] = number

    for rule_name, declaration_line in empty_rules.items():
        call = re.compile(rf"^\s*{re.escape(rule_name)}\s*\.\s*setContent\s*(?:\(|\{{)")
        for number, line in enumerate(lines, 1):
            if call.search(line):
                issues.append(
                    f"{path}:{number}: {rule_name}.setContent is unavailable on "
                    f"createEmptyComposeRule (declared at line {declaration_line}); "
                    "use createComposeRule for content ownership"
                )
    return issues


def kotlin_files(inputs: list[Path]) -> list[Path]:
    found: set[Path] = set()
    for entry in inputs:
        if entry.is_file() and entry.suffix == ".kt":
            found.add(entry)
        elif entry.is_dir():
            found.update(entry.rglob("*.kt"))
        else:
            raise FileNotFoundError(f"Kotlin test input does not exist: {entry}")
    return sorted(found)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "paths", nargs="*", type=Path,
        help="Kotlin test files or directories (default: android/app/src/test)",
    )
    args = parser.parse_args(argv)
    try:
        files = kotlin_files(args.paths or [Path("android/app/src/test")])
    except FileNotFoundError as error:
        print(error, file=sys.stderr)
        return 2
    issues = [item for file in files for item in violations(file, file.read_text(encoding="utf-8"))]
    for issue in issues:
        print(issue, file=sys.stderr)
    if issues:
        print(f"Compose test preflight FAILED: {len(issues)} issue(s) in {len(files)} Kotlin files", file=sys.stderr)
        return 1
    print(f"Compose test preflight passed: {len(files)} Kotlin files")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
