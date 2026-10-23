package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity

/** Skittering Invasion — Rise of the Eldrazi #10 (original printing). */
val SkitteringInvasion = card("Skittering Invasion") {
    manaCost = "{7}"
    colorIdentity = ""
    typeLine = "Kindred Sorcery — Eldrazi"
    oracleText = "Create five 0/1 colorless Eldrazi Spawn creature tokens. " +
        "They have \"Sacrifice this token: Add {C}.\""

    spell {
        effect = Effects.CreateEldraziSpawn(5)
    }

    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "10"
        artist = "Eric Deschamps"
        imageUri = "https://api.scryfall.com/cards/roe/10?format=image"
    }
}
