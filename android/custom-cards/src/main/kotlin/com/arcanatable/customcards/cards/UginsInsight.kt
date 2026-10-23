package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.references.Player

/** Scry based on the greatest *current* mana value, then draw three. */
val UginsInsight = card("Ugin's Insight") {
    manaCost = "{3}{U}{U}"
    colorIdentity = "U"
    typeLine = "Sorcery"
    oracleText = "Scry X, where X is the greatest mana value among permanents you control, then draw three cards."

    spell {
        val x = DynamicAmounts.battlefield(Player.You).maxManaValue()
        effect = Effects.Scry(x) then Effects.DrawCards(3)
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "87"
        artist = "Tyler Jacobson"
    }
}
