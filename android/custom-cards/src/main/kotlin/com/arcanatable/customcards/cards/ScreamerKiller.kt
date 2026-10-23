package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter

/**
 * Screamer-Killer — Tyranid Swarm.
 * Trigger only when its controller casts a creature SPELL of mana value >=5;
 * the trigger targets any valid player, creature, planeswalker, or battle.
 */
val ScreamerKiller = card("Screamer-Killer") {
    manaCost = "{4}{R}"
    colorIdentity = "R"
    typeLine = "Creature — Tyranid"
    power = 5
    toughness = 5
    oracleText = "Trample\nBio-Plasmic Scream — Whenever you cast a creature spell with mana value " +
        "5 or greater, Screamer-Killer deals 5 damage to any target."

    keywords(Keyword.TRAMPLE)

    triggeredAbility {
        trigger = Triggers.you.casts(GameObjectFilter.Creature.manaValueAtLeast(5))
        val chosen = target(Targets.Any)
        effect = Effects.DealDamage(5, chosen)
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "84"
        artist = "David Auden Nash"
    }
}
