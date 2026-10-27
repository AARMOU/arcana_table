#!/usr/bin/env python3
"""Fail-closed source/asset parity check for the B137 keyword-probe regression.

The Kotlin test accepts a card from EITHER shipped Argentum JSONL, but rejects
shadowing (the same name in both). This preflight must use the same contract;
requiring every fixture to be custom would falsely reject upstream migrations.
Run after :custom-cards:exportCards and before expensive Android compilation.
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path
import re


TEST_METHOD = "cardsDoNotInheritKeywordLabelsFromCreatedTokensOrAnimationEffects"
DEFAULT_TEST = Path("android/app/src/test/java/com/arcanatable/DeckSupportTest.kt")
DEFAULT_ASSETS = Path("android/app/src/main/assets/argentum")


def expected_names(source: str) -> set[str]:
    match = re.search(
        r"\bfun\s+" + TEST_METHOD +
        r"\s*\(\s*\)\s*\{[\s\S]*?\bval\s+names\s*=\s*setOf\s*\(([\s\S]*?)\)",
        source,
    )
    if not match:
        raise ValueError("keyword-probe test or its named fixture set is missing")
    names = set(re.findall(r'"([^"\n]+)"', match.group(1)))
    if not names:
        raise ValueError("keyword-probe fixture set is empty")
    return names


def present_names(path: Path, expected: set[str]) -> set[str]:
    if not path.is_file():
        raise ValueError(f"shipped card asset missing: {path}")
    present: set[str] = set()
    with path.open(encoding="utf-8") as stream:
        for line_number, line in enumerate(stream, 1):
            if not line.strip():
                continue
            try:
                card = json.loads(line)
            except json.JSONDecodeError as exc:
                raise ValueError(f"invalid JSONL {path}:{line_number}: {exc}") from exc
            if not isinstance(card, dict) or not isinstance(card.get("name"), str):
                raise ValueError(f"invalid card name in {path}:{line_number}")
            if card["name"] in expected:
                present.add(card["name"])
    return present


def verify(test: Path, assets: Path) -> tuple[int, int, int]:
    if not test.is_file():
        raise ValueError(f"keyword-probe test source missing: {test}")
    expected = expected_names(test.read_text(encoding="utf-8"))
    custom = present_names(assets / "custom-cards.jsonl", expected)
    upstream = present_names(assets / "all-cards.jsonl", expected)
    overlap = sorted(custom & upstream)
    if overlap:
        raise ValueError("custom/upstream card shadowing: " + ", ".join(overlap))
    missing = sorted(expected - custom - upstream)
    if missing:
        raise ValueError("combined-source test/asset provenance mismatch: " +
                         ", ".join(missing) +
                         "; reconcile the reviewed card source/export before Android CI")
    return len(expected), len(custom), len(upstream)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--test", type=Path, default=DEFAULT_TEST)
    parser.add_argument("--assets", type=Path, default=DEFAULT_ASSETS)
    args = parser.parse_args()
    try:
        total, custom, upstream = verify(args.test, args.assets)
    except (OSError, ValueError) as exc:
        parser.exit(1, f"FAIL: {exc}\n")
    print(f"PASS: {total} keyword probes present ({custom} custom, "
          f"{upstream} upstream); no custom/upstream shadowing")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
