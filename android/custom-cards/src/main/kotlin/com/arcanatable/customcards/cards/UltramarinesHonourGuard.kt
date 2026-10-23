package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.dsl.mechanics.squad
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.ModifyStats
import com.wingedsheep.sdk.scripting.filters.unified.GroupFilter

val UltramarinesHonourGuard = card("Ultramarines Honour Guard") {
    manaCost = "{3}{W}"
    colorIdentity = "W"
    typeLine = "Creature — Astartes Warrior"
    power = 2
    toughness = 2
    oracleText = "Squad {2}\nOther creatures you control get +1/+1."

    squad("{2}")
    staticAbility {
        ability = ModifyStats(+1, +1, GroupFilter(GameObjectFilter.Creature.youControl(), excludeSelf = true))
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "18"
        artist = "Games Workshop"
    }
}
