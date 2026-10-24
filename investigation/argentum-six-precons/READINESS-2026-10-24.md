# Six official precons — current implementation gap (2026-10-24)

> Name/index presence only. **Not a gameplay readiness certification.** All unmerged card PRs and unverified mechanics are excluded.

**Integration base:** `d6bdb8b3386e34862f79c04b435195e0014beb47` (`gpt/arcana-integration-next`)
**Manifest:** `investigation/argentum-six-precons/deck-manifest.json`, restored from existing historical audit source (not a new or fabricated decklist).

| Official deck | Indexed / 100 | Missing slots |
|---|---:|---:|
| Forces of the Imperium | 74 | 26 |
| Tyranid Swarm | 68 | 32 |
| Necron Dynasties | 70 | 30 |
| The Ruinous Powers | 68 | 32 |
| Eldrazi Unbound | 73 | 27 |
| Eldrazi Incursion | 73 | 27 |

**TOTAL: 426/600 indexed slots, 174 missing slots, 170 distinct missing names.**

## Commander-first readiness

The following four *official face commanders* are missing from the pinned indexes: **The Swarmlord** (Tyranid Swarm), **Abaddon the Despoiler** (The Ruinous Powers), **Zhulodok, Void Gorger** (Eldrazi Unbound), and **Ulalek, Fused Atrocity** (Eldrazi Incursion). Their decks cannot be represented faithfully even at the commander slot until these cards have engine-backed implementations. Inquisitor Greyfax and Szarekh, the Silent King are indexed; that alone does not prove their behaviors are correct.

**Mechanics with known integration risk:** Ulalek's `{C/color}`-hybrid mana and stack-copy fidelity, Zhulodok's chained cascade, and commander-zone recast semantics must be exercised against the exact B131 engine, not merely asserted from static card text.

## Missing names, by deck

### Forces of the Imperium — 26

Collective Effort, Launch the Fleet, Port Town, Marneus Calgar, Celestine, the Living Saint, Defenders of Humanity, Triumph of Saint Katherine, Ultramarines Honour Guard, Vexilus Praetor, Zephyrim, Arco-Flagellant, Belisarius Cawl, Birth of the Imperium, Callidus Assassin, Commissar Severina Raine, Cybernetica Datasmith, Epistolary Librarian, The Flesh Is Weak, Inquisitor Eisenhorn, Neyam Shai Murad, Sister Repentia, The Golden Throne, Inquisitorial Rosette, Reaver Titan, Redemptor Dreadnought, Thunderhawk Gunship

### Tyranid Swarm — 32

The Swarmlord, Icon of Ancestry, Abundance, Opal Palace, Magus Lucea Kane, Genestealer Patriarch, Exocrine, The Red Terror, Biophagus, Bone Sabres, Broodlord, Clamavus, Haruspex, Hierophant Bio-Titan, Hormagaunt Horde, Nexos, Old One Eye, Sporocyst, Termagant Swarm, Tervigon, Toxicrene, The First Tyrannic War, Ghyrson Starn, Kelermorph, Malanthrope, Mawloc, Ravener, Shadow in the Warp, Tyranid Harridan, Acolyte Hybrid, Purestrain Genestealer, Trygon Prime, Goliath Truck

### Necron Dynasties — 30

Caged Sun, Cranial Plating, Imotekh the Stormlord, Anrakyr the Traveller, Biotransference, Chronomancer, Cryptek, Illuminor Szeras, Lokhust Heavy Destroyer, Lychguard, Necron Overlord, Out of the Tombs, Royal Warden, Shard of the Nightbringer, Shard of the Void Dragon, Technomancer, Their Number Is Legion, Tomb Blade, Trazyn the Infinite, Triarch Stalker, The War in Heaven, Canoptek Scarab Swarm, Canoptek Wraith, Convergence of Dominion, Ghost Ark, Necron Monolith, Resurrection Orb, Sceptre of Eternal Glory, Tomb Fortress, Psychomancer

### The Ruinous Powers — 32

Abaddon the Despoiler, Past in Flames, Assault Suit, Be'lakor, the Dark Master, Mortarion, Daemon Primarch, Nurgle's Conscription, Poxwalkers, Sloppity Bilepiper, Aspiring Champion, Bloodthirster, Dark Apostle, Keeper of Secrets, Khârn the Betrayer, Knight Rampager, Let the Galaxy Burn, Blood for the Blood God!, Chaos Defiler, Chaos Mutation, Drach'Nyen, Exalted Flamer of Tzeentch, Helbrute, The Horus Heresy, Kill! Maim! Burn!, Lucius the Eternal, Magnus the Red, Mutalith Vortex Beast, Pink Horror, The Ruinous Powers, Tzaangor Shaman, Nurgle's Rot, Seeker of Slaanesh, The Lost and the Damned

### Eldrazi Unbound — 27

Zhulodok, Void Gorger, Omarthis, Ghostfire Initiate, Kozilek, the Great Distortion, Abstruse Archaic, Calamity of the Titans, Desecrate Reality, Flayer of Loyalties, Ugin's Mastery, Darksteel Monolith, It That Betrays, Matter Reshaper, Ugin, the Ineffable, Ancient Stone Idol, Forsaken Monument, Kaldra Compleat, Metalwork Colossus, Mirage Mirror, Myriad Construct, Phyrexian Triniform, Arch of Orazca, Mirrorpool, Tyrite Sanctum, Geode Golem, Not of This World, Scaretiller, Arcane Lighthouse, Eldrazi Temple

### Eldrazi Incursion — 27

Ulalek, Fused Atrocity, Azlask, the Swelling Scourge, Benthic Anomaly, Eldritch Immunity, Selective Obliteration, Angelic Aberration, Bismuth Mindrender, Hideous Taskmaster, Twins of Discord, Mutated Cultist, Eldrazi Confluence, Spawnbed Protector, Chittering Dispatcher, Inversion Behemoth, Ugin, the Ineffable, Eldrazi Conscription, Sire of Stagnation, Morophon, the Boundless, Kozilek's Return, Corrupted Crossroads, Vile Redeemer, Eldrazi Displacer, Rishkar's Expertise, Eldrazi Temple, Forsaken Monument, Ulamog's Nullifier, Opal Palace

## Shared cards worth deduplicating first

- **Opal Palace** (2 slots): Tyranid Swarm; Eldrazi Incursion
- **Ugin, the Ineffable** (2 slots): Eldrazi Unbound; Eldrazi Incursion
- **Forsaken Monument** (2 slots): Eldrazi Unbound; Eldrazi Incursion
- **Eldrazi Temple** (2 slots): Eldrazi Unbound; Eldrazi Incursion

## Existing work not yet counted as complete

- [#94](https://github.com/AARMOU/arcana_table/pull/94) — current B131 integration base; three Eldrazi custom definitions already counted.
- [#74](https://github.com/AARMOU/arcana_table/pull/74) — Imperium batch (Arco-Flagellant, Ultramarines Honour Guard, Commissar Severina Raine).
- [#76](https://github.com/AARMOU/arcana_table/pull/76) — Tyranid batch (Bone Sabres, Tervigon).
- [#77](https://github.com/AARMOU/arcana_table/pull/77) — Necron batch (Cranial Plating, Chronomancer, Royal Warden).
- [#84](https://github.com/AARMOU/arcana_table/pull/84) — Ruinous Powers batch (Keeper of Secrets, Nurgle's Rot).
- [#85](https://github.com/AARMOU/arcana_table/pull/85) — Eldrazi Unbound batch (Matter Reshaper, Scaretiller).
- [#107](https://github.com/AARMOU/arcana_table/pull/107) — Zephyrim still blocked by failed Miracle/Squad priority scenario and possible engine interaction gap.
- [#98](https://github.com/AARMOU/arcana_table/pull/98) — three Eldrazi Incursion cards merged to this *development* integration branch; current 73/100 count includes them.

## Reproduce

```bash
python3 investigation/argentum-six-precons/coverage-audit.py --json-output /tmp/six-precon-gaps.json --markdown-output /tmp/six-precon-gaps.md
```

## Ship gates

1. 600/600 official deck slots indexed at **one pinned integration SHA**, with commanders and deck entry points verified; no placeholder substitutions counted.
2. For every engine/custom card, executable real-engine Oracle scenarios, including shared keyword/trigger/cast interactions and multiplayer where relevant.
3. Verify colorless-hybrid `{C/color}`, stack copy, Myriad and Miracle/Squad correctness before claiming the affected cards.
4. Pass deterministic registry/export audit, complete custom-card tests, Android debug unit tests and a head-matched Build 131 integration gate.
5. Build a debug APK from the exact validated candidate and test each deck against offline AI through actual turns; production/release only after explicit owner authorization.

No paid workers, dispatch changes, production merges, APKs, or signing actions are part of this report.
