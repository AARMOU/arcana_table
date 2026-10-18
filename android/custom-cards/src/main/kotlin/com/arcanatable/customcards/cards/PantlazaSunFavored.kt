package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter

val PantlazaSunFavored = card("Pantlaza, Sun-Favored") {
    manaCost = "{2}{R}{G}{W}"
    colorIdentity = "RGW"
    typeLine = "Legendary Creature — Dinosaur"
    power = 4
    toughness = 4
    oracleText = "Whenever Pantlaza or another Dinosaur you control enters, you may discover X, where X is that creature's toughness. Do this only once each turn."

    triggeredAbility {
        trigger = Triggers.a(GameObjectFilter.Creature.withSubtype("Dinosaur").youControl()).enters()
        oncePerTurn = true
        effect = Effects.May(Effects.Discover(DynamicAmounts.triggeringToughness()))
        description = "Whenever Pantlaza or another Dinosaur you control enters, you may discover X, where X is that creature's toughness. Do this only once each turn."
    }

    metadata {
        rarity = Rarity.MYTHIC
        collectorNumber = "30"
        imageUri = "https://api.scryfall.com/cards/named?exact=Pantlaza%2C%20Sun-Favored&format=image"
    }
}
