# Argentum / Arcana — six official precon import

Working branch: `gpt/argentum-six-precons`, forked from Build 124 preview commit
`025995bb9d37b2aa62a309ce7ab4852e2a81d377`.

## Scope

Only these six stock 100-card Commander decks, not Collector's Edition duplicates:

- Forces of the Imperium — Inquisitor Greyfax
- Tyranid Swarm — The Swarmlord
- Necron Dynasties — Szarekh, the Silent King
- The Ruinous Powers — Abaddon the Despoiler
- Eldrazi Unbound — Zhulodok, Void Gorger
- Eldrazi Incursion — Ulalek, Fused Atrocity

The source list is `deck-manifest.json`: 600 physical deck slots, each with its original
quantity and commander designation. The archive files were retrieved at
`taw/magic-preconstructed-decks@d2cfc97c25eb5bfaf8b353b6f75717067c4197f6`;
each file references the corresponding Wizards of the Coast decklist.
Foil-etched *display commanders are not playable deck cards* and were explicitly excluded.

## Audited starting point

The exact Build 124 preview main index *plus its separate custom index* contained
427 distinct card names across these decks, 276 of which were absent.
Those absences correspond to **286 missing deck slots**:

| Deck | Missing |
| --- | ---: |
| Forces of the Imperium | 48 |
| Tyranid Swarm | 48 |
| Necron Dynasties | 49 |
| The Ruinous Powers | 49 |
| Eldrazi Unbound | 46 |
| Eldrazi Incursion | 46 |

The double index is essential: Unclaimed Territory is absent from the stock
`all-cards.index.tsv` but already exists in Arcana's `custom-cards.index.tsv`.
Counting the first file alone falsely reports 277 absent unique cards.

`coverage-audit.py` recomputes all counts against whichever index files are
present; it intentionally does *not* assume a fixed missing count once new cards ship.

## Implementation

Source definitions are written using the existing Argentum SDK under
`android/custom-cards/src/main/kotlin/com/arcanatable/customcards/cards/`, registered
in `CustomCards.kt`, and only become Android assets after
`:custom-cards:exportCards`. Each new behavior gets its own scenario test.
## Verified implementation progress — October 21, 2026

**Five newly implemented and exported cards** are in the work branch:
Spatial Contortion, Skittering Invasion, Blight Grenade, Deny Reality, and
Bituminous Blast. Each has its own Kotlin scenario test. Two successive complete
CI runs passed and each committed the generated data assets:
- First batch: https://github.com/AARMOU/arcana_table/actions/runs/39659876554
- Second batch: https://github.com/AARMOU/arcana_table/actions/runs/39660505506

The custom-card index contains 119 cards after the second batch. Rechecking
the six deck manifests against both stock and custom indexes leaves **271
distinct cards / 281 deck slots still unsupported**:
Forces of the Imperium 48, Tyranid Swarm 48, Necron Dynasties 49,
The Ruinous Powers 46, Eldrazi Unbound 45, Eldrazi Incursion 45.

These five definitions are not a completed precon. No six-deck selection or
APK release has been enabled. More difficult commanders and novel rules
interactions must not be approximated or substituted with inert placeholders.

CI: `.github/workflows/argentum-six-precons.yml` runs the custom-card
tests on the isolated branch. No six-deck selection/UI publication, merging into
main, release APK signing, or Play publication is authorized by this preparation.
