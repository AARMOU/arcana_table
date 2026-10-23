package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter

val GreyKnightParagon = card("Grey Knight Paragon") {
    manaCost = "{4}{W}"
    colorIdentity = "W"
    typeLine = "Creature — Astartes Knight"
    power = 4
    toughness = 4
    oracleText = "Flash\nRites of Banishment — When this creature enters, destroy target attacking creature. " +
        "If that creature is a Demon, exile it instead."
    keywords(Keyword.FLASH)

    triggeredAbility {
        trigger = Triggers.self.enters()
        val attacker = target(TargetFilter.AttackingCreature)
        effect = Effects.If(
            condition = Conditions.TargetMatchesFilter(GameObjectFilter.Creature.withSubtype("Demon"), attacker),
            then = Effects.Exile(attacker),
            otherwise = Effects.Destroy(attacker),
        )
    }

    metadata { rarity = Rarity.UNCOMMON; collectorNumber = "13"; artist = "Games Workshop" }
}
