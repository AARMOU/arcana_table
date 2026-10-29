package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter

val VerdantSunsAvatar = card("Verdant Sun's Avatar") {
    manaCost = "{5}{G}{G}"
    colorIdentity = "G"
    typeLine = "Creature — Dinosaur Avatar"
    power = 5
    toughness = 5
    oracleText = "Whenever this creature or another creature you control enters, you gain life equal to that creature's toughness."

    triggeredAbility {
        trigger = Triggers.a(GameObjectFilter.Creature.youControl()).enters()
        effect = Effects.GainLife(DynamicAmounts.triggeringToughness())
        description = "Whenever this creature or another creature you control enters, you gain life equal to that creature's toughness."
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "262"
        artist = "Izzy"
    }
}
