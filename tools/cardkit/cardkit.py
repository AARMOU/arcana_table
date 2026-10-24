#!/usr/bin/env python3
"""cardkit — card implementation toolkit for agents working on Arcana Table custom cards.

Run from anywhere in the repository:  tools/cardkit/cardkit <command> --help

  classify NAME...      readiness for each card (SUPPORTED / ENGINE_BLOCKED / NEEDS_REVIEW / IMPLEMENTED_AND_TESTED)
  similar NAME|--text   implemented cards whose rules text matches each line (what to copy)
  gaps                  deck coverage + readiness for the six precons or any deck list (ManaBox text/CSV)
  unit NAME...          an [Arcana Queue] issue body for SUPPORTED cards (printed, never filed)
  newcard NAME          scaffold a card file + failing test, with references for every line
  check                 repository rules for card PRs (scaffolds unregistered, tests present, Oracle drift)
  blocker NAME          record an engine gap so the card routes to Claude instead of burning agent runs
  oracle fetch|show     refresh / inspect the offline Scryfall cache
  patterns | sdk-index | docs-check   regenerate / verify the agent reference docs
"""
from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from cardkit_lib import (check, decklist, docs_check, gaps, oracle, patterns, readiness, refs,  # noqa: E402
                         repo, scaffold, sdk)


def _print_verdict(v: dict, verbose: bool) -> None:
    print(f"{v['name']}: {v['readiness']}" + (f"  (route: {v['route']})" if v.get("route") else ""))
    for r in v["reasons"]:
        print(f"  - {r}")
    if verbose:
        for line in v.get("lines", []):
            print(f"  [{line['score']:.2f}] {line['text']}")
            for m in line["matches"][:2]:
                print(f"         ~{m['score']:.2f} {m['card']} [{m['source']}] {m['where']}")


def cmd_classify(a) -> int:
    out = [readiness.classify(n) for n in a.names]
    if a.json:
        print(json.dumps(out, indent=2, ensure_ascii=False))
    else:
        for v in out:
            _print_verdict(v, a.verbose)
    return 0


def cmd_similar(a) -> int:
    if a.text:
        lines, name = [a.text], ""
    else:
        card = oracle.cached(a.name) or next((c for c in repo.library_cards() if repo.normalize_name(c["name"]) == repo.normalize_name(a.name)), None)
        if not card:
            print(f"No Oracle text for {a.name!r}: pass --text, or run `cardkit oracle fetch \"{a.name}\"`", file=sys.stderr)
            return 2
        name = card.get("name")
        lines = [l for l in __import__("cardkit_lib.text", fromlist=["x"]).split_lines(card.get("oracle_text") or card.get("oracleText", ""))]
    ix = refs.index()
    for line in lines:
        print(f"\n{line}")
        for m in ix.search(line, name, k=a.k, exclude=name):
            print(f"  ~{m.score:.2f} {m.ref.card} [{m.ref.source}]\n        {m.ref.line}\n        {refs.where(m.ref.card, m.ref.source)}")
    return 0


def cmd_gaps(a) -> int:
    decks = gaps.load_decks(a.manifest, a.deck)
    claims = None
    if a.claims_from_github:
        try:
            claims = gaps.open_pr_claims()
        except gaps.ClaimsUnavailable as exc:
            print(f"ERROR: {exc}; refusing to produce dispatch lists without verified claims", file=sys.stderr)
            return 4
    result = gaps.analyze(decks, use_refs=not a.no_refs, claims=claims)
    if a.json_output:
        a.json_output.parent.mkdir(parents=True, exist_ok=True)
        a.json_output.write_text(json.dumps(result, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    if a.markdown_output:
        a.markdown_output.parent.mkdir(parents=True, exist_ok=True)
        a.markdown_output.write_text(gaps.render_md(result), encoding="utf-8")
    for d in result["decks"]:
        print(f"{d['name']}: {d['playableSlots']}/{d['slots']} playable — supported {d['SUPPORTED']}, "
              f"review {d['NEEDS_REVIEW']}, blocked {d['ENGINE_BLOCKED']} (slots)")
    t = result["totals"]
    print(f"DISTINCT: implemented {t['IMPLEMENTED_AND_TESTED']}, supported {t['SUPPORTED']}, "
          f"needs review {t['NEEDS_REVIEW']}, engine-blocked {t['ENGINE_BLOCKED']}")
    print("ROUTES: " + ", ".join(f"{k} {len(v)}" for k, v in result["dispatch"].items() if not isinstance(v, bool))
          + ("" if claims is not None else " — NOT DISPATCHABLE: open-PR claims not checked (add --claims-from-github)"))
    return 0


def cmd_unit(a) -> int:
    """Fails closed: prints nothing unless every card is dispatchable and open-PR claims were verified."""
    verdicts = [readiness.classify(n) for n in a.names]
    refused, warned = [], []
    for v in verdicts:
        r = v["readiness"]
        if r in ("ENGINE_BLOCKED", "IMPLEMENTED_AND_TESTED"):
            refused.append((v, f"{r} is never dispatchable"))
        elif v.get("oracleMissing") or not v.get("oracle"):
            refused.append((v, "no Oracle record; fail closed"))
        elif r == "NEEDS_REVIEW":
            if a.allow_review:
                warned.append(v)
            else:
                refused.append((v, "NEEDS_REVIEW (pass --allow-review only after owner/GPT triage)"))
    if not refused:
        try:
            prs = gaps.open_pr_claims()
        except gaps.ClaimsUnavailable as exc:
            print(f"refusing: {exc}. A queue unit needs verified open-PR claims.", file=sys.stderr)
            return 1
        for v in verdicts:
            by, _ = gaps._claimed_by(v["name"], prs)
            if by:
                refused.append((v, f"already claimed by {by}"))
    for v, why in refused:
        print(f"refusing {v['name']}: {why} — " + "; ".join(v["reasons"]), file=sys.stderr)
    if refused:
        print("No queue body printed.", file=sys.stderr)
        return 1
    for v in warned:
        print(f"WARNING: {v['name']} is NEEDS_REVIEW and included only because of --allow-review. This body is "
              f"not an authorization; the dispatcher's own admission checks still apply.", file=sys.stderr)
    print(gaps.queue_unit(verdicts, review=[v["name"] for v in warned]))
    return 0


def cmd_newcard(a) -> int:
    try:
        info = scaffold.create(a.name, a.out_dir, a.test_dir, online=not a.offline, force=a.force)
    except OSError as exc:
        print(str(exc), file=sys.stderr)
        return 2
    print(f"Scaffolded {info['card']} ({info['readiness']}):")
    for f in info["files"]:
        print(f"  {f}")
    for r in info["reasons"]:
        print(f"  - {r}")
    print("\nNext:\n"
          f"  1. Implement every CARDKIT-TODO in {info['files'][0]} (docs/agents/CARD-PATTERNS.md).\n"
          f"  2. Replace the failing test with real scenarios; run:\n"
          f"       cd android && ./gradlew :custom-cards:test --tests 'com.arcanatable.customcards.{info['val']}ScenarioTest'\n"
          f"  3. Delete the CARDKIT-SCAFFOLD block, then register it in CustomCards.kt:\n"
          f"       Entry({info['val']}, \"{info['set']}\", \"<deck label>\"),\n"
          f"  4. cd android && ./gradlew :custom-cards:exportCards :custom-cards:test   (commit the regenerated assets)\n"
          f"  5. tools/cardkit/cardkit check")
    return 0


def cmd_check(a) -> int:
    if a.write_baseline:
        data = check.write_baseline()
        print(f"Wrote {repo.rel(check.BASELINE)}: {len(data['untested'])} untested, {len(data['unregistered'])} unregistered")
        return 0
    errors, warnings = check.run(strict_oracle=a.strict_oracle)
    for w in warnings:
        print(f"warning: {w}")
    for e in errors:
        print(f"ERROR: {e}")
    print(f"cardkit check: {len(errors)} error(s), {len(warnings)} warning(s), "
          f"{len(repo.card_sources())} card definitions, {len(repo.registry_entries())} registered")
    return 1 if errors else 0


def cmd_blocker(a) -> int:
    path = repo.DATA_DIR / "known-blockers.json"
    data = json.loads(path.read_text(encoding="utf-8"))
    data.setdefault("cards", {})[a.name] = {"reason": a.reason, "source": a.source, "verified": a.verified}
    data["cards"] = dict(sorted(data["cards"].items(), key=lambda kv: kv[0].casefold()))
    path.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    card = oracle.cached(a.name) or {}
    print(f"Recorded engine blocker for {a.name} in {repo.rel(path)}.\n\nPaste into #141 (engine backlog):\n")
    print(f"### Engine blocker: {a.name}\n- **Missing primitive:** {a.reason}\n- **Evidence:** {a.source}\n"
          f"- **Oracle:** {card.get('oracle_text', '(not cached)').replace(chr(10), ' / ')}\n"
          f"- **Reproduced on pinned engine:** {'yes' if a.verified else 'no — needs independent reproduction'}")
    return 0


def cmd_oracle(a) -> int:
    if a.action == "show":
        for n in a.names:
            print(json.dumps(oracle.cached(n), indent=2, ensure_ascii=False))
        return 0
    names = list(a.names)
    if a.manifest or a.deck or not names:
        for d in gaps.load_decks(a.manifest, a.deck):
            names += [c["name"] for c in d["cards"]]
    if a.missing_only:
        names = [n for n in names if readiness.implementation_state(n)[0] != "IMPLEMENTED_AND_TESTED"]
    names = sorted({n for n in names if not oracle.cached(n) or a.refresh}, key=str.casefold)
    if not names:
        print("Oracle cache already has every requested card.")
        return 0
    try:
        cards, missing = oracle.fetch_many(names)
    except OSError as exc:
        print(f"Scryfall unreachable: {exc}", file=sys.stderr)
        return 2
    changed = oracle.save(cards)
    print(f"Fetched {len(cards)} card(s), {changed} new or changed; not found: {missing or 'none'}")
    return 0 if not missing else 3


def cmd_generated(a, module) -> int:
    ok = module.write(check=a.check)
    if a.check and not ok:
        print(f"{module.__name__} output is stale: run `tools/cardkit/cardkit {a.cmd}` and commit it", file=sys.stderr)
        return 1
    print("up to date" if a.check else "written")
    return 0


def cmd_docs_check(a) -> int:
    errors = docs_check.run()
    for e in errors:
        print(f"ERROR: {e}")
    print(f"docs-check: {len(errors)} error(s)")
    return 1 if errors else 0


def main(argv: list[str] | None = None) -> int:
    p = argparse.ArgumentParser(prog="cardkit", description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = p.add_subparsers(dest="cmd", required=True)

    s = sub.add_parser("classify", help="readiness for card names")
    s.add_argument("names", nargs="+")
    s.add_argument("--json", action="store_true")
    s.add_argument("-v", "--verbose", action="store_true", help="show per-line matches")
    s.set_defaults(func=cmd_classify)

    s = sub.add_parser("similar", help="implemented cards matching each rules line")
    s.add_argument("name", nargs="?")
    s.add_argument("--text", help="match this rules text instead of a card's Oracle text")
    s.add_argument("-k", type=int, default=4)
    s.set_defaults(func=cmd_similar)

    s = sub.add_parser("gaps", help="deck coverage with readiness classification")
    s.add_argument("--manifest", type=Path, help=f"deck manifest JSON (default: {repo.rel(gaps.MANIFEST)})")
    s.add_argument("--deck", type=Path, action="append", help="deck list file (ManaBox/Moxfield text or CSV); repeatable")
    s.add_argument("--json-output", type=Path)
    s.add_argument("--markdown-output", type=Path)
    s.add_argument("--no-refs", action="store_true", help="skip line matching (fast; never yields SUPPORTED)")
    s.add_argument("--claims-from-github", action="store_true",
                   help="mark cards named in open PRs (via gh) as claimed and keep them out of dispatch lists")
    s.set_defaults(func=cmd_gaps)

    s = sub.add_parser("unit", help="[Arcana Queue] issue body for SUPPORTED cards")
    s.add_argument("names", nargs="+")
    s.add_argument("--allow-review", action="store_true",
                   help="also allow NEEDS_REVIEW cards that have an Oracle record (after triage); never blocked or implemented cards")
    s.set_defaults(func=cmd_unit)

    s = sub.add_parser("newcard", help="scaffold a custom card + failing scenario test")
    s.add_argument("name")
    s.add_argument("--offline", action="store_true", help="use only the cached Oracle record")
    s.add_argument("--force", action="store_true")
    s.add_argument("--out-dir", type=Path)
    s.add_argument("--test-dir", type=Path)
    s.set_defaults(func=cmd_newcard)

    s = sub.add_parser("check", help="card PR rules (CI)")
    s.add_argument("--strict-oracle", action="store_true", help="treat Oracle drift as errors")
    s.add_argument("--write-baseline", action="store_true", help=argparse.SUPPRESS)
    s.set_defaults(func=cmd_check)

    s = sub.add_parser("blocker", help="record an engine gap for a card")
    s.add_argument("name")
    s.add_argument("--reason", required=True, help="the exact missing primitive")
    s.add_argument("--source", required=True, help="issue/PR/test that reproduced it")
    s.add_argument("--verified", action="store_true", help="reproduced on the pinned engine")
    s.set_defaults(func=cmd_blocker)

    s = sub.add_parser("oracle", help="Scryfall cache")
    s.add_argument("action", choices=["fetch", "show"])
    s.add_argument("names", nargs="*")
    s.add_argument("--manifest", type=Path)
    s.add_argument("--deck", type=Path, action="append")
    s.add_argument("--missing-only", action="store_true", help="only cards not already implemented")
    s.add_argument("--refresh", action="store_true", help="re-fetch cards already cached")
    s.set_defaults(func=cmd_oracle)

    for name, module in (("patterns", patterns), ("sdk-index", sdk)):
        s = sub.add_parser(name, help=f"regenerate docs/agents/generated/ ({name})")
        s.add_argument("--check", action="store_true", help="fail if the committed output is stale")
        s.set_defaults(func=lambda a, m=module: cmd_generated(a, m))

    s = sub.add_parser("docs-check", help="verify agent docs against the SDK index and real sources")
    s.set_defaults(func=cmd_docs_check)

    a = p.parse_args(argv)
    if a.cmd == "similar" and not (a.name or a.text):
        p.error("similar needs a card name or --text")
    return a.func(a)


if __name__ == "__main__":
    sys.exit(main())
