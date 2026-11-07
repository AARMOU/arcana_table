# Copilot instructions — Arcana Table

You are most often asked to implement Magic: The Gathering cards for the Argentum rules engine inside
`android/custom-cards/`. Read [`AGENTS.md`](../AGENTS.md) and follow
[`docs/agents/CARD-WORKFLOW.md`](../docs/agents/CARD-WORKFLOW.md) exactly.

## Before writing any code

```sh
tools/cardkit/cardkit classify "<Card Name>" -v
```

- `SUPPORTED`: proceed. The output lists, per rules line, an implemented card to copy (file + test).
- `ENGINE_BLOCKED` or `NEEDS_REVIEW` with `possible-engine-gap`: **do not implement.** Comment on the
  issue with the classify output and stop — this is Claude's engine work (#141), not a card task.
- `NEEDS_REVIEW` (no Oracle record cached): run `tools/cardkit/cardkit oracle fetch "<Card Name>"`. If
  Scryfall is unreachable, say so in the issue and stop; never write card text from memory.

## Implementing

1. `tools/cardkit/cardkit newcard "<Card Name>"` creates the card file and a deliberately failing test.
2. Replace each `CARDKIT-TODO` with code copied in shape from the referenced card and
   [`docs/agents/CARD-PATTERNS.md`](../docs/agents/CARD-PATTERNS.md).
3. Every `Effects.*`, `Triggers.*`, `Conditions.*`, `Costs.*`, `DynamicAmounts.*`, `Filters.*`,
   `Targets.*`, `Patterns.*`, `KeywordAbility.*` and `Keyword.*` you use must appear in
   [`docs/agents/generated/sdk-api.md`](../docs/agents/generated/sdk-api.md). If it is not there, it does
   not exist — do not invent it, do not add it, do not edit `android/app/libs/` or `argentum-patches/`.
4. If any sentence cannot be expressed: stop, run
   `tools/cardkit/cardkit blocker "<Card Name>" --reason "<missing primitive>" --source "<this issue/PR>"`,
   commit only that data change, and report. Do not ship a partial or approximate card.

## Testing

- Write real scenarios in `<Val>ScenarioTest.kt`: one positive and at least one negative/edge case per
  ability ("may" declined, no legal target, intervening "if" false, wrong controller).
- `customScenario(Card, "Library Card", …)` — name every Argentum library card the test uses.
- Run: `cd android && ./gradlew --no-daemon :custom-cards:test --tests 'com.arcanatable.customcards.<Val>ScenarioTest'`
- Never weaken, `@Ignore` or delete an existing test.

## Finishing

1. Remove the `CARDKIT-SCAFFOLD` block; add `Entry(<Val>, "<SET>", "<deck label>"),` to `CustomCards.kt`.
2. `cd android && ./gradlew --no-daemon :custom-cards:exportCards :custom-cards:test` and commit the
   regenerated `android/app/src/main/assets/argentum/custom-cards.*`.
3. `tools/cardkit/cardkit check` must report 0 errors; `git diff --check` clean.
4. Draft PR against `gpt/arcana-integration-next`, one commit per card, with the exact commands and results.

## Scope of a card PR

Approved cards are integrated automatically, and integration accepts **only** these files. A PR that
changes any other file is skipped, even after certification and owner approval:

- `android/custom-cards/src/main/kotlin/com/arcanatable/customcards/cards/<Val>.kt` (added)
- `android/custom-cards/src/test/kotlin/com/arcanatable/customcards/<Val>ScenarioTest.kt` (added)
- `android/custom-cards/src/main/kotlin/com/arcanatable/customcards/CustomCards.kt` (modified)
- the regenerated `android/app/src/main/assets/argentum/custom-cards.jsonl` and `custom-cards.index.tsv`
- the regenerated `docs/agents/generated/pattern-index.json` and `pattern-index.md`

If adding your card makes a **shared** test fail (for example anything under `tools/cardkit/tests/`),
do not edit that test in your card PR. Report the failing test and its output in the PR and on the
issue, and stop; the shared fix lands separately on `gpt/arcana-integration-next`.

Never touch `main`, release/signing, billing or dispatch configuration, or other cards' behavior.
