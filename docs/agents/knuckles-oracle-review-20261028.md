# Knuckles the Echidna — isolated Oracle/rules feasibility (#369)

**Status:** GPT rules review only. No card registration, no asset export, no SDK modification, no paid worker, and no gameplay certification.

**Owner request:** #350; trusted GPT route #369; coordination #81 and #321. Starting integration source `gpt/arcana-integration-next@9dd5fc9c8da129d8aeb58ef6ecbfecc9ff575c9f`. The current numbered Android build is B140; this review is **not** a B140 release branch.

## Evidence before engine work

The committed Cardkit Oracle cache does **not** contain Knuckles. The independent workflow `.github/workflows/knuckles-oracle-review.yml` requests the actual Scryfall record for **Secret Lair Drop #2082**, verifies canonical identity and all three Oracle rules lines, then emits the Scryfall record and Cardkit classification as review artifacts. Network failure or wording drift fails closed. The workflow cannot change the integration branch or dispatch Copilot.

Printed card: {2}{R}{R}, Legendary Creature — Echidna Warrior, 2/4; double strike, trample, haste. Combat damage to a player by one or more of your creatures creates **one Treasure per damaged player per combat-damage event**. At the beginning of your upkeep, if you control at least thirty artifacts, you win the game. These are **review expectations, not verified engine implementation**.

## Required authoritative Argentum positive/negative scenarios (not yet implemented)

1. **Combat-damage event grouping:** two of your creatures simultaneously deal combat damage to the **same** player → exactly one Treasure, not two; zero actual damage → none.
2. **Multiplayer separation:** one or more of your creatures deal combat damage to each of two different players in the same combat-damage step → two Treasures. Damage by another player's creatures does not trigger yours.
3. **Double strike:** Knuckles connects in first-strike and normal combat-damage steps → two separate events, potentially two Treasures. Trample and prevention must use real damage events, not merely attack declarations.
4. **Upkeep threshold:** 29 artifacts → no win trigger; 30 → trigger. Treasures and other artifact tokens count; creatures alone do not.
5. **Timing/interaction:** removing artifacts after the upkeep trigger is put on the stack must follow the actual Oracle intervening-if behavior (check on trigger and resolution); 30 artifacts acquired *after* upkeep began must not retroactively trigger.
6. **Four-player ownership:** only the controller's upkeep and combat-damage events count; opponent-owned artifacts and damage do not count toward the condition or Treasure generation.

**Next engineering gate:** Read the Scryfall evidence artifact and inspect the pinned Argentum engine's combat-damage aggregation, beginning-of-upkeep triggered-ability predicate and game-win primitive. Implement only the **shared authoritative primitive** if genuinely absent, with real JUnit positive/negative scenarios. Do not add a card-specific app-side rules workaround. A Cardkit `SUPPORTED/copilot` verdict alone is not playable certification; hand back to Copilot only after authoritative support and queue gates are independently verified.
