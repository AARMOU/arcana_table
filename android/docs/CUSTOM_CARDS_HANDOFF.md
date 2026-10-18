# Custom cards: handoff notes

Arcana includes extra card definitions for offline games. Argentum's own card still takes priority when it has that card. Custom definitions live in `custom-cards`; exporting them creates the files the app reads.

## Added from the submitted decks

- **Ayula, Queen Among Bears:** Mother Bear, Werebear, Bosco, Just a Bear, Wilson, Refined Grizzly, Druid's Deliverance, Traverse the Outlands, and Bearscape.
- **Hazel of the Rootbloom:** Frog-Squirrels, Scurry Oak, Nut Collector, Compy Swarm, Primal Amulet, Immoral Bargain, Acorn Harvest, Cloakwood Hermit, Springleaf Parade, and Black Market Connections.
- **Sliver Overlord:** Acidic Sliver, Cloudshredder Sliver, Crystalline Sliver, Diffusion Sliver, Dregscape Sliver, The First Sliver's Chosen, Frenetic Sliver, Fungus Sliver, Harmonic Sliver, Hibernation Sliver, Homing Sliver, Lavabelly Sliver, Leeching Sliver, Sliver Queen, and Sliver Gravemother.

Each added card has its own scenario-test source targeting the pinned Argentum engine. Tippy-Toe, Terrific Partner is left out because the engine cannot represent its token-copy replacement effect. The Sliver deck still has 14 names missing from this first pass; Hatchery Sliver needs Replicate, and Pulmonic Sliver needs an optional replacement effect. The other unadded Sliver cards need a separate implementation review before inclusion.

## Submitted card pool

Added 30 more cards from the larger collection: Constricting Sliver, Kodama of the West Tree, Allosaurus Shepherd, Barret, Avalanche Leader, Kratos, God of War, Blightsteel Colossus, Nekusar, the Mindrazer, Michelangelo, the Heart, Venom, Eddie Brock, Purphoros, God of the Forge, Horn of Greed, Silence, Sword of Body and Mind, Sword of Feast and Famine, Sword of Fire and Ice, Sword of Light and Shadow, Sword of War and Peace, Sigarda's Aid, Yavimaya Hollow, Violent Outburst, Elesh Norn, Grand Cenobite, Rampaging Ferocidon, Spitting Dilophosaurus, Descendants' Path, Raffine, Scheming Seer, Cloud, Ex-SOLDIER, Leonardo, the Balance, Wakka, Devoted Guardian, Captain James T. Kirk, and Raphael, the Muscle. Each has a separate scenario test. Elesh Norn's stat effects, Rampaging Ferocidon's no-life-gain rule, Spitting Dilophosaurus's counter and blocking restriction, Descendants' Path's shared-creature-type check, Raffine's variable connive, Cloud's equipment attack trigger, Leonardo's counter ability, Wakka's artifact-and-turn trigger, Captain Kirk's hand-dependent modal choice, and Raphael's damage doubling and Mutagen token are covered by their tests.

The following submitted cards are still left out. These are concrete gaps in the pinned engine or behaviors that still need checking against its card language; they are not being represented as if supported:

- Alms Collector needs to replace an opponent drawing a second or later card in a turn with one card for that player and one for Alms Collector's controller. The SDK lacks a way to redistribute only the extra cards from a multi-card draw.
- Bruvac the Grandiloquent needs to multiply milling; the engine's current mill modifier adds a fixed amount instead.
- Greymond, Avacyn's Stalwart needs a lasting choice of keyword abilities as creatures enter; that behavior has not been verified.
- Grimlock, Dinobot Leader, Megatron, Tyrant, Optimus Prime, Hero, Starscream, Power Hungry, and Slicer, Hired Muscle need their transforming or converting behavior.
- Heroes in a Half Shell needs to put a counter on each creature in the batch that damaged a player; the SDK does not expose that damage event's full creature group to the effect.
- Locke, Treasure Hunter needs to gather cards milled from every player's library and grant permission to cast from that combined group; the current library/card-source effects do not expose that collection.
- Ratchet, Field Medic needs Living Metal and conversion; the engine does not represent those mechanics.
- Shredder, Shadow Master needs opponent-specific attacking copies and a sacrifice/life-loss sequence; that behavior has not been verified.
- Vadrok, Apex of Thunder needs mutate and free casting from the graveyard; the engine does not provide the needed combination.
- Doubling Cube needs to double the mana already in a player's mana pool.
- Jeweled Lotus needs a restriction on spending its mana to cast a commander.
- Mox Diamond needs an enter-the-battlefield replacement that discards a land card.
- Dark Remedy could not be matched to exact card text, so it was not defined.
- Ephemerate needs an immediate exile-and-return of any creature, plus rebound. The SDK has rebound and an end-step blink, but its immediate exile-and-return is limited to double-faced cards.
- Determined Iteration's populate effect is available, but the engine cannot give the created token haste and schedule its sacrifice.
- Lotus Vale needs an enter-the-battlefield replacement that sacrifices two untapped lands.
- Gemstone Caverns needs a starting-hand/game-start replacement effect.

## Existing approximations and omissions

- **Blightsteel Colossus:** infect is unsupported by the pinned engine, so it deals normal damage rather than giving poison counters. Trample, indestructible, and its library replacement are represented.
- **Constricting Sliver:** the pinned engine does not fire its static granted enters abilities in this test path, so the exile/return effect is implemented directly on Constricting Sliver. Only Constricting Sliver's own entry is tracked; other Slivers do not receive the ability.
- Displaced Dinosaurs uses an enters trigger.
- Flash Photography is sorcery speed.
- Quest for Renewal untaps at the opponent's upkeep.
- Food Chain is not a true mana ability.
- Urza's Saga keeps its own lore counters.
- Sakashima of a Thousand Faces makes its copies nonlegendary.
- Goblin Welder is left out because the engine cannot perform the required zone swap.
- Ratchet, Field Medic is left out because its convert / Living Metal behavior is unsupported.

The app's exported custom-card data does not add these cards to Argentum's online server. They are for offline games. The custom card definitions now carry Scryfall image links. Some older set and collector-number metadata remains inaccurate and was left unchanged; the image links were matched to the card name instead.

## Export and checks

There are 92 registered custom-card definitions and 92 separate scenario-test source files. The latest saved custom-card test run (2026-10-17 18:06 MDT) completed 99 tests with 0 failures, including the Constricting Sliver and Venom scenarios. The corresponding export completed at 18:07 MDT and wrote 92 unique card records to `app/src/main/assets/argentum/custom-cards.jsonl` and 92 matching names to `custom-cards.index.tsv`; a local consistency check confirmed the two files contain the same 92 names.

Those saved results verify the custom-card module and export. In the current shell, the Gradle 8.13 distribution is missing and the wrapper cannot download it because network access is unavailable, so I could not rerun the tasks in this session. No Android app unit-test results are present, and app tests remain unverified here because the Android SDK is unavailable. When the build environment is available, run `./gradlew :custom-cards:test :custom-cards:exportCards :app:testDebugUnitTest` from `android/`.
