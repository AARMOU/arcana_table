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

**37 distinct missing cards implemented, scenario-tested and exported.**
The first 16 were:

- Spatial Contortion; Skittering Invasion; Blight Grenade; Deny Reality; Bituminous Blast
- Awakening Zone; Crashing Drawbridge; All Is Dust
- For the Emperor!; Knight Paladin; Bloodcrusher of Khorne
- Tyranid Invasion; Sanguinary Priest; Venomthrope; Tyranid Prime; Space Marine Scout

The following sixteen-card batch was then scenario-tested and exported:

- Defile; Mutilate; Their Name Is Death; Plasmancer; Ulamog's Crusher
- Assault Intercessor; Herald of Slaanesh; Noise Marine; Chaos Terminator Lord
- Sister Hospitaller; Desert of the Glorified; Guildless Commons; Tendo Ice Bridge
- Hull Breach; Atalan Jackal; Winged Hive Tyrant

The next five implemented cards are **Tallyman of Nurgle, Necron Deathmark,
Sea Gate Wreckage, Beacon of Unrest, and Geier Reach Sanitarium**. Tallyman's
one- and seven-creature-death branches explicitly charge life to its controller.
Beacon reanimates a card from either graveyard under its caster's control and
uses the SDK's self-shuffle-on-resolve flag. Sea Gate Wreckage's empty-hand gate
is an activation restriction; Geier Reach Sanitarium makes every player draw
before any player discards.

Every implemented card has a scenario test. The verified GitHub Actions card
regression, export, duplicate/coverage validation and asset commits include:

- https://github.com/AARMOU/arcana_table/actions/runs/39675301048
- https://github.com/AARMOU/arcana_table/actions/runs/39680532346
- https://github.com/AARMOU/arcana_table/actions/runs/39681757357

**Latest checked, exported commit:** `1ff2efd9c024003edbf77ee0f2aaf936ed7396f6`.
The custom-card index contains **151 distinct names**. Comparing both indexes
against the six official 100-card decklists leaves **239 distinct cards (248 deck
slots) missing**.

| Deck | Missing deck slots |
| --- | ---: |
| Forces of the Imperium | 42 |
| Tyranid Swarm | 42 |
| Necron Dynasties | 42 |
| The Ruinous Powers | 41 |
| Eldrazi Unbound | 40 |
| Eldrazi Incursion | 41 |

This audit counts card definitions available in the two indexes; it is **not**
an end-to-end certificate that every interaction in each deck works.
Full-deck runs, mechanics spanning unsupported cards, and six-deck UI
publication remain untested/not implemented. More difficult commanders and
novel rules interactions must never be approximated or substituted with
inert placeholders. No release branch, APK signing or Play Store upload occurs
from this import branch without separate authorization.

CI: `.github/workflows/argentum-six-precons.yml` runs the custom-card
tests on the isolated branch. No six-deck selection/UI publication, merging into
main, release APK signing, or Play publication is authorized by this preparation.
