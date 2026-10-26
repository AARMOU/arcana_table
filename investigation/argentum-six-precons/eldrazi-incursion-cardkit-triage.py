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

# Independent ownership/capability guardrails override a heuristic SUPPORTED.
# "source-review" is not an implementation claim; every candidate still needs
# exact Oracle, a real engine scenario, registry/export and CI.
TRIAGE = {
    "Ulalek, Fused Atrocity": ("GPT #132 / draft #156", "candidate tested on isolated runtime; not on shared development"),
    "Forsaken Monument": ("Copilot CANDIDATE #151", "cross-deck; NO authorization; test mana tap replacement and anthem"),
    "Eldrazi Temple": ("GPT #152 / draft #158", "colorless-Eldrazi ability-source restriction needs source-built engine primitive"),
    "Ugin, the Ineffable": ("GPT #153", "unique token-to-face-down-exile link survives Ugin departure"),
    "Eldrazi Displacer": ("Copilot CANDIDATE #154", "blink ownership/tapped/token disappearance must be proved"),
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
    "Spawnbed Protector": ("GPT LOW-HANGING REVIEW", "end-step graveyard return and Scion creation; verify up-to-one targeting"),
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
    index = {
        line.split("\t", 1)[0]
        for path in INDEXES
        for line in path.read_text().splitlines()
        if line.strip()
    }
    decks = {deck["name"]: deck for deck in manifest["decks"]}
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
        for path in [MANIFEST, *INDEXES]
    }
    result = {
        "integrationCommit": integration_commit,
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
