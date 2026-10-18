package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.*
import com.wingedsheep.sdk.scripting.*
import com.wingedsheep.sdk.scripting.effects.*
import com.wingedsheep.sdk.scripting.filters.unified.*
import com.wingedsheep.sdk.scripting.targets.*

val SliverQueen = card("Sliver Queen") {
    manaCost = "{W}{U}{B}{R}{G}"
    colorIdentity = "WUBRG"
    typeLine = "Legendary Creature — Sliver"
    power = 7
    toughness = 7
    oracleText = "{2}: Create a 1/1 colorless Sliver creature token."

    activatedAbility {
        cost = Costs.Mana("{2}")
        effect = Effects.CreateToken(power = 1, toughness = 1, creatureTypes = setOf("Sliver"))
        description = "{2}: Create a 1/1 colorless Sliver creature token."
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "129"
        artist = "Ron Spencer"
        imageUri = "https://api.scryfall.com/cards/named?exact=Sliver%20Queen&format=image"
    }
}
