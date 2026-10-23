package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter

/** Sanguinary Priest — Warhammer 40,000 Commander #53. */
val SanguinaryPriest = card("Sanguinary Priest") {
    manaCost = "{3}{B}"
    colorIdentity = "B"
    typeLine = "Creature — Astartes Cleric"
    power = 2
    toughness = 4
    oracleText = "Lifelink\nBlood Chalice — Whenever another creature you control dies, " +
        "this creature deals 1 damage to any target."

    keywords(Keyword.LIFELINK)
    triggeredAbility {
        trigger = Triggers.another(GameObjectFilter.Creature.youControl()).dies()
        val damageTarget = target(Targets.Any)
        effect = Effects.DealDamage(1, damageTarget)
    }

    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "53"
        artist = "Games Workshop"
    }
}
