#!/usr/bin/env python3
"""Read-only, exact-original Eldrazi Incursion Cardkit audit (#150).

Uses the current official manifest and BOTH actual shipped card indexes, runs the
integrated Cardkit classifier on every missing name, and overlays human-reviewed
rules hazards. Never converts a SUPPORTED heuristic into a worker authorization.
"""
from __future__ import annotations

import hashlib
import json
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
MANIFEST = ROOT / "investigation/argentum-six-precons/deck-manifest.json"
INDEXES = [
    ROOT / "android/app/src/main/assets/argentum/all-cards.index.tsv",
    ROOT / "android/app/src/main/assets/argentum/custom-cards.index.tsv",
]
OUTPUT = ROOT / "investigation/argentum-six-precons/eldrazi-cardkit-report"
CUSTOM_JSONL = ROOT / "android/app/src/main/assets/argentum/custom-cards.jsonl"
ALL_JSONL = ROOT / "android/app/src/main/assets/argentum/all-cards.jsonl"

# SHA-256 of sorted "original card name\tquantity\n" records from the
# immutable original Wizards deck manifests. Only names and counts are pinned:
# metadata/order changes do not masquerade as substitutions. These digests
# were independently calculated from manifest blob dcc6ce418422d221a87df04e233d4f1df7c9cc05.
ORIGINAL_DECK_DIGESTS = {
    "Eldrazi Incursion": "43d3cf6a753c82d38992d35d3dbe9b09d3caaec4f8e0369065aaca4a5559849f",
    "Eldrazi Unbound": "f4f98c4f0e2c22f89f8fa8bf92e65847cb94cb8739ad867bc061759bf70ccdcf",
}


def original_deck_digest(cards: list[dict]) -> str:
    """Canonical original-deck identity; aggregate legitimate split-printing rows."""
    # Original Unbound intentionally splits Wastes across two 8+7 rows.
    # Aggregate duplicate names instead of treating valid print-art splits as
    # substitutions. Added copies still change the normalized deck digest.
    counts = {}
    for card in cards:
        name, quantity = card["name"], card["quantity"]
        if not isinstance(name, str) or not name or name != name.strip():
            raise ValueError(f"Invalid original card name: {name!r}")
        if type(quantity) is not int or quantity <= 0:
            raise ValueError(f"Invalid original card quantity: {name!r}: {quantity!r}")
        counts[name] = counts.get(name, 0) + quantity
    if not counts:
        raise ValueError("Empty original deck")
    rows = [f"{name}\t{quantity}" for name, quantity in counts.items()]
    canonical = "\n".join(sorted(rows)) + "\n"
    return hashlib.sha256(canonical.encode("utf-8")).hexdigest()


def verify_original_decks(decks: dict, expected: dict | None = None) -> dict:
    """Refuse to count any modified or substituted original precon as coverage."""
    expected = ORIGINAL_DECK_DIGESTS if expected is None else expected
    observed = {}
    for name, pinned_digest in expected.items():
        if name not in decks:
            raise ValueError(f"Missing original deck: {name}")
        actual = original_deck_digest(decks[name]["cards"])
        if actual != pinned_digest:
            raise ValueError(
                f"Original {name} identity mismatch: expected {pinned_digest}, got {actual}. "
                "Do not substitute cards or alter original quantities."
            )
        observed[name] = actual
    return observed



def unique_manifest_decks(manifest: dict) -> dict[str, dict]:
    """Reject duplicate deck names instead of silently overwriting an original.

    A dictionary comprehension would accept a second Eldrazi Incursion entry
    and hide the first. An exact 100-card precon audit must reject that input
    even when the last duplicate happens to match the pinned digest.
    """
    rows = manifest.get("decks") if isinstance(manifest, dict) else None
    if not isinstance(rows, list):
        raise ValueError("Invalid deck manifest: expected decks list")
    decks: dict[str, dict] = {}
    for number, deck in enumerate(rows, 1):
        if not isinstance(deck, dict):
            raise ValueError(f"Invalid deck entry at row {number}")
        name = deck.get("name")
        if not isinstance(name, str) or not name or name != name.strip():
            raise ValueError(f"Invalid deck name at row {number}: {name!r}")
        if name in decks:
            raise ValueError(f"Duplicate deck name in manifest: {name!r}")
        if not isinstance(deck.get("cards"), list):
            raise ValueError(f"Invalid cards list for deck {name!r}")
        decks[name] = deck
    return decks


def read_index_entries(path: Path) -> dict[str, str]:
    """Parse a shipped index without silently accepting duplicate or token-only rows."""
    entries: dict[str, str] = {}
    for line_number, line in enumerate(path.read_text().splitlines(), 1):
        if not line.strip():
            continue
        parts = line.split("\t")
        if len(parts) != 2:
            raise ValueError(f"{path}:{line_number}: expected name and card/token kind")
        name, kind = parts
        if not name or name != name.strip() or kind not in {"card", "basic", "token"}:
            raise ValueError(f"{path}:{line_number}: invalid card index row {line!r}")
        if name in entries:
            raise ValueError(f"{path}:{line_number}: duplicate index entry {name!r}")
        entries[name] = kind
    if not entries:
        raise ValueError(f"Empty card index: {path}")
    return entries


def verify_custom_export(entries: dict[str, str], jsonl_path: Path) -> int:
    """Reject ghost index entries and unindexed exports before counting coverage.

    The all-cards and custom indexes may overlap, but within each library every
    indexed name must have exactly one corresponding serialized definition.
    """
    exported: set[str] = set()
    for line_number, line in enumerate(jsonl_path.read_text().splitlines(), 1):
        if not line.strip():
            continue
        try:
            record = json.loads(line)
        except json.JSONDecodeError as exc:
            raise ValueError(f"{jsonl_path}:{line_number}: invalid JSON") from exc
        name = record.get("name") if isinstance(record, dict) else None
        if not isinstance(name, str) or not name or name != name.strip():
            raise ValueError(f"{jsonl_path}:{line_number}: invalid exported card name")
        if name in exported:
            raise ValueError(f"{jsonl_path}:{line_number}: duplicate exported card {name!r}")
        exported.add(name)
    indexed = set(entries)
    if indexed != exported:
        raise ValueError(
            f"Custom index/export mismatch: index-only={sorted(indexed - exported)!r}; "
            f"export-only={sorted(exported - indexed)!r}"
        )
    return len(exported)


# Independent ownership/capability guardrails override a heuristic SUPPORTED.
# "source-review" is not an implementation claim; every candidate still needs
# exact Oracle, a real engine scenario, registry/export and CI.
TRIAGE = {
    "Ulalek, Fused Atrocity": ("GPT #132 / draft #156", "candidate tested on isolated runtime; not on shared development"),
    "Forsaken Monument": ("Copilot CANDIDATE #151", "cross-deck; NO authorization; test mana tap replacement and anthem"),
    "Eldrazi Temple": ("GPT #152 / draft #158", "colorless-Eldrazi ability-source restriction needs source-built engine primitive"),
    "Ugin, the Ineffable": ("GPT #153", "unique token-to-face-down-exile link survives Ugin departure"),
    "Eldrazi Displacer": ("GPT #154 / existing draft #163", "13 real-engine scenarios green on isolated head; DO NOT dispatch a duplicate Copilot worker; upgraded integration pending"),
    "Chittering Dispatcher": ("ENGINE BLOCKED #34/#36", "Myriad defender/planeswalker choice not approximable"),
    "Corrupted Crossroads": ("GPT RESTRICTED MANA", "Devoid-only spend predicate; ColorlessSpellsOnly is too broad"),
    "Azlask, the Swelling Scourge": ("GPT COMPLEX", "experience counters, variable anthem and granted annihilator"),
    "Benthic Anomaly": ("GPT COMPLEX", "multi-opponent creature choices and computed token copy characteristics"),
    "Eldritch Immunity": ("GPT SOURCE REVIEW", "Overload changes target to each; verify exact pipeline"),
    "Selective Obliteration": ("GPT COMPLEX", "each-player simultaneous color choice and exact permanent-color filter"),
    "Angelic Aberration": ("GPT SOURCE REVIEW", "variable sacrifice with base power/toughness, token creation"),
    "Bismuth Mindrender": ("GPT COMPLEX", "exile-until-nonland and life alternative cast permission"),
    "Hideous Taskmaster": ("GPT COMPLEX", "per-opponent target, temporary control and granted annihilator"),
    "Twins of Discord": ("GPT COMPLEX", "odd/even choice combat blocking and granted bloodthirst"),
    "Mutated Cultist": ("GPT COMPLEX", "remove counters from player/permanent, delayed next-spell reduction"),
    "Eldrazi Confluence": ("GPT SOURCE REVIEW", "choose three repeatable modes, blink target identity and tapped owner"),
    "Spawnbed Protector": ("GPT #170 / test-only draft #175", "existing sole GPT candidate; verify zero-target two Scions and illegal-target fizzle; do not dispatch duplicate"),
    "Inversion Behemoth": ("GPT SOURCE REVIEW", "any-number targets, continuous switch P/T until end of turn"),
    "Morophon, the Boundless": ("GPT COMPLEX", "chosen type, colored-symbol-only cost reduction and changeling"),
    "Kozilek's Return": ("GPT COMPLEX", "cast-from-graveyard optional exile trigger, seven-plus mana-value check"),
    "Vile Redeemer": ("GPT SOURCE REVIEW", "optional {C} cast trigger, nontoken death count under controller"),
    "Rishkar's Expertise": ("GPT SOURCE REVIEW", "greatest power draw and restricted free cast during resolution"),
    "Ulamog's Nullifier": ("GPT SOURCE REVIEW", "exiled cards owned by opponents, conditional processing and counterspell"),
    "Opal Palace": ("GPT COMPLEX", "commander cast-from-command-zone count and spend-linked counters"),
}


def main() -> None:
    manifest = json.loads(MANIFEST.read_text())
    index_sources = [read_index_entries(path) for path in INDEXES]
    all_export_count = verify_custom_export(index_sources[0], ALL_JSONL)
    custom_export_count = verify_custom_export(index_sources[1], CUSTOM_JSONL)
    # Basic lands count as real original cards; token definitions never do.
    index = {
        name
        for source in index_sources
        for name, kind in source.items()
        if kind in {"card", "basic"}
    }
    decks = unique_manifest_decks(manifest)
    original_identity = verify_original_decks(decks)
    incursion = decks["Eldrazi Incursion"]
    unbound = decks["Eldrazi Unbound"]
    assert sum(x["quantity"] for x in incursion["cards"]) == 100
    assert sum(x["quantity"] for x in unbound["cards"]) == 100
    missing = [x for x in incursion["cards"] if x["name"] not in index]
    unbound_missing = {x["name"] for x in unbound["cards"] if x["name"] not in index}
    assert len({x["name"] for x in missing}) == len(missing), "duplicate missing card name"
    names = [x["name"] for x in missing]
    assert set(names) <= set(TRIAGE), "new missing card requires explicit safety triage"
    cmd = [str(ROOT / "tools/cardkit/cardkit"), "classify", *names, "--json"]
    classified = json.loads(subprocess.check_output(cmd, cwd=ROOT, text=True)) if names else []
    by_name = {v["name"]: v for v in classified}
    assert set(by_name) == set(names)
    rows = []
    for entry in missing:
        name = entry["name"]
        lane, hazard = TRIAGE[name]
        v = by_name[name]
        rows.append({
            "name": name, "quantity": entry["quantity"],
            "sharedWithEldraziUnbound": name in unbound_missing,
            "cardkitReadiness": v["readiness"], "cardkitRoute": v.get("route"),
            "engineeringLane": lane, "mandatoryReview": hazard,
            "cardkitReasons": v.get("reasons", []),
            "oracle": v.get("oracle", {}),
        })
    indexed = sum(x["quantity"] for x in incursion["cards"] if x["name"] in index)
    indexed_unbound = sum(x["quantity"] for x in unbound["cards"] if x["name"] in index)
    integration_commit = subprocess.check_output(
        ["git", "rev-parse", "HEAD"], cwd=ROOT, text=True
    ).strip()
    input_hashes = {
        str(path.relative_to(ROOT)): hashlib.sha256(path.read_bytes()).hexdigest()
        for path in [MANIFEST, *INDEXES, ALL_JSONL, CUSTOM_JSONL]
    }
    result = {
        "integrationCommit": integration_commit,
        "originalDeckSha256": original_identity,
        "originalDeckIdentityVerified": True,
        "allExportIndexParityVerified": True,
        "allExportCount": all_export_count,
        "customExportIndexParityVerified": True,
        "customExportCount": custom_export_count,
        "inputSha256": input_hashes,
        "basis": "current checkout; two shipped indexes + exact official manifest + offline Cardkit Oracle",
        "incursionIndexedSlots": indexed,
        "incursionMissingSlots": 100 - indexed,
        "unboundIndexedSlots": indexed_unbound,
        "unboundMissingSlots": 100 - indexed_unbound,
        "sharedMissingNames": [r["name"] for r in rows if r["sharedWithEldraziUnbound"]],
        "noPaidWorkerAuthorization": True,
        "readinessIsNotRulesProof": True,
        "rows": rows,
    }
    OUTPUT.with_suffix(".json").write_text(json.dumps(result, indent=2, ensure_ascii=False) + "\n")
    lines = [
        "# Eldrazi Incursion — exact 100-card Cardkit gap triage",
        "",
        f"Integration commit `{integration_commit}`: **{indexed}/100 indexed**, {100-indexed} missing; "
        f"Eldrazi Unbound **{indexed_unbound}/100 indexed**.",
        "",
        "Original deck identities SHA-256 pinned and verified; no card substitutions or quantity drift.",
        f"Both shipped index/JSONL pairs verified (all-cards {all_export_count}; custom {custom_export_count} unique definitions); tokens excluded from card coverage.",
        "",
        "Cardkit SUPPORTED is only a similarity heuristic. None of these missing cards "
        "is complete until rules tests, deterministic export, integration and device QA.",
        "",
        "| Missing original card | Cardkit | Independent engineering gate |",
        "| --- | --- | --- |",
    ]
    for row in rows:
        lines.append(f"| {row['name']} | {row['cardkitReadiness']} | "
                     f"{row['engineeringLane']}: {row['mandatoryReview']} |")
    lines += [
        "",
        "Existing Sceptre #147 one-shot authorization remains untouched. "
        "No new paid admission or automatic Copilot queue claim.",
        "",
        "Shared missing names: " + ", ".join(result["sharedMissingNames"]) + ".",
        "",
    ]
    OUTPUT.with_suffix(".md").write_text("\n".join(lines))
    print(f"Integration {integration_commit}: Eldrazi Incursion {indexed}/100; Unbound {indexed_unbound}/100; "
          f"Cardkit classified {len(rows)} missing names")
    for row in rows:
        print(f"{row['cardkitReadiness']:<25} {row['name']:<32} {row['engineeringLane']}")


if __name__ == "__main__":
    main()
