package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter

val CopperGnomes = card("Copper Gnomes") {
    manaCost = "{2}"
    colorIdentity = ""
    typeLine = "Artifact Creature — Gnome"
    power = 1
    toughness = 1
    oracleText = "{4}, Sacrifice this creature: You may put an artifact card from your hand onto the battlefield."

    activatedAbility {
        cost = Costs.Composite(Costs.Mana("{4}"), Costs.SacrificeSelf)
        effect = Effects.May(Patterns.Hand.putFromHand(filter = GameObjectFilter.Artifact))
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "291"
        artist = "Jeff Laubenstein"
    }
}
