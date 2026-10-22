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
## Active hardest-first work — October 22, 2026

The resumed work starts with **Ulalek, Fused Atrocity**, ahead of simpler card definitions.
Its stack-copy engine patch and executable scenarios are in
`staged-engine/0004-stack-object-copying.patch`; the exact unregistered card candidate and
an executed reproduction of the missing colorless-hybrid mana parser are in `blocked/ulalek/`.
The 12 existing spell-copy retargeting regressions passed locally. New SDK/batch-copy validation
and the dedicated stack-copy CI workflow are still in progress; they are not reported as passed.
No pinned app JAR, live registry, exported asset, release or APK changed. The verified card
count below therefore remains **80**, with **196 distinct cards / 201 deck slots missing**.
See `SDK-GAPS.md` for the remaining engine and Android integration gates.

## Verified implementation progress — October 21, 2026

**80 distinct missing cards implemented, scenario-tested and exported** (33 beyond the previous 47-card checkpoint).
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

Three subsequent batches added ten cards and individually tested their real SDK behavior:

- **Batch 1:** Deploy to the Front; Spawning Bed; Mage-Ring Network.
  Tested multiplayer creature counts, sacrifice-for-mana Eldrazi Scions,
  and variable storage-counter payments.
- **Batch 2:** Ugin's Insight; Gargoyle Flock; Sifter of Skulls; Drowner of Hope.
  Tested variable scrying, end-step Tyranid creation, nontoken death
  triggers, Devoid, Scion creation, and sacrifice-to-tap abilities.
- **Batch 3:** Molten Slagheap; Ruins of Oran-Rief; Shrine of the Forsaken Gods.
  Tested the mana-split decision, creature-color/entry targeting, the
  seven-land gate, and mana restricted to colorless spells.

All three batches passed full custom-card CI regressions and committed generated
Android assets. Successful checkpoint:
https://github.com/AARMOU/arcana_table/actions/runs/39687965995

The next verified 25-card batch included **Bile Blight, Exterminatus, Endless Atlas,
Cryptothrall, Night Scythe, Sautekh Immortal, Skorpekh Lord, Lictor, Bred for the Hunt,
Deathleaper, Terror Weapon, Genestealer Locus, Primaris Chaplain, Sister of Silence,
Space Marine Devastator, Grey Knight Paragon, Plague Drone, Heralds of Tzeentch,
Lord of Change, Venomcrawler, Deepfathom Skulker, Skittering Cicada, Bane of Bala Ged,
Soul of New Phyrexia, Inquisitor Greyfax, and Szarekh, the Silent King**. Scenario tests
were executed for each. These definitions are exported; this count does not include the
blocked Port Town candidate documented in `SDK-GAPS.md`.

The preceding five verified implementations are **Bastion Protector, Reverberate,
Warping Wail, Stonecoil Serpent, and Return of the Wildspeaker**.
Their executable tests cover controller-specific commander buffs, stack-spell copying,
all three modal choices including Scion mana generation, X entry counters and
multicolored protection, and Human-excluding modal draw or combat buffs.
Full custom-card regressions and index/export verification passed:
https://github.com/AARMOU/arcana_table/actions/runs/39719229079

Three more tested and exported implementations are **Thunderwolf Cavalry**, **Vanguard Suppressor**, and **Company Commander**. They have real combat, squad, and entry-trigger scenarios. Full custom-card regressions, asset export, and coverage audit passed in [Actions run 39730047167](https://github.com/AARMOU/arcana_table/actions/runs/39730047167).

Every implemented card has a scenario test. The verified GitHub Actions card
regression, export, duplicate/coverage validation and asset commits include:

- https://github.com/AARMOU/arcana_table/actions/runs/39675301048
- https://github.com/AARMOU/arcana_table/actions/runs/39680532346
- https://github.com/AARMOU/arcana_table/actions/runs/39681757357
- https://github.com/AARMOU/arcana_table/actions/runs/39686282839
- https://github.com/AARMOU/arcana_table/actions/runs/39687026625
- https://github.com/AARMOU/arcana_table/actions/runs/39687965995
- https://github.com/AARMOU/arcana_table/actions/runs/39717112709
- https://github.com/AARMOU/arcana_table/actions/runs/39719229079

**Latest checked, exported commit:** `abf16c3a114859673d04ad2c434d4e073ad5348a`.
The custom-card index contains **194 distinct names**. Comparing both indexes
against the six official 100-card decklists leaves **196 distinct cards (201 deck
slots) missing**.

| Deck | Missing deck slots |
| --- | ---: |
| Forces of the Imperium | 31 |
| Tyranid Swarm | 37 |
| Necron Dynasties | 36 |
| The Ruinous Powers | 34 |
| Eldrazi Unbound | 31 |
| Eldrazi Incursion | 32 |

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
