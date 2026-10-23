package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity

val DinosaurEgg = card("Dinosaur Egg") {
    manaCost = "{1}{G}"
    colorIdentity = "G"
    typeLine = "Creature — Dinosaur Egg"
    power = 0
    toughness = 3
    oracleText = "Evolve (Whenever a creature you control enters, if that creature has greater power or toughness than this creature, put a +1/+1 counter on this creature.)\nWhen this creature dies, you may discover X, where X is its toughness."

    evolve()

    triggeredAbility {
        trigger = Triggers.self.dies()
        effect = Effects.May(Effects.Discover(DynamicAmounts.triggeringToughness()))
        description = "When this creature dies, you may discover X, where X is its toughness."
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "92"
        imageUri = "https://api.scryfall.com/cards/named?exact=Dinosaur%20Egg&format=image"
    }
}
