package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.events.Recipient

val CuriousAltisaur = card("Curious Altisaur") {
    manaCost = "{3}{G}"
    colorIdentity = "G"
    typeLine = "Creature — Dinosaur"
    power = 2
    toughness = 5
    oracleText = "Reach, vigilance\nWhenever a Dinosaur you control deals combat damage to a player, draw a card."
    keywords(Keyword.REACH, Keyword.VIGILANCE)

    triggeredAbility {
        trigger = Triggers.a(GameObjectFilter.Creature.withSubtype("Dinosaur").youControl())
            .dealsCombatDamage(Recipient.AnyPlayer)
        effect = Effects.DrawCards(1)
        description = "Whenever a Dinosaur you control deals combat damage to a player, draw a card."
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "90"
        artist = "Simon Dominic"
        imageUri = "https://cards.scryfall.io/normal/front/8/3/83fb87d8-d6f6-4cb0-aca1-49e6fcb181b1.jpg?1783913908"
    }
}
