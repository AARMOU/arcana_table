package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.effects.Mode
import com.wingedsheep.sdk.scripting.targets.EffectTarget

val BlackMarketConnections = card("Black Market Connections") {
    manaCost = "{2}{B}"
    colorIdentity = "B"
    typeLine = "Enchantment"
    oracleText = "At the beginning of your precombat main phase, choose one or more —\n" +
        "• Sell Contraband — Create a Treasure token. You lose 1 life.\n" +
        "• Buy Information — Draw a card. You lose 2 life.\n" +
        "• Hire a Mercenary — Create a 3/2 colorless Shapeshifter creature token with changeling. You lose 3 life."

    triggeredAbility {
        trigger = Triggers.you.beginningOf(Step.PRECOMBAT_MAIN)
        effect = Effects.Modal(
            modes = listOf(
                Mode(effect = Effects.CreateTreasure() then Effects.LoseLife(1, EffectTarget.Controller), description = "Sell Contraband"),
                Mode(effect = Effects.DrawCards(1) then Effects.LoseLife(2, EffectTarget.Controller), description = "Buy Information"),
                Mode(
                    effect = Effects.CreateToken(
                        power = 3,
                        toughness = 2,
                        creatureTypes = setOf("Shapeshifter"),
                        keywords = setOf(Keyword.CHANGELING),
                    ) then Effects.LoseLife(3, EffectTarget.Controller),
                    description = "Hire a Mercenary",
                ),
            ),
            chooseCount = 3,
            minChooseCount = 1,
        )
        description = "At the beginning of your precombat main phase, choose one or more — Sell Contraband; Buy Information; Hire a Mercenary."
    }
    metadata {
        rarity = Rarity.RARE
        collectorNumber = "155"
        imageUri = "https://cards.scryfall.io/normal/front/9/d/9d9ca869-e68a-4f53-9f52-85c714dac6f3.jpg?1783903238"
    }
}
