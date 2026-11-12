#!/usr/bin/env python3
"""Prove APK/AAB card bytes match the checkout; this does not certify game rules."""
import argparse
import hashlib
import json
from pathlib import Path
from zipfile import ZipFile


CARD_ASSETS = (
    "all-cards.jsonl", "all-cards.index.tsv", "custom-cards.jsonl",
    "custom-cards.index.tsv", "custom-overrides.txt", "por-cards.jsonl",
    "commander-decks.json",
)


def digest(data):
    return hashlib.sha256(data).hexdigest()


def verify(archive, assets, required_custom_cards=()):
    archive, assets = Path(archive), Path(assets)
    if archive.suffix.lower() not in (".apk", ".aab"):
        raise ValueError("Expected an .apk or .aab archive")
    prefix = "base/assets/argentum/" if archive.suffix.lower() == ".aab" else "assets/argentum/"
    hashes = {}
    custom_data = custom_index = None
    with ZipFile(archive) as package:
        names = package.namelist()
        for filename in CARD_ASSETS:
            member = prefix + filename
            if names.count(member) != 1:
                raise ValueError(f"Expected exactly one packaged {member}")
            expected = (assets / filename).read_bytes()
            actual = package.read(member)
            if not expected.strip() or actual != expected:
                raise ValueError(f"Empty or stale packaged card asset: {filename}")
            hashes[filename] = digest(actual)
            if filename == "custom-cards.jsonl":
                custom_data = actual.decode("utf-8")
            elif filename == "custom-cards.index.tsv":
                custom_index = actual.decode("utf-8")
    definitions = [json.loads(line)["name"] for line in custom_data.splitlines() if line.strip()]
    indexed = [line.split("\t")[0] for line in custom_index.splitlines() if line.strip()]
    if definitions != indexed or len(definitions) != len({name.casefold() for name in definitions}):
        raise ValueError("Custom-card JSONL/index names differ or contain duplicates")
    for name in required_custom_cards:
        if name not in definitions:
            raise ValueError(f"Required custom card absent from package: {name}")
    return {
        "archive_sha256": digest(archive.read_bytes()),
        "custom_card_count": len(definitions),
        "asset_sha256": hashes,
        "required_custom_cards": list(required_custom_cards),
        "scope": "Packaged byte parity only; requires prior export and rules/runtime/device tests.",
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("archive", type=Path)
    parser.add_argument("--assets", type=Path, default=Path("android/app/src/main/assets/argentum"))
    parser.add_argument("--require-custom-card", action="append", default=[])
    args = parser.parse_args()
    try:
        result = verify(args.archive, args.assets, args.require_custom_card)
    except (OSError, ValueError, KeyError) as error:
        parser.exit(1, f"Card asset verification FAILED: {error}\n")
    print(json.dumps(result, indent=2))


if __name__ == "__main__":
    main()
