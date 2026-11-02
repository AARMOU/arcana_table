package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.targets.TargetPermanentOrPlayer

/**
 * Mutated Cultist — Modern Horizons 3 Commander #53. {2}{B} Creature — Eldrazi Horror 1/3.
 *
 * "Devoid / When you cast this spell, remove all counters from up to one target permanent or
 * opponent. The next spell you cast this turn costs {1} less to cast for each counter removed this
 * way. / Deathtouch"
 *
 * - A cast trigger (`Triggers.self.isCast()`): it goes on the stack above the Cultist and resolves
 *   first; putting the Cultist onto the battlefield without casting it doesn't trigger it.
 * - "Up to one target permanent or opponent" is `TargetPermanentOrPlayer(opponentsOnly = true)`
 *   made optional: any permanent, or an opponent (team-aware), never its controller; zero targets
 *   is allowed.
 * - The counter total (every kind; for a player that's poison, energy, experience…, which live in
 *   the same counters component as a permanent's) is stored *before* `RemoveAllCounters` runs.
 * - `ReduceNextSpellCost` locks the number in on resolution: a one-shot rider that only reduces
 *   generic mana and floors it at {0} (CR 601.2f), is spent by the next spell its controller casts
 *   this turn even if that spell couldn't use all of it, and is cleared at the next turn. A total
 *   of 0 installs no rider. A spell cast in response, before the trigger resolves, isn't "next".
 */
val MutatedCultist = card("Mutated Cultist") {
    manaCost = "{2}{B}"
    colorIdentity = "B"
    typeLine = "Creature — Eldrazi Horror"
    power = 1
    toughness = 3
    oracleText = "Devoid (This card has no color.)\n" +
        "When you cast this spell, remove all counters from up to one target permanent or opponent. " +
        "The next spell you cast this turn costs {1} less to cast for each counter removed this way.\n" +
        "Deathtouch"

    keywords(Keyword.DEVOID, Keyword.DEATHTOUCH)

    triggeredAbility {
        trigger = Triggers.self.isCast()
        val victim = target(
            TargetPermanentOrPlayer(
                opponentsOnly = true,
                descriptionOverride = "up to one target permanent or opponent",
            ),
            optional = true,
        )
        effect = Effects.Pipeline {
            val removed = storeNumber(DynamicAmounts.countersOn(victim, null), name = "cultistCountersRemoved")
            run(Effects.RemoveAllCounters(victim))
            run(Effects.ReduceNextSpellCost(removed.amount))
        }
        description = "When you cast this spell, remove all counters from up to one target permanent or " +
            "opponent. The next spell you cast this turn costs {1} less to cast for each counter removed this way."
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "53"
        artist = "Dave Kendall"
        imageUri = "https://cards.scryfall.io/normal/front/c/c/cc4bb871-cde9-43a9-8089-5987fa0dd4ef.jpg?1783911422"
    }
}
