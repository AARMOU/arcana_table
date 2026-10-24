# Six precons — active development gap as of 2026-10-24

> **This is name/index coverage, not verified Oracle/gameplay completeness.** Full deck playability and a device APK remain blocked by missing cards and engine rules.

**Pinned active integration:** `gpt/arcana-integration-next@bcfcc8e493290fe0f31866a88208b1f369c7ca12`.
**Deck manifest:** `investigation/argentum-six-precons/deck-manifest.json` from the archived official six-precon source; indexes are from the pinned Build 131 integration head.

| Official precon | Indexed slots | Still missing |
|---|---:|---:|
| Forces of the Imperium | 77/100 | 23 |
| Tyranid Swarm | 70/100 | 30 |
| Necron Dynasties | 73/100 | 27 |
| The Ruinous Powers | 70/100 | 30 |
| Eldrazi Unbound | 75/100 | 25 |
| Eldrazi Incursion | 73/100 | 27 |

**Totals: 438/600 indexed, 162 missing slots, 158 distinct missing card names.**

## Face commanders still absent

- **The Swarmlord** — Tyranid Swarm
- **Abaddon the Despoiler** — The Ruinous Powers
- **Zhulodok, Void Gorger** — Eldrazi Unbound
- **Ulalek, Fused Atrocity** — Eldrazi Incursion

Two already indexed face commanders are Inquisitor Greyfax and Szarekh, the Silent King. Index presence is not proof of correct game behavior.

## Missing names by deck

### Forces of the Imperium — 23 slots

Collective Effort, Launch the Fleet, Port Town, Marneus Calgar, Celestine, the Living Saint, Defenders of Humanity, Triumph of Saint Katherine, Vexilus Praetor, Zephyrim, Belisarius Cawl, Birth of the Imperium, Callidus Assassin, Cybernetica Datasmith, Epistolary Librarian, The Flesh Is Weak, Inquisitor Eisenhorn, Neyam Shai Murad, Sister Repentia, The Golden Throne, Inquisitorial Rosette, Reaver Titan, Redemptor Dreadnought, Thunderhawk Gunship

### Tyranid Swarm — 30 slots

The Swarmlord, Icon of Ancestry, Abundance, Opal Palace, Magus Lucea Kane, Genestealer Patriarch, Exocrine, The Red Terror, Biophagus, Broodlord, Clamavus, Haruspex, Hierophant Bio-Titan, Hormagaunt Horde, Nexos, Old One Eye, Sporocyst, Termagant Swarm, Toxicrene, The First Tyrannic War, Ghyrson Starn, Kelermorph, Malanthrope, Mawloc, Ravener, Shadow in the Warp, Tyranid Harridan, Acolyte Hybrid, Purestrain Genestealer, Trygon Prime, Goliath Truck

### Necron Dynasties — 27 slots

Caged Sun, Imotekh the Stormlord, Anrakyr the Traveller, Biotransference, Cryptek, Illuminor Szeras, Lokhust Heavy Destroyer, Lychguard, Necron Overlord, Out of the Tombs, Shard of the Nightbringer, Shard of the Void Dragon, Technomancer, Their Number Is Legion, Tomb Blade, Trazyn the Infinite, Triarch Stalker, The War in Heaven, Canoptek Scarab Swarm, Canoptek Wraith, Convergence of Dominion, Ghost Ark, Necron Monolith, Resurrection Orb, Sceptre of Eternal Glory, Tomb Fortress, Psychomancer

### The Ruinous Powers — 30 slots

Abaddon the Despoiler, Past in Flames, Assault Suit, Be'lakor, the Dark Master, Mortarion, Daemon Primarch, Nurgle's Conscription, Poxwalkers, Sloppity Bilepiper, Aspiring Champion, Bloodthirster, Dark Apostle, Khârn the Betrayer, Knight Rampager, Let the Galaxy Burn, Blood for the Blood God!, Chaos Defiler, Chaos Mutation, Drach'Nyen, Exalted Flamer of Tzeentch, Helbrute, The Horus Heresy, Kill! Maim! Burn!, Lucius the Eternal, Magnus the Red, Mutalith Vortex Beast, Pink Horror, The Ruinous Powers, Tzaangor Shaman, Seeker of Slaanesh, The Lost and the Damned

### Eldrazi Unbound — 25 slots

Zhulodok, Void Gorger, Omarthis, Ghostfire Initiate, Kozilek, the Great Distortion, Abstruse Archaic, Calamity of the Titans, Desecrate Reality, Flayer of Loyalties, Ugin's Mastery, Darksteel Monolith, It That Betrays, Ugin, the Ineffable, Ancient Stone Idol, Forsaken Monument, Kaldra Compleat, Metalwork Colossus, Mirage Mirror, Myriad Construct, Phyrexian Triniform, Arch of Orazca, Mirrorpool, Tyrite Sanctum, Geode Golem, Not of This World, Arcane Lighthouse, Eldrazi Temple

### Eldrazi Incursion — 27 slots

Ulalek, Fused Atrocity, Azlask, the Swelling Scourge, Benthic Anomaly, Eldritch Immunity, Selective Obliteration, Angelic Aberration, Bismuth Mindrender, Hideous Taskmaster, Twins of Discord, Mutated Cultist, Eldrazi Confluence, Spawnbed Protector, Chittering Dispatcher, Inversion Behemoth, Ugin, the Ineffable, Eldrazi Conscription, Sire of Stagnation, Morophon, the Boundless, Kozilek's Return, Corrupted Crossroads, Vile Redeemer, Eldrazi Displacer, Rishkar's Expertise, Eldrazi Temple, Forsaken Monument, Ulamog's Nullifier, Opal Palace

## Cross-deck gaps

- Opal Palace (2 slots): Tyranid Swarm; Eldrazi Incursion
- Ugin, the Ineffable (2 slots): Eldrazi Unbound; Eldrazi Incursion
- Forsaken Monument (2 slots): Eldrazi Unbound; Eldrazi Incursion
- Eldrazi Temple (2 slots): Eldrazi Unbound; Eldrazi Incursion

## In-flight (exclude from verified coverage until exact-head tests and committed export)

- [PR #119](https://github.com/AARMOU/arcana_table/pull/119) — Eldrazi Conscription for Eldrazi Incursion (one slot). Initial CI passed; exact committed-head CI pending as of report refresh.
- [PR #120](https://github.com/AARMOU/arcana_table/pull/120) — Goliath Truck for Tyranid Swarm (one slot). Scenario/Android CI pending as of report refresh.
- [PR #107](https://github.com/AARMOU/arcana_table/pull/107) — Zephyrim / Miracle–Squad unresolved; neither counted nor merge-ready.

## Completed development integration

- [PR #118](https://github.com/AARMOU/arcana_table/pull/118): 12 cards (Imperium 3, Tyranid 2, Necron 3, Ruinous Powers 2, Eldrazi Unbound 2) squash-merged DEV-ONLY as `bcfcc8e493290fe0f31866a88208b1f369c7ca12`.
- [Post-commit CI run 40104585345](https://github.com/AARMOU/arcana_table/actions/runs/40104585345): 436/436 custom-card tests passed, 236 Android debug cases (0 failures, 4 skipped); exported assets 229 distinct names and hash-verified.
- Earlier [PR #98](https://github.com/AARMOU/arcana_table/pull/98): three Eldrazi Incursion definitions included in this base.

## Reproduce on any worktree with this manifest and pinned indexes

```bash
python3 investigation/argentum-six-precons/coverage-audit.py --json-output /tmp/six-precon-gaps.json --markdown-output /tmp/six-precon-gaps.md
```

## Non-negotiable release gates

1. All 600 slots match the official six decklists; all six face commanders exist in the true runtime and are legal to cast.
2. All missing card implementations have full Oracle effects and executable positive/negative Argentum scenarios. Name presence is not enough.
3. Colorless-hybrid mana, stack copies, Myriad, Miracle/Squad, cascade and command-zone casting/counters are tested against the exact engine as applicable.
4. Exact-head full custom-card and Android regression passes plus deterministic JSONL/index artifact parity.
5. A debug APK from that specific validated head is installed on the Fold and every precon can complete an actual two-player offline AI game; production/release only on explicit approval.

Safety: `main`, signing and Play Store unaffected. Never bypass #81 numeric billing/reservation safeguards or spawn a paid worker while admission is blocked.
