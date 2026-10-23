package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.*
import com.wingedsheep.sdk.scripting.*
import com.wingedsheep.sdk.scripting.effects.*

val Bearscape = card("Bearscape") {
    manaCost = "{1}{G}{G}"
    colorIdentity = "G"
    typeLine = "Enchantment"
    oracleText = "{1}{G}, Exile two cards from your graveyard: Create a 2/2 green Bear creature token."

    activatedAbility {
        cost = Costs.Composite(Costs.Mana("{1}{G}"), Costs.ExileFromGraveyard(2))
        effect = Effects.CreateToken(
            power = 2,
            toughness = 2,
            colors = setOf(Color.GREEN),
            creatureTypes = setOf("Bear"),
        )
        description = "{1}{G}, Exile two cards from your graveyard: Create a 2/2 green Bear creature token."
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "229"
        artist = "Heather Hudson"
        imageUri = "https://cards.scryfall.io/normal/front/3/2/3284b61a-bd95-4846-ad3f-903cd1158867.jpg?1783945221"
    }
}
