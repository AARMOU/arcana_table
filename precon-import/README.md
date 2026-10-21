# Warhammer 40K + Eldrazi: six-precon engine import

This branch tracks the requested **card implementation**, not merely six importable deck files. It is isolated from the engine-delay/build branches. Android baseline: `gpt/engine-delay-apk-build` at `025995bb9d37b2aa62a309ce7ab4852e2a81d377` (Build 124 preview).

## Authoritative decklists

- [Warhammer 40,000 (four decks)](https://magic.wizards.com/en/news/announcements/warhammer-40000-commander-decklists): Forces of the Imperium, Tyranid Swarm, Necron Dynasties, The Ruinous Powers.
- [Commander Masters](https://magic.wizards.com/en/news/announcements/commander-masters-commander-decklists): Eldrazi Unbound.
- [Modern Horizons 3](https://magic.wizards.com/en/news/announcements/modern-horizons-3-commander-decklists): Eldrazi Incursion.

A pinned machine-readable copy is sourced from [taw/magic-preconstructed-decks-data](https://github.com/taw/magic-preconstructed-decks-data/tree/1f43fd58eb58d67f1c769a2c5f8e8461b4695ee8), file `decks_v2.json`, to enable reproducible comparisons. All deck quantities are checked to total exactly 100. Collector's versions and tokens are not counted as extra decks/cards.

## Audit

`audit.py` reads the bundled `all-cards.index.tsv` **and** `custom-cards.index.tsv`, checks every playable card name, deduplicates across six decks, then writes `inventory.json`, `REPORT.md`, and fifteen numbered batches. It does not modify the app. Run the `Warhammer Eldrazi precon coverage audit` GitHub Actions job to regenerate the inventory as an artifact.

**A name in an index is not behavioral support.** Each absent card needs a correct canonical Argentum `cardDef` (or verified preexisting definition), reprint mapping when applicable, rules/trigger support, scenario tests when earned, card export, and a real-game test. No card should be marked complete solely because its name was added to JSON or an app index.

## Implementation constraints

1. Preserve Build 124's engine-delay work, AI quality, Android packaging and existing seven WotC decks. Do not mutate a live build from this import branch.
2. Implement with Argentum's upstream `.agents/skills/add-card/SKILL.md` and `AGENTS.md`: earliest printing owns definition, later printings use `Printing`; one card's scenarios in one test file.
3. Keep the fifteen batches as **work partitions**, not evidence fifteen agents were started or changes were made. Nontrivial shared engine features should be integrated and tested before cards dependent on those features.
4. Passing a catalog audit cannot be labeled passing Kotlin/Gradle tests; require per-card scenarios and no new AI performance or correctness regressions.
5. Merge/publish only after verified import coverage and mobile compatibility, not after the audit itself.

## Status

Inventory/audit infrastructure is work in progress on this branch. No six-precon engine card implementation has been certified. Earlier historical notes indicated ~276 missing *distinct* names; the CI output is the authoritative audit for this pinned source and baseline.
