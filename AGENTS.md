# AGENTS.md — Arcana Table

Instructions for AI agents (GPT/Codex, Claude, Copilot, others). GitHub Copilot also reads
[`.github/copilot-instructions.md`](.github/copilot-instructions.md).

Arcana Table is an Android MTG table app whose offline rules come from the **Argentum** engine
(`wingedsheep/argentum-engine`, pinned at `61e71e586b100faa661f86cac827ac3c3bbb5a78`, shipped as jars in
`android/app/libs/`). Cards Argentum lacks are written in Argentum's own card DSL in
`android/custom-cards/` and shipped to the app as data.

## Where things are

| Path | What |
|---|---|
| `android/custom-cards/src/main/kotlin/com/arcanatable/customcards/cards/` | one Kotlin file per custom card |
| `android/custom-cards/src/main/kotlin/com/arcanatable/customcards/CustomCards.kt` | the registry (`Entry(Card, "SET", "deck")`) |
| `android/custom-cards/src/test/kotlin/com/arcanatable/customcards/` | one `<Card>ScenarioTest.kt` per card; helpers in `support/` |
| `android/app/src/main/assets/argentum/` | exported data the app loads (`custom-cards.jsonl`, library `all-cards.*`) |
| `android/app/libs/argentum-*.jar` | the engine. **Never edit or replace from a card task.** |
| `investigation/argentum-six-precons/` | six official precon manifest + `coverage-audit.py` |
| `tools/cardkit/` | card toolkit: readiness, references, scaffolding, checks |
| `docs/agents/` | workflow, verified pattern cookbook, generated SDK + pattern indexes |

## Implementing cards

Follow [`docs/agents/CARD-WORKFLOW.md`](docs/agents/CARD-WORKFLOW.md). The short version:

```sh
tools/cardkit/cardkit gaps                      # what is missing, and who should take it
tools/cardkit/cardkit classify "Card" -v        # SUPPORTED / NEEDS_REVIEW / ENGINE_BLOCKED / IMPLEMENTED_AND_TESTED
tools/cardkit/cardkit newcard "Card"            # scaffold + failing test, references per line
# implement using docs/agents/CARD-PATTERNS.md; verify every API in docs/agents/generated/sdk-api.md
cd android && ./gradlew --no-daemon :custom-cards:test --tests 'com.arcanatable.customcards.<Val>ScenarioTest'
# register in CustomCards.kt, then:
cd android && ./gradlew --no-daemon :custom-cards:exportCards :custom-cards:test
tools/cardkit/cardkit check
```

Hard rules:

1. **Only call SDK members listed in [`docs/agents/generated/sdk-api.md`](docs/agents/generated/sdk-api.md).**
   It is generated from the committed jar; anything else does not compile or does not exist.
2. **No approximations.** If a rules sentence has no primitive, the card is blocked: run
   `tools/cardkit/cardkit blocker "Card" --reason … --source …`, report it in issue #141, stop.
3. Oracle text, cost, types and stats come from Scryfall (`tools/cardkit/cardkit oracle …`), never memory.
4. Every card has its own scenario test with positive **and** negative cases on the real engine. Never
   weaken, skip or delete another test.
5. Do not register a scaffold; `cardkit check` and CI enforce this.

## Lanes and coordination (issue #81)

- **Worker-neutral ownership:** Any available authorized worker (GPT, Claude, Copilot where explicitly dispatched, or a human) may continue an engine/card task from the exact GitHub commit, issue #81 handoff and tests. Nobody is a required dependency.
- **Engine compatibility:** one authoritative, audited patch series in development. Engine patches, rules tests and audit registration should be proposed **separately from unfinished card/UI work**, then merged only after full engine-specific checks and explicit owner approval. Card/UI work follows the reviewed engine lane.
- **Copilot:** only well-scoped `SUPPORTED` card/glue tasks authorized by existing queue policy. Do not override spend throttles.
- Development base: `gpt/arcana-integration-next`. Work on isolated `gpt/…`, `claude/…` or owner-approved worker branches. Open **draft** PRs against development; post handoffs to #81.
- The owner approves every merge, release and spend.

Never, without explicit owner approval: push to or merge into `main`; release, sign or publish builds;
replace runtime jars or B131 snapshots; change billing, dispatch, `ARCANA_*` variables or Copilot agent
settings; open `[Arcana Queue]` issues or apply `arcana:authorize-once`.

## Weekly engine upgrade and stale-branch guard

`.github/workflows/argentum-weekly-upgrade.yml` moves Arcana onto upstream Argentum every Monday. The separate read-only `.github/workflows/argentum-engine-pr-guard.yml` audits open engine-changing PRs weekly and when engine PRs change. Its checks flag **stale development engine pins**, **mandatory patches missing from the audit**, long-lived isolated engine patches and upstream drift. A drift warning is *not* source-compatibility or Oracle-playability certification; the full upgrade and card/Android suites remain required.

When a run is red (patch conflict, engine build failure, collision or failing test), use its **Handoff** section and [`engine-upgrade/HANDOFF.md`](engine-upgrade/HANDOFF.md). Work on an isolated `gpt/argentum-weekly-fix-<date>` or `claude/argentum-weekly-fix-<date>` (pushing it runs a read-only dry run), never push to `bot/argentum-weekly`.

**One resolution path for every worker:** resolve upstream conflicts in the isolated engine checkout, run `python3 engine-upgrade/weekly.py finish argentum-engine`, commit the refreshed mandatory patches, pin files and any `engine-upgrade/rerere/` resolutions, then rerun the exact-head checks. **Never** hand-copy a resolved patch to a long-lived branch without capturing and testing its reusable resolution. If another worker disappears, take over at the recorded SHA and commands rather than starting the port from scratch.

## Checks to run before asking for review

```sh
python3 -m unittest discover -s tools/cardkit/tests
tools/cardkit/cardkit check
tools/cardkit/cardkit docs-check
git diff --check
```
