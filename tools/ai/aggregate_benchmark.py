#!/usr/bin/env python3
"""Aggregate paired headless Arcana AI benchmark shards (no dependencies)."""
import argparse
import json
import math
from collections import Counter, defaultdict
from pathlib import Path

def percentile(values, q):
    if not values:
        return 0.0
    xs = sorted(values)
    return xs[max(0, min(len(xs) - 1, math.ceil(q * len(xs)) - 1))]

def wilson95(wins, trials):
    if not trials:
        return (0.0, 1.0)
    z = 1.959963984540054
    p = wins / trials
    den = 1 + z * z / trials
    mid = (p + z * z / (2 * trials)) / den
    half = z * math.sqrt(p * (1 - p) / trials + z * z / (4 * trials * trials)) / den
    return ((mid - half), (mid + half))

def aggregate(files):
    games = []
    excluded = []
    for file in files:
        data = json.loads(file.read_text())
        if data.get("schema") != 1 or data.get("candidateIdenticalToBaseline") is not True:
            raise ValueError(f"{file}: wrong schema or non-control candidate")
        if data.get("baselineProfile") != "arcana-mobile-b123":
            raise ValueError(f"{file}: unexpected baseline profile")
        games.extend(data["games"])
        excluded.extend(data.get("excludedDecks", []))
    grouped = defaultdict(list)
    for game in games:
        mode = game["mode"]
        if mode not in (2, 4):
            raise ValueError(f"Unsupported game mode {mode}")
        grouped[(mode, game["pair"])].append(game)
        if game["status"] == "decisive" and game["winnerSide"] not in ("candidate", "baseline"):
            raise ValueError(f"Decisive game missing winner side: {game}")
    for mode in (2, 4):
        these = [g for g in games if g["mode"] == mode]
        if len(these) != 200:
            raise ValueError(f"{mode}p expected 200 games, got {len(these)}")
        pairs = [v for (m, _), v in grouped.items() if m == mode]
        if len(pairs) != 100:
            raise ValueError(f"{mode}p expected 100 seed/deck pairs, got {len(pairs)}")
        for match in pairs:
            if len(match) != 2:
                raise ValueError(f"{mode}p pair missing a swapped game")
            a, b = match
            if a["seed"] != b["seed"] or a["deckIds"] != b["deckIds"]:
                raise ValueError("Paired games changed seed or decks")
            sa, sb = set(a["candidateSeatsZeroBased"]), set(b["candidateSeatsZeroBased"])
            if sa.intersection(sb) or sa.union(sb) != set(range(mode)):
                raise ValueError("Profile roles do not swap every seat")
    if len(games) != 400:
        raise ValueError(f"Expected exactly 400 games, got {len(games)}")
    return games, sorted(set(excluded))

def report(games, excluded):
    out = [
        "# Arcana AI control-vs-control baseline — 400 real-engine games",
        "",
        "**NOT a stronger AI:** current mobile AI plays both sides with identical behavior. JVM timing cannot certify Fold responsiveness.",
        "Seeded, paired role-swap experiments: 200 two-player and 200 four-player games.",
        "Undecided games are excluded only from the decisive-game win-rate denominator and remain reported in full.",
        "",
        "| Seats | Games | Candidate wins | Baseline wins | Draws | Timeouts | Errors | Candidate share of decisive wins (Wilson 95% CI) |",
        "|---|---:|---:|---:|---:|---:|---:|---|",
    ]
    for mode in (2, 4):
        gs = [g for g in games if g["mode"] == mode]
        c = Counter(g["status"] for g in gs)
        wins = sum(g["winnerSide"] == "candidate" for g in gs)
        losses = sum(g["winnerSide"] == "baseline" for g in gs)
        lo, hi = wilson95(wins, wins + losses)
        pct = 100 * wins / (wins + losses) if wins + losses else 0
        errors = sum(c[k] for k in c if k not in ("decisive", "draw", "timeout"))
        out.append(f"| {mode}p | {len(gs)} | {wins} | {losses} | {c['draw']} | {c['timeout']} | {errors} | {pct:.1f}% [{100*lo:.1f}%, {100*hi:.1f}%] ({wins+losses} decisive) |")
    out.extend(["", "| Seats | Side | Tier | Decisions | p50 ms | p95 ms | p99 ms | max ms | AI ms / game turn | Game wall p95 ms |",
                "|---|---|---|---:|---:|---:|---:|---:|---:|---:|"])
    for mode in (2, 4):
        gs = [g for g in games if g["mode"] == mode]
        for side in ("candidate", "baseline"):
            all_samples = [s for g in gs for s in g["decisions"] if s["side"] == side]
            turns = sum(max(g["turns"], 1) for g in gs)
            ai_per_turn = sum(s["nanos"] for s in all_samples) / 1_000_000 / turns
            for tier in sorted({s["tier"] for s in all_samples}):
                ms = [s["nanos"] / 1_000_000 for s in all_samples if s["tier"] == tier]
                out.append(f"| {mode}p | {side} | {tier} | {len(ms)} | {percentile(ms,.5):.2f} | {percentile(ms,.95):.2f} | {percentile(ms,.99):.2f} | {max(ms):.2f} | {ai_per_turn:.2f} | {percentile([g['wallMs'] for g in gs],.95):.2f} |")
    out.extend(["", f"Distinct excluded-deck reasons: {len(excluded)} (see aggregate JSON).",
                "A 'timeout' means an action, turn or wall cap was reached; it does not mean a completed draw.",
                "Illegal actions are separately counted; other dumb-play heuristics remain unimplemented.",
                "Benchmarks reset the 600-ms thinking burst per decision; production shares it across a host burst.",
                "Do not merge AI tuning without a nonidentical candidate, large-board comparison and Fold feel test.",
                ""])
    return "\n".join(out)

def main():
    p = argparse.ArgumentParser()
    p.add_argument("--input", required=True, type=Path)
    p.add_argument("--output", required=True, type=Path)
    a = p.parse_args()
    files = sorted(a.input.rglob("comparison.json"))
    if len(files) != 8:
        raise SystemExit(f"Expected 8 nonoverlapping shards, got {len(files)}: {files}")
    games, excluded = aggregate(files)
    a.output.mkdir(parents=True, exist_ok=True)
    (a.output / "comparison.json").write_text(json.dumps({
        "schema": 1, "candidateIdenticalToBaseline": True,
        "baselineProfile": "arcana-mobile-b123",
        "games": games, "excludedDecks": excluded,
    }, indent=2) + "\n")
    md = report(games, excluded)
    (a.output / "comparison.md").write_text(md)
    print(md)

if __name__ == "__main__":
    main()
