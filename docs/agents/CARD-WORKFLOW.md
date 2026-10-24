# Implementing a card: the workflow

For GPT, Copilot and Claude alike. Every command runs from the repository root with plain `python3`
(no installs). Gradle commands need a JDK 17 and network access to Maven/Google (true on GitHub runners
and in Copilot's environment).

```
find → classify → scaffold → implement → test → register + export → check → draft PR
```

## 1. Find work that can actually be finished

```sh
tools/cardkit/cardkit gaps                         # six official precons (default manifest)
tools/cardkit/cardkit gaps --deck my-deck.txt      # any ManaBox / Moxfield text or CSV export
tools/cardkit/cardkit gaps --json-output out/gaps.json --markdown-output out/gaps.md
```

Each card gets one readiness class:

| Readiness | Meaning | Who |
|---|---|---|
| `SUPPORTED` | every rules line matches an already-implemented card; no unknown keyword | Copilot / GPT |
| `NEEDS_REVIEW` | no cached Oracle text, an unmatched line, an unknown keyword, multi-face, or a scaffold in progress | GPT triage; `reviewHint: possible-engine-gap` → Claude |
| `ENGINE_BLOCKED` | a recorded engine gap applies (`tools/cardkit/data/known-blockers.json`) | Claude (issue #141) |
| `IMPLEMENTED_AND_TESTED` | Argentum library card, or registered custom card with a scenario test | nobody |

`SUPPORTED` is evidence, not a promise: it means the engine already expresses each sentence somewhere.
The JSON's `dispatch` block lists names per route. `tools/cardkit/cardkit unit "A" "B"` prints an
`[Arcana Queue]` issue body (dispatcher format) for SUPPORTED cards; it never files anything — opening a
queue issue is an owner decision because it can spend Copilot credits.

## 2. Classify one card and read its references

```sh
tools/cardkit/cardkit classify "Card Name" -v      # readiness, reasons, best match per line
tools/cardkit/cardkit similar "Card Name"          # top implemented matches per line, with file paths
tools/cardkit/cardkit similar --text "Whenever this creature attacks, draw a card."
```

Matches marked `[custom]` link a Kotlin file **and its test** in this repo. Matches marked `[library]` link
the card's source in upstream Argentum at the pinned commit (`61e71e5…`), which is the same DSL the app
compiles against.

No Oracle text cached? `tools/cardkit/cardkit oracle fetch "Card Name"` (needs api.scryfall.com). If your
sandbox blocks Scryfall, ask for the cardkit CI job's `oracle-cache` artifact or a teammate with access —
do not type Oracle text from memory.

## 3. Scaffold

```sh
tools/cardkit/cardkit newcard "Card Name"            # or --offline to use only the cache
```

Creates `cards/<Val>.kt` (name, cost, identity, type line, stats, verbatim Oracle text, metadata, plain
keywords already proven by tested cards) and `<Val>ScenarioTest.kt` that **fails on purpose**. Each rules
line becomes a `CARDKIT-TODO` with its closest references. A scaffold is not an implementation:
`cardkit check` errors if a scaffold is registered or its failing test is left in place.

## 4. Implement

For each `CARDKIT-TODO`:

1. Open the referenced card (and its test) and copy its shape. [`CARD-PATTERNS.md`](CARD-PATTERNS.md)
   shows the common shapes.
2. Before using any `Effects.X`, `Triggers.x.y`, `Conditions.X`, `Costs.X`, `DynamicAmounts.x`,
   `Filters.X`, `Targets.X`, `Patterns.X.y`, `KeywordAbility.x`, `Keyword.X`: confirm it in
   [`generated/sdk-api.md`](generated/sdk-api.md). Not listed = does not exist.
3. Model the Oracle text literally: "may" vs mandatory, "up to N" vs N, "target" vs "each", intervening
   "if", who controls/draws/chooses, timing (cast vs resolution), last-known information.
4. Delete the `CARDKIT-TODO` line once that line is implemented.

**Stop rule.** If one line has no primitive after you have checked the references, `sdk-api.md` and the
vendored language reference, the card is blocked. Do not approximate it, do not ship part of it, do not
touch `android/app/libs/*.jar`, `argentum-patches/` or engine sources. Instead:

```sh
tools/cardkit/cardkit blocker "Card Name" --reason "exact missing primitive" --source "PR/issue/test that shows it"
```

commit that one-line data change on your branch, paste the printed record into issue #141, and move on.
Two failed implementation attempts on the same line is the limit.

## 5. Test on the real engine

Replace the scaffold test with real scenarios: the positive case for each ability **and** its negative /
edge case. Patterns and helpers: [`CARD-PATTERNS.md` §8](CARD-PATTERNS.md#8-scenario-tests).

```sh
cd android
./gradlew --no-daemon :custom-cards:test --tests 'com.arcanatable.customcards.<Val>ScenarioTest'
```

Then delete the `CARDKIT-SCAFFOLD` comment block in the card file.

## 6. Register and export

Add `Entry(<Val>, "<SET>", "<deck label>"),` to `CustomCards.kt` (set code of the real printing; deck
label like the neighbouring entries, e.g. `"Six WotC precons"`). Then:

```sh
cd android
./gradlew --no-daemon :custom-cards:exportCards :custom-cards:test
```

Commit the regenerated `android/app/src/main/assets/argentum/custom-cards.{jsonl,index.tsv}`.
`CustomCardDataTest` fails if the committed data does not match the definitions.

## 7. Check and open a draft PR

```sh
tools/cardkit/cardkit check          # must print 0 error(s)
git diff --check
```

- Branch: `copilot/…` or `gpt/…` for card work, `claude/…` for engine/infrastructure.
- Base: `gpt/arcana-integration-next`. Draft PR. One card per commit; up to ~5 related cards per PR.
- PR body: cards, the exact Gradle commands you ran with results, CI run link, anything left out and why.
- Never push to `main`, release, sign, change billing/dispatch settings or replace runtime JARs.
- Post a short handoff in issue #81 when the PR is ready for review.

## Updating the toolkit's data

| When | Run |
|---|---|
| a card is added/registered (CI checks this) | `tools/cardkit/cardkit patterns` |
| the engine jars change (CI checks this; needs a JDK) | `tools/cardkit/cardkit sdk-index`, then re-vendor `docs/agents/argentum-upstream/` from the new pinned commit |
| new deck lists to audit | `tools/cardkit/cardkit oracle fetch --deck list.txt --missing-only` |
| a blocker is fixed in the engine | remove its entry from `tools/cardkit/data/known-blockers.json` |
