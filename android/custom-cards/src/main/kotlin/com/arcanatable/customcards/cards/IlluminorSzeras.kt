package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity

val IlluminorSzeras = card("Illuminor Szeras") {
    manaCost = "{2}{B}"
    colorIdentity = "B"
    typeLine = "Legendary Artifact Creature — Necron"
    power = 3
    toughness = 3
    oracleText = "Secrets of the Soul — {T}, Sacrifice another creature: Add an amount of {B} equal to the sacrificed creature's mana value."

    activatedAbility {
        cost = Costs.Composite(
            Costs.Tap,
            Costs.SacrificeAnother(GameObjectFilter.Creature),
        )
        effect = Effects.AddMana(
            Color.BLACK,
            DynamicAmounts.sacrificedManaValue(0),
        )
        manaAbility = true
        timing = TimingRule.ManaAbility
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "37"
        artist = "Darren Tan"
        imageUri = "https://cards.scryfall.io/normal/front/d/1/d161a190-3c85-4207-9d88-7ff9213efea8.jpg?1783920924"
    }
}
