#!/usr/bin/env python3
"""Runner-only, fail-closed migration of two legacy Argentum JSONL wire shapes.

The shipped all-cards.jsonl was exported against the earlier SDK. The pinned
upgraded SDK replaces these two serial names with composable equivalents.
Never write this migration to production assets or treat it as card certification.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import tempfile
import unittest

OLD_MANA = "AddAnyColorManaSpendOnChosenType"
OLD_COUNTERS = "DoubleCounterPlacement"


def migrate(value, counts):
    if isinstance(value, list):
        return [migrate(v, counts) for v in value]
    if not isinstance(value, dict):
        return value
    converted = {k: migrate(v, counts) for k, v in value.items()}
    tag = converted.get("type")
    if tag == OLD_MANA:
        allowed = {"type", "amount", "creatureOnly", "riders"}
        if set(converted) - allowed or not isinstance(converted.get("creatureOnly", False), bool):
            raise ValueError(f"Unreviewed {OLD_MANA} wire shape: {sorted(converted)}")
        result = {"type": "AddManaOfChoice"}
        if "amount" in converted:
            result["amount"] = converted["amount"]
        result["restriction"] = {
            "type": "OfChosenType",
            "creatureOnly": converted.get("creatureOnly", False),
        }
        if "riders" in converted:
            result["riders"] = converted["riders"]
        counts[OLD_MANA] += 1
        return result
    if tag == OLD_COUNTERS:
        allowed = {"type", "placedByYou", "appliesTo", "effectsOnly"}
        if set(converted) - allowed or not isinstance(converted.get("placedByYou", False), bool):
            raise ValueError(f"Unreviewed {OLD_COUNTERS} wire shape: {sorted(converted)}")
        result = {"type": "ScaleCounterPlacement", "scale": "DOUBLE"}
        if converted.get("placedByYou", False):
            result["placer"] = {"type": "You"}
        if "appliesTo" in converted:
            result["appliesTo"] = converted["appliesTo"]
        if "effectsOnly" in converted:
            result["effectsOnly"] = converted["effectsOnly"]
        counts[OLD_COUNTERS] += 1
        return result
    return converted


def migrate_jsonl(path):
    path = Path(path)
    original_hash = hashlib.sha256(path.read_bytes()).hexdigest()
    counts = {OLD_MANA: 0, OLD_COUNTERS: 0}
    rows = 0
    fd, temporary = tempfile.mkstemp(prefix=".argentum-compat-", dir=path.parent, text=True)
    try:
        with os.fdopen(fd, "w", encoding="utf-8", newline="\n") as output:
            with path.open(encoding="utf-8") as source:
                for line in source:
                    if not line.strip():
                        raise ValueError(f"Blank JSONL row {rows + 1}")
                    old = json.loads(line)
                    if not isinstance(old, dict) or not isinstance(old.get("name"), str):
                        raise ValueError(f"Invalid card row {rows + 1}")
                    before = tuple(counts.values())
                    new = migrate(old, counts)
                    if new.get("name") != old["name"]:
                        raise AssertionError("Card identity changed")
                    if tuple(counts.values()) == before:
                        output.write(line)
                    else:
                        output.write(json.dumps(new, ensure_ascii=False, separators=(",", ":")) + "\n")
                    rows += 1
        if rows == 0 or any(v == 0 for v in counts.values()):
            raise ValueError(f"Expected both legacy wire shapes in full library; found {counts}")
        os.replace(temporary, path)
    finally:
        if os.path.exists(temporary):
            os.unlink(temporary)
    print(json.dumps({"rows": rows, "migrations": counts, "before_sha256": original_hash,
                      "after_sha256": hashlib.sha256(path.read_bytes()).hexdigest()}))


class MigrationTests(unittest.TestCase):
    def test_cavern_mana_preserves_chosen_type_and_uncounterable_rider(self):
        counts = {OLD_MANA: 0, OLD_COUNTERS: 0}
        old = {"type": OLD_MANA, "creatureOnly": True,
               "riders": ["MakesSpellUncounterable"]}
        self.assertEqual(migrate(old, counts),
                         {"type": "AddManaOfChoice",
                          "restriction": {"type": "OfChosenType", "creatureOnly": True},
                          "riders": ["MakesSpellUncounterable"]})
        self.assertEqual(counts[OLD_MANA], 1)

    def test_unclaimed_territory_allows_chosen_type_abilities(self):
        counts = {OLD_MANA: 0, OLD_COUNTERS: 0}
        self.assertEqual(migrate({"type": OLD_MANA}, counts)["restriction"],
                         {"type": "OfChosenType", "creatureOnly": False})

    def test_counter_doubling_preserves_event_filter_and_effects_only(self):
        counts = {OLD_MANA: 0, OLD_COUNTERS: 0}
        event = {"type": "CounterPlacementEvent", "recipient": {"type": "RecipientObject",
                                                                  "filter": "permanent ctrl:you"}}
        new = migrate({"type": OLD_COUNTERS, "appliesTo": event, "effectsOnly": True}, counts)
        self.assertEqual(new, {"type": "ScaleCounterPlacement", "scale": "DOUBLE",
                               "appliesTo": event, "effectsOnly": True})

    def test_placed_by_you_maps_to_source_controller_not_any_placer(self):
        counts = {OLD_MANA: 0, OLD_COUNTERS: 0}
        self.assertEqual(migrate({"type": OLD_COUNTERS, "placedByYou": True}, counts)["placer"],
                         {"type": "You"})

    def test_unknown_legacy_fields_fail_closed(self):
        for tag in (OLD_MANA, OLD_COUNTERS):
            with self.subTest(tag=tag), self.assertRaises(ValueError):
                migrate({"type": tag, "newUnknownSemantic": True},
                        {OLD_MANA: 0, OLD_COUNTERS: 0})

    def test_unrelated_card_content_unchanged(self):
        counts = {OLD_MANA: 0, OLD_COUNTERS: 0}
        old = {"name": "Eldrazi Temple", "script": {"activatedAbilities": [
            {"effect": {"type": "AddColorlessMana", "amount": 2}}]}}
        self.assertEqual(migrate(old, counts), old)
        self.assertEqual(counts, {OLD_MANA: 0, OLD_COUNTERS: 0})


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--self-test", action="store_true")
    parser.add_argument("--apply")
    args = parser.parse_args()
    if args.self_test:
        result = unittest.defaultTestLoader.loadTestsFromTestCase(MigrationTests)
        if not unittest.TextTestRunner(verbosity=2).run(result).wasSuccessful():
            raise SystemExit(1)
    elif args.apply:
        migrate_jsonl(args.apply)
    else:
        parser.error("Specify --self-test or --apply")
