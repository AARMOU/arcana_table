# Pinned Argentum Assay ↔ Arcana Cardkit compatibility (2026-10-24)

**Status:** pinned source and saved ledger inspected; no fresh pinned Assay CLI execution or real Eldrazi scenario result is certified here. Arcana's optional, read-only `cardkit assay-review` command (PR #157) can compare a separately generated Assay bake against Cardkit verdicts. It never promotes card readiness or authorizes worker dispatch; it does not establish that any Eldrazi card is implemented.

## Immutable reference points

- Arcana development base inspected: `gpt/arcana-integration-next@e0a78e3bd7a1d95fd08cc3579440fee665b2a79f`.
- Arcana's compiled engine and generated SDK index are pinned to upstream `wingedsheep/argentum-engine@61e71e586b100faa661f86cac827ac3c3bbb5a78` (see root `AGENTS.md` and `docs/agents/generated/sdk-api.md`).
- That **exact upstream pin already contains** `oracle-assay/`, `oracle-assay/src/main/kotlin/com/wingedsheep/assay/cli/Main.kt`, `.agents/skills/add-card/SKILL.md`, `justfile` and the upstream DSL reference. We need not take an arbitrary upstream-main SDK update merely to *inspect* Assay.
- Source links: [pinned Assay CLI](https://github.com/wingedsheep/argentum-engine/blob/61e71e586b100faa661f86cac827ac3c3bbb5a78/oracle-assay/src/main/kotlin/com/wingedsheep/assay/cli/Main.kt), [pinned add-card skill](https://github.com/wingedsheep/argentum-engine/blob/61e71e586b100faa661f86cac827ac3c3bbb5a78/.agents/skills/add-card/SKILL.md), [pinned Assay Gradle module](https://github.com/wingedsheep/argentum-engine/blob/61e71e586b100faa661f86cac827ac3c3bbb5a78/oracle-assay/build.gradle.kts), [Arcana Cardkit](../../tools/cardkit/README.md).

## Capability crosswalk

| Task | Arcana responsibility / existing tool | Upstream complement | Decision |
| --- | --- | --- | --- |
| Exact precon deck gap and **distinct card** accounting | `cardkit gaps`; pinned app library index + custom registry; deck-manifest | `just card-list-status` / `just card-loop` | **Keep Cardkit**: it knows Arcana's deck and compiled/exported reality. |
| Source-backed similarity search | `cardkit similar`, pattern index, upstream source mapping | `add-card` reference examples, SDK language reference | Reuse the **pattern** and source references, not a second index. |
| Oracle lookup, card scaffolding | `cardkit oracle` + `newcard`, failing scenario skeleton | `add-card` and `assay compile` | Preserve Arcana scaffolder; use compiled JSON only as an optional draft/model aid. |
| Declarative SDK availability | Generated `sdk-api.md` from **committed SDK JAR** | Source-side SDK docs and Assay's parsed `CardDefinition` | **Arcana's pinned JAR wins**. A name appearing only in newer upstream source is not available in our runtime. |
| Oracle semantics and missing grammar | Wording similarity `SUPPORTED`/`NEEDS_REVIEW` (heuristic) | `assay parse/explain/compile` | Add an **optional independent evidence signal**; do not silently promote a readiness class. |
| Real gameplay semantics | Positive + negative `<Card>ScenarioTest.kt`, deterministic custom export, Android integration | Upstream `generate-scenario` / `verify` | **Keep Arcana's stricter tests**, app-private information checks and debug gameplay validation. |
| Work assignment | #81 / #141 / #150, claim de-duplication, one-shot guarded dispatcher | Upstream `just contribute` / `just card-loop` | **Do not run upstream workers**: they bypass Arcana-specific billing, ownership, claims and runtime gates. |
| Card discovery and packaging | `CustomCards.kt` entries → JSONL/index assets → Android app | Upstream `CardDiscovery`, canonical set and reprint rows | **Not interchangeable**. Arcana must register/export even when a source DSL card compiles. |

## What Assay actually establishes

Pinned `oracle-assay` is a source-side Kotlin module depending on `mtg-sdk` and `mtg-sdk-tooling`; **it is not one of Arcana's shipped Android JARs**. For a **separate checkout of exactly the pinned upstream commit** with its required Java/Gradle environment, the upstream command entrypoints are:

```sh
just assay parse "Card Name"
just assay explain "Card Name"
just assay compile "Card Name"
```

These are commands in the **Argentum checkout**, *not* commands under Arcana's `tools/cardkit`. The pinned CLI also supports `assay compile --file path/to/scryfall-card.json` for a locally supplied Scryfall-style card record; this avoids confusing a cached Oracle record with a current network fetch. Check the full upstream CLI help/just recipes before executing; don't assume the `just` runner, downloaded corpus, or JDK 21 toolchain are available in the Android build environment.

**Interpretation rules:**

1. `assay parse` and `explain` are investigative: **exit 0 even when a card's lines decline**. Review every line's verdict and explanation, not process exit status.
2. `assay compile` returns JSON for a wholly parsed card and exits **1 on a decline**. A decline may mean an unhandled *Oracle grammar spelling*, not a missing SDK/engine primitive; inspect generated pinned SDK index, source and examples before recording `ENGINE_BLOCKED`.
3. Conversely, a whole-card read is **not** proof of correct activation timing, mana spend restrictions, cast-time choices, linked exile, targeting, stack priority, private information or Android presentation. Upstream `add-card` explicitly warns about sloppy shapes (additional costs, X/chosen values, cast-time choice carried into later effects).
4. Assay's compilation output is **not a runtime card loader** or a canonical-printing decision. Do not copy `setCode` or image URI from its arbitrary Oracle-bulk printing into a new card blindly.
5. Never turn `SUPPORTED` into `IMPLEMENTED_AND_TESTED` using either Assay or similarity scores. For indexed upstream cards, the Cardkit level already makes explicit that a source index is not an Arcana-specific gameplay scenario.
6. Only the pinned SDK and tested game runtime count for release eligibility. No replacement `android/app/libs/*.jar`, compiled runtime, `argentum-patches/` or B131 baseline is authorized here.

## Proposed safe read-only pilot: Eldrazi Incursion / Eldrazi Unbound

Track under [#150](https://github.com/AARMOU/arcana_table/issues/150) and [#81](https://github.com/AARMOU/arcana_table/issues/81). Existing verified manifest baseline: **75/100 indexed per deck**, three *shared missing names* = **six unfilled deck slots**. That baseline counts indexed cards, not proven gameplay completion.

1. Inspect the three **shared, still missing** names **once each** with the pinned Assay source-side tooling and Cardkit `classify -v`, recording Oracle text revision, full/declined lines, relevant SDK primitives, source references and mismatch risks:
   - **Forsaken Monument** (#151): source-inspected candidate for existing mana and colorless buffs; still requires real tapped-for-colorless and quantity/trigger interaction scenarios.
   - **Eldrazi Temple** (#152): constrained second mana ability; do not substitute `ColorlessSpellsOnly` for “cast Eldrazi spells **or activate abilities of Eldrazi**”.
   - **Ugin, the Ineffable** (#153): cost reduction, destroy colored permanent and per-token linked face-down exile; treat as engine-sensitive until separate tokens and their exiled cards are proven distinct.
2. Compare with **Eldrazi Displacer** (#154) as an ostensibly straightforward control, and **Corrupted Crossroads** (in #150) as an intentionally dangerous wording-near-match. Crossroads permits spending produced colored mana only on Devoid spells, **not all colorless spells**.
3. Store pilot verdicts as **observations**, with `assay=whole|declined|unavailable`, `pinned_sdk_supported=yes|no|unverified`, `scenario=passed|failed|not_run`, and `arcana_route=GPT|Claude|Copilot_candidate`. Do not alter machine dispatch routing on parser output alone.
4. Compare **false-supported** (Cardkit says SUPPORTED, pinned engine cannot express exact Oracle) and **false-blocked** (Cardkit/Assay declines but pinned SDK can express it) against manually source-verified cases. The opt-in `cardkit assay-review` adapter reports additional evidence only; it does **not** adjust classifications, blockers, or dispatch eligibility. Any later automation needs independent review.
5. Only separately authorized, admitted worker issues can go to Copilot. Upstream `card-loop`, `contribute`, remote agent launch, billing/dispatch changes and automatic label application are **out of scope**.

## Optional Cardkit bridge (implemented; advisory only)

To compare an Assay bake from a separate upstream source checkout with Arcana's cached Oracle/Cardkit records:

```sh
tools/cardkit/cardkit assay-review --ledger /tmp/assay-verdicts.json \
  --assay-commit "<full upstream Git SHA>" \
  --card "Forsaken Monument" --card "Eldrazi Temple" \
  --json-output /tmp/assay-cardkit-review.json
```

The caller declares the Assay source commit; the bake JSON does not authenticate its provenance. Always compare against Arcana's pinned engine commit and generated SDK index. A whole-card parser read or decline **cannot** prove runtime correctness or an engine gap. See [CARD-WORKFLOW.md](CARD-WORKFLOW.md#optional-cross-check-upstream-argentum-assay-advisory) for the bake command and verification steps. The saved pinned ledger below is precomputed evidence, not a new Assay execution.

## Acceptance gates for follow-up engineering

- A reproducible **non-writing** pinned Assay smoke on named pilot cards with version, invocation, standard output, stderr and exit codes attached to a draft PR or #81.
- No claim that a parser decline equals missing engine functionality, or that a parser success equals tested gameplay.
- Existing `cardkit check`, `patterns --check`, `sdk-index --check`, `docs-check`, code tests and positive/negative engine scenarios remain authoritative.
- Keep all work off `main`, preserve B131 and the ongoing Ulalek/Lunar Whale/Eldrazi work branches; no paid worker dispatch, JAR swap, merge or release without explicit owner authorization.

## Saved pinned Assay verdicts: evidence, not live execution

The exact pinned upstream tree includes [`game-server/src/main/resources/coverage/assay-verdicts.json`](https://github.com/wingedsheep/argentum-engine/blob/61e71e586b100faa661f86cac827ac3c3bbb5a78/game-server/src/main/resources/coverage/assay-verdicts.json) (blob SHA `40223cfbb9ffe31070298a32f2ac311507c9328b`). Read its JSON records directly: `corpus=34932`, `whole=11129`.

Cross-checked **all 25 exact missing names** from [#150](https://github.com/AARMOU/arcana_table/issues/150) against this saved ledger: **25/25 have `kind=LINE_DECLINED`; 0/25 are saved as whole-card readings.** Examples of the first recorded declined line:

| Card | First decline shown in saved ledger (abridged) | Consequence |
| --- | --- | --- |
| Forsaken Monument | `Colorless creatures you control get +2/+2.` | Even this source-inspected candidate is not fully parsed by the *pinned parser*; don't infer SDK absence. |
| Eldrazi Temple | `{T}: Add {C}{C}. Spend this mana only to cast colorless Eldrazi spells or activate abilities of colorless Eldrazi.` | Continue source/test audit for precisely restricted mana. |
| Ugin, the Ineffable | `Colorless spells you cast cost {2} less to cast.` | Do not mistake the grammar's first decline for proof the hard linked-exile case is or is not supported. |
| Eldrazi Displacer | `{2}{C}: Exile another target creature, then return it to the battlefield tapped under its owner's control.` | Cardkit/source and scenario evidence supersede a grammar decline. |
| Corrupted Crossroads | `{T}, Pay 1 life: Add one mana of any color. Spend this mana only to cast a spell with devoid.` | Restriction is mechanically distinct from general colorless spending. |

The saved ledger does **not** diagnose every line or describe why it declined. It is not a fresh Assay CLI run, proof of a missing engine primitive, or test of Oracle behavior. **Revised prioritization:** use this ledger as a cheap negative screening signal, do *targeted* `assay explain` only when its grammar diagnosis would clarify a concrete card, and focus primary implementation effort on pinned SDK source + Cardkit + executable engine scenarios. Do not build an Assay-dependent auto-approval classifier for this Eldrazi backlog based on the saved verdicts.
