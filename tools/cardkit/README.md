# cardkit

Card-implementation toolkit for agents (GPT, Copilot, Claude) working on Arcana's custom cards.
Python 3.9+ standard library only; `sdk-index` additionally needs a JDK (`javap`). Run from the repo root:

```sh
tools/cardkit/cardkit <command> --help
```

| Command | What it does |
|---|---|
| `gaps [--deck FILE…] [--json-output P] [--markdown-output P] [--no-refs]` | Deck coverage + readiness for the six precons (default) or any ManaBox/Moxfield text or CSV export; per-deck playable slots, missing cards grouped by mechanic, `dispatch` lists per route |
| `classify NAME… [-v] [--json]` | `SUPPORTED` / `NEEDS_REVIEW` / `ENGINE_BLOCKED` / `IMPLEMENTED_AND_TESTED`, with reasons and per-line references |
| `similar NAME` / `similar --text "…"` | Implemented cards whose rules text matches each line: custom cards (file + test) or Argentum library cards (pinned upstream source link) |
| `newcard NAME [--offline]` | Card file + deliberately failing scenario test; facts filled in, every rules line a `CARDKIT-TODO` with references |
| `check [--strict-oracle]` | CI rules: no registered scaffolds, every registered card tested, no orphan definitions, Oracle drift warnings |
| `unit NAME…` | `[Arcana Queue]` issue body (dispatcher format) for SUPPORTED cards — printed only |
| `blocker NAME --reason … --source … [--verified]` | Records an engine gap in `data/known-blockers.json` and prints a #141 record |
| `oracle fetch [NAME…] [--deck F] [--missing-only]` / `oracle show NAME` | Refresh / read the offline Scryfall cache (`data/oracle-cache.jsonl`) |
| `patterns [--check]` | Regenerates `docs/agents/generated/pattern-index.{md,json}` |
| `sdk-index [--check]` | Regenerates `docs/agents/generated/sdk-api.{md,json}` from `android/app/libs/argentum-mtg-sdk.jar` |
| `docs-check` | Verifies agent docs: SDK names exist, excerpts match real files, links resolve |

The existing `investigation/argentum-six-precons/coverage-audit.py` keeps its output and flags, and gains
`--deck` and `--readiness` (which delegate to `gaps`).

## How readiness is decided

1. **Implemented?** Argentum library index, or a custom card that is registered, has a scenario test and
   no scaffold markers → `IMPLEMENTED_AND_TESTED`.
2. **Recorded gap?** Card or keyword listed in `data/known-blockers.json` → `ENGINE_BLOCKED`.
3. **Oracle record** from `data/oracle-cache.jsonl` (none → `NEEDS_REVIEW`).
4. **Keywords** must be known: SDK vocabulary (generated index) or printed as a keyword on an implemented
   card. Scryfall lists ability words and 40K named abilities ("Martyrdom —") as keywords; those are ignored.
5. **Every rules line** is matched against ~30k lines from implemented cards (library Oracle text from the
   app's assets + custom cards), normalized for self-references, numbers, ability words and reminder text.
   A line also scores by its clauses (trigger condition / cost / each sentence; quoted granted abilities
   stay whole), minus a 0.04 combination penalty, so "When this dies, you gain 2 life and draw two cards"
   counts when both halves exist. All lines ≥ 0.88 → `SUPPORTED`; any line < 0.6 → `NEEDS_REVIEW` with
   `reviewHint: possible-engine-gap` (routed to Claude).
6. `gaps --claims-from-github` (read-only, needs `gh`) keeps cards named in an open PR's title or branch out
   of the dispatch lists (`dispatch.claimed`), and notes PRs whose body mentions a card.

Nothing is ever marked supported by name or art alone, and nothing is marked blocked without a recorded
source.

## Data files

| File | Source | Refresh |
|---|---|---|
| `data/oracle-cache.jsonl` | Scryfall `/cards/collection` | `cardkit oracle fetch …` (or the CI job's artifact) |
| `data/known-blockers.json` | issues/PRs/docs that reproduced a gap | `cardkit blocker …`; delete entries when fixed |
| `data/upstream-card-sources.tsv.gz` | card name → source path in upstream Argentum at the pinned commit | regenerate when the pin moves |
| `data/check-baseline.json` | pre-existing exceptions for `check` (currently none) | never add to it |

## Tests

```sh
python3 -m unittest discover -s tools/cardkit/tests -v
```

They run against the real card definitions, registry, assets and SDK index.
