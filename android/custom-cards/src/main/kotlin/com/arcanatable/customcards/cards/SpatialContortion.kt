package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter

/** Spatial Contortion — Oath of the Gatewatch #8. */
val SpatialContortion = card("Spatial Contortion") {
    manaCost = "{1}{C}"
    colorIdentity = ""
    typeLine = "Instant"
    oracleText = "Target creature gets +3/-3 until end of turn."

    spell {
        val creature = target(TargetFilter.Creature)
        effect = Effects.ModifyStats(3, -3, creature)
    }

    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "8"
        artist = "Daarken"
        imageUri = "https://api.scryfall.com/cards/ogw/8?format=image"
    }
}
