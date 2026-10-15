#!/usr/bin/env python3
"""Generate a deterministic, type-stratified ETB benchmark from Scryfall JSONL.

Download the current Oracle Cards bulk file from https://api.scryfall.com/bulk-data
and pass its .jsonl.gz path with --input. The committed fixture stores only the
trigger condition, card name, and type line needed by the offline unit test.
"""

import argparse
import gzip
import json
import random
import re
from pathlib import Path


QUOTAS = {"CREATURE_ETB": 625, "PERMANENT_ETB": 250, "LAND_ETB": 125}
SEED = 20261014
TRIGGER = re.compile(
    r"(?i)^(?:whenever|when)\s+(.+?)\s+enters(?:\s+the battlefield)?"
    r"(?:\s+(?:under your control|under an opponent's control))?$"
)


def event_for_type(type_line: str) -> str | None:
    if re.search(r"(?i)\bland\b", type_line):
        return "LAND_ETB"
    if re.search(r"(?i)\bcreature\b", type_line):
        return "CREATURE_ETB"
    if re.search(r"(?i)\b(artifact|enchantment|planeswalker|battle)\b", type_line):
        return "PERMANENT_ETB"
    return None


def self_references(subject: str, face_name: str) -> bool:
    return subject.casefold() in {face_name.casefold(), "this"} or bool(re.fullmatch(r"(?i)this\s+[a-z-]+", subject))


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", required=True, type=Path, help="Scryfall Oracle Cards .jsonl.gz file")
    parser.add_argument(
        "--output", type=Path,
        default=Path("app/src/test/resources/etb-card-benchmark.json"),
    )
    args = parser.parse_args()

    pools = {event: {} for event in QUOTAS}
    with gzip.open(args.input, "rt", encoding="utf-8") as source:
        for raw in source:
            card = json.loads(raw)
            oracle_id = card.get("oracle_id")
            if not oracle_id:
                continue
            faces = card.get("card_faces") or [card]
            for face in faces:
                name = face.get("name") or card.get("name", "")
                type_line = face.get("type_line") or card.get("type_line", "")
                event = event_for_type(type_line)
                if not name or not event:
                    continue
                oracle = face.get("oracle_text") or card.get("oracle_text") or ""
                for line in oracle.splitlines():
                    line = line.strip()
                    if "," not in line:
                        continue
                    condition = line.split(",", 1)[0].strip()
                    condition = condition.split("—")[-1].strip()
                    match = TRIGGER.fullmatch(condition)
                    if match and self_references(match.group(1).strip(), name):
                        pools[event].setdefault(oracle_id, {
                            "card": name,
                            "typeLine": type_line,
                            "condition": condition,
                            "event": event,
                            "oracleId": oracle_id,
                        })
                        break

    rng = random.Random(SEED)
    rows = []
    selected_ids = set()
    for event in ("LAND_ETB", "PERMANENT_ETB", "CREATURE_ETB"):
        quota = QUOTAS[event]
        candidates = list(pools[event].values())
        candidates = [row for row in candidates if row["oracleId"] not in selected_ids]
        if len(candidates) < quota:
            raise SystemExit(f"Need {quota} {event} examples; found {len(candidates)}")
        rng.shuffle(candidates)
        selected = candidates[:quota]
        selected_ids.update(row["oracleId"] for row in selected)
        rows.extend(selected)
    rng.shuffle(rows)

    output = {
        "source": "Scryfall Oracle Cards bulk data",
        "sourceUpdated": "2026-10-14",
        "purpose": "Offline regression cases for self-referencing enters triggers; not an estimate of all game-action coverage.",
        "sampleSeed": SEED,
        "strata": QUOTAS,
        "cards": rows,
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(output, ensure_ascii=False, separators=(",", ":")) + "\n", encoding="utf-8")
    print(f"Wrote {len(rows)} cards to {args.output}")
    print("Available candidates: " + ", ".join(f"{event}={len(pools[event])}" for event in QUOTAS))


if __name__ == "__main__":
    main()
