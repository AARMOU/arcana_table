package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.filters.unified.GroupFilter
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter
import com.wingedsheep.sdk.scripting.targets.EffectTarget

/** Star of Extinction — CMM #259. */
val StarOfExtinction = card("Star of Extinction") {
    manaCost = "{5}{R}{R}"
    colorIdentity = "R"
    typeLine = "Sorcery"
    oracleText = "Destroy target land. Star of Extinction deals 20 damage to each creature and each planeswalker."

    spell {
        val land = target(TargetFilter.Land)
        effect = Effects.Destroy(land) then Effects.ForEachInGroup(
            GroupFilter(GameObjectFilter.CreatureOrPlaneswalker),
            Effects.DealDamage(20, EffectTarget.IterationEntity),
        )
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "259"
        artist = "Chris Rahn"
    }
}
