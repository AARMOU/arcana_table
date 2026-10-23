package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.*
import com.wingedsheep.sdk.scripting.filters.unified.GroupFilter

val NutCollector = card("Nut Collector") {
    manaCost = "{5}{G}"
    colorIdentity = "G"
    typeLine = "Creature — Human Druid"
    power = 1
    toughness = 1
    oracleText = "At the beginning of your upkeep, you may create a 1/1 green Squirrel creature token.\n" +
        "Threshold — All Squirrels get +2/+2 as long as there are seven or more cards in your graveyard."

    triggeredAbility {
        trigger = Triggers.you.beginningOf(Step.UPKEEP)
        optional = true
        effect = Effects.CreateToken(1, 1, setOf(Color.GREEN), setOf("Squirrel"))
        description = "At the beginning of your upkeep, you may create a 1/1 green Squirrel creature token."
    }
    staticAbility {
        ability = ConditionalStaticAbility(
            ability = ModifyStats(
                powerBonus = 2,
                toughnessBonus = 2,
                filter = GroupFilter.AllCreatures.withSubtype("Squirrel"),
            ),
            condition = Conditions.CardsInGraveyardAtLeast(7),
        )
    }
    metadata {
        rarity = Rarity.MYTHIC
        collectorNumber = "171"
        imageUri = "https://cards.scryfall.io/normal/front/3/2/32980fc5-32ef-4f6d-8972-7a9e122c02e2.jpg?1783918438"
    }
}
