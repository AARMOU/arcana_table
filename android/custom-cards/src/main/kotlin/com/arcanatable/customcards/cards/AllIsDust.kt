package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.predicates.CardPredicate

/**
 * Rise of the Eldrazi #1. The mass sacrifice is a single simultaneous batch
 * so death triggers see the entire group leaving together.
 */
val AllIsDust = card("All Is Dust") {
    manaCost = "{7}"
    colorIdentity = ""
    typeLine = "Kindred Sorcery — Eldrazi"
    oracleText = "Each player sacrifices all permanents they control that are one or more colors."

    spell {
        effect = Effects.SacrificeAll(
            GameObjectFilter.Permanent.withCardPredicate(CardPredicate.IsColored)
        )
    }

    metadata {
        rarity = Rarity.MYTHIC
        collectorNumber = "1"
        artist = "Jason Felix"
    }
}
