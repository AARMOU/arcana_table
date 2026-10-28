#!/usr/bin/env python3
"""Fail CI on regex literals accepted by desktop Java but rejected by Android ICU.

Scans static Kotlin Regex and Java Pattern.compile string-literal calls
in Android app and custom-card Java/Kotlin sources. Dynamic patterns remain the
responsibility of runtime/on-device tests.
"""

from __future__ import annotations

import argparse
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCES = (ROOT / "android/app/src", ROOT / "android/custom-cards/src")
CALL = re.compile(r"\b(?:Regex|Pattern\s*\.\s*compile)\s*\(")
QUANTIFIER = re.compile(r"\{\d+(?:,\d*)?\}")


def code_only(source: str) -> str:
    """Replace Kotlin/Java comments and literals with whitespace, keeping offsets."""
    out = list(source)
    n = len(source)
    i = 0
    while i < n:
        start = i
        if source.startswith("//", i):
            j = source.find("\n", i)
            i = n if j < 0 else j
        elif source.startswith("/*", i):
            depth = 1
            i += 2
            while i < n and depth:
                if source.startswith("/*", i):
                    depth += 1
                    i += 2
                elif source.startswith("*/", i):
                    depth -= 1
                    i += 2
                else:
                    i += 1
        elif source.startswith('"""', i):
            j = source.find('"""', i + 3)
            i = n if j < 0 else j + 3
        elif source[i] in ('"', "'"):
            quote = source[i]
            i += 1
            while i < n:
                if source[i] == "\\":
                    i += 2
                elif source[i] == quote:
                    i += 1
                    break
                else:
                    i += 1
        else:
            i += 1
            continue
        for k in range(start, min(i, n)):
            if out[k] != "\n":
                out[k] = " "
    return "".join(out)


def literal(source: str, pos: int) -> str | None:
    """Return a decoded static first argument of Regex/Pattern.compile."""
    if source.startswith('"""', pos):
        end = source.find('"""', pos + 3)
        return None if end < 0 else source[pos + 3:end]
    if pos >= len(source) or source[pos] != '"':
        return None
    pieces = []
    i = pos + 1
    while i < len(source):
        ch = source[i]
        if ch == '"':
            return "".join(pieces)
        if ch == "\\" and i + 1 < len(source):
            nxt = source[i + 1]
            pieces.append({"n": "\n", "r": "\r", "t": "\t", "b": "\b", "f": "\f"}.get(nxt, nxt))
            i += 2
        else:
            pieces.append(ch)
            i += 1
    return None


def issues(pattern: str) -> list[str]:
    """Check Android-specific syntax hazards, not general regex validity."""
    bad = []
    depth = 0
    i = 0
    while i < len(pattern):
        ch = pattern[i]
        if ch == "\\" and i + 1 < len(pattern):
            nxt = pattern[i + 1]
            if nxt == "Q":  # Regex literal quoting: \Q...\E
                end = pattern.find(r"\E", i + 2)
                i = len(pattern) if end < 0 else end + 2
                continue
            if depth and nxt == "-":
                bad.append(r"escaped hyphen \- inside a character class")
            if not depth and nxt in "pPxN" and i + 2 < len(pattern) and pattern[i + 2] == "{":
                end = pattern.find("}", i + 3)
                if end >= 0:
                    i = end + 1
                    continue
            i += 2
            continue
        if ch == "[":
            depth += 1
        elif ch == "]":
            if depth:
                depth -= 1
            else:
                bad.append("unescaped ] outside a character class")
        elif ch == "{" and not depth:
            quantifier = QUANTIFIER.match(pattern, i)
            if quantifier:
                i = quantifier.end()
                continue
        elif ch == "}" and not depth:
            bad.append("unescaped } outside a character class")
        i += 1
    return bad


def scan(source: str) -> list[tuple[int, str]]:
    findings = []
    for match in CALL.finditer(code_only(source)):
        pos = match.end()
        while pos < len(source) and source[pos].isspace():
            pos += 1
        value = literal(source, pos)
        if value is None or "${" in value:
            # Kotlin interpolation builds a dynamic pattern; needs on-device testing.
            continue
        line = source.count("\n", 0, match.start()) + 1
        findings.extend((line, issue) for issue in issues(value))
    return findings


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("paths", nargs="*", type=Path, help="explicit source files or trees (default: app and custom-cards)")
    args = parser.parse_args()
    roots = args.paths or SOURCES
    files = sorted({p for root in roots for p in (root.rglob("*") if root.is_dir() else (root,)) if p.is_file() and p.suffix in {".kt", ".java"}})
    failed = 0
    for path in files:
        for line, issue in scan(path.read_text(encoding="utf-8")):
            print(f"{path}:{line}: Android-incompatible regex: {issue}")
            failed += 1
    print(f"Android regex scan: {len(files)} source files, {failed} issue(s)")
    return 1 if failed else 0


if __name__ == "__main__":
    raise SystemExit(main())
